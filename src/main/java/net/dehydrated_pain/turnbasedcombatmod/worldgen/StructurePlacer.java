package net.dehydrated_pain.turnbasedcombatmod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class StructurePlacer {

    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath(MODID, "combatdim");
    private static final ResourceLocation STRUCTURE = ResourceLocation.fromNamespaceAndPath(MODID, "plains_battleground");

    // (21, 1, 5 because all world are of size 42 spawn at height 0 and camera forces z=5 to avoid seeing  the edges of the battleground
    private static final BlockPos STRUCTURE_POS = new BlockPos(0, 0, 0);


    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LOGGER.info("LevelEvent.Load fired for dimension: {}", level.dimension().location());
        
        // Only place structure in combat dimension
        if (!level.dimension().location().equals(DIMENSION)) {
            LOGGER.info("Not combat dimension, skipping structure placement");
            return;
        }

        LOGGER.info("Combat dimension loaded, placing structure");
        place(level, STRUCTURE_POS);
    }


    public static void place(ServerLevel level) {
        place(level, STRUCTURE_POS);
    }
    
    public static void place(ServerLevel level, BlockPos pos) {
        if (level == null) return;
        
        try {
            LOGGER.info("Loading chunk at {}", pos);
            level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
            
            LOGGER.info("Getting structure template: {}", STRUCTURE);
            StructureTemplate template = level.getStructureManager().getOrCreate(STRUCTURE);
            
            LOGGER.info("Placing structure at {}", pos);
            template.placeInWorld(level, pos, pos, new StructurePlaceSettings(), level.random, 2);
            LOGGER.info("Structure placed successfully");
        } catch (Exception e) {
            LOGGER.error("Error placing structure: {}", e.getMessage(), e);
        }
    }
}
