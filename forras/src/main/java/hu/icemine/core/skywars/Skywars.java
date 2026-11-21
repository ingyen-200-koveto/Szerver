package hu.icemine.core.skywars;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.util.Items;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Az IceMine SkyWars: Solo és Duó, saját világ (ice_skywars), a pályákat a plugin építi és minden meccs után
 * újraépíti, egyszerre egyet.
 */
public final class Skywars implements ModeHandler {

    public static final String ID = "skywars";
    public static final String FILE = "skywars";
    public static final String ADMIN = "icemine.admin.skywars";
    public static final int SPACING = 300;
    public static final int HEIGHT = 100;

    private final IceMineCore core;
    private final Map<String, SwMode> modes = new LinkedHashMap<>();
    private final Map<String, SwArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, SwArena> tracked = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Deque<SwArena> builds = new ArrayDeque<>();
    private World world;
    private int countdown = 30;
    private int fullCountdown = 10;
    private long killReward = 10;
    private long winReward = 50;

    public Skywars(IceMineCore core) {
        this.core = core;
    }

    public IceMineCore core() {
        return core;
    }

    public Map<String, SwMode> modes() {
        return modes;
    }

    public Collection<SwArena> allArenas() {
        return arenas.values();
    }

    public SwArena arena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public SwMode mode(String text) {
        for (SwMode m : modes.values()) {
            if (m.matches(text)) {
                return m;
            }
        }
        return null;
    }

    int countdown() {
        return countdown;
    }

    int fullCountdown() {
        return fullCountdown;
    }

    long killReward() {
        return killReward;
    }

