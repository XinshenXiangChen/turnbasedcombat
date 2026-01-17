package net.dehydrated_pain.turnbasedcombatmod.events;

import net.dehydrated_pain.turnbasedcombatmod.combat.CombatInstanceServer;
import net.dehydrated_pain.turnbasedcombatmod.network.QTERequestPacket;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.ParryTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

@EventBusSubscriber(modid = MODID)
public class PlayerCombatEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (inCombatDimension(player)) return;

        // Find all living entities in the swing radius (example: 3 blocks around player)
        List<Entity> hitEntities = player.level().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(2),
                e -> e != player && e.isAlive());

        if (hitEntities.isEmpty()) return;

        List<Entity> toTeleport = hitEntities.size() > 3 ? hitEntities.subList(0, 3) : hitEntities;

        BlockPos playerPos = player.blockPosition();
        Biome biome = player.serverLevel().getBiome(playerPos).value();

        // Cancel the attack event to prevent Epic Fight and other mods from processing it
        // We're handling combat in our turn-based system instead
        event.setCanceled(true);

        new CombatInstanceServer(player, toTeleport, player, biome);
    }

    /**
     * When player is attacked OUTSIDE combat dimension - start combat with attacker as first attacker
     */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttacked(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        if (inCombatDimension(player)) return;
        
        // Get the attacker (source entity)
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;

        if (!attacker.isAlive()) return;
        
        // Check if there's already a combat instance to avoid duplicates
        if (CombatInstanceServer.getCombatInstance(player.getUUID()) != null) return;

        // Cancel the damage event to prevent Epic Fight from processing it
        // We're handling combat in our turn-based system instead
        event.setCanceled(true);

        // Build list with attacker first, then nearby entities
        List<Entity> toTeleport = new java.util.ArrayList<>();
        toTeleport.add(attacker);

        List<Entity> nearbyEntities = player.level().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(2),
                e -> e != player && e != attacker && e.isAlive());

        toTeleport.addAll(nearbyEntities);

        if (toTeleport.size() > 3) {
            toTeleport = toTeleport.subList(0, 3);
        }

        BlockPos playerPos = player.blockPosition();
        Biome biome = player.serverLevel().getBiome(playerPos).value();

        new CombatInstanceServer(player, toTeleport, attacker, biome);
    }
    
    /**
     * When attacked in the combat dimension - detect when enemy attacks player and initiate parry QTE
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private static void attackedInCombat(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!inCombatDimension(player)) return;
        
        CombatInstanceServer combatInstance = CombatInstanceServer.getCombatInstance(player.getUUID());
        if (combatInstance == null) return;
        
        // Skip if we're intentionally applying pending damage (to prevent infinite loop)
        if (combatInstance.isApplyingPendingDamage()) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;

        if (!combatInstance.isEnemy(attacker.getUUID())) return;

        event.setCanceled(true);
        combatInstance.storePendingDamage(event.getSource(), event.getAmount());
        
        LOGGER.info("Enemy attacked player, initiating parry QTE. Damage: {}", event.getAmount());

        // TODO: get the attack type somehow probably register all possible attacks and their parry type
        PacketDistributor.sendToPlayer(player, new QTERequestPacket(ParryTypes.JUMP));
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



}
