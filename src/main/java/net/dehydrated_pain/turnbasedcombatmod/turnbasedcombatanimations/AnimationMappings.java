package net.dehydrated_pain.turnbasedcombatmod.turnbasedcombatanimations;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.gameasset.Animations;

import java.util.HashMap;
import java.util.Map;

public class AnimationMappings {
    public record WeaponAnimationSet(
            AnimationManager.AnimationAccessor<? extends AttackAnimation> animation,
            Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> skills
    ) {}


    public static Map<Item, WeaponAnimationSet> animationMappings = new HashMap<>();


    public static void init() {
        // Trident
        animationMappings.put(Items.TRIDENT, new WeaponAnimationSet(Animations.TRIDENT_AUTO1, Map.of("auto2", Animations.TRIDENT_AUTO2)));
        
        // Swords
        animationMappings.put(Items.WOODEN_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        animationMappings.put(Items.STONE_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        animationMappings.put(Items.IRON_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        animationMappings.put(Items.GOLDEN_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        animationMappings.put(Items.DIAMOND_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        animationMappings.put(Items.NETHERITE_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, Map.of()));
        
        // Axes
        animationMappings.put(Items.WOODEN_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        animationMappings.put(Items.STONE_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        animationMappings.put(Items.IRON_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        animationMappings.put(Items.GOLDEN_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        animationMappings.put(Items.DIAMOND_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        animationMappings.put(Items.NETHERITE_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, Map.of()));
        
        // Pickaxes
        animationMappings.put(Items.WOODEN_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.STONE_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.IRON_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.GOLDEN_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.DIAMOND_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.NETHERITE_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        
        // Shovels
        animationMappings.put(Items.WOODEN_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.STONE_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.IRON_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.GOLDEN_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.DIAMOND_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.NETHERITE_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        
        // Hoes
        animationMappings.put(Items.WOODEN_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.STONE_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.IRON_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.GOLDEN_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.DIAMOND_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        animationMappings.put(Items.NETHERITE_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
        
        // Mace
        animationMappings.put(Items.MACE, new WeaponAnimationSet(Animations.TOOL_AUTO1, Map.of()));
    }
}
