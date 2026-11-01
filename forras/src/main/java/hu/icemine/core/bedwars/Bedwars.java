package hu.icemine.core.bedwars;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Placer;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Az IceMine BedWars: módok (Solo, Duó, 3v3v3v3, 4v4v4v4), arénák, beszállás, statisztika, jutalmak.
 * Az arénák a saját világukban (alapból ice_bedwars) vannak, a pályákat a plugin építi fel, egyszerre egyet, hogy ne akadjon a szerver.
 */
public final class Bedwars implements ModeHandler {

    public static final String ID = "bedwars";
    public static final String FILE = "bedwars";
    public static final String ADMIN = "icemine.admin.bedwars";
    /** Ennyi blokk van két aréna között. */
    public static final int SPACING = 400;
    public static final int HEIGHT = 80;

    private final IceMineCore core;
    private final Map<String, BwMode> modes = new LinkedHashMap<>();
    private final Map<String, BwArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, BwArena> tracked = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Deque<BuildJob> builds = new ArrayDeque<>();
    private final Shop shop = new Shop();
    private final ShopMenus shopMenus;
    private final BwMenus menus;
    private BwSettings settings = new BwSettings();
    private World world;

    private static final class BuildJob {
        final BwArena arena;
        final BwMap.Theme previous;

        BuildJob(BwArena arena, BwMap.Theme previous) {
            this.arena = arena;
            this.previous = previous;
        }
    }

    public Bedwars(IceMineCore core) {
        this.core = core;
        this.shopMenus = new ShopMenus(this);
        this.menus = new BwMenus(this);
    }

    public IceMineCore core() {
        return core;
    }

    public BwSettings settings() {
        return settings;
    }

    public Shop shop() {
        return shop;
    }

    public ShopMenus shopMenus() {
        return shopMenus;
    }

    public BwMenus menus() {
        return menus;
    }

    public Map<String, BwMode> modes() {
        return modes;
    }

    public Collection<BwArena> allArenas() {
        return arenas.values();
    }

    public List<BwArena> arenas(BwMode mode) {
        List<BwArena> result = new ArrayList<>();
        for (BwArena arena : arenas.values()) {
            if (arena.mode == mode) {
                result.add(arena);
            }
        }
        return result;
    }

    public BwArena arena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public BwMode mode(String text) {
        for (BwMode mode : modes.values()) {
            if (mode.matches(text)) {
                return mode;
            }
        }
        return null;
    }

    // ---- betöltés ----

