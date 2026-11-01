package hu.icemine.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Migrations {

    private Migrations() {
    }

    public static List<String> run(ConfigFiles files) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration modes = files.get("modes");
        if (modes.isSet("modes") && !modes.isSet("modes.combofly")) {
            copyDefault(modes, "modes.combofly");
            for (String mode : new String[]{"skypvp", "kitpvp"}) {
                if (modes.isSet("modes." + mode)) {
                    modes.set("modes." + mode + ".combofly", false);
                }
            }
            files.save("modes");
            notes.add("modes.yml: bekerült a ComboFly aréna, a SkyPvP-ben és a KitPvP-ben mostantól sima PvP van");
            YamlConfiguration combo = files.get("combofly");
            if (combo.isSet("allowed-modes")) {
                List<String> allowed = new ArrayList<>(combo.getStringList("allowed-modes"));
                allowed.remove("skypvp");
                allowed.remove("kitpvp");
                if (!allowed.contains("combofly")) {
                    allowed.add("combofly");
                }
                combo.set("allowed-modes", allowed);
                if (!combo.isSet("modes.combofly")) {
                    copyDefault(combo, "modes.combofly");
                }
                files.save("combofly");
                notes.add("combofly.yml: a ComboFly csak a saját arénájában működik");
            }
        }
        YamlConfiguration board = files.get("scoreboard");
        if (board.isSet("boards") && !board.isSet("boards.combofly")) {
            copyDefault(board, "boards.combofly");
            for (String mode : new String[]{"skypvp", "kitpvp"}) {
                if (board.isSet("boards." + mode)) {
                    List<String> lines = new ArrayList<>(board.getStringList("boards." + mode));
                    lines.removeIf(line -> line.contains("%combo%"));
                    board.set("boards." + mode, lines);
                }
            }
            files.save("scoreboard");
            notes.add("scoreboard.yml: új tábla a ComboFly arénának");
        }
        YamlConfiguration main = files.get("config");
        if (main.isSet("leaderboards.boards") && !main.isSet("leaderboards.boards.combokills")) {
            copyDefault(main, "leaderboards.boards.combokills");
            files.save("config");
            notes.add("config.yml: új toplista (ComboFly ölések)");
        }
        YamlConfiguration lobby = files.get("lobby");
        if (lobby.isSet("menus.stats.icons") && !lobby.isSet("menus.stats.icons.combokills")) {
            lobby.set("menus.stats.icons.combokills", "AMETHYST_SHARD");
            files.save("lobby");
        }
        YamlConfiguration messages = files.get("messages_hu");
        Configuration defaults = messages.getDefaults();
        if (defaults != null) {
            boolean changed = false;
            String[][] outdated = {{"combo.stats", "%kitpvp%"}, {"general.first-join", "kapuk"}, {"combo.help", "kapun"}, {"admin.help", "/im rank"},
                    {"admin.stats-header", "%rank%"}};
            for (String[] check : outdated) {
                if (!messages.isSet(check[0])) {
                    continue;
                }
                Object current = messages.get(check[0]);
                String text = current instanceof List ? String.join("\n", messages.getStringList(check[0])) : String.valueOf(current);
                if (text.contains(check[1])) {
                    messages.set(check[0], defaults.get(check[0]));
                    changed = true;
                }
            }
            if (changed) {
                files.save("messages_hu");
            }
        }
        YamlConfiguration arena = files.get("comboarena");
        if (arena != null && arena.isSet("kits") && !arena.getBoolean("migrated.pearls", false)) {
            for (String kit : new String[]{"jegkard", "tuske"}) {
                String path = "kits." + kit + ".items";
                if (arena.isSet(path)) {
                    List<String> items = new ArrayList<>(arena.getStringList(path));
                    boolean has = false;
                    for (String item : items) {
                        has |= item.trim().toUpperCase(java.util.Locale.ROOT).startsWith("ENDER_PEARL");
                    }
                    if (!has) {
                        items.add("ENDER_PEARL 3");
                        arena.set(path, items);
                    }
                }
            }
            if (!arena.isSet("pearl-cooldown")) {
                arena.set("pearl-cooldown", 15);
            }
            arena.set("migrated.pearls", true);
            files.save("comboarena");
            notes.add("comboarena.yml: a ComboFly kitekbe bekerült 3 enderpearl (15 mp várakozással)");
        }
        if (board.isSet("boards") && !board.isSet("titles.combofly")) {
            board.set("boards.combofly", board.getDefaults() == null ? board.getStringList("boards.combofly") : board.getDefaults().getStringList("boards.combofly"));
            board.set("titles.combofly", board.getDefaults() == null ? "&bIce&fMine &f| 2.0" : board.getDefaults().getString("titles.combofly"));
            if (!board.isSet("show-numbers")) {
                board.set("show-numbers", java.util.Arrays.asList("combofly"));
            }
            files.save("scoreboard");
            notes.add("scoreboard.yml: új ComboFly tábla");
        }
        for (String name : new String[]{"messages_hu", "scoreboard", "lobby", "contacts"}) {
            YamlConfiguration target = files.get(name);
            if (target == null || target.getDefaults() == null) {
                continue;
            }
            boolean branded = false;
            for (String key : target.getKeys(true)) {
                Object value = target.get(key);
                String text = value instanceof List ? String.join("\n", target.getStringList(key)) : value instanceof String ? (String) value : null;
                if (text == null || !(text.contains("ICE MINE") || text.contains("&6IceMine"))) {
                    continue;
                }
                Object fresh = target.getDefaults().get(key);
                if (fresh != null) {
                    target.set(key, fresh);
                    branded = true;
                }
            }
            if (branded) {
                files.save(name);
                notes.add(name + ".yml: az IceMine név új színekkel (Ice kék, Mine fehér)");
            }
        }
        String[][] discord = {{"contacts", "discord-url", "https://discord.gg/icemine"}, {"scoreboard", "discord", "discord.gg/icemine"}};
        for (String[] entry : discord) {
            YamlConfiguration target = files.get(entry[0]);
            if (target != null && target.getString(entry[1], "").contains("YJV2HNqTMc")) {
                target.set(entry[1], entry[2]);
                files.save(entry[0]);
                notes.add(entry[0] + ".yml: új Discord link (" + entry[2] + ")");
            }
        }
        YamlConfiguration lobbyFile = files.get("lobby");
        if (lobbyFile != null && lobbyFile.getDefaults() != null && !lobbyFile.getBoolean("migrated.info-v1", false)) {
            if ("menu:stats".equals(lobbyFile.getString("hotbar.items.stats.action", ""))) {
                lobbyFile.set("hotbar.items.stats", null);
            }
            if (!lobbyFile.isSet("info-texts")) {
                copyDefault(lobbyFile, "info-texts");
                copyDefault(lobbyFile, "info-hover");
            }
            for (String item : new String[]{"server", "rules", "commands"}) {
                String path = "menus.info.items." + item;
                if (lobbyFile.isSet(path) && !lobbyFile.isSet(path + ".action")) {
                    lobbyFile.set(path, null);
                    copyDefault(lobbyFile, path);
                }
            }
            if (lobbyFile.getString("hotbar.items.cosmetics.lore", "").contains("lobbyban") || lobbyFile.getStringList("hotbar.items.cosmetics.lore").toString().contains("lobbyban")) {
                lobbyFile.set("hotbar.items.cosmetics.lore", lobbyFile.getDefaults().getStringList("hotbar.items.cosmetics.lore"));
            }
            lobbyFile.set("migrated.info-v1", true);
            files.save("lobby");
            notes.add("lobby.yml: a toplisták kikerültek a lobbyból, az információkra rá lehet kattintani");
        }
        if (lobbyFile != null && lobbyFile.isSet("cosmetics") && !lobbyFile.isSet("cosmetics.kill-effects")) {
            copyDefault(lobbyFile, "cosmetics.kill-effects");
            copyDefault(lobbyFile, "cosmetics.disabled-worlds");
            files.save("lobby");
            notes.add("lobby.yml: új kozmetikák (ölés effektek), a nyomvonalak minden módban látszanak");
        }
        if (main != null && "parkour.best_ms".equals(main.getString("leaderboards.boards.parkour.key", ""))) {
            main.set("leaderboards.boards.parkour.key", "parkour2.best_ms");
            files.save("config");
            notes.add("config.yml: új parkour pálya, új rekordlista");
        }
        YamlConfiguration parkourFile = files.get("parkour");
        if (parkourFile != null && parkourFile.getInt("min-time-ms", 0) == 15000) {
            parkourFile.set("min-time-ms", 40000);
            parkourFile.set("fall-depth", 6);
            files.save("parkour");
        }
        if (board.isSet("boards") && !board.isSet("boards.modelobby")) {
            copyDefault(board, "boards.modelobby");
            files.save("scoreboard");
        }
        if (lobbyFile != null && lobbyFile.getDefaults() != null && lobbyFile.isSet("info-texts")) {
            ConfigurationSection texts = lobbyFile.getDefaults().getConfigurationSection("info-texts");
            boolean added = false;
            if (texts != null) {
                for (String key : texts.getKeys(false)) {
                    if (!lobbyFile.isSet("info-texts." + key)) {
                        lobbyFile.set("info-texts." + key, texts.get(key));
                        added = true;
                    }
                }
            }
            if (added) {
                files.save("lobby");
            }
        }
        if (modes.isSet("modes") && !modes.isSet("modes.duels")) {
            copyDefault(modes, "modes.duels");
            files.save("modes");
            notes.add("modes.yml: új játékmód: Duels");
        }
        if (board.isSet("boards") && !board.isSet("boards.duels")) {
            copyDefault(board, "boards.duels");
            files.save("scoreboard");
        }
        if (main.isSet("leaderboards.boards") && !main.isSet("leaderboards.boards.duelswins")) {
            copyDefault(main, "leaderboards.boards.duelswins");
            copyDefault(main, "leaderboards.boards.duelsstreak");
            copyDefault(main, "leaderboards.boards.duelselo");
            files.save("config");
        }
        YamlConfiguration lobbies = files.get("modelobbies");
        if (lobbies != null && lobbies.isSet("lobbies") && !lobbies.isSet("lobbies.duels")) {
            copyDefault(lobbies, "lobbies.duels");
            files.save("modelobbies");
        }
        if (modes.isSet("modes") && !modes.getBoolean("migrated.bedwars-v1", false)) {
            // a régi, külön pluginos Bedwars bejegyzései helyett az IceMine saját BedWars módja
            modes.set("modes.bedwars", null);
            copyDefault(modes, "modes.bedwars");
            modes.set("migrated.bedwars-v1", true);
            files.save("modes");
            if (main.isSet("leaderboards.boards")) {
                main.set("leaderboards.boards.bedwarswins", null);
                main.set("leaderboards.boards.bedwarsfinals", null);
                copyDefault(main, "leaderboards.boards.bedwarswins");
                copyDefault(main, "leaderboards.boards.bedwarsfinals");
                files.save("config");
            }
            if (lobbies != null && lobbies.isSet("lobbies")) {
                lobbies.set("lobbies.bedwars", null);
                copyDefault(lobbies, "lobbies.bedwars");
                files.save("modelobbies");
            }
            if (lobbyFile != null && lobbyFile.isConfigurationSection("npcs.soon_2") && !lobbyFile.isSet("npcs.mode_bedwars")) {
                ConfigurationSection location = lobbyFile.getConfigurationSection("npcs.soon_2.location");
                if (location != null) {
                    for (Map.Entry<String, Object> entry : location.getValues(true).entrySet()) {
                        if (!(entry.getValue() instanceof ConfigurationSection)) {
                            lobbyFile.set("npcs.mode_bedwars.location." + entry.getKey(), entry.getValue());
                        }
                    }
                }
                hu.icemine.core.lobby.LobbyDefaults.npc(lobbyFile, "mode_bedwars");
                lobbyFile.set("npcs.soon_2", null);
                files.save("lobby");
            }
            notes.add("új játékmód: BedWars (Solo, Duó, 3v3v3v3, 4v4v4v4), saját lobbyval és szoborral a lobbyban");
        }
        if (lobbies != null && lobbies.isConfigurationSection("lobbies.bedwars") && !lobbies.getBoolean("migrated.bedwars-statues-v1", false)) {
            if (!lobbies.isSet("lobbies.bedwars.npcs")) {
                copyDefault(lobbies, "lobbies.bedwars.npcs");
            }
            lobbies.set("lobbies.bedwars.built", false);
            lobbies.set("migrated.bedwars-statues-v1", true);
            files.save("modelobbies");
            notes.add("modelobbies.yml: a BedWars lobbyban négy szobor van (Solo, Duó, 3v3v3v3, 4v4v4v4), a lobby most újraépül");
        }
        if (resetIfContains(files, "modes", "modes.kitpvp.description", "öt kit")) {
            notes.add("modes.yml: új KitPvP leírás");
        }
        YamlConfiguration kitpvp = files.get("kitpvp");
        if (kitpvp != null && kitpvp.getDefaults() != null && kitpvp.isSet("kits") && !kitpvp.getBoolean("migrated.abilities-v1", false)) {
            ConfigurationSection defaultKits = kitpvp.getDefaults().getConfigurationSection("kits");
            if (defaultKits != null) {
                for (String id : defaultKits.getKeys(false)) {
                    if (!kitpvp.isSet("kits." + id)) {
                        copyDefault(kitpvp, "kits." + id);
                    } else if (!kitpvp.isSet("kits." + id + ".ability") && defaultKits.isSet(id + ".ability")) {
                        copyDefault(kitpvp, "kits." + id + ".ability");
                    }
                }
            }
            if (!kitpvp.isSet("upgrades")) {
                copyDefault(kitpvp, "upgrades");
            }
            kitpvp.set("migrated.abilities-v1", true);
            files.save("kitpvp");
            notes.add("kitpvp.yml: kit képességek, 4 új kit (Vadász, Ugró, Villámmágus, Jégkirály) és fejlesztések");
        }
        YamlConfiguration gensFile = files.get("gens");
        if (gensFile != null && gensFile.getDefaults() != null && gensFile.isSet("tiers") && !gensFile.getBoolean("migrated.progress-v1", false)) {
            for (String section : new String[]{"island-upgrades", "prestige", "rebirth", "autosell", "backpack"}) {
                if (!gensFile.isSet(section)) {
                    copyDefault(gensFile, section);
                }
            }
            gensFile.set("migrated.progress-v1", true);
            files.save("gens");
            notes.add("gens.yml: prestige, rebirth, sziget fejlesztések, automatikus eladás, hátizsák");
        }
        if (main.isSet("leaderboards.boards") && !main.isSet("leaderboards.boards.gensprestige")) {
            copyDefault(main, "leaderboards.boards.gensprestige");
            files.save("config");
        }
        if (lobbyFile != null && lobbyFile.isSet("menus.stats.icons") && !lobbyFile.isSet("menus.stats.icons.gensprestige")) {
            lobbyFile.set("menus.stats.icons.gensprestige", "NETHER_STAR");
            files.save("lobby");
        }
        if (lobbyFile != null && lobbyFile.isSet("hotbar.items") && !lobbyFile.getBoolean("migrated.mainmenu-v1", false)) {
            if (!lobbyFile.isSet("hotbar.items.main")) {
                boolean taken = false;
                ConfigurationSection items = lobbyFile.getConfigurationSection("hotbar.items");
                for (String key : items.getKeys(false)) {
                    if (items.getInt(key + ".slot", -1) == 4) {
                        taken = true;
                    }
                }
                if (!taken) {
                    copyDefault(lobbyFile, "hotbar.items.main");
                }
            }
            if (!lobbyFile.isSet("menus.main")) {
                copyDefault(lobbyFile, "menus.main");
            }
            lobbyFile.set("migrated.mainmenu-v1", true);
            files.save("lobby");
            notes.add("lobby.yml: főmenü a hotbaron (nether csillag)");
        }
        YamlConfiguration perms = files.get("permissions");
        if (perms != null && perms.getDefaults() != null) {
            boolean added = false;
            for (String section : new String[]{"commands", "icem"}) {
                ConfigurationSection permDefaults = perms.getDefaults().getConfigurationSection(section);
                if (permDefaults == null || !perms.isSet(section)) {
                    continue;
                }
                for (String key : permDefaults.getKeys(false)) {
                    if (!perms.isSet(section + "." + key)) {
                        copyDefault(perms, section + "." + key);
                        added = true;
                    }
                }
            }
            if (added) {
                files.save("permissions");
                notes.add("permissions.yml: új parancsok joga");
            }
        }
        YamlConfiguration modeLobbies = files.get("modelobbies");
        if (modeLobbies != null && modeLobbies.isSet("lobbies") && !modeLobbies.getBoolean("migrated.unique-v1", false)) {
            ConfigurationSection section = modeLobbies.getConfigurationSection("lobbies");
            java.util.Set<String> themes = java.util.Set.of("kitpvp", "skypvp", "combofly", "gens", "kingdoms", "duels");
            for (String key : section.getKeys(false)) {
                if (!modeLobbies.isSet("lobbies." + key + ".theme")) {
                    modeLobbies.set("lobbies." + key + ".theme", themes.contains(key) ? key : "snow");
                }
                modeLobbies.set("lobbies." + key + ".built", false);
            }
            modeLobbies.set("migrated.unique-v1", true);
            files.save("modelobbies");
            notes.add("modelobbies.yml: minden játékmód saját témájú lobbyt kap, most újraépülnek");
        }
        YamlConfiguration duelsFile = files.get("duels");
        if (duelsFile != null && duelsFile.isSet("arenas") && !duelsFile.getBoolean("migrated.arenas-v2", false)) {
            duelsFile.set("built", null);
            duelsFile.set("migrated.arenas-v2", true);
            files.save("duels");
            notes.add("duels.yml: új Duels arénák (lelátó, tornyok, fedezékek, sumo kert, Bridge szigetek), most újraépülnek");
        }
        YamlConfiguration combofly = files.get("combofly");
        if (refresh(combofly, "migrated.combo-v2", "knockback-horizontal", "knockback-vertical", "combo-scaling", "max-vertical", "max-height",
                "hit-delay-ticks", "damage-multiplier", "modes.combofly")) {
            files.save("combofly");
            notes.add("combofly.yml: kombós lökés, gyorsabb ütések (4 tick) és erősebb sebzés az arénában");
        }
        return notes;
    }

    static boolean resetIfContains(ConfigFiles files, String file, String path, String needle) {
        YamlConfiguration config = files.get(file);
        if (config == null || config.getDefaults() == null || !config.isSet(path)) {
            return false;
        }
        Object value = config.get(path);
        String text = value instanceof List ? String.join("\n", config.getStringList(path)) : String.valueOf(value);
        if (!text.contains(needle)) {
            return false;
        }
        config.set(path, config.getDefaults().get(path));
        files.save(file);
        return true;
    }

    static boolean refresh(YamlConfiguration config, String marker, String... paths) {
        if (config == null || config.getDefaults() == null || config.getBoolean(marker, false)) {
            return false;
        }
        for (String path : paths) {
            config.set(path, null);
            copyDefault(config, path);
        }
        config.set(marker, true);
        return true;
    }

    private static void copyDefault(YamlConfiguration config, String path) {
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        Object value = defaults.get(path);
        if (!(value instanceof ConfigurationSection)) {
            if (value != null) {
                config.set(path, value);
            }
            return;
        }
        ConfigurationSection section = (ConfigurationSection) value;
        for (String key : section.getKeys(true)) {
            Object leaf = section.get(key);
            if (!(leaf instanceof ConfigurationSection)) {
                config.set(path + "." + key, leaf);
            }
        }
    }
}
