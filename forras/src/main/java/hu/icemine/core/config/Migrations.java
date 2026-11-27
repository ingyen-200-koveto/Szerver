package hu.icemine.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Migrations {

    /** A Gens nehezebb lett: {útvonal, régi alapérték, új}. */
    private static final String[][] HARDER_GENS = {
            {"level.xp-per-crop", "2", "1"},
            {"level.base", "150", "350"},
            {"level.growth", "1.17", "1.21"},
            {"level.reward-money", "500", "250"},
            {"level.reward-elixir", "200", "100"},
            {"elixir.rate", "1.0", "0.4"},
            {"hoe.enchants.boseg.price", "750", "2500"},
            {"hoe.enchants.boseg.price-multiplier", "1.42", "1.5"},
            {"hoe.enchants.kaszalas.price", "25000", "150000"},
            {"hoe.enchants.aranykez.price", "1500", "5000"},
            {"hoe.enchants.aranykez.price-multiplier", "1.45", "1.52"},
            {"hoe.enchants.szerencse.price", "10000", "40000"},
            {"hoe.enchants.szerencse.price-multiplier", "1.6", "1.7"},
            {"prestige.price", "25000000", "100000000"},
            {"autosell.price", "250000", "1000000"},
            {"backpack.large-price", "150000", "500000"},
            {"generators.slot-price", "250000", "750000"},
            {"generators.upgrade-refund", "0.5", "0.3"},
            {"events.interval-minutes", "45", "60"}
    };


    /** A régi rang nevek és előtagok: {rang, név, előtag}. */
    private static final String[][] OLD_RANKS = {
            {"default", "&7Játékos", "&7&lJÁTÉKOS &7"},
            {"bronz", "&6&lBRONZ", "&6&lBRONZ &6"},
            {"wizard", "&5&lWIZARD", "&5&lWIZARD &d"},
            {"iceking", "&b&lICE&f&lKING", "&b&lICE&f&lKING &b"},
            {"yeti", "&f&lYETI", "&f&lYETI &f"},
            {"media", "&d&lMÉDIA", "&d&lMÉDIA &d"},
            {"epitesz", "&2&lÉPÍTÉSZ", "&2&lÉPÍTÉSZ &a"},
            {"jnmoderator", "&b&lJN.MODERÁTOR", "&b&lJN.MODERÁTOR &b"},
            {"moderator", "&9&lMODERÁTOR", "&9&lMODERÁTOR &9"},
            {"srmoderator", "&1&lSR.MODERÁTOR", "&1&lSR.MODERÁTOR &9"},
            {"admin", "&c&lADMIN", "&c&lADMIN &c"},
            {"sradmin", "&4&lSR.ADMIN", "&4&lSR.ADMIN &c"},
            {"fejleszto", "&e&lFEJLESZTŐ", "&e&lFEJLESZTŐ &e"},
            {"manager", "&3&lMANAGER", "&3&lMANAGER &3"},
            {"tulajdonos", "&4&lTULAJDONOS", "&4&lTULAJDONOS &c"}
    };


    /** Módlobbyk, amelyek új, részletes témát kaptak: {lobby, téma}. */
    private static final String[][] NEW_LOBBIES = {{"kingdoms", "kingdoms", "v2"}, {"duels", "duels", "v2"}, {"bedwars", "bedwars", "v2"},
            {"skywars", "snow", "v2"}, {"combofly", "combofly", "v2"}, {"fyland", "fyland", "v2"},
            {"gens", "gens", "v3"}, {"kingdoms", "kingdoms", "v3"}, {"duels", "duels", "v3"}, {"combofly", "combofly", "v3"},
            {"bedwars", "bedwars", "v4"}, {"combofly", "combofly", "v4"}, {"fyland", "fyland", "v4"},
            {"gens", "gens", "v5"}};


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
        if (main.isSet("leaderboards.boards") && !main.isSet("leaderboards.boards.szint")) {
            copyDefault(main, "leaderboards.boards.szint");
            files.save("config");
            notes.add("config.yml: új toplista (szint)");
        }
        if (!main.isSet("levels") && main.getDefaults() != null && main.getDefaults().isSet("levels")) {
            copyDefault(main, "levels");
            files.save("config");
            notes.add("config.yml: szintek (levels) beállításai");
        }
        YamlConfiguration lobby = files.get("lobby");
        if (lobby.isSet("menus.stats.icons") && !lobby.isSet("menus.stats.icons.combokills")) {
            lobby.set("menus.stats.icons.combokills", "AMETHYST_SHARD");
            files.save("lobby");
        }
        YamlConfiguration duelsCfg = files.get("duels");
        if (duelsCfg != null && duelsCfg.isSet("arenas") && !duelsCfg.isSet("arenas.combofly")) {
            duelsCfg.set("arenas.combofly.count", 4);
            duelsCfg.set("arenas.combofly.themes", java.util.List.of("ice", "nether", "desert", "forest"));
            if ("classic".equals(duelsCfg.getString("modes.combofly.arena"))) {
                duelsCfg.set("modes.combofly.arena", "combofly");
            }
            files.save("duels");
            notes.add("duels.yml: ComboFly kupola arénák (a két játékos a két végén kezd)");
        }
        if (lobby.getDefaults() != null && (!lobby.isSet("secret-heads.enabled") || !lobby.isSet("snowstorm"))) {
            if (!lobby.isSet("secret-heads.enabled")) {
                lobby.set("secret-heads.enabled", lobby.getDefaults().getBoolean("secret-heads.enabled", true));
                lobby.set("secret-heads.reward", lobby.getDefaults().getLong("secret-heads.reward", 25));
                lobby.set("secret-heads.reward-all", lobby.getDefaults().getLong("secret-heads.reward-all", 1000));
            }
            if (!lobby.isSet("snowstorm")) {
                copyDefault(lobby, "snowstorm");
            }
            files.save("lobby");
            notes.add("lobby.yml: rejtett ajándékok és hóvihar");
        }
        if (lobby.isSet("menus.stats.icons") && !lobby.isSet("menus.stats.icons.szint")) {
            lobby.set("menus.stats.icons.szint", "EXPERIENCE_BOTTLE");
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
        if (main != null && "parkour2.best_ms".equals(main.getString("leaderboards.boards.parkour.key", ""))) {
            main.set("leaderboards.boards.parkour.key", "parkour3.best_ms");
            files.save("config");
            notes.add("config.yml: új, kockánként megjelenő parkour, új rekordlista");
        }
        if (main != null && !main.isSet("weboldal-rangok")) {
            copyDefault(main, "weboldal-rangok");
            files.save("config");
            notes.add("config.yml: a weboldalon vett rangok automatikusan megjönnek (weboldal-rangok)");
        }
        if (main != null && !main.isSet("weboldal-belepes")) {
            copyDefault(main, "weboldal-belepes");
            files.save("config");
            notes.add("config.yml: belépés a weboldalas jelszóval (weboldal-belepes)");
        }
        if (parkourFile != null && !parkourFile.isSet("checkpoint-every")) {
            if (parkourFile.getInt("min-time-ms", 0) == 40000) {
                parkourFile.set("min-time-ms", 25000);
            }
            if (parkourFile.getInt("fall-depth", 0) == 6) {
                parkourFile.set("fall-depth", 3);
            }
            parkourFile.set("checkpoint-every", 14);
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
        if (modes.isSet("modes") && !modes.getBoolean("migrated.skywars-v1", false)) {
            if (!modes.isSet("modes.skywars")) {
                copyDefault(modes, "modes.skywars");
            }
            modes.set("migrated.skywars-v1", true);
            files.save("modes");
            notes.add("modes.yml: a játékmód választóba bekerült a SkyWars");
            YamlConfiguration mainFile = files.get("config");
            if (mainFile != null && mainFile.isSet("leaderboards.boards") && !mainFile.isSet("leaderboards.boards.skywarswins")) {
                copyDefault(mainFile, "leaderboards.boards.skywarswins");
                copyDefault(mainFile, "leaderboards.boards.skywarskills");
                files.save("config");
            }
            YamlConfiguration lobbySw = files.get("lobby");
            if (lobbySw != null) {
                boolean changed = false;
                if (lobbySw.isSet("menus.stats.icons") && !lobbySw.isSet("menus.stats.icons.skywarswins")) {
                    copyDefault(lobbySw, "menus.stats.icons.skywarswins");
                    copyDefault(lobbySw, "menus.stats.icons.skywarskills");
                    changed = true;
                }
                if (modeStatue(lobbySw, "skywars", new String[]{"soon_3", "soon_2", "soon_1", "soon"})) {
                    changed = true;
                    notes.add("lobby.yml: a SkyWars szobra a lobbyban (a \"Hamarosan\" szobor helyén)");
                }
                if (changed) {
                    files.save("lobby");
                }
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
        if (lobbies != null && lobbies.isSet("lobbies") && !lobbies.getBoolean("migrated.skywars-lobby-v1", false)) {
            if (!lobbies.isSet("lobbies.skywars")) {
                copyDefault(lobbies, "lobbies.skywars");
            }
            lobbies.set("migrated.skywars-lobby-v1", true);
            files.save("modelobbies");
        }
        if (lobbies != null && lobbies.isSet("lobbies.gens") && !lobbies.getBoolean("migrated.gens-village-v1", false)) {
            // a Gens lobby új, falusi változata: egyszer újraépül
            if (lobbies.getString("lobbies.gens.theme", "gens").equalsIgnoreCase("gens")) {
                lobbies.set("lobbies.gens.built", false);
            }
            lobbies.set("migrated.gens-village-v1", true);
            files.save("modelobbies");
        }
        for (String name : new String[]{"messages_hu", "kingdoms", "contacts", "market", "lobby", "modelobbies", "scoreboard"}) {
            YamlConfiguration file = files.get(name);
            if (file == null || file.getBoolean("migrated.prefix-dot-v1", false)) {
                continue;
            }
            for (String key : file.getKeys(true)) {
                Object value = file.get(key);
                if (value instanceof String text && text.contains("[IceMine]")) {
                    file.set(key, newPrefix(text));
                } else if (value instanceof java.util.List<?> list && list.stream().anyMatch(o -> o instanceof String t && t.contains("[IceMine]"))) {
                    java.util.List<Object> fixed = new java.util.ArrayList<>();
                    for (Object o : list) {
                        fixed.add(o instanceof String t ? newPrefix(t) : o);
                    }
                    file.set(key, fixed);
                }
            }
            file.set("migrated.prefix-dot-v1", true);
            files.save(name);
        }
        YamlConfiguration rankStyle = files.get("ranks");
        if (rankStyle != null && rankStyle.getDefaults() != null && rankStyle.isConfigurationSection("ranks")
                && !rankStyle.getBoolean("migrated.rank-style-v2", false)) {
            // szebb rang nevek és előtagok (ikon + színátmenet); amit valaki átírt, ahhoz nem nyúlunk
            for (String[] old : OLD_RANKS) {
                String path = "ranks." + old[0];
                if (!rankStyle.isConfigurationSection(path) || !rankStyle.getDefaults().isSet(path)) {
                    continue;
                }
                if (old[1].equals(rankStyle.getString(path + ".name"))) {
                    rankStyle.set(path + ".name", rankStyle.getDefaults().getString(path + ".name"));
                }
                if (old[2].equals(rankStyle.getString(path + ".luckperms.prefix"))) {
                    rankStyle.set(path + ".luckperms.prefix", rankStyle.getDefaults().getString(path + ".luckperms.prefix"));
                }
            }
            rankStyle.set("migrated.rank-style-v2", true);
            files.save("ranks");
            notes.add("ranks.yml: szebb rang nevek és előtagok (a LuckPermsben is frissülnek)");
        }
        YamlConfiguration fieldTexts = files.get("messages_hu");
        if (fieldTexts != null && !fieldTexts.getBoolean("migrated.gens-field-level-v1", false)) {
            // a földek a Gens szinttel nyílnak: az árakról szóló szövegek helyett a szintek
            for (String path : new String[]{"gens.field.locked-buy", "gens.field.locked-later", "gens.field.need-previous", "gens.field.buy-confirm"}) {
                fieldTexts.set(path, null);
            }
            for (String path : new String[]{"gens.field.locked-level", "gens.field.need-level", "gens.field.unlocked-level", "gens.labels.shared",
                    "gens.menu.field.next", "gens.menu.crops.locked", "gens.menu.crops.hint", "gens.first-join"}) {
                fieldTexts.set(path, null);
                copyDefault(fieldTexts, path);
            }
            fieldTexts.set("migrated.gens-field-level-v1", true);
            files.save("messages_hu");
        }
        YamlConfiguration playerRank = files.get("ranks");
        if (playerRank != null && playerRank.getDefaults() != null && playerRank.isConfigurationSection("ranks.default")
                && !playerRank.getBoolean("migrated.player-rank-v1", false)) {
            // a Játékos rang is kap rendes előtagot (eddig csak szürke név volt)
            String prefix = playerRank.getString("ranks.default.luckperms.prefix", "");
            if (prefix.equals("&7") || prefix.equals("&7&lJÁTÉKOS &7")) {
                playerRank.set("ranks.default.luckperms.prefix", playerRank.getDefaults().getString("ranks.default.luckperms.prefix"));
            }
            if ("&7Játékos".equals(playerRank.getString("ranks.default.name"))) {
                playerRank.set("ranks.default.name", playerRank.getDefaults().getString("ranks.default.name"));
            }
            playerRank.set("migrated.player-rank-v1", true);
            files.save("ranks");
        }
        YamlConfiguration balance = files.get("gens");
        if (balance != null && balance.getDefaults() != null && !balance.getBoolean("migrated.harder-v1", false)) {
            // lassabb fejlődés: ami még a régi alapértéken állt, az az újat kapja (amit átírtál, ahhoz nem nyúlunk)
            for (String[] change : HARDER_GENS) {
                if (balance.isSet(change[0]) && Math.abs(balance.getDouble(change[0]) - Double.parseDouble(change[1])) < 1e-9) {
                    balance.set(change[0], change[2].contains(".") ? Double.parseDouble(change[2]) : Long.parseLong(change[2]));
                }
            }
            for (String id : new String[]{"tarolo", "sebesseg", "ertek"}) {
                String path = "island-upgrades." + id + ".prices";
                java.util.List<Long> old = balance.getLongList(path);
                java.util.List<Long> shipped = balance.getDefaults().getLongList(path);
                if (!old.isEmpty() && old.size() == shipped.size()) {
                    boolean same = true;
                    for (int i = 0; i < old.size(); i++) {
                        same &= old.get(i) * 3 == shipped.get(i);
                    }
                    if (same) {
                        balance.set(path, shipped);
                    }
                }
            }
            org.bukkit.configuration.ConfigurationSection tiers = balance.getDefaults().getConfigurationSection("generators.tiers");
            if (tiers != null) {
                for (String key : tiers.getKeys(false)) {
                    String base = "generators.tiers." + key + ".";
                    long shippedPrice = balance.getDefaults().getLong(base + "price", -1);
                    if (shippedPrice > 0 && balance.getLong(base + "price") * 3 == shippedPrice) {
                        balance.set(base + "price", shippedPrice);
                    }
                    if (balance.getInt(base + "seconds") == 5) {
                        balance.set(base + "seconds", 8);
                    }
                }
            }
            int oldSpacing = balance.getInt("plot.spacing", 96);
            if (oldSpacing < 1000) {
                // a szigetek messze kerülnek egymástól; a régieket a gazdájuk következő belépésekor átköltöztetjük
                balance.set("plot.old-spacing", oldSpacing);
                balance.set("plot.spacing", 1000);
            }
            if (!balance.isSet("messages-moved")) {
                YamlConfiguration movedTexts = files.get("messages_hu");
                if (movedTexts != null && !movedTexts.isSet("gens.island-moved")) {
                    copyDefault(movedTexts, "gens.island-moved");
                    files.save("messages_hu");
                }
            }
            balance.set("migrated.harder-v1", true);
            files.save("gens");
            notes.add("gens.yml: lassabb fejlődés (drágább generátorok, kapa és fejlesztések, több XP kell egy szinthez), a szigetek 1000 blokkra egymástól");
        }
        if (balance != null && balance.get("generators.tiers", null) instanceof org.bukkit.configuration.ConfigurationSection own
                && own.getKeys(false).isEmpty()) {
            // egy régi frissítés üres generátor listát hagyott a gens.yml-ben, emiatt egy generátor sem működött
            balance.set("generators.tiers", null);
            if (balance.get("generators", null) instanceof org.bukkit.configuration.ConfigurationSection gens && gens.getKeys(false).isEmpty()) {
                balance.set("generators", null);
            }
            files.save("gens");
            notes.add("gens.yml: a generátorok listája üres volt, visszaállt az alapra");
        }
        YamlConfiguration boards = files.get("scoreboard");
        if (boards != null && !boards.getBoolean("migrated.gens-title-v1", false)) {
            if (!boards.isSet("titles.gens")) {
                boards.set("titles.gens", "&a&lGENS TYCOON");
            }
            java.util.List<String> lines = new java.util.ArrayList<>(boards.getStringList("boards.gens"));
            if (lines.remove("&a&lGENS TYCOON")) {
                if (lines.size() > 1 && lines.get(1).isEmpty()) {
                    lines.remove(1);
                }
                boards.set("boards.gens", lines);
            }
            boards.set("migrated.gens-title-v1", true);
        }
        if (boards != null && !boards.getBoolean("migrated.chat-arrow-v1", false)) {
            if ("%prefix%%name%&8: %chatcolor%%message%".equals(boards.getString("chat.format"))) {
                boards.set("chat.format", "%prefix%%name% &8» %chatcolor%%message%");
            }
            boards.set("migrated.chat-arrow-v1", true);
            files.save("scoreboard");
            files.save("scoreboard");
        }
        YamlConfiguration logins = files.get("messages_hu");
        if (logins != null && !logins.getBoolean("migrated.login-text-v1", false)) {
            for (String key : new String[]{"success", "success-local", "success-old", "success-offline"}) {
                logins.set("weblogin." + key, "&aSikeres bejelentkezés, jó játékot!");
            }
            logins.set("migrated.login-text-v1", true);
            files.save("messages_hu");
        }
        if (lobbies != null && lobbies.isSet("lobbies.gens") && !lobbies.getBoolean("migrated.gens-hub-v1", false)) {
            lobbies.set("lobbies.gens.enabled", false);
            lobbies.set("migrated.gens-hub-v1", true);
            files.save("modelobbies");
        }
        YamlConfiguration lobbyTexts = files.get("messages_hu");
        if (lobbyTexts != null && "&b[Lobby] &7".equals(lobbyTexts.getString("lobby-prefix"))) {
            lobbyTexts.set("lobby-prefix", "&b&lLobby &8• &7");
            files.save("messages_hu");
        }
        if (lobbies != null && lobbies.isSet("lobbies")) {
            // a részletes, új témájú módlobbyk: mindegyik egyszer újraépül
            boolean changed = false;
            for (String[] pair : NEW_LOBBIES) {
                String key = "migrated.lobby-" + pair[2] + "-" + pair[0];
                if (lobbies.getBoolean(key, false)) {
                    continue;
                }
                if (lobbies.isSet("lobbies." + pair[0]) && lobbies.getString("lobbies." + pair[0] + ".theme", pair[1]).equalsIgnoreCase(pair[1])) {
                    lobbies.set("lobbies." + pair[0] + ".built", false);
                }
                lobbies.set(key, true);
                changed = true;
            }
            if (changed) {
                files.save("modelobbies");
            }
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
        if (gensFile != null && gensFile.isSet("hoe.enchants.kaszalas") && !gensFile.getBoolean("migrated.shared-farm-v1", false)) {
            // a földek a központi közös farmra kerültek, a kapa legfeljebb 5x5-öt arat
            if (gensFile.getInt("hoe.enchants.kaszalas.max", 2) > 2) {
                gensFile.set("hoe.enchants.kaszalas.max", 2);
            }
            gensFile.set("hoe.enchants.kaszalas.description", java.util.List.of("&7Egyszerre több növényt aratsz:", "&73x3, utána 5x5."));
            YamlConfiguration texts = files.get("messages_hu");
            if (texts != null && texts.getStringList("gens.first-join").toString().contains("ez itt a te farmod")) {
                texts.set("gens.first-join", null);
                copyDefault(texts, "gens.first-join");
                files.save("messages_hu");
            }
            gensFile.set("migrated.shared-farm-v1", true);
            files.save("gens");
            notes.add("gens.yml: a földek a központi közös farmon vannak, a kaszálás legfeljebb 5x5");
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
        YamlConfiguration oneBlockLobbies = files.get("modelobbies");
        if (oneBlockLobbies != null && oneBlockLobbies.isSet("lobbies") && !oneBlockLobbies.getBoolean("migrated.unique-v1", false)) {
            ConfigurationSection section = oneBlockLobbies.getConfigurationSection("lobbies");
            java.util.Set<String> themes = java.util.Set.of("fullpvp", "fyland", "combofly", "gens", "kingdoms", "duels");
            for (String key : section.getKeys(false)) {
                if (!oneBlockLobbies.isSet("lobbies." + key + ".theme")) {
                    oneBlockLobbies.set("lobbies." + key + ".theme", themes.contains(key) ? key : "snow");
                }
                oneBlockLobbies.set("lobbies." + key + ".built", false);
            }
            oneBlockLobbies.set("migrated.unique-v1", true);
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
        notes.addAll(compassV2(files::get, files::save));
        notes.addAll(cosmeticsRemoved(files::get, files::save));
        notes.addAll(blockedV2(files::get, files::save));
        YamlConfiguration hotbarLobby = files.get("lobby");
        if (hotbarLobby != null && hotbarLobby.isConfigurationSection("hotbar.items.selector") && hotbarLobby.getInt("hotbar.items.selector.slot", 4) != 4) {
            // az iránytű mindig középen van
            boolean taken = false;
            for (String key : hotbarLobby.getConfigurationSection("hotbar.items").getKeys(false)) {
                if (!key.equals("selector") && hotbarLobby.getInt("hotbar.items." + key + ".slot", -1) == 4) {
                    hotbarLobby.set("hotbar.items." + key + ".slot", hotbarLobby.getInt("hotbar.items.selector.slot"));
                    taken = true;
                }
            }
            hotbarLobby.set("hotbar.items.selector.slot", 4);
            files.save("lobby");
            notes.add("lobby.yml: a játékmód választó iránytű középre került" + (taken ? " (a helyén lévő tárgy a régi helyére)" : ""));
        }
        YamlConfiguration viewConfig = files.get("config");
        if (viewConfig != null && !viewConfig.getBoolean("migrated.view-gens-v1", false)) {
            if (!viewConfig.isSet("view.worlds.ice_gens")) {
                viewConfig.set("view.worlds.ice_gens", 12);
            }
            viewConfig.set("migrated.view-gens-v1", true);
            files.save("config");
        }
        if (viewConfig != null && !viewConfig.getBoolean("migrated.view-gens-v2", false)) {
            // a Gensben is az egész pálya látszódjon
            if (viewConfig.getInt("view.worlds.ice_gens", 0) == 12) {
                viewConfig.set("view.worlds.ice_gens", null);
            }
            viewConfig.set("migrated.view-gens-v2", true);
            files.save("config");
        }
        YamlConfiguration logConfig = files.get("config");
        if (logConfig != null && !logConfig.getBoolean("migrated.changelog-off-v1", false)) {
            // a changelog egyelőre ki van kapcsolva
            logConfig.set("changelog.discord", false);
            logConfig.set("migrated.changelog-off-v1", true);
            files.save("config");
        }
        YamlConfiguration kitConfig = files.get("config");
        if (kitConfig != null && !kitConfig.getBoolean("migrated.blocked-v3", false)) {
            // a /kit kivéve: a ComboFlyban mindenkinek ugyanaz a kitje
            List<String> list = new ArrayList<>(kitConfig.getStringList("blocked-commands"));
            for (String name : List.of("kit", "kits")) {
                if (!list.contains(name)) {
                    list.add(name);
                }
            }
            kitConfig.set("blocked-commands", list);
            kitConfig.set("migrated.blocked-v3", true);
            files.save("config");
            YamlConfiguration kitTexts = files.get("messages_hu");
            if (kitTexts != null && kitTexts.isSet("icem.help.kit")) {
                kitTexts.set("icem.help.kit", null);
                files.save("messages_hu");
            }
        }
        notes.addAll(dailyRemoved(files::get, files::save));
        notes.addAll(gensBoardV2(files::get, files::save));
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
        if (oneBlockLobbies != null && !oneBlockLobbies.getBoolean("migrated.oneblock-title", false) && oneBlockLobbies.isConfigurationSection("lobbies.fyland")) {
            oneBlockLobbies.set("migrated.oneblock-title", true);
            if ("ONEBLOCK".equals(oneBlockLobbies.getString("lobbies.fyland.title")) && oneBlockLobbies.getBoolean("lobbies.fyland.built", false)) {
                oneBlockLobbies.set("lobbies.fyland.built", false);
                notes.add("modelobbies.yml: a Fyland neve OneBlock lett, a lobbyja az új felirattal újraépül");
            }
            files.save("modelobbies");
        }
        YamlConfiguration rankFile = files.get("ranks");
        if (rankFile != null && rankFile.isSet("ranks.default.luckperms") && !rankFile.isSet("ranks.default.luckperms.prefix")
                && "jatekos".equals(rankFile.getString("ranks.default.group"))) {
            copyDefault(rankFile, "ranks.default.luckperms.prefix");
            files.save("ranks");
            notes.add("ranks.yml: Játékos rang (jatekos csoport) előtaggal, az új játékosok automatikusan megkapják");
        }
        YamlConfiguration lobbyNpcs = files.get("lobby");
        if (creativeStatue(lobbyNpcs)) {
            files.save("lobby");
            notes.add("lobby.yml: a Creative szobra a lobbyban (a \"Hamarosan\" szobor helyén)");
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
        YamlConfiguration lobbyInfo = files.get("lobby");
        if (lobbyInfo != null) {
            java.util.List<String> combo = lobbyInfo.getStringList("info-texts.mode_combofly");
            if (combo.removeIf(line -> line.contains("/combofly"))) {
                lobbyInfo.set("info-texts.mode_combofly", combo);
                files.save("lobby");
                notes.add("lobby.yml: a ComboFly leírásból kikerült a /combofly sor (a kombó kijelzés megszűnt)");
            }
            org.bukkit.configuration.ConfigurationSection infok = lobbyInfo.getConfigurationSection("info-texts");
            boolean torolt = false;
            if (infok != null) {
                for (String key : infok.getKeys(false)) {
                    java.util.List<String> sorok = lobbyInfo.getStringList("info-texts." + key);
                    if (sorok.removeIf(line -> line.contains("/kozmetika") || line.contains("/parkour") || line.contains("/lottery"))) {
                        lobbyInfo.set("info-texts." + key, sorok);
                        torolt = true;
                    }
                }
            }
            if (torolt) {
                files.save("lobby");
                notes.add("lobby.yml: a /kozmetika, /parkour és /lottery parancs kikerült a leírásokból");
            }
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

    /** 3.51: a Gens scoreboard (generátorok, elixír, szint), ha a régi alapértelmezett volt. */
    static List<String> gensBoardV2(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration board = get.apply("scoreboard");
        if (board == null || board.getBoolean("migrated.gens-v2", false)) {
            return notes;
        }
        List<String> lines = board.getStringList("boards.gens");
        if (lines.contains("&7Földek: &f%fields%&7/&f%max_fields%") && lines.contains("&7Learatva: &f%harvested%")) {
            org.bukkit.configuration.Configuration defaults = board.getDefaults();
            if (defaults != null && !defaults.getStringList("boards.gens").isEmpty()) {
                board.set("boards.gens", defaults.getStringList("boards.gens"));
                notes.add("scoreboard.yml: új Gens scoreboard (generátorok, elixír, szint)");
            }
        }
        board.set("migrated.gens-v2", true);
        save.accept("scoreboard");
        return notes;
    }

    /** 3.50: a napi jutalom és a Hasznos parancsok (hologram, NPC, info gomb, szöveg) kikerült. */
    static List<String> dailyRemoved(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby != null && !lobby.getBoolean("migrated.daily-removed", false)) {
            lobby.set("npcs.daily", null);
            lobby.set("holograms.info_commands", null);
            lobby.set("menus.info.items.commands", null);
            lobby.set("info-texts.commands", null);
            lobby.set("migrated.daily-removed", true);
            save.accept("lobby");
            notes.add("lobby.yml: napi jutalom NPC és Hasznos parancsok törölve");
        }
        YamlConfiguration economy = get.apply("economy");
        if (economy != null && !economy.getBoolean("migrated.daily-removed", false)) {
            economy.set("daily.enabled", false);
            economy.set("migrated.daily-removed", true);
            save.accept("economy");
            notes.add("economy.yml: napi jutalom kikapcsolva");
        }
        return notes;
    }

    /** 3.49: kivett parancsok (játékosoknak): combofly, sw, bw, arena, kapcsolat, discord, top, hub, l, napi, penz, fizet, baltop. */
    static List<String> blockedV2(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration config = get.apply("config");
        if (config == null || config.getBoolean("migrated.blocked-v2", false)) {
            return notes;
        }
        List<String> list = new ArrayList<>(config.getStringList("blocked-commands"));
        for (String name : List.of("combofly", "sw", "skywars", "bw", "bedwars", "arena", "arenak", "arenas", "kapcsolat", "kapcsolatok", "contacts", "discord", "top", "toplista", "leaderboard", "hub", "l", "napi", "daily", "penz", "balance", "bal", "money", "fizet", "pay", "utal", "baltop", "penztop", "balancetop")) {
            if (!list.contains(name)) {
                list.add(name);
            }
        }
        config.set("blocked-commands", list);
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby != null && lobby.getStringList("info-texts.commands").stream().anyMatch(line -> line.contains("/kapcsolat"))) {
            org.bukkit.configuration.Configuration defaults = lobby.getDefaults();
            if (defaults != null) {
                lobby.set("info-texts.commands", defaults.getStringList("info-texts.commands"));
                save.accept("lobby");
            }
        }
        config.set("migrated.blocked-v2", true);
        save.accept("config");
        notes.add("config.yml: kivett parancsok a blocked-commands listában");
        return notes;
    }

    /** 3.46: a kozmetikák (nyomvonal, ölés effekt) kikerültek: a beállításuk és a rangoknál a felsorolásuk is. */
    static List<String> cosmeticsRemoved(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby != null && !lobby.getBoolean("migrated.cosmetics-removed", false)) {
            lobby.set("cosmetics", null);
            lobby.set("menus.cosmetics", null);
            lobby.set("migrated.cosmetics-removed", true);
            save.accept("lobby");
            notes.add("lobby.yml: kozmetikák törölve");
        }
        YamlConfiguration ranks = get.apply("ranks");
        if (ranks != null && ranks.isConfigurationSection("ranks") && !ranks.getBoolean("migrated.cosmetics-removed", false)) {
            java.util.Set<String> old = java.util.Set.of("&7Hónyom és jégszilánk effekt", "&7Felhő nyomvonal", "&7Szívek és tűzijáték ölés effekt",
                    "&7Fagy és kristály nyomvonal, lélek effekt", "&7Minden kozmetika");
            for (String id : ranks.getConfigurationSection("ranks").getKeys(false)) {
                String base = "ranks." + id + ".";
                ranks.set(base + "cosmetics", null);
                List<String> perks = new ArrayList<>(ranks.getStringList(base + "perks"));
                if (perks.removeIf(old::contains)) {
                    ranks.set(base + "perks", perks);
                }
            }
            ranks.set("migrated.cosmetics-removed", true);
            save.accept("ranks");
            notes.add("ranks.yml: kozmetikák kivéve a rangokból");
        }
        return notes;
    }

    /** 3.45: szebb iránytű (Játékmódok) a lobby hotbaron, ha a régi alapértelmezett név van beállítva. */
    static List<String> compassV2(java.util.function.Function<String, YamlConfiguration> get, java.util.function.Consumer<String> save) {
        List<String> notes = new ArrayList<>();
        YamlConfiguration lobby = get.apply("lobby");
        if (lobby == null || !lobby.isSet("hotbar.items.selector") || lobby.getBoolean("migrated.compass-v2", false)) {
            return notes;
        }
        if ("&b[IceMine] &fJátékmódok".equals(lobby.getString("hotbar.items.selector.name"))) {
            org.bukkit.configuration.Configuration defaults = lobby.getDefaults();
            if (defaults != null) {
                lobby.set("hotbar.items.selector.name", defaults.getString("hotbar.items.selector.name"));
                lobby.set("hotbar.items.selector.glow", true);
                lobby.set("hotbar.items.selector.lore", defaults.getStringList("hotbar.items.selector.lore"));
                notes.add("lobby.yml: szebb Játékmódok iránytű");
            }
        }
        lobby.set("migrated.compass-v2", true);
        save.accept("lobby");
        return notes;
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

    /** Új mód szobra egy "Hamarosan" szobor helyére a főlobbyban (ha van még ilyen). */
    static boolean modeStatue(YamlConfiguration lobby, String mode, String[] candidates) {
        String marker = "migrated." + mode + "-statue";
        if (lobby == null || !lobby.isConfigurationSection("npcs") || lobby.isSet("npcs.mode_" + mode) || lobby.getBoolean(marker, false)) {
            return false;
        }
        ConfigurationSection npcs = lobby.getConfigurationSection("npcs");
        String soon = null;
        for (String candidate : candidates) {
            if (npcs.isConfigurationSection(candidate + ".location")) {
                soon = candidate;
                break;
            }
        }
        lobby.set(marker, true);
        if (soon == null) {
            return false;
        }
        ConfigurationSection location = npcs.getConfigurationSection(soon + ".location");
        for (Map.Entry<String, Object> entry : location.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                lobby.set("npcs.mode_" + mode + ".location." + entry.getKey(), entry.getValue());
            }
        }
        hu.icemine.core.lobby.LobbyDefaults.npc(lobby, "mode_" + mode);
        lobby.set("npcs." + soon, null);
        return true;
    }

    /** 3.15: a Creative elkészült, a szobra az egyik "Hamarosan" szobor helyére kerül a főlobbyban. */
    static boolean creativeStatue(YamlConfiguration lobby) {
        if (lobby == null || !lobby.isConfigurationSection("npcs") || lobby.isSet("npcs.mode_creative") || lobby.getBoolean("migrated.creative-statue", false)) {
            return false;
        }
        ConfigurationSection npcs = lobby.getConfigurationSection("npcs");
        String soon = null;
        for (String candidate : new String[]{"soon_2", "soon_3", "soon_1", "soon"}) {
            if (npcs.isConfigurationSection(candidate + ".location")) {
                soon = candidate;
                break;
            }
        }
        if (soon == null) {
            for (String key : npcs.getKeys(false)) {
                if (key.startsWith("soon") && npcs.isConfigurationSection(key + ".location")) {
                    soon = key;
                    break;
                }
            }
        }
        lobby.set("migrated.creative-statue", true);
        if (soon == null) {
            return true;
        }
        ConfigurationSection location = npcs.getConfigurationSection(soon + ".location");
        for (Map.Entry<String, Object> entry : location.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                lobby.set("npcs.mode_creative.location." + entry.getKey(), entry.getValue());
            }
        }
        hu.icemine.core.lobby.LobbyDefaults.npc(lobby, "mode_creative");
        lobby.set("npcs." + soon, null);
        return true;
    }

    /** A régi "[IceMine]" előtag helyett "IceMine •", ahogy a játékmódoknál. */
    static String newPrefix(String text) {
        return text.replace("&b[IceMine] &7", "&b&lIceMine &8• &7").replace("&b[IceMine] ", "&b&lIceMine &8• ")
                .replace("&b[IceMine]\n", "&b&lIceMine\n").replace("[IceMine]", "IceMine •");
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
