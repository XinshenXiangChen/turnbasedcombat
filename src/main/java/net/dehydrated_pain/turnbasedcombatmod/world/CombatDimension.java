package net.dehydrated_pain.turnbasedcombatmod.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class CombatDimension {
    public static final ResourceKey<LevelStem> COMBATDIM_KEY = ResourceKey.create(Registries.LEVEL_STEM,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));
    public static final ResourceKey<Level> COMBATDIM_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));
    public static final ResourceKey<DimensionType> COMBATDIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));



}
