package net.dehydrated_pain.turnbasedcombatmod.combat;

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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumSet;
import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class CombatInstanceServer {
    int turn_index = 0;

    ServerPlayer player;
    List<Entity> enemies;
    ServerLevel serverLevel;

    // (21, 1, 5 because all world are of size 42 spawn at height 0 and camera forces z=5 to avoid seeing  the edges of the battleground
    // TODO: make the calculation not hardcoded xD
    private static final BlockPos PLAYER_SPAWN_POS = new BlockPos(21, 1, 5);
    private static final BlockPos FIRST_ENEMY_SPAWN_POS = new BlockPos(21, 1, 20);
    private static final Integer ENEMY_SEPARATION = 4;

    public CombatInstanceServer(ServerPlayer _player, List<Entity> _enemies) {

        player = _player;

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));

        serverLevel = player.getServer().getLevel(dimKey);
        enemies = _enemies;

        setCombatEnvironment();
    }

    public void turnBasedCombat() {
    }

    /*
    setCombatEnvironment should create a carbon copy of the surroundings of the player, then teleport the player and the enemies into the world

    also store original player combat to teleport back
     */
    private void setCombatEnvironment() {
        Minecraft mc = Minecraft.getInstance();
        
        // Place structure when entering combat dimension
        StructurePlacer.place(serverLevel);

        for (Entity enemy: enemies) {
            if (enemy instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setDeltaMovement(0, 0, 0);
            }
        }

        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        teleport(player, serverLevel, enemies);

        LOGGER.info("send player combat packet");

        sendStartCombatPacket(player);

    }
    
    public static void sendStartCombatPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StartCombatPacket());
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
