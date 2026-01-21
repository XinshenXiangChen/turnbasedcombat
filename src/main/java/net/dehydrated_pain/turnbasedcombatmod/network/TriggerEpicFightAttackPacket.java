package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record TriggerEpicFightAttackPacket(boolean isSkill, String skill) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TriggerEpicFightAttackPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "trigger_epic_fight_attack"));

    public static final StreamCodec<ByteBuf, TriggerEpicFightAttackPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            TriggerEpicFightAttackPacket::isSkill,
            ByteBufCodecs.STRING_UTF8,
            TriggerEpicFightAttackPacket::skill,
            TriggerEpicFightAttackPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
