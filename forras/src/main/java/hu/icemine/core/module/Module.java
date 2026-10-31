package hu.icemine.core.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class Module {
    private final JavaPlugin plugin;
    private final String name;
    private final String configName;
    private YamlConfiguration config;

    protected Module(JavaPlugin javaplugin, String s, String s1) {
        this.plugin = javaplugin;
        this.name = s;
        this.configName = s1;
    }

    public abstract void onEnable();

    public abstract void onDisable();

    public JavaPlugin plugin() {
        return this.plugin;
    }

    public String name() {
        return this.name;
    }

    private File configFile() {
        return new File(this.plugin.getDataFolder(), this.configName);
    }

    public File getDataFolder() {
        File file1 = new File(this.plugin.getDataFolder(), this.name);
        if (!file1.isDirectory()) {
            file1.mkdirs();
        }

        return file1;
    }

    public Logger getLogger() {
        return this.plugin.getLogger();
    }

    public Server getServer() {
        return this.plugin.getServer();
    }

    public PluginCommand getCommand(String s) {
        return this.plugin.getCommand(s);
    }

    public InputStream getResource(String s) {
        return this.plugin.getResource(s);
    }

    public void saveDefaultConfig() {
        File file1 = this.configFile();
        if (!file1.exists()) {
            try (InputStream inputstream = this.plugin.getResource(this.configName)) {
                if (inputstream != null) {
                    file1.getParentFile().mkdirs();
                    Files.copy(inputstream, file1.toPath());
                }
            } catch (IOException ioexception) {
                this.getLogger().warning("Nem sikerült létrehozni: " + this.configName + " (" + ioexception.getMessage() + ")");
            }
        }
    }

    public void reloadConfig() {
        YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(this.configFile());

        try (InputStream inputstream = this.plugin.getResource(this.configName)) {
            if (inputstream != null) {
                yamlconfiguration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(inputstream, StandardCharsets.UTF_8)));
            }
        } catch (IOException ioexception) {
        }

        this.config = yamlconfiguration;
    }

    public FileConfiguration getConfig() {
        if (this.config == null) {
            this.reloadConfig();
        }

        return this.config;
    }

    public void saveConfig() {
        try {
            this.getConfig().save(this.configFile());
        } catch (IOException ioexception) {
            this.getLogger().warning("Nem sikerült menteni: " + this.configName + " (" + ioexception.getMessage() + ")");
        }
    }
}
