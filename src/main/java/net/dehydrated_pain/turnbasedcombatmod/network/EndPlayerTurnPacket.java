package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record EndPlayerTurnPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EndPlayerTurnPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "end_player_turn"));

    public static final StreamCodec<ByteBuf, EndPlayerTurnPacket> STREAM_CODEC = StreamCodec.unit(new EndPlayerTurnPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
