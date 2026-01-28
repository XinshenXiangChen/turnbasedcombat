package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.*;
import net.dehydrated_pain.turnbasedcombatmod.utils.playerturn.EnemyInfo;
import net.dehydrated_pain.turnbasedcombatmod.structuregen.StructurePlacer;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.ParryTypes;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.SkillInfo;
import net.dehydrated_pain.turnbasedcombatmod.turnbasedcombatanimations.AnimationMappings;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;
import static net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;

@EventBusSubscriber(modid = MODID)
public class CombatInstanceServer {
    
    // Store active combat instances by player UUID
    private static final Map<UUID, CombatInstanceServer> activeCombatInstances = new ConcurrentHashMap<>();

    public ServerPlayer player;
    List<Mob> enemies;
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

    public boolean sucessfullParry = false;
    
    // Store pending damage information for parry mechanic
    private DamageSource pendingDamageSource = null;
    private float pendingDamageAmount = 0.0f;
    private boolean hasPendingDamage = false;
    private int pendingDamageTicks = 0;
    private static final int PARRY_RESPONSE_TIMEOUT_TICKS = 40; // 2 seconds at 20 TPS
    private ParryTypes pendingParryType = null; // Store the type of parry requested for animation

    private boolean isApplyingPendingDamage = false;

    // Delay after each attack before next turn (in ticks, 20 ticks = 1 second)
    private static final int POST_ATTACK_DELAY_TICKS = 30;
    private int postAttackDelayCounter = 0;

    // Delay after successful parry to let the parry counter-attack animation play (in ticks)
    private static final int PARRY_ANIMATION_DELAY_TICKS = 20; // 1 second for parry animation
    private int parryAnimationDelayCounter = 0;
    private boolean waitingForParryAnimation = false;

    private static final Integer ENEMY_SEPARATION = 3;

    // battle stuff
    private final BlockPos PLAYER_SPAWN_POS = new BlockPos(0, 1, -7);
    private final BlockPos FIRST_ENEMY_SPAWN_POS = new BlockPos(0, 1, 0);
    private int battlefieldSpawnY = 1;  // Calculated Y height for player spawn (highest block in battlefield)
    private Map<UUID, BlockPos> enemOnBattleOriginalPos = new HashMap<>();
    private Queue<UUID> battleQueue = new LinkedList<>();
    Entity currentBattleEntity;
    boolean entityTurnFinished = true;
    boolean hasSentPlayerTurnPacket = false;

     // Attack/Skill animation state
    private boolean isPerformingAttackAnimation = false;
    private boolean isSkillAnimation = false;
    private String animationSkillName = "";
    private int attackAnimationTicks = 0;
    private int calculatedAnimationTicks = 0;  // Calculated from Epic Fight animation duration
    private static final int TELEPORT_DELAY_TICKS = 10;   // Time to teleport to enemy (0.5 seconds = 10 ticks)
    private static final int ANIMATION_OFFSET_TICKS = 4; // Additional offset/delay after animation completes (0.2 seconds = 4 ticks)
    private double combatPosX, combatPosY, combatPosZ;   // Player's position in combat before attack
    private float combatYaw, combatPitch;
    private Entity attackTarget = null;
    