    public void load(YamlConfiguration config, List<String> problems) {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        builds.clear();
        settings = BwSettings.read(config);
        problems.addAll(shop.load(config.getConfigurationSection("prices")));
        world = loadWorld(config.getString("world", "ice_bedwars"), problems);
        if (world == null) {
            return;
        }
        modes.clear();
        ConfigurationSection modeSection = config.getConfigurationSection("modes");
        if (modeSection != null) {
            for (String key : modeSection.getKeys(false)) {
                ConfigurationSection section = modeSection.getConfigurationSection(key);
                if (section != null && section.getBoolean("enabled", true)) {
                    BwMode mode = BwMode.read(key, section, problems);
                    modes.put(mode.id, mode);
                }
            }
        }
        if (modes.isEmpty()) {
            problems.add("bedwars.yml -> modes: nincs egy mód sem");
        }
        Set<Long> used = new HashSet<>();
        ConfigurationSection built = config.getConfigurationSection("built");
        if (built != null) {
            for (String key : built.getKeys(false)) {
                used.add(slotKey(built.getInt(key + ".x") / SPACING, built.getInt(key + ".z") / SPACING));
            }
        }
        Map<String, BwArena> next = new LinkedHashMap<>();
        for (BwMode mode : modes.values()) {
            for (int n = 1; n <= mode.arenas; n++) {
                String id = mode.id + "-" + n;
                BwMap.Theme map = mode.maps.get((n - 1) % mode.maps.size());
                BwArena old = arenas.get(id);
                if (old != null && old.size() > 0 && old.mode.teams == mode.teams && old.map == map) {
                    next.put(id, old);
                    continue;
                }
                int x;
                int z;
                BwMap.Theme previous = null;
                if (built != null && built.isConfigurationSection(id)) {
                    x = built.getInt(id + ".x");
                    z = built.getInt(id + ".z");
                    previous = BwMap.Theme.of(built.getString(id + ".map"));
                } else {
                    int[] slot = freeSlot(used);
                    x = slot[0] * SPACING;
                    z = slot[1] * SPACING;
                    config.set("built." + id + ".x", x);
                    config.set("built." + id + ".z", z);
                    config.set("built." + id + ".map", "");
                    core.configs().save(FILE);
                }
                BwArena arena = new BwArena(this, id, mode, map, world, x, HEIGHT, z);
                if (previous == map) {
                    arena.setReady(true);
                    arena.reset();
                    arena.clearEntities();
                } else {
                    builds.add(new BuildJob(arena, previous));
                }
                next.put(id, arena);
            }
        }
        for (Map.Entry<String, BwArena> entry : arenas.entrySet()) {
            if (!next.containsKey(entry.getKey()) || next.get(entry.getKey()) != entry.getValue()) {
                stopArena(entry.getValue());
            }
        }
        arenas.clear();
        arenas.putAll(next);
        tasks.add(Bukkit.getScheduler().runTaskTimer(core, () -> {
            for (BwArena arena : arenas.values()) {
                arena.tick();
            }
        }, 5L, 5L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(core, () -> {
            for (BwArena arena : arenas.values()) {
                arena.spin();
            }
        }, 2L, 2L));
        if (!builds.isEmpty()) {
            core.getLogger().info("BedWars: " + builds.size() + " pályát kell felépíteni, egyesével építem őket.");
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(core, this::buildNext, 40L, 40L));
    }

    private static long slotKey(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    private static int[] freeSlot(Set<Long> used) {
        for (int i = 0; i < 4096; i++) {
            int gx = i % 8;
            int gz = i / 8;
            if (used.add(slotKey(gx, gz))) {
                return new int[]{gx, gz};
            }
        }
        return new int[]{0, 0};
    }

    private void stopArena(BwArena arena) {
        arena.kickAll();
    }

    private void buildNext() {
        BuildJob job = builds.poll();
        if (job == null) {
            return;
        }
        BwArena arena = job.arena;
        if (arenas.get(arena.id) != arena) {
            return;
        }
        long started = System.currentTimeMillis();
        Placer placer = new Placer(world, core.getLogger());
        if (job.previous != null) {
            int old = BwMap.extent(job.previous.teams);
            placer.clear(arena.cx - old, arena.cy - 32, arena.cz - old, arena.cx + old, arena.cy + BwMap.LOBBY_Y + 8, arena.cz + old);
        }
        new BwMap(placer, arena.cx, arena.cy, arena.cz, arena.map).build();
        YamlConfiguration config = core.configs().get(FILE);
        config.set("built." + arena.id + ".map", arena.map.id);
        core.configs().save(FILE);
        arena.setReady(true);
        arena.reset();
        arena.clearEntities();
        core.getLogger().info("BedWars pálya kész: " + arena.id + " (" + arena.map.id + ", " + placer.placed() + " blokk, "
                + (System.currentTimeMillis() - started) + " ms)");
    }

    /** Újraépíti az arénát, ha senki nincs rajta. */
    public boolean rebuild(BwArena arena) {
        if (arena.size() > 0) {
            return false;
        }
        arena.setReady(false);
        builds.add(new BuildJob(arena, arena.map));
        return true;
    }

    private World loadWorld(String name, List<String> problems) {
        World loaded = Bukkit.getWorld(name);
        if (loaded == null) {
            WorldCreator creator = new WorldCreator(name);
            creator.type(WorldType.FLAT);
            creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
            creator.generateStructures(false);
            loaded = creator.createWorld();
        }
        if (loaded == null) {
            problems.add("bedwars.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
            return null;
        }
        loaded.setDifficulty(Difficulty.NORMAL);
        loaded.setSpawnFlags(false, false);
        loaded.setPVP(true);
        String[][] rules = {
                {"doDaylightCycle", "false"}, {"doWeatherCycle", "false"}, {"doMobSpawning", "false"}, {"doFireTick", "false"},
                {"randomTickSpeed", "0"}, {"announceAdvancements", "false"}, {"showDeathMessages", "false"}, {"doImmediateRespawn", "true"},
                {"keepInventory", "false"}, {"naturalRegeneration", "true"}, {"mobGriefing", "false"}, {"doTileDrops", "true"}
        };
        for (String[] rule : rules) {
            loaded.setGameRuleValue(rule[0], rule[1]);
        }
        loaded.setStorm(false);
        loaded.setThundering(false);
        loaded.setTime(6000L);
        return loaded;
    }

    // ---- beszállás ----

    public BwArena arenaOf(Player player) {
        return tracked.get(player.getUniqueId());
    }

    public BwArena arenaAt(Location location) {
        for (BwArena arena : arenas.values()) {
            if (arena.contains(location)) {
                return arena;
            }
        }
        return null;
    }

    void track(Player player, BwArena arena) {
        tracked.put(player.getUniqueId(), arena);
    }

    void untrack(Player player) {
        tracked.remove(player.getUniqueId());
    }

    void untrackId(UUID id) {
        tracked.remove(id);
    }

    private boolean ready(Player player) {
        if (!core.auth().isLoggedIn(player)) {
            core.messages().send(player, "auth.login-first");
            return false;
        }
        if (tracked.containsKey(player.getUniqueId())) {
            core.messages().send(player, "bedwars.join.already");
            return false;
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "bedwars.join.combat");
            return false;
        }
        ModeInfo info = core.modes().info(ID);
        if (info != null && info.status == ModeInfo.Status.CLOSED) {
            core.messages().send(player, "mode.closed", "%mode%", info.name);
            return false;
        }
        if (info != null && info.status == ModeInfo.Status.MAINTENANCE && !player.hasPermission("icemine.admin.maintenance.bypass")) {
            core.messages().send(player, "mode.maintenance", "%mode%", info.name);
            return false;
        }
        return true;
    }

    /** A legjobb szabad aréna ebből a módból: ahol a legtöbben várnak, de még van hely. */
    public BwArena best(BwMode mode) {
        BwArena best = null;
        for (BwArena arena : arenas(mode)) {
            if (arena.joinable() && (best == null || arena.size() > best.size())) {
                best = arena;
            }
        }
        return best;
    }

    public void play(Player player, BwMode mode) {
        if (world == null || !ready(player)) {
            return;
        }
        BwArena arena = best(mode);
        if (arena == null) {
            boolean building = false;
            for (BwArena other : arenas(mode)) {
                building |= !other.ready();
            }
            core.messages().send(player, building ? "bedwars.join.building" : "bedwars.join.none", "%mode%", mode.name);
            return;
        }
        arena.join(player);
    }

    public void join(Player player, BwArena arena) {
        if (world != null && ready(player)) {
            arena.join(player);
        }
    }

    public void spectate(Player player, BwArena arena) {
        if (!ready(player)) {
            return;
        }
        if (!arena.spectate(player)) {
            core.messages().send(player, "bedwars.spectate.not-running");
        }
    }

    public Location returnLocation(Player player) {
        if (core.modeLobbies() != null && core.modeLobbies().has(ID)) {
            Location location = core.modeLobbies().entry(ID);
            if (location != null && location.getWorld() != null) {
                return location;
            }
        }
        return core.lobby().spawn(player);
    }

    // ---- statisztika, jutalom ----

    public void stat(UUID id, String key, long delta) {
        PlayerProfile profile = core.profiles().profile(id);
        if (profile != null) {
            profile.addStat(key, delta);
        }
    }

    public void winStreak(UUID id) {
        PlayerProfile profile = core.profiles().profile(id);
        if (profile != null) {
            long streak = profile.addStat("bedwars.streak", 1);
            profile.maxStat("bedwars.best_streak", streak);
        }
    }

    public void resetStreak(UUID id) {
        PlayerProfile profile = core.profiles().profile(id);
        if (profile != null && profile.stat("bedwars.streak") != 0) {
            profile.setStat("bedwars.streak", 0);
        }
    }

    public void reward(Player player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long boosted = core.ranks().boost(player, amount);
        String currency = core.economy().defaultCurrency();
        core.economy().deposit(player, currency, boosted, reason);
        core.messages().send(player, "bedwars.reward", "%amount%", core.economy().format(currency, boosted));
    }

    public void killEffect(Player player, Location at) {
        if (at != null) {
            core.cosmetics().playKill(player, at);
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
    public Location spawn(Player player) {
        BwArena arena = tracked.get(player.getUniqueId());
        if (arena != null && arena.lobby() != null) {
            return arena.lobby();
        }
        return returnLocation(player);
    }

    @Override
    public void enter(Player player) {
        if (tracked.containsKey(player.getUniqueId()) || player.hasPermission("icemine.admin.build")) {
            return;
        }
        Bukkit.getScheduler().runTask(core, () -> {
            if (player.isOnline() && player.getWorld() == world && !tracked.containsKey(player.getUniqueId())) {
                core.guard().allowNext(player);
                player.teleport(returnLocation(player));
            }
        });
    }

    @Override
    public void leave(Player player, boolean quitting) {
        if (!core.isEnabled()) {
            // leállításkor a shutdown() küld haza mindenkit, statisztika (vereség) nélkül
            return;
        }
        BwArena arena = tracked.get(player.getUniqueId());
        if (arena != null) {
            arena.leave(player, false);
        }
        if (!quitting && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        values.put("%mode%", "&6&lBedWars");
        BwArena arena = tracked.get(player.getUniqueId());
        values.put("%bw_mode%", arena == null ? "-" : arena.mode.name);
        values.put("%bw_map%", arena == null ? "-" : arena.mapName());
        values.put("%bw_players%", arena == null ? "0" : String.valueOf(arena.size()));
    }

    @Override
    public void shutdown() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (BwArena arena : arenas.values()) {
            stopArena(arena);
        }
        tracked.clear();
    }
}
