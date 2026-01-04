package net.dehydrated_pain.turnbasedcombatmod.events;

import net.dehydrated_pain.turnbasedcombatmod.combat.CombatInstanceServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

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

        // Create combat instance (it will register itself)
        new CombatInstanceServer(player, toTeleport);
    }








}
