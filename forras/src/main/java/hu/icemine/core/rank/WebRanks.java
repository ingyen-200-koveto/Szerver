package hu.icemine.core.rank;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.economy.IceCoins;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * A weboldalon vett rangok: az IceCoin lekérdezés a rangot is visszaadja, és ha a játékosnak a weboldalon rangja van,
 * megkapja a hozzá tartozó LuckPerms csoportot. Ha lejár, leveszi. Csak azt a csoportot veszi le, amit ő adott,
 * a kézzel (lp paranccsal) adott rangokhoz nem nyúl.
 */
public final class WebRanks {

    private final IceMineCore core;
    private final Map<String, String> groups = new HashMap<>();
    private boolean enabled;
    private String site = "";
    private File file;
    private YamlConfiguration given = new YamlConfiguration();

    public WebRanks(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration main) {
        enabled = main.getBoolean("weboldal-rangok.enabled", true);
        groups.clear();
        ConfigurationSection section = main.getConfigurationSection("weboldal-rangok.rangok");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String group = section.getString(key, "").trim().toLowerCase(Locale.ROOT);
                if (!group.isEmpty()) {
                    groups.put(key.toLowerCase(Locale.ROOT), group);
                }
            }
        }
        site = main.getString("weboldal-belepes.weboldal", "icemine.hu");
        file = new File(core.getDataFolder(), "weboldal-rangok.yml");
        given = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    static String target(Map<String, String> groups, String webRank) {
        if (webRank == null) {
            return null;
        }
        return groups.get(webRank.trim().toLowerCase(Locale.ROOT));
    }

    /** @param ranks weboldalas név -> rang, csak azok, akiknek van megerősített fiókja */
    public void update(Map<String, String> ranks) {
        if (!enabled || !core.prefixes().hooked()) {
            return;
        }
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String web = ranks.get(IceCoins.key(player));
            if (web == null) {
                continue;
            }
            String id = player.getUniqueId().toString();
            String target = target(groups, web);
            String previous = given.getString(id);
            if (Objects.equals(target, previous)) {
                continue;
            }
            boolean touched = false;
            if (previous != null) {
                lp(player, "remove", previous);
                given.set(id, null);
                changed = true;
                touched = true;
                if (target == null) {
                    core.messages().send(player, "webrank.expired", "%rank%", display(previous), "%url%", site);
                }
            }
            if (target != null && !player.hasPermission("group." + target)) {
                lp(player, "add", target);
                given.set(id, target);
                changed = true;
                touched = true;
                core.messages().send(player, "webrank.given", "%rank%", display(target));
            }
            if (!touched) {
                continue;
            }
            Bukkit.getScheduler().runTaskLater(core, () -> {
                if (player.isOnline()) {
                    core.ranks().apply(player);
                }
            }, 40L);
        }
        if (changed && file != null) {
            try {
                given.save(file);
            } catch (IOException exception) {
                core.getLogger().warning("Nem sikerült menteni: " + file.getName() + " (" + exception.getMessage() + ")");
            }
        }
    }

    private void lp(Player player, String action, String group) {
        core.getLogger().info("Weboldalas rang: " + player.getName() + " " + (action.equals("add") ? "megkapta" : "elvesztette") + ": " + group);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getUniqueId() + " parent " + action + " " + group);
    }

    private String display(String group) {
        for (Ranks.Rank rank : core.ranks().all()) {
            if (group.equalsIgnoreCase(rank.group)) {
                return rank.name;
            }
        }
        return group;
    }
}
