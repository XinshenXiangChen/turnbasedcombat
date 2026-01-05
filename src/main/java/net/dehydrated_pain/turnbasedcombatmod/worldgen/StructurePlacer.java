package net.dehydrated_pain.turnbasedcombatmod.worldgen;

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
    private ResourceLocation STRUCTURE;

    double structureX, structureY;
    ServerLevel level;

    public StructurePlacer(Biome biome, ServerLevel level) {
        this.level = level;
        String biomeName = "supademacaco"; // Default fallback
        
        // Get the biome's ResourceLocation from the registry using RegistryAccess
        if (level != null) {
            RegistryAccess registryAccess = level.registryAccess();
            var biomeRegistry = registryAccess.registry(Registries.BIOME);
            
            if (biomeRegistry != null) {
                ResourceLocation biomeLocation = biomeRegistry.get().getKey(biome);
                if (biomeLocation != null) {
                    // Get the path part (e.g., "plains" from "minecraft:plains")
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

        // Create structure name based on biome (e.g., "plains_battleground")
        STRUCTURE = ResourceLocation.fromNamespaceAndPath(MODID, biomeName + "_battleground");
    }

    public void place() {
        place(new BlockPos(0, 0, 0));
    }

    public void place(BlockPos offset) {
        if (level == null) return;
        
        LOGGER.info("Getting structure template: {}", STRUCTURE);
        StructureTemplate template = level.getStructureManager().getOrCreate(STRUCTURE);
        
        // Get structure dimensions
        Vec3i size = template.getSize();
        int widthX = size.getX();   // Width in X direction
        int heightY = size.getY();  // Height in Y direction
        int depthZ = size.getZ();   // Depth/Width in Z direction
        
        LOGGER.info("Structure dimensions - Width (X): {}, Height (Y): {}, Depth (Z): {}", 
                widthX, heightY, depthZ);
        
        // Calculate placement position (centered at origin or use your desired position)
        // Structure is placed at the corner position, so if you want it centered:
        BlockPos pos = new BlockPos(
                (-widthX / 2) + offset.getX() ,  // Center X
                0,             // Y position (ground level)
                (-depthZ / 2)+ offset.getZ()   // Center Z
        );
        
        // Or if you want to place at a specific position:
        // BlockPos pos = new BlockPos(0, 0, 0);
        
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
