package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.EndCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.EndPlayerTurnPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.PlayerTurnPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTERequestPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTEResponsePacket;
import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.utils.playerturn.EnemyInfo;
import net.dehydrated_pain.turnbasedcombatmod.ui.CombatUIConfig;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.ParryTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;


@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class CombatInstanceClient {
    public static boolean inCombat = false;
    public static boolean isPlayerTurn = false;
    
    // Enemy selection state
    public static List<EnemyInfo> enemyInfoList = new ArrayList<>();
    public static int selectedEnemyIndex = 0;
    public static boolean isSelectingEnemy = false;
    
    private static final double CAMERA_OFFSET_X = -3.0;
    private static final double CAMERA_OFFSET_Y = 1.5;
    private static final double CAMERA_OFFSET_Z = 0.0;


    // Each client only handles their own player, so static is safe here
    private static final double PARRY_TIMEOUT_TIME = 0.15;
    private static final double DODGE_TIMEOUT_TIME = 0.2;
    private static double reactionTimer = 0.0;
    private static boolean parried = false;
    private static boolean qteActive = false;
    private static ParryTypes activeParryType = null;
    private static long qteStartTime = 0;


    // Shared cooldown for both parry and dodge
    private static double DEFENSE_COOLDOWN_TIME = 1;
    private static double defenseCooldownTime = 0;
    private static boolean defenseOnCooldown = false;



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
            
            if (reactionTimer >= DODGE_TIMEOUT_TIME) {
                handleQTETimeout();
            }
        }

        // Update defense cooldown (shared for parry and dodge)
        if (defenseOnCooldown && inCombat) {
            defenseCooldownTime -= 1.0 / 20.0; // Decrement by 1 tick (assuming 20 TPS)
            if (defenseCooldownTime <= 0) {
                defenseOnCooldown = false;
                defenseCooldownTime = 0;
            }
        }
    }
    

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!isSelectingEnemy || !inCombat) return;
        
        // TEST: hardcoded position at (0, 50, 0) looking down
        Vec3 testCameraPos = new Vec3(30, 50, 30);
        
        event.setYaw(0);
        event.setPitch(90); // look straight down
        
        Camera camera = event.getCamera();
        camera.setPosition(testCameraPos);
    }
    
    public static void selectEnemy(int index) {
        if (enemyInfoList.isEmpty()) return;
        
        selectedEnemyIndex = Math.max(0, Math.min(index, enemyInfoList.size() - 1));
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Selected enemy " + (selectedEnemyIndex + 1) + "/" + enemyInfoList.size()));
        }
    }
    
    public static void selectPreviousEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex <= 0) return;
        selectEnemy(selectedEnemyIndex - 1);
    }
    
    public static void selectNextEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex >= enemyInfoList.size() - 1) return;
        selectEnemy(selectedEnemyIndex + 1);
    }
    
    public static EnemyInfo getSelectedEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex < 0 || selectedEnemyIndex >= enemyInfoList.size()) {
            return null;
        }
        return enemyInfoList.get(selectedEnemyIndex);
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

        if (defenseOnCooldown) {
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onCalculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (!inCombat) event.setDistance(4.0F);
        // Set camera distance farther (default is usually around 4.0)
        else event.setDistance(4.0F);
    }


    public static void startCombatNetworkHandler(final StartCombatPacket pkt, final IPayloadContext context) {
        // Network thread: we only set flags or read packet data if needed

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

    public static void playerTurnNetworkHandler(final PlayerTurnPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
                    isPlayerTurn = true;
                    isSelectingEnemy = false; // Reset selection mode when turn starts
                    enemyInfoList = new ArrayList<>(pkt.enemyInfoList());
                    selectedEnemyIndex = 0; // Start with first enemy (leftmost)
                })
                .exceptionally(e -> {
                    context.disconnect(Component.literal("Failed to handle player turn: " + e.getMessage()));
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

        }).exceptionally(e -> {
 
            context.disconnect(Component.literal("Failed to handle QTE request: " + e.getMessage()));
            return null;
        });
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        if (!inCombat) return;
        
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        
        int key = event.getKey();
        
        if (isSelectingEnemy) {
            if (key == GLFW.GLFW_KEY_N) {
                selectPreviousEnemy();
                return;
            } else if (key == GLFW.GLFW_KEY_M) {
                selectNextEnemy();
                return;
            }
        }
        
        Boolean isParry = getReactionTypeForKey(key);
        
        // If it's a parry/dodge key, check if we should trigger cooldown
        if (isParry != null) {
            if (defenseOnCooldown) {
                return;
            }

            if (!qteActive) {
                defenseOnCooldown = true;
                defenseCooldownTime = DEFENSE_COOLDOWN_TIME;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String actionType = isParry ? "Parry" : "Dodge";
                    mc.player.sendSystemMessage(Component.literal(actionType + " used! Cooldown: " + DEFENSE_COOLDOWN_TIME + "s"));
                }
                return;
            }
            
            // If in QTE, handle the QTE response
            if (qteActive && !parried) {
                ParryTypes requiredType = activeParryType;
                if (requiredType == null) return;
                
                String requiredAction = requiredType.getActionName();
                
                boolean isValidKey = false;
                
                if (isParry == null) {
                    return;
                } else if (!isParry) {
                    isValidKey = true;
                } else {
                    isValidKey = isKeyForAction(key, requiredAction);
                }
                
                if (isValidKey) {
                    boolean isDodgeAction = !isParry; // If isParry is false, it's a dodge
                    double timeoutWindow = isDodgeAction ? DODGE_TIMEOUT_TIME : PARRY_TIMEOUT_TIME;
                    
                    // Check if within the timeout window
                    if (reactionTimer < timeoutWindow) {
                        handleDefenseSuccess(isParry);
                    }
                }
            }
        }
    }

    private static Boolean getReactionTypeForKey(int key) {

        if (key == GLFW.GLFW_KEY_E || 
            key == GLFW.GLFW_KEY_LEFT_SHIFT || 
            key == GLFW.GLFW_KEY_RIGHT_SHIFT || 
            key == GLFW.GLFW_KEY_SPACE) {
            return true;
        }
        
        // Dodge keys
        if (key == GLFW.GLFW_KEY_Q) {
            return false;
        }
        
        return null; // Not a defensive reaction key
    }
    
    /**
     * Checks if the pressed key matches the required action name
     */
    private static boolean isKeyForAction(int key, String actionName) {
        if (actionName.equalsIgnoreCase("E") && key == GLFW.GLFW_KEY_E) return true;
        if (actionName.equalsIgnoreCase("SHIFT") && (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) return true;
        if (actionName.equalsIgnoreCase("jump") && key == GLFW.GLFW_KEY_SPACE) return true;
        if (actionName.equalsIgnoreCase("Q") && key == GLFW.GLFW_KEY_Q) return true;
        
        return false;
    }
    

    private static void handleDefenseSuccess(boolean isParry) {
        parried = true;
        qteActive = false;
        
        // Set cooldown after successful defense
        defenseOnCooldown = false;
        defenseCooldownTime = 0;

        
        // Send response packet to server (success = true, isParry indicates the type)
        sendParrySuccess(parried, isParry);
        

        clearQTE();
    }

    

    private static void handleQTETimeout() {
        parried = false;
        qteActive = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Parry Failed! You didn't respond in time."));
        }
        
        boolean isParry = true; 
        clearQTE();
        
        sendParrySuccess(parried, isParry);
    }
    

    private static void clearQTE() {
        activeParryType = null;
        reactionTimer = 0.0;
        qteStartTime = 0;
    }

    public static void endCombat() {
        Minecraft mc = Minecraft.getInstance();
        inCombat = false;
        isPlayerTurn = false;
        isSelectingEnemy = false;
        enemyInfoList.clear();
        selectedEnemyIndex = 0;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        
        // Clear QTE state when combat ends
        clearQTE();
        parried = false;
        qteActive = false;
        
        defenseOnCooldown = false;
        defenseCooldownTime = 0;
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

    private static void sendParrySuccess(Boolean success, boolean isParry) {
        PacketDistributor.sendToServer(new QTEResponsePacket(success, isParry));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!inCombat || !isPlayerTurn) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getGuiGraphics().guiWidth();
        int screenHeight = event.getGuiGraphics().guiHeight();
        
        // Load UI design from config
        CombatUIConfig.TurnIndicatorConfig config = CombatUIConfig.getTurnIndicator();
        
        int x = (screenWidth - config.width) / 2;
        int y = (screenHeight - config.height) / 2;

        guiGraphics.blit(config.image, x, y, 0, 0, config.width, config.height, config.width, config.height);
        
        // Show enemy selection indicator if in selection mode
        if (isSelectingEnemy && !enemyInfoList.isEmpty()) {
            String selectionText = "Select Enemy: " + (selectedEnemyIndex + 1) + "/" + enemyInfoList.size() + " [J] <- -> [L]";
            int textWidth = mc.font.width(selectionText);
            guiGraphics.drawString(mc.font, selectionText, (screenWidth - textWidth) / 2, screenHeight - 40, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!inCombat || !isPlayerTurn) return;
        
        if (event.isUseItem()) {
            Minecraft mc = Minecraft.getInstance();
            if (!isSelectingEnemy) {
                isSelectingEnemy = true;
                selectedEnemyIndex = 0;
                
                if (mc.player != null && !enemyInfoList.isEmpty()) {
                    mc.player.sendSystemMessage(Component.literal("Selecting enemy... Use [J]/[L] to switch, Right-click to confirm"));
                }
            } else {
                isPlayerTurn = false;
                isSelectingEnemy = false;
                PacketDistributor.sendToServer(new EndPlayerTurnPacket());
            }
            event.setCanceled(true);
        }
    }
}
