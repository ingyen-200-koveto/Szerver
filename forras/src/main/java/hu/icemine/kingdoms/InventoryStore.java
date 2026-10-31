package hu.icemine.kingdoms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class InventoryStore {

    private final File folder;
    private final Logger logger;

    InventoryStore(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Nem tudtam létrehozni a mappát: " + folder.getPath());
        }
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    boolean has(UUID id) {
        return file(id).exists();
    }

    void save(Player player) {
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
        try {
            yaml.save(file(player.getUniqueId()));
        } catch (IOException exception) {
            logger.warning("Nem sikerült menteni " + player.getName() + " cuccait: " + exception.getMessage());
        }
    }

    boolean restore(Player player) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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
            player.setHealth(Math.max(1.0, Math.min(20.0, yaml.getDouble("health", 20.0))));
        } catch (IllegalArgumentException ignored) {
        }
        player.setFoodLevel(yaml.getInt("food", 20));
        player.setLevel(yaml.getInt("level", 0));
        player.setExp((float) Math.max(0.0, Math.min(0.99, yaml.getDouble("exp", 0.0))));
        return true;
    }

    Location location(UUID id, World world) {
        File file = file(id);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!world.getName().equals(yaml.getString("location.world", ""))) {
            return null;
        }
        return new Location(world, yaml.getDouble("location.x", 0), yaml.getDouble("location.y", 0), yaml.getDouble("location.z", 0),
                (float) yaml.getDouble("location.yaw", 0), (float) yaml.getDouble("location.pitch", 0));
    }
}
