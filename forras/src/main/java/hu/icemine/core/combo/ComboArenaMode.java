package hu.icemine.core.combo;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.build.Title;
import hu.icemine.core.kitpvp.PvpMode;
import hu.icemine.core.util.Locs;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ComboArenaMode extends PvpMode {

    public static final String ID = "combofly";
    public static final String FILE = "comboarena";

    private Location spawn;
    private Location safeCenter;
    private double safeRadius = 7.5;
    private boolean fallDamage = true;
    private int pearlCooldown = 15;
    private boolean oldCombat = true;
    private static Object attackSpeedType;
    private final List<double[]> safeAreas = new ArrayList<>();
    private org.bukkit.scheduler.BukkitTask guardTask;
    private int guardTicks;
    private final ComboSpawn extras = new ComboSpawn(core, this);

    public ComboArenaMode(IceMineCore core) {
        super(core, ID);
    }

    public void load(YamlConfiguration config, List<String> problems) {
        customMap(config);
        String name = config.getString("world", "ice_combofly");
        if (world != null && !world.getName().equals(name)) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                core.modes().send(player, "lobby", true);
            }
            world = null;
        }
        if (world == null) {
            world = createWorld(name);
            if (world == null) {
                problems.add("comboarena.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        prepareWorld(world, config.getLong("time", 6000));
        if (!config.getBoolean("built", false) && !config.getBoolean("custom", false)) {
            build(config);
        }
        if (config.getBoolean("title.enabled", false) && config.getBoolean("title.built", false) && config.getInt("title.style", 1) < 2) {
            clearTitle(config);
            config.set("title.built", false);
        }
        if (config.getBoolean("title.enabled", false) && !config.getBoolean("title.built", false)) {
            config.set("title.style", 2);
            placeTitle(config, problems);
        }
        loadCommon(config, "comboarena.yml", problems);
        spawn = Locs.read(config.getConfigurationSection("spawn"), world);
        if (spawn == null) {
            problems.add("comboarena.yml -> spawn: hiányzik, a világ spawnját használom");
            spawn = world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        safeCenter = Locs.read(config.getConfigurationSection("safe-center"), world);
        if (safeCenter == null) {
            safeCenter = spawn.clone();
        }
        safeRadius = Math.max(3.0, Math.min(30.0, config.getDouble("safe-radius", 7.5)));
        loadSafeAreas(config);
        fallDamage = config.getBoolean("fall-damage", true);
        pearlCooldown = Math.max(0, Math.min(120, config.getInt("pearl-cooldown", 15)));
        oldCombat = config.getBoolean("old-combat", true);
        upgradeKits(config);
        for (Player player : world.getPlayers()) {
            combatStyle(player, true);
        }
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        extras.load(config, world);
        if (guardTask != null) {
            guardTask.cancel();
        }
        guardTask = Bukkit.getScheduler().runTaskTimer(core, this::guardSafeZones, 20L, 4L);
    }

    private void loadSafeAreas(YamlConfiguration config) {
        if (config.getBoolean("custom", false) && !config.isSet("safe-areas") && config.getDefaults() != null
                && config.getDefaults().isConfigurationSection("custom-map.safe-areas")) {
            org.bukkit.configuration.ConfigurationSection fresh = config.getDefaults().getConfigurationSection("custom-map.safe-areas");
            for (String key : fresh.getKeys(true)) {
                Object value = fresh.get(key);
                if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                    config.set("safe-areas." + key, value);
                }
            }
            core.configs().save(FILE);
        }
        safeAreas.clear();
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("safe-areas");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection area = section.getConfigurationSection(id);
            if (area == null || !area.getBoolean("enabled", true)) {
                continue;
            }
            safeAreas.add(new double[]{area.getDouble("x"), area.getDouble("y"), area.getDouble("z"),
                    Math.max(1.0, Math.min(30.0, area.getDouble("radius", 5))), Math.max(2.0, Math.min(64.0, area.getDouble("height", 10)))});
        }
    }

    static boolean insideArea(double[] area, double x, double y, double z) {
        double dx = x - area[0];
        double dz = z - area[2];
        return dx * dx + dz * dz <= area[3] * area[3] && y >= area[1] - 1 && y < area[1] + area[4];
    }

    private double[] areaAt(Location location) {
        for (double[] area : safeAreas) {
            if (insideArea(area, location.getX(), location.getY(), location.getZ())) {
                return area;
            }
        }
        return null;
    }

    private void guardSafeZones() {
        if (world == null) {
            return;
        }
        guardTicks += 4;
        boolean draw = guardTicks % 12 == 0;
        for (Player player : world.getPlayers()) {
            if (player.hasMetadata("NPC") || !core.combat().tagged(player) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation();
            if (inSafe(player, at)) {
                double[] area = areaAt(at);
                double cx = area == null ? safeCenter.getX() : area[0];
                double cz = area == null ? safeCenter.getZ() : area[2];
                org.bukkit.util.Vector out = new org.bukkit.util.Vector(at.getX() - cx, 0, at.getZ() - cz);
                if (out.lengthSquared() < 1.0E-4) {
                    out = at.getDirection().setY(0).multiply(-1);
                }
                if (out.lengthSquared() < 1.0E-4) {
                    out = new org.bukkit.util.Vector(1, 0, 0);
                }
                player.setVelocity(out.normalize().multiply(0.9).setY(0.35));
                core.messages().actionBar(player, "pvp.no-safe-in-combat");
                continue;
            }
            if (!draw) {
                continue;
            }
            for (double[] area : safeAreas) {
                double dx = at.getX() - area[0];
                double dz = at.getZ() - area[2];
                double reach = area[3] + 6;
                if (dx * dx + dz * dz > reach * reach || at.getY() < area[1] - 4 || at.getY() > area[1] + area[4] + 4) {
                    continue;
                }
                org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(230, 30, 30), 1.4f);
                int points = (int) Math.max(16, area[3] * 7);
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2 * i / points;
                    double px = area[0] + Math.cos(angle) * area[3];
                    double pz = area[2] + Math.sin(angle) * area[3];
                    for (double h = 0.3; h <= 2.4; h += 0.7) {
                        player.spawnParticle(org.bukkit.Particle.DUST, px, area[1] + h, pz, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }
    }

    private void upgradeKits(YamlConfiguration config) {
        if (config.getBoolean("migrated.kit-v2", false) || config.getDefaults() == null) {
            return;
        }
        config.set("kits", null);
        org.bukkit.configuration.ConfigurationSection fresh = config.getDefaults().getConfigurationSection("kits");
        if (fresh != null) {
            for (String key : fresh.getKeys(true)) {
                Object value = fresh.get(key);
                if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                    config.set("kits." + key, value);
                }
            }
        }
        config.set("default-kit", config.getDefaults().getString("default-kit", "combo"));
        config.set("migrated.kit-v2", true);
        core.configs().save(FILE);
        loadCommon(config, "comboarena.yml", new ArrayList<>());
        core.getLogger().info("ComboFly: az új Combo kit (dia szett, op almák, 12 enderpearl) bekerült.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitNpc(PlayerInteractEntityEvent event) {
        if (extras.isNpc(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                extras.use(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitNpcAt(PlayerInteractAtEntityEvent event) {
        if (extras.isNpc(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                extras.use(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitNpcHit(EntityDamageByEntityEvent event) {
        if (extras.isNpc(event.getEntity())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player) {
                extras.use((Player) event.getDamager());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitNpcDamage(EntityDamageEvent event) {
        if (extras.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKitNpcStand(PlayerArmorStandManipulateEvent event) {
        if (extras.isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void build(YamlConfiguration config) {
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 100);
        int z = config.getInt("center.z", 0);
        core.getLogger().info("Építem a ComboFly arénát...");
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(x - ComboArenaBuild.CLEAR, y - 36, z - ComboArenaBuild.CLEAR, x + ComboArenaBuild.CLEAR, y + 30, z + ComboArenaBuild.CLEAR);
        ComboArenaBuild build = new ComboArenaBuild(placer, x, y, z);
        build.build();
        Layout layout = build.layout();
        Layout.Point point = layout.points.get("spawn");
        Layout.Point safe = layout.points.get("safe");
        Locs.write(config, "spawn", x + point.x, y + point.y, z + point.z, point.yaw);
        Locs.write(config, "safe-center", x + safe.x, y + safe.y, z + safe.z, 0);
        config.set("safe-radius", safe.yaw);
        config.set("void-y", y - 30);
        config.set("built", true);
        core.configs().save(FILE);
        core.getLogger().info("A ComboFly aréna elkészült (" + placer.placed() + " blokk).");
    }

    public void rebuild() {
        YamlConfiguration config = core.configs().get(FILE);
        clearTitle(config);
        config.set("title.built", false);
        if (config.getBoolean("custom", false)) {
            core.configs().save(FILE);
            load(config, new ArrayList<>());
            return;
        }
        config.set("built", false);
        for (Player player : new ArrayList<>(world.getPlayers())) {
            core.modes().send(player, "lobby", true);
        }
        build(config);
        load(config, new ArrayList<>());
    }

    private void customMap(YamlConfiguration config) {
        String folder = text(config, "custom-map.folder");
        if (folder.isEmpty() || config.getBoolean("custom-map.applied", false)) {
            return;
        }
        if (!new File(new File(Bukkit.getWorldContainer(), folder), "level.dat").isFile()) {
            return;
        }
        config.set("world", folder);
        config.set("custom", true);
        config.set("built", true);
        Locs.write(config, "spawn", number(config, "custom-map.spawn.x", 0.5), number(config, "custom-map.spawn.y", 100),
                number(config, "custom-map.spawn.z", 0.5), number(config, "custom-map.spawn.yaw", 0));
        Locs.write(config, "safe-center", number(config, "custom-map.safe-center.x", 0.5), number(config, "custom-map.safe-center.y", 100),
                number(config, "custom-map.safe-center.z", 0.5), 0);
        config.set("safe-radius", number(config, "custom-map.safe-radius", 10));
        config.set("void-y", number(config, "custom-map.void-y", 0));
        config.set("fall-damage", false);
        for (String key : new String[]{"x", "y", "z", "scale", "facing"}) {
            if (!config.isSet("title." + key)) {
                Object value = fallback(config, "title." + key);
                if (value != null) {
                    config.set("title." + key, value);
                }
            }
        }
        config.set("title.enabled", true);
        config.set("title.built", false);
        config.set("custom-map.applied", true);
        core.configs().save(FILE);
        refreshText("modes", "modes.combofly.description", "Jégbúra");
        refreshText("messages_hu", "comboarena.welcome", "lépcsőn");
        core.getLogger().info("ComboFly: megtaláltam a saját pályát (" + folder + "), mostantól azt használom.");
    }

    private void refreshText(String file, String path, String outdated) {
        YamlConfiguration target = core.configs().get(file);
        if (target == null || !target.isSet(path) || target.getDefaults() == null) {
            return;
        }
        Object current = target.get(path);
        String joined = current instanceof List ? String.join("\n", target.getStringList(path)) : String.valueOf(current);
        if (joined.contains(outdated)) {
            target.set(path, target.getDefaults().get(path));
            core.configs().save(file);
        }
    }

    private static Object fallback(YamlConfiguration config, String path) {
        if (config.isSet(path)) {
            return config.get(path);
        }
        Configuration defaults = config.getDefaults();
        return defaults == null ? null : defaults.get(path);
    }

    private static String text(YamlConfiguration config, String path) {
        Object value = fallback(config, path);
        return value == null ? "" : value.toString().trim();
    }

    private static double number(YamlConfiguration config, String path, double def) {
        Object value = fallback(config, path);
        return value instanceof Number ? ((Number) value).doubleValue() : def;
    }

    private Title title(YamlConfiguration config, Placer placer) {
        return new Title(placer, config.getInt("title.x", 0), config.getInt("title.y", 154), config.getInt("title.z", -72),
                config.getInt("title.scale", 3), config.getString("title.facing", "south"));
    }

    private void placeTitle(YamlConfiguration config, List<String> problems) {
        String facing = config.getString("title.facing", "south");
        if (!Title.validFacing(facing)) {
            problems.add("comboarena.yml -> title.facing: csak south, north, east vagy west lehet (most: " + facing + "), south-ot használok");
            config.set("title.facing", "south");
        }
        Placer placer = new Placer(world, core.getLogger());
        Title title = title(config, placer);
        title.build();
        int[] box = title.box();
        List<Integer> placed = new ArrayList<>();
        for (int value : box) {
            placed.add(value);
        }
        config.set("title.placed", placed);
        config.set("title.built", true);
        core.configs().save(FILE);
        core.getLogger().info("A ComboFly IceMine felirata kész (" + placer.placed() + " blokk).");
    }

    private void clearTitle(YamlConfiguration config) {
        List<Integer> box = config.getIntegerList("title.placed");
        if (box.size() != 6 || world == null) {
            return;
        }
        new Placer(world, core.getLogger()).clear(box.get(0), box.get(1), box.get(2), box.get(3), box.get(4), box.get(5));
        config.set("title.placed", null);
    }

    public void setSpawn(Location location) {
        spawn = location.clone();
        YamlConfiguration config = core.configs().get(FILE);
        Locs.write(config, "spawn", location);
        core.configs().save(FILE);
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
        if (safeCenter == null || location.getWorld() != world) {
            return false;
        }
        double dx = location.getX() - safeCenter.getX();
        double dz = location.getZ() - safeCenter.getZ();
        double dy = location.getY() - safeCenter.getY();
        if (dx * dx + dz * dz <= safeRadius * safeRadius && dy > -4 && dy < 12) {
            return true;
        }
        return areaAt(location) != null;
    }

    @Override
    protected void prepare(Player player) {
        resetState(player, GameMode.ADVENTURE);
        combatStyle(player, true);
        giveSafeHotbar(player);
    }

    @Override
    public void enter(Player player) {
        prepare(player);
        core.messages().send(player, "comboarena.welcome");
    }

    private static AttributeInstance attackSpeed(Player player) {
        if (attackSpeedType == null) {
            for (Object value : (Iterable<?>) Registry.ATTRIBUTE) {
                if (value instanceof Keyed && ((Keyed) value).getKey().getKey().endsWith("attack_speed")) {
                    attackSpeedType = value;
                    break;
                }
            }
        }
        return attackSpeedType == null ? null : player.getAttribute((Attribute) attackSpeedType);
    }

    private void combatStyle(Player player, boolean inArena) {
        AttributeInstance speed = attackSpeed(player);
        if (speed == null) {
            return;
        }
        speed.setBaseValue(inArena && oldCombat ? 1024.0 : speed.getDefaultValue());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == world) {
            combatStyle(player, true);
        } else if (event.getFrom() == world) {
            combatStyle(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinCombat(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        combatStyle(player, player.getWorld() == world);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (oldCombat && event.getEntity().getWorld() == world && event.getCause().name().equals("ENTITY_SWEEP_ATTACK")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl) || event.getEntity().getWorld() != world || pearlCooldown <= 0) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        int ticks = pearlCooldown * 20;
        Bukkit.getScheduler().runTask(core, () -> {
            if (player.isOnline() && player.getWorld() == world) {
                player.setCooldown(Material.ENDER_PEARL, ticks);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!fallDamage && event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getWorld() != world) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && event.getPlayer().hasPermission("icemine.admin.build")) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.PHYSICAL) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == world && !(event.getPlayer().getGameMode() == GameMode.CREATIVE && event.getPlayer().hasPermission("icemine.admin.build"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == world && !(event.getPlayer().getGameMode() == GameMode.CREATIVE && event.getPlayer().hasPermission("icemine.admin.build"))) {
            event.setCancelled(true);
        }
    }
}
