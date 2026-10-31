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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class GensMode implements ModeHandler, Listener {

    public static final String ID = "gens";

    private final IceMineCore core;
    private final Map<UUID, GensData.Player> players = new ConcurrentHashMap<>();
    private final Map<UUID, GensData.Player> pending = new ConcurrentHashMap<>();
    private final Map<Long, GensData.Generator> generators = new HashMap<>();
    private final TreeMap<Integer, GensData.Tier> tiers = new TreeMap<>();
    private final TreeMap<Integer, GensData.Level> levels = new TreeMap<>();
    private final Map<Material, Long> prices = new HashMap<>();
    private final QuestBook quests = new QuestBook();
    private final Map<UUID, Long> warned = new HashMap<>();
    private World world;
    private Location hub;
    private int spacing = 96;
    private int buildRadius = 40;
    private int height = 100;
    private int nextPlot = 1;
    private String currency = "gens";
    private long generatorPrice = 300;
    private int starterGenerators = 2;
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
        buildRadius = Math.min(spacing / 2 - 4, values.integer("plot.build-radius", 40, 16, 250));
        height = values.integer("plot.height", 100, 0, 250);
        voidY = values.decimal("void-y", height - 40, -64, 320);
        currency = values.text("currency", "gens");
        if (core.economy().currency(currency) == null) {
            problems.add("gens.yml -> currency: ismeretlen pénznem (" + currency + "), az economy.yml-ben is létre kell hozni");
            currency = core.economy().defaultCurrency();
        }
        generatorPrice = values.number("generator-price", 300, 0, Long.MAX_VALUE / 4);
        starterGenerators = values.integer("starter-generators", 2, 0, 10);
        dailyAmount = values.number("daily.amount", 500, 0, Long.MAX_VALUE / 4);
        problems.addAll(values.problems());
        loadTiers(config, problems);
        loadLevels(config, problems);
        loadQuests(config, problems);
        prices.clear();
        for (GensData.Tier tier : tiers.values()) {
            prices.put(tier.drop, tier.value);
        }
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

    private void loadTiers(YamlConfiguration config, List<String> problems) {
        tiers.clear();
        ConfigurationSection section = config.getConfigurationSection("tiers");
        if (section == null) {
            problems.add("gens.yml -> tiers: nincs egy generátor szint sem");
            return;
        }
        for (String key : section.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                problems.add("gens.yml -> tiers." + key + ": a kulcs csak szám lehet (1, 2, 3...)");
                continue;
            }
            String base = "tiers." + key + ".";
            Values values = new Values("gens.yml", config::get);
            Material block = Items.material(values.text(base + "block", "COAL_BLOCK"), Material.COAL_BLOCK);
            Material drop = Items.material(values.text(base + "drop", "COAL"), Material.COAL);
            if (!block.isBlock()) {
                problems.add("gens.yml -> " + base + "block: ez nem blokk (" + block + ")");
                continue;
            }
            tiers.put(level, new GensData.Tier(level, values.text(base + "name", key), block, drop,
                    values.number(base + "value", 1, 0, 1_000_000_000L),
                    values.integer(base + "interval", 6, 1, 3600),
                    values.integer(base + "amount", 1, 1, 64),
                    values.number(base + "capacity", 64, 1, 100_000),
                    values.number(base + "upgrade", 0, 0, Long.MAX_VALUE / 4)));
            problems.addAll(values.problems());
        }
    }

    private void loadLevels(YamlConfiguration config, List<String> problems) {
        levels.clear();
        ConfigurationSection section = config.getConfigurationSection("levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    String base = "levels." + key + ".";
                    Values values = new Values("gens.yml", config::get);
                    levels.put(level, new GensData.Level(level, values.integer(base + "slots", 6, 1, 200),
                            values.decimal(base + "multiplier", 1.0, 0.1, 100.0), values.number(base + "price", 0, 0, Long.MAX_VALUE / 4)));
                    problems.addAll(values.problems());
                } catch (NumberFormatException exception) {
                    problems.add("gens.yml -> levels." + key + ": a kulcs csak szám lehet");
                }
            }
        }
        if (levels.isEmpty()) {
            levels.put(1, new GensData.Level(1, 6, 1.0, 0));
        }
    }

    private void loadQuests(YamlConfiguration config, List<String> problems) {
        List<QuestBook.Quest> list = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("quests.pool");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String type = section.getString(id + ".type", "").toLowerCase(Locale.ROOT);
                if (!type.equals("sell_items") && !type.equals("collect_items") && !type.equals("upgrade") && !type.equals("earn") && !type.equals("place_gen")) {
                    problems.add("gens.yml -> quests.pool." + id + ".type: sell_items, collect_items, upgrade, earn vagy place_gen lehet");
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
                try (PreparedStatement statement = connection.prepareStatement("SELECT world_x, world_y, world_z, tier, stored FROM im_gens_generators WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            result.generators.add(new GensData.Generator(uuid, rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4), rows.getLong(5)));
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
        long now = System.currentTimeMillis();
        for (GensData.Generator generator : data.generators) {
            generator.next = now + interval(generator) * 1000L;
            generators.put(generator.key(), generator);
        }
    }

    public void detach(UUID uuid) {
        GensData.Player data = players.remove(uuid);
        warned.remove(uuid);
        if (data == null) {
            return;
        }
        for (GensData.Generator generator : data.generators) {
            generators.remove(generator.key());
        }
        save(data, true);
    }

    public GensData.Player data(Player player) {
        return players.get(player.getUniqueId());
    }

    private void save(GensData.Player data, boolean all) {
        if (data.plot < 0) {
            return;
        }
        String trusted = data.encodeTrusted();
        String questText = data.encodeQuests();
        int plot = data.plot;
        int level = data.level;
        long daily = data.daily;
        List<long[]> rows = new ArrayList<>();
        for (GensData.Generator generator : data.generators) {
            if (all || generator.dirty) {
                rows.add(new long[]{generator.x, generator.y, generator.z, generator.tier, generator.stored});
                generator.dirty = false;
            }
        }
        boolean main = all || data.dirty;
        data.dirty = false;
        Database database = core.database();
        String uuid = data.uuid.toString();
        database.execute(connection -> {
            if (main) {
                try (PreparedStatement statement = connection.prepareStatement(database.upsertPlot())) {
                    statement.setString(1, uuid);
                    statement.setInt(2, plot);
                    statement.setInt(3, level);
                    statement.setString(4, trusted);
                    statement.setLong(5, daily);
                    statement.setString(6, questText);
                    statement.executeUpdate();
                }
            }
            if (!rows.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement(database.upsertGenerator())) {
                    for (long[] row : rows) {
                        statement.setInt(1, (int) row[0]);
                        statement.setInt(2, (int) row[1]);
                        statement.setInt(3, (int) row[2]);
                        statement.setString(4, uuid);
                        statement.setInt(5, (int) row[3]);
                        statement.setLong(6, row[4]);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
        });
    }

    private void deleteGenerator(GensData.Generator generator) {
        int x = generator.x;
        int y = generator.y;
        int z = generator.z;
        core.database().execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_gens_generators WHERE world_x = ? AND world_y = ? AND world_z = ?")) {
                statement.setInt(1, x);
                statement.setInt(2, y);
                statement.setInt(3, z);
                statement.executeUpdate();
            }
        });
    }

    public void saveAll() {
        for (GensData.Player data : players.values()) {
            save(data, false);
        }
    }

    public GensData.Level level(GensData.Player data) {
        GensData.Level level = levels.floorEntry(Math.max(1, data.level)) == null ? levels.firstEntry().getValue() : levels.floorEntry(Math.max(1, data.level)).getValue();
        return level;
    }

    public GensData.Level nextLevel(GensData.Player data) {
        Map.Entry<Integer, GensData.Level> next = levels.higherEntry(data.level);
        return next == null ? null : next.getValue();
    }

    public GensData.Tier tier(int level) {
        return tiers.get(level);
    }

    public TreeMap<Integer, GensData.Tier> tiers() {
        return tiers;
    }

    private int interval(GensData.Generator generator) {
        GensData.Tier tier = tiers.get(generator.tier);
        return tier == null ? 10 : tier.interval;
    }

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
        int[] center = plotCenter(data.plot);
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(center[0] - GensBuild.PLOT_CLEAR, height - 25, center[1] - GensBuild.PLOT_CLEAR, center[0] + GensBuild.PLOT_CLEAR, height + 30, center[1] + GensBuild.PLOT_CLEAR);
        new GensBuild(placer, center[0], height, center[1], false, data.plot).build();
        save(data, true);
        core.messages().sendList(player, "gens.first-join", "%player%", player.getName());
    }

    @Override
    public Location spawn(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null || world == null) {
            return hub;
        }
        ensurePlot(player, data);
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
            if (data != null && data.fresh) {
                for (int i = 0; i < starterGenerators; i++) {
                    player.getInventory().addItem(generatorItem(tiers.isEmpty() ? null : tiers.firstEntry().getValue()));
                }
                data.fresh = false;
            }
        }
        player.setFoodLevel(20);
        if (data != null) {
            quests.roll(data, LocalDate.now().toString());
            updateStats(player, data);
        }
        core.messages().send(player, "gens.welcome");
    }

    @Override
    public void leave(Player player, boolean quitting) {
        core.inventories().save(ID, player);
        if (!quitting) {
            player.getInventory().clear();
        }
    }

    private void updateStats(Player player, GensData.Player data) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.setStat("gens.generators", data.generators.size());
            profile.setStat("gens.level", data.level);
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
        GensData.Level level = level(data);
        values.put("%generators%", String.valueOf(data.generators.size()));
        values.put("%slots%", String.valueOf(level.slots));
        values.put("%plot_level%", String.valueOf(data.level));
        values.put("%multiplier%", String.format(Locale.ROOT, "%.2fx", level.multiplier));
        values.put("%production%", Text.number(productionPerMinute(data)));
        values.put("%plot%", "#" + data.plot);
        values.put("%earned%", Text.number(profile == null ? 0 : profile.stat("gens.earned")));
    }

    public long productionPerMinute(GensData.Player data) {
        double multiplier = level(data).multiplier;
        long total = 0;
        for (GensData.Generator generator : data.generators) {
            GensData.Tier tier = tiers.get(generator.tier);
            if (tier != null) {
                total += Math.round(tier.value * tier.amount * multiplier * 60.0 / tier.interval);
            }
        }
        return total;
    }

    public ItemStack generatorItem(GensData.Tier tier) {
        if (tier == null) {
            return Items.of(Material.STONE).build();
        }
        List<String> lore = core.messages().list("gens.item.lore", "%drop%", tier.drop.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                "%value%", core.economy().format(currency, tier.value), "%interval%", String.valueOf(tier.interval), "%capacity%", String.valueOf(tier.capacity));
        return Items.of(tier.block).name(core.messages().raw("gens.item.name").replace("%tier%", tier.name)).lore(lore).glow().tag("gens:generator").value(tier.level).build();
    }

    public void tick() {
        if (generators.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (GensData.Generator generator : generators.values()) {
            if (now < generator.next) {
                continue;
            }
            GensData.Tier tier = tiers.get(generator.tier);
            GensData.Player data = players.get(generator.owner);
            if (tier == null || data == null) {
                continue;
            }
            generator.next = now + tier.interval * 1000L;
            if (!world.isChunkLoaded(generator.x >> 4, generator.z >> 4)) {
                continue;
            }
            long before = generator.stored;
            generator.stored = GenMath.produce(generator.stored, tier.capacity, tier.amount, level(data).multiplier);
            if (generator.stored != before) {
                generator.dirty = true;
                Player owner = Bukkit.getPlayer(generator.owner);
                if (owner != null && owner.getWorld() == world && owner.getLocation().distanceSquared(new Location(world, generator.x, generator.y, generator.z)) < 576) {
                    world.spawnParticle(Particle.END_ROD, generator.x + 0.5, generator.y + 1.2, generator.z + 0.5, 2, 0.15, 0.1, 0.15, 0.01);
                }
            }
        }
    }

    private boolean member(GensData.Player owner, Player player) {
        return owner.uuid.equals(player.getUniqueId()) || owner.trusted.contains(player.getUniqueId());
    }

    private boolean admin(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    private boolean canBuild(Player player, Location location) {
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
        return owner != null && owner.trusted.contains(player.getUniqueId());
    }

    private void deny(Player player) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            warned.put(player.getUniqueId(), now);
            core.messages().actionBar(player, "gens.not-your-plot");
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (!canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            deny(player);
            return;
        }
        ItemStack hand = event.getItemInHand();
        if (!"gens:generator".equals(Items.tagOf(hand))) {
            return;
        }
        GensData.Player data = players.get(player.getUniqueId());
        GensData.Tier tier = tiers.get(Items.valueOf(hand));
        if (data == null || tier == null || data.plot != plotAt(block.getLocation())) {
            event.setCancelled(true);
            core.messages().send(player, "gens.only-own-plot");
            return;
        }
        GensData.Level level = level(data);
        if (data.generators.size() >= level.slots) {
            event.setCancelled(true);
            core.messages().send(player, "gens.limit", "%slots%", String.valueOf(level.slots));
            return;
        }
        GensData.Generator generator = new GensData.Generator(player.getUniqueId(), block.getX(), block.getY(), block.getZ(), tier.level, 0);
        generator.next = System.currentTimeMillis() + tier.interval * 1000L;
        generator.dirty = true;
        data.generators.add(generator);
        generators.put(generator.key(), generator);
        updateStats(player, data);
        save(data, false);
        core.messages().send(player, "gens.placed", "%tier%", tier.name, "%count%", String.valueOf(data.generators.size()), "%slots%", String.valueOf(level.slots));
        progress(player, "place_gen", 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        GensData.Generator generator = generators.get(GensData.key(block.getX(), block.getY(), block.getZ()));
        if (generator != null) {
            event.setCancelled(true);
            if (!generator.owner.equals(player.getUniqueId()) && !admin(player)) {
                deny(player);
                return;
            }
            pickUp(player, generator);
            return;
        }
        if (!canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    private void pickUp(Player player, GensData.Generator generator) {
        GensData.Player data = players.get(generator.owner);
        GensData.Tier tier = tiers.get(generator.tier);
        world.getBlockAt(generator.x, generator.y, generator.z).setType(Material.AIR, false);
        generators.remove(generator.key());
        if (data != null) {
            data.generators.remove(generator);
            updateStats(player, data);
        }
        deleteGenerator(generator);
        if (tier != null) {
            give(player, generatorItem(tier));
            collect(player, generator, tier);
        }
        core.messages().send(player, "gens.picked-up");
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            world.dropItemNaturally(player.getLocation(), left);
        }
    }

    private long collect(Player player, GensData.Generator generator, GensData.Tier tier) {
        long amount = generator.stored;
        if (amount <= 0) {
            return 0;
        }
        long given = 0;
        while (amount > 0) {
            int stack = (int) Math.min(amount, tier.drop.getMaxStackSize());
            Map<Integer, ItemStack> left = player.getInventory().addItem(new ItemStack(tier.drop, stack));
            int notGiven = 0;
            for (ItemStack item : left.values()) {
                notGiven += item.getAmount();
            }
            given += stack - notGiven;
            amount -= stack - notGiven;
            if (notGiven > 0) {
                break;
            }
        }
        generator.stored = amount;
        generator.dirty = true;
        if (given > 0) {
            progress(player, "collect_items", given);
        }
        return given;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() != world) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            if (generators.containsKey(GensData.key(block.getX(), block.getY(), block.getZ()))) {
                event.setCancelled(true);
            }
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            if (!canBuild(player, block.getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        GensData.Generator generator = generators.get(GensData.key(block.getX(), block.getY(), block.getZ()));
        if (generator != null) {
            event.setCancelled(true);
            if (!generator.owner.equals(player.getUniqueId())) {
                deny(player);
                return;
            }
            GensData.Tier tier = tiers.get(generator.tier);
            if (tier == null) {
                return;
            }
            if (player.isSneaking()) {
                upgrade(player, generator);
            } else {
                long given = collect(player, generator, tier);
                if (given > 0) {
                    core.messages().actionBar(player, "gens.collected", "%amount%", String.valueOf(given), "%stored%", String.valueOf(generator.stored),
                            "%capacity%", String.valueOf(tier.capacity));
                } else {
                    core.messages().actionBar(player, "gens.info", "%tier%", tier.name, "%stored%", String.valueOf(generator.stored),
                            "%capacity%", String.valueOf(tier.capacity));
                }
            }
            return;
        }
        if (!canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            deny(player);
        }
    }

    public void upgrade(Player player, GensData.Generator generator) {
        GensData.Tier tier = tiers.get(generator.tier);
        Map.Entry<Integer, GensData.Tier> next = tiers.higherEntry(generator.tier);
        if (tier == null || next == null || tier.upgrade <= 0) {
            core.messages().send(player, "gens.max-tier");
            return;
        }
        Ledger.Result result = core.economy().withdraw(player, currency, tier.upgrade, "gens:upgrade");
        if (result != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, tier.upgrade));
            return;
        }
        generator.tier = next.getKey();
        generator.dirty = true;
        world.getBlockAt(generator.x, generator.y, generator.z).setType(next.getValue().block, false);
        world.spawnParticle(Particle.FIREWORK, generator.x + 0.5, generator.y + 1.0, generator.z + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.resonate", 0.8f, 1.2f);
        core.messages().send(player, "gens.upgraded", "%tier%", next.getValue().name, "%price%", core.economy().format(currency, tier.upgrade));
        progress(player, "upgrade", 1);
    }

    public long sellAll(Player player) {
        if (player.getWorld() != world) {
            core.messages().send(player, "gens.only-in-gens");
            return 0;
        }
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
            total += GenMath.sellValue(item.getAmount(), price);
            count += item.getAmount();
            player.getInventory().setItem(i, null);
        }
        if (total <= 0) {
            core.messages().send(player, "gens.nothing-to-sell");
            return 0;
        }
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
        progress(player, "sell_items", count);
        progress(player, "earn", total);
        return total;
    }

    public void buyGenerator(Player player) {
        if (tiers.isEmpty()) {
            return;
        }
        Ledger.Result result = core.economy().withdraw(player, currency, generatorPrice, "gens:buy");
        if (result != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, generatorPrice));
            return;
        }
        give(player, generatorItem(tiers.firstEntry().getValue()));
        core.messages().send(player, "gens.bought-generator", "%price%", core.economy().format(currency, generatorPrice));
    }

    public void upgradePlot(Player player) {
        GensData.Player data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        GensData.Level next = nextLevel(data);
        if (next == null) {
            core.messages().send(player, "gens.max-level");
            return;
        }
        Ledger.Result result = core.economy().withdraw(player, currency, next.price, "gens:level");
        if (result != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(currency, next.price));
            return;
        }
        data.level = next.level;
        data.dirty = true;
        updateStats(player, data);
        save(data, false);
        core.messages().send(player, "gens.level-up", "%level%", String.valueOf(next.level), "%slots%", String.valueOf(next.slots),
                "%multiplier%", String.format(Locale.ROOT, "%.2fx", next.multiplier));
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
        core.guard().allowNext(player);
        player.teleport(home(data));
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
            core.messages().send(player, "gens.trusted", "%player%", target.getName());
            core.messages().send(target, "gens.trusted-you", "%player%", player.getName());
        } else {
            data.trusted.remove(target.getUniqueId());
            core.messages().send(player, "gens.untrusted", "%player%", target.getName());
        }
        data.dirty = true;
        save(data, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().getWorld() == world && !canBuild(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()).getLocation())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getWorld() == world && plotAt(event.getToBlock().getLocation()) != plotAt(event.getBlock().getLocation())) {
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
    }

    public Location hub() {
        return hub;
    }

    public long dailyAmount() {
        return dailyAmount;
    }

    public long generatorPrice() {
        return generatorPrice;
    }
}
