package hu.icemine.core.fyland;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.ClickText;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Fyland: OneBlock jellegű mód. Mindenki kap egy saját szigetet a semmi közepén, ami egyetlen blokkból áll. Ha
 * kibányászod, a helyén azonnal új blokk jelenik meg; a blokkok fázisokban jönnek (Síkság, Föld alatt, Tél, Óceán...
 * egészen az Endig), néha láda vagy mob is. A szigetet a csapattal együtt bővíted, a piacon eladod, amit bányásztál,
 * és a pénzből fejlesztesz (nagyobb sziget, több tag, több láda).
 */
public final class FylandMode implements ModeHandler, Listener {

    public static final String ID = "fyland";
    public static final String FILE = "fyland";

    /** Egy fejlesztés: értékek szintenként (0. = alap), és az árak (az i. ár az i+1. szintre visz). */
    record Upgrade(String id, String name, Material icon, List<String> description, List<Double> values, List<Long> prices, String format) {

        int max() {
            return prices.size();
        }

        double value(int level) {
            if (values.isEmpty()) {
                return 0;
            }
            return values.get(Math.max(0, Math.min(values.size() - 1, level)));
        }
    }

    record Offer(ItemStack item, long price) {
    }

    private record Invite(UUID island, UUID from, long until) {
    }

    private final IceMineCore core;
    private final Random random = new Random();
    private final Map<UUID, FyIsland> islands = new HashMap<>();
    private final Map<UUID, FyIsland> byPlayer = new HashMap<>();
    private final Map<Long, FyIsland> byCell = new HashMap<>();
    private final List<FyPhase> phases = new ArrayList<>();
    private final Map<String, Upgrade> upgrades = new LinkedHashMap<>();
    private final Map<Material, Long> sellPrices = new LinkedHashMap<>();
    private final List<Offer> buyOffers = new ArrayList<>();
    private final List<ItemStack> starterItems = new ArrayList<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, String> confirmKey = new HashMap<>();
    private final Map<UUID, Long> confirmUntil = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, UUID> borders = new HashMap<>();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Set<UUID> died = new HashSet<>();
    private final FyMenus menus;
    private YamlConfiguration config;
    private World world;
    private FyStore store;
    private boolean loaded;
    private int nextIndex = 1;
    private int spacing = 400;
    private int islandY = 100;
    private int levelBlocks = 200;
    private long resetCooldown = 3_600_000L;
    private double voidY = 40;
    private boolean keepInventory = true;
    private String currency = "fyland";

    public FylandMode(IceMineCore core) {
        this.core = core;
        this.menus = new FyMenus(this);
    }

    IceMineCore core() {
        return core;
    }

    FyMenus menus() {
        return menus;
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

    // ---- betöltés ----

    public void load(YamlConfiguration config, List<String> problems) {
        this.config = config;
        String name = config.getString("world", "fyland");
        if (world == null) {
            world = createWorld(name);
            if (world == null) {
                problems.add("fyland.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        spacing = Math.max(150, config.getInt("island.spacing", 400));
        islandY = Math.max(world.getMinHeight() + 10, Math.min(world.getMaxHeight() - 40, config.getInt("island.y", 100)));
        levelBlocks = Math.max(10, config.getInt("island.level-blocks", 200));
        resetCooldown = Math.max(0, config.getLong("island.reset-cooldown-minutes", 60)) * 60_000L;
        voidY = config.getDouble("island.void-y", islandY - 60);
        keepInventory = config.getBoolean("island.keep-inventory", true);
        currency = config.getString("currency", "fyland");
        if (core.economy().currency(currency) == null) {
            problems.add("fyland.yml -> currency: ismeretlen pénznem (" + currency + "), a(z) " + core.economy().defaultCurrency() + " lesz helyette");
            currency = core.economy().defaultCurrency();
        }
        prepareWorld();
        loadPhases(config, problems);
        loadUpgrades(config, problems);
        loadMarket(config, problems);
        starterItems.clear();
        for (String spec : config.getStringList("starter-items")) {
            ItemStack item = Kit.parse(null, spec, "fyland.yml -> starter-items", problems);
            if (item != null) {
                starterItems.add(item);
            }
        }
        if (!loaded) {
            store = new FyStore(core.database(), core.getLogger());
            try {
                store.init();
                for (FyIsland island : store.loadAll()) {
                    register(island);
                }
                nextIndex = store.nextIndex();
                for (FyIsland island : islands.values()) {
                    nextIndex = Math.max(nextIndex, island.index + 1);
                }
                loaded = true;
            } catch (Exception exception) {
                problems.add("Fyland: nem sikerült betölteni a szigeteket (" + exception.getMessage() + "), a mód most nem indul el");
                world = null;
                return;
            }
        }
        buildHub();
    }

    private World createWorld(String name) {
        World loadedWorld = Bukkit.getWorld(name);
        if (loadedWorld != null) {
            return loadedWorld;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");
        creator.generateStructures(false);
        return creator.createWorld();
    }

    private void prepareWorld() {
        world.setDifficulty(Difficulty.NORMAL);
        world.setPVP(false);
        world.setSpawnFlags(false, true);
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("keepInventory", String.valueOf(keepInventory));
        world.setGameRuleValue("doInsomnia", "false");
        world.setGameRuleValue("doPatrolSpawning", "false");
        world.setGameRuleValue("doTraderSpawning", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("announceAdvancements", "false");
        world.setGameRuleValue("spawnRadius", "0");
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(0, islandY + 1, 0);
    }

    private void buildHub() {
        if (config.getBoolean("hub-built", false)) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 4.4) {
                    continue;
                }
                world.getBlockAt(dx, islandY, dz).setType(d > 3.4 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK, false);
                world.getBlockAt(dx, islandY - 1, dz).setType(Material.DIRT, false);
                if (d < 2.5) {
                    world.getBlockAt(dx, islandY - 2, dz).setType(Material.DIRT, false);
                }
            }
        }
        world.getBlockAt(0, islandY - 3, 0).setType(Material.DIRT, false);
        world.getBlockAt(3, islandY + 1, 3).setType(Material.LANTERN, false);
        world.getBlockAt(-3, islandY + 1, -3).setType(Material.LANTERN, false);
        config.set("hub-built", true);
        core.configs().save(FILE);
    }

    private void loadPhases(YamlConfiguration config, List<String> problems) {
        phases.clear();
        ConfigurationSection section = config.getConfigurationSection("phases");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry != null) {
                    phases.add(FyPhase.read(id, entry, problems));
                }
            }
        }
        if (phases.isEmpty()) {
            problems.add("fyland.yml -> phases: nincs egy fázis sem, alap síkságot használok");
            FyPhase fallback = new FyPhase("siksag", "&a&lSíkság", Material.GRASS_BLOCK, 300);
            phases.add(fallback);
        }
    }

    private void loadUpgrades(YamlConfiguration config, List<String> problems) {
        upgrades.clear();
        ConfigurationSection section = config.getConfigurationSection("upgrades");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            List<Double> values = entry.getDoubleList("values");
            List<Long> prices = new ArrayList<>();
            for (Object price : entry.getList("prices", List.of())) {
                if (price instanceof Number number) {
                    prices.add(Math.max(0, number.longValue()));
                }
            }
            if (values.size() != prices.size() + 1) {
                problems.add("fyland.yml -> upgrades." + id + ": a values listában eggyel több elem kell, mint a prices listában");
            }
            upgrades.put(id, new Upgrade(id, entry.getString("name", id), Items.material(entry.getString("icon", "ANVIL"), Material.ANVIL),
                    entry.getStringList("description"), values, prices, entry.getString("format", "%value%")));
        }
    }

    private void loadMarket(YamlConfiguration config, List<String> problems) {
        sellPrices.clear();
        ConfigurationSection sell = config.getConfigurationSection("market.sell");
        if (sell != null) {
            for (String key : sell.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) {
                    problems.add("fyland.yml -> market.sell: ismeretlen tárgy (" + key + ")");
                    continue;
                }
                long price = sell.getLong(key);
                if (price > 0) {
                    sellPrices.put(material, price);
                }
            }
        }
        buyOffers.clear();
        for (Map<?, ?> raw : config.getMapList("market.buy")) {
            Object spec = raw.get("item");
            Object price = raw.get("price");
            if (spec == null || !(price instanceof Number)) {
                problems.add("fyland.yml -> market.buy: minden tételnek kell item és price");
                continue;
            }
            ItemStack item = Kit.parse(null, String.valueOf(spec), "fyland.yml -> market.buy", problems);
            if (item != null) {
                buyOffers.add(new Offer(item, Math.max(0, ((Number) price).longValue())));
            }
        }
    }

    // ---- üzenetek ----

    String text(String key, String... pairs) {
        String value = config == null ? null : config.getString("messages." + key);
        return Text.replace(value == null ? "&c[fyland: " + key + "]" : value, pairs);
    }

    List<String> texts(String key, String... pairs) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList("messages." + key)) {
            result.add(Text.replace(line, pairs));
        }
        return result;
    }

    void send(Player player, String key, String... pairs) {
        core.messages().sendTagged(player, text(key, pairs));
    }

    void click(Player player, String key, String... pairs) {
        player.sendMessage(ClickText.line(core.messages().prefix() + text(key, pairs), "&7Kattints: &f%value%", "&7Kattints"));
    }

    void tell(FyIsland island, String key, String... pairs) {
        for (UUID member : allMembers(island)) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                send(online, key, pairs);
            }
        }
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 1500) {
            warned.put(player.getUniqueId(), now);
            player.sendActionBar(Text.color(text(key)));
        }
    }

    String money(long amount) {
        return core.economy().format(currency, amount);
    }

    String currency() {
        return currency;
    }

    // ---- szigetek ----

    private static long cell(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xffffffffL);
    }

    private void register(FyIsland island) {
        islands.put(island.id, island);
        int[] grid = FyIsland.grid(island.index);
        byCell.put(cell(grid[0], grid[1]), island);
        byPlayer.put(island.owner, island);
        for (UUID member : island.members.keySet()) {
            byPlayer.put(member, island);
        }
    }

    private void unregister(FyIsland island) {
        islands.remove(island.id);
        int[] grid = FyIsland.grid(island.index);
        byCell.remove(cell(grid[0], grid[1]), island);
        byPlayer.remove(island.owner, island);
        for (UUID member : island.members.keySet()) {
            byPlayer.remove(member, island);
        }
    }

    Collection<FyIsland> islands() {
        return islands.values();
    }

    public FyIsland islandOf(UUID player) {
        return byPlayer.get(player);
    }

    FyIsland islandOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    /** A sziget, amelyiknek a rácsmezőjén a hely van (a mérettől függetlenül), vagy null. */
    FyIsland cellAt(Location location) {
        if (world == null || location.getWorld() != world) {
            return null;
        }
        int gx = (int) Math.round(location.getX() / spacing);
        int gz = (int) Math.round(location.getZ() / spacing);
        return byCell.get(cell(gx, gz));
    }

    int radius(FyIsland island) {
        Upgrade size = upgrades.get("size");
        return size == null ? 25 : (int) Math.round(size.value(island.level("size")));
    }

    int maxMembers(FyIsland island) {
        Upgrade members = upgrades.get("members");
        return members == null ? 4 : (int) Math.round(members.value(island.level("members")));
    }

    double luck(FyIsland island) {
        Upgrade luck = upgrades.get("luck");
        return luck == null ? 0 : luck.value(island.level("luck"));
    }

    boolean inside(FyIsland island, Location location) {
        int r = radius(island);
        return Math.abs(location.getBlockX() - island.x) <= r && Math.abs(location.getBlockZ() - island.z) <= r;
    }

    /** A sziget, amelyiken (a határán belül) a hely van, vagy null. */
    FyIsland islandAt(Location location) {
        FyIsland island = cellAt(location);
        return island != null && inside(island, location) ? island : null;
    }

    FyPhase phase(FyIsland island) {
        return phases.get(Math.floorMod(island.phase, phases.size()));
    }

    List<FyPhase> phases() {
        return phases;
    }

    Map<String, Upgrade> upgrades() {
        return upgrades;
    }

    Map<Material, Long> sellPrices() {
        return sellPrices;
    }

    List<Offer> buyOffers() {
        return buyOffers;
    }

    int level(FyIsland island) {
        return (int) (island.blocks / levelBlocks) + 1;
    }

    List<UUID> allMembers(FyIsland island) {
        List<UUID> all = new ArrayList<>();
        all.add(island.owner);
        all.addAll(island.members.keySet());
        return all;
    }

    private boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    boolean canBuild(Player player, Location location) {
        if (builder(player)) {
            return true;
        }
        FyIsland island = islandAt(location);
        return island != null && island.member(player.getUniqueId());
    }

    Location home(FyIsland island) {
        Location home = new Location(world, island.homeX, island.homeY, island.homeZ, island.homeYaw, 0);
        Block below = home.clone().subtract(0, 0.2, 0).getBlock();
        if (!below.getType().isSolid() || !inside(island, home)) {
            return new Location(world, island.x + 0.5, island.y + 1, island.z + 0.5, island.homeYaw, 0);
        }
        return home;
    }

    private FyIsland create(Player owner) {
        int index = nextIndex++;
        store.saveNextIndex(nextIndex);
        FyIsland island = new FyIsland(UUID.randomUUID(), owner.getUniqueId(), owner.getName());
        place(island, index);
        island.created = System.currentTimeMillis();
        register(island);
        store.save(island);
        if (inWorld(owner)) {
            for (ItemStack item : starterItems) {
                give(owner, item.clone());
            }
        }
        return island;
    }

    /** A sziget a megadott rácshelyre kerül, és lerakjuk a varázsblokkot. */
    private void place(FyIsland island, int index) {
        int[] grid = FyIsland.grid(index);
        island.index = index;
        island.x = grid[0] * spacing;
        island.z = grid[1] * spacing;
        island.y = islandY;
        island.homeX = island.x + 0.5;
        island.homeY = island.y + 1;
        island.homeZ = island.z + 0.5;
        island.homeYaw = 0;
        world.getChunkAt(island.x >> 4, island.z >> 4).load(true);
        Block block = world.getBlockAt(island.x, island.y, island.z);
        if (block.getType().isAir()) {
            block.setType(Material.GRASS_BLOCK, false);
        }
        island.dirty = true;
    }

    void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        for (ItemStack left : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    void teleport(Player player, Location location) {
        core.guard().allowNext(player);
        player.setFallDistance(0f);
        player.teleport(location);
    }

    // ---- ModeHandler ----

    @Override
    public Location spawn(Player player) {
        if (world == null) {
            return null;
        }
        FyIsland island = islandOf(player);
        if (island == null) {
            island = create(player);
            send(player, "island-created");
        }
        return home(island);
    }

    @Override
    public void enter(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        boolean restored = core.inventories().restore(ID, player);
        if (!restored) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.setLevel(0);
            player.setExp(0f);
            player.setFoodLevel(20);
            try {
                player.setHealth(player.getMaxHealth());
            } catch (IllegalArgumentException ignored) {
            }
            FyIsland island = islandOf(player);
            if (island != null && island.owner.equals(player.getUniqueId()) && island.blocks == 0) {
                for (ItemStack item : starterItems) {
                    if (!player.getInventory().containsAtLeast(item, item.getAmount())) {
                        give(player, item.clone());
                    }
                }
            }
            for (String line : texts("first-join", "%player%", player.getName())) {
                player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
            }
        } else {
            send(player, "welcome");
        }
        if (islandOf(player) == null) {
            FyIsland island = create(player);
            send(player, "island-created");
            teleport(player, home(island));
        }
        updatePlayer(player);
    }

    @Override
    public void leave(Player player, boolean quitting) {
        core.inventories().save(ID, player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setLevel(0);
        player.setExp(0f);
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        borders.remove(player.getUniqueId());
        borderKey.remove(player.getUniqueId());
        try {
            player.setWorldBorder(null);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        FyIsland island = islandOf(player);
        values.put("%fy_money%", money(core.economy().balance(player, currency)));
        if (island == null) {
            values.put("%fy_phase%", "-");
            values.put("%fy_progress%", "-");
            values.put("%fy_blocks%", "0");
            values.put("%fy_level%", "1");
            values.put("%fy_members%", "-");
            values.put("%fy_cycle%", "1");
            values.put("%fy_island%", "-");
            return;
        }
        FyPhase phase = phase(island);
        values.put("%fy_phase%", Text.color(phase.name));
        values.put("%fy_progress%", Text.number(island.phaseBlocks) + "/" + Text.number(phase.blocks));
        values.put("%fy_blocks%", Text.number(island.blocks));
        values.put("%fy_level%", String.valueOf(level(island)));
        values.put("%fy_members%", island.size() + "/" + maxMembers(island));
        values.put("%fy_cycle%", String.valueOf(island.cycle));
        values.put("%fy_island%", island.ownerName);
    }

    @Override
    public void shutdown() {
        saveAll();
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    public void saveAll() {
        if (store == null) {
            return;
        }
        for (FyIsland island : islands.values()) {
            if (island.dirty) {
                store.save(island);
            }
        }
    }

    // ---- másodpercenként: bossbar és sziget határ ----

    public void tick() {
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        invites.values().removeIf(invite -> invite.until < now);
        for (Player player : world.getPlayers()) {
            updatePlayer(player);
        }
    }

    private void updatePlayer(Player player) {
        if (world == null || player.getWorld() != world) {
            return;
        }
        FyIsland island = cellAt(player.getLocation());
        BossBar bar = bars.get(player.getUniqueId());
        if (island == null) {
            if (bar != null) {
                bar.setVisible(false);
            }
            if (borders.remove(player.getUniqueId()) != null) {
                try {
                    player.setWorldBorder(null);
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        Block magic = world.isChunkLoaded(island.x >> 4, island.z >> 4) ? world.getBlockAt(island.x, island.y, island.z) : null;
        if (magic != null && magic.getType().isAir()) {
            // ha valahogy eltűnt a varázsblokk (pl. WorldEdit), visszarakjuk, hogy ne ragadjon be senki
            next(island, magic, false);
        }
        FyPhase phase = phase(island);
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }
        bar.setVisible(true);
        bar.setTitle(Text.color(text("bossbar", "%phase%", phase.name, "%done%", Text.number(island.phaseBlocks), "%total%", Text.number(phase.blocks),
                "%level%", String.valueOf(level(island)), "%owner%", island.ownerName)));
        bar.setProgress(Math.max(0.0, Math.min(1.0, island.phaseBlocks / (double) phase.blocks)));
        UUID shown = borders.get(player.getUniqueId());
        int size = radius(island) * 2 + 1;
        String key = island.id + ":" + size;
        if (shown == null || !key.equals(borderKey.get(player.getUniqueId()))) {
            try {
                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(island.x + 0.5, island.z + 0.5);
                border.setSize(size);
                border.setWarningDistance(0);
                border.setWarningTime(0);
                player.setWorldBorder(border);
                borders.put(player.getUniqueId(), island.id);
                borderKey.put(player.getUniqueId(), key);
            } catch (RuntimeException ignored) {
                borders.put(player.getUniqueId(), island.id);
                borderKey.put(player.getUniqueId(), key);
            }
        }
    }

    private final Map<UUID, String> borderKey = new HashMap<>();

    private void refreshBars(FyIsland island) {
        for (Player player : world.getPlayers()) {
            if (cellAt(player.getLocation()) == island) {
                updatePlayer(player);
            }
        }
    }

    // ---- a varázsblokk ----

    private FyIsland magicAt(Block block) {
        FyIsland island = cellAt(block.getLocation());
        if (island != null && block.getX() == island.x && block.getY() == island.y && block.getZ() == island.z) {
            return island;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        FyIsland island = magicAt(block);
        if (island == null) {
            if (!canBuild(player, block.getLocation())) {
                event.setCancelled(true);
                warn(player, "no-build");
            }
            return;
        }
        if (!island.member(player.getUniqueId()) && !builder(player)) {
            event.setCancelled(true);
            warn(player, "no-build");
            return;
        }
        int exp = event.getExpToDrop();
        event.setCancelled(true);
        mine(player, island, block, exp);
    }

    private void mine(Player player, FyIsland island, Block block, int exp) {
        Location top = block.getLocation().add(0.5, 1.05, 0.5);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            for (ItemStack drop : block.getDrops(tool, player)) {
                drop(top, drop);
            }
            BlockState state = block.getState();
            if (state instanceof Container container) {
                Inventory inventory = container.getInventory();
                for (ItemStack item : inventory.getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        drop(top, item);
                    }
                }
                inventory.clear();
            }
            if (exp > 0) {
                world.spawn(top, ExperienceOrb.class, orb -> orb.setExperience(exp));
            }
            if (tool.getType().getMaxDurability() > 0) {
                player.damageItemStack(EquipmentSlot.HAND, 1);
            }
        }
        island.blocks++;
        island.phaseBlocks++;
        island.dirty = true;
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.addStat(ID + ".blocks", 1);
        }
        FyPhase phase = phase(island);
        boolean advanced = false;
        if (island.phaseBlocks >= phase.blocks) {
            advance(island);
            advanced = true;
        }
        next(island, block, !advanced);
        refreshBars(island);
    }

    private void drop(Location at, ItemStack item) {
        Item entity = world.dropItem(at, item);
        entity.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
    }

    private void advance(FyIsland island) {
        FyPhase done = phase(island);
        island.phase++;
        island.phaseBlocks = 0;
        if (island.phase >= phases.size()) {
            island.phase = 0;
            island.cycle++;
        }
        island.rewardPending = !done.reward.isEmpty();
        FyPhase now = phase(island);
        for (UUID member : allMembers(island)) {
            Player online = Bukkit.getPlayer(member);
            if (online == null) {
                continue;
            }
            send(online, island.phase == 0 ? "phase-cycle" : "phase-next", "%phase%", now.name, "%done%", done.name, "%cycle%", String.valueOf(island.cycle));
            online.sendTitle(Text.color(now.name), Text.color(text("phase-title")), 10, 50, 15);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        }
    }

    /** A következő blokk a varázsblokk helyére (jutalom láda, sima láda vagy a fázis egy blokkja), és néha egy mob. */
    private void next(FyIsland island, Block block, boolean allowMob) {
        FyPhase phase = phase(island);
        if (island.rewardPending) {
            island.rewardPending = false;
            FyPhase previous = phases.get(Math.floorMod(island.phase - 1, phases.size()));
            chest(block, previous.reward);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(0.5, 1.2, 0.5), 40, 0.4, 0.4, 0.4, 0.2);
            return;
        }
        if (!phase.chest.isEmpty() && random.nextDouble() < phase.chestChance + luck(island)) {
            chest(block, phase.chest);
            world.playSound(block.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.4f);
        } else {
            Material material = phase.pick(random);
            block.setType(material, false);
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                leaves.setPersistent(true);
                block.setBlockData(leaves, false);
            }
        }
        if (allowMob && random.nextDouble() < phase.mobChance) {
            EntityType type = phase.mob(random);
            if (type != null) {
                try {
                    Entity entity = world.spawnEntity(block.getLocation().add(0.5, 1, 0.5), type);
                    if (entity instanceof LivingEntity living) {
                        living.setRemoveWhenFarAway(false);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void chest(Block block, List<FyPhase.Loot> loot) {
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) {
            return;
        }
        Inventory inventory = chest.getBlockInventory();
        List<ItemStack> items = new ArrayList<>();
        for (FyPhase.Loot entry : loot) {
            ItemStack item = entry.roll(random);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty() && !loot.isEmpty()) {
            FyPhase.Loot first = loot.get(0);
            items.add(new ItemStack(first.material(), first.min()));
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots, random);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inventory.setItem(slots.get(i), items.get(i));
        }
    }

    // ---- védelem ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() != world) {
            return;
        }
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBlock().getWorld() == world && !canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.getBlock().getWorld() == world && !canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (block.getType() == Material.ENDER_CHEST && event.getAction() == Action.RIGHT_CLICK_BLOCK && !builder(player)) {
            event.setCancelled(true);
            warn(player, "no-enderchest");
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) {
            return;
        }
        if (!canBuild(player, block.getLocation())) {
            ItemStack hand = event.getItem();
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && hand != null && hand.getType().isEdible()) {
                // enni más szigetén is lehet, csak a blokkhoz nem nyúlhat
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                return;
            }
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() == EquipmentSlot.HAND) {
                warn(player, "no-build");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getWorld() != world || entity instanceof Player) {
            return;
        }
        if (!canBuild(event.getPlayer(), entity.getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().getWorld() != world) {
            return;
        }
        Player attacker = hu.icemine.core.combat.CombatTag.attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (!canBuild(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(attacker, "no-build");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getEntity().getWorld() != world) {
            return;
        }
        Player remover = event.getRemover() == null ? null : hu.icemine.core.combat.CombatTag.attacker(event.getRemover());
        if (remover != null && !canBuild(remover, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getWorld() != world) {
            return;
        }
        FyIsland from = islandAt(event.getBlock().getLocation());
        FyIsland to = islandAt(event.getToBlock().getLocation());
        if (from != to || magicAt(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlock().getWorld() == world && pistonBlocked(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlock().getWorld() == world && pistonBlocked(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonBlocked(Block piston, List<Block> moved, org.bukkit.block.BlockFace direction) {
        FyIsland island = islandAt(piston.getLocation());
        for (Block block : moved) {
            if (magicAt(block) != null) {
                return true;
            }
            Block target = block.getRelative(direction);
            if (island == null || islandAt(target.getLocation()) != island || magicAt(target) != null) {
                return true;
            }
        }
        return false;
    }

    private void filterExplosion(Location origin, List<Block> blocks) {
        FyIsland island = islandAt(origin);
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (magicAt(block) != null || island == null || islandAt(block.getLocation()) != island) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == world) {
            filterExplosion(event.getLocation(), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld() == world) {
            filterExplosion(event.getBlock().getLocation(), event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getWorld() == world && magicAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (event.getBlock().getWorld() == world && magicAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getWorld() == world && magicAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (event.getBlock().getWorld() == world && magicAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getFrom().getWorld() == world) {
            event.setCancelled(true);
            warn(event.getPlayer(), "no-portal");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getFrom().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getWorld() == world) {
            event.setCancelled(true);
        }
    }

    // ---- void, halál ----

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() != world || to.getY() >= voidY) {
            return;
        }
        rescue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.getWorld() == world && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            rescue(player);
        }
    }

    private void rescue(Player player) {
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }
        FyIsland island = cellAt(player.getLocation());
        if (island == null || (!island.member(player.getUniqueId()) && !island.visitors)) {
            island = islandOf(player);
        }
        Location target = island == null ? world.getSpawnLocation().add(0.5, 0, 0.5) : home(island);
        Bukkit.getScheduler().runTask(core, () -> {
            rescuing.remove(player.getUniqueId());
            if (player.isOnline() && player.getWorld() == world) {
                teleport(player, target);
                warn(player, "void");
            }
        });
    }

    private final Set<UUID> rescuing = new HashSet<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getWorld() != world) {
            return;
        }
        died.add(player.getUniqueId());
        if (keepInventory) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!died.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        FyIsland island = islandOf(event.getPlayer());
        event.setRespawnLocation(island == null ? world.getSpawnLocation().add(0.5, 0, 0.5) : home(island));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        died.remove(uuid);
        warned.remove(uuid);
        confirmKey.remove(uuid);
        confirmUntil.remove(uuid);
        borderKey.remove(uuid);
    }

    // ---- csapat ----

    /** Kétlépcsős megerősítés: igazat ad, ha 15 mp-en belül másodszor kéri ugyanazt. */
    boolean confirmed(Player player, String key) {
        long now = System.currentTimeMillis();
        String previous = confirmKey.get(player.getUniqueId());
        Long until = confirmUntil.get(player.getUniqueId());
        if (key.equals(previous) && until != null && until > now) {
            confirmKey.remove(player.getUniqueId());
            confirmUntil.remove(player.getUniqueId());
            return true;
        }
        confirmKey.put(player.getUniqueId(), key);
        confirmUntil.put(player.getUniqueId(), now + 15_000L);
        return false;
    }

    public void goHome(Player player) {
        if (!inWorld(player)) {
            core.modes().sendAndReport(player, ID);
            return;
        }
        FyIsland island = islandOf(player);
        if (island == null) {
            island = create(player);
            send(player, "island-created");
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        teleport(player, home(island));
        send(player, "home");
    }

    public void setHome(Player player) {
        FyIsland island = islandOf(player);
        if (island == null || !inWorld(player)) {
            send(player, "no-island");
            return;
        }
        Location here = player.getLocation();
        if (islandAt(here) != island || !here.clone().subtract(0, 0.2, 0).getBlock().getType().isSolid()) {
            send(player, "sethome-bad");
            return;
        }
        island.homeX = here.getX();
        island.homeY = here.getY();
        island.homeZ = here.getZ();
        island.homeYaw = here.getYaw();
        island.dirty = true;
        send(player, "sethome");
    }

    public void invite(Player player, String targetName) {
        FyIsland island = islandOf(player);
        if (island == null) {
            send(player, "no-island");
            return;
        }
        if (!island.owner.equals(player.getUniqueId())) {
            send(player, "owner-only");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            send(player, "player-offline", "%player%", targetName);
            return;
        }
        if (island.member(target.getUniqueId())) {
            send(player, "already-member", "%player%", target.getName());
            return;
        }
        if (island.size() >= maxMembers(island)) {
            send(player, "team-full", "%max%", String.valueOf(maxMembers(island)));
            return;
        }
        invites.put(target.getUniqueId(), new Invite(island.id, player.getUniqueId(), System.currentTimeMillis() + 300_000L));
        send(player, "invite-sent", "%player%", target.getName());
        click(target, "invite-received", "%player%", player.getName());
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    public void accept(Player player) {
        Invite invite = invites.get(player.getUniqueId());
        FyIsland target = invite == null ? null : islands.get(invite.island);
        if (target == null || invite.until < System.currentTimeMillis()) {
            invites.remove(player.getUniqueId());
            send(player, "no-invite");
            return;
        }
        if (target.size() >= maxMembers(target)) {
            send(player, "team-full", "%max%", String.valueOf(maxMembers(target)));
            return;
        }
        FyIsland current = islandOf(player);
        if (current != null) {
            if (current.owner.equals(player.getUniqueId()) && !current.members.isEmpty()) {
                send(player, "accept-owner-team");
                return;
            }
            if (!confirmed(player, "accept")) {
                send(player, "accept-confirm");
                return;
            }
            if (current.owner.equals(player.getUniqueId())) {
                unregister(current);
                store.delete(current.id);
            } else {
                current.members.remove(player.getUniqueId());
                byPlayer.remove(player.getUniqueId(), current);
                current.dirty = true;
            }
        }
        invites.remove(player.getUniqueId());
        target.members.put(player.getUniqueId(), player.getName());
        byPlayer.put(player.getUniqueId(), target);
        target.dirty = true;
        store.save(target);
        tell(target, "joined", "%player%", player.getName());
        if (inWorld(player)) {
            teleport(player, home(target));
        }
    }

    public void leaveIsland(Player player) {
        FyIsland island = islandOf(player);
        if (island == null) {
            send(player, "no-island");
            return;
        }
        if (island.owner.equals(player.getUniqueId())) {
            send(player, "owner-cant-leave");
            return;
        }
        if (!confirmed(player, "leave")) {
            send(player, "leave-confirm");
            return;
        }
        island.members.remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId(), island);
        island.dirty = true;
        store.save(island);
        tell(island, "left", "%player%", player.getName());
        send(player, "you-left");
        if (inWorld(player)) {
            FyIsland fresh = create(player);
            send(player, "island-created");
            teleport(player, home(fresh));
        }
    }

    public void kick(Player player, String targetName) {
        FyIsland island = islandOf(player);
        if (island == null || !island.owner.equals(player.getUniqueId())) {
            send(player, "owner-only");
            return;
        }
        UUID target = null;
        for (Map.Entry<UUID, String> entry : island.members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                target = entry.getKey();
            }
        }
        if (target == null) {
            send(player, "not-member", "%player%", targetName);
            return;
        }
        if (!confirmed(player, "kick:" + target)) {
            send(player, "kick-confirm", "%player%", island.members.get(target));
            return;
        }
        String name = island.members.remove(target);
        byPlayer.remove(target, island);
        island.dirty = true;
        store.save(island);
        tell(island, "kicked", "%player%", name);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            send(online, "you-kicked", "%owner%", player.getName());
            if (inWorld(online)) {
                FyIsland fresh = create(online);
                send(online, "island-created");
                teleport(online, home(fresh));
            }
        }
    }

    public void transfer(Player player, String targetName) {
        FyIsland island = islandOf(player);
        if (island == null || !island.owner.equals(player.getUniqueId())) {
            send(player, "owner-only");
            return;
        }
        UUID target = null;
        for (Map.Entry<UUID, String> entry : island.members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                target = entry.getKey();
            }
        }
        if (target == null) {
            send(player, "not-member", "%player%", targetName);
            return;
        }
        if (!confirmed(player, "transfer:" + target)) {
            send(player, "transfer-confirm", "%player%", island.members.get(target));
            return;
        }
        String newName = island.members.remove(target);
        island.members.put(island.owner, island.ownerName);
        island.owner = target;
        island.ownerName = newName;
        island.dirty = true;
        store.save(island);
        tell(island, "transferred", "%player%", newName);
    }

    public void reset(Player player) {
        FyIsland island = islandOf(player);
        if (island == null || !island.owner.equals(player.getUniqueId())) {
            send(player, "owner-only");
            return;
        }
        long left = island.lastReset + resetCooldown - System.currentTimeMillis();
        if (left > 0) {
            send(player, "reset-cooldown", "%time%", Text.duration(left));
            return;
        }
        if (!confirmed(player, "reset")) {
            send(player, "reset-confirm");
            return;
        }
        int[] oldGrid = FyIsland.grid(island.index);
        byCell.remove(cell(oldGrid[0], oldGrid[1]), island);
        int index = nextIndex++;
        store.saveNextIndex(nextIndex);
        place(island, index);
        island.blocks = 0;
        island.phase = 0;
        island.phaseBlocks = 0;
        island.cycle = 1;
        island.rewardPending = false;
        island.lastReset = System.currentTimeMillis();
        int[] grid = FyIsland.grid(index);
        byCell.put(cell(grid[0], grid[1]), island);
        store.save(island);
        for (ItemStack item : starterItems) {
            give(player, item.clone());
        }
        for (UUID member : allMembers(island)) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                send(online, "reset-done");
                if (inWorld(online)) {
                    teleport(online, home(island));
                }
            }
        }
    }

    public void toggleVisitors(Player player) {
        FyIsland island = islandOf(player);
        if (island == null || !island.owner.equals(player.getUniqueId())) {
            send(player, "owner-only");
            return;
        }
        island.visitors = !island.visitors;
        island.dirty = true;
        send(player, island.visitors ? "visitors-on" : "visitors-off");
        if (!island.visitors) {
            for (Player visitor : world.getPlayers()) {
                if (cellAt(visitor.getLocation()) == island && !island.member(visitor.getUniqueId()) && !builder(visitor)) {
                    FyIsland own = islandOf(visitor);
                    if (own != null) {
                        teleport(visitor, home(own));
                        send(visitor, "visit-closed-kicked");
                    }
                }
            }
        }
    }

    /** Sziget keresése egy tag nevéből (az offline tagokat is megtalálja). */
    FyIsland findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return islandOf(online);
        }
        for (FyIsland island : islands.values()) {
            if (island.ownerName.equalsIgnoreCase(name)) {
                return island;
            }
            for (String member : island.members.values()) {
                if (member.equalsIgnoreCase(name)) {
                    return island;
                }
            }
        }
        return null;
    }

    public void visit(Player player, String targetName) {
        FyIsland island = findByName(targetName);
        visit(player, island, targetName);
    }

    void visit(Player player, FyIsland island, String label) {
        if (island == null) {
            send(player, "visit-none", "%player%", label);
            return;
        }
        if (!island.visitors && !island.member(player.getUniqueId()) && !player.hasPermission("icemine.admin.fyland")) {
            send(player, "visit-closed", "%player%", island.ownerName);
            return;
        }
        if (!inWorld(player)) {
            core.modes().sendAndReport(player, ID);
            if (!inWorld(player)) {
                return;
            }
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        teleport(player, home(island));
        send(player, "visiting", "%player%", island.ownerName);
    }

    public void buyUpgrade(Player player, String id) {
        FyIsland island = islandOf(player);
        Upgrade upgrade = upgrades.get(id);
        if (island == null || upgrade == null) {
            return;
        }
        int level = island.level(id);
        if (level >= upgrade.max()) {
            send(player, "upgrade-max");
            return;
        }
        long price = upgrade.prices().get(level);
        if (core.economy().withdraw(player, currency, price, ID + ":upgrade:" + id) != Ledger.Result.OK) {
            send(player, "not-enough-money", "%price%", money(price));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        island.upgrades.put(id, level + 1);
        island.dirty = true;
        store.save(island);
        tell(island, "upgraded", "%player%", player.getName(), "%upgrade%", upgrade.name(), "%level%", String.valueOf(level + 1));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.3f);
        refreshBars(island);
    }

    // ---- piac ----

    private boolean plain(ItemStack item) {
        if (item == null || item.getType().isAir() || !sellPrices.containsKey(item.getType())) {
            return false;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasLore() && !meta.hasEnchants());
    }

    /** Egy anyagból mennyi van a játékosnál (amit el lehet adni). */
    int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (plain(item) && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Eladás: egy anyagból mindent, vagy (null esetén) minden eladhatót. */
    public void sell(Player player, Material only) {
        if (!inWorld(player)) {
            send(player, "only-here");
            return;
        }
        long total = 0;
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!plain(item) || (only != null && item.getType() != only)) {
                continue;
            }
            total += sellPrices.get(item.getType()) * item.getAmount();
            amount += item.getAmount();
            contents[i] = null;
        }
        if (amount == 0) {
            send(player, "sell-nothing");
            return;
        }
        player.getInventory().setStorageContents(contents);
        long earned = core.ranks().boost(player, total);
        core.economy().deposit(player, currency, earned, ID + ":sell");
        send(player, "sold", "%amount%", Text.number(amount), "%money%", money(earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    public void buy(Player player, Offer offer, int times) {
        if (!inWorld(player)) {
            return;
        }
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        int stacks = (int) Math.ceil(offer.item().getAmount() * times / (double) Math.max(1, offer.item().getMaxStackSize()));
        if (free < stacks) {
            send(player, "inventory-full");
            return;
        }
        long price = offer.price() * times;
        if (core.economy().withdraw(player, currency, price, ID + ":buy") != Ledger.Result.OK) {
            send(player, "not-enough-money", "%price%", money(price));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        for (int i = 0; i < times; i++) {
            give(player, offer.item().clone());
        }
        send(player, "bought", "%amount%", String.valueOf(offer.item().getAmount() * times), "%item%", itemName(offer.item().getType()), "%price%", money(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    String itemName(Material material) {
        String custom = config.getString("item-names." + material.name().toLowerCase(Locale.ROOT));
        if (custom != null) {
            return custom;
        }
        StringBuilder name = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    List<FyIsland> top(int limit) {
        List<FyIsland> sorted = new ArrayList<>(islands.values());
        sorted.sort(Comparator.comparingLong((FyIsland island) -> island.blocks).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    // ---- admin ----

    public void adminSetPhase(org.bukkit.command.CommandSender sender, String name, String phaseText) {
        FyIsland island = findByName(name);
        if (island == null) {
            core.messages().sendTagged(sender, text("visit-none", "%player%", name));
            return;
        }
        int index = -1;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).id.equalsIgnoreCase(phaseText) || String.valueOf(i + 1).equals(phaseText)) {
                index = i;
            }
        }
        if (index < 0) {
            List<String> ids = new ArrayList<>();
            for (FyPhase phase : phases) {
                ids.add(phase.id);
            }
            core.messages().sendTagged(sender, text("admin-phases", "%phases%", String.join(", ", ids)));
            return;
        }
        island.phase = index;
        island.phaseBlocks = 0;
        island.dirty = true;
        store.save(island);
        core.messages().sendTagged(sender, text("admin-phase-set", "%player%", island.ownerName, "%phase%", phases.get(index).name));
        core.adminLog(sender, "fyland-phase", island.ownerName, phases.get(index).id);
        refreshBars(island);
    }

    public void adminDelete(org.bukkit.command.CommandSender sender, String name) {
        FyIsland island = findByName(name);
        if (island == null) {
            core.messages().sendTagged(sender, text("visit-none", "%player%", name));
            return;
        }
        unregister(island);
        store.delete(island.id);
        core.messages().sendTagged(sender, text("admin-deleted", "%player%", island.ownerName));
        core.adminLog(sender, "fyland-delete", island.ownerName, null);
        for (UUID member : allMembers(island)) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && inWorld(online)) {
                FyIsland fresh = create(online);
                teleport(online, home(fresh));
                send(online, "island-created");
            }
        }
    }
}
