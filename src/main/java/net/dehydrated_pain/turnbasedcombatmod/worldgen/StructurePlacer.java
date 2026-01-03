package net.dehydrated_pain.turnbasedcombatmod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class StructurePlacer {

    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim");

    private static final ResourceLocation STRUCTURE =
            ResourceLocation.fromNamespaceAndPath(MODID, "plains_battleground");

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().location().equals(DIMENSION)) return;

        place(level, new BlockPos(0, 80, 0));

    }

    private static void place(ServerLevel level, BlockPos pos) {
        StructureTemplate template =
                level.getStructureManager().getOrCreate(STRUCTURE);

        StructurePlaceSettings settings = new StructurePlaceSettings();

        template.placeInWorld(
                level,
                pos,
                pos,
                settings,
                level.random,
                2
        );
    }
}
