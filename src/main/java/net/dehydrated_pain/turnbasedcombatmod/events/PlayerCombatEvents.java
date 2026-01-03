package net.dehydrated_pain.turnbasedcombatmod.events;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.rmi.registry.Registry;
import java.util.EnumSet;
import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class PlayerCombatEvents {

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Target dimension
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));
        ServerLevel targetLevel = player.getServer().getLevel(dimKey);
        if (targetLevel == null) return;

        ServerLevel currentLevel = player.serverLevel();
        if (currentLevel == targetLevel) return;

        // Find all living entities in the swing radius (example: 3 blocks around player)
        List<Entity> hitEntities = player.level().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(2),
                e -> e != player && e.isAlive());

        // Limit to 3 entities max
        List<Entity> toTeleport = hitEntities.size() > 3 ? hitEntities.subList(0, 3) : hitEntities;

        teleport(player, targetLevel, toTeleport);
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


    /*
    setCombatEnvironment should create a carbon copy of the surroundings of the player, then teleport the player and the enemies into the world

    also store original player combat to teleport back
     */
    private void setCombatEnvironment() {

    }



}
