package hu.icemine.core.build;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public final class Placer implements Build.Sink {

    private final World world;
    private final Logger logger;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final BlockData air;
    private int placed;

    public Placer(World world, Logger logger) {
        this.world = world;
        this.logger = logger;
        this.air = Bukkit.createBlockData(Material.AIR);
    }

    public int placed() {
        return placed;
    }

    @Override
    public void set(int x, int y, int z, String data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        BlockData value = data(data);
        if (value.getMaterial().isAir() && block.getType().isAir()) {
            return;
        }
        block.setBlockData(value, data.startsWith("water"));
        placed++;
    }

    private BlockData data(String data) {
        BlockData result = cache.get(data);
        if (result == null) {
            try {
                result = Bukkit.createBlockData(data.indexOf(':') >= 0 ? data : "minecraft:" + data);
            } catch (IllegalArgumentException exception) {
                logger.warning("Ismeretlen blokk az építésben: " + data);
                result = Bukkit.createBlockData(Material.STONE);
            }
            cache.put(data, result);
        }
        return result;
    }

    public int clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        int count = 0;
        int minY = Math.max(world.getMinHeight(), Math.min(y1, y2));
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(y1, y2));
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        // chunkonként: ami üres (még sosem volt ott semmi), azt gyorsan átugorjuk
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!world.isChunkGenerated(cx, cz)) {
                    continue;
                }
                org.bukkit.ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int sectionIndex = sy - (world.getMinHeight() >> 4);
                    if (sectionIndex < 0 || snap.isSectionEmpty(sectionIndex)) {
                        continue;
                    }
                    for (int x = Math.max(minX, cx << 4); x <= Math.min(maxX, (cx << 4) + 15); x++) {
                        for (int z = Math.max(minZ, cz << 4); z <= Math.min(maxZ, (cz << 4) + 15); z++) {
                            for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
                                if (!snap.getBlockType(x & 15, y, z & 15).isAir()) {
                                    world.getBlockAt(x, y, z).setBlockData(air, false);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }
}
