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
    int turn_index = 0;

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
    private Map<UUID, double[]> enemyOriginalPositions = new HashMap<>(); // [x, y, z]
    private Map<UUID, Vec2> enemyOriginalRot = new HashMap<>();

    // (21, 1, 5 because all world are of size 42 spawn at height 0 and camera forces z=5 to avoid seeing  the edges of the battleground
    // TODO: make the calculation not hardcoded xD
    private static final BlockPos PLAYER_SPAWN_POS = new BlockPos(21, 1, 5);
    private static final BlockPos FIRST_ENEMY_SPAWN_POS = new BlockPos(21, 1, 20);
    private static final Integer ENEMY_SEPARATION = 4;

    public CombatInstanceServer(ServerPlayer _player, List<Entity> _enemies) {

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
        }
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
            enemyOriginalPositions.put(enemyUUID, new double[]{
                enemy.getX(), 
                enemy.getY(), 
                enemy.getZ()
            });
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
                    double[] originalPos = enemyOriginalPositions.get(enemyUUID);
                    Vec2 originalRot = enemyOriginalRot.get(enemyUUID);

                    if (originalLevel != null && originalPos != null && originalRot != null) {
                        enemy.teleportTo(
                                originalLevel,
                                originalPos[0], originalPos[1], originalPos[2],
                                EnumSet.noneOf(RelativeMovement.class),
                                originalRot.x,
                                originalRot.y
                        );
                        LOGGER.info("Teleported enemy {} back to original position at ({}, {}, {})",
                                enemyUUID, originalPos[0], originalPos[1], originalPos[2]);
                    }
                }
            }
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



    private static void teleport(ServerPlayer player, ServerLevel targetLevel, List<Entity> toTeleport) {
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

        LOGGER.info(String.valueOf(initialPositionX));
        LOGGER.info(String.valueOf(toTeleport.size()));
        for (Entity entity : toTeleport) {

            entity.teleportTo(
                    targetLevel,
                    initialPositionX, FIRST_ENEMY_SPAWN_POS.getY(), FIRST_ENEMY_SPAWN_POS.getZ(),
                    EnumSet.noneOf(RelativeMovement.class),
                    player.getYRot(),
                    player.getXRot()
            );
            initialPositionX += ENEMY_SEPARATION;

        }
    }

}
