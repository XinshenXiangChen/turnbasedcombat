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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
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
        List<Mob> hitEntities = player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(2));

        if (hitEntities.isEmpty()) return;

        List<Mob> toTeleport = hitEntities.size() > 3 ? hitEntities.subList(0, 3) : hitEntities;

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
        List<Mob> toTeleport = new java.util.ArrayList<>();

        toTeleport.add((Mob) attacker);

        List<Mob> nearbyEntities = player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(2));

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
        
        // Damage from the rehurt event (parry mechanic) never hit if this if doesnt exist
        if (combatInstance.isApplyingPendingDamage()) return;

        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;

        if (!combatInstance.isEnemy(attacker.getUUID())) return;

        event.setCanceled(true);
        
        // TODO: get the attack type somehow probably register all possible attacks and their parry type
        ParryTypes parryType = ParryTypes.JUMP; // Default for now
        
        combatInstance.storePendingDamage(event.getSource(), event.getAmount(), parryType);
        
        LOGGER.info("Enemy attacked player, initiating parry QTE. Damage: {}, ParryType: {}", event.getAmount(), parryType);

        PacketDistributor.sendToPlayer(player, new QTERequestPacket(parryType));
    }

    

    /**
     * Block item dropping/throwing during combat
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        
        if (inCombatDimension(player)) {
            // Cancel the toss - this prevents the item entity from spawning
            event.setCanceled(true);
            
            // The item was already removed from inventory, so we need to give it back
            ItemStack droppedItem = event.getEntity().getItem();
            if (!droppedItem.isEmpty()) {
                // Try to add back to inventory, or it will be lost
                if (!player.getInventory().add(droppedItem)) {
                    // If inventory is full, just let it drop (shouldn't happen normally)
                    event.setCanceled(false);
                }
            }
            LOGGER.debug("Blocked item toss in combat dimension, returned item to inventory");
        }
    }
    
    /**
     * Block item pickup during combat (optional, but keeps combat clean)
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        
        if (inCombatDimension(player)) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    /**
     * Disable all knockback on the player in the combat dimension
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        if (inCombatDimension(player)) {
            event.setCanceled(true);
        }
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
