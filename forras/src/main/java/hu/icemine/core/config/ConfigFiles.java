package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigFiles {

    public static final String[] NAMES = {"config", "messages_hu", "lobby", "modes", "combofly", "combat-tag", "parkour", "economy",
            "scoreboard", "contacts", "fyland", "market", "comboarena", "gens", "modelobbies", "duels", "ranks", "permissions", "bedwars"};

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> files = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public ConfigFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> loadAll() {
        problems.clear();
        for (String name : NAMES) {
            load(name);
        }
        return new ArrayList<>(problems);
    }

    public List<String> reload(String name) {
        problems.clear();
        load(name);
        return new ArrayList<>(problems);
    }

    public File folder() {
        return plugin.getDataFolder();
    }

    public YamlConfiguration get(String name) {
        YamlConfiguration config = files.get(name);
        if (config == null) {
            load(name);
            config = files.get(name);
        }
        return config;
    }

    public void save(String name) {
        YamlConfiguration config = files.get(name);
        if (config == null) {
            return;
        }
        try {
            config.save(new File(plugin.getDataFolder(), name + ".yml"));
        } catch (IOException exception) {
            plugin.getLogger().warning("Nem sikerült menteni: " + name + ".yml (" + exception.getMessage() + ")");
        }
    }

    public void report(List<String> found) {
        for (String line : found) {
            plugin.getLogger().warning("Config hiba: " + line);
        }
    }

    private void load(String name) {
        File file = new File(plugin.getDataFolder(), name + ".yml");
        if (!file.exists()) {
            try {
                plugin.saveResource(name + ".yml", false);
            } catch (IllegalArgumentException exception) {
                problems.add(name + ".yml: nincs alapértelmezett változat a pluginban");
            }
        }
        YamlConfiguration defaults = new YamlConfiguration();
        InputStream stream = plugin.getResource(name + ".yml");
        if (stream != null) {
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            } catch (IOException | InvalidConfigurationException exception) {
                problems.add(name + ".yml (beépített): " + exception.getMessage());
            }
        }
        YamlConfiguration config = new YamlConfiguration();
        if (file.exists()) {
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                problems.add(name + ".yml: hibás YAML, a beépített alapértékeket használom. Részletek: " + firstLine(exception.getMessage()));
                config = new YamlConfiguration();
            }
        }
        config.setDefaults(defaults);
        files.put(name, config);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "?";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
