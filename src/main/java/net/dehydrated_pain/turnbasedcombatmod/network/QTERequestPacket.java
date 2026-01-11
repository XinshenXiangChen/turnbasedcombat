package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;

import net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse.ParryTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record QTERequestPacket(ParryTypes parryType) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QTERequestPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "qte_request"));

    public static final StreamCodec<ByteBuf, QTERequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE.map(
                    ordinal -> ParryTypes.values()[ordinal & 0xFF],
                    parryType -> (byte) parryType.ordinal()
            ),
            QTERequestPacket::parryType,
            QTERequestPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
