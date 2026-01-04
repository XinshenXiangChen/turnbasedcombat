package net.dehydrated_pain.turnbasedcombatmod.combat;

import com.ibm.icu.impl.Pair;
import net.dehydrated_pain.turnbasedcombatmod.network.EndCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.worldgen.StructurePlacer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class CombatInstanceServer {
    
    // Store active combat instances by player UUID
    private static final Map<UUID, CombatInstanceServer> activeCombatInstances = new ConcurrentHashMap<>();

    public ServerPlayer player;
    List<Entity> enemies;
    List<UUID> enemyUUIDs;
    public ServerLevel combatServerLevel;
    
    // Store original position and dimension for teleporting back
    private ServerLevel originalLevel;
    private double originalX, originalY, originalZ;
    private float originalYRot, originalXRot;

    // Store enemy original positions, levels, and rotations
    private Map<UUID, ServerLevel> enemyOriginalLevels = new HashMap<>();
    private Map<UUID, BlockPos> enemyOriginalPositions = new HashMap<>(); // [x, y, z]
    private Map<UUID, Vec2> enemyOriginalRot = new HashMap<>();



    // TODO: make the calculation not hardcoded xD


    private static final BlockPos PLAYER_SPAWN_POS = new BlockPos(21, 1, 5);
    private static final BlockPos FIRST_ENEMY_SPAWN_POS = new BlockPos(21, 1, 20);
    private static final Integer ENEMY_SEPARATION = 4;

    // battle stuff
    private Map<UUID, BlockPos> enemOnBattleOriginalPos = new HashMap<>();
    private Queue<UUID> battleQueue = new LinkedList<>();
    Entity currentBattleEntity;
    boolean entityTurnFinished = true;

    public CombatInstanceServer(ServerPlayer _player, List<Entity> _enemies, Entity firstAttacker) {

        player = _player;

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));

        combatServerLevel = player.getServer().getLevel(dimKey);
        enemies = _enemies;
        // Store UUIDs for reliable entity lookup
        enemyUUIDs = _enemies.stream()
                .map(Entity::getUUID)
                .collect(Collectors.toList());

        setCombatEnvironment();

        // build initial battle queue using UUIDs
        UUID firstAttackerUUID = firstAttacker.getUUID();
        battleQueue.offer(firstAttackerUUID);

        if (firstAttacker instanceof ServerPlayer && firstAttacker == player) {
            for (Entity entity: _enemies) {
                battleQueue.offer(entity.getUUID());
            }

        }
        else {

            for (Entity entity: _enemies) {
                UUID entityUUID = entity.getUUID();
                if (!battleQueue.contains(entityUUID)) {
                    battleQueue.offer(entityUUID);
                }

            }
            // battleQueue.offer(player.getUUID());
        }

        // Register this instance
        activeCombatInstances.put(player.getUUID(), this);
    }
    
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Run turnBasedCombat() every tick for all active combat instances
        activeCombatInstances.values().forEach(CombatInstanceServer::turnBasedCombat);
        // Remove instances where combat has ended (player is no longer in combat dimension)
        activeCombatInstances.entrySet().removeIf(entry -> {
            CombatInstanceServer instance = entry.getValue();
            return instance.player == null || 
                   instance.combatServerLevel == null ||
                   instance.player.serverLevel() != instance.combatServerLevel;
        });
    }

    public void turnBasedCombat() {
        if (shouldEndCombat()) {
            endCombatEnvironment();
            return;
        }

        if (!entityTurnFinished) {

            if (currentBattleEntity == null) {
                entityTurnFinished = true;
            }

            if (currentBattleEntity instanceof ServerPlayer && currentBattleEntity == player) {
                // Player's turn - handle player actions here
                LOGGER.info("Player's turn");
            } else {
                // Only enable attack if not already attacking
                if (currentBattleEntity instanceof Mob mob && mob.getTarget() == null) {
                    enemyAttack(currentBattleEntity);
                }
            }
        }
        else {
            UUID attackerUUID = battleQueue.poll();
            if (attackerUUID == null) return;
            // Look up the entity from the combat dimension using UUID
            currentBattleEntity = combatServerLevel.getEntity(attackerUUID);
            if (currentBattleEntity != null) {
                entityTurnFinished = false;
            }
        }

    }

    private void enemyAttack(Entity enemy) {
        // Ensure we have the entity from the combat dimension
        Entity enemyInCombat = combatServerLevel.getEntity(enemy.getUUID());
        if (enemyInCombat == null) {
            LOGGER.warn("Enemy {} not found in combat dimension", enemy.getUUID());
            return;
        }
        
        // Use the entity from combat dimension
        enemy = enemyInCombat;
        
        if (!(enemy instanceof Mob enemyMob)) {
            LOGGER.warn("Enemy {} is not a Mob", enemy.getUUID());
            return;
        }
        
        enemyMob.setNoAi(false);
        enemyMob.setTarget(player);
        LOGGER.info("Enabled AI for enemy {} to attack player", enemy.getName().getString());
    }

    /**
     * Checks if all enemies are dead
     * @return true if all enemies are dead or removed
     */
    public boolean areAllEnemiesDead() {
        if (enemyUUIDs == null || enemyUUIDs.isEmpty()) return true;
        if (combatServerLevel == null) return true;
        

        for (UUID enemyUUID : enemyUUIDs) {
            Entity enemy = combatServerLevel.getEntity(enemyUUID);
            if (enemy != null && enemy.isAlive()) {
                return false;
            }
        }
        
        LOGGER.info("Enemy status: {} total, {} alive in combat dimension", enemyUUIDs.size());
        return true;
    }
    
    /**
     * Checks if the player is dead
     * @return true if the player is dead
     */
    public boolean isPlayerDead() {
        return player == null || !player.isAlive();
    }
    
    /**
     * Checks if combat should end (either all enemies dead or player dead)
     * @return true if combat should end
     */
    public boolean shouldEndCombat() {
        return areAllEnemiesDead() || isPlayerDead();
    }


    /*
    setCombatEnvironment should create a carbon copy of the surroundings of the player, then teleport the player and the enemies into the world

    also store original player combat to teleport back
     */
    private void setCombatEnvironment() {
        Minecraft mc = Minecraft.getInstance();
        
        // Store original position and dimension
        originalLevel = player.serverLevel();
        originalX = player.getX();
        originalY = player.getY();
        originalZ = player.getZ();
        originalYRot = player.getYRot();
        originalXRot = player.getXRot();
        
        LOGGER.info("Stored original position: ({}, {}, {}) in dimension {}", 
                originalX, originalY, originalZ, originalLevel.dimension().location());
        
        // Place structure when entering combat dimension
        StructurePlacer.place(combatServerLevel);

        // Store original positions, levels, and rotations for all enemies
        for (Entity enemy: enemies) {
            UUID enemyUUID = enemy.getUUID();
            enemyOriginalLevels.put(enemyUUID, (ServerLevel) enemy.level());
            enemyOriginalPositions.put(enemyUUID, new BlockPos((int) enemy.getX(),(int) enemy.getY(), (int) enemy.getZ()));
            enemyOriginalRot.put(enemyUUID, new Vec2(enemy.getYRot(), enemy.getXRot()));
            
            if (enemy instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setDeltaMovement(0, 0, 0);
            }
        }

        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        teleport(player, combatServerLevel, enemies);

        LOGGER.info("send player combat packet");

        sendStartCombatPacket(player);

    }

    private void endCombatEnvironment() {
        boolean playerDied = isPlayerDead();
        
        // Teleport player back to original position and dimension (if alive)
        if (player != null && originalLevel != null && !playerDied) {
            player.teleportTo(
                    originalLevel,
                    originalX, originalY, originalZ,
                    EnumSet.noneOf(RelativeMovement.class),
                    originalYRot,
                    originalXRot
            );
            LOGGER.info("Teleported player back to original position at ({}, {}, {}) in dimension {}",
                    originalX, originalY, originalZ, originalLevel.dimension().location());
        }
        
        // If player died, teleport ALL enemies back to their original locations
        if (playerDied && enemyOriginalLevels != null && enemyOriginalPositions != null) {
            for (UUID enemyUUID : enemyUUIDs) {
                Entity enemy = combatServerLevel.getEntity(enemyUUID);
                if (enemy != null && enemy.isAlive()) {
                    ServerLevel originalLevel = enemyOriginalLevels.get(enemyUUID);
                    BlockPos originalPos = enemyOriginalPositions.get(enemyUUID);
                    Vec2 originalRot = enemyOriginalRot.get(enemyUUID);

                    if (originalLevel != null && originalPos != null && originalRot != null) {
                        enemy.teleportTo(
                                originalLevel,
                                originalPos.getX(), originalPos.getY(), originalPos.getZ(),
                                EnumSet.noneOf(RelativeMovement.class),
                                originalRot.x,
                                originalRot.y
                        );
                        LOGGER.info("Teleported enemy {} back to original position at ({}, {}, {})",
                                enemyUUID, originalPos.getX(), originalPos.getY(), originalPos.getZ());
                    }
                }
            }
        }
        
        // Reset chunks near (0, 0) in the combat dimension to clear the combat area
        if (combatServerLevel != null) {
            BlockPos resetCenter = new BlockPos(0, 0, 0);
            resetChunks(combatServerLevel, resetCenter, 3); // Reset 3 chunks radius around (0, 0)
            LOGGER.info("Reset chunks around (0, 0) in combat dimension");
        }

        sendEndCombatPacket(player);

        // Remove from active instances
        if (player != null) {
            activeCombatInstances.remove(player.getUUID());
        }
    }

    public static void removeCombatInstance(UUID playerUUID) {
        activeCombatInstances.remove(playerUUID);
    }
    
    /**
     * Get the combat instance for a player
     */
    public static CombatInstanceServer getCombatInstance(UUID playerUUID) {
        return activeCombatInstances.get(playerUUID);
    }
    
    /**
     * Check if an entity is an enemy in this combat instance
     */
    public boolean isEnemy(UUID entityUUID) {
        return enemyUUIDs.contains(entityUUID);
    }
    
    /**
     * Finish the current enemy's turn - freeze them and return to original position
     */
    public void finishEnemyTurn() {
        if (currentBattleEntity == null) {
            entityTurnFinished = true;
            return;
        }
        
        // If it's the player's turn, just mark as finished
        if (currentBattleEntity instanceof ServerPlayer && currentBattleEntity == player) {
            entityTurnFinished = true;
            return;
        }
        
        // Finish enemy turn
        Entity enemy = combatServerLevel.getEntity(currentBattleEntity.getUUID());
        if (enemy == null) {
            LOGGER.warn("Enemy {} not found in combat dimension when finishing turn", currentBattleEntity.getUUID());
            entityTurnFinished = true;
            return;
        }
        
        // Freeze the enemy
        if (enemy instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setDeltaMovement(0, 0, 0);
            mob.setTarget(null);
        }
        
        // Return to original combat position
        BlockPos originalPos = enemOnBattleOriginalPos.get(enemy.getUUID());
        if (originalPos != null) {
            LOGGER.info("Enemy {} current position before teleport back: ({}, {}, {})", 
                    enemy.getName().getString(), enemy.getX(), enemy.getY(), enemy.getZ());
            LOGGER.info("Teleporting enemy {} back to stored position: ({}, {}, {})", 
                    enemy.getName().getString(), originalPos.getX(), originalPos.getY(), originalPos.getZ());
            
            enemy.teleportTo(
                    combatServerLevel,
                    originalPos.getX(), originalPos.getY(), originalPos.getZ(),
                    EnumSet.noneOf(RelativeMovement.class),
                    enemy.getYRot(),
                    enemy.getXRot()
            );
            LOGGER.info("Finished turn for enemy {} - frozen and returned to position ({}, {}, {})", 
                    enemy.getName().getString(), originalPos.getX(), originalPos.getY(), originalPos.getZ());
        } else {
            LOGGER.warn("No original combat position found for enemy {} (UUID: {})", 
                    enemy.getName().getString(), enemy.getUUID());
        }
        
        // Re-add to queue for next round
        battleQueue.offer(enemy.getUUID());
        
        // Mark turn as finished
        entityTurnFinished = true;
        currentBattleEntity = null;
    }

    public static void sendStartCombatPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StartCombatPacket());
    }

    public static void sendEndCombatPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new EndCombatPacket());
    }
    
    /**
     * Resets chunks by clearing all entities and blocks in the specified area
     * @param level The server level
     * @param centerPos Center position of the area to reset
     * @param radiusXZ Radius in chunks (X and Z direction)
     */
    public static void resetChunks(ServerLevel level, BlockPos centerPos, int radiusXZ) {
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;
        
        for (int chunkX = centerChunkX - radiusXZ; chunkX <= centerChunkX + radiusXZ; chunkX++) {
            for (int chunkZ = centerChunkZ - radiusXZ; chunkZ <= centerChunkZ + radiusXZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, true);
                
                if (chunk != null) {
                    // Remove all entities from the chunk
                    // Get entities in the chunk area
                    int minX = chunkPos.getMinBlockX();
                    int maxX = chunkPos.getMaxBlockX();
                    int minZ = chunkPos.getMinBlockZ();
                    int maxZ = chunkPos.getMaxBlockZ();
                    
                    List<Entity> entities = level.getEntitiesOfClass(Entity.class, 
                        new AABB(minX, level.getMinBuildHeight(), minZ, 
                                 maxX, level.getMaxBuildHeight(), maxZ));
                    
                    for (Entity entity : entities) {
                        if (!(entity instanceof ServerPlayer)) { // Don't remove players
                            entity.remove(Entity.RemovalReason.DISCARDED);
                        }
                    }
                    
                    // Clear all blocks in the chunk (set to air)
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                                BlockPos pos = chunkPos.getBlockAt(x, y, z);
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }
        
        LOGGER.info("Reset chunks around {} with radius {}", centerPos, radiusXZ);
    }



    private void teleport(ServerPlayer player, ServerLevel targetLevel, List<Entity> toTeleport) {
        // Teleport player first - find the top of the structure
        double targetX = 0, targetZ = 0;

        double targetY = 80;
        
        player.teleportTo(
                targetLevel,
                PLAYER_SPAWN_POS.getX(), PLAYER_SPAWN_POS.getY(), PLAYER_SPAWN_POS.getZ(),
                EnumSet.noneOf(RelativeMovement.class),
                player.getYRot(),
                player.getXRot()
        );

        // Teleport each entity with relative offsets
        double initialPositionX;
        if (toTeleport.size() != 1) {
            initialPositionX = FIRST_ENEMY_SPAWN_POS.getX() - ENEMY_SEPARATION;
        }
        else initialPositionX = FIRST_ENEMY_SPAWN_POS.getX();

        LOGGER.info("Initial enemy position X: {}", initialPositionX);
        LOGGER.info("Number of entities to teleport: {}", toTeleport.size());
        for (Entity entity : toTeleport) {
            // Calculate the target position we're teleporting to
            double storeX = initialPositionX;
            double storeY = FIRST_ENEMY_SPAWN_POS.getY();
            double storeZ = FIRST_ENEMY_SPAWN_POS.getZ();
            
            LOGGER.info("Teleporting enemy {} to combat dimension at position: ({}, {}, {})", 
                    entity.getName().getString(), targetX, targetY, targetZ);

            entity.teleportTo(
                    targetLevel,
                    storeX, storeY, storeZ,
                    EnumSet.noneOf(RelativeMovement.class),
                    player.getYRot(),
                    player.getXRot()
            );
            
            // Store the position we're teleporting TO, not the entity's current position
            // (which might still be from the old dimension)
            BlockPos storedPos = new BlockPos((int) storeX, (int) storeY, (int) storeZ);
            enemOnBattleOriginalPos.put(entity.getUUID(), storedPos);
            LOGGER.info("Stored original combat position for enemy {}: ({}, {}, {})", 
                    entity.getName().getString(), storedPos.getX(), storedPos.getY(), storedPos.getZ());

            initialPositionX += ENEMY_SEPARATION;

        }
    }

}
