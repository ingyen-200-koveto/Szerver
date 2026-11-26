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
        for (String id : ranks.getKeys(false)) {
            ConfigurationSection rank = ranks.getConfigurationSection(id);
            ConfigurationSection lp = rank == null ? null : rank.getConfigurationSection("luckperms");
            if (lp == null) {
                continue;
            }
            String group = rank.getString("group", id).toLowerCase(Locale.ROOT).trim();
            String prefix = lp.getString("prefix", "");
            if (!group.matches("[a-z0-9_\\-]+") || !done.contains(group) || done.contains(prefixKey(group)) || prefix.isBlank()) {
                continue;
            }
            // az új, szebb rang előtagok: a már kész csoportok is megkapják egyszer
            String base = "lp group " + group + " ";
            create.add(base + "meta clear prefix");
            create.add(base + "meta setprefix " + Math.max(1, lp.getInt("weight", 0)) + " " + quote(prefix));
        }
        for (String[] extra : EXTRA) {
            if (!done.contains(extraKey(extra)) && (done.contains(extra[0]) || create.contains("lp creategroup " + extra[0]))) {
                create.add("lp group " + extra[0] + " permission set " + extra[1] + " true");
            }
        }
        return create;
    }

    /** Később bekerült jogok: a már kész csoportok is megkapják egyszer (GrimAC, LibertyBans). */
    static final String[][] EXTRA = {
            {"jnmoderator", "grim.alerts"},
            {"jnmoderator", "libertybans.mute.*"},
            {"jnmoderator", "libertybans.kick.*"},
            {"jnmoderator", "libertybans.warn.*"},
            {"jnmoderator", "libertybans.list.*"},
            {"moderator", "libertybans.ban.*"},
            {"moderator", "grim.verbose"},
            {"srmoderator", "libertybans.*"},
            {"admin", "grim.*"},
    };

    /** Ha a ranks.yml előtagjai újra megváltoznak, itt kell növelni, akkor a kész csoportok is újra megkapják. */
    static final String PREFIX_VERSION = "3";

    static String prefixKey(String group) {
        return "prefix:" + PREFIX_VERSION + ":" + group;
    }

    static String extraKey(String[] extra) {
        return "perm:" + extra[0] + ":" + extra[1];
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
                Bukkit.dispatchCommand(hu.icemine.core.util.SilentSender.get(), command);
            } catch (RuntimeException exception) {
                logger.warning("LuckPerms parancs hiba (" + command + "): " + exception.getMessage());
            }
        }
        done.addAll(groups(config));
        for (String group : groups(config)) {
            done.add(prefixKey(group));
        }
        for (String[] extra : EXTRA) {
            if (done.contains(extra[0])) {
                done.add(extraKey(extra).toLowerCase(Locale.ROOT));
            }
        }
        try {
            Files.write(file.toPath(), String.join("\n", done).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            logger.warning("Nem tudtam írni: " + DONE_FILE + " (" + exception.getMessage() + ")");
        }
    }
}
