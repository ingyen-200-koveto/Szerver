package hu.icemine.kingdoms;

import hu.icemine.core.config.Upgrades;
import hu.icemine.core.module.Module;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
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

public final class IceMineKingdoms extends Module {

    private static final String OBJECTIVE = "kingdoms";
    private static final String SUFFIX = "0123456789abcdef";

    private final Map<String, Kingdom> kingdoms = new ConcurrentHashMap<>();
    private final Map<String, Kingdom> claims = new ConcurrentHashMap<>();
    private final Map<UUID, Kingdom> members = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, String> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> kingdomChat = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastArea = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> shown = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private World world;
    private Location spawn;
    private int townX;
    private int townZ;
    private InventoryStore store;
    private File dataFile;
    private boolean dirty;
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
        if (String.join("\n", getConfig().getStringList("tablist.header")).contains("❄ IceMine ❄") && getConfig().getDefaults() != null) {
            getConfig().set("tablist.header", getConfig().getDefaults().getStringList("tablist.header"));
            saveConfig();
        }
        store = new InventoryStore(new File(getDataFolder(), "inventories"), getLogger());
        dataFile = new File(getDataFolder(), "kingdoms.yml");
        world = loadWorld();
        if (world == null) {
            getLogger().severe("Nem sikerült betölteni a Kingdoms világot, a Kingdoms mód nem indul el.");
            return;
        }
        prepareWorld();
        loadData();
        KingdomCommands commands = new KingdomCommands(this);
        for (String name : new String[]{"k", "kadmin"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
        }
        getServer().getPluginManager().registerEvents(new KingdomListener(this), plugin());
        startTasks();
        for (Player player : world.getPlayers()) {
            enter(player);
        }
        enabled = true;
        getLogger().info("Kingdoms elindult, " + kingdoms.size() + " királyság betöltve.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                store.save(player);
            }
        }
        saveData();
    }

    private World loadWorld() {
        String name = getConfig().getString("world", "kingdoms");
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }
        return new WorldCreator(name).createWorld();
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
        switch (name) {
            case "sand":
            case "red_sand":
            case "podzol":
            case "mycelium":
            case "coarse_dirt":
            case "snow_block":
            case "moss_block":
            case "stone":
            case "terracotta":
            case "grass_block":
                return name;
            default:
                return "grass_block";
        }
    }

    boolean insideBorder(int cx, int cz) {
        double half = getConfig().getInt("border-size", 6000) / 2.0 - 16;
        return Math.abs(cx * 16 + 8 - (townX + 0.5)) < half && Math.abs(cz * 16 + 8 - (townZ + 0.5)) < half;
    }

    void teleport(Player player, Location target) {
        int delay = getConfig().getInt("teleport-delay", 3);
        if (delay <= 0 || admin(player)) {
            player.teleport(target);
            return;
        }
        player.sendMessage(message("teleporting").replace("%seconds%", String.valueOf(delay)));
        Location start = player.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            if (!player.isOnline() || player.getWorld() != world) {
                return;
            }
            if (player.getLocation().distanceSquared(start) > 1.0) {
                player.sendMessage(message("teleport-cancelled"));
                return;
            }
            if (player.teleport(target)) {
                player.sendMessage(message("teleported"));
            }
        }, delay * 20L);
    }

    void reload() {
        reloadConfig();
        prepareWorld();
        startTasks();
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

    private void loadData() {
        kingdoms.clear();
        claims.clear();
        members.clear();
        names.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = yaml.getConfigurationSection("names");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    names.put(UUID.fromString(key), players.getString(key, "?"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("kingdoms");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                Kingdom kingdom = new Kingdom(entry.getString("name", key), UUID.fromString(entry.getString("leader", "")), entry.getLong("created", 0L));
                ConfigurationSection list = entry.getConfigurationSection("members");
                if (list != null) {
                    for (String id : list.getKeys(false)) {
                        try {
                            Kingdom.Role role = Kingdom.Role.valueOf(list.getString(id, "MEMBER"));
                            UUID uuid = UUID.fromString(id);
                            kingdom.members.put(uuid, role);
                            members.put(uuid, kingdom);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                for (String chunk : entry.getStringList("claims")) {
                    kingdom.claims.add(chunk);
                    claims.put(chunk, kingdom);
                }
                if (entry.contains("home.x")) {
                    kingdom.home = new double[]{entry.getDouble("home.x", 0), entry.getDouble("home.y", 0), entry.getDouble("home.z", 0), entry.getDouble("home.yaw", 0)};
                }
                kingdoms.put(kingdom.name.toLowerCase(Locale.ROOT), kingdom);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Hibás királyság a mentésben: " + key);
            }
        }
    }

    void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            yaml.set("names." + entry.getKey(), entry.getValue());
        }
        for (Kingdom kingdom : kingdoms.values()) {
            String base = "kingdoms." + kingdom.name.toLowerCase(Locale.ROOT);
            yaml.set(base + ".name", kingdom.name);
            yaml.set(base + ".leader", kingdom.leader.toString());
            yaml.set(base + ".created", kingdom.created);
            for (Map.Entry<UUID, Kingdom.Role> member : kingdom.members.entrySet()) {
                yaml.set(base + ".members." + member.getKey(), member.getValue().name());
            }
            yaml.set(base + ".claims", new ArrayList<>(kingdom.claims));
            if (kingdom.home != null) {
                yaml.set(base + ".home.x", kingdom.home[0]);
                yaml.set(base + ".home.y", kingdom.home[1]);
                yaml.set(base + ".home.z", kingdom.home[2]);
                yaml.set(base + ".home.yaw", kingdom.home[3]);
            }
        }
        try {
            yaml.save(dataFile);
            dirty = false;
        } catch (IOException exception) {
            getLogger().warning("Nem sikerült menteni a királyságokat: " + exception.getMessage());
        }
    }

    void markDirty() {
        dirty = true;
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
        }, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin(), () -> {
            if (dirty) {
                saveData();
            }
            for (Player player : world.getPlayers()) {
                store.save(player);
            }
        }, 6000L, 6000L));
    }

    World world() {
        return world;
    }

    Location spawn() {
        return spawn;
    }

    boolean inKingdoms(Entity entity) {
        return entity.getWorld() == world;
    }

    void remember(Player player) {
        names.put(player.getUniqueId(), player.getName());
    }

    String prefix(Player player) {
        for (MetadataValue value : player.getMetadata("icemine_prefix")) {
            if (value.getOwningPlugin() != null && value.getOwningPlugin() == plugin()) {
                return value.asString();
            }
        }
        return "";
    }

    String nameOf(UUID id) {
        String name = names.get(id);
        return name == null ? "?" : name;
    }

    UUID idOf(String name) {
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    Kingdom kingdomOf(UUID id) {
        return members.get(id);
    }

    Kingdom kingdom(String name) {
        return kingdoms.get(name.toLowerCase(Locale.ROOT));
    }

    List<Kingdom> all() {
        return new ArrayList<>(kingdoms.values());
    }

    Kingdom owner(Location location) {
        return claims.get(Kingdom.chunk(location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    Kingdom ownerOf(int cx, int cz) {
        return claims.get(Kingdom.chunk(cx, cz));
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

    boolean admin(Player player) {
        return player.hasPermission("icemine.kingdoms.admin") && player.getGameMode() == GameMode.CREATIVE;
    }

    boolean canBuild(Player player, Location location) {
        if (admin(player)) {
            return true;
        }
        if (inSpawn(location)) {
            return false;
        }
        Kingdom owner = owner(location);
        return owner == null || owner.members.containsKey(player.getUniqueId());
    }

    int maxClaims(Kingdom kingdom) {
        return getConfig().getInt("claims.base", 6) + getConfig().getInt("claims.per-member", 4) * kingdom.members.size();
    }

    Kingdom create(Player player, String name) {
        Kingdom kingdom = new Kingdom(name, player.getUniqueId(), System.currentTimeMillis());
        kingdom.members.put(player.getUniqueId(), Kingdom.Role.KING);
        kingdoms.put(name.toLowerCase(Locale.ROOT), kingdom);
        members.put(player.getUniqueId(), kingdom);
        dirty = true;
        return kingdom;
    }

    void addMember(Kingdom kingdom, UUID id) {
        kingdom.members.put(id, Kingdom.Role.MEMBER);
        members.put(id, kingdom);
        invites.remove(id);
        dirty = true;
    }

    void removeMember(Kingdom kingdom, UUID id) {
        kingdom.members.remove(id);
        members.remove(id);
        kingdomChat.remove(id);
        dirty = true;
    }

    void disband(Kingdom kingdom) {
        for (String chunk : kingdom.claims) {
            claims.remove(chunk);
        }
        for (UUID id : kingdom.members.keySet()) {
            members.remove(id);
            kingdomChat.remove(id);
        }
        kingdoms.remove(kingdom.name.toLowerCase(Locale.ROOT));
        dirty = true;
    }

    void claim(Kingdom kingdom, int cx, int cz) {
        String key = Kingdom.chunk(cx, cz);
        kingdom.claims.add(key);
        claims.put(key, kingdom);
        dirty = true;
    }

    void unclaim(Kingdom kingdom, int cx, int cz) {
        String key = Kingdom.chunk(cx, cz);
        kingdom.claims.remove(key);
        claims.remove(key);
        dirty = true;
    }

    boolean adjacent(Kingdom kingdom, int cx, int cz) {
        return kingdom.claims.contains(Kingdom.chunk(cx + 1, cz)) || kingdom.claims.contains(Kingdom.chunk(cx - 1, cz))
                || kingdom.claims.contains(Kingdom.chunk(cx, cz + 1)) || kingdom.claims.contains(Kingdom.chunk(cx, cz - 1));
    }

    void invite(UUID id, Kingdom kingdom) {
        invites.put(id, kingdom.name.toLowerCase(Locale.ROOT));
    }

    String invitation(UUID id) {
        return invites.get(id);
    }

    boolean toggleChat(UUID id) {
        boolean now = !kingdomChat.getOrDefault(id, false);
        kingdomChat.put(id, now);
        return now;
    }

    boolean chatMode(UUID id) {
        return kingdomChat.getOrDefault(id, false);
    }

    Location home(Kingdom kingdom) {
        if (kingdom.home == null) {
            return null;
        }
        return new Location(world, kingdom.home[0], kingdom.home[1], kingdom.home[2], (float) kingdom.home[3], 0f);
    }

    void enter(Player player) {
        remember(player);
        if (!player.hasPermission("icemine.kingdoms.bypass")) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFlying(false);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE);
        Location saved = store.location(player.getUniqueId(), world);
        boolean known = store.restore(player);
        if (!known) {
            player.getInventory().clear();
            try {
                player.setHealth(20.0);
            } catch (IllegalArgumentException ignored) {
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
        createSidebar(player);
        updateTab(player);
        player.sendTitle(Text.color(getConfig().getString("welcome.title", "&6&lKingdoms")), Text.color(getConfig().getString("welcome.subtitle", "")), 10, 40, 15);
    }

    void leave(Player player) {
        store.save(player);
        boards.remove(player.getUniqueId());
        shown.remove(player.getUniqueId());
        lastArea.remove(player.getUniqueId());
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

    String area(Location location) {
        if (inSpawn(location)) {
            return Text.color(getConfig().getString("areas.spawn", "&bSpawn"));
        }
        Kingdom owner = owner(location);
        if (owner == null) {
            return Text.color(getConfig().getString("areas.wild", "&2Vadon"));
        }
        return Text.color(getConfig().getString("areas.kingdom", "&6%kingdom% &7földje").replace("%kingdom%", owner.name));
    }

    void checkArea(Player player, Location to) {
        String now = area(to);
        String before = lastArea.put(player.getUniqueId(), now);
        if (before != null && !before.equals(now)) {
            player.sendActionBar(now);
        }
    }

    String message(String key) {
        String text = getConfig().getString("messages." + key);
        return Text.color(prefix() + (text == null ? key : text));
    }

    /** A közös IceMine prefix (messages_hu.yml), hogy a Kingdoms üzenetei is ugyanúgy nézzenek ki, mint a többi. */
    String prefix() {
        if (plugin() instanceof hu.icemine.core.IceMineCore core && core.messages() != null) {
            return core.messages().prefix();
        }
        return hu.icemine.core.config.Messages.DEFAULT_PREFIX;
    }

    private String apply(String text, Player player) {
        Kingdom kingdom = kingdomOf(player.getUniqueId());
        int online = 0;
        if (kingdom != null) {
            for (UUID id : kingdom.members.keySet()) {
                if (Bukkit.getPlayer(id) != null) {
                    online++;
                }
            }
        }
        Kingdom.Role role = kingdom == null ? null : kingdom.role(player.getUniqueId());
        return Text.color(text
                .replace("%player%", player.getName())
                .replace("%kingdom%", kingdom == null ? getConfig().getString("no-kingdom", "&8nincs") : "&6" + kingdom.name)
                .replace("%role%", role == null ? "-" : role.title)
                .replace("%members%", kingdom == null ? "0" : String.valueOf(kingdom.members.size()))
                .replace("%kmonline%", String.valueOf(online))
                .replace("%claims%", kingdom == null ? "0" : String.valueOf(kingdom.claims.size()))
                .replace("%maxclaims%", kingdom == null ? "0" : String.valueOf(maxClaims(kingdom)))
                .replace("%here%", area(player.getLocation()))
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
        }
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        shown.put(player.getUniqueId(), new ArrayList<>());
        updateSidebar(player);
    }

    private void updateSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        List<String> raw = getConfig().getStringList("sidebar.lines");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < raw.size() && i < SUFFIX.length(); i++) {
            lines.add(apply(raw.get(i), player) + "§" + SUFFIX.charAt(i));
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
