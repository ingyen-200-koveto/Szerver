package hu.icemine.core.rank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * A ranks.yml rangjainak LuckPerms csoportjai: indításkor egyszer létrehozza őket (súly, név, előtag, szülő, jogok)
 * LuckPerms konzol parancsokkal. Amelyik csoport kész, azt beírja a .luckperms-kesz fájlba, és többet nem nyúl hozzá,
 * így a LuckPermsben utólag átírt beállítások megmaradnak.
 */
public final class LuckPermsSetup {

    static final String DONE_FILE = ".luckperms-kesz";

    private LuckPermsSetup() {
    }

    /** A parancsok, amik a még el nem készült csoportokat beállítják. Előbb minden csoport létrejön, utána a beállítások. */
    static List<String> commands(YamlConfiguration config, Set<String> done) {
        List<String> create = new ArrayList<>();
        List<String> setup = new ArrayList<>();
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        if (ranks == null) {
            return create;
        }
        for (String id : ranks.getKeys(false)) {
            ConfigurationSection rank = ranks.getConfigurationSection(id);
            ConfigurationSection lp = rank == null ? null : rank.getConfigurationSection("luckperms");
            if (lp == null) {
                continue;
            }
            String group = rank.getString("group", id).toLowerCase(Locale.ROOT).trim();
            if (!group.matches("[a-z0-9_\\-]+") || done.contains(group)) {
                continue;
            }
            if (!group.equals("default")) {
                create.add("lp creategroup " + group);
            }
            int weight = lp.getInt("weight", 0);
            String base = "lp group " + group + " ";
            setup.add(base + "setweight " + weight);
            String display = lp.getString("display", "");
            if (!display.isBlank()) {
                setup.add(base + "setdisplayname " + quote(display));
            }
            String prefix = lp.getString("prefix", "");
            if (!prefix.isBlank()) {
                setup.add(base + "meta setprefix " + Math.max(1, weight) + " " + quote(prefix));
            }
            String parent = lp.getString("parent", "").toLowerCase(Locale.ROOT).trim();
            if (parent.matches("[a-z0-9_\\-]+")) {
                setup.add(base + "parent add " + parent);
            }
            for (String permission : lp.getStringList("permissions")) {
                if (permission != null && permission.matches("[A-Za-z0-9_.*\\-]+")) {
                    setup.add(base + "permission set " + permission + " true");
                }
            }
        }
        create.addAll(setup);
        return create;
    }

    /** A csoportok, amiket ezek a parancsok beállítanak. */
    static Set<String> groups(YamlConfiguration config) {
        Set<String> groups = new LinkedHashSet<>();
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        if (ranks == null) {
            return groups;
        }
        for (String id : ranks.getKeys(false)) {
            ConfigurationSection rank = ranks.getConfigurationSection(id);
            if (rank != null && rank.isConfigurationSection("luckperms")) {
                groups.add(rank.getString("group", id).toLowerCase(Locale.ROOT).trim());
            }
        }
        return groups;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "'") + "\"";
    }

    public static void run(File dataFolder, YamlConfiguration config, Logger logger) {
        if (!config.getBoolean("luckperms-setup", true)) {
            return;
        }
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) {
            return;
        }
        File file = new File(dataFolder, DONE_FILE);
        Set<String> done = new LinkedHashSet<>();
        if (file.isFile()) {
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        done.add(line.trim().toLowerCase(Locale.ROOT));
                    }
                }
            } catch (IOException exception) {
                logger.warning("Nem tudtam olvasni: " + DONE_FILE + " (" + exception.getMessage() + ")");
                return;
            }
        }
        List<String> commands = commands(config, done);
        if (commands.isEmpty()) {
            return;
        }
        logger.info("LuckPerms: beállítom a rangokat a ranks.yml alapján (" + commands.size() + " parancs)...");
        for (String command : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                logger.warning("LuckPerms parancs hiba (" + command + "): " + exception.getMessage());
            }
        }
        done.addAll(groups(config));
        try {
            Files.write(file.toPath(), String.join("\n", done).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            logger.warning("Nem tudtam írni: " + DONE_FILE + " (" + exception.getMessage() + ")");
        }
    }
}
