package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.EndCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTERequestPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTEResponsePacket;
import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse.DodgeTypes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;


@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class CombatInstanceClient {
    public static boolean inCombat = false;
    
    private static final ResourceKey<Level> COMBAT_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));


    // Make sure that if the player is nt in a combat dimension to avoid it being
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // If in combat but not in combat dimension, exit combat
        if (inCombat) {
            ResourceKey<Level> currentDim = mc.player.level().dimension();
            if (!currentDim.equals(COMBAT_DIMENSION)) {
                endCombat();
            }
        }
    }
    
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!inCombat) return;
        
        // Completely zero out all movement inputs
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;
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
            DodgeTypes requiredDodgeType = pkt.dodgeType();
            String actionName = requiredDodgeType.getActionName();
            
            // TODO: Display QTE prompt to player with the required dodge type
            // The client now knows what type of dodge is required (e.g., "jump" or "Q")
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "QTE: Press " + actionName + " to dodge!"));
            }
        }).exceptionally(e -> {
            // Handle exception, optional
            context.disconnect(net.minecraft.network.chat.Component.literal("Failed to handle QTE request: " + e.getMessage()));
            return null;
        });
    }


    public static void endCombat() {
        Minecraft mc = Minecraft.getInstance();
        inCombat = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
}
