package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record StartCombatPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartCombatPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "start_combat"));

    // Since StartCombatPacket is a record with no fields, we use StreamCodec.unit
    // This creates a codec that always encodes/decodes to the same instance
    public static final StreamCodec<ByteBuf, StartCombatPacket> STREAM_CODEC = StreamCodec.unit(new StartCombatPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}