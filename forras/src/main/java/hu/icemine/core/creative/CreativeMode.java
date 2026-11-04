package hu.icemine.core.creative;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.ClickText;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Creative mód: végtelen telekrács, mindenki kap telket (rangtól függ, hányat), azon kreatív módban építhet.
 * A telkek az adatbázisban vannak, a világot a PlotGenerator rajzolja (nem kell felépíteni). A saját telekre
 * barátokat lehet felvenni, másokat kitiltani, és a telek egy paranccsal újrakezdhető.
 */
public final class CreativeMode implements ModeHandler, Listener {

    public static final String ID = "creative";
    public static final String FILE = "creative";

    private final IceMineCore core;
    private final Map<Long, Plot> plots = new HashMap<>();
    private final Map<UUID, Long> lastPlot = new HashMap<>();
    private final Map<UUID, String> confirm = new HashMap<>();
    private final Map<UUID, Long> confirmUntil = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Deque<Runnable> clearJobs = new ArrayDeque<>();
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);
    private CreativeWorldEdit worldEdit;
    private boolean weEnabled = true;
    private long weMinPlaytime = 7_200_000L;
    private int weMaxBlocks = 20000;
    private int weMaxRadius = 20;
    private int weMaxCount = 10;
    private List<String> wePermissions = new ArrayList<>();
    private long tickCount;
    private final Map<String, Integer> rankLimits = new HashMap<>();
    private YamlConfiguration config;
    private World world;
    private PlotGrid grid;
    private PlotGenerator generator;
    private PlotStore store;
    private boolean loaded;
    private int defaultLimit = 1;
    private int entityLimit = 40;
    private int maxLimit = 2;
    private long hubKey = PlotGrid.key(0, 0);
    private Material claimedBorder = Material.QUARTZ_SLAB;

    public CreativeMode(IceMineCore core) {
        this.core = core;
    }

    IceMineCore core() {
        return core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public World world() {
        return world;
    }

    public boolean inWorld(Player player) {
        return world != null && player.getWorld() == world;
    }

    PlotGrid grid() {
        return grid;
    }

    // ---- betöltés ----

    public void load(YamlConfiguration config, List<String> problems) {
        this.config = config;
        if (world == null) {
            int size = Math.max(16, Math.min(256, config.getInt("plot.size", 48)));
            int road = Math.max(3, Math.min(16, config.getInt("plot.road", 7)));
            int height = Math.max(-50, Math.min(200, config.getInt("plot.height", 64)));
            grid = new PlotGrid(size, road, height);
            generator = new PlotGenerator(grid, material(config, "plot.ground", Material.GRASS_BLOCK, problems), material(config, "plot.fill", Material.DIRT, problems),
                    material(config, "plot.road-block", Material.SMOOTH_STONE, problems), material(config, "plot.border", Material.SMOOTH_STONE_SLAB, problems));
            String name = config.getString("world", "creative");
            WorldCreator creator = new WorldCreator(name).generator(generator).generateStructures(false);
            world = creator.createWorld();
            if (world == null) {
                problems.add("creative.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
            prepareWorld();
        }
        claimedBorder = material(config, "plot.border-claimed", Material.QUARTZ_SLAB, problems);
        defaultLimit = Math.max(0, config.getInt("limits.default", 1));
        rankLimits.clear();
        ConfigurationSection ranks = config.getConfigurationSection("limits.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) {
                rankLimits.put(rank.toLowerCase(Locale.ROOT), Math.max(0, ranks.getInt(rank)));
            }
        }
        entityLimit = Math.max(0, config.getInt("entities-per-plot", 40));
        loadWorldEdit(config);
        maxLimit = Math.max(1, config.getInt("limits.max", 2));
        hubKey = PlotGrid.key(config.getInt("hub.plot-x", 0), config.getInt("hub.plot-z", 0));
        blocked.clear();
        for (String name : config.getStringList("blocked-items")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                problems.add("creative.yml -> blocked-items: ismeretlen tárgy (" + name + ")");
            } else {
                blocked.add(material);
            }
        }
        if (!loaded) {
            store = new PlotStore(core.database());
            try {
                store.init();
                for (Plot plot : store.loadAll()) {
                    plots.put(plot.key(), plot);
                }
                loaded = true;
            } catch (Exception exception) {
                problems.add("Creative: nem sikerült betölteni a telkeket (" + exception.getMessage() + "), a mód most nem indul el");
                world = null;
            }
        }
        if (world != null && loaded && !config.getBoolean("hub.built", false)) {
            Plot taken = plots.get(hubKey);
            config.set("hub.built", true);
            core.configs().save(FILE);
            if (taken != null) {
                relocate(taken, this::buildHub);
            } else {
                buildHub();
            }
        }
        if (world != null) {
            Location spawn = hubSpawn();
            world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        }
    }

    private static Material material(YamlConfiguration config, String path, Material fallback, List<String> problems) {
        String name = config.getString(path);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            problems.add("creative.yml -> " + path + ": ismeretlen blokk (" + name + ")");
            return fallback;
        }
        return material;
    }

    private void prepareWorld() {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doTraderSpawning", "false");
        world.setGameRuleValue("doPatrolSpawning", "false");
        world.setGameRuleValue("announceAdvancements", "false");
        world.setGameRuleValue("randomTickSpeed", "3");
        world.setGameRuleValue("spawnRadius", "0");
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(Math.max(2000, config.getInt("border-size", 20000)));
    }

    // ---- üzenetek ----

    String text(String key, String... pairs) {
        String value = config == null ? null : config.getString("messages." + key);
        return Text.replace(value == null ? "&c[creative: " + key + "]" : value, pairs);
    }

    List<String> texts(String key, String... pairs) {
        List<String> result = new ArrayList<>();
        if (config != null) {
            for (String line : config.getStringList("messages." + key)) {
                result.add(Text.replace(line, pairs));
            }
        }
        return result;
    }

    void send(Player player, String key, String... pairs) {
        core.messages().sendTagged(player, text(key, pairs));
    }

    void click(Player player, String key, String... pairs) {
        player.sendMessage(ClickText.line(core.messages().prefix() + text(key, pairs), "&7Kattints: &f%value%", "&7Kattints"));
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 1500) {
            warned.put(player.getUniqueId(), now);
            player.sendActionBar(Text.color(text(key)));
        }
    }

    /** Kétlépcsős megerősítés (15 mp-en belül ugyanaz kétszer). */
    boolean confirmed(Player player, String key) {
        long now = System.currentTimeMillis();
        if (key.equals(confirm.get(player.getUniqueId())) && confirmUntil.getOrDefault(player.getUniqueId(), 0L) > now) {
            confirm.remove(player.getUniqueId());
            confirmUntil.remove(player.getUniqueId());
            return true;
        }
        confirm.put(player.getUniqueId(), key);
        confirmUntil.put(player.getUniqueId(), now + 15_000L);
        return false;
    }

    // ---- telkek ----

    Plot plotAt(Location location) {
        if (world == null || location.getWorld() != world) {
            return null;
        }
        long key = grid.plotAt(location.getBlockX(), location.getBlockZ());
        return key == PlotGrid.NONE ? null : plots.get(key);
    }

    boolean onPlot(Location location) {
        if (world == null || location.getWorld() != world) {
            return false;
        }
        long key = grid.plotAt(location.getBlockX(), location.getBlockZ());
        return key != PlotGrid.NONE && key != hubKey;
    }

    boolean inHub(Location location) {
        return world != null && location.getWorld() == world && grid.plotAt(location.getBlockX(), location.getBlockZ()) == hubKey;
    }

    // ---- a központ (a telkek között) ----

    private int hubCenterX() {
        return grid.minX(PlotGrid.keyX(hubKey)) + grid.size / 2;
    }

    private int hubCenterZ() {
        return grid.minZ(PlotGrid.keyZ(hubKey)) + grid.size / 2;
    }

    /** A központ: itt érkezik mindenki, aki a Creative-ba jön. */
    Location hubSpawn() {
        return new Location(world, hubCenterX() + 0.5, grid.height + 1, hubCenterZ() + 11.5, 180, 0);
    }

    public void hub(Player player) {
        if (ready(player)) {
            teleport(player, hubSpawn());
        }
    }

    private void put(int x, int y, int z, String data) {
        world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(data), false);
    }

    /** A központ felépítése a központi telekre: szökőkút, utak, fák, lámpák, feliratok és két kattintható pont. */
    void buildHub() {
        int x0 = grid.minX(PlotGrid.keyX(hubKey));
        int z0 = grid.minZ(PlotGrid.keyZ(hubKey));
        int size = grid.size;
        int h = grid.height;
        int cx = hubCenterX();
        int cz = hubCenterZ();
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                for (int y = h + 1; y <= h + 10; y++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
                int dx = x - cx;
                int dz = z - cz;
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean path = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                String floor;
                if (d <= 4.5) {
                    floor = "water";
                    put(x, h - 1, z, "prismarine_bricks");
                } else if (d <= 5.5) {
                    floor = "dark_prismarine";
                    put(x, h + 1, z, "prismarine_brick_slab[type=bottom]");
                } else if (d <= 8.5) {
                    floor = "smooth_quartz";
                } else if (d <= 9.5) {
                    floor = "purpur_block";
                } else if (path) {
                    floor = Math.abs(dx) == 0 || Math.abs(dz) == 0 ? "purpur_block" : "smooth_quartz";
                } else if (d <= 12.5) {
                    floor = "polished_andesite";
                } else {
                    floor = "grass_block";
                    int hash = Math.floorMod(x * 7 + z * 13 + x * z, 41);
                    if (hash == 0) {
                        put(x, h + 1, z, "allium");
                    } else if (hash == 11) {
                        put(x, h + 1, z, "azure_bluet");
                    } else if (hash == 23) {
                        put(x, h + 1, z, "pink_tulip");
                    } else if (hash == 31 || hash == 37) {
                        put(x, h + 1, z, "short_grass");
                    }
                }
                put(x, h, z, floor);
            }
        }
        for (int y = h; y <= h + 3; y++) {
            put(cx, y, cz, "quartz_pillar[axis=y]");
        }
        put(cx, h + 4, cz, "sea_lantern");
        put(cx, h + 5, cz, "amethyst_cluster[facing=up]");
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int step : new int[]{12, 19}) {
                for (int side : new int[]{-2, 2}) {
                    int x = cx + dir[0] * step + (dir[0] == 0 ? side : 0);
                    int z = cz + dir[1] * step + (dir[1] == 0 ? side : 0);
                    put(x, h + 1, z, "dark_oak_fence");
                    put(x, h + 2, z, "dark_oak_fence");
                    put(x, h + 3, z, "lantern[hanging=false]");
                }
            }
        }
        for (int[] corner : new int[][]{{15, 15}, {-15, 15}, {15, -15}, {-15, -15}}) {
            int tx = cx + corner[0];
            int tz = cz + corner[1];
            for (int y = h + 1; y <= h + 5; y++) {
                put(tx, y, tz, "cherry_log[axis=y]");
            }
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    for (int ly = 4; ly <= 7; ly++) {
                        double r = Math.sqrt(lx * lx + lz * lz + (ly - 5.5) * (ly - 5.5));
                        if (r <= 2.6 && !(lx == 0 && lz == 0 && ly <= 5)) {
                            put(tx + lx, h + ly, tz + lz, "cherry_leaves[persistent=true]");
                        }
                    }
                }
            }
        }
        put(cx - 3, h + 1, cz + 7, "amethyst_block");
        put(cx + 3, h + 1, cz + 7, "purpur_pillar[axis=y]");
        for (int[] spot : grid.ringOf(PlotGrid.keyX(hubKey), PlotGrid.keyZ(hubKey))) {
            put(spot[0], h + 1, spot[1], "purpur_slab[type=bottom]");
        }
        spawnHubEntities();
    }

    /** A feliratok és a kattintható pontok (egyszer, a felépítéskor; újraépítésnél előbb a régieket töröljük). */
    void spawnHubEntities() {
        int cx = hubCenterX();
        int cz = hubCenterZ();
        int h = grid.height;
        BoundingBox box = new BoundingBox(cx - 30, h - 2, cz - 30, cx + 30, h + 20, cz + 30);
        for (Entity entity : world.getNearbyEntities(box)) {
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith("icemine_creative_hub")) {
                    entity.remove();
                    break;
                }
            }
        }
        label(new Location(world, cx + 0.5, h + 7.2, cz + 0.5), text("hub-title"), 1.6f);
        label(new Location(world, cx - 2.5, h + 2.9, cz + 7.5), text("hub-auto"), 1.0f);
        label(new Location(world, cx + 3.5, h + 2.9, cz + 7.5), text("hub-menu"), 1.0f);
        clickable(new Location(world, cx - 2.5, h + 2, cz + 7.5), "icemine_creative_hub:auto");
        clickable(new Location(world, cx + 3.5, h + 2, cz + 7.5), "icemine_creative_hub:menu");
    }

    private void label(Location at, String text, float scale) {
        world.spawn(at, org.bukkit.entity.TextDisplay.class, display -> {
            display.setText(Text.color(text.replace("\\n", "\n")));
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setPersistent(true);
            display.addScoreboardTag("icemine_creative_hub");
            try {
                org.bukkit.util.Transformation transformation = display.getTransformation();
                Object vector = org.bukkit.util.Transformation.class.getMethod("getScale").invoke(transformation);
                vector.getClass().getMethod("set", float.class).invoke(vector, scale);
                display.setTransformation(transformation);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // ha nem sikerül, marad az alap méret
            }
        });
    }

    private void clickable(Location at, String tag) {
        world.spawn(at, org.bukkit.entity.Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.2f);
            interaction.setInteractionHeight(1.4f);
            interaction.setResponsive(true);
            interaction.setPersistent(true);
            interaction.addScoreboardTag("icemine_creative_hub");
            interaction.addScoreboardTag(tag);
        });
    }

    List<Plot> plotsOf(UUID player) {
        List<Plot> result = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.owner.equals(player)) {
                result.add(plot);
            }
        }
        result.sort((a, b) -> Long.compare(a.created, b.created));
        return result;
    }

    java.util.Collection<Plot> plots() {
        return plots.values();
    }

    /** Hány telke lehet: az alap, vagy a rangja (és a gyengébb rangok) közül a legtöbb. */
    int limit(Player player) {
        if (player.hasPermission("icemine.creative.unlimited")) {
            return Integer.MAX_VALUE;
        }
        int limit = defaultLimit;
        hu.icemine.core.rank.Ranks.Rank mine = core.ranks().of(player);
        int order = mine == null ? 0 : mine.order();
        for (hu.icemine.core.rank.Ranks.Rank rank : core.ranks().all()) {
            Integer value = rankLimits.get(rank.id);
            if (value != null && rank.order() <= order) {
                limit = Math.max(limit, value);
            }
        }
        return Math.min(limit, maxLimit);
    }

    String limitText(Player player) {
        int limit = limit(player);
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }

    private boolean builder(Player player) {
        return player.hasPermission("icemine.admin.creative");
    }

    /** A WorldEdit ide írhat-e a játékos nevében: a saját (vagy felvett) telkén, a bedrock fölött. */
    boolean canEdit(UUID player, int x, int y, int z) {
        if (world == null || y <= grid.height - 6) {
            return false;
        }
        long key = grid.plotAt(x, z);
        Plot plot = key == PlotGrid.NONE ? null : plots.get(key);
        return plot != null && !plot.clearing && plot.canBuild(player);
    }

    boolean isBlocked(Material material) {
        return blocked.contains(material);
    }

    // ---- WorldEdit ----

    private void loadWorldEdit(YamlConfiguration config) {
        weEnabled = config.getBoolean("worldedit.enabled", true);
        weMinPlaytime = Math.max(0, config.getLong("worldedit.min-playtime-minutes", 120)) * 60_000L;
        weMaxBlocks = Math.max(100, config.getInt("worldedit.max-blocks", 20000));
        weMaxRadius = Math.max(1, config.getInt("worldedit.max-radius", 20));
        weMaxCount = Math.max(1, config.getInt("worldedit.max-count", 10));
        wePermissions = config.isSet("worldedit.permissions") ? config.getStringList("worldedit.permissions")
                : config.getDefaults() == null ? new ArrayList<>() : config.getDefaults().getStringList("worldedit.permissions");
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null && weEnabled && plugin != null && plugin.isEnabled()) {
            try {
                worldEdit = new CreativeWorldEdit(this);
                core.getLogger().info("Creative: WorldEdit megtalálva, a telkeken a 2 óránál többet játszott játékosok használhatják.");
            } catch (Throwable error) {
                worldEdit = null;
                core.getLogger().warning("Creative: a WorldEdit fent van, de nem tudok rákapcsolódni (" + error + ")");
            }
        }
        if (worldEdit != null && !weEnabled) {
            worldEdit.unregister();
            worldEdit = null;
        }
    }

    /** Hány perc játékidő hiányzik még a WorldEdithez (0, ha már megvan). */
    long weMissing(Player player) {
        hu.icemine.core.data.PlayerProfile profile = core.profiles().profile(player);
        long played = profile == null ? 0 : profile.playtime();
        return Math.max(0, weMinPlaytime - played);
    }

    private void updateWorldEdit(Player player) {
        if (worldEdit == null) {
            return;
        }
        boolean eligible = inWorld(player) && !builder(player) && weMissing(player) == 0;
        boolean had = worldEdit.has(player);
        worldEdit.update(player, eligible, wePermissions, weMaxBlocks);
        if (eligible && !had) {
            send(player, "worldedit-on");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (!message.startsWith("//") && !message.toLowerCase(Locale.ROOT).startsWith("/worldedit:")) {
            return;
        }
        if (!inWorld(player) || builder(player)) {
            if (!builder(player) && !player.hasPermission("worldedit.*") && worldEdit != null && worldEdit.has(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (worldEdit == null || !worldEdit.has(player)) {
            long missing = weMissing(player);
            if (missing > 0) {
                event.setCancelled(true);
                send(player, "worldedit-playtime", "%time%", Text.duration(missing));
            }
            return;
        }
        String[] parts = message.substring(message.startsWith("//") ? 2 : 1).split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT).replace("worldedit:", "").replace("/", "");
        boolean radiusCommand = command.equals("sphere") || command.equals("hsphere") || command.equals("cyl") || command.equals("hcyl")
                || command.equals("pyramid") || command.equals("hpyramid");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("-")) {
                continue;
            }
            for (String piece : part.split(",")) {
                try {
                    double number = Double.parseDouble(piece);
                    if (radiusCommand && i >= 2 && number > weMaxRadius) {
                        event.setCancelled(true);
                        send(player, "worldedit-too-big", "%max%", String.valueOf(weMaxRadius));
                        return;
                    }
                    if (command.equals("stack") && i == 1 && number > weMaxCount) {
                        event.setCancelled(true);
                        send(player, "worldedit-too-many", "%max%", String.valueOf(weMaxCount));
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // nem szám (pl. blokk minta)
                }
            }
        }
    }

    boolean canBuild(Player player, Location location) {
        if (builder(player)) {
            return true;
        }
        Plot plot = plotAt(location);
        return plot != null && !plot.clearing && plot.canBuild(player.getUniqueId()) && location.getBlockY() > grid.height - 6;
    }

    Location home(Plot plot) {
        double x = grid.minX(plot.px) + grid.size / 2.0;
        double z = grid.minZ(plot.pz) - 2.5;
        return new Location(world, x, grid.height + 1, z, 0, 10);
    }

    void teleport(Player player, Location location) {
        core.guard().allowNext(player);
        player.setFallDistance(0f);
        player.teleport(location);
    }

    private boolean ready(Player player) {
        if (world == null) {
            core.messages().send(player, "mode.unavailable", "%mode%", "Creative");
            return false;
        }
        if (!inWorld(player)) {
            core.modes().sendAndReport(player, ID);
            return inWorld(player);
        }
        return true;
    }

    private void paintBorder(Plot plot, Material material) {
        int y = grid.height + 1;
        for (int[] spot : grid.ringOf(plot.px, plot.pz)) {
            Block block = world.getBlockAt(spot[0], y, spot[1]);
            if (block.getType() == generator.border || block.getType() == claimedBorder || block.getType().isAir()) {
                block.setType(material, false);
            }
        }
    }

    private Plot claimAt(Player player, int px, int pz) {
        Plot plot = new Plot(px, pz, player.getUniqueId(), player.getName());
        plot.created = System.currentTimeMillis();
        plots.put(plot.key(), plot);
        store.save(plot);
        paintBorder(plot, claimedBorder);
        return plot;
    }

    private boolean underLimit(Player player) {
        int have = plotsOf(player.getUniqueId()).size();
        if (have >= limit(player)) {
            send(player, "limit", "%max%", limitText(player));
            return false;
        }
        return true;
    }

    /** /plot auto: a legközelebbi szabad telek a központ körül. */
    public void auto(Player player) {
        if (!ready(player) || !underLimit(player)) {
            return;
        }
        for (int i = 0; i < 1_000_000; i++) {
            int[] spot = PlotGrid.spiral(i);
            long key = PlotGrid.key(spot[0], spot[1]);
            if (key != hubKey && !plots.containsKey(key)) {
                Plot plot = claimAt(player, spot[0], spot[1]);
                teleport(player, home(plot));
                send(player, "claimed", "%x%", String.valueOf(plot.px), "%z%", String.valueOf(plot.pz));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                return;
            }
        }
    }

    /** /plot claim: az a telek, amin állsz. */
    public void claim(Player player) {
        if (!ready(player)) {
            return;
        }
        long key = grid.plotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (key == PlotGrid.NONE) {
            send(player, "not-on-plot");
            return;
        }
        if (key == hubKey) {
            send(player, "hub-reserved");
            return;
        }
        if (plots.containsKey(key)) {
            send(player, "taken", "%owner%", plots.get(key).ownerName);
            return;
        }
        if (!underLimit(player)) {
            return;
        }
        Plot plot = claimAt(player, PlotGrid.keyX(key), PlotGrid.keyZ(key));
        send(player, "claimed", "%x%", String.valueOf(plot.px), "%z%", String.valueOf(plot.pz));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    public void home(Player player, String which) {
        if (!ready(player)) {
            return;
        }
        List<Plot> mine = plotsOf(player.getUniqueId());
        if (mine.isEmpty()) {
            click(player, "no-plot");
            return;
        }
        int index = 1;
        if (which != null) {
            try {
                index = Integer.parseInt(which);
            } catch (NumberFormatException exception) {
                for (int i = 0; i < mine.size(); i++) {
                    if (mine.get(i).name.equalsIgnoreCase(which)) {
                        index = i + 1;
                    }
                }
            }
        }
        if (index < 1 || index > mine.size()) {
            send(player, "no-such-home", "%max%", String.valueOf(mine.size()));
            return;
        }
        teleport(player, home(mine.get(index - 1)));
    }

    public void visit(Player player, String name, String which) {
        if (world == null) {
            core.messages().send(player, "mode.unavailable", "%mode%", "Creative");
            return;
        }
        List<Plot> list = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.ownerName.equalsIgnoreCase(name) || plot.name.equalsIgnoreCase(name)) {
                list.add(plot);
            }
        }
        list.sort((a, b) -> Long.compare(a.created, b.created));
        if (list.isEmpty()) {
            send(player, "visit-none", "%player%", name);
            return;
        }
        int index = 1;
        if (which != null) {
            try {
                index = Math.max(1, Math.min(list.size(), Integer.parseInt(which)));
            } catch (NumberFormatException ignored) {
                index = 1;
            }
        }
        Plot plot = list.get(index - 1);
        if (plot.denied.containsKey(player.getUniqueId()) && !builder(player)) {
            send(player, "denied-you", "%plot%", plot.display());
            return;
        }
        if (!ready(player)) {
            return;
        }
        teleport(player, home(plot));
        send(player, "visiting", "%plot%", plot.display());
    }

    private Plot ownedHere(Player player) {
        Plot plot = plotAt(player.getLocation());
        if (plot == null) {
            send(player, onPlot(player.getLocation()) ? "plot-free" : "not-on-plot");
            return null;
        }
        if (!plot.owner.equals(player.getUniqueId()) && !builder(player)) {
            send(player, "not-yours");
            return null;
        }
        return plot;
    }

    public void info(Player player) {
        Plot plot = plotAt(player.getLocation());
        if (plot == null) {
            send(player, onPlot(player.getLocation()) ? "plot-free" : "not-on-plot");
            return;
        }
        for (String line : texts("info", "%plot%", plot.display(), "%owner%", plot.ownerName, "%x%", String.valueOf(plot.px), "%z%", String.valueOf(plot.pz),
                "%trusted%", plot.trusted.isEmpty() ? "-" : String.join(", ", plot.trusted.values()),
                "%denied%", plot.denied.isEmpty() ? "-" : String.join(", ", plot.denied.values()),
                "%date%", new java.text.SimpleDateFormat("yyyy.MM.dd.").format(new java.util.Date(plot.created)))) {
            player.sendMessage(Text.color(line));
        }
    }

    private UUID lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    private static UUID byName(Map<UUID, String> map, String name) {
        for (Map.Entry<UUID, String> entry : map.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void trust(Player player, String name, boolean add) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        if (add) {
            UUID id = lookup(name);
            if (id == null) {
                send(player, "unknown-player", "%player%", name);
                return;
            }
            if (id.equals(plot.owner)) {
                send(player, "self");
                return;
            }
            Player online = Bukkit.getPlayer(id);
            String real = online != null ? online.getName() : name;
            plot.trusted.put(id, real);
            plot.denied.remove(id);
            store.save(plot);
            send(player, "trusted", "%player%", real);
            if (online != null) {
                send(online, "trusted-you", "%owner%", player.getName(), "%plot%", plot.display());
            }
            return;
        }
        UUID id = byName(plot.trusted, name);
        if (id == null) {
            send(player, "not-trusted", "%player%", name);
            return;
        }
        String removed = plot.trusted.remove(id);
        store.save(plot);
        send(player, "untrusted", "%player%", removed);
    }

    public void deny(Player player, String name, boolean add) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        if (!add) {
            UUID id = byName(plot.denied, name);
            if (id == null) {
                send(player, "not-denied", "%player%", name);
                return;
            }
            String removed = plot.denied.remove(id);
            store.save(plot);
            send(player, "undenied", "%player%", removed);
            return;
        }
        UUID id = lookup(name);
        if (id == null) {
            send(player, "unknown-player", "%player%", name);
            return;
        }
        if (id.equals(plot.owner)) {
            send(player, "self");
            return;
        }
        Player online = Bukkit.getPlayer(id);
        String real = online != null ? online.getName() : name;
        plot.denied.put(id, real);
        plot.trusted.remove(id);
        store.save(plot);
        send(player, "denied", "%player%", real);
        if (online != null && plotAt(online.getLocation()) == plot && !builder(online)) {
            kickOff(online, plot);
            send(online, "denied-you", "%plot%", plot.display());
        }
    }

    public void kick(Player player, String name) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || plotAt(target.getLocation()) != plot || target == player) {
            send(player, "kick-none", "%player%", name);
            return;
        }
        if (builder(target)) {
            send(player, "kick-none", "%player%", name);
            return;
        }
        kickOff(target, plot);
        send(player, "kicked", "%player%", target.getName());
        send(target, "kicked-you", "%plot%", plot.display());
    }

    private void kickOff(Player target, Plot plot) {
        teleport(target, home(plot).add(0, 0, -2));
    }

    public void rename(Player player, String name) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        if (name == null || name.length() < 2 || name.length() > 24 || !name.matches("[\\p{L}0-9_ !.\\-]+")) {
            send(player, "name-bad");
            return;
        }
        plot.name = name;
        store.save(plot);
        send(player, "named", "%name%", name);
    }

    public void clear(Player player) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        if (plot.clearing) {
            send(player, "clearing");
            return;
        }
        if (!confirmed(player, "clear:" + plot.id())) {
            send(player, "clear-confirm");
            return;
        }
        regenerate(plot, () -> send(player, "cleared"));
        send(player, "clear-started");
    }

    public void delete(Player player) {
        Plot plot = ownedHere(player);
        if (plot == null) {
            return;
        }
        if (!confirmed(player, "delete:" + plot.id())) {
            send(player, "delete-confirm");
            return;
        }
        remove(plot);
        send(player, "deleted");
    }

    void remove(Plot plot) {
        plots.remove(plot.key());
        store.delete(plot);
        regenerate(plot, () -> paintBorder(plot, generator.border));
    }

    /** A telek visszaállítása az eredeti állapotba, részletekben (tickenként néhány oszlop), hogy ne akadjon a szerver. */
    void regenerate(Plot plot, Runnable done) {
        plot.clearing = true;
        int x0 = grid.minX(plot.px);
        int z0 = grid.minZ(plot.pz);
        int size = grid.size;
        BoundingBox box = new BoundingBox(x0, world.getMinHeight(), z0, x0 + size, world.getMaxHeight(), z0 + size);
        for (Entity entity : world.getNearbyEntities(box)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        int[] next = {0};
        int total = size * size;
        int bottom = grid.height - 5;
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            int end = Math.min(total, next[0] + 48);
            for (int i = next[0]; i < end; i++) {
                int x = x0 + i % size;
                int z = z0 + i / size;
                int top = Math.max(grid.height + 1, world.getHighestBlockYAt(x, z));
                for (int y = bottom; y <= top; y++) {
                    Material wanted = generator.column(x, y, z);
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != wanted) {
                        block.setType(wanted, false);
                    }
                }
            }
            next[0] = end;
            if (end < total) {
                clearJobs.addLast(step[0]);
            } else {
                plot.clearing = false;
                if (done != null) {
                    done.run();
                }
            }
        };
        clearJobs.addLast(step[0]);
    }

    /**
     * Ha a központ helyén már volt egy lefoglalt telek: a tulajdonosa kap egy új telket, oda átmásoljuk a blokkokat
     * (részletekben), aztán a régi helyet kiürítjük, és utána jöhet a központ.
     */
    void relocate(Plot old, Runnable then) {
        Plot moved = null;
        for (int i = 1; i < 1_000_000 && moved == null; i++) {
            int[] spot = PlotGrid.spiral(i);
            long key = PlotGrid.key(spot[0], spot[1]);
            if (key != hubKey && !plots.containsKey(key)) {
                moved = new Plot(spot[0], spot[1], old.owner, old.ownerName);
            }
        }
        if (moved == null) {
            return;
        }
        Plot target = moved;
        target.name = old.name;
        target.created = old.created;
        target.trusted.putAll(old.trusted);
        target.denied.putAll(old.denied);
        plots.remove(old.key());
        store.delete(old);
        plots.put(target.key(), target);
        store.save(target);
        paintBorder(target, claimedBorder);
        core.getLogger().info("Creative: " + old.ownerName + " telke a központ helyén volt, átköltöztettem ide: " + target.id());
        int fromX = grid.minX(old.px);
        int fromZ = grid.minZ(old.pz);
        int toX = grid.minX(target.px);
        int toZ = grid.minZ(target.pz);
        int size = grid.size;
        int bottom = grid.height - 5;
        int total = size * size;
        int[] next = {0};
        target.clearing = true;
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            int end = Math.min(total, next[0] + 32);
            for (int i = next[0]; i < end; i++) {
                int dx = i % size;
                int dz = i / size;
                int top = Math.max(grid.height + 1, world.getHighestBlockYAt(fromX + dx, fromZ + dz));
                for (int y = bottom; y <= top; y++) {
                    Block from = world.getBlockAt(fromX + dx, y, fromZ + dz);
                    Block to = world.getBlockAt(toX + dx, y, toZ + dz);
                    if (!to.getBlockData().equals(from.getBlockData())) {
                        to.setBlockData(from.getBlockData(), false);
                    }
                }
            }
            next[0] = end;
            if (end < total) {
                clearJobs.addLast(step[0]);
            } else {
                target.clearing = false;
                Plot ghost = new Plot(old.px, old.pz, old.owner, old.ownerName);
                regenerate(ghost, then);
            }
        };
        clearJobs.addLast(step[0]);
    }

    /** Tickenként: a telek törlések következő darabja. */
    public void tick() {
        if (++tickCount % 100 == 0 && world != null && worldEdit != null) {
            for (Player player : world.getPlayers()) {
                updateWorldEdit(player);
            }
        }
        int jobs = clearJobs.size();
        for (int i = 0; i < Math.min(jobs, 3); i++) {
            Runnable job = clearJobs.pollFirst();
            if (job != null) {
                job.run();
            }
        }
    }

    // ---- menü ----

    public void menu(Player player) {
        if (!ready(player)) {
            return;
        }
        Menu menu = new Menu("creative-main", 4, text("menu.title"));
        menu.frame(Material.PURPLE_STAINED_GLASS_PANE, null);
        List<Plot> mine = plotsOf(player.getUniqueId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < mine.size() && i < slots.length; i++) {
            Plot plot = mine.get(i);
            int index = i + 1;
            menu.set(slots[i], Items.of(Material.GRASS_BLOCK).name("&a&l" + plot.display())
                    .lore(texts("menu.plot-lore", "%x%", String.valueOf(plot.px), "%z%", String.valueOf(plot.pz), "%trusted%", String.valueOf(plot.trusted.size()))).build(),
                    clicker -> {
                        clicker.closeInventory();
                        home(clicker, String.valueOf(index));
                    });
        }
        if (mine.isEmpty()) {
            menu.set(13, Items.of(Material.PAPER).name(text("menu.no-plot")).lore(texts("menu.no-plot-lore")).build());
        }
        menu.set(29, Items.of(Material.EMERALD).name(text("menu.auto")).lore(texts("menu.auto-lore", "%have%", String.valueOf(mine.size()), "%max%", limitText(player))).build(), clicker -> {
            clicker.closeInventory();
            auto(clicker);
        });
        menu.set(31, Items.of(Material.ENDER_EYE).name(text("menu.visit")).lore(texts("menu.visit-lore")).build(), this::visitMenu);
        menu.set(33, Items.of(Material.BOOK).name(text("menu.help")).lore(texts("menu.help-lore")).build(), clicker -> {
            clicker.closeInventory();
            help(clicker);
        });
        menu.open(player);
    }

    private void visitMenu(Player player) {
        Menu menu = new Menu("creative-visit", 4, text("menu.visit-title"));
        menu.frame(Material.PURPLE_STAINED_GLASS_PANE, null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int i = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (i >= slots.length) {
                break;
            }
            if (online == player || plotsOf(online.getUniqueId()).isEmpty()) {
                continue;
            }
            String name = online.getName();
            menu.set(slots[i++], Items.head(online).name("&f" + name).lore(texts("menu.visit-entry", "%plots%", String.valueOf(plotsOf(online.getUniqueId()).size()))).build(),
                    clicker -> {
                        clicker.closeInventory();
                        visit(clicker, name, null);
                    });
        }
        if (i == 0) {
            menu.set(13, Items.of(Material.PAPER).name(text("menu.visit-empty")).build());
        }
        menu.set(31, Items.of(Material.ARROW).name(text("menu.back")).build(), this::menu);
        menu.open(player);
    }

    void help(Player player) {
        for (String line : texts("help")) {
            player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
        }
    }

    // ---- ModeHandler ----

    @Override
    public Location spawn(Player player) {
        if (world == null) {
            return null;
        }
        return hubSpawn();
    }

    @Override
    public void enter(Player player) {
        boolean restored = core.inventories().restore(ID, player);
        if (!restored) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            for (String line : texts("first-join", "%player%", player.getName())) {
                player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
            }
        } else {
            send(player, "welcome");
        }
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFoodLevel(20);
        if (plotsOf(player.getUniqueId()).isEmpty()) {
            click(player, "no-plot");
        }
        updateWorldEdit(player);
    }

    @Override
    public void leave(Player player, boolean quitting) {
        core.inventories().save(ID, player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        lastPlot.remove(player.getUniqueId());
        if (worldEdit != null) {
            worldEdit.revoke(player);
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        Plot here = plotAt(player.getLocation());
        values.put("%cr_plot%", inHub(player.getLocation()) ? text("hub-name") : here == null ? (onPlot(player.getLocation()) ? text("free") : "-") : here.display());
        values.put("%cr_owner%", here == null ? "-" : here.ownerName);
        values.put("%cr_plots%", plotsOf(player.getUniqueId()).size() + "/" + limitText(player));
    }

    // ---- védelem ----

    private boolean here(Location location) {
        return world != null && location.getWorld() == world;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!here(event.getBlock().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (blocked.contains(event.getBlock().getType()) && !builder(player)) {
            event.setCancelled(true);
            warn(player, "blocked-item");
            return;
        }
        if (!canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(player, "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (here(event.getBlock().getLocation()) && !canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (here(event.getBlock().getLocation()) && !canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        if (here(event.getBlock().getLocation()) && !canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!here(player.getLocation())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && blocked.contains(item.getType()) && !builder(player)) {
            event.setCancelled(true);
            warn(player, "blocked-item");
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (block.getType() == Material.ENDER_CHEST && !builder(player)) {
            event.setCancelled(true);
            warn(player, "no-enderchest");
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.PHYSICAL) && !canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                warn(player, "no-build");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.Interaction && here(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Set<String> tags = event.getRightClicked().getScoreboardTags();
            if (tags.contains("icemine_creative_hub:auto")) {
                List<Plot> mine = plotsOf(event.getPlayer().getUniqueId());
                if (mine.size() >= limit(event.getPlayer()) && !mine.isEmpty()) {
                    home(event.getPlayer(), null);
                } else {
                    auto(event.getPlayer());
                }
            } else if (tags.contains("icemine_creative_hub:menu")) {
                menu(event.getPlayer());
            }
            return;
        }
        if (here(event.getRightClicked().getLocation()) && !(event.getRightClicked() instanceof Player)
                && !canBuild(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!here(event.getEntity().getLocation())) {
            return;
        }
        Player attacker = hu.icemine.core.combat.CombatTag.attacker(event.getDamager());
        if (event.getEntity() instanceof Player || attacker == null || !canBuild(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!here(event.getEntity().getLocation())) {
            return;
        }
        if (!(event.getRemover() instanceof Player player) || !canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean crowded(Location location) {
        if (entityLimit <= 0) {
            return false;
        }
        long key = grid.plotAt(location.getBlockX(), location.getBlockZ());
        if (key == PlotGrid.NONE) {
            return true;
        }
        int x0 = grid.minX(PlotGrid.keyX(key));
        int z0 = grid.minZ(PlotGrid.keyZ(key));
        BoundingBox box = new BoundingBox(x0, world.getMinHeight(), z0, x0 + grid.size, world.getMaxHeight(), z0 + grid.size);
        int count = 0;
        for (Entity entity : world.getNearbyEntities(box)) {
            if (!(entity instanceof Player)) {
                count++;
            }
        }
        return count >= entityLimit;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (here(event.getEntity().getLocation()) && event.getPlayer() != null && !builder(event.getPlayer())
                && (!canBuild(event.getPlayer(), event.getEntity().getLocation()) || crowded(event.getEntity().getLocation()))) {
            event.setCancelled(true);
            warn(event.getPlayer(), "entity-limit");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (here(event.getEntity().getLocation()) && event.getPlayer() != null && !builder(event.getPlayer())
                && (!canBuild(event.getPlayer(), event.getEntity().getLocation()) || crowded(event.getEntity().getLocation()))) {
            event.setCancelled(true);
            warn(event.getPlayer(), "entity-limit");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!here(event.getLocation())) {
            return;
        }
        switch (event.getSpawnReason()) {
            case SPAWNER_EGG, DISPENSE_EGG, BUILD_SNOWMAN, BUILD_IRONGOLEM, BREEDING, EGG, SLIME_SPLIT, CUSTOM, COMMAND -> {
                if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND
                        && crowded(event.getLocation())) {
                    event.setCancelled(true);
                }
            }
            default -> event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        if (here(event.getLocation())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(BlockExplodeEvent event) {
        if (here(event.getBlock().getLocation())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrime(ExplosionPrimeEvent event) {
        if (here(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        if (here(from.getLocation()) && grid.plotAt(from.getX(), from.getZ()) != grid.plotAt(to.getX(), to.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Block from = event.getSource();
        Block to = event.getBlock();
        if (here(to.getLocation()) && grid.plotAt(from.getX(), from.getZ()) != grid.plotAt(to.getX(), to.getZ())) {
            event.setCancelled(true);
        }
    }

    private boolean crossing(Block piston, List<Block> moved, org.bukkit.block.BlockFace face) {
        long home = grid.plotAt(piston.getX(), piston.getZ());
        for (Block block : moved) {
            Block target = block.getRelative(face);
            if (grid.plotAt(block.getX(), block.getZ()) != home || grid.plotAt(target.getX(), target.getZ()) != home) {
                return true;
            }
        }
        Block head = piston.getRelative(face);
        return grid.plotAt(head.getX(), head.getZ()) != home;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (here(event.getBlock().getLocation()) && crossing(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        if (here(event.getBlock().getLocation()) && crossing(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (here(event.getPlayer().getLocation())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !here(player.getLocation()) || builder(player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && blocked.contains(cursor.getType())) {
            event.setCancelled(true);
            warn(player, "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !here(to)) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        long key = grid.plotAt(to.getBlockX(), to.getBlockZ());
        Plot plot = key == PlotGrid.NONE ? null : plots.get(key);
        if (plot != null && plot.denied.containsKey(player.getUniqueId()) && !builder(player)) {
            event.setTo(from.clone());
            warn(player, "denied-enter");
            return;
        }
        Long previous = lastPlot.get(player.getUniqueId());
        if (previous == null || previous != key) {
            lastPlot.put(player.getUniqueId(), key);
            if (key == hubKey) {
                player.sendActionBar(Text.color(text("enter-hub")));
            } else if (key != PlotGrid.NONE) {
                player.sendActionBar(Text.color(plot == null ? text("enter-free") : text("enter-plot", "%plot%", plot.display(), "%owner%", plot.ownerName)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastPlot.remove(id);
        confirm.remove(id);
        confirmUntil.remove(id);
        warned.remove(id);
    }

    @Override
    public void shutdown() {
        while (!clearJobs.isEmpty()) {
            Runnable job = clearJobs.pollFirst();
            if (job != null) {
                job.run();
            }
        }
    }
}
