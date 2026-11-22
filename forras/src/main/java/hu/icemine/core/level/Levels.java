package hu.icemine.core.level;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Text;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Szerver szintű szintek: minden játékmódból jön XP (győzelem, ölés, ágy, blokk...).
 * Az XP a "level.xp" statisztikában van, a szint ebből számolódik.
 */
public final class Levels {

    public static final String XP = "level.xp";
    public static final int MAX = 200;

    /** Kulcs vagy "*.vég" -> {xp, ennyi egységenként}. */
    private final Map<String, int[]> rules = new LinkedHashMap<>();
    private final IceMineCore core;
    private boolean enabled = true;
    private boolean chat = true;
    private boolean nametag = true;
    private long reward = 10;
    private long milestone = 250;

    public Levels(IceMineCore core) {
        this.core = core;
    }

    public void load(ConfigurationSection config) {
        rules.clear();
        defaults();
        if (config != null) {
            enabled = config.getBoolean("enabled", true);
            chat = config.getBoolean("chat", true);
            nametag = config.getBoolean("nametag", true);
            reward = Math.max(0, config.getLong("reward-per-level", 10));
            milestone = Math.max(0, config.getLong("reward-every-10", 250));
            ConfigurationSection xp = config.getConfigurationSection("xp");
            if (xp != null) {
                for (String key : xp.getKeys(true)) {
                    if (xp.isConfigurationSection(key)) {
                        continue;
                    }
                    String[] parts = xp.getString(key, "").trim().split("\\s*/\\s*");
                    try {
                        int amount = Integer.parseInt(parts[0].trim());
                        int every = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;
                        rules.put(key, new int[]{amount, every});
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        PlayerProfile.hook(enabled ? this::changed : null);
    }

    private void defaults() {
        put("bedwars.wins", 60, 1);
        put("bedwars.final_kills", 8, 1);
        put("bedwars.kills", 4, 1);
        put("bedwars.beds", 20, 1);
        put("bedwars.games", 10, 1);
        put("skywars.wins", 60, 1);
        put("skywars.kills", 8, 1);
        put("skywars.games", 10, 1);
        put("duels.wins", 25, 1);
        put("duels.played", 5, 1);
        put("combofly.combos", 1, 1);
        put("lobby.pvp_kills", 2, 1);
        put("gens.quests", 25, 1);
        put("gens.harvested", 1, 40);
        put("fyland.blocks", 1, 15);
        put("parkour3.completions", 30, 1);
        put("lobby.snowflakes", 2, 1);
        put("*.kills", 4, 1);
        put("*.wins", 30, 1);
    }

    private void put(String key, int xp, int every) {
        rules.put(key, new int[]{xp, every});
    }

    private int[] rule(String key) {
        int[] exact = rules.get(key);
        if (exact != null) {
            return exact;
        }
        int dot = key.lastIndexOf('.');
        return dot < 0 ? null : rules.get("*" + key.substring(dot));
    }

    // ---- számolás ----

    /** Ennyi XP kell a szint -> szint+1 lépéshez. */
    public static long need(int level) {
        return 100L + 35L * (level - 1);
    }

    public static int level(long xp) {
        int level = 1;
        long left = xp;
        while (level < MAX && left >= need(level)) {
            left -= need(level);
            level++;
        }
        return level;
    }

    /** {szint, XP a szinten belül, kell a következőhöz}. */
    public static long[] progress(long xp) {
        int level = 1;
        long left = xp;
        while (level < MAX && left >= need(level)) {
            left -= need(level);
            level++;
        }
        return new long[]{level, left, level >= MAX ? 0 : need(level)};
    }

    public int level(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        return profile == null ? 1 : level(profile.stat(XP));
    }

    public static String color(int level) {
        if (level >= 100) {
            return "&c";
        }
        if (level >= 75) {
            return "&d";
        }
        if (level >= 50) {
            return "&5";
        }
        if (level >= 40) {
            return "&b";
        }
        if (level >= 30) {
            return "&6";
        }
        if (level >= 20) {
            return "&e";
        }
        if (level >= 10) {
            return "&f";
        }
        return "&7";
    }

    /** Pl. "&8[&e23✦&8] ". */
    public String tag(Player player) {
        if (!enabled) {
            return "";
        }
        int level = level(player);
        return "&8[" + color(level) + level + "✦&8] ";
    }

    public boolean chat() {
        return enabled && chat;
    }

    public boolean nametag() {
        return enabled && nametag;
    }

    public static String bar(long have, long need, int length) {
        int filled = need <= 0 ? length : (int) Math.min(length, have * length / need);
        return "&b" + "▌".repeat(filled) + "&8" + "▌".repeat(length - filled);
    }

    // ---- XP ----

    private void changed(PlayerProfile profile, String key, long before, long after) {
        if (key.startsWith("level.") || key.endsWith("streak") || key.contains(".best")) {
            return;
        }
        int[] rule = rule(key);
        if (rule == null) {
            return;
        }
        long units = after / rule[1] - before / rule[1];
        if (units <= 0) {
            return;
        }
        add(profile, units * rule[0]);
    }

    public void add(PlayerProfile profile, long amount) {
        long old = profile.stat(XP);
        long now = old + amount;
        profile.setStat(XP, now);
        int from = level(old);
        int to = level(now);
        if (to > from) {
            if (Bukkit.isPrimaryThread()) {
                levelUp(profile, from, to);
            } else {
                Bukkit.getScheduler().runTask(core, () -> levelUp(profile, from, to));
            }
        }
    }

    private void levelUp(PlayerProfile profile, int from, int to) {
        Player player = Bukkit.getPlayer(profile.uuid());
        if (player == null) {
            return;
        }
        long coins = 0;
        for (int l = from + 1; l <= to; l++) {
            coins += reward * l + (l % 10 == 0 ? milestone : 0);
        }
        String currency = core.economy().defaultCurrency();
        if (coins > 0) {
            core.economy().deposit(player, currency, coins, "szintlépés " + to);
        }
        String c = color(to);
        player.sendTitle(Text.color(c + "&l" + to + ". SZINT"), Text.color("&7Jutalom: &f" + core.economy().format(currency, coins)), 5, 50, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.2F);
        core.messages().sendTagged(player, "&aSzintet léptél! &7Most " + c + to + ". szintű &7vagy, kaptál &f" + core.economy().format(currency, coins) + "&7-t.");
        if (to % 10 == 0) {
            String text = Text.color("&b[IceMine] &f" + player.getName() + " &7elérte a " + c + "&l" + to + ". szintet&7!");
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.sendMessage(text);
            }
        }
    }
}