    long winReward() {
        return winReward;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        for (SwArena a : arenas.values()) {
            a.kickAll();
        }
        arenas.clear();
        builds.clear();
        tracked.clear();
        countdown = Math.max(5, config.getInt("countdown", 30));
        fullCountdown = Math.max(3, Math.min(countdown, config.getInt("full-countdown", 10)));
        killReward = Math.max(0, config.getLong("rewards.kill", 10));
        winReward = Math.max(0, config.getLong("rewards.win", 50));
        world = loadWorld(config.getString("world", "ice_skywars"), problems);
        if (world == null) {
            return;
        }
        modes.clear();
        ConfigurationSection ms = config.getConfigurationSection("modes");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(key);
                if (s != null && s.getBoolean("enabled", true)) {
                    SwMode m = SwMode.read(key, s, problems);
                    modes.put(m.id, m);
                }
            }
        }
        if (modes.isEmpty()) {
            problems.add("skywars.yml -> modes: nincs egy mód sem");
        }
        int slot = 0;
        for (SwMode m : modes.values()) {
            for (int n = 1; n <= m.arenas; n++) {
                String id = m.id + "-" + n;
                SwMap.Theme theme = m.maps.get((n - 1) % m.maps.size());
                SwArena a = new SwArena(this, id, m, theme, world, (slot % 6) * SPACING, HEIGHT, (slot / 6) * SPACING);
                slot++;
                arenas.put(id, a);
                builds.add(a);
            }
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(core, () -> {
            for (SwArena a : arenas.values()) {
                a.tick();
            }
        }, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(core, this::buildNext, 40L, 30L));
    }

    void queueBuild(SwArena a) {
        if (!builds.contains(a)) {
            builds.add(a);
        }
    }

    private void buildNext() {
        SwArena a = builds.poll();
        if (a == null || arenas.get(a.id) != a) {
            return;
        }
        long t = System.currentTimeMillis();
        a.build();
        core.getLogger().fine("SkyWars pálya kész: " + a.id + " (" + (System.currentTimeMillis() - t) + " ms)");
    }

    private World loadWorld(String name, List<String> problems) {
        World w = Bukkit.getWorld(name);
        if (w == null) {
            WorldCreator c = new WorldCreator(name);
            c.type(WorldType.FLAT);
            c.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
            c.generateStructures(false);
            w = c.createWorld();
        }
        if (w == null) {
            problems.add("skywars.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
            return null;
        }
        w.setDifficulty(Difficulty.NORMAL);
        w.setSpawnFlags(false, false);
        w.setPVP(true);
        String[][] rules = {
                {"doDaylightCycle", "false"}, {"doWeatherCycle", "false"}, {"doMobSpawning", "false"}, {"doFireTick", "false"},
                {"randomTickSpeed", "0"}, {"announceAdvancements", "false"}, {"showDeathMessages", "false"}, {"doImmediateRespawn", "true"},
                {"keepInventory", "false"}, {"naturalRegeneration", "true"}, {"mobGriefing", "false"}, {"doTileDrops", "true"}, {"spectatorsGenerateChunks", "false"}
        };
        for (String[] r : rules) {
            w.setGameRuleValue(r[0], r[1]);
        }
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(6000L);
        return w;
    }

    // ---- beszállás ----

    public SwArena arenaOf(Player p) {
        return tracked.get(p.getUniqueId());
    }

    void track(Player p, SwArena a) {
        tracked.put(p.getUniqueId(), a);
    }

    void untrack(Player p) {
        tracked.remove(p.getUniqueId());
    }

    void untrackId(UUID id) {
        tracked.remove(id);
    }

    private boolean ready(Player p) {
        if (!core.auth().isLoggedIn(p)) {
            core.messages().send(p, "auth.login-first");
            return false;
        }
        if (tracked.containsKey(p.getUniqueId())) {
            core.messages().sendTagged(p, "&cMár egy SkyWars meccsben vagy. Kilépés: &f/sw leave");
            return false;
        }
        if (core.partyk() != null && core.partyk().elfoglalt(p)) {
            core.messages().sendTagged(p, "&cMost épp egy másik játékban vagy.");
            return false;
        }
        ModeInfo info = core.modes().info(ID);
        if (info != null && info.status == ModeInfo.Status.CLOSED) {
            core.messages().send(p, "mode.closed", "%mode%", info.name);
            return false;
        }
        if (info != null && info.status == ModeInfo.Status.MAINTENANCE && !p.hasPermission("icemine.admin.maintenance.bypass")) {
            core.messages().send(p, "mode.maintenance", "%mode%", info.name);
            return false;
        }
        return true;
    }

    public void play(Player p, SwMode mode) {
        if (world == null || !ready(p) || !partyEngedi(p)) {
            return;
        }
        List<Player> tagok = partyTagok(p);
        SwArena best = null;
        for (SwArena a : arenas.values()) {
            if (a.mode == mode && a.joinable() && a.capacity() - a.size() >= tagok.size() + 1 && (best == null || a.size() > best.size())) {
                best = a;
            }
        }
        if (best == null) {
            boolean epul = arenas.values().stream().anyMatch(a -> a.mode == mode && a.state() == SwArena.State.BUILDING);
            core.messages().sendTagged(p, epul ? "&eA pályák még épülnek, próbáld újra pár másodperc múlva."
                    : tagok.isEmpty() ? "&cMost nincs szabad pálya ebben a módban, próbáld kicsit később." : "&cNincs olyan szabad pálya, ahol az egész party elférne.");
            return;
        }
        if (!best.join(p)) {
            return;
        }
        for (Player m : tagok) {
            if (best.joinable()) {
                best.join(m);
                core.messages().sendTagged(m, "&7A party vezetője beléptetett a SkyWarsba.");
            }
        }
    }

    private boolean partyEngedi(Player p) {
        hu.icemine.core.social.Partyk partyk = core.partyk();
        if (partyk == null || partyk.of(p.getUniqueId()) == null || partyk.vezetoE(p.getUniqueId())) {
            return true;
        }
        core.messages().sendTagged(p, "&cPartyban vagy: a party vezetője léptet be mindenkit. Egyedül: &f/party kilep");
        return false;
    }

    private List<Player> partyTagok(Player vezeto) {
        hu.icemine.core.social.Partyk partyk = core.partyk();
        List<Player> out = new ArrayList<>();
        if (partyk == null || !partyk.vezetoE(vezeto.getUniqueId())) {
            return out;
        }
        for (Player m : partyk.of(vezeto.getUniqueId()).online()) {
            if (!m.equals(vezeto) && !partyk.elfoglalt(m) && core.auth().isLoggedIn(m) && !tracked.containsKey(m.getUniqueId())) {
                out.add(m);
            }
        }
        return out;
    }

    public void spectate(Player p, Player target) {
        SwArena a = target == null ? null : tracked.get(target.getUniqueId());
        if (a == null || !ready(p) || !a.spectate(p)) {
            core.messages().sendTagged(p, "&c" + (target == null ? "Nincs ilyen játékos." : target.getName() + " most nem játszik SkyWarst."));
        }
    }

    void sendHome(Player p) {
        Location l = core.lobby().spawn(p);
        if (l != null) {
            core.guard().allowNext(p);
            p.teleport(l);
        }
    }

    public void menu(Player p) {
        Menu menu = new Menu("skywars", 3, hu.icemine.core.util.Text.color("&b&lSkyWars &8» &fVálassz módot"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int[] slots = {11, 15, 13};
        int i = 0;
        for (SwMode m : modes.values()) {
            if (i >= 2) {
                break;
            }
            int waiting = 0;
            int playing = 0;
            for (SwArena a : arenas.values()) {
                if (a.mode == m) {
                    if (a.playing()) {
                        playing += a.size();
                    } else {
                        waiting += a.size();
                    }
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7" + m.islands + " sziget, szigetenként " + m.teamSize + " fő");
            lore.add("");
            lore.add("&fVár: &a" + waiting + " &8| &fJátszik: &e" + playing);
            lore.add("");
            lore.add("&e» Kattints a játékhoz!");
            menu.set(slots[i], Items.of(m.teamSize > 1 ? Material.DIAMOND_SWORD : Material.IRON_SWORD).name(m.name).lore(color(lore)).hideAll().build(), c -> {
                c.closeInventory();
                play(c, m);
            });
            i++;
        }
        PlayerProfile pr = core.profiles().profile(p.getUniqueId());
        List<String> st = new ArrayList<>();
        if (pr != null) {
            st.add("&fGyőzelmek: &a" + pr.stat("skywars.wins") + " &8| &fMeccsek: &f" + pr.stat("skywars.games"));
            st.add("&fÖlések: &c" + pr.stat("skywars.kills") + " &8| &fHalálok: &7" + pr.stat("skywars.deaths"));
            st.add("&fGyőzelmi sorozat: &e" + pr.stat("skywars.streak") + " &8(legjobb: " + pr.stat("skywars.best_streak") + ")");
        }
        menu.set(13, Items.of(Material.BOOK).name("&e&lStatisztikád").lore(color(st)).build());
        menu.open(p);
    }

    private static List<String> color(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            out.add(hu.icemine.core.util.Text.color(s));
        }
        return out;
    }

    // ---- statisztika, jutalom ----

    void stat(UUID id, String key, long delta) {
        PlayerProfile p = core.profiles().profile(id);
        if (p != null) {
            p.addStat(key, delta);
        }
    }

    void winStreak(UUID id) {
        PlayerProfile p = core.profiles().profile(id);
        if (p != null) {
            long s = p.addStat("skywars.streak", 1);
            p.maxStat("skywars.best_streak", s);
        }
    }

    void resetStreak(UUID id) {
        PlayerProfile p = core.profiles().profile(id);
        if (p != null && p.stat("skywars.streak") != 0) {
            p.setStat("skywars.streak", 0);
        }
    }

    void reward(Player p, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long boosted = core.ranks().boost(p, amount);
        String currency = core.economy().defaultCurrency();
        core.economy().deposit(p, currency, boosted, reason);
        core.messages().sendTagged(p, "&6+" + core.economy().format(currency, boosted));
    }

    void killEffect(Player p, Location at) {
        try {
            core.cosmetics().playKill(p, at);
        } catch (RuntimeException ignored) {
        }
    }

    // ---- ModeHandler ----

    @Override
    public String id() {
        return ID;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public Location spawn(Player p) {
        SwArena a = tracked.get(p.getUniqueId());
        return a != null ? a.spectatorPoint() : core.lobby().spawn(p);
    }

    @Override
    public boolean openMenu(Player p) {
        if (tracked.containsKey(p.getUniqueId())) {
            return false;
        }
        menu(p);
        return true;
    }

    @Override
    public void enter(Player p) {
        if (tracked.containsKey(p.getUniqueId()) || p.hasPermission("icemine.admin.build")) {
            return;
        }
        Bukkit.getScheduler().runTask(core, () -> {
            if (p.isOnline() && p.getWorld() == world && !tracked.containsKey(p.getUniqueId())) {
                sendHome(p);
            }
        });
    }

    @Override
    public void leave(Player p, boolean quitting) {
        if (!core.isEnabled()) {
            return;
        }
        SwArena a = tracked.get(p.getUniqueId());
        if (a != null) {
            a.leave(p, false);
        }
        if (!quitting && p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.ADVENTURE);
        }
    }

    @Override
    public void placeholders(Player p, Map<String, String> values) {
        values.put("%mode%", "&b&lSkyWars");
    }

    @Override
    public void shutdown() {
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
        for (SwArena a : arenas.values()) {
            a.kickAll();
        }
        tracked.clear();
    }
}
