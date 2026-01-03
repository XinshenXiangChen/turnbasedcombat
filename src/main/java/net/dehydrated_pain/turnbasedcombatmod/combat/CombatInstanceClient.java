package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;


@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class CombatInstanceClient {
    public static boolean inCombat = false;


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


    public static void startCombatNetworkHandler(final StartCombatPacket pkt, final IPayloadContext context) {
        // Network thread: we only set flags or read packet data if needed
        // (StartCombatPacket has no data, so nothing to do here)

        // Main thread work
        context.enqueueWork(() -> {
                    CombatInstanceClient.inCombat = true;
                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
                })
                .exceptionally(e -> {
                    // Handle exception, optional
                    context.disconnect(net.minecraft.network.chat.Component.literal("Failed to start combat: " + e.getMessage()));
                    return null;
                });
    }

    public static void endCombat() {
        Minecraft mc = Minecraft.getInstance();
        inCombat = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
}
