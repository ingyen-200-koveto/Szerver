package hu.icemine.core.creative;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** A Creative világ: végtelen telekrács. A telek fű (alatta föld és kő), az út sima kő, a telek körül félblokk szegély. */
final class PlotGenerator extends ChunkGenerator {

    final PlotGrid grid;
    final Material ground;
    final Material fill;
    final Material roadBlock;
    final Material border;

    PlotGenerator(PlotGrid grid, Material ground, Material fill, Material roadBlock, Material border) {
        this.grid = grid;
        this.ground = ground;
        this.fill = fill;
        this.roadBlock = roadBlock;
        this.border = border;
    }

    /** A telek egy oszlopa: mi legyen ezen a magasságon (a törlés is ezt használja). */
    Material column(int x, int y, int z) {
        int h = grid.height;
        if (y == h - 6) {
            return Material.BEDROCK;
        }
        if (y < h - 6) {
            return Material.AIR;
        }
        boolean plot = grid.plotAt(x, z) != PlotGrid.NONE;
        if (y <= h - 4) {
            return Material.STONE;
        }
        if (y < h) {
            return plot ? fill : Material.STONE;
        }
        if (y == h) {
            return plot ? ground : roadBlock;
        }
        if (y == h + 1 && !plot && grid.ring(x, z)) {
            return border;
        }
        return Material.AIR;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int h = grid.height;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = (chunkX << 4) + dx;
                int z = (chunkZ << 4) + dz;
                for (int y = h - 6; y <= h + 1; y++) {
                    Material material = column(x, y, z);
                    if (material != Material.AIR) {
                        data.setBlock(dx, y, dz, material);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, grid.road / 2 + 0.5, grid.height + 1, grid.road / 2 + 0.5);
    }
}
