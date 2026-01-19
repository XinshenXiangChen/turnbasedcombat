package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record TriggerEpicFightAttackPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TriggerEpicFightAttackPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "trigger_epic_fight_attack"));

    public static final StreamCodec<ByteBuf, TriggerEpicFightAttackPacket> STREAM_CODEC = StreamCodec.unit(new TriggerEpicFightAttackPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
