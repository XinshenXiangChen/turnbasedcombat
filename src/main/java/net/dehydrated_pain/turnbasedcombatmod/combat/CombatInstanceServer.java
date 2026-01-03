package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
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
        // Teleport player first
        double targetX = 0, targetY = 80, targetZ = 0;
        player.teleportTo(
                targetLevel,
                targetX, targetY, targetZ,
                EnumSet.noneOf(RelativeMovement.class),
                player.getYRot(),
                player.getXRot()
        );

        // Teleport each entity with relative offsets
        for (Entity entity : toTeleport) {

            entity.teleportTo(
                    targetLevel,
                    targetX, targetY, targetZ,
                    EnumSet.noneOf(RelativeMovement.class),
                    player.getYRot(),
                    player.getXRot()
            );

        }
    }

}
