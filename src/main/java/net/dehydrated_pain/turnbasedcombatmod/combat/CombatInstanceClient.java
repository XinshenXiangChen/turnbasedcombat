package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.EndCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTERequestPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTEResponsePacket;
import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse.DodgeTypes;
import net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse.ParryTypes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;


@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class CombatInstanceClient {
    public static boolean inCombat = false;


    // Each client only handles their own player, so static is safe here
    private static final double REACTION_TIMEOUT_TIME = 0.15;
    private static double reactionTimer = 0.0;
    private static boolean parried = false;
    private static boolean qteActive = false;
    private static ParryTypes activeParryType = null;
    private static long qteStartTime = 0;


    private static double PARRY_COOLDOWN_TIME = 0.3;
    private static double parryCooldownTime = 0;
    private static boolean parryOnCooldown = false;



    private static final ResourceKey<Level> COMBAT_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));



    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (inCombat) {
            ResourceKey<Level> currentDim = mc.player.level().dimension();
            if (!currentDim.equals(COMBAT_DIMENSION)) {
                endCombat();
            }
        }

        if (qteActive && !parried) {
            double elapsedSeconds = (System.currentTimeMillis() - qteStartTime) / 1000.0;
            reactionTimer = elapsedSeconds;
            
            if (reactionTimer >= REACTION_TIMEOUT_TIME) {
                handleQTETimeout();
            }
        }
    }
    
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!inCombat) return;

        // Completely zero out all movement inputs
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;

        // this is only for the animation, the damage handling is in keypressed event

        if (parryOnCooldown) {
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;

        }
    }

    @SubscribeEvent
    public static void onCalculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (!inCombat) event.setDistance(8.0F);
        // Set camera distance farther (default is usually around 4.0)
        else event.setDistance(8.0F);
    }


    public static void startCombatNetworkHandler(final StartCombatPacket pkt, final IPayloadContext context) {
        // Network thread: we only set flags or read packet data if needed
        // (StartCombatPacket has no data, so nothing to do here)

        // Main thread work
        context.enqueueWork(() -> {
                    CombatInstanceClient.inCombat = true;
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                })
                .exceptionally(e -> {
                    // Handle exception, optional
                    context.disconnect(net.minecraft.network.chat.Component.literal("Failed to start combat: " + e.getMessage()));
                    return null;
                });
    }
    
    public static void endCombatNetworkHandler(final EndCombatPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
                    endCombat();
                })
                .exceptionally(e -> {
                    // Handle exception, optional
                    context.disconnect(net.minecraft.network.chat.Component.literal("Failed to end combat: " + e.getMessage()));
                    return null;
                });
    }

    public static void qteRequesteNetworkHandler(final QTERequestPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
            ParryTypes requiredParryType = pkt.parryType();
            String actionName = requiredParryType.getActionName();
            
            // Start the QTE timer
            activeParryType = requiredParryType;
            qteActive = true;
            parried = false;
            reactionTimer = 0.0;
            qteStartTime = System.currentTimeMillis();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                    "QTE: Press " + actionName + " to parry! (0.1s)"));
            }
        }).exceptionally(e -> {
            // Handle exception, optional
            context.disconnect(Component.literal("Failed to handle QTE request: " + e.getMessage()));
            return null;
        });
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {



        if (!qteActive || parried) return; 
        
        int key = event.getKey(); // GLFW key code

        // set parry/dodge timer on cd

        ParryTypes requiredType = activeParryType;
        
        if (requiredType == null) return;
        
        String actionName = requiredType.getActionName();
        boolean correctKeyPressed = false;

        // TODO this could be done much better
        if (actionName.equalsIgnoreCase("E") && key == GLFW.GLFW_KEY_E) {
            correctKeyPressed = true;
        } else if (actionName.equalsIgnoreCase("SHIFT") && (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            correctKeyPressed = true;
        } else if (actionName.equalsIgnoreCase("jump") && key == GLFW.GLFW_KEY_SPACE) {
            correctKeyPressed = true;
        }
        
        // If correct key was pressed and within time limit
        if (correctKeyPressed && reactionTimer < REACTION_TIMEOUT_TIME) {
            handleParrySuccess();
        }
    }
    
    /**
     * Handles successful parry - player pressed the correct key in time
     */
    private static void handleParrySuccess() {
        parried = true;
        qteActive = false;
        parryOnCooldown = false;
        parryCooldownTime = 0;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Parry Successful!"));
        }
        
        // Send response packet to server (success = true)
        sendParrySuccess(parried);
        

        clearQTE();
    }
    
    /**
     * Handles QTE timeout - player didn't parry in time
     */
    private static void handleQTETimeout() {
        parried = false;
        qteActive = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Parry Failed! You didn't respond in time."));
        }
        
        clearQTE();
        
        // Send response packet to server (success = false)
        sendParrySuccess(parried);
    }
    
    /**
     * Clears the QTE state
     */
    private static void clearQTE() {
        activeParryType = null;
        reactionTimer = 0.0;
        qteStartTime = 0;
    }

    public static void endCombat() {
        Minecraft mc = Minecraft.getInstance();
        inCombat = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        
        // Clear QTE state when combat ends
        clearQTE();
        parried = false;
        qteActive = false;
    }
    
    /**
     * Gets whether the player successfully parried
     */
    public static boolean hasParried() {
        return parried;
    }
    
    /**
     * Gets whether a QTE is currently active
     */
    public static boolean isQTEActive() {
        return qteActive;
    }

    private static void sendParrySuccess(Boolean success) {
        PacketDistributor.sendToServer(new QTEResponsePacket(success));
    }
}
