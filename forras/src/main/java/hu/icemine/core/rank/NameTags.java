package hu.icemine.core.rank;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Text;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * A játékosok feje fölött: a név előtt a rang, a név alatt az életerő (pl. "20 ❤").
 *
 * A rang: a LuckPerms előtag, ha be van állítva, különben az IceMine rang neve (ranks.yml), alap rangnál semmi.
 * BedWars meccsben a rang helyett a csapat színe és betűje látszik.
 * Ha a TAB plugin kezeli a névtáblákat (ez az alap), a rangot a TAB API-n keresztül adjuk át neki, mert a TAB
 * más pluginok csapatait nem engedi. Az életerőt mindig mi írjuk ki (a TAB alapból nem csinálja).
 */
public final class NameTags implements Listener {

    static final String HEALTH = "im_hp";
    static final String TEAM = "im";

    private final IceMineCore core;
    private final Map<UUID, String> sentToTab = new HashMap<>();
    private boolean enabled = true;
    private boolean showHealth = true;
    private boolean showRank = true;
    private String healthTitle = "&c❤";
    private Set<String> disabledWorlds = new HashSet<>();
    private Tab tab;
    private boolean tabChecked;
    private boolean failed;

    public NameTags(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("nametags.enabled", true);
        showHealth = config.getBoolean("nametags.health", true);
        showRank = config.getBoolean("nametags.rank", true);
        healthTitle = config.getString("nametags.health-title", "&c❤");
        disabledWorlds = new HashSet<>();
        for (String world : config.getStringList("nametags.disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        sentToTab.clear();
        if (!enabled) {
            cleanup();
        }
    }

    /** A rang (vagy BedWars csapat) előtag, színkódokkal, a végén szóközzel; üres, ha nincs mit kiírni. */
    String prefix(Player player) {
        if (core.bedwars() != null) {
            String team = core.bedwars().nameTag(player);
            if (team != null) {
                return team;
            }
        }
        if (!showRank) {
            return "";
        }
        String prefix = core.prefixes().prefix(player);
        if (prefix != null && !Text.strip(Text.color(prefix)).isBlank()) {
            return prefix.endsWith(" ") ? prefix : prefix + " ";
        }
        Ranks.Rank rank = core.ranks().of(player);
        if (rank == null || rank.id.equals("default")) {
            return "";
        }
        return rank.name + " &f";
    }

    private boolean active(Player player) {
        return enabled && !disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private static int health(Player player) {
        return (int) Math.ceil(Math.max(0.0, player.getHealth() + player.getAbsorptionAmount()));
    }

    /** Negyed másodpercenként: minden játékos scoreboardjára felírjuk a többiek rangját és életerejét. */
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            update();
        } catch (RuntimeException error) {
            if (!failed) {
                failed = true;
                core.getLogger().log(java.util.logging.Level.WARNING, "Hiba a névtábláknál (rang, életerő), egyszer írom ki:", error);
            }
        }
    }

    private void update() {
        List<Player> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasMetadata("NPC")) {
                online.add(player);
            }
        }
        Map<String, String> prefixes = new HashMap<>();
        for (Player player : online) {
            prefixes.put(player.getName(), active(player) ? prefix(player) : "");
        }
        boolean tabTeams = tabTeams();
        if (tabTeams) {
            for (Player player : online) {
                String prefix = prefixes.get(player.getName());
                if (!prefix.equals(sentToTab.get(player.getUniqueId())) && tab.setPrefix(player, prefix)) {
                    sentToTab.put(player.getUniqueId(), prefix);
                }
            }
        }
        Map<Scoreboard, Boolean> seen = new IdentityHashMap<>();
        for (Player viewer : online) {
            Scoreboard board = viewer.getScoreboard();
            if (seen.put(board, Boolean.TRUE) != null) {
                continue;
            }
            if (showHealth) {
                health(board, online);
            }
            if (!tabTeams) {
                teams(board, online, prefixes);
            }
        }
    }

    private void health(Scoreboard board, List<Player> online) {
        Objective objective = board.getObjective(HEALTH);
        if (objective == null) {
            Objective taken = board.getObjective(DisplaySlot.BELOW_NAME);
            if (taken != null) {
                return;
            }
            objective = board.registerNewObjective(HEALTH, "dummy", Text.color(healthTitle));
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        for (Player player : online) {
            Score score = objective.getScore(player.getName());
            int value = active(player) ? health(player) : 0;
            if (!score.isScoreSet() || score.getScore() != value) {
                score.setScore(value);
            }
        }
    }

    private void teams(Scoreboard board, List<Player> online, Map<String, String> prefixes) {
        boolean bedwars = board.getObjective("icemine_bw") != null;
        for (Player player : online) {
            String name = player.getName();
            String prefix = prefixes.get(name);
            Team current = board.getEntryTeam(name);
            if (current != null && !current.getName().startsWith(TEAM)) {
                // egy másik rendszer (pl. a BedWars csapatszínek) már beosztotta, azt nem bántjuk
                continue;
            }
            if (bedwars || prefix.isEmpty()) {
                if (current != null) {
                    current.removeEntry(name);
                }
                continue;
            }
            String teamName = TEAM + Integer.toHexString(prefix.hashCode());
            if (current != null && current.getName().equals(teamName)) {
                continue;
            }
            if (current != null) {
                current.removeEntry(name);
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.setPrefix(Text.color(prefix));
                ChatColor color = lastColor(Text.color(prefix));
                if (color != null) {
                    team.setColor(color);
                }
            }
            team.addEntry(name);
        }
    }

    static ChatColor lastColor(String text) {
        ChatColor result = null;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == ChatColor.COLOR_CHAR) {
                ChatColor color = ChatColor.getByChar(text.charAt(i + 1));
                if (color != null && color.isColor()) {
                    result = color;
                }
            }
        }
        return result;
    }

    private boolean tabTeams() {
        if (!tabChecked) {
            tabChecked = true;
            tab = Tab.connect(core);
        }
        return tab != null && tab.teamsEnabled();
    }

    /** Leállításkor és kikapcsoláskor: a fő scoreboardról levesszük, amit felírtunk (a többi scoreboard úgyis eldobható). */
    public void cleanup() {
        Scoreboard main = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (main == null) {
            return;
        }
        Objective objective = main.getObjective(HEALTH);
        if (objective != null) {
            objective.unregister();
        }
        for (Team team : new ArrayList<>(main.getTeams())) {
            if (team.getName().startsWith(TEAM) && team.getName().length() <= 10) {
                team.unregister();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sentToTab.remove(event.getPlayer().getUniqueId());
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        main.resetScores(event.getPlayer().getName());
        Team team = main.getEntryTeam(event.getPlayer().getName());
        if (team != null && team.getName().startsWith(TEAM)) {
            team.removeEntry(event.getPlayer().getName());
        }
    }

    /** A TAB plugin API-ja reflexióval (nem kell hozzá fordítási függőség, és ha a TAB nincs fent, semmi nem történik). */
    static final class Tab {
        private final Object api;
        private final Method getPlayer;
        private final Method getNameTagManager;
        private final IceMineCore core;
        private Method setPrefix;
        private boolean warned;

        private Tab(IceMineCore core, Object api, Method getPlayer, Method getNameTagManager) {
            this.core = core;
            this.api = api;
            this.getPlayer = getPlayer;
            this.getNameTagManager = getNameTagManager;
        }

        static Tab connect(IceMineCore core) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("TAB");
            if (plugin == null || !plugin.isEnabled()) {
                return null;
            }
            try {
                Class<?> type = Class.forName("me.neznamy.tab.api.TabAPI", true, plugin.getClass().getClassLoader());
                Object api = type.getMethod("getInstance").invoke(null);
                Tab tab = new Tab(core, api, type.getMethod("getPlayer", UUID.class), type.getMethod("getNameTagManager"));
                core.getLogger().info("TAB megtalálva: a rangot a TAB-nak adom át a névtáblákhoz.");
                return tab;
            } catch (Throwable error) {
                core.getLogger().warning("A TAB fent van, de az API-ja nem érhető el (" + error + "), a névtábla rangot nem tudom beállítani.");
                return null;
            }
        }

        private Object manager() {
            try {
                return getNameTagManager.invoke(api);
            } catch (Throwable error) {
                return null;
            }
        }

        boolean teamsEnabled() {
            return manager() != null;
        }

        boolean setPrefix(Player player, String prefix) {
            Object manager = manager();
            if (manager == null) {
                return false;
            }
            try {
                Object tabPlayer = getPlayer.invoke(api, player.getUniqueId());
                if (tabPlayer == null) {
                    return false;
                }
                if (setPrefix == null) {
                    for (Method method : manager.getClass().getMethods()) {
                        if (method.getName().equals("setPrefix") && method.getParameterCount() == 2 && method.getParameterTypes()[1] == String.class) {
                            setPrefix = method;
                            break;
                        }
                    }
                    if (setPrefix == null) {
                        throw new NoSuchMethodException("NameTagManager.setPrefix");
                    }
                }
                setPrefix.invoke(manager, tabPlayer, prefix.isEmpty() ? null : prefix);
                return true;
            } catch (Throwable error) {
                if (!warned) {
                    warned = true;
                    core.getLogger().warning("Nem sikerült a névtábla rangot átadni a TAB-nak: " + error);
                }
                return false;
            }
        }
    }
}
