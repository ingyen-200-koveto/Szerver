package hu.icemine.core.board;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.mode.ModeService;
import hu.icemine.core.util.Text;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class BoardService implements Listener {

    private static final String OBJECTIVE = "icemine";
    private static final String SUFFIX = "0123456789abcdef";

    private final IceMineCore core;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();
    private final Map<String, List<String>> templates = new LinkedHashMap<>();
    private String title = "&b&l❄ &b&lIce&f&lMine&b&l ❄";
    private final Map<String, String> titles = new HashMap<>();
    private final java.util.Set<String> numbered = new java.util.HashSet<>();
    private final java.util.Set<String> fast = new java.util.HashSet<>();
    private List<String> header = new ArrayList<>();
    private List<String> footer = new ArrayList<>();
    private boolean chatEnabled = true;
    private String chatFormat = "%prefix%%name%&8: %chatcolor%%message%";

    public BoardService(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        title = config.getString("title", "&b&l❄ &b&lIce&f&lMine&b&l ❄");
        titles.clear();
        ConfigurationSection titleSection = config.getConfigurationSection("titles");
        if (titleSection != null) {
            for (String id : titleSection.getKeys(false)) {
                titles.put(id, titleSection.getString(id, title));
            }
        }
        numbered.clear();
        numbered.addAll(config.getStringList("show-numbers"));
        fast.clear();
        templates.clear();
        ConfigurationSection section = config.getConfigurationSection("boards");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                List<String> lines = new ArrayList<>(section.getStringList(id));
                if (lines.size() > 15) {
                    problems.add("scoreboard.yml -> boards." + id + ": legfeljebb 15 sor lehet, a többit levágom");
                    lines = new ArrayList<>(lines.subList(0, 15));
                }
                templates.put(id, lines);
                if (String.join("\n", lines).contains("%combat_time%") || String.join("\n", lines).contains("%pearl%")) {
                    fast.add(id);
                }
            }
        }
        header = new ArrayList<>(config.getStringList("tablist.header"));
        footer = new ArrayList<>(config.getStringList("tablist.footer"));
        chatEnabled = config.getBoolean("chat.enabled", true);
        chatFormat = config.getString("chat.format", "%prefix%%name%&8: %chatcolor%%message%");
        for (Player player : Bukkit.getOnlinePlayers()) {
            rebuild(player);
        }
    }

    public String global(String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        String result = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%parkour_record%", core.parkour().record());
        if (result.contains("_")) {
            for (ModeInfo info : core.modes().all()) {
                int online = core.modes().online(info.id);
                result = result.replace("%online_" + info.id + "%", online < 0 ? "?" : String.valueOf(online))
                        .replace("%status_" + info.id + "%", core.menus().statusText(info))
                        .replace("%mode_name_" + info.id + "%", info.name);
            }
            if (result.contains("%bw_") && core.bedwars() != null) {
                result = core.bedwars().global(result);
            }
        }
        return result;
    }

    static String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.1fs", Math.max(0.0, value));
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> values = new HashMap<>();
        PlayerProfile profile = core.profiles().profile(player);
        values.put("%player%", player.getName());
        values.put("%rank%", Text.color(core.prefixes().prefix(player)).trim());
        values.put("%rank_name%", core.prefixes().group(player));
        String currency = core.economy().defaultCurrency();
        values.put("%money%", core.economy().format(currency, profile == null ? 0 : profile.balance(currency)));
        values.put("%icecoin%", core.iceCoins() == null ? "-" : core.iceCoins().display(player));
        values.put("%gens_money%", core.economy().format("gens", profile == null ? 0 : profile.balance("gens")));
        values.put("%discord%", core.configs().get("scoreboard").getString("discord", ""));
        values.put("%combo_best%", String.valueOf(profile == null ? 0 : profile.stat("combofly.best")));
        long combatMillis = core.combat().tagged(player) ? Math.max(0L, core.combat().remainingMillis(player)) : 0L;
        values.put("%combat_time%", seconds(combatMillis / 1000.0));
        values.put("%pearl%", seconds(player.getCooldown(org.bukkit.Material.ENDER_PEARL) / 20.0));
        values.put("%combat%", core.combat().tagged(player) ? core.messages().get("board.in-combat", "%seconds%", String.valueOf(core.combat().remainingSeconds(player))) : core.messages().get("board.no-combat"));
        ModeHandler handler = core.modes().currentHandler(player);
        if (handler != null) {
            handler.placeholders(player, values);
        }
        if (!values.containsKey("%mode%")) {
            String mode = core.modes().current(player);
            ModeInfo info = core.modes().info(mode);
            values.put("%mode%", info == null ? "-" : info.name);
        }
        return values;
    }

    public String apply(String text, Map<String, String> values) {
        String result = text;
        if (result.indexOf('%') >= 0) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            result = global(result);
        }
        return Text.color(result);
    }

    private String boardMode(Player player) {
        String mode = core.modes().current(player);
        if (mode != null && mode.startsWith(hu.icemine.core.modelobby.ModeLobbies.PREFIX) && !templates.containsKey(mode)) {
            return templates.containsKey("modelobby") ? "modelobby" : ModeService.LOBBY;
        }
        return mode;
    }

    private String templateId(Player player) {
        String mode = boardMode(player);
        if (mode == null) {
            return null;
        }
        return templates.containsKey(mode) ? mode : "default";
    }

    public void fastTick() {
        if (fast.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(player.getUniqueId()) && fast.contains(templateId(player))) {
                update(player);
            }
        }
    }

    private List<String> template(Player player) {
        String mode = boardMode(player);
        if (mode == null) {
            return null;
        }
        if (!mode.equals(ModeService.LOBBY) && !mode.equals("modelobby") && !mode.startsWith(hu.icemine.core.modelobby.ModeLobbies.PREFIX)) {
            ModeInfo info = core.modes().info(mode);
            if (info == null || !info.coreScoreboard) {
                return null;
            }
        }
        List<String> lines = templates.get(mode);
        return lines != null ? lines : templates.get("default");
    }

    public void rebuild(Player player) {
        List<String> lines = template(player);
        boards.remove(player.getUniqueId());
        shown.remove(player.getUniqueId());
        updateTab(player);
        if (lines == null) {
            return;
        }
        PlayerProfile profile = core.profiles().profile(player);
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String id = templateId(player);
        Objective objective = board.registerNewObjective(OBJECTIVE, "dummy", Text.color(titles.getOrDefault(id, title)));
        if (profile == null || !profile.flag("board.hidden", false)) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        if (!numbered.contains(id)) {
            try {
                Class<?> format = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
                Object blank = format.getMethod("blank").invoke(null);
                Method method = Objective.class.getMethod("numberFormat", format);
                method.invoke(objective, blank);
            } catch (Throwable ignored) {
            }
        }
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        shown.put(player.getUniqueId(), new ArrayList<>());
        update(player);
    }

    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE);
        List<String> template = template(player);
        if (objective == null || template == null) {
            return;
        }
        Map<String, String> values = placeholders(player);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < template.size() && i < SUFFIX.length(); i++) {
            lines.add(apply(template.get(i), values) + "§" + SUFFIX.charAt(i));
        }
        List<String> old = shown.get(player.getUniqueId());
        if (old != null) {
            for (String line : old) {
                if (!lines.contains(line)) {
                    board.resetScores(line);
                }
            }
        }
        int score = lines.size();
        for (String line : lines) {
            if (old == null || !old.contains(line) || old.indexOf(line) != lines.indexOf(line)) {
                objective.getScore(line).setScore(score);
            }
            score--;
        }
        shown.put(player.getUniqueId(), lines);
    }

    private static boolean tabPlugin() {
        return Bukkit.getPluginManager().getPlugin("TAB") != null;
    }

    public void updateTab(Player player) {
        ModeInfo info = core.modes().info(core.modes().current(player));
        boolean external = info != null && !info.coreScoreboard;
        if (external || tabPlugin()) {
            return;
        }
        Map<String, String> values = placeholders(player);
        List<String> top = new ArrayList<>();
        for (String line : header) {
            top.add(apply(line, values));
        }
        List<String> bottom = new ArrayList<>();
        for (String line : footer) {
            bottom.add(apply(line, values));
        }
        player.setPlayerListHeaderFooter(String.join("\n", top), String.join("\n", bottom));
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            core.prefixes().apply(player);
            if (boards.containsKey(player.getUniqueId())) {
                update(player);
                updateTab(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!chatEnabled) {
            return;
        }
        Player player = event.getPlayer();
        String format = Text.color(chatFormat
                .replace("%prefix%", core.prefixes().prefix(player))
                .replace("%suffix%", core.prefixes().suffix(player))
                .replace("%chatcolor%", "&f")
                .replace("%rank_name%", core.prefixes().group(player)))
                .replace("%name%", "%1$s").replace("%message%", "%2$s");
        if (!format.contains("%1$s") || !format.contains("%2$s")) {
            return;
        }
        event.setFormat(format.replace("%", "%%").replace("%%1$s", "%1$s").replace("%%2$s", "%2$s"));
        if (player.hasPermission("icemine.chat.color")) {
            event.setMessage(Text.color(event.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        shown.remove(event.getPlayer().getUniqueId());
    }
}
