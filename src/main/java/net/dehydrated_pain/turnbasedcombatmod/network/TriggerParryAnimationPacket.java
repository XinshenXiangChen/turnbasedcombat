package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.ParryTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

/**
 * Packet sent from server to client to trigger a parry animation
 * Only sent when parry is successful
 */
public record TriggerParryAnimationPacket(ParryTypes parryType) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TriggerParryAnimationPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "trigger_parry_animation"));

    public static final StreamCodec<ByteBuf, TriggerParryAnimationPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE.map(
                    ordinal -> ParryTypes.values()[ordinal & 0xFF],
                    parryType -> (byte) parryType.ordinal()
            ),
            TriggerParryAnimationPacket::parryType,
            TriggerParryAnimationPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