    public CombatInstanceServer(ServerPlayer _player, List<Mob> _enemies, Entity firstAttacker, Biome biome) {

        player = _player;

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));

        combatServerLevel = player.getServer().getLevel(dimKey);
        enemies = _enemies;
        // Store UUIDs for reliable entity lookup
        enemyUUIDs = _enemies.stream()
                .map(Entity::getUUID)
                .collect(Collectors.toList());

        setCombatEnvironment(biome);

        // build initial battle queue using UUIDs
        UUID firstAttackerUUID = firstAttacker.getUUID();

        if (firstAttacker instanceof ServerPlayer && firstAttacker == player) {
            battleQueue.offer(firstAttackerUUID);
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
            battleQueue.offer(player.getUUID());
        }

        // Register this instance
        activeCombatInstances.put(player.getUUID(), this);
    }
    
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Run turnBasedCombat() every tick for all active combat instances
        activeCombatInstances.values().forEach(CombatInstanceServer::turnBasedCombat);
        // Process attack animations
        activeCombatInstances.values().forEach(CombatInstanceServer::processAttackAnimation);
        // Process parry animation delays
        activeCombatInstances.values().forEach(CombatInstanceServer::processParryAnimationDelay);
        // Process pending damage timeouts
        activeCombatInstances.values().forEach(CombatInstanceServer::processPendingDamageTimeout);
        // Remove instances where combat has ended 
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

        // Wait for post-attack delay before starting next turn
        if (postAttackDelayCounter > 0) {
            postAttackDelayCounter--;
            return;
        }

        if (!entityTurnFinished && currentBattleEntity != null && currentBattleEntity.isAlive()) {
            if (currentBattleEntity instanceof ServerPlayer && currentBattleEntity == player) {
                // Player's turn - send packet if not already sent
                if (!hasSentPlayerTurnPacket) {
                    sendPlayerTurnPacket(player);
                    hasSentPlayerTurnPacket = true;
                }
                // Wait for player to attack (EndPlayerTurnPacket will finish the turn)
            } else {

                if (currentBattleEntity instanceof Mob mob && !waitingForParryAnimation) {
                    enemyAttack(currentBattleEntity);
                }
            }
        }
        else {
            UUID attackerUUID = battleQueue.poll();
            if (attackerUUID == null) return;
            // Look up the entity from the combat dimension using UUID
            currentBattleEntity = combatServerLevel.getEntity(attackerUUID);
            if (currentBattleEntity != null && currentBattleEntity.isAlive()) {
                entityTurnFinished = false;
                hasSentPlayerTurnPacket = false; // Reset flag for new turn
            } else {
                // Entity is dead or not found, just return (fail)
                LOGGER.warn("Entity {} is dead or not found, stopping turn processing", attackerUUID);

            }
        }

    }
    private void playerAttack() {
        LOGGER.info("playerturn");
        // Don't finish turn immediately - wait for player to attack
        // The turn will finish when EndPlayerTurnPacket is received
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
    }

    /**
     * Checks if all enemies are dead
     * @return true if all enemies are dead or removed
     */
    public boolean areAllEnemiesDead() {
        if (enemyUUIDs == null || enemyUUIDs.isEmpty()) {
            LOGGER.warn("areAllEnemiesDead: enemyUUIDs is null or empty");
            return true;
        }
        if (combatServerLevel == null) {
            LOGGER.warn("areAllEnemiesDead: combatServerLevel is null");
            return true;
        }
        
        int aliveCount = 0;
        int deadCount = 0;
        int notFoundCount = 0;

        for (UUID enemyUUID : enemyUUIDs) {
            Entity enemy = combatServerLevel.getEntity(enemyUUID);
            if (enemy == null) {
                notFoundCount++;
                LOGGER.warn("Enemy with UUID {} not found in combat dimension", enemyUUID);
            } else if (enemy.isAlive()) {
                aliveCount++;
                return false; // Found at least one alive enemy
            } else {
                deadCount++;
                LOGGER.warn("Enemy {} is dead", enemy.getName().getString());
            }
        }
        
        LOGGER.info("Enemy status: {} total, {} alive, {} dead, {} not found in combat dimension", 
                enemyUUIDs.size(), aliveCount, deadCount, notFoundCount);
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
    private void setCombatEnvironment(Biome biome) {

        Minecraft mc = Minecraft.getInstance();
        
        // Store original position and dimension
        originalLevel = player.serverLevel();
        originalX = player.getX();
        originalY = player.getY();
        originalZ = player.getZ();
        originalYRot = player.getYRot();
        originalXRot = player.getXRot();

        // Place structure when entering combat dimension
        StructurePlacer sp = new StructurePlacer(biome, combatServerLevel);
        sp.place();
        // Store original positions, levels, and rotations for all enemies
        for (Mob enemy: enemies) {
            if (enemy instanceof Mob mob) {
                UUID enemyUUID = enemy.getUUID();
                enemyOriginalLevels.put(enemyUUID, (ServerLevel) enemy.level());
                enemyOriginalPositions.put(enemyUUID, new BlockPos((int) enemy.getX(),(int) enemy.getY(), (int) enemy.getZ()));
                enemyOriginalRot.put(enemyUUID, new Vec2(enemy.getYRot(), enemy.getXRot()));


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
        
        // If player died, teleport back and restore ai
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
                        
                        // Restore AI for the enemy after teleporting back
                        if (enemy instanceof Mob mob) {
                            mob.setNoAi(false);

                        }
                        
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
        LOGGER.info(battleQueue.toString());
        battleQueue.offer(enemy.getUUID());
        
        // Mark turn as finished and start delay
        entityTurnFinished = true;
        currentBattleEntity = null;
        postAttackDelayCounter = POST_ATTACK_DELAY_TICKS;
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



    /**
     * Find the highest solid block at the given X, Z coordinates
     * @param level The level to search in
     * @param x X coordinate
     * @param z Z coordinate
     * @return The Y coordinate of the highest solid block + 1 (so entity spawns on top)
     */
    private int findHighestBlock(ServerLevel level, int x, int z) {
        // Start from the top and work down to find the highest solid block
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).blocksMotion()) {
                return y + 1; // Return Y + 1 so entity spawns on top of the block
            }
        }
        // If no solid block found, return a safe default
        return level.getMinBuildHeight() + 1;
    }
    
    private void teleport(ServerPlayer player, ServerLevel targetLevel, List<Mob> toTeleport) {
        // Find the highest block at player spawn position
        int playerX = PLAYER_SPAWN_POS.getX();
        int playerZ = PLAYER_SPAWN_POS.getZ();
        int playerY = findHighestBlock(targetLevel, playerX, playerZ);
        
        // Store the calculated Y height for use in parry and attack teleports
        battlefieldSpawnY = playerY;
        
        LOGGER.info("Player spawn position: ({}, {}, {}) - highest block at Y={}", 
                playerX, playerZ, playerY - 1, playerY);
        
        player.teleportTo(
                targetLevel,
                playerX, playerY, playerZ,
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
            UUID entityUUID = entity.getUUID();
            LOGGER.info("=== Teleporting enemy {} (UUID: {}) ===", entity.getName().getString(), entityUUID);
            LOGGER.info("Enemy alive before teleport: {}", entity.isAlive());
            LOGGER.info("Enemy dimension before teleport: {}", entity.level().dimension().location());
            
            // Calculate the target position we're teleporting to
            int storeX = (int) initialPositionX;
            int storeZ = FIRST_ENEMY_SPAWN_POS.getZ();
            // Find the highest block at this position
            int storeY = findHighestBlock(targetLevel, storeX, storeZ);
            
            LOGGER.info("Teleporting enemy {} to combat dimension at position: ({}, {}, {}) - highest block at Y={}", 
                    entity.getName().getString(), storeX, storeY, storeZ, storeY - 1);

            entity.teleportTo(
                    targetLevel,
                    storeX, storeY, storeZ,
                    EnumSet.noneOf(RelativeMovement.class),
                    player.getYRot(),
                    player.getXRot()
            );
            
            // Verify enemy after teleportation (on next tick)
            targetLevel.getServer().execute(() -> {
                targetLevel.getServer().execute(() -> {
                    Entity enemyAfterTP = targetLevel.getEntity(entityUUID);
                    if (enemyAfterTP != null) {
                        LOGGER.info("Enemy {} found in combat dimension after teleport", entity.getName().getString());
                        LOGGER.info("  - Alive: {}", enemyAfterTP.isAlive());
                        LOGGER.info("  - Position: ({}, {}, {})", enemyAfterTP.getX(), enemyAfterTP.getY(), enemyAfterTP.getZ());
                        LOGGER.info("  - Dimension: {}", enemyAfterTP.level().dimension().location());
                    } else {
                        LOGGER.error("Enemy {} NOT FOUND in combat dimension after teleport!", entity.getName().getString());
                    }
                });
            });
            
            // Store the position we're teleporting TO, not the entity's current position
            // (which might still be from the old dimension)
            BlockPos storedPos = new BlockPos(storeX, storeY, storeZ);
            enemOnBattleOriginalPos.put(entityUUID, storedPos);
            LOGGER.info("Stored original combat position for enemy {}: ({}, {}, {})", 
                    entity.getName().getString(), storedPos.getX(), storedPos.getY(), storedPos.getZ());

            initialPositionX += ENEMY_SEPARATION;
        }
        
        LOGGER.info("Finished teleporting {} enemies", toTeleport.size());
    }



    // send and recieve packets stuff
    public static void sendStartCombatPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StartCombatPacket());
    }

    public static void sendEndCombatPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new EndCombatPacket());
    }

    public void sendPlayerTurnPacket(ServerPlayer player) {
        // Get all alive enemies' info to send to the client
        List<EnemyInfo> enemyInfoList = new ArrayList<>();
        for (UUID enemyUUID : enemyUUIDs) {
            Entity enemy = combatServerLevel.getEntity(enemyUUID);
            if (enemy != null && enemy.isAlive()) {
                enemyInfoList.add(new EnemyInfo(enemyUUID, enemy.blockPosition()));
            }
        }
        
        // Get skills for the currently held weapon
        List<SkillInfo> skills = new ArrayList<>();
        Item heldItem = player.getMainHandItem().getItem();
        AnimationMappings.WeaponAnimationSet weaponSet = AnimationMappings.animationMappings.get(heldItem);
        if (weaponSet != null && weaponSet.skills() != null) {
            for (String skillName : weaponSet.skills().keySet()) {
                // For now, set hunger cost to null (will be configured later)
                skills.add(new SkillInfo(skillName, 1.));
            }
        }
        
        if (!enemyInfoList.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new PlayerTurnPacket(enemyInfoList, skills));
        } else {
            LOGGER.warn("No alive enemies found to send in PlayerTurnPacket");
        }
    }


    private void sendQTEPacketNetworkHandler(ServerPlayer player, ParryTypes parryType) {
        PacketDistributor.sendToPlayer(player, new QTERequestPacket(parryType));
    }


    public static void qteResponseNetworkHandler(final QTEResponsePacket pkt, final IPayloadContext context) {
        // Main thread work

        boolean success = pkt.success();
        boolean isParry = pkt.isParry();
        ServerPlayer player = (ServerPlayer) context.player();
        if (player == null) return;

        CombatInstanceServer instance = getCombatInstance(player.getUUID());
        if (instance != null) {
            String reactionType = isParry ? "parry" : "dodge";
            player.sendSystemMessage(Component.literal("Server register a " + success + " (" + reactionType + ")"));
            instance.sucessfullParry = success;

            if (instance.hasPendingDamage) {
                instance.pendingDamageTicks = 0;

                if (success) {
                    // Parry succeeded - cancel damage and trigger parry animation
                    LOGGER.info("Parry successful ({}) - damage cancelled", instance.pendingParryType);
                    instance.hasPendingDamage = false;
                    instance.pendingDamageSource = null;
                    instance.pendingDamageAmount = 0.0f;

                    if (instance.currentBattleEntity instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.setNoAi(true);
                        mob.setDeltaMovement(0, 0, 0);
                        
                        LOGGER.info("Disabled AI for {} during parry counter-attack", mob.getName().getString());
                    }
                    
                    // Play parry animation on server (handles hit detection and syncs to client)
                    if (instance.pendingParryType != null) {
                        instance.playParryAnimationOnServer(instance.pendingParryType);
                    }
                    instance.pendingParryType = null;
                    
                    // Start delay to let parry counter-attack animation play before enemy teleports back
                    instance.waitingForParryAnimation = true;
                    instance.parryAnimationDelayCounter = PARRY_ANIMATION_DELAY_TICKS;
                    LOGGER.info("Starting parry animation delay ({} ticks) before enemy teleports back", PARRY_ANIMATION_DELAY_TICKS);
                } else {
                    // Parry failed - apply stored damage
                    if (instance.pendingDamageSource != null && player.isAlive()) {
                        instance.isApplyingPendingDamage = true;
                        try {
                            player.hurt(instance.pendingDamageSource, instance.pendingDamageAmount);
                            LOGGER.info("Parry failed - applied {} damage to player", instance.pendingDamageAmount);
                        } finally {
                            instance.isApplyingPendingDamage = false;
                        }
                    }
                    instance.hasPendingDamage = false;
                    instance.pendingDamageSource = null;
                    instance.pendingDamageAmount = 0.0f;
                    
                    // Parry failed - finish enemy turn immediately
                    instance.finishEnemyTurn();
                }
            } else {
                // No pending damage - finish enemy turn immediately
                instance.finishEnemyTurn();
            }
        }
    }

    public static void endPlayerTurnNetworkHandler(final EndPlayerTurnPacket pkt, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            CombatInstanceServer instance = getCombatInstance(player.getUUID());
            if (instance != null) {
                int abilityIndex = pkt.abilityIndex();
                int enemyIndex = pkt.enemyIndex();
                String skillName = pkt.skillName();
                
                // Execute the player's action based on ability
                instance.executePlayerAction(abilityIndex, enemyIndex, skillName);
                
                // Finish player's turn and start delay
                instance.entityTurnFinished = true;
                instance.hasSentPlayerTurnPacket = false;
                instance.battleQueue.offer(player.getUUID());
                instance.postAttackDelayCounter = POST_ATTACK_DELAY_TICKS;
                LOGGER.info("Player {} finished their turn with ability {} on enemy {}", 
                    player.getName().getString(), abilityIndex, enemyIndex);
            }
        });
    }
    
    /**
     * Execute the player's action based on selected ability and target enemy.
     * @param abilityIndex 0=Attack, 1=Skill, 2=Item
     * @param enemyIndex index of the target enemy
     * @param skillName name of the skill if abilityIndex is SKILL, empty string otherwise
     */
    private void executePlayerAction(int abilityIndex, int enemyIndex, String skillName) {
        // Get the target enemy
        Entity targetEnemy = getEnemyByIndex(enemyIndex);
        if (targetEnemy == null) {
            LOGGER.warn("Target enemy at index {} not found", enemyIndex);
            return;
        }
        
        switch (abilityIndex) {
            case EndPlayerTurnPacket.ABILITY_ATTACK -> {
                // Basic attack - deal damage to the enemy
                performPlayerAttack(targetEnemy);
            }
            case EndPlayerTurnPacket.ABILITY_SKILL -> {
                // Skill - teleport and use skill animation
                performPlayerSkill(targetEnemy, skillName);
            }
            case EndPlayerTurnPacket.ABILITY_ITEM -> {
                // Item - placeholder for item usage
                performPlayerItem(targetEnemy);
            }
            default -> LOGGER.warn("Unknown ability index: {}", abilityIndex);
        }
    }
    
    /**
     * Get an enemy entity by its index in the alive enemies list.
     */
    private Entity getEnemyByIndex(int index) {
        List<Entity> aliveEnemies = new ArrayList<>();
        for (UUID enemyUUID : enemyUUIDs) {
            Entity enemy = combatServerLevel.getEntity(enemyUUID);
            if (enemy != null && enemy.isAlive()) {
                aliveEnemies.add(enemy);
            }
        }
        
        if (index >= 0 && index < aliveEnemies.size()) {
            return aliveEnemies.get(index);
        }
        return null;
    }
    
   
    /**
     * Get the animation duration in ticks from Epic Fight animation.
     * @param isSkill Whether this is a skill animation
     * @param skillName Name of the skill (if isSkill is true)
     * @return Animation duration in ticks, or default value if animation not found
     */
    private int getAnimationDurationTicks(boolean isSkill, String skillName) {
        Item heldItem = player.getMainHandItem().getItem();
        AnimationMappings.WeaponAnimationSet weaponSet = AnimationMappings.animationMappings.get(heldItem);
        
        if (weaponSet == null) {
            LOGGER.warn("No animation mapping found for item: {}", heldItem);
            return 20; // Default to 1 second (20 ticks)
        }
        
        AnimationManager.AnimationAccessor<? extends AttackAnimation> animationAccessor;
        if (isSkill) {
            animationAccessor = weaponSet.skills().get(skillName);
            if (animationAccessor == null) {
                LOGGER.warn("No skill animation found for skill: {} with item: {}", skillName, heldItem);
                return 20; // Default to 1 second
            }
        } else {
            animationAccessor = weaponSet.animation();
            if (animationAccessor == null) {
                LOGGER.warn("No attack animation found for item: {}", heldItem);
                return 20; // Default to 1 second
            }
        }
        
        try {
            // Get the actual animation object from the accessor
            AttackAnimation animation = animationAccessor.get();
            if (animation != null) {
                float totalTimeSeconds = animation.getTotalTime();
                int totalTicks = Math.round(totalTimeSeconds * 20.0f);
                LOGGER.info("Animation duration: {} seconds ({} ticks) for {} {}", 
                    totalTimeSeconds, totalTicks, isSkill ? "skill" : "attack", isSkill ? skillName : "");
                return totalTicks;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting animation duration: {}", e.getMessage());
        }
        
        return 20; // Default fallback
    }
    
    /**
     * Perform a basic attack on the target enemy.
     * Teleports player to enemy, attacks, then teleports back.
     */
    private void performPlayerAttack(Entity target) {
        if (player == null || target == null) return;
        
        // Store player's combat position (where to return after attack)
        combatPosX = player.getX();
        combatPosY = player.getY();
        combatPosZ = player.getZ();
        combatYaw = player.getYRot();
        combatPitch = player.getXRot();
        attackTarget = target;
        
        // Calculate animation duration from Epic Fight animation
        calculatedAnimationTicks = getAnimationDurationTicks(false, "");
        
        // Calculate position in front of enemy (1.5 blocks away, facing the enemy)
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        double attackDistance = 1.5;
        double attackX = target.getX() - (dx / distance) * attackDistance;
        double attackZ = target.getZ() - (dz / distance) * attackDistance;
        double attackY = target.getY();
        
        // Calculate yaw to face the enemy
        float yaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        
        // Teleport player to attack position
        player.teleportTo(attackX, attackY, attackZ);
        
        // Start attack animation sequence
        isPerformingAttackAnimation = true;
        attackAnimationTicks = 0;
        
        LOGGER.info("Player dashing to attack {} (animation will take {} ticks)", target.getName().getString(), calculatedAnimationTicks);
    }
    
    /**
     * Process the attack/skill animation sequence (called from tick).
     * Uses Epic Fight animation duration + offset to determine when to return.
     */
    private void processAttackAnimation() {
        if (!isPerformingAttackAnimation || attackTarget == null) return;
        
        attackAnimationTicks++;
        
        // At TELEPORT_DELAY_TICKS, trigger the attack animation
        if (attackAnimationTicks == TELEPORT_DELAY_TICKS) {
            // Reset enemy invulnerability to ensure hit registers
            if (attackTarget instanceof LivingEntity livingTarget) {
                livingTarget.invulnerableTime = 0;
                livingTarget.hurtTime = 0;
            }
            
            // Play attack animation on server (handles hit detection and syncs to client)
            playAttackAnimationOnServer(isSkillAnimation, animationSkillName);
        }
        
        int totalTicksNeeded = TELEPORT_DELAY_TICKS + calculatedAnimationTicks + ANIMATION_OFFSET_TICKS;
        
        if (attackAnimationTicks >= totalTicksNeeded) {
            player.teleportTo(combatPosX, battlefieldSpawnY, combatPosZ);
            
            isPerformingAttackAnimation = false;
            isSkillAnimation = false;
            animationSkillName = "";
            attackAnimationTicks = 0;
            calculatedAnimationTicks = 0;
            attackTarget = null;
        }
    }
    
    /**
     * Play attack animation on server-side PlayerPatch.
     * This is required for Epic Fight's hit detection to work properly.
     */
    private void playAttackAnimationOnServer(boolean isSkill, String skillName) {
        var serverPatch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(
                player, yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
        
        if (serverPatch == null) return;
        
        var weaponSet = AnimationMappings.animationMappings.get(player.getMainHandItem().getItem());
        if (weaponSet == null) return;
        
        if (isSkill) {
            var skillAnim = weaponSet.skills().get(skillName);
            if (skillAnim != null) {
                serverPatch.playAnimationSynchronized(skillAnim, 0);
            }
        } else {
            serverPatch.playAnimationSynchronized(weaponSet.animation(), 0);
        }
    }
    
    /**
     * Play parry animation on server-side PlayerPatch.
     * This is required for Epic Fight's hit detection to work properly.
     */
    private void playParryAnimationOnServer(ParryTypes parryType) {
        var serverPatch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(
                player, yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
        
        if (serverPatch == null) return;
        
        var weaponSet = AnimationMappings.animationMappings.get(player.getMainHandItem().getItem());
        if (weaponSet == null) return;
        
        var parryAnimation = switch (parryType) {
            case PARRY -> weaponSet.parry();
            case JUMP -> weaponSet.parryJump();
            case CROUCH -> weaponSet.parryShift();
        };
        
        if (parryAnimation != null) {
            serverPatch.playAnimationSynchronized(parryAnimation, 0);
        }
    }
    
    /**
     * Process parry animation delay - waits for the parry counter-attack animation to complete
     * before teleporting the player and enemy back to their positions.
     */
    private void processParryAnimationDelay() {
        if (!waitingForParryAnimation) return;
        
        parryAnimationDelayCounter--;
        
        if (parryAnimationDelayCounter <= 0) {
            // Delay complete - teleport player back to spawn position
            if (player != null && player.isAlive()) {
                player.teleportTo(PLAYER_SPAWN_POS.getX(), battlefieldSpawnY, PLAYER_SPAWN_POS.getZ());
            }

            // Now finish the enemy turn (teleport enemy back)
            waitingForParryAnimation = false;
            parryAnimationDelayCounter = 0;
            LOGGER.info("Parry animation delay complete - finishing enemy turn");
            finishEnemyTurn();
        }
    }
    
    /**
     * Perform a skill on the target enemy.
     * Teleports player to enemy, uses skill, then teleports back.
     */
    private void performPlayerSkill(Entity target, String skillName) {
        if (player == null || target == null) return;
        
        // Store player's combat position (where to return after skill)
        combatPosX = player.getX();
        combatPosY = player.getY();
        combatPosZ = player.getZ();
        combatYaw = player.getYRot();
        combatPitch = player.getXRot();
        attackTarget = target;
        isSkillAnimation = true;
        animationSkillName = skillName;
        
        // Calculate animation duration from Epic Fight animation
        calculatedAnimationTicks = getAnimationDurationTicks(true, skillName);
        
        // Calculate position in front of enemy (1.5 blocks away, facing the enemy)
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        double attackDistance = 1.5;
        double attackX = target.getX() - (dx / distance) * attackDistance;
        double attackZ = target.getZ() - (dz / distance) * attackDistance;
        double attackY = target.getY();
        
        // Calculate yaw to face the enemy
        float yaw = (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
        
        // Teleport player to attack position
        player.teleportTo(attackX, attackY, attackZ);
        
        // Start skill animation sequence
        isPerformingAttackAnimation = true;
        attackAnimationTicks = 0;
        
        LOGGER.info("Player dashing to use skill {} on {} (animation will take {} ticks)", 
            skillName, target.getName().getString(), calculatedAnimationTicks);
    }
    
    /**
     * Use an item on the target (or self).
     */
    private void performPlayerItem(Entity target) {

        player.heal(4.0f);
        LOGGER.info("Player used item, healed for 4 HP");
    }
    /**
     * Store pending damage information for parry mechanic
     */
    public void storePendingDamage(DamageSource damageSource, float damageAmount, ParryTypes parryType) {
        this.pendingDamageSource = damageSource;
        this.pendingDamageAmount = damageAmount;
        this.hasPendingDamage = true;
        this.pendingDamageTicks = PARRY_RESPONSE_TIMEOUT_TICKS;
        this.pendingParryType = parryType;
        this.sucessfullParry = false; // Reset parry state for new attack
    }
    
    /**
     * Check if we're currently applying pending damage (to prevent event handler from canceling it)
     */
    public boolean isApplyingPendingDamage() {
        return isApplyingPendingDamage;
    }
    
    /**
     * Process pending damage timeout - if player doesn't respond to QTE in time, apply damage
     */
    private void processPendingDamageTimeout() {
        if (hasPendingDamage && pendingDamageTicks > 0) {
            pendingDamageTicks--;
            if (pendingDamageTicks <= 0) {
                // Timeout - apply damage if parry didn't succeed
                if (!sucessfullParry && pendingDamageSource != null && player != null && player.isAlive()) {
                    isApplyingPendingDamage = true;
                    try {
                        player.hurt(pendingDamageSource, pendingDamageAmount);
                        LOGGER.info("Parry QTE timeout - applied {} damage to player", pendingDamageAmount);
                    } finally {
                        isApplyingPendingDamage = false;
                    }
                } else if (sucessfullParry) {
                    LOGGER.info("Parry QTE timeout but parry was successful - damage cancelled");
                }
                // Clear pending damage
                hasPendingDamage = false;
                pendingDamageSource = null;
                pendingDamageAmount = 0.0f;
                pendingDamageTicks = 0;
            }
        }
    }
}


