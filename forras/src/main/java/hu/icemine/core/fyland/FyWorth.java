package hu.icemine.core.fyland;

import hu.icemine.core.util.Text;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Sziget érték és szint: a szigetre rakott értékes blokkok (fyland.yml: block-values) összértéke. Lerakáskor nő,
 * kiütéskor csökken; a /fy szint parancs az egész szigetet újraszámolja (a háttérszálon). Szint = érték / pont-per-szint + 1.
 */
final class FyWorth implements Listener {

    private final FylandMode mode;
    private final Map<Material, Long> values = new EnumMap<>(Material.class);
    private long pointsPerLevel = 100;
    private long recalcCooldown = 300_000L;

    FyWorth(FylandMode mode) {
        this.mode = mode;
    }

    void load(YamlConfiguration config, List<String> problems) {
        values.clear();
        pointsPerLevel = Math.max(1, config.getLong("island.level-points", 100));
        recalcCooldown = Math.max(0, config.getLong("island.recalc-cooldown-seconds", 300)) * 1000L;
        ConfigurationSection section = config.isSet("block-values") ? config.getConfigurationSection("block-values")
                : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection("block-values");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) {
                problems.add("fyland.yml -> block-values." + key + ": ismeretlen blokk");
                continue;
            }
            long value = section.getLong(key);
            if (value > 0) {
                values.put(material, value);
            }
        }
    }

    long value(Material material) {
        return values.getOrDefault(material, 0L);
    }

    Map<Material, Long> values() {
        return values;
    }

    int level(FyIsland island) {
        return (int) Math.min(Integer.MAX_VALUE, island.worth / pointsPerLevel + 1);
    }

    /** Ennyi érték kell még a következő szinthez. */
    long toNext(FyIsland island) {
        return pointsPerLevel - island.worth % pointsPerLevel;
    }

    void removed(FyIsland island, Material material) {
        long value = value(material);
        if (island != null && value > 0) {
            island.worth = Math.max(0, island.worth - value);
            island.dirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getWorld() != mode.world()) {
            return;
        }
        long value = value(block.getType());
        if (value <= 0) {
            return;
        }
        FyIsland island = mode.islandAt(block.getLocation());
        if (island != null) {
            if (event.getBlockReplacedState().getType() == block.getType()) {
                return;
            }
            island.worth += value;
            island.dirty = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != mode.world()) {
            return;
        }
        FyIsland island = mode.islandAt(block.getLocation());
        if (island != null && !(block.getX() == island.x && block.getY() == island.y && block.getZ() == island.z)) {
            removed(island, block.getType());
        }
    }

    /** /fy szint: a sziget értékének újraszámolása (a chunkokat a fő szálon fényképezzük, a számolás a háttérben fut). */
    void recalc(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            mode.send(player, "no-island");
            return;
        }
        long now = System.currentTimeMillis();
        long left = island.lastRecalc + recalcCooldown - now;
        if (left > 0 && !player.hasPermission("icemine.admin.fyland")) {
            mode.send(player, "level-cooldown", "%time%", Text.duration(left));
            return;
        }
        World world = mode.world();
        if (world == null) {
            return;
        }
        island.lastRecalc = now;
        int radius = mode.radius(island);
        int minX = island.x - radius;
        int maxX = island.x + radius;
        int minZ = island.z - radius;
        int maxZ = island.z + radius;
        List<ChunkSnapshot> snapshots = new java.util.ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                snapshots.add(world.getChunkAt(cx, cz).getChunkSnapshot(true, false, false));
            }
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        Map<Material, Long> table = new EnumMap<>(values);
        mode.send(player, "level-calculating");
        Bukkit.getScheduler().runTaskAsynchronously(mode.core(), () -> {
            long total = 0;
            for (ChunkSnapshot snapshot : snapshots) {
                int baseX = snapshot.getX() << 4;
                int baseZ = snapshot.getZ() << 4;
                for (int dx = 0; dx < 16; dx++) {
                    int x = baseX + dx;
                    if (x < minX || x > maxX) {
                        continue;
                    }
                    for (int dz = 0; dz < 16; dz++) {
                        int z = baseZ + dz;
                        if (z < minZ || z > maxZ) {
                            continue;
                        }
                        int top = Math.min(maxY - 1, snapshot.getHighestBlockYAt(dx, dz) + 1);
                        for (int y = minY; y <= top; y++) {
                            if (x == island.x && y == island.y && z == island.z) {
                                continue;
                            }
                            Long value = table.get(snapshot.getBlockType(dx, y, dz));
                            if (value != null) {
                                total += value;
                            }
                        }
                    }
                }
            }
            long result = total;
            Bukkit.getScheduler().runTask(mode.core(), () -> {
                long before = island.worth;
                island.worth = result;
                island.dirty = true;
                if (player.isOnline()) {
                    mode.send(player, "level-done", "%level%", String.valueOf(level(island)), "%worth%", Text.number(result),
                            "%change%", (result >= before ? "&a+" : "&c") + Text.number(result - before));
                }
            });
        });
    }
}
