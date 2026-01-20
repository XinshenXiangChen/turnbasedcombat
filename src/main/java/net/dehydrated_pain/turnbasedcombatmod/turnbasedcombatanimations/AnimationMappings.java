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
            Map<String, ResourceLocation> skills
    ) {}


    public static Map<Item, WeaponAnimationSet> animationMappings = new HashMap<>();


    public static void init() {
        animationMappings.put(
                Items.TRIDENT,
                new WeaponAnimationSet(
                        // Epic Fight animation ID
                        Animations.TRIDENT_AUTO1,
                        Map.of(
                                "skill1", ResourceLocation.fromNamespaceAndPath("turnbasedcombatmod", "trident_skill_1")
                        )
                )
        );
    }
}
