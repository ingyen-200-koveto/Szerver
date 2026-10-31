package hu.icemine.core.skypvp;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.config.Values;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.kitpvp.PvpMode;
import hu.icemine.core.util.Locs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class SkyPvpMode extends PvpMode {

    private static final class Loot {
        final ItemStack item;
        final int weight;

        Loot(ItemStack item, int weight) {
            this.item = item;
            this.weight = weight;
        }
    }

    private final Random random = new Random();
    private final List<Location> normalChests = new ArrayList<>();
    private final List<Location> midChests = new ArrayList<>();
    private final List<Loot> normalLoot = new ArrayList<>();
    private final List<Loot> midLoot = new ArrayList<>();
    private final Map<Long, Long> placed = new HashMap<>();
    private final Set<Material> placeable = new HashSet<>();
    private Location spawn;
    private Location safeCenter;
    private double safeRadius = 9;
    private int refillSeconds = 180;
    private int minItems = 3;
    private int maxItems = 6;
    private long decay = 45000L;
    private int maxBuildY = 200;
    private long nextRefill;

    public SkyPvpMode(IceMineCore core) {
        super(core, "skypvp");
    }

    public void load(YamlConfiguration config, List<String> problems) {
        String name = config.getString("world", "ice_skypvp");
        if (world == null) {
            world = createWorld(name);
            if (world == null) {
                problems.add("skypvp.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        prepareWorld(world, config.getLong("time", 6000));
        if (!config.getBoolean("built", false)) {
            build(config);
        }
        loadCommon(config, "skypvp.yml", problems);
        Values values = new Values("skypvp.yml", config::get);
        spawn = Locs.read(config.getConfigurationSection("spawn"), world);
        safeCenter = Locs.read(config.getConfigurationSection("safe-center"), world);
        safeRadius = values.decimal("safe-radius", 9, 3, 40);
        if (spawn == null) {
            problems.add("skypvp.yml -> spawn: nincs beállítva (/icemine setspawn skypvp)");
            spawn = world.getSpawnLocation();
        }
        if (safeCenter == null) {
            safeCenter = spawn.clone();
        }
        refillSeconds = values.integer("loot.refill-seconds", 180, 30, 3600);
        minItems = values.integer("loot.min-items", 3, 1, 27);
        maxItems = Math.max(minItems, values.integer("loot.max-items", 6, 1, 27));
        decay = values.integer("blocks.decay-seconds", 45, 5, 3600) * 1000L;
        maxBuildY = values.integer("blocks.max-y", 200, -64, 320);
        problems.addAll(values.problems());
        placeable.clear();
        for (String material : config.getStringList("blocks.allowed")) {
            Material type = Material.matchMaterial(material);
            if (type == null || !type.isBlock()) {
                problems.add("skypvp.yml -> blocks.allowed: ismeretlen blokk (" + material + ")");
            } else {
                placeable.add(type);
            }
        }
        normalChests.clear();
        midChests.clear();
        readChests(config, "chests.normal", normalChests);
        readChests(config, "chests.mid", midChests);
        readLoot(config, "loot.normal", normalLoot, problems);
        readLoot(config, "loot.mid", midLoot, problems);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        refill(false);
    }

    private void readChests(YamlConfiguration config, String path, List<Location> target) {
        for (String entry : config.getStringList(path)) {
            String[] parts = entry.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                target.add(new Location(world, Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void readLoot(YamlConfiguration config, String path, List<Loot> target, List<String> problems) {
        target.clear();
        for (String entry : config.getStringList(path)) {
            String[] parts = entry.split("\\|");
            ItemStack item = Kit.parse(null, parts[0].trim(), "skypvp.yml -> " + path, problems);
            if (item == null) {
                continue;
            }
            int weight = 10;
            if (parts.length > 1) {
                try {
                    weight = Math.max(1, Math.min(1000, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException exception) {
                    problems.add("skypvp.yml -> " + path + ": hibás súly (" + parts[1].trim() + ")");
                }
            }
            target.add(new Loot(item, weight));
        }
    }

    private void build(YamlConfiguration config) {
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 100);
        int z = config.getInt("center.z", 0);
        core.getLogger().info("Építem a SkyPvP szigeteket...");
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(x - SkyBuild.CLEAR, y - 40, z - SkyBuild.CLEAR, x + SkyBuild.CLEAR, y + 60, z + SkyBuild.CLEAR);
        SkyBuild build = new SkyBuild(placer, x, y, z);
        build.build();
        Layout layout = build.layout();
        Layout.Point point = layout.points.get("spawn");
        if (point != null) {
            Locs.write(config, "spawn", x + point.x, y + point.y, z + point.z, point.yaw);
        }
        Layout.Point safe = layout.points.get("safe");
        if (safe != null) {
            Locs.write(config, "safe-center", x + safe.x, y + safe.y, z + safe.z, 0);
            config.set("safe-radius", safe.yaw);
        }
        List<String> normal = new ArrayList<>();
        for (Layout.Point chest : layout.lists.getOrDefault("chests:normal", new ArrayList<>())) {
            normal.add((int) (x + chest.x) + "," + (int) (y + chest.y) + "," + (int) (z + chest.z));
        }
        List<String> mid = new ArrayList<>();
        for (Layout.Point chest : layout.lists.getOrDefault("chests:mid", new ArrayList<>())) {
            mid.add((int) (x + chest.x) + "," + (int) (y + chest.y) + "," + (int) (z + chest.z));
        }
        config.set("chests.normal", normal);
        config.set("chests.mid", mid);
        config.set("void-y", y - 30);
        config.set("blocks.max-y", y + 30);
        config.set("built", true);
        core.configs().save("skypvp");
        core.getLogger().info("A SkyPvP szigetek elkészültek (" + placer.placed() + " blokk).");
    }

    public void rebuild() {
        YamlConfiguration config = core.configs().get("skypvp");
        config.set("built", false);
        for (Player player : world.getPlayers()) {
            core.modes().send(player, "lobby", true);
        }
        clearPlaced();
        build(config);
        load(config, new ArrayList<>());
    }

    public void reset() {
        clearPlaced();
        refill(true);
    }

    @Override
    public Location spawn(Player player) {
        return spawn == null ? null : spawn.clone();
    }

    @Override
    protected Location respawn(Player player) {
        return spawn(player);
    }

    @Override
    protected boolean inSafe(Player player, Location location) {
        if (location.getWorld() != world || safeCenter == null) {
            return false;
        }
        double dx = location.getX() - safeCenter.getX();
        double dz = location.getZ() - safeCenter.getZ();
        double dy = location.getY() - safeCenter.getY();
        return dx * dx + dz * dz <= safeRadius * safeRadius && dy > -6 && dy < 14;
    }

    @Override
    protected void prepare(Player player) {
        resetState(player, GameMode.SURVIVAL);
        giveSafeHotbar(player);
    }

    @Override
    public void enter(Player player) {
        prepare(player);
        core.messages().send(player, "skypvp.welcome");
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        super.placeholders(player, values);
        long left = Math.max(0, (nextRefill - System.currentTimeMillis()) / 1000);
        values.put("%refill%", String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60));
    }

    private ItemStack pick(List<Loot> table) {
        int total = 0;
        for (Loot loot : table) {
            total += loot.weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Loot loot : table) {
            roll -= loot.weight;
            if (roll < 0) {
                return loot.item.clone();
            }
        }
        return null;
    }

    public void refill(boolean announce) {
        nextRefill = System.currentTimeMillis() + refillSeconds * 1000L;
        fill(normalChests, normalLoot);
        fill(midChests, midLoot.isEmpty() ? normalLoot : midLoot);
        if (announce && world != null) {
            String text = core.messages().prefixed("skypvp.refilled");
            for (Player player : world.getPlayers()) {
                player.sendMessage(text);
            }
        }
    }

    private void fill(List<Location> chests, List<Loot> table) {
        for (Location location : chests) {
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != Material.CHEST) {
                block.setType(Material.CHEST, false);
            }
            BlockState state = block.getState();
            if (!(state instanceof Chest)) {
                continue;
            }
            Inventory inventory = ((Chest) state).getBlockInventory();
            inventory.clear();
            int count = minItems + random.nextInt(maxItems - minItems + 1);
            for (int i = 0; i < count; i++) {
                ItemStack item = pick(table);
                if (item != null) {
                    inventory.setItem(random.nextInt(inventory.getSize()), item);
                }
            }
        }
    }

    public void tick() {
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextRefill) {
            refill(!world.getPlayers().isEmpty());
        }
        if (placed.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, Long>> iterator = placed.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                Block block = blockAt(entry.getKey());
                if (block != null) {
                    block.setType(Material.AIR, false);
                }
                iterator.remove();
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private Block blockAt(long key) {
        int x = (int) (key >> 38);
        int z = (int) ((key >> 12) & 0x3FFFFFF);
        int y = (int) (key & 0xFFF);
        if (x >= 1 << 25) {
            x -= 1 << 26;
        }
        if (z >= 1 << 25) {
            z -= 1 << 26;
        }
        if (y >= 1 << 11) {
            y -= 1 << 12;
        }
        return world == null ? null : world.getBlockAt(x, y, z);
    }

    private void clearPlaced() {
        for (Long key : placed.keySet()) {
            Block block = blockAt(key);
            if (block != null) {
                block.setType(Material.AIR, false);
            }
        }
        placed.clear();
    }

    @Override
    public void shutdown() {
        clearPlaced();
    }

    private boolean admin(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (admin(player)) {
            return;
        }
        if (!placeable.contains(block.getType()) || inSafe(player, block.getLocation().add(0.5, 0, 0.5)) || block.getY() > maxBuildY) {
            event.setCancelled(true);
            return;
        }
        placed.put(key(block.getX(), block.getY(), block.getZ()), System.currentTimeMillis() + decay);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld() != world || admin(event.getPlayer())) {
            return;
        }
        Long placedAt = placed.remove(key(block.getX(), block.getY(), block.getZ()));
        if (placedAt == null) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (admin(player)) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (type == Material.CHEST) {
            if (equipped(player) == null || inSafe(player, player.getLocation())) {
                event.setCancelled(true);
                core.messages().actionBar(player, "skypvp.chest-locked");
            }
            return;
        }
        if (type.isInteractable() && !placeable.contains(type)) {
            event.setCancelled(true);
        }
    }
}
