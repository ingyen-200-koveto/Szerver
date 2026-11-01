package hu.icemine.core.gens;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.Database;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Gens: mindenkinek saját farm szigete van 8 földdel. A növényeket a Gens kapával kell learatni (nincs generátor),
 * a termést eladod, a pénzből új földeket nyitsz és a kapát fejleszted. Learatás után a növény magától visszanő.
 */
public final class GensMode implements ModeHandler, Listener {

    public static final String ID = "gens";
    public static final String HOE_TAG = "gens:hoe";
    /** A profilban ("gens.farm") jegyezzük, hogy a sziget már a földes változat. */
    static final String FARM_VERSION = "1";
    private static final String LABEL_TAG = "icemine_gens_label";

    /** Egy learatott növény, ami épp visszanő. */
    private static final class Regrow {
        final int x;
        final int y;
        final int z;
        final Crop crop;
        final long half;
        final long ready;
        boolean halfDone;

        Regrow(int x, int y, int z, Crop crop, long half, long ready) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.crop = crop;
            this.half = half;
            this.ready = ready;
        }
    }

    /** Egy blokk a sziget egyik földjén. */
    private record Spot(int plot, int field, int dx, int dz, int dy, int cx, int cz) {
    }

    private final IceMineCore core;
    private final Map<UUID, GensData.Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, GensData.Player> pending = new ConcurrentHashMap<>();
    private final List<GensData.Field> fields = new ArrayList<>();
    private final HoeUpgrades hoe = new HoeUpgrades();
    private final Map<Material, Long> prices = new HashMap<>();
    private final QuestBook quests = new QuestBook();
    private final GensProgress progress = new GensProgress();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Map<UUID, ItemStack[]> backpacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> autosold = new HashMap<>();
    private final Map<UUID, Long> confirm = new HashMap<>();
    private final Map<UUID, String> confirmKey = new HashMap<>();
    private final Map<Long, Regrow> regrowing = new HashMap<>();
    private final Map<UUID, List<TextDisplay>> labels = new HashMap<>();
    private final Map<String, BlockData> blocks = new HashMap<>();
    private final Map<Crop, Material> cropBlocks = new EnumMap<>(Crop.class);
    private final Map<Crop, Material> cropDrops = new EnumMap<>(Crop.class);
    private final Map<Crop, Material> cropSoils = new EnumMap<>(Crop.class);
    private long autosellFlush;
    private long labelCheck;
    private World world;
    private Location hub;
    private int spacing = 96;
    private int buildRadius = 40;
    private int height = 100;
    private int nextPlot = 1;
    private String currency = "gens";
    private long dailyAmount = 500;
    private double voidY = 40;

    public GensMode(IceMineCore core) {
        this.core = core;
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

    public QuestBook quests() {
        return quests;
    }

    public GensProgress progress() {
        return progress;
    }

    public HoeUpgrades hoe() {
        return hoe;
    }

    public List<GensData.Field> fields() {
        return Collections.unmodifiableList(fields);
    }

    public String currency() {
        return currency;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Values values = new Values("gens.yml", config::get);
        String name = values.text("world", "ice_gens");
        if (world == null) {
            world = Bukkit.getWorld(name);
            if (world == null) {
                WorldCreator creator = new WorldCreator(name);
                creator.type(WorldType.FLAT);
                creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
                creator.generateStructures(false);
                world = creator.createWorld();
            }
            if (world == null) {
                problems.add("gens.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("keepInventory", "true");
        world.setGameRuleValue("announceAdvancements", "false");
        world.setGameRuleValue("randomTickSpeed", "3");
        world.setStorm(false);
        world.setTime(values.integer("time", 6000, 0, 23999));
        spacing = values.integer("plot.spacing", 96, 48, 512);
        buildRadius = Math.min(spacing / 2 - 4, values.integer("plot.build-radius", 40, GensBuild.PLOT_CLEAR, 250));
        height = values.integer("plot.height", 100, 0, 250);
        voidY = values.decimal("void-y", height - 40, -64, 320);
        currency = values.text("currency", "gens");
        if (core.economy().currency(currency) == null) {
            problems.add("gens.yml -> currency: ismeretlen pénznem (" + currency + "), az economy.yml-ben is létre kell hozni");
            currency = core.economy().defaultCurrency();
        }
        dailyAmount = values.number("daily.amount", 500, 0, Long.MAX_VALUE / 4);
        problems.addAll(values.problems());
        fields.clear();
        fields.addAll(GensData.loadFields(config, problems));
        hoe.load(config, problems);
        progress.load(config, fields.size(), problems);
        loadQuests(config, problems);
        loadMaterials(problems);
        prices.clear();
        ConfigurationSection extra = config.getConfigurationSection("sell-prices");
        if (extra != null) {
            for (String key : extra.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    problems.add("gens.yml -> sell-prices." + key + ": ismeretlen tárgy");
                } else {
                    prices.put(material, Math.max(0, extra.getLong(key)));
                }
            }
        }
        for (GensData.Field field : fields) {
            prices.merge(cropDrops.get(field.crop), field.value, Math::max);
        }
        if (!config.getBoolean("hub-built", false)) {
            buildHub(config);
        }
        hub = Locs.read(config.getConfigurationSection("hub"), world);
        if (hub == null) {
            hub = new Location(world, 0.5, height + 1, 0.5);
        }
        world.setSpawnLocation(hub.getBlockX(), hub.getBlockY(), hub.getBlockZ());
        try {
            nextPlot = core.database().query(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(plot) FROM im_gens_plots");
                     ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) + 1 : 1;
                }
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            problems.add("Gens: nem sikerült kiolvasni a következő telek számát, 1-ről indulok (" + exception.getMessage() + ")");
        }
        nextPlot = Math.max(1, nextPlot);
    }

    private void loadMaterials(List<String> problems) {
        cropBlocks.clear();
        cropDrops.clear();
        cropSoils.clear();
        for (Crop crop : Crop.values()) {
            cropBlocks.put(crop, Items.material(crop.block, Material.WHEAT));
            cropDrops.put(crop, Items.material(crop.drop, Material.WHEAT));
            String soil = crop.soil.contains("[") ? crop.soil.substring(0, crop.soil.indexOf('[')) : crop.soil;
            cropSoils.put(crop, Items.material(soil, Material.FARMLAND));
        }
    }

    private void loadQuests(YamlConfiguration config, List<String> problems) {
        List<QuestBook.Quest> list = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("quests.pool");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String type = QuestBook.type(section.getString(id + ".type", ""));
                if (type == null) {
                    problems.add("gens.yml -> quests.pool." + id + ".type: harvest, sell_items, upgrade, earn vagy unlock lehet");
                    continue;
                }
                list.add(new QuestBook.Quest(id, type, Math.max(1, section.getLong(id + ".amount", 10)), Math.max(0, section.getLong(id + ".reward", 100)),
                        section.getString(id + ".name", id)));
            }
        }
        quests.configure(list, config.getInt("quests.per-day", 3));
    }

    private void buildHub(YamlConfiguration config) {
        core.getLogger().info("Építem a Gens központi szigetét...");
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(-GensBuild.HUB_CLEAR, height - 30, -GensBuild.HUB_CLEAR, GensBuild.HUB_CLEAR, height + 40, GensBuild.HUB_CLEAR);
        GensBuild build = new GensBuild(placer, 0, height, 0, true, 0);
        build.build();
        Layout.Point spawn = build.layout().points.get("spawn");
        if (spawn != null) {
            Locs.write(config, "hub", spawn.x, height + spawn.y, spawn.z, spawn.yaw);
        }
        config.set("hub-built", true);
        core.configs().save("gens");
    }

    public void rebuildHub() {
        YamlConfiguration config = core.configs().get("gens");
        buildHub(config);
        hub = Locs.read(config.getConfigurationSection("hub"), world);
    }

    // ---- betöltés, mentés ----

    public boolean preload(UUID uuid) {
        try {
            GensData.Player data = core.database().query(connection -> {
                GensData.Player result = null;
                try (PreparedStatement statement = connection.prepareStatement("SELECT plot, level, trusted, daily, quests FROM im_gens_plots WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (rows.next()) {
                            result = new GensData.Player(uuid, rows.getInt(1), rows.getInt(2));
                            result.decodeTrusted(rows.getString(3));
                            result.daily = rows.getLong(4);
                            result.decodeQuests(rows.getString(5));
                        }
                    }
                }
                if (result == null) {
                    result = new GensData.Player(uuid, -1, 1);
                    result.fresh = true;
                    return result;
                }
                // a régi generátorok (csak az átállásig vannak meg, a visszatérítéshez)
                try (PreparedStatement statement = connection.prepareStatement("SELECT tier FROM im_gens_generators WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            result.legacyGenerators.add(rows.getInt(1));
                        }
                    }
                }
                return result;
            }).get(8, TimeUnit.SECONDS);
            pending.put(uuid, data);
            return true;
        } catch (Exception exception) {
            core.getLogger().warning("Nem sikerült betölteni a Gens adatokat: " + exception.getMessage());
            return false;
        }
    }

    public void attach(Player player) {
        GensData.Player data = pending.remove(player.getUniqueId());
        if (data == null) {
            if (!preload(player.getUniqueId())) {
                data = new GensData.Player(player.getUniqueId(), -1, 1);
            } else {
                data = pending.remove(player.getUniqueId());
            }
        }
        players.put(player.getUniqueId(), data);
        readProfile(player, data);
        backpacks.put(player.getUniqueId(), core.inventories().loadItems("gens_backpack", player.getUniqueId(), progress.backpackMax()));
    }

    /** A prestige, rebirth, fejlesztések, a kapa és a kapcsolók a játékos profiljában vannak. */
    private void readProfile(Player player, GensData.Player data) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        data.prestige = (int) profile.stat("gens.prestige");
        data.rebirth = (int) profile.stat("gens.rebirth");
        data.upgrades.clear();
        for (String id : progress.upgrades().keySet()) {
            data.upgrades.put(id, number(profile.setting("gens.up." + id, "0")));
        }
        data.hoe.clear();
        for (String id : HoeUpgrades.IDS) {
            data.hoe.put(id, number(profile.setting("gens.hoe." + id, "0")));
        }
        data.autosellOwned = profile.flag("gens.autosell.owned", false);
        data.autosell = profile.flag("gens.autosell", false);
        data.backpackLarge = profile.flag("gens.backpack.large", false);
        data.locked = profile.flag("gens.locked", false);
    }

    private static int number(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void writeProfile(Player player, GensData.Player data) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        profile.setStat("gens.prestige", data.prestige);
        profile.setStat("gens.rebirth", data.rebirth);
        profile.setStat("gens.prestige_score", data.rebirth * 1000L + data.prestige);
        for (Map.Entry<String, Integer> entry : data.upgrades.entrySet()) {
            profile.put("gens.up." + entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : data.hoe.entrySet()) {
            profile.put("gens.hoe." + entry.getKey(), String.valueOf(entry.getValue()));
        }
        profile.setStat("gens.hoe_level", hoe.total(data.hoe));
        profile.putFlag("gens.autosell.owned", data.autosellOwned);
        profile.putFlag("gens.autosell", data.autosell);
        profile.putFlag("gens.backpack.large", data.backpackLarge);
        profile.putFlag("gens.locked", data.locked);
    }

    public void detach(UUID uuid) {
        GensData.Player data = players.remove(uuid);
        warned.remove(uuid);
        confirm.remove(uuid);
        confirmKey.remove(uuid);
        removeLabels(uuid);
        flushAutosell(uuid);
        ItemStack[] pack = backpacks.remove(uuid);
        if (pack != null) {
            core.inventories().saveItems("gens_backpack", uuid, pack);
        }
        if (data != null) {
            save(data, true);
        }
    }

    public GensData.Player data(Player player) {
        return players.get(player.getUniqueId());
    }

    private void save(GensData.Player data, boolean all) {
        if (data.plot < 0 || !(all || data.dirty)) {
            return;
        }
        data.dirty = false;
        String trusted = data.encodeTrusted();
        String questText = data.encodeQuests();
        int plot = data.plot;
        int level = data.level;
        long daily = data.daily;
        Database database = core.database();
        String uuid = data.uuid.toString();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(database.upsertPlot())) {
                statement.setString(1, uuid);
                statement.setInt(2, plot);
                statement.setInt(3, level);
                statement.setString(4, trusted);
                statement.setLong(5, daily);
                statement.setString(6, questText);
                statement.executeUpdate();
            }
        });
    }

    public void saveAll() {
        for (GensData.Player data : players.values()) {
            save(data, false);
        }
    }

    // ---- számok ----

    /** A következő megnyitható föld, vagy null, ha már mind megvan. */
    public GensData.Field nextField(GensData.Player data) {
        return data.level >= fields.size() ? null : fields.get(Math.max(0, data.level));
    }

    public int unlocked(GensData.Player data) {
        return Math.max(1, Math.min(data.level, fields.size()));
    }

    /** Termelési szorzó: prestige × rebirth (ennyiszer több termény jár egy növényért). */
    public double multiplier(GensData.Player data) {
        return progress.productionMultiplier(data.prestige, data.rebirth);
    }

    /** Egy termény ára a kapa "Aranykéz" fejlesztésével (a sziget "Jobb ár" fejlesztése és a rang az eladáskor jön rá). */
    public long unitPrice(GensData.Player data, long value) {
        return Math.round(value * (1.0 + hoe.valuePercent(data == null ? Map.of() : data.hoe) / 100.0));
    }

    /** Eladási szorzó: a sziget "érték" fejlesztése, a rang pénz szorzója erre jön rá. */
    public long sellBoost(Player player, GensData.Player data, long amount) {
        double value = data == null ? 1.0 : progress.valueMultiplier(data.upgrades);
        return core.ranks().boost(player, Math.round(amount * value));
    }

    /** Extra termés esélye százalékban: a kapa "Bőség" és a sziget "Termékeny föld" fejlesztése együtt. */
    public double extraPercent(GensData.Player harvester, GensData.Player owner) {
        return hoe.extraPercent(harvester.hoe) + progress.percent(owner.upgrades, GensProgress.Effect.CAPACITY);
    }

    /**
     * Rang bónusz: icemine.gens.bonus.N = +N×10% esély extra termésre (a régi extraslots jog is ennyit ér).
     * Csak a kifejezetten megadott jog számít, így az OP-ok nem kapják meg automatikusan a legnagyobbat.
     */
    public double rankBonus(Player player) {
        for (int n = 10; n >= 1; n--) {
            if (granted(player, "icemine.gens.bonus." + n) || granted(player, "icemine.gens.extraslots." + n)) {
                return n * 10.0;
            }
        }
        return 0;
    }

    private static boolean granted(Player player, String node) {
        return player.isPermissionSet(node) && player.hasPermission(node);
    }

    /** Ennyi idő alatt nő vissza a növény ezen a földön (a sziget "Gyorsabb növekedés" fejlesztésével). */
    public long regrowMillis(GensData.Player owner, GensData.Field field) {
        return progress.intervalMillis(field.regrow, owner == null ? Map.of() : owner.upgrades);
    }

    /** Becsült bevétel percenként, ha minden megnyitott földet folyamatosan aratsz (a menüben és a scoreboardon). */
    public long productionPerMinute(GensData.Player data) {
        double total = 0;
        double amount = HoeUpgrades.expected(multiplier(data), extraPercent(data, data));
        for (int i = 0; i < unlocked(data); i++) {
            GensData.Field field = fields.get(i);
            total += FarmLayout.SIZE * amount * unitPrice(data, field.value) * progress.valueMultiplier(data.upgrades) * 60000.0 / regrowMillis(data, field);
        }
        return Math.round(total);
    }

    public String hoeName(GensData.Player data) {
        List<String> names = core.messages().list("gens.hoe.tiers");
        int tier = hoe.tier(data.hoe);
        return tier < names.size() ? names.get(tier) : HoeUpgrades.MATERIALS[tier];
    }

    // ---- telkek ----

    public int[] plotCenter(int plot) {
        int[] cell = GenMath.spiral(plot);
        return new int[]{cell[0] * spacing, cell[1] * spacing};
    }

    public int plotAt(Location location) {
        int gx = Math.round((float) location.getBlockX() / spacing);
        int gz = Math.round((float) location.getBlockZ() / spacing);
        if (gx == 0 && gz == 0) {
            return 0;
        }
        int dx = location.getBlockX() - gx * spacing;
        int dz = location.getBlockZ() - gz * spacing;
        if (Math.abs(dx) > buildRadius || Math.abs(dz) > buildRadius) {
            return -1;
        }
        int layer = Math.max(Math.abs(gx), Math.abs(gz));
        int first = (2 * layer - 1) * (2 * layer - 1);
        int last = (2 * layer + 1) * (2 * layer + 1) - 1;
        for (int index = first; index <= last; index++) {
            int[] cell = GenMath.spiral(index);
            if (cell[0] == gx && cell[1] == gz) {
                return index;
            }
        }
        return -1;
    }

    private GensData.Player ownerOf(int plot) {
        if (plot <= 0) {
            return null;
        }
        for (GensData.Player data : players.values()) {
            if (data.plot == plot) {
                return data;
            }
        }
        return null;
    }

    /** Ha ez a blokk egy sziget földjén van (talaj, növény vagy fölötte), megmondja, melyiken. */
    private Spot spot(Block block) {
        if (block.getWorld() != world) {
            return null;
        }
        int dy = block.getY() - height;
        if (dy < -1 || dy > 2) {
            return null;
        }
        int plot = plotAt(block.getLocation());
        if (plot <= 0) {
            return null;
        }
        int[] center = plotCenter(plot);
        int dx = block.getX() - center[0];
        int dz = block.getZ() - center[1];
        int field = FarmLayout.fieldAt(dx, dz);
        return field < 0 ? null : new Spot(plot, field, dx, dz, dy, center[0], center[1]);
    }

    /** A tér egyik különleges blokkja (eladó hordó, kapa üllő)? */
    private boolean plazaBlock(Block block, int[] where, Material type) {
        if (block.getType() != type || block.getY() != height + 1) {
            return false;
        }
        int plot = plotAt(block.getLocation());
        if (plot <= 0) {
            return false;
        }
        int[] center = plotCenter(plot);
        return block.getX() - center[0] == where[0] && block.getZ() - center[1] == where[1];
    }

    public Location home(GensData.Player data) {
        int[] center = plotCenter(data.plot);
        Layout.Point spawn = GensBuild.plotSpawn();
        return new Location(world, center[0] + spawn.x, height + spawn.y, center[1] + spawn.z, (float) spawn.yaw, 0f);
    }

    private void ensurePlot(Player player, GensData.Player data) {
        if (data.plot > 0) {
            return;
        }
        data.plot = nextPlot++;
        data.level = 1;
        data.dirty = true;
        buildFarm(data, GensBuild.PLOT_CLEAR);
        markFarm(player);
        save(data, true);
        core.messages().sendList(player, "gens.first-join", "%player%", player.getName());
    }

    private boolean farmReady(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        return profile == null || FARM_VERSION.equals(profile.setting("gens.farm", "0"));
    }

    private void markFarm(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.put("gens.farm", FARM_VERSION);
        }
    }

    /**
     * A régi, generátoros szigetet átépítjük farmmá. A megnyitott földek száma a régi sziget szint lesz (1-8),
     * a generátorok árát és a megmaradt régi termést visszakapja, amikor legközelebb belép a Gensbe (finishConversion).
     */
    private boolean ensureFarm(Player player, GensData.Player data) {
        if (data.plot <= 0 || farmReady(player)) {
            return false;
        }
        data.level = Math.max(1, Math.min(fields.size(), data.level));
        data.dirty = true;
        data.legacyPending = true;
        purgeRegrow(data.plot);
        buildFarm(data, buildRadius);
        markFarm(player);
        String uuid = data.uuid.toString();
        core.database().execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_gens_generators WHERE uuid = ?")) {
                statement.setString(1, uuid);
                statement.executeUpdate();
            }
        });
        save(data, true);
        core.getLogger().info("Gens: " + player.getName() + " szigete átépítve farmmá (" + data.level + " föld).");
        return true;
    }

    private void buildFarm(GensData.Player data, int clearRadius) {
        int[] center = plotCenter(data.plot);
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(center[0] - clearRadius, height - 25, center[1] - clearRadius, center[0] + clearRadius, height + 40, center[1] + clearRadius);
        new GensBuild(placer, center[0], height, center[1], false, data.plot).build();
        for (int i = 0; i < FarmLayout.FIELDS; i++) {
            buildField(placer, center, i, i < unlocked(data));
        }
    }

    private void buildField(Placer placer, int[] center, int index, boolean open) {
        Crop crop = index < fields.size() ? fields.get(index).crop : Crop.WHEAT;
        new FieldBuild(placer, center[0], height, center[1], index, crop, open && index < fields.size()).build();
    }

    /** Minden megnyitott földön beérett növény legyen (újraindítás vagy egy átírt gens.yml után), kivéve ami épp visszanő. */
    private void refreshFields(GensData.Player data) {
        if (data.plot <= 0 || world == null) {
            return;
        }
        int[] center = plotCenter(data.plot);
        for (int i = 0; i < unlocked(data); i++) {
            Crop crop = fields.get(i).crop;
            BlockData soil = block(crop.soil);
            BlockData ripe = block(crop.mature());
            for (int[] cell : FarmLayout.cells(i)) {
                int x = center[0] + cell[0];
                int z = center[1] + cell[1];
                Block ground = world.getBlockAt(x, height, z);
                if (ground.getType() != cropSoils.get(crop)) {
                    ground.setBlockData(soil, false);
                }
                if (regrowing.containsKey(GensData.key(x, height + 1, z))) {
                    continue;
                }
                Block plant = world.getBlockAt(x, height + 1, z);
                if (!mature(plant, crop)) {
                    plant.setBlockData(ripe, false);
                }
            }
        }
    }

    private void purgeRegrow(int plot) {
        int[] center = plotCenter(plot);
        regrowing.values().removeIf(r -> Math.abs(r.x - center[0]) <= GensBuild.FARM && Math.abs(r.z - center[1]) <= GensBuild.FARM);
    }

    private BlockData block(String data) {
        BlockData result = blocks.get(data);
        if (result == null) {
            try {
                result = Bukkit.createBlockData("minecraft:" + data);
            } catch (IllegalArgumentException exception) {
                result = Bukkit.createBlockData(Material.AIR);
            }
            blocks.put(data, result);
        }
        return result;
    }

    private boolean mature(Block block, Crop crop) {
        Material type = cropBlocks.get(crop);
        if (block.getType() != type) {
            return false;
        }
        if (crop.wholeBlock()) {
            return true;
        }
        return block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    // ---- belépés, kilépés ----

    @Override
    public Location spawn(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null || world == null) {
            return hub;
        }
        ensurePlot(player, data);
        ensureFarm(player, data);
        refreshFields(data);
        return home(data);
    }

    @Override
    public void enter(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        boolean restored = core.inventories().restore(ID, player);
        if (!restored) {
            player.getInventory().clear();
        }
        player.setFoodLevel(20);
        if (data != null) {
            data.fresh = false;
            ensurePlot(player, data);
            if (ensureFarm(player, data)) {
                core.guard().allowNext(player);
                player.teleport(home(data));
            }
            refreshFields(data);
            finishConversion(player, data);
            refreshHoe(player, data);
            spawnLabels(data);
            quests.roll(data, LocalDate.now().toString());
            updateStats(player, data);
        }
        core.messages().send(player, "gens.welcome");
    }

    @Override
    public void leave(Player player, boolean quitting) {
        removeLabels(player.getUniqueId());
        core.inventories().save(ID, player);
        if (!quitting) {
            player.getInventory().clear();
        }
    }

    /** Átállás után: a régi generátorokért és a megmaradt régi termésért (szén, vas...) pénz jár, a tárgyak eltűnnek. */
    private void finishConversion(Player player, GensData.Player data) {
        if (!data.legacyPending) {
            return;
        }
        data.legacyPending = false;
        long refund = 0;
        long count = data.legacyGenerators.size();
        for (int tier : data.legacyGenerators) {
            refund += GensData.legacyGenerator(tier);
        }
        data.legacyGenerators.clear();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            long[] value = legacyValue(contents[i]);
            if (value != null) {
                refund += value[0];
                count += value[1];
                inventory.setItem(i, null);
            }
        }
        ItemStack[] pack = backpacks.get(player.getUniqueId());
        if (pack != null) {
            for (int i = 0; i < pack.length; i++) {
                long[] value = legacyValue(pack[i]);
                if (value != null) {
                    refund += value[0];
                    count += value[1];
                    pack[i] = null;
                }
            }
            core.inventories().saveItems("gens_backpack", player.getUniqueId(), pack);
        }
        if (refund > 0) {
            core.economy().deposit(player, currency, refund, "gens:farm-refund");
        }
        core.messages().sendList(player, refund > 0 ? "gens.farm.converted" : "gens.farm.converted-empty",
                "%generators%", Text.number(count), "%money%", core.economy().format(currency, refund), "%fields%", String.valueOf(unlocked(data)));
    }

    /** Egy régi tárgy értéke: {pénz, generátorok száma}; null, ha nem régi Gens tárgy. */
    private static long[] legacyValue(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        if ("gens:generator".equals(Items.tagOf(item))) {
            return new long[]{GensData.legacyGenerator(Items.valueOf(item)) * item.getAmount(), item.getAmount()};
        }
        long price = item.hasItemMeta() ? 0 : GensData.legacyDrop(item.getType().name());
        return price > 0 ? new long[]{price * item.getAmount(), 0} : null;
    }

    private void updateStats(Player player, GensData.Player data) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.setStat("gens.level", unlocked(data));
            profile.setStat("gens.fields", unlocked(data));
            profile.setStat("gens.hoe_level", hoe.total(data.hoe));
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        GensData.Player data = players.get(player.getUniqueId());
        PlayerProfile profile = core.profiles().profile(player);
        values.put("%mode%", core.modes().info(ID) == null ? "Gens" : core.modes().info(ID).name);
        if (data == null) {
            return;
        }
        String unlocked = String.valueOf(unlocked(data));
        String max = String.valueOf(fields.size());
        values.put("%fields%", unlocked);
        values.put("%max_fields%", max);
        // a régi nevek is maradnak, hogy a saját scoreboardok ne törjenek el
        values.put("%generators%", unlocked);
        values.put("%slots%", max);
        values.put("%plot_level%", unlocked);
        values.put("%hoe%", hoeName(data));
        values.put("%hoe_level%", String.valueOf(hoe.total(data.hoe)));
        values.put("%multiplier%", String.format(Locale.ROOT, "%.2fx", multiplier(data)));
        values.put("%prestige%", String.valueOf(data.prestige));
        values.put("%rebirth%", String.valueOf(data.rebirth));
        values.put("%production%", Text.number(productionPerMinute(data)));
        values.put("%plot%", "#" + data.plot);
        values.put("%earned%", Text.number(profile == null ? 0 : profile.stat("gens.earned")));
        values.put("%harvested%", Text.number(profile == null ? 0 : profile.stat("gens.harvested")));
    }

    // ---- kapa ----

    public static boolean isHoe(ItemStack item) {
        return item != null && HOE_TAG.equals(Items.tagOf(item));
    }

    public ItemStack hoeItem(GensData.Player data) {
        int tier = hoe.tier(data.hoe);
        Material material = Items.material(HoeUpgrades.MATERIALS[tier], Material.WOODEN_HOE);
        int radius = hoe.radius(data.hoe);
        List<String> lore = core.messages().list("gens.hoe.item.lore",
                "%boseg%", String.valueOf(data.hoe.getOrDefault("boseg", 0)), "%kaszalas%", String.valueOf(data.hoe.getOrDefault("kaszalas", 0)),
                "%aranykez%", String.valueOf(data.hoe.getOrDefault("aranykez", 0)), "%szerencse%", String.valueOf(data.hoe.getOrDefault("szerencse", 0)),
                "%area%", core.messages().raw("gens.hoe.area." + radius), "%level%", String.valueOf(hoe.total(data.hoe)));
        Items item = Items.of(material).name(core.messages().raw("gens.hoe.item.name").replace("%hoe%", hoeName(data))).lore(lore)
                .unbreakable().hideAll().tag(HOE_TAG);
        if (hoe.total(data.hoe) > 0) {
            item.glow();
        }
        return item.build();
    }

    /** Lecseréli a táskában a kapát a mostanira (új anyag, új leírás), vagy ad egyet, ha nincs nála. */
    public void refreshHoe(Player player, GensData.Player data) {
        if (data == null || player.getWorld() != world) {
            return;
        }
        ItemStack fresh = hoeItem(data);
        PlayerInventory inventory = player.getInventory();
        boolean found = false;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isHoe(contents[i])) {
                if (found) {
                    inventory.setItem(i, null);
                } else {
                    inventory.setItem(i, fresh);
                    found = true;
                }
            }
        }
        if (found) {
            return;
        }
        ItemStack first = inventory.getItem(0);
        if (first == null || first.getType().isAir()) {
            inventory.setItem(0, fresh);
        } else {
            give(player, fresh);
        }
    }

    public void buyHoeUpgrade(Player player, String id) {
        GensData.Player data = players.get(player.getUniqueId());
        HoeUpgrades.Enchant enchant = hoe.get(id);
        if (data == null || enchant == null) {
            return;
        }
        int level = data.hoe.getOrDefault(id, 0);
        long price = enchant.priceFor(level);
        if (price < 0) {
            core.messages().send(player, "gens.hoe.max", "%name%", enchant.name);
            return;
        }
        int tierBefore = hoe.tier(data.hoe);
        if (!pay(player, price, "gens:hoe:" + id)) {
            return;
        }
        data.hoe.put(id, level + 1);
        writeProfile(player, data);
        updateStats(player, data);
        refreshHoe(player, data);
        core.messages().send(player, "gens.hoe.bought", "%name%", enchant.name, "%level%", String.valueOf(level + 1), "%max%", String.valueOf(enchant.max));
        if (hoe.tier(data.hoe) > tierBefore) {
            core.messages().send(player, "gens.hoe.new-tier", "%hoe%", hoeName(data));
            player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), "minecraft:block.anvil.use", 0.6f, 1.4f);
        }
        progress(player, "upgrade", 1);
    }

    // ---- földek ----

    public void unlockField(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        GensData.Field next = nextField(data);
        if (next == null) {
            core.messages().send(player, "gens.field.max");
            return;
        }
        if (!pay(player, next.price, "gens:field")) {
            return;
        }
        data.level = next.index + 1;
        data.dirty = true;
        int[] center = plotCenter(data.plot);
        buildField(new Placer(world, core.getLogger()), center, next.index, true);
        int[] middle = FarmLayout.center(next.index);
        if (player.getWorld() == world) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, center[0] + middle[0] + 0.5, height + 1.5, center[1] + middle[1] + 0.5, 60, 3, 0.4, 3, 0);
            player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 0.7f, 1.3f);
        }
        if (player.getWorld() == world) {
            spawnLabels(data);
        }
        updateStats(player, data);
        save(data, false);
        core.messages().send(player, "gens.field.unlocked", "%field%", next.name, "%count%", String.valueOf(unlocked(data)),
                "%max%", String.valueOf(fields.size()), "%price%", core.economy().format(currency, next.price));
        progress(player, "unlock", 1);
    }

    private void spawnLabels(GensData.Player data) {
        removeLabels(data.uuid);
        if (data.plot <= 0 || world == null) {
            return;
        }
        int[] center = plotCenter(data.plot);
        if (!world.isChunkLoaded(center[0] >> 4, center[1] >> 4)) {
            return;
        }
        List<TextDisplay> list = new ArrayList<>();
        for (int i = unlocked(data); i < fields.size(); i++) {
            GensData.Field field = fields.get(i);
            int[] middle = FarmLayout.center(i);
            List<String> lines = core.messages().list(i == data.level ? "gens.labels.next" : "gens.labels.locked",
                    "%field%", field.name, "%price%", core.economy().format(currency, field.price), "%value%", core.economy().format(currency, field.value));
            list.add(label(new Location(world, center[0] + middle[0] + 0.5, height + 1.4, center[1] + middle[1] + 0.5), String.join("\n", lines)));
        }
        list.add(label(new Location(world, center[0] + FarmLayout.SELL[0] + 0.5, height + 3.1, center[1] + FarmLayout.SELL[1] + 0.5),
                String.join("\n", core.messages().list("gens.labels.sell"))));
        list.add(label(new Location(world, center[0] + FarmLayout.HOE[0] + 0.5, height + 2.2, center[1] + FarmLayout.HOE[1] + 0.5),
                String.join("\n", core.messages().list("gens.labels.hoe"))));
        labels.put(data.uuid, list);
    }

    private TextDisplay label(Location location, String text) {
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.addScoreboardTag(LABEL_TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
        display.setShadowed(true);
        display.setLineWidth(200);
        display.setViewRange(0.6f);
        display.setText(text);
        return display;
    }

    private void removeLabels(UUID uuid) {
        List<TextDisplay> list = labels.remove(uuid);
        if (list != null) {
            for (TextDisplay display : list) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
    }

    /** A feliratok nem mentődnek (chunk kirakáskor eltűnnek), ezért ha a gazda a szigetén van, pár másodpercenként pótoljuk. */
    private void checkLabels() {
        for (Player player : world.getPlayers()) {
            GensData.Player data = players.get(player.getUniqueId());
            if (data == null || data.plot <= 0 || plotAt(player.getLocation()) != data.plot) {
                continue;
            }
            List<TextDisplay> list = labels.get(player.getUniqueId());
            boolean broken = list == null;
            if (list != null) {
                for (TextDisplay display : list) {
                    if (!display.isValid()) {
                        broken = true;
                        break;
                    }
                }
            }
            if (broken) {
                spawnLabels(data);
            }
        }
    }

    // ---- aratás ----

    private void harvest(Player player, Block block, Spot spot) {
        GensData.Player owner = ownerOf(spot.plot());
        if (owner == null) {
            warn(player, "gens.owner-offline");
            return;
        }
        boolean own = owner.uuid.equals(player.getUniqueId());
        if (!own && !owner.can(player.getUniqueId(), GensData.COLLECT) && !admin(player)) {
            deny(player);
            return;
        }
        GensData.Player self = players.get(player.getUniqueId());
        if (self == null || spot.field() >= unlocked(owner)) {
            return;
        }
        if (!isHoe(player.getInventory().getItemInMainHand())) {
            warn(player, "gens.need-hoe");
            return;
        }
        GensData.Field field = fields.get(spot.field());
        Crop crop = field.crop;
        if (!mature(block, crop)) {
            warn(player, "gens.not-ripe");
            return;
        }
        Material drop = cropDrops.get(crop);
        if (!self.autosell && !hasRoom(player, self, drop)) {
            warn(player, "gens.full");
            return;
        }
        int radius = hoe.radius(self.hoe);
        List<int[]> cells = radius >= 3 ? FarmLayout.cells(spot.field()) : FarmLayout.around(spot.field(), spot.dx(), spot.dz(), radius);
        double extra = extraPercent(self, owner) + rankBonus(player);
        double multiplier = field.multiplier * multiplier(self);
        double luck = hoe.luckPercent(self.hoe);
        long regrow = regrowMillis(owner, field);
        long now = System.currentTimeMillis();
        BlockData young = block(crop.young());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int crops = 0;
        long items = 0;
        int lucky = 0;
        for (int[] cell : cells) {
            int x = spot.cx() + cell[0];
            int z = spot.cz() + cell[1];
            long key = GensData.key(x, height + 1, z);
            if (regrowing.containsKey(key)) {
                continue;
            }
            Block target = world.getBlockAt(x, height + 1, z);
            if (!mature(target, crop)) {
                continue;
            }
            target.setBlockData(young, false);
            regrowing.put(key, new Regrow(x, height + 1, z, crop, now + regrow / 2, now + regrow));
            crops++;
            items += HoeUpgrades.amount(multiplier, extra, random.nextDouble());
            if (luck > 0 && random.nextDouble() * 100.0 < luck) {
                lucky++;
            }
        }
        if (crops == 0) {
            return;
        }
        long unit = unitPrice(self, field.value);
        if (self.autosell) {
            autosold.merge(self.uuid, Math.round(GenMath.sellValue(items, unit) * progress.autosellRate()), Long::sum);
        } else {
            deliver(player, self, drop, items);
        }
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.addStat("gens.harvested", crops);
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, Math.min(12, 2 + crops / 4),
                0.3 + Math.min(3, radius), 0.2, 0.3 + Math.min(3, radius), 0);
        player.playSound(block.getLocation(), crop.wholeBlock() ? "minecraft:block.wood.break" : "minecraft:block.crop.break", 0.6f,
                0.9f + random.nextFloat() * 0.3f);
        core.messages().actionBar(player, self.autosell ? "gens.harvested-sold" : "gens.harvested", "%amount%", Text.number(items),
                "%field%", field.name, "%money%", core.economy().format(currency, GenMath.sellValue(items, unit)));
        if (lucky > 0) {
            long found = sellBoost(player, self, GenMath.sellValue(lucky, Math.round(unit * hoe.luckMultiplier())));
            if (found > 0 && core.economy().deposit(player, currency, found, "gens:luck") == Ledger.Result.OK) {
                if (profile != null) {
                    profile.addStat("gens.earned", found);
                }
                core.messages().send(player, "gens.lucky", "%money%", core.economy().format(currency, found));
                player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.8f, 1.6f);
                progress(player, "earn", found);
            }
        }
        progress(player, "harvest", crops);
    }

    /** Van-e még hely a terménynek a táskában vagy a hátizsákban. */
    private boolean hasRoom(Player player, GensData.Player data, Material drop) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir() || item.getType() == drop && !item.hasItemMeta() && item.getAmount() < item.getMaxStackSize()) {
                return true;
            }
        }
        ItemStack[] pack = backpacks.get(player.getUniqueId());
        if (pack == null || player.getOpenInventory().getTopInventory().getHolder() instanceof GensBackpack) {
            return false;
        }
        int size = progress.backpackSize(largeBackpack(player, data));
        for (int i = 0; i < size && i < pack.length; i++) {
            if (pack[i] == null || pack[i].getType().isAir() || pack[i].getType() == drop && !pack[i].hasItemMeta() && pack[i].getAmount() < drop.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** A termény a táskába megy, ami nem fér el, a hátizsákba, és ami ott sem, a játékos lába elé. */
    private void deliver(Player player, GensData.Player data, Material drop, long amount) {
        while (amount > 0) {
            int stack = (int) Math.min(amount, drop.getMaxStackSize());
            Map<Integer, ItemStack> left = player.getInventory().addItem(new ItemStack(drop, stack));
            int notGiven = 0;
            for (ItemStack item : left.values()) {
                notGiven += item.getAmount();
            }
            amount -= stack - notGiven;
            if (notGiven > 0) {
                break;
            }
        }
        ItemStack[] pack = backpacks.get(player.getUniqueId());
        if (amount > 0 && pack != null && !(player.getOpenInventory().getTopInventory().getHolder() instanceof GensBackpack)) {
            amount = stash(pack, progress.backpackSize(largeBackpack(player, data)), drop, amount);
        }
        while (amount > 0) {
            int stack = (int) Math.min(amount, drop.getMaxStackSize());
            world.dropItemNaturally(player.getLocation(), new ItemStack(drop, stack));
            amount -= stack;
        }
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    public void tick() {
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= autosellFlush) {
            autosellFlush = now + 10_000L;
            for (UUID uuid : new ArrayList<>(autosold.keySet())) {
                flushAutosell(uuid);
            }
        }
        if (now >= labelCheck) {
            labelCheck = now + 5_000L;
            checkLabels();
        }
        if (regrowing.isEmpty()) {
            return;
        }
        Iterator<Regrow> iterator = regrowing.values().iterator();
        while (iterator.hasNext()) {
            Regrow regrow = iterator.next();
            if (now < regrow.half || regrow.halfDone && now < regrow.ready) {
                continue;
            }
            if (!world.isChunkLoaded(regrow.x >> 4, regrow.z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(regrow.x, regrow.y, regrow.z);
            if (now >= regrow.ready) {
                block.setBlockData(block(regrow.crop.mature()), false);
                iterator.remove();
            } else {
                regrow.halfDone = true;
                if (!regrow.crop.wholeBlock()) {
                    block.setBlockData(block(regrow.crop.half()), false);
                }
            }
        }
    }

    /** Az automatikus eladás pénze tíz másodpercenként egyben jön meg (így nem pörög az egyenleg minden aratásnál). */
    private void flushAutosell(UUID uuid) {
        Long amount = autosold.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (amount == null || amount <= 0 || player == null) {
            return;
        }
        GensData.Player data = players.get(uuid);
        long total = sellBoost(player, data, amount);
        if (core.economy().deposit(player, currency, total, "gens:autosell") == Ledger.Result.OK) {
            PlayerProfile profile = core.profiles().profile(player);
            if (profile != null) {
                profile.addStat("gens.earned", total);
            }
            progress(player, "earn", total);
            if (player.getWorld() == world) {
                core.messages().actionBar(player, "gens.autosold", "%money%", core.economy().format(currency, total));
            }
        }
    }

    // ---- jogok ----

    private boolean admin(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    private boolean canBuild(Player player, Location location) {
        return canDo(player, location, GensData.BUILD);
    }

    /** Szabad-e ezt a szigeten: a tulajdonos mindent, a tag a neki megadott jogokkal (építés, ládák, aratás). */
    private boolean canDo(Player player, Location location, int flag) {
        if (admin(player)) {
            return true;
        }
        int plot = plotAt(location);
        if (plot <= 0) {
            return false;
        }
        if (location.getBlockY() < height - 25 || location.getBlockY() > height + 60) {
            return false;
        }
        GensData.Player data = players.get(player.getUniqueId());
        if (data != null && data.plot == plot) {
            return true;
        }
        GensData.Player owner = ownerOf(plot);
        return owner != null && owner.can(player.getUniqueId(), flag);
    }

    private void deny(Player player) {
        warn(player, "gens.not-your-plot");
    }

    /** Action bar figyelmeztetés, legfeljebb kétmásodpercenként (az aratásnál sok kattintás jön egymás után). */
    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            warned.put(player.getUniqueId(), now);
            core.messages().actionBar(player, key);
        }
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public void progress(Player player, String type, long amount) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        for (QuestBook.Quest quest : quests.add(data, today(), type, amount)) {
            if (quest.reward > 0) {
                core.economy().deposit(player, currency, quest.reward, "gens:quest");
            }
            PlayerProfile profile = core.profiles().profile(player);
            if (profile != null) {
                profile.addStat("gens.quests", 1);
            }
            core.messages().send(player, "gens.quest-done", "%quest%", quest.name, "%reward%", core.economy().format(currency, quest.reward));
            player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 0.7f, 1.2f);
        }
    }

    // ---- események ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (isHoe(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (spot(block) != null && !admin(player)) {
            event.setCancelled(true);
            warn(player, "gens.field-protected");
            return;
        }
        if (!canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        Spot spot = spot(block);
        if (spot != null) {
            boolean hoeInHand = isHoe(player.getInventory().getItemInMainHand());
            if (admin(player) && !hoeInHand) {
                return;
            }
            event.setCancelled(true);
            if (spot.dy() == 1) {
                harvest(player, block, spot);
            } else {
                warn(player, "gens.field-protected");
            }
            return;
        }
        if (plazaBlock(block, FarmLayout.SELL, Material.BARREL) || plazaBlock(block, FarmLayout.HOE, Material.ANVIL)) {
            if (!admin(player)) {
                event.setCancelled(true);
                return;
            }
        }
        if (!canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    /** A dinnyét és a tököt a kapával egy ütéssel le lehet aratni (a többi növény amúgy is azonnal törik). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != world || !isHoe(event.getItemInHand())) {
            return;
        }
        Spot spot = spot(block);
        if (spot != null && spot.dy() == 1 && spot.field() < fields.size() && fields.get(spot.field()).crop.wholeBlock()) {
            event.setInstaBreak(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != world) {
            return;
        }
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL) {
            // a termőföldet senki ne taposhassa le
            if (block != null && (block.getType() == Material.FARMLAND || spot(block) != null || !canBuild(player, block.getLocation()))) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            if (block != null && spot(block) != null) {
                event.setCancelled(true);
            }
            return;
        }
        boolean hoeInHand = isHoe(event.getItem());
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (hoeInHand) {
                core.gensMenu().hoe(player);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        if (plazaBlock(block, FarmLayout.SELL, Material.BARREL)) {
            event.setCancelled(true);
            sellAll(player);
            return;
        }
        if (plazaBlock(block, FarmLayout.HOE, Material.ANVIL)) {
            event.setCancelled(true);
            core.gensMenu().hoe(player);
            return;
        }
        if (spot(block) != null) {
            // bogyó szedés, csontliszt, kapálás: a földeken semmi ilyet nem lehet
            event.setCancelled(true);
            if (hoeInHand) {
                core.gensMenu().hoe(player);
            }
            return;
        }
        if (hoeInHand && !player.isSneaking() && !(block.getState() instanceof org.bukkit.block.Container)) {
            event.setCancelled(true);
            core.gensMenu().hoe(player);
            return;
        }
        int flag = block.getState() instanceof org.bukkit.block.Container ? GensData.CONTAINERS : GensData.BUILD;
        if (!canDo(player, block.getLocation(), flag)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvestBlock(PlayerHarvestBlockEvent event) {
        if (event.getHarvestedBlock().getWorld() == world && spot(event.getHarvestedBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** A földeken csak a mi rendszerünk növeszt (a véletlen növekedés kikapcsolva), máshol nőhetnek a csemeték. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (event.getBlock().getWorld() == world && spot(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getBlock().getWorld() == world && spot(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Víz nélkül is nedves marad a termőföld, és nem válik földdé. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (event.getBlock().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getWorld() == world && event.getBlock().getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getWorld() == world && (event.getBlock().getType() == Material.FARMLAND || spot(event.getBlock()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isHoe(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            core.messages().actionBar(event.getPlayer(), "gens.hoe.no-drop");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (event.getPlayer().getWorld() == world && (spot(target) != null || !canBuild(event.getPlayer(), target.getLocation()))) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getWorld() != world) {
            return;
        }
        if (spot(event.getToBlock()) != null || plotAt(event.getToBlock().getLocation()) != plotAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == world) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld() == world) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.getWorld() != world || !(entity instanceof Player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Player player = (Player) entity;
            Bukkit.getScheduler().runTask(core, () -> rescue(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() != world || to.getY() >= voidY) {
            return;
        }
        rescue(event.getPlayer());
    }

    private void rescue(Player player) {
        if (!player.isOnline() || player.getWorld() != world) {
            return;
        }
        player.setFallDistance(0f);
        GensData.Player data = players.get(player.getUniqueId());
        core.guard().allowNext(player);
        player.teleport(data != null && data.plot > 0 ? home(data) : hub);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(core, () -> {
            if (Bukkit.getPlayer(event.getPlayer().getUniqueId()) == null) {
                detach(event.getPlayer().getUniqueId());
            }
        });
    }

    @Override
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(players.keySet())) {
            detach(uuid);
        }
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(LABEL_TAG)) {
                    entity.remove();
                }
            }
            // ami épp visszanőne, az most rögtön beérik (különben a következő belépésig kicsi maradna)
            for (Regrow regrow : regrowing.values()) {
                if (world.isChunkLoaded(regrow.x >> 4, regrow.z >> 4)) {
                    world.getBlockAt(regrow.x, regrow.y, regrow.z).setBlockData(block(regrow.crop.mature()), false);
                }
            }
            regrowing.clear();
        }
    }

    // ---- eladás, napi jutalom ----

    public long sellAll(Player player) {
        if (player.getWorld() != world) {
            core.messages().send(player, "gens.only-in-gens");
            return 0;
        }
        GensData.Player data = players.get(player.getUniqueId());
        long total = 0;
        long count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.hasItemMeta() && Items.tagOf(item) != null) {
                continue;
            }
            Long price = prices.get(item.getType());
            if (price == null || price <= 0) {
                continue;
            }
            total += GenMath.sellValue(item.getAmount(), unitPrice(data, price));
            count += item.getAmount();
            player.getInventory().setItem(i, null);
        }
        ItemStack[] pack = backpacks.get(player.getUniqueId());
        if (pack != null && !(player.getOpenInventory().getTopInventory().getHolder() instanceof GensBackpack)) {
            for (int i = 0; i < pack.length; i++) {
                ItemStack item = pack[i];
                if (item == null || item.hasItemMeta() && Items.tagOf(item) != null) {
                    continue;
                }
                Long price = prices.get(item.getType());
                if (price == null || price <= 0) {
                    continue;
                }
                total += GenMath.sellValue(item.getAmount(), unitPrice(data, price));
                count += item.getAmount();
                pack[i] = null;
            }
        }
        if (total <= 0) {
            core.messages().send(player, "gens.nothing-to-sell");
            return 0;
        }
        total = sellBoost(player, data, total);
        Ledger.Result result = core.economy().deposit(player, currency, total, "gens:sell");
        if (result != Ledger.Result.OK) {
            core.messages().send(player, "gens.sell-limit");
            return 0;
        }
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.addStat("gens.earned", total);
            profile.addStat("gens.sold", count);
        }
        core.messages().send(player, "gens.sold", "%amount%", Text.number(count), "%money%", core.economy().format(currency, total));
        player.playSound(player.getLocation(), "minecraft:entity.villager.yes", 0.6f, 1.1f);
        progress(player, "sell_items", count);
        progress(player, "earn", total);
        return total;
    }

    public void daily(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        long day = LocalDate.now().toEpochDay();
        if (data.daily >= day) {
            core.messages().send(player, "gens.daily-taken");
            return;
        }
        if (dailyAmount > 0 && core.economy().deposit(player, currency, dailyAmount, "gens:daily") == Ledger.Result.OK) {
            data.daily = day;
            data.dirty = true;
            save(data, false);
            core.messages().send(player, "gens.daily", "%money%", core.economy().format(currency, dailyAmount));
        }
    }

    public boolean dailyAvailable(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        return data != null && data.daily < LocalDate.now().toEpochDay();
    }

    // ---- haza, látogatás, tagok ----

    public void goHome(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (player.getWorld() != world) {
            core.modes().sendAndReport(player, ID);
            return;
        }
        ensurePlot(player, data);
        ensureFarm(player, data);
        refreshFields(data);
        finishConversion(player, data);
        core.guard().allowNext(player);
        player.teleport(home(data));
        spawnLabels(data);
    }

    public void visit(Player player, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            GensData.Player data = players.get(online.getUniqueId());
            if (data == null || data.plot <= 0) {
                core.messages().send(player, "gens.no-plot");
                return;
            }
            visitPlot(player, data.plot, online.getName());
            return;
        }
        core.database().query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT g.plot, p.name FROM im_gens_plots g JOIN im_players p ON p.uuid = g.uuid WHERE p.name_lower = ?")) {
                statement.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? new Object[]{rows.getInt(1), rows.getString(2)} : null;
                }
            }
        }).whenComplete((row, error) -> Bukkit.getScheduler().runTask(core, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (row == null) {
                core.messages().send(player, "gens.no-plot");
                return;
            }
            visitPlot(player, (Integer) row[0], (String) row[1]);
        }));
    }

    private void visitPlot(Player player, int plot, String owner) {
        if (player.getWorld() != world) {
            core.messages().send(player, "gens.only-in-gens");
            return;
        }
        GensData.Player target = ownerOf(plot);
        GensData.Player self = players.get(player.getUniqueId());
        if (target != null && target.locked && !target.trusted.contains(player.getUniqueId()) && (self == null || self.plot != plot)
                && !player.hasPermission("icemine.admin.build")) {
            core.messages().send(player, "gens.locked-visit", "%player%", owner);
            return;
        }
        int[] center = plotCenter(plot);
        Layout.Point spawn = GensBuild.plotSpawn();
        core.guard().allowNext(player);
        player.teleport(new Location(world, center[0] + spawn.x, height + spawn.y, center[1] + spawn.z, (float) spawn.yaw, 0f));
        core.messages().send(player, "gens.visiting", "%player%", owner);
    }

    public void trust(Player player, String name, boolean add) {
        GensData.Player data = players.get(player.getUniqueId());
        Player target = Bukkit.getPlayerExact(name);
        if (data == null) {
            return;
        }
        if (target == null || target.equals(player)) {
            core.messages().send(player, "general.no-player");
            return;
        }
        if (add) {
            if (data.trusted.size() >= 10) {
                core.messages().send(player, "gens.trust-limit");
                return;
            }
            data.trusted.add(target.getUniqueId());
            data.permissions.remove(target.getUniqueId());
            core.messages().send(player, "gens.trusted", "%player%", target.getName());
            core.messages().send(target, "gens.trusted-you", "%player%", player.getName());
        } else {
            data.trusted.remove(target.getUniqueId());
            data.permissions.remove(target.getUniqueId());
            core.messages().send(player, "gens.untrusted", "%player%", target.getName());
        }
        data.dirty = true;
        save(data, false);
    }

    public void togglePermission(Player owner, UUID member, int flag) {
        GensData.Player data = players.get(owner.getUniqueId());
        if (data == null || !data.trusted.contains(member)) {
            return;
        }
        int flags = data.permissionsOf(member) ^ flag;
        if (flags == GensData.DEFAULT_MEMBER) {
            data.permissions.remove(member);
        } else {
            data.permissions.put(member, flags);
        }
        data.dirty = true;
        save(data, false);
    }

    public void removeMember(Player owner, UUID member) {
        GensData.Player data = players.get(owner.getUniqueId());
        if (data == null || !data.trusted.remove(member)) {
            return;
        }
        data.permissions.remove(member);
        data.dirty = true;
        save(data, false);
        core.messages().send(owner, "gens.untrusted", "%player%", nameOf(member));
    }

    public String nameOf(UUID uuid) {
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
    }

    public void toggleLock(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.locked = !data.locked;
        writeProfile(player, data);
        core.messages().send(player, data.locked ? "gens.lock.on" : "gens.lock.off");
    }

    // ---- hátizsák ----

    /** Betesz egy adag terméket a hátizsákba (a látható részbe), és visszaadja, mennyi nem fért el. */
    static long stash(ItemStack[] pack, int size, Material material, long amount) {
        int max = material.getMaxStackSize();
        for (int i = 0; i < size && i < pack.length && amount > 0; i++) {
            ItemStack item = pack[i];
            if (item != null && item.getType() == material && !item.hasItemMeta() && item.getAmount() < max) {
                int add = (int) Math.min(amount, max - item.getAmount());
                item.setAmount(item.getAmount() + add);
                amount -= add;
            }
        }
        for (int i = 0; i < size && i < pack.length && amount > 0; i++) {
            if (pack[i] == null || pack[i].getType().isAir()) {
                int add = (int) Math.min(amount, max);
                pack[i] = new ItemStack(material, add);
                amount -= add;
            }
        }
        return amount;
    }

    public boolean largeBackpack(Player player, GensData.Player data) {
        return data.backpackLarge || player.hasPermission("icemine.gens.backpack.large");
    }

    public void openBackpack(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        ItemStack[] pack = backpacks.get(player.getUniqueId());
        if (data == null || pack == null) {
            return;
        }
        int size = progress.backpackSize(largeBackpack(player, data));
        player.openInventory(new GensBackpack(player.getUniqueId(), core.messages().raw("gens.backpack.title")
                .replace("%size%", String.valueOf(size)), pack, size).getInventory());
        player.playSound(player.getLocation(), "minecraft:item.armor.equip_leather", 0.8f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBackpackClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GensBackpack backpack)) {
            return;
        }
        ItemStack[] pack = backpacks.get(backpack.owner());
        if (pack == null) {
            return;
        }
        backpack.copyInto(pack);
        core.inventories().saveItems("gens_backpack", backpack.owner(), pack);
    }

    public void buyLargeBackpack(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (largeBackpack(player, data)) {
            core.messages().send(player, "gens.backpack.already");
            return;
        }
        if (!pay(player, progress.backpackPrice(), "gens:backpack")) {
            return;
        }
        data.backpackLarge = true;
        writeProfile(player, data);
        core.messages().send(player, "gens.backpack.bought", "%size%", String.valueOf(progress.backpackSize(true)));
    }

    private boolean pay(Player player, long price, String reason) {
        if (price <= 0) {
            return true;
        }
        if (core.economy().withdraw(player, currency, price, reason) != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, price));
            return false;
        }
        return true;
    }

    // ---- automatikus eladás ----

    public boolean autosellUnlocked(Player player, GensData.Player data) {
        return data.autosellOwned || player.hasPermission("icemine.gens.autosell");
    }

    public void toggleAutosell(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (!autosellUnlocked(player, data)) {
            if (!pay(player, progress.autosellPrice(), "gens:autosell")) {
                return;
            }
            data.autosellOwned = true;
            core.messages().send(player, "gens.autosell.bought");
        }
        data.autosell = !data.autosell;
        writeProfile(player, data);
        core.messages().send(player, data.autosell ? "gens.autosell.on" : "gens.autosell.off");
    }

    // ---- sziget fejlesztések ----

    public void buyIslandUpgrade(Player player, String id) {
        GensData.Player data = players.get(player.getUniqueId());
        GensProgress.IslandUpgrade upgrade = progress.upgrades().get(id);
        if (data == null || upgrade == null) {
            return;
        }
        int level = data.upgrades.getOrDefault(id, 0);
        long price = upgrade.priceFor(level);
        if (price < 0) {
            core.messages().send(player, "gens.upgrade.max", "%name%", upgrade.name);
            return;
        }
        if (!pay(player, price, "gens:island:" + id)) {
            return;
        }
        data.upgrades.put(id, level + 1);
        writeProfile(player, data);
        core.messages().send(player, "gens.upgrade.bought", "%name%", upgrade.name, "%level%", String.valueOf(level + 1),
                "%max%", String.valueOf(upgrade.maxLevel()));
        player.playSound(player.getLocation(), "minecraft:block.anvil.use", 0.6f, 1.3f);
        progress(player, "upgrade", 1);
    }

    // ---- prestige és rebirth ----

    /** Két kattintás kell (10 mp-en belül), mert mindkettő elveszi a pénzt és lezárja a földeket. */
    private boolean confirmed(Player player, String what) {
        long now = System.currentTimeMillis();
        Long last = confirm.get(player.getUniqueId());
        String key = what + ":" + player.getUniqueId();
        if (last != null && now - last < 10_000L && key.equals(confirmKey.get(player.getUniqueId()))) {
            confirm.remove(player.getUniqueId());
            confirmKey.remove(player.getUniqueId());
            return true;
        }
        confirm.put(player.getUniqueId(), now);
        confirmKey.put(player.getUniqueId(), key);
        return false;
    }

    public void prestige(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null || player.getWorld() != world) {
            return;
        }
        if (data.prestige >= progress.prestigeMax()) {
            core.messages().send(player, "gens.prestige.max");
            return;
        }
        if (!progress.canPrestige(unlocked(data), data.prestige)) {
            core.messages().send(player, "gens.prestige.need-level", "%level%", String.valueOf(progress.prestigeLevel()));
            return;
        }
        long price = progress.prestigeCost(data.prestige, data.rebirth);
        if (core.economy().balance(player, currency) < price) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, price));
            return;
        }
        if (!confirmed(player, "prestige")) {
            core.messages().send(player, "gens.prestige.confirm", "%price%", core.economy().format(currency, price),
                    "%next%", String.valueOf(data.prestige + 1), "%bonus%", Text.decimal(progress.prestigeBonus()));
            return;
        }
        if (!pay(player, price, "gens:prestige")) {
            return;
        }
        data.prestige++;
        resetIsland(player, data);
        writeProfile(player, data);
        core.messages().send(player, "gens.prestige.done", "%prestige%", String.valueOf(data.prestige),
                "%multiplier%", String.format(Locale.ROOT, "%.2fx", multiplier(data)));
        for (Player other : world.getPlayers()) {
            if (!other.equals(player)) {
                core.messages().send(other, "gens.prestige.broadcast", "%player%", player.getName(), "%prestige%", String.valueOf(data.prestige));
            }
        }
        player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.3);
    }

    public void rebirth(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null || player.getWorld() != world) {
            return;
        }
        if (!progress.canRebirth(data.prestige, data.rebirth)) {
            core.messages().send(player, "gens.rebirth.need-prestige", "%prestige%", String.valueOf(progress.rebirthPrestige()));
            return;
        }
        long price = progress.rebirthCost(data.rebirth);
        if (core.economy().balance(player, currency) < price) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, price));
            return;
        }
        if (!confirmed(player, "rebirth")) {
            core.messages().send(player, "gens.rebirth.confirm", "%price%", core.economy().format(currency, price),
                    "%next%", String.valueOf(data.rebirth + 1), "%bonus%", Text.decimal(progress.rebirthBonus()));
            return;
        }
        if (!pay(player, price, "gens:rebirth")) {
            return;
        }
        data.rebirth++;
        data.prestige = 0;
        for (String id : new ArrayList<>(data.upgrades.keySet())) {
            data.upgrades.put(id, 0);
        }
        for (String id : HoeUpgrades.IDS) {
            data.hoe.put(id, 0);
        }
        resetIsland(player, data);
        writeProfile(player, data);
        refreshHoe(player, data);
        core.messages().send(player, "gens.rebirth.done", "%rebirth%", String.valueOf(data.rebirth),
                "%multiplier%", String.format(Locale.ROOT, "%.2fx", multiplier(data)));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                core.messages().send(other, "gens.rebirth.broadcast", "%player%", player.getName(), "%rebirth%", String.valueOf(data.rebirth));
            }
        }
        player.playSound(player.getLocation(), "minecraft:entity.ender_dragon.growl", 0.7f, 1.2f);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 120, 1.0, 1.5, 1.0, 0.15);
    }

    /**
     * Újrakezdés: a Gens pénz eltűnik, és csak az első föld marad nyitva (a többi újra lezárul). A kapa megmarad
     * (rebirth-nél a hívó nullázza), az épített blokkok is maradnak.
     */
    private void resetIsland(Player player, GensData.Player data) {
        autosold.remove(player.getUniqueId());
        core.economy().set(player, currency, 0, "gens:reset");
        data.level = 1;
        data.dirty = true;
        purgeRegrow(data.plot);
        int[] center = plotCenter(data.plot);
        Placer placer = new Placer(world, core.getLogger());
        for (int i = 0; i < FarmLayout.FIELDS; i++) {
            buildField(placer, center, i, i < unlocked(data));
        }
        spawnLabels(data);
        updateStats(player, data);
        save(data, true);
    }

    public Location hub() {
        return hub;
    }

    public long dailyAmount() {
        return dailyAmount;
    }
}
