package net.dehydrated_pain.turnbasedcombatmod.network;

import io.netty.buffer.ByteBuf;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.SkillInfo;
import net.dehydrated_pain.turnbasedcombatmod.utils.playerturn.EnemyInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public record PlayerTurnPacket(List<EnemyInfo> enemyInfoList, List<SkillInfo> skills) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerTurnPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "player_turn"));

    public static final StreamCodec<ByteBuf, EnemyInfo> ENEMY_INFO_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            EnemyInfo::enemyUUID,
            BlockPos.STREAM_CODEC,
            EnemyInfo::enemyPosition,
            EnemyInfo::new
    );

    public static final StreamCodec<ByteBuf, SkillInfo> SKILL_INFO_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SkillInfo::skillName,
            ByteBufCodecs.DOUBLE,
            SkillInfo::skillHungerCost,
            SkillInfo::new
    );

    public static final StreamCodec<ByteBuf, PlayerTurnPacket> STREAM_CODEC = StreamCodec.composite(
            ENEMY_INFO_CODEC.apply(ByteBufCodecs.list()),
            PlayerTurnPacket::enemyInfoList,
            SKILL_INFO_CODEC.apply(ByteBufCodecs.list()),
            PlayerTurnPacket::skills,
            PlayerTurnPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
