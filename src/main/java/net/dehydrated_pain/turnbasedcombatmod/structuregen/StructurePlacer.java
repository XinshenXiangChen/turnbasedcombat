package net.dehydrated_pain.turnbasedcombatmod.structuregen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;

public class StructurePlacer {

    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath(MODID, "combatdim");
    private ResourceLocation TEST_STRUCTURE = ResourceLocation.fromNamespaceAndPath(MODID, "test");
    private ResourceLocation STRUCTURE;

    double structureX, structureY;
    ServerLevel level;

    public StructurePlacer(Biome biome, ServerLevel level) {
        this.level = level;
        String biomeName = "test"; // Default fallback
        
        // Get the biome's ResourceLocation from the registry using RegistryAccess
        if (level != null) {
            RegistryAccess registryAccess = level.registryAccess();
            var biomeRegistry = registryAccess.registry(Registries.BIOME);
            
            if (biomeRegistry != null) {
                ResourceLocation biomeLocation = biomeRegistry.get().getKey(biome);
                if (biomeLocation != null) {

                    biomeName = biomeLocation.getPath();
                    LOGGER.info("Biome location: {}, Biome name: {}", biomeLocation, biomeName);
                } else {
                    LOGGER.warn("Could not find biome key in registry, using default name: {}", biomeName);
                }
            } else {
                LOGGER.warn("Biome registry not found, using default name: {}", biomeName);
            }
        } else {
            LOGGER.warn("ServerLevel is null, using default biome name: {}", biomeName);
        }


        STRUCTURE = ResourceLocation.fromNamespaceAndPath(MODID, biomeName);
    }

    public void place() {
        place(new BlockPos(0, 0, 0));
    }

    public void place(BlockPos offset) {
        if (level == null) return;

        LOGGER.info("Getting structure template: {}", STRUCTURE);
        StructureTemplate template = level.getStructureManager().get(STRUCTURE).orElse(null);

        if (template == null) {
            LOGGER.error("Failed to load structure from resources: {}. Make sure the file exists at data/{}/structures/{}.nbt",
                    STRUCTURE, MODID, STRUCTURE.getPath());
            return;
        }

        Vec3i size = template.getSize();
        int widthX = size.getX();
        int heightY = size.getY();
        int depthZ = size.getZ();
        
        LOGGER.info("Structure dimensions - Width (X): {}, Height (Y): {}, Depth (Z): {}", 
                widthX, heightY, depthZ);

        BlockPos pos = new BlockPos(
                (-widthX / 2) + offset.getX() ,
                -heightY + offset.getY(),
                (-depthZ / 2)+ offset.getZ()
        );
        

        
        LOGGER.info("=== STRUCTURE PLACEMENT ===");
        LOGGER.info("Structure: {}", STRUCTURE);
        LOGGER.info("Placing structure at position: X={}, Y={}, Z={}", pos.getX(), pos.getY(), pos.getZ());
        LOGGER.info("Structure dimensions: {}x{}x{} (Width x Height x Depth)", widthX, heightY, depthZ);
        LOGGER.info("Structure bounds: X[{}, {}], Y[{}, {}], Z[{}, {}]", 
                pos.getX(), pos.getX() + widthX - 1,
                pos.getY(), pos.getY() + heightY - 1,
                pos.getZ(), pos.getZ() + depthZ - 1);
        LOGGER.info("Loading chunk at chunk coordinates: ({}, {})", pos.getX() >> 4, pos.getZ() >> 4);
        level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
        
        template.placeInWorld(level, pos, pos, new StructurePlaceSettings(), level.random, 2);
        LOGGER.info("Structure placed successfully at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
    }
}
