package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record QTEResponsePacket(boolean success, boolean isParry) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QTEResponsePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "qte_response"));

    // StreamCodec for serializing/deserializing the packet with boolean fields
    public static final StreamCodec<ByteBuf, QTEResponsePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            QTEResponsePacket::success,
            ByteBufCodecs.BOOL,
            QTEResponsePacket::isParry,
            QTEResponsePacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
