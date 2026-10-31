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
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setBlockData(air, false);
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
