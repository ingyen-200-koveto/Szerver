package hu.icemine.kingdoms;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.Upgrades;
import hu.icemine.core.module.Module;
import hu.icemine.core.util.ClickText;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * A Kingdoms mód: saját (generált) világ, királyi város a spawnon, királyságok rangokkal, földekkel, Nexusszal, bankkal,
 * Resource Pointtal, kapcsolatokkal, nemzetekkel, inváziókkal, tornyokkal és építményekkel. Az adatok az IceMine
 * adatbázisában vannak (SQLite vagy MySQL).
 */
public final class IceMineKingdoms extends Module {

    private static final String OBJECTIVE = "kingdoms";
    private static final String SUFFIX = "0123456789abcdef";

    private final KConfig settings = new KConfig();
    private final Map<UUID, String> lastArea = new HashMap<>();
    private final Map<UUID, Long> lastChunk = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private KingdomStore store;
    private KingdomManager manager;
    private Structures structures;
    private Invasions invasions;
    private Borders borders;
    private KingdomMenus menus;
    private Donations donations;
    private World world;
    private Location spawn;
    private int townX;
    private int townZ;
    private InventoryStore inventories;
    private boolean enabled;

    public IceMineKingdoms(JavaPlugin plugin) {
        super(plugin, OBJECTIVE, "kingdoms.yml");
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String note : new Upgrades(plugin().getDataFolder(), plugin()::getResource).apply(java.util.Set.of("kingdoms"), new Upgrades.Target() {
            @Override
            public YamlConfiguration config(String file) {
                return getConfig() instanceof YamlConfiguration yaml ? yaml : null;
            }

            @Override
            public void save(String file) {
                saveConfig();
            }
        })) {
            getLogger().info("Config frissítve: " + note);
        }
        List<String> problems = new ArrayList<>();
        settings.load(getConfig(), problems);
        for (String problem : problems) {
            getLogger().warning(problem);
        }
        inventories = new InventoryStore(new File(getDataFolder(), "inventories"), getLogger());
        world = loadWorld();
        if (world == null) {
            getLogger().severe("Nem sikerült betölteni a Kingdoms világot, a Kingdoms mód nem indul el.");
            return;
        }
        prepareWorld();
        store = new KingdomStore(core().database(), getLogger());
        manager = new KingdomManager(this, store);
        structures = new Structures(this);
        invasions = new Invasions(this);
        borders = new Borders(this);
        menus = new KingdomMenus(this);
        donations = new Donations(this);
        try {
            store.init();
            KingdomStore.Loaded loaded = store.loadAll(settings.defaultRanks);
            File legacy = new File(getDataFolder(), "kingdoms.yml");
            if (loaded.kingdoms.isEmpty() && legacy.isFile()) {
                loaded = KingdomStore.convertLegacy(legacy, settings.defaultRanks, getLogger());
                manager.load(loaded);
                manager.saveAll();
                getLogger().info("Kingdoms: a régi mentésből átvettem " + loaded.kingdoms.size() + " királyságot.");
            } else {
                manager.load(loaded);
            }
        } catch (Exception exception) {
            getLogger().severe("Kingdoms: nem sikerült betölteni az adatokat, a mód nem indul el (" + exception.getMessage() + ")");
            return;
        }
        structures.rebuild();
        invasions.loadMasswar();
        KingdomCommands commands = new KingdomCommands(this);
        for (String name : new String[]{"k", "kadmin"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
        }
        for (org.bukkit.event.Listener listener : new org.bukkit.event.Listener[]{new KingdomListener(this), structures, invasions, borders, donations}) {
            getServer().getPluginManager().registerEvents(listener, plugin());
        }
        startTasks();
        for (Player player : world.getPlayers()) {
            enter(player);
        }
        enabled = true;
        getLogger().info("Kingdoms elindult, " + manager.all().size() + " királyság és " + manager.nations().size() + " nemzet betöltve.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (invasions != null) {
            invasions.stopAll();
        }
        if (structures != null) {
            structures.removeSoldiers();
        }
        if (world != null && inventories != null) {
            for (Player player : world.getPlayers()) {
                inventories.save(player);
            }
        }
        if (manager != null) {
            manager.saveDirty();
        }
    }

    // ---- szolgáltatások ----

    public IceMineCore core() {
        return (IceMineCore) plugin();
    }

    public KConfig settings() {
        return settings;
    }

    public KingdomManager manager() {
        return manager;
    }

    public Structures structures() {
        return structures;
    }

    public Invasions invasions() {
        return invasions;
    }

    public Borders borders() {
        return borders;
    }

    public KingdomMenus menus() {
        return menus;
    }

    public Donations donations() {
        return donations;
    }

    public World world() {
        return world;
    }

    Location spawn() {
        return spawn;
    }

    boolean inKingdoms(Entity entity) {
        return world != null && entity.getWorld() == world;
    }

    // ---- világ ----

    private World loadWorld() {
        String name = getConfig().getString("world", "kingdoms");
        World loaded = Bukkit.getWorld(name);
        return loaded != null ? loaded : new WorldCreator(name).createWorld();
    }

    private void prepareWorld() {
        world.setDifficulty(Difficulty.NORMAL);
        world.setPVP(true);
        world.setSpawnFlags(getConfig().getBoolean("spawn-monsters", true), getConfig().getBoolean("spawn-animals", true));
        ConfigurationSection rules = getConfig().getConfigurationSection("gamerules");
        if (rules != null) {
            for (String rule : rules.getKeys(false)) {
                String value = rules.getString(rule);
                if (value != null && !world.setGameRuleValue(rule, value)) {
                    getLogger().warning("Ismeretlen gamerule: " + rule);
                }
            }
        }
        FileConfiguration config = getConfig();
        if (!config.getBoolean("spawn.built", false)) {
            buildSpawn(config);
        }
        townX = config.getInt("spawn.town-x", 0);
        townZ = config.getInt("spawn.town-z", 0);
        spawn = new Location(world, config.getDouble("spawn.x", townX + 0.5), config.getDouble("spawn.y", 80), config.getDouble("spawn.z", townZ + 0.5),
                (float) config.getDouble("spawn.yaw", 180), 0f);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        world.getWorldBorder().setCenter(townX + 0.5, townZ + 0.5);
        world.getWorldBorder().setSize(config.getInt("border-size", 6000));
    }

    private void buildSpawn(FileConfiguration config) {
        Location origin = world.getSpawnLocation();
        int tx = config.contains("spawn.town-x") ? config.getInt("spawn.town-x", 0) : origin.getBlockX();
        int tz = config.contains("spawn.town-z") ? config.getInt("spawn.town-z", 0) : origin.getBlockZ();
        List<Integer> heights = new ArrayList<>();
        int r = SpawnTown.RADIUS;
        for (int dx = -r; dx <= r; dx += 5) {
            for (int dz = -r; dz <= r; dz += 5) {
                if (dx * dx + dz * dz <= r * r) {
                    heights.add(groundAt(tx + dx, tz + dz));
                }
            }
        }
        Collections.sort(heights);
        int surface = Math.max(64, Math.min(110, heights.get(heights.size() / 2)));
        Map<String, BlockData> cache = new HashMap<>();
        SpawnTown.Ground ground = new SpawnTown.Ground() {
            @Override
            public int at(int x, int z) {
                return groundAt(tx + x, tz + z);
            }

            @Override
            public String top(int x, int z) {
                return surfaceOf(tx + x, tz + z);
            }
        };
        new SpawnTown((x, y, z, data) -> world.getBlockAt(x, y, z).setBlockData(blockData(cache, data), false), tx, surface, tz, ground).build();
        config.set("spawn.built", true);
        config.set("spawn.town-x", tx);
        config.set("spawn.town-z", tz);
        config.set("spawn.x", tx + 0.5);
        config.set("spawn.y", surface + 1.0);
        config.set("spawn.z", tz + SpawnTown.SPAWN_Z + 0.5);
        config.set("spawn.yaw", 180.0);
        saveConfig();
        getLogger().info("Felépült a királyi város (" + tx + ", " + surface + ", " + tz + ").");
    }

    void rebuildSpawn() {
        getConfig().set("spawn.built", false);
        prepareWorld();
    }

    private int groundAt(int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        int bottom = y - 64;
        while (y > bottom) {
            if (natural(world.getBlockAt(x, y, z).getType())) {
                return y;
            }
            y--;
        }
        return y;
    }

    private static boolean natural(Material type) {
        if (!type.isSolid()) {
            return false;
        }
        String name = type.name();
        return !name.endsWith("_LEAVES") && !name.endsWith("_LOG") && !name.endsWith("_WOOD") && !name.endsWith("_STEM")
                && !name.endsWith("MUSHROOM_BLOCK") && !name.equals("SNOW") && !name.equals("ICE") && !name.equals("CACTUS") && !name.equals("BAMBOO");
    }

    private String surfaceOf(int x, int z) {
        String name = world.getBlockAt(x, groundAt(x, z), z).getType().name().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "sand", "red_sand", "podzol", "mycelium", "coarse_dirt", "snow_block", "moss_block", "stone", "terracotta", "grass_block" -> name;
            default -> "grass_block";
        };
    }

    private BlockData blockData(Map<String, BlockData> cache, String data) {
        BlockData result = cache.get(data);
        if (result == null) {
            try {
                result = Bukkit.createBlockData(data.contains(":") ? data : "minecraft:" + data);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Hibás blokk az építésben: " + data);
                result = Bukkit.createBlockData("minecraft:stone");
            }
            cache.put(data, result);
        }
        return result;
    }

    boolean insideBorder(int cx, int cz) {
        double half = getConfig().getInt("border-size", 6000) / 2.0 - 16;
        return Math.abs(cx * 16 + 8 - (townX + 0.5)) < half && Math.abs(cz * 16 + 8 - (townZ + 0.5)) < half;
    }

    boolean inSpawn(Location location) {
        if (location.getWorld() != world) {
            return false;
        }
        double dx = location.getX() - (townX + 0.5);
        double dz = location.getZ() - (townZ + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= getConfig().getInt("spawn-protection", 48);
    }

    boolean chunkNearSpawn(int cx, int cz) {
        double dx = cx * 16 + 8 - (townX + 0.5);
        double dz = cz * 16 + 8 - (townZ + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= getConfig().getInt("spawn-protection", 48) + 16;
    }

    void teleport(Player player, Location target) {
        int delay = getConfig().getInt("teleport-delay", 3);
        if (delay <= 0 || admin(player)) {
            player.teleport(target);
            return;
        }
        send(player, "teleporting", "%seconds%", String.valueOf(delay));
        Location start = player.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!player.isOnline() || player.getWorld() != world) {
                return;
            }
            if (player.getLocation().distanceSquared(start) > 1.0) {
                send(player, "teleport-cancelled");
                return;
            }
            if (player.teleport(target)) {
                send(player, "teleported");
            }
        }, delay * 20L);
    }

    void reload() {
        reloadConfig();
        List<String> problems = new ArrayList<>();
        settings.load(getConfig(), problems);
        for (String problem : problems) {
            getLogger().warning(problem);
        }
        prepareWorld();
        startTasks();
    }

    private void startTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            for (Player player : world.getPlayers()) {
                updateSidebar(player);
                updateTab(player);
            }
            invasions.tick();
        }, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            structures.tick();
            borders.tick();
        }, 10L, 10L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            manager.tickPower();
            manager.saveDirty();
        }, 1200L, 1200L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            for (Player player : world.getPlayers()) {
                inventories.save(player);
            }
        }, 6000L, 6000L));
    }

    // ---- belépés, kilépés ----

    void enter(Player player) {
        KPlayer data = manager.player(player);
        data.lastSeen = System.currentTimeMillis();
        if (!player.hasPermission("icemine.kingdoms.bypass")) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFlying(false);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE);
        Location saved = inventories.location(player.getUniqueId(), world);
        boolean known = inventories.restore(player);
        if (!known) {
            player.getInventory().clear();
            try {
                player.setHealth(20.0);
            } catch (IllegalArgumentException ignored) {
                // a max életerő más lehet, nem gond
            }
            player.setFoodLevel(20);
            for (String spec : getConfig().getStringList("starter-kit")) {
                ItemStack item = parse(spec);
                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
            for (String line : getConfig().getStringList("messages.first-join")) {
                player.sendMessage(Text.color(line.replace("%player%", player.getName())));
            }
        }
        player.teleport(saved != null ? saved : spawn);
        lastArea.remove(player.getUniqueId());
        lastChunk.remove(player.getUniqueId());
        createSidebar(player);
        updateTab(player);
        player.sendTitle(Text.color(getConfig().getString("welcome.title", "&6&lKingdoms")), Text.color(getConfig().getString("welcome.subtitle", "")), 10, 40, 15);
    }

    void leave(Player player) {
        inventories.save(player);
        KPlayer data = manager.player(player);
        data.lastSeen = System.currentTimeMillis();
        data.autoClaim = false;
        data.dirty = true;
        boards.remove(player.getUniqueId());
        shown.remove(player.getUniqueId());
        lastArea.remove(player.getUniqueId());
        lastChunk.remove(player.getUniqueId());
        manager.forget(player.getUniqueId());
    }

    private ItemStack parse(String spec) {
        String[] parts = spec.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = parts.length > 1 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 1;
        return new ItemStack(material, Math.max(1, Math.min(64, amount)));
    }

    /** A hely neve (action bar és scoreboard): királyi város, vadon, vagy a királyság neve a kapcsolat színével. */
    String area(Player player, Location location) {
        if (inSpawn(location)) {
            return Text.color(getConfig().getString("areas.spawn", "&bSpawn"));
        }
        Kingdom owner = manager.owner(location);
        if (owner == null) {
            return Text.color(getConfig().getString("areas.wild", "&2Vadon"));
        }
        Relation relation = manager.relation(player.getUniqueId(), owner);
        return Text.color(getConfig().getString("areas.kingdom", "%color%%kingdom% &7földje").replace("%color%", relation.color).replace("%kingdom%", owner.name));
    }

    /** Mozgáskor: új terület kiírása, automata foglalás és térkép chunk váltáskor. */
    void moved(Player player, Location to) {
        String now = area(player, to);
        String before = lastArea.put(player.getUniqueId(), now);
        if (before != null && !before.equals(now)) {
            Kingdom owner = manager.owner(to);
            if (settings.landTitles && owner != null && !inSpawn(to)) {
                Relation relation = manager.relation(player.getUniqueId(), owner);
                player.sendTitle("", Text.color(relation.color + owner.name + (owner.description.isEmpty() ? "" : " &8- &7" + owner.description)), 5, 30, 10);
            } else {
                player.sendActionBar(now);
            }
        }
        long chunk = Lands.key(to.getBlockX() >> 4, to.getBlockZ() >> 4);
        Long previous = lastChunk.put(player.getUniqueId(), chunk);
        if (previous != null && previous != chunk) {
            KPlayer data = manager.player(player);
            if (data.autoClaim && !manager.claim(player, true) && manager.owner(chunk) == null) {
                data.autoClaim = false;
                send(player, "autoclaim-off");
            }
            if (data.autoMap) {
                showMap(player);
            }
        }
    }

    /** A környék térképe a chatben, színekkel (saját, szövetséges, ellenség, szabad, spawn). */
    void showMap(Player player) {
        if (player.getWorld() != world) {
            send(player, "only-in-kingdoms");
            return;
        }
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        player.sendMessage(Text.color(getConfig().getString("map.header", "&6&lTérkép")));
        for (String line : Lands.render(cx, cz, 15, 4, key -> {
            if (chunkNearSpawn(Lands.x(key), Lands.z(key))) {
                return "&b■";
            }
            Kingdom owner = manager.owner(key);
            if (owner == null) {
                return "&8■";
            }
            return manager.relation(player.getUniqueId(), owner).color + "■";
        }, "&e◆")) {
            player.sendMessage(Text.color(" " + line));
        }
        player.sendMessage(Text.color(getConfig().getString("map.legend", " &a■ &7tiétek  &d■ &7szövetséges  &c■ &7ellenség  &f■ &7más  &8■ &7szabad  &b■ &7spawn")));
        borders.show(player, cx, cz);
    }

    // ---- üzenetek ----

    String message(String key) {
        return Text.color(prefix() + raw(key));
    }

    private String raw(String key) {
        String text = getConfig().getString("messages." + key);
        return text == null ? key : text;
    }

    /** Üzenet kulcs alapján (kingdoms.yml: messages), az IceMine prefixszel. */
    public void send(CommandSender sender, String key, String... pairs) {
        if (sender != null) {
            sender.sendMessage(Text.color(prefix() + replace(raw(key), pairs)));
        }
    }

    /** Szöveg kulcs alapján, prefix és színezés nélkül (menükhöz, címekhez). */
    public String text(String key, String... pairs) {
        return replace(raw(key), pairs);
    }

    /** Kattintható üzenet: a [[szöveg|run:/parancs]] részekre kattintva lefut a parancs. */
    public void click(Player player, String key, String... pairs) {
        player.sendMessage(ClickText.line(prefix() + replace(raw(key), pairs), "&7Kattints: &f%value%", "&7Kattints"));
    }

    public void broadcast(String key, String... pairs) {
        String line = Text.color(prefix() + replace(raw(key), pairs));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (world == null || player.getWorld() == world) {
                player.sendMessage(line);
            }
        }
    }

    static String replace(String text, String... pairs) {
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return result;
    }

    public String label(String path, String fallback) {
        return getConfig().getString(path, fallback);
    }

    public String color(String text) {
        return Text.color(text);
    }

    public String strip(String text) {
        return org.bukkit.ChatColor.stripColor(Text.color(text));
    }

    /** A közös IceMine prefix (messages_hu.yml), hogy a Kingdoms üzenetei is ugyanúgy nézzenek ki, mint a többi. */
    String prefix() {
        if (core().messages() != null) {
            return core().messages().prefix();
        }
        return hu.icemine.core.config.Messages.DEFAULT_PREFIX;
    }

    public String currency() {
        return settings.currency == null || settings.currency.isBlank() ? core().economy().defaultCurrency() : settings.currency;
    }

    public String money(long amount) {
        return core().economy().format(currency(), amount);
    }

    public String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        if (seconds < 60) {
            return seconds + " mp";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " perc" + (seconds % 60 > 0 && minutes < 10 ? " " + seconds % 60 + " mp" : "");
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " óra" + (minutes % 60 > 0 ? " " + minutes % 60 + " perc" : "");
        }
        return hours / 24 + " nap " + hours % 24 + " óra";
    }

    /** Összeg a chatből (pl. "1500", "1.5k", "2m"); 0 és üzenet, ha nem értelmes. */
    long amount(Player player, String text) {
        String clean = text.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace(",", ".");
        double multiplier = 1;
        if (clean.endsWith("k")) {
            multiplier = 1_000;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000;
            clean = clean.substring(0, clean.length() - 1);
        }
        try {
            long value = Math.round(Double.parseDouble(clean) * multiplier);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // lent szólunk
        }
        send(player, "bad-amount");
        return 0;
    }

    // ---- egyebek ----

    boolean admin(Player player) {
        return player.hasPermission("icemine.kingdoms.admin") && player.getGameMode() == GameMode.CREATIVE;
    }

    boolean combatTagged(Player player) {
        try {
            return core().combat() != null && core().combat().tagged(player);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    boolean vanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    String rankPrefix(Player player) {
        for (MetadataValue value : player.getMetadata("icemine_prefix")) {
            if (value.getOwningPlugin() != null && value.getOwningPlugin() == plugin()) {
                return value.asString();
            }
        }
        return "";
    }

    /** Ha változik a királyság vagy a rang, a scoreboard és a tab azonnal frissüljön. */
    void refreshTags(Player player) {
        if (player.getWorld() == world) {
            updateSidebar(player);
            updateTab(player);
        }
    }

    private String apply(String text, Player player) {
        Kingdom kingdom = manager.kingdomOf(player.getUniqueId());
        Rank rank = kingdom == null ? null : kingdom.rankOf(player.getUniqueId());
        KPlayer data = manager.player(player);
        return Text.color(text
                .replace("%player%", player.getName())
                .replace("%kingdom%", kingdom == null ? getConfig().getString("no-kingdom", "&8nincs") : kingdom.display())
                .replace("%role%", rank == null ? "-" : rank.display())
                .replace("%rank%", rank == null ? "-" : rank.display())
                .replace("%members%", kingdom == null ? "0" : String.valueOf(kingdom.members.size()))
                .replace("%kmonline%", kingdom == null ? "0" : String.valueOf(manager.online(kingdom).size()))
                .replace("%claims%", kingdom == null ? "0" : String.valueOf(kingdom.claims.size()))
                .replace("%maxclaims%", kingdom == null ? "0" : String.valueOf(manager.maxClaims(kingdom)))
                .replace("%level%", kingdom == null ? "-" : String.valueOf(manager.level(kingdom)))
                .replace("%rp%", kingdom == null ? "0" : hu.icemine.core.util.Text.number(kingdom.resourcePoints))
                .replace("%bank%", kingdom == null ? "-" : money(kingdom.bank))
                .replace("%power%", String.valueOf(Math.round(data.power)))
                .replace("%kpower%", kingdom == null ? "0" : String.valueOf(Math.round(manager.power(kingdom))))
                .replace("%here%", area(player, player.getLocation()))
                .replace("%masswar%", invasions.masswar() ? text("masswar-running") : "")
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())));
    }

    private void createSidebar(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE, "dummy", Text.color(getConfig().getString("sidebar.title", "&6&lKingdoms")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            Class<?> format = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = format.getMethod("blank").invoke(null);
            Method method = Objective.class.getMethod("numberFormat", format);
            method.invoke(objective, blank);
        } catch (Throwable ignored) {
            // régebbi szerveren látszanak a számok, nem gond
        }
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        shown.put(player.getUniqueId(), new ArrayList<>());
        updateSidebar(player);
    }

    private void updateSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        Objective objective = board == null ? null : board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        List<String> raw = getConfig().getStringList("sidebar.lines");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < raw.size() && i < SUFFIX.length(); i++) {
            String line = apply(raw.get(i), player);
            if (raw.get(i).contains("%masswar%") && !invasions.masswar()) {
                continue;
            }
            lines.add(line + "§" + SUFFIX.charAt(i));
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
            objective.getScore(line).setScore(score--);
        }
        shown.put(player.getUniqueId(), lines);
    }

    private void updateTab(Player player) {
        List<String> header = new ArrayList<>();
        for (String line : getConfig().getStringList("tablist.header")) {
            header.add(apply(line, player));
        }
        List<String> footer = new ArrayList<>();
        for (String line : getConfig().getStringList("tablist.footer")) {
            footer.add(apply(line, player));
        }
        player.setPlayerListHeaderFooter(String.join("\n", header), String.join("\n", footer));
    }
}
