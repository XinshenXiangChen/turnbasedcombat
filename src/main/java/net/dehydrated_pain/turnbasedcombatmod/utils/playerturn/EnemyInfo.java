package net.dehydrated_pain.turnbasedcombatmod.utils.playerturn;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record EnemyInfo(UUID enemyUUID, BlockPos enemyPosition) {
}
