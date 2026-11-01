package hu.icemine.core.mode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InventoryStore {

    private final File root;
    private final Logger logger;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "IceMine-Inventory");
        thread.setDaemon(true);
        return thread;
    });

    public InventoryStore(File root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    private File file(String mode, UUID id) {
        return new File(new File(root, mode), id + ".yml");
    }

    public void save(String mode, Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("contents", new ArrayList<>(Arrays.asList(player.getInventory().getContents())));
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        Location location = player.getLocation();
        yaml.set("location.world", location.getWorld().getName());
        yaml.set("location.x", location.getX());
        yaml.set("location.y", location.getY());
        yaml.set("location.z", location.getZ());
        yaml.set("location.yaw", (double) location.getYaw());
        yaml.set("location.pitch", (double) location.getPitch());
        String data = yaml.saveToString();
        String key = mode + "/" + player.getUniqueId();
        cache.put(key, data);
        File target = file(mode, player.getUniqueId());
        writer.execute(() -> {
            write(target, data);
            cache.remove(key, data);
        });
    }

    private void write(File target, String data) {
        try {
            File folder = target.getParentFile();
            if (!folder.exists() && !folder.mkdirs()) {
                logger.warning("Nem tudtam létrehozni a mappát: " + folder.getPath());
                return;
            }
            File temp = new File(folder, target.getName() + ".tmp");
            Files.write(temp.toPath(), data.getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            logger.warning("Nem sikerült menteni a tárgyakat (" + target.getName() + "): " + exception.getMessage());
        }
    }

    private YamlConfiguration read(String mode, UUID id) {
        String key = mode + "/" + id;
        String data = cache.get(key);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (data != null) {
                yaml.loadFromString(data);
                return yaml;
            }
            File file = file(mode, id);
            if (!file.exists()) {
                return null;
            }
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Sérült tárgymentés (" + key + "): " + exception.getMessage());
            return null;
        }
    }

    public boolean restore(String mode, Player player) {
        YamlConfiguration yaml = read(mode, player.getUniqueId());
        if (yaml == null) {
            return false;
        }
        List<?> list = yaml.getList("contents");
        int size = player.getInventory().getContents().length;
        ItemStack[] contents = new ItemStack[size];
        if (list != null) {
            for (int i = 0; i < list.size() && i < size; i++) {
                Object value = list.get(i);
                contents[i] = value instanceof ItemStack ? (ItemStack) value : null;
            }
        }
        player.getInventory().setContents(contents);
        try {
            player.setHealth(Math.max(1.0, Math.min(player.getMaxHealth(), yaml.getDouble("health", 20.0))));
        } catch (IllegalArgumentException ignored) {
        }
        player.setFoodLevel(Math.max(0, Math.min(20, yaml.getInt("food", 20))));
        player.setLevel(Math.max(0, yaml.getInt("level", 0)));
        player.setExp((float) Math.max(0.0, Math.min(0.99, yaml.getDouble("exp", 0.0))));
        return true;
    }

    /** Tetszőleges tárgylista mentése (pl. a Gens hátizsák), ugyanabba a mappaszerkezetbe, aszinkron írással. */
    public void saveItems(String mode, UUID id, ItemStack[] items) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("contents", new ArrayList<>(Arrays.asList(items)));
        String data = yaml.saveToString();
        String key = mode + "/" + id;
        cache.put(key, data);
        File target = file(mode, id);
        writer.execute(() -> {
            write(target, data);
            cache.remove(key, data);
        });
    }

    public ItemStack[] loadItems(String mode, UUID id, int size) {
        ItemStack[] contents = new ItemStack[size];
        YamlConfiguration yaml = read(mode, id);
        if (yaml == null) {
            return contents;
        }
        List<?> list = yaml.getList("contents");
        if (list != null) {
            for (int i = 0; i < list.size() && i < size; i++) {
                Object value = list.get(i);
                contents[i] = value instanceof ItemStack ? (ItemStack) value : null;
            }
        }
        return contents;
    }

    public Location location(String mode, UUID id, World world) {
        YamlConfiguration yaml = read(mode, id);
        if (yaml == null || !world.getName().equals(yaml.getString("location.world", ""))) {
            return null;
        }
        return new Location(world, yaml.getDouble("location.x", 0), yaml.getDouble("location.y", 0), yaml.getDouble("location.z", 0),
                (float) yaml.getDouble("location.yaw", 0), (float) yaml.getDouble("location.pitch", 0));
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
