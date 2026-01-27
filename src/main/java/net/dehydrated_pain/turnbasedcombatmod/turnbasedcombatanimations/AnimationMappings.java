package net.dehydrated_pain.turnbasedcombatmod.turnbasedcombatanimations;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;

import java.util.HashMap;
import java.util.Map;

public class AnimationMappings {
    public record WeaponAnimationSet(
            AnimationManager.AnimationAccessor<? extends AttackAnimation> animation,
            Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> skills,
            // Parry animations - TODO: Will probably have to change to extend AttackAnimation 
            AnimationManager.AnimationAccessor<? extends StaticAnimation> parry,      
            AnimationManager.AnimationAccessor<? extends StaticAnimation> parryJump,   
            AnimationManager.AnimationAccessor<? extends StaticAnimation> parryShift   
    ) {
        // Constructor with just attack animation and skills (no parry animations - uses null)
        public WeaponAnimationSet(
                AnimationManager.AnimationAccessor<? extends AttackAnimation> animation,
                Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> skills
        ) {
            // TODO: change the animation to proper parry animations
            this(animation, skills, animation, animation, animation);
        }
    }


    public static Map<Item, WeaponAnimationSet> animationMappings = new HashMap<>();


    public static void init() {
        // Trident - has auto1, auto2, auto3
        animationMappings.put(Items.TRIDENT, new WeaponAnimationSet(Animations.TRIDENT_AUTO1, Map.of(
                "auto2", Animations.TRIDENT_AUTO2,
                "auto3", Animations.TRIDENT_AUTO3
        )));
        
        // Swords - has auto1, auto2, auto3
        Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> swordSkills = Map.of(
                "auto2", Animations.SWORD_AUTO2,
                "auto3", Animations.SWORD_AUTO3
        );
        animationMappings.put(Items.WOODEN_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        animationMappings.put(Items.STONE_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        animationMappings.put(Items.IRON_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        animationMappings.put(Items.GOLDEN_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        animationMappings.put(Items.DIAMOND_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        animationMappings.put(Items.NETHERITE_SWORD, new WeaponAnimationSet(Animations.SWORD_AUTO1, swordSkills));
        
        // Axes - has auto1, auto2
        Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> axeSkills = Map.of(
                "auto2", Animations.AXE_AUTO2
        );
        animationMappings.put(Items.WOODEN_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        animationMappings.put(Items.STONE_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        animationMappings.put(Items.IRON_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        animationMappings.put(Items.GOLDEN_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        animationMappings.put(Items.DIAMOND_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        animationMappings.put(Items.NETHERITE_AXE, new WeaponAnimationSet(Animations.AXE_AUTO1, axeSkills));
        
        // Tools (Pickaxes, Shovels, Hoes) - has auto1, auto2
        Map<String, AnimationManager.AnimationAccessor<? extends AttackAnimation>> toolSkills = Map.of(
                "auto2", Animations.TOOL_AUTO2
        );
        
        // Pickaxes
        animationMappings.put(Items.WOODEN_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.STONE_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.IRON_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.GOLDEN_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.DIAMOND_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.NETHERITE_PICKAXE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        
        // Shovels
        animationMappings.put(Items.WOODEN_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.STONE_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.IRON_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.GOLDEN_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.DIAMOND_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.NETHERITE_SHOVEL, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        
        // Hoes
        animationMappings.put(Items.WOODEN_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.STONE_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.IRON_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.GOLDEN_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.DIAMOND_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        animationMappings.put(Items.NETHERITE_HOE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
        
        // Mace
        animationMappings.put(Items.MACE, new WeaponAnimationSet(Animations.TOOL_AUTO1, toolSkills));
    }
}
