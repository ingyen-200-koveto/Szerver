package hu.icemine.core.lobby;

import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LobbyDefaults {

    private LobbyDefaults() {
    }

    static void hologram(YamlConfiguration config, String id) {
        String base = "holograms." + id + ".";
        if (id.startsWith("portal_")) {
            String mode = id.substring("portal_".length());
            config.set(base + "type", "mode");
            config.set(base + "target", mode);
            config.set(base + "lines", Arrays.asList("%mode_name%", "&7Online: &f%online_" + mode + "%", "%status_" + mode + "%"));
            config.set(base + "action", "mode:" + mode);
            return;
        }
        if (id.startsWith("top_")) {
            config.set(base + "type", "leaderboard");
            config.set(base + "target", id.substring("top_".length()));
            config.set(base + "background", "70081828");
            return;
        }
        config.set(base + "type", "text");
        config.set(base + "lines", lines(id));
        String action = action(id);
        if (!action.isEmpty()) {
            config.set(base + "action", action);
        }
    }

    private static String action(String id) {
        switch (id) {
            case "info_commands":
                return "info:commands";
            case "info_rules":
                return "info:rules";
            case "info_server":
                return "info:server";
            case "info_contacts":
                return "contacts";
            case "parkour_start":
                return "info:parkour";
            default:
                return "";
        }
    }

    private static List<String> lines(String id) {
        switch (id) {
            case "welcome":
                return Arrays.asList("&b&l❄ &b&lIce&f&lMine&b&l ❄", "&fÜdv a szerveren!", "&7Fent vagytok: &b%online% &7játékos",
                        "&7Kattints egy szoborra, vagy használd az &biránytűt&7!");
            case "parkour_start":
                return Arrays.asList("&b&lJÉGPARKOUR", "&7Lépj a nyomólapra, és indul az óra!", "&7Rekord: &f%parkour_record%",
                        "&e» Kattints a részletekért! «");
            case "info_commands":
                return Arrays.asList("&a&lHasznos parancsok", "&7/lobby, /spawn, /napi, /profil", "&e» Kattints rám! «");
            case "info_rules":
                return Arrays.asList("&e&lSzabályok", "&7Olvasd el, mielőtt játszol!", "&e» Kattints rám! «");
            case "info_server":
                return Arrays.asList("&b&lA szerverről", "&7IP, verziók, Discord", "&e» Kattints rám! «");
            case "info_contacts":
                return Arrays.asList("&3&lKapcsolatok", "&7Discord, weboldal, YouTube", "&e» Kattints rám! «");
            case "parkour_finish":
                return Arrays.asList("&a&lCÉL", "&7Ügyes voltál, ha idáig eljutottál!", "&7Pár másodperc múlva visszaviszlek a lobbyba.");
            case "info":
                return Arrays.asList("&b&lHasznos tudnivalók", "&7Kapcsolatok: &f/kapcsolat", "&7Pénzed: &f/penz &8| &7Napi jutalom: &f/napi",
                        "&7Profilod: &f/profil", "&7Vissza a lobbyba: &f/lobby");
            case "contacts":
                return Arrays.asList("&3&lKapcsolatok", "&7Discord, weboldal és a többi", "&7Kattints a lakosra!");
            case "combofly_info":
                return Arrays.asList("&d&lCOMBOFLY", "&7Üsd egymás után az ellenfelet,", "&7és ne hagyd leérni!", "&7A kombód: &f/combofly stats");
            default:
                return Arrays.asList("&b" + id);
        }
    }

    private static final String[][] LOOKS = {
            {"skypvp", "PLAYER_HEAD", "LEATHER_CHESTPLATE #7FD0FF", "LEATHER_LEGGINGS #7FD0FF", "LEATHER_BOOTS #3D8FD8", "BOW"},
            {"kitpvp", "PLAYER_HEAD", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS", "DIAMOND_SWORD"},
            {"combofly", "PLAYER_HEAD", "LEATHER_CHESTPLATE #A35CF0", "LEATHER_LEGGINGS #A35CF0", "LEATHER_BOOTS #6A2FB0", "STONE_SWORD"},
            {"gens", "PLAYER_HEAD", "LEATHER_CHESTPLATE #5FC94A", "LEATHER_LEGGINGS #5FC94A", "LEATHER_BOOTS #3E8C2E", "EMERALD"},
            {"duels", "PLAYER_HEAD", "LEATHER_CHESTPLATE #F2C230", "LEATHER_LEGGINGS #F2C230", "LEATHER_BOOTS #A8801A", "GOLDEN_SWORD"},
            {"bedwars", "PLAYER_HEAD", "LEATHER_CHESTPLATE #E0701B", "LEATHER_LEGGINGS #E0701B", "LEATHER_BOOTS #9A4A12", "RED_BED"},
            {"kingdoms", "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "LEATHER_LEGGINGS #7A4A24", "LEATHER_BOOTS #4E2F17", "GOLDEN_SWORD"},
            {"soon", "PLAYER_HEAD", "LEATHER_CHESTPLATE #8C8C8C", "LEATHER_LEGGINGS #8C8C8C", "LEATHER_BOOTS #5A5A5A", "CLOCK"}
    };

    private static void look(YamlConfiguration config, String base, String mode) {
        for (String[] look : LOOKS) {
            if (look[0].equals(mode)) {
                config.set(base + "type", "statue");
                config.set(base + "equipment.helmet", look[1]);
                config.set(base + "equipment.chestplate", look[2]);
                config.set(base + "equipment.leggings", look[3]);
                config.set(base + "equipment.boots", look[4]);
                config.set(base + "equipment.hand", look[5]);
                return;
            }
        }
        look(config, base, "soon");
    }

    public static void npc(YamlConfiguration config, String id) {
        String base = "npcs." + id + ".";
        if (id.startsWith("soon")) {
            config.set(base + "name", Arrays.asList("&7&lHAMAROSAN", "&8Új játékmód érkezik"));
            config.set(base + "action", "message:&7Ez a játékmód még készül. Hamarosan itt lesz, figyeld a Discordot!");
            look(config, base, "soon");
            return;
        }
        switch (id) {
            case "selector":
                config.set(base + "name", Arrays.asList("&b&lJátékmódok", "&7Kattints rám!"));
                config.set(base + "profession", "cartographer");
                config.set(base + "action", "menu:selector");
                break;
            case "stats":
                config.set(base + "name", Arrays.asList("&e&lToplisták", "&7Ki a legjobb?"));
                config.set(base + "profession", "librarian");
                config.set(base + "action", "menu:stats");
                break;
            case "contacts":
                config.set(base + "name", Arrays.asList("&3&lKapcsolatok", "&7Discord, weboldal, YouTube"));
                config.set(base + "profession", "cleric");
                config.set(base + "action", "book:contacts");
                break;
            case "daily":
                config.set(base + "name", Arrays.asList("&a&lNapi jutalom", "&7Nézz be hozzám minden nap!"));
                config.set(base + "profession", "farmer");
                config.set(base + "action", "command:napi");
                break;
            case "info":
                config.set(base + "name", Arrays.asList("&f&lInformációk"));
                config.set(base + "profession", "mason");
                config.set(base + "action", "menu:info");
                break;
            default:
                if (id.startsWith("mode_")) {
                    String mode = id.substring("mode_".length());
                    config.set(base + "name", Arrays.asList("&e&lKATTINTS A JÁTÉKHOZ", "%mode_name_" + mode + "%", "&f%online_" + mode + "% &7játékos"));
                    config.set(base + "action", "mode:" + mode);
                    look(config, base, mode);
                } else {
                    config.set(base + "name", Arrays.asList("&b" + id));
                    config.set(base + "profession", "nitwit");
                    config.set(base + "action", "menu:selector");
                }
        }
    }
}
