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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class PlayerCombatEvents {

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (inCombatDimension(player)) return;

        // Find all living entities in the swing radius (example: 3 blocks around player)
        List<Entity> hitEntities = player.level().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(2),
                e -> e != player && e.isAlive());


        List<Entity> toTeleport = hitEntities.size() > 3 ? hitEntities.subList(0, 3) : hitEntities;

        // Create combat instance (it will register itself)
        // new CombatInstanceServer(player, toTeleport, player);

        // TODO: change, this is to test enemy attacking correctly
        new CombatInstanceServer(player, toTeleport, toTeleport.getFirst());
    }

    /**
     * whent he player gets attacked this method is called and the entity gains priority
     */
    @SubscribeEvent
    public static void onPlayerGetsAttacked() {

    }

    private static boolean inCombatDimension(ServerPlayer player) {
        // Target dimension
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));

        ServerLevel targetLevel = player.getServer().getLevel(dimKey);
        ServerLevel currentLevel = player.serverLevel();
        if (currentLevel == targetLevel) return true;
        return false;
    }

    /**
     * When attacked in the combat dimension
     */
    @SubscribeEvent
    public static void attackedInCombat(LivingIncomingDamageEvent event) {

    }








}
