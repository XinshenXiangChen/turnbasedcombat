package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record EndPlayerTurnPacket(int abilityIndex, int enemyIndex, String skillName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EndPlayerTurnPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "end_player_turn"));

    public static final StreamCodec<ByteBuf, EndPlayerTurnPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EndPlayerTurnPacket::abilityIndex,
            ByteBufCodecs.INT, EndPlayerTurnPacket::enemyIndex,
            ByteBufCodecs.STRING_UTF8, EndPlayerTurnPacket::skillName,
            EndPlayerTurnPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    // Helper constants for ability types
    public static final int ABILITY_ATTACK = 0;
    public static final int ABILITY_SKILL = 1;
    public static final int ABILITY_ITEM = 2;
}
