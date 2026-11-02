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
        if (modes.isSet("modes") && !modes.getBoolean("migrated.creative-v1", false)) {
            if (!modes.isSet("modes.creative")) {
                copyDefault(modes, "modes.creative");
            }
            modes.set("migrated.creative-v1", true);
            files.save("modes");
            notes.add("modes.yml: a játékmód választóba bekerült a Creative (hamarosan), új ikonok");
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
        if (gensFile != null && gensFile.getDefaults() != null && !gensFile.getBoolean("migrated.farm-v1", false)) {
            // generátorok helyett farm: a generátor és sziget szintek kikerülnek, jönnek a földek és a kapa
            for (String path : new String[]{"tiers", "levels", "generator-price", "starter-generators", "rebirth.extra-slots"}) {
                gensFile.set(path, null);
            }
            for (String section : new String[]{"fields", "hoe"}) {
                if (!gensFile.isSet(section)) {
                    copyDefault(gensFile, section);
                }
            }
            gensFile.set("quests", null);
            copyDefault(gensFile, "quests");
            gensFile.set("hub-built", false);
            gensFile.set("migrated.farm-v1", true);
            files.save("gens");
            notes.add("gens.yml: generátorok helyett farm (8 föld, fejleszthető kapa), a központi sziget újraépül");
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
            java.util.Set<String> themes = java.util.Set.of("fullpvp", "fyland", "combofly", "gens", "kingdoms", "duels");
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
        if (lobbyFile != null && lobbyFile.isSet("hotbar.items") && !lobbyFile.getBoolean("migrated.hotbar-v2", false)) {
            // a főmenü (nether csillag) és a kapcsolatok könyv kikerült a lobby hotbarjáról
            for (String item : new String[]{"main", "contacts"}) {
                lobbyFile.set("hotbar.items." + item, null);
            }
            lobbyFile.set("migrated.hotbar-v2", true);
            files.save("lobby");
            notes.add("lobby.yml: a főmenü és a kapcsolatok tárgy kikerült a hotbarról");
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
        notes.addAll(newModes(files));
        notes.addAll(hotbarV3(files::get, files::save));
        YamlConfiguration economyFile = files.get("economy");
        if (economyFile != null && economyFile.isSet("currencies") && !economyFile.isSet("currencies.dollar")) {
            copyDefault(economyFile, "currencies.dollar");
            files.save("economy");
            notes.add("economy.yml: új pénznem a Kingdomsnak (Dollár)");
        }
        YamlConfiguration fylandFile = files.get("fyland");
        if (fylandFile != null && fylandFile.isSet("phases")) {
            List<String> added = new java.util.ArrayList<>();
            for (String path : new String[]{"visit", "block-values", "battlefield", "airdrop", "enchanter", "island.level-points", "island.recalc-cooldown-seconds"}) {
                if (!fylandFile.isSet(path)) {
                    copyDefault(fylandFile, path);
                    added.add(path);
                }
            }
            if (!added.isEmpty()) {
                files.save("fyland");
                notes.add("fyland.yml: sziget érték és szint, csatatér, ellátmány láda és egyedi varázslatok (" + String.join(", ", added) + ")");
            }
        }
        YamlConfiguration scoreboardFile = files.get("scoreboard");
        if (scoreboardFile != null && scoreboardFile.isSet("boards") && !scoreboardFile.isSet("boards.creative")) {
            copyDefault(scoreboardFile, "boards.creative");
            files.save("scoreboard");
            notes.add("scoreboard.yml: új tábla a Creative módhoz");
        }
        YamlConfiguration ranksFile = files.get("ranks");
        if (newRanks(ranksFile)) {
            try {
                java.nio.file.Files.writeString(new java.io.File(files.folder(), "ranks.yml.regi").toPath(), previousRanks, java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException ignored) {
                // a mentés csak biztonsági másolat, nélküle is mehet tovább
            }
            files.save("ranks");
            notes.add("ranks.yml: új rangok (Bronz, Wizard, IceKing, Yeti és a csapat rangjai), a régi fájl: ranks.yml.regi");
        }
        return notes;
    }

    /** A régi azonosító, és ami helyette jön: a SkyPvP helyén Fyland, a KitPvP és a FullPvP megszűnt (a szobra "hamarosan" lesz). */
    static final String[][] REPLACED = {{"skypvp", "fyland"}, {"kitpvp", null}, {"fullpvp", null}};
    static final String MARKER = "migrated.modes-v2";
    static final String SOON_STATUE = "soon_2";

    /**
     * 3.11-3.12: a KitPvP, a SkyPvP és a FullPvP kikerült, a Fyland (OneBlock) bekerült. Minden configban rendet
     * rakunk (játékmód választó, mód lobbyk, főlobby szobrok, toplisták, scoreboard, rang kitek, pénznem).
     */
    static List<String> newModes(ConfigFiles files) {
        return newModes(files::get, files::save);
    }

    static List<String> newModes(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration modes = get.apply("modes");
        if (modes != null && modes.isSet("modes") && !modes.getBoolean(MARKER, false)) {
            for (String[] pair : REPLACED) {
                int slot = modes.getInt("modes." + pair[0] + ".slot", -1);
                modes.set("modes." + pair[0], null);
                if (pair[1] != null && !modes.isSet("modes." + pair[1])) {
                    copyDefault(modes, "modes." + pair[1]);
                    if (slot >= 0) {
                        modes.set("modes." + pair[1] + ".slot", slot);
                    }
                }
            }
            modes.set(MARKER, true);
            save.accept("modes");
            notes.add("modes.yml: a KitPvP, a SkyPvP és a FullPvP kikerült, a Fyland bekerült");
        }
        YamlConfiguration lobbies = get.apply("modelobbies");
        if (lobbies != null && lobbies.isSet("lobbies") && !lobbies.getBoolean(MARKER, false)) {
            for (String[] pair : REPLACED) {
                lobbies.set("lobbies." + pair[0], null);
                if (pair[1] != null && !lobbies.isSet("lobbies." + pair[1])) {
                    copyDefault(lobbies, "lobbies." + pair[1]);
                }
            }
            lobbies.set(MARKER, true);
            save.accept("modelobbies");
            notes.add("modelobbies.yml: a kivett módok lobbyja törölve, új lobby a Fylandnak");
        }
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby != null && !lobby.getBoolean(MARKER, false)) {
            for (String[] pair : REPLACED) {
                String oldNpc = "npcs.mode_" + pair[0];
                String newId = pair[1] == null ? SOON_STATUE : "mode_" + pair[1];
                String newNpc = "npcs." + newId;
                if (lobby.isSet(oldNpc + ".location") && !lobby.isSet(newNpc)) {
                    Object location = lobby.get(oldNpc + ".location");
                    ConfigurationSection where = location instanceof ConfigurationSection section ? section : null;
                    if (where != null) {
                        for (String key : where.getKeys(false)) {
                            lobby.set(newNpc + ".location." + key, where.get(key));
                        }
                    }
                    hu.icemine.core.lobby.LobbyDefaults.npc(lobby, newId);
                }
                lobby.set(oldNpc, null);
                ConfigurationSection holograms = lobby.isSet("holograms") ? lobby.getConfigurationSection("holograms") : null;
                if (holograms != null) {
                    for (String key : holograms.getKeys(false)) {
                        if (key.equals("portal_" + pair[0])) {
                            lobby.set("holograms." + key, null);
                        } else if (pair[0].equals(lobby.getString("holograms." + key + ".target"))) {
                            if (pair[1] == null) {
                                lobby.set("holograms." + key, null);
                            } else {
                                lobby.set("holograms." + key + ".target", pair[1]);
                            }
                        }
                    }
                }
                lobby.set("portals." + pair[0], null);
                if (lobby.isSet("menus.stats.icons")) {
                    lobby.set("menus.stats.icons." + pair[0], null);
                    if (pair[1] != null) {
                        copyDefault(lobby, "menus.stats.icons." + pair[1]);
                    }
                }
                if (lobby.isSet("info-texts")) {
                    lobby.set("info-texts.mode_" + pair[0], null);
                    if (pair[1] != null) {
                        copyDefault(lobby, "info-texts.mode_" + pair[1]);
                    }
                }
            }
            if (lobby.isSet("menus.stats.icons")) {
                lobby.set("menus.stats.icons.streak", null);
            }
            lobby.set(MARKER, true);
            save.accept("lobby");
            notes.add("lobby.yml: a főlobby szobrai rendbe téve (Fyland, a kivett módok helyén \"hamarosan\")");
        }
        YamlConfiguration main = get.apply("config");
        if (main != null && main.isSet("leaderboards.boards") && !main.getBoolean(MARKER, false)) {
            for (String[] pair : REPLACED) {
                main.set("leaderboards.boards." + pair[0], null);
                if (pair[1] != null && !main.isSet("leaderboards.boards." + pair[1])) {
                    copyDefault(main, "leaderboards.boards." + pair[1]);
                }
            }
            String streak = main.getString("leaderboards.boards.streak.key", "");
            if (streak.startsWith("kitpvp.") || streak.startsWith("fullpvp.")) {
                main.set("leaderboards.boards.streak", null);
            }
            main.set(MARKER, true);
            save.accept("config");
            notes.add("config.yml: a kivett módok toplistái törölve, új toplista: Fyland blokkok");
        }
        YamlConfiguration board = get.apply("scoreboard");
        if (board != null && board.isSet("boards") && !board.getBoolean(MARKER, false)) {
            for (String[] pair : REPLACED) {
                board.set("boards." + pair[0], null);
                if (pair[1] != null && !board.isSet("boards." + pair[1])) {
                    copyDefault(board, "boards." + pair[1]);
                }
            }
            board.set(MARKER, true);
            save.accept("scoreboard");
            notes.add("scoreboard.yml: új tábla a Fylandnak");
        }
        YamlConfiguration economy = get.apply("economy");
        if (economy != null && economy.isSet("currencies") && !economy.isSet("currencies.fyland")) {
            copyDefault(economy, "currencies.fyland");
            save.accept("economy");
            notes.add("economy.yml: új pénznem a Fylandnak (Szigetérme)");
        }
        YamlConfiguration ranks = get.apply("ranks");
        if (ranks != null && ranks.isSet("ranks") && !ranks.getBoolean(MARKER, false)) {
            java.util.Set<String> keep = new java.util.HashSet<>();
            YamlConfiguration combo = get.apply("comboarena");
            if (combo != null) {
                if (combo.isSet("kits")) {
                    keep.addAll(combo.getConfigurationSection("kits").getKeys(false));
                }
                if (combo.getDefaults() != null && combo.getDefaults().isConfigurationSection("kits")) {
                    keep.addAll(combo.getDefaults().getConfigurationSection("kits").getKeys(false));
                }
            }
            ConfigurationSection section = ranks.getConfigurationSection("ranks");
            for (String rank : section.getKeys(false)) {
                List<String> kits = new ArrayList<>(ranks.getStringList("ranks." + rank + ".kits"));
                kits.removeIf(kit -> !keep.contains(kit));
                ranks.set("ranks." + rank + ".kits", kits);
                List<String> perks = new ArrayList<>(ranks.getStringList("ranks." + rank + ".perks"));
                if (perks.removeIf(line -> line.contains("FullPvP") || line.trim().endsWith(" kit"))) {
                    ranks.set("ranks." + rank + ".perks", perks);
                }
            }
            ranks.set(MARKER, true);
            save.accept("ranks");
            notes.add("ranks.yml: a rangokból kikerültek a megszűnt módok kitjei");
        }
        return notes;
    }

    private static String previousRanks = "";

    /** 3.13: az új rangok (Bronz, Wizard, IceKing, Yeti, Média, Építész és a csapat rangjai) a régi VIP/MVP... helyett. */
    static boolean newRanks(YamlConfiguration ranks) {
        if (ranks == null || ranks.getDefaults() == null || !ranks.isSet("ranks") || ranks.getBoolean("migrated.ranks-v2", false)) {
            return false;
        }
        previousRanks = ranks.saveToString();
        ranks.set("ranks", null);
        copyDefault(ranks, "ranks");
        if (!ranks.isSet("luckperms-setup")) {
            ranks.set("luckperms-setup", true);
        }
        ranks.set("migrated.ranks-v2", true);
        return true;
    }

    /** 3.12: a lobby hotbarról kikerült a profil és a kozmetikák, az iránytű középre, a parkour a bal szélre került. */
    static List<String> hotbarV3(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby == null || !lobby.isSet("hotbar.items") || lobby.getBoolean("migrated.hotbar-v3", false)) {
            return notes;
        }
        lobby.set("hotbar.items.profile", null);
        lobby.set("hotbar.items.cosmetics", null);
        String[][] slots = {{"selector", "4"}, {"parkour", "0"}, {"settings", "7"}, {"info", "8"}};
        for (String[] slot : slots) {
            if (lobby.isSet("hotbar.items." + slot[0])) {
                lobby.set("hotbar.items." + slot[0] + ".slot", Integer.parseInt(slot[1]));
            }
        }
        lobby.set("migrated.hotbar-v3", true);
        save.accept("lobby");
        notes.add("lobby.yml: új hotbar (iránytű középen, parkour balra, beállítások és infó jobbra)");
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
