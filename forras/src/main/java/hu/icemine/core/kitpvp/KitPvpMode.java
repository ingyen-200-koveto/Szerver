package hu.icemine.core.kitpvp;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public final class KitPvpMode extends PvpMode {

    public static final class Arena {
        public final String id;
        public final String name;
        public final Material icon;
        public final List<String> description;
        public final Location spawn;
        public final Location safeCenter;
        public final double safeRadius;

        Arena(String id, String name, Material icon, List<String> description, Location spawn, Location safeCenter, double safeRadius) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.spawn = spawn;
            this.safeCenter = safeCenter;
            this.safeRadius = safeRadius;
        }
    }

    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, String> playerArena = new HashMap<>();
    private final Map<Integer, List<ItemStack>> streakItems = new HashMap<>();
    private String defaultArena = "";
    private final KitUpgrades upgrades;

    public KitPvpMode(IceMineCore core) {
        super(core, "kitpvp");
        this.upgrades = new KitUpgrades(core);
    }

    public KitUpgrades upgrades() {
        return upgrades;
    }

    @Override
    protected double cooldownMultiplier(Player player) {
        return upgrades.cooldownMultiplier(player);
    }

    @Override
    protected double rewardMultiplier(Player player) {
        return upgrades.rewardMultiplier(player);
    }

    @Override
    protected void afterEquip(Player player, Kit kit) {
        upgrades.apply(player);
    }

    @Override
    protected void extraHotbar(org.bukkit.inventory.PlayerInventory inventory) {
        if (!upgrades.all().isEmpty()) {
            inventory.setItem(4, Items.of(Material.EXPERIENCE_BOTTLE).name(core.messages().raw("upgrade.item"))
                    .lore(core.messages().list("upgrade.item-lore")).tag("pvp:upgrades").build());
        }
    }

    @Override
    protected void openUpgrades(Player player) {
        upgrades.open(player, () -> openKits(player));
    }

    @Override
    protected void decorateKitMenu(Menu menu, Player player) {
        if (upgrades.all().isEmpty()) {
            return;
        }
        menu.set(menu.size() - 5, Items.of(Material.EXPERIENCE_BOTTLE).name(core.messages().raw("upgrade.item"))
                .lore(core.messages().list("upgrade.item-lore")).build(), clicker -> upgrades.open(clicker, () -> openKits(clicker)));
    }

    /**
     * Új kit a játékos jelenlegi felszereléséből (/icem kit create). Beírja a kitpvp.yml-be, és ha rangot is megadtak,
     * a ranks.yml-ben ahhoz a ranghoz köti.
     */
    public boolean createKit(Player admin, String kitId, String rankId) {
        YamlConfiguration config = core.configs().get("kitpvp");
        String base = "kits." + kitId;
        List<String> items = new ArrayList<>();
        ItemStack[] contents = admin.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || Items.tagOf(item) != null) {
                continue;
            }
            String spec = Kit.spec(item);
            if (spec != null) {
                items.add(spec);
            }
        }
        if (items.isEmpty()) {
            core.messages().send(admin, "kit.create-empty");
            return false;
        }
        ItemStack hand = admin.getInventory().getItemInMainHand();
        config.set(base, null);
        config.set(base + ".name", "&f&l" + Character.toUpperCase(kitId.charAt(0)) + kitId.substring(1));
        config.set(base + ".icon", (hand == null || hand.getType().isAir() ? Material.IRON_SWORD : hand.getType()).name());
        config.set(base + ".description", List.of("&7Egyedi kit."));
        config.set(base + ".items", items);
        ItemStack[] armor = admin.getInventory().getArmorContents();
        String[] slots = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < slots.length && i < armor.length; i++) {
            String spec = Kit.spec(armor[i]);
            if (spec != null) {
                config.set(base + ".armor." + slots[i], spec);
            }
        }
        core.configs().save("kitpvp");
        if (rankId != null) {
            YamlConfiguration ranks = core.configs().get("ranks");
            String path = "ranks." + rankId + ".kits";
            List<String> list = new ArrayList<>(ranks.getStringList(path));
            if (!list.contains(kitId)) {
                list.add(kitId);
            }
            ranks.set(path, list);
            core.configs().save("ranks");
            core.reloadConfigs("ranks");
        }
        core.reloadConfigs("kitpvp");
        return true;
    }

    public boolean deleteKit(String kitId) {
        YamlConfiguration config = core.configs().get("kitpvp");
        if (!config.isSet("kits." + kitId)) {
            return false;
        }
        config.set("kits." + kitId, null);
        core.configs().save("kitpvp");
        YamlConfiguration ranks = core.configs().get("ranks");
        ConfigurationSection section = ranks.getConfigurationSection("ranks");
        boolean changed = false;
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                List<String> list = new ArrayList<>(ranks.getStringList("ranks." + rank + ".kits"));
                if (list.remove(kitId)) {
                    ranks.set("ranks." + rank + ".kits", list);
                    changed = true;
                }
            }
        }
        if (changed) {
            core.configs().save("ranks");
            core.reloadConfigs("ranks");
        }
        core.reloadConfigs("kitpvp");
        return true;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        String name = config.getString("world", "ice_kitpvp");
        if (world == null) {
            world = createWorld(name);
            if (world == null) {
                problems.add("kitpvp.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        prepareWorld(world, config.getLong("time", 6000));
        upgrade(config);
        if (!config.getBoolean("built", false)) {
            build(config);
        }
        loadCommon(config, "kitpvp.yml", problems);
        upgrades.load(config.getConfigurationSection("upgrades"), currency(), problems);
        arenas.clear();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                Location spawn = Locs.read(section.getConfigurationSection(id + ".spawn"), world);
                Location center = Locs.read(section.getConfigurationSection(id + ".safe-center"), world);
                if (spawn == null || center == null) {
                    problems.add("kitpvp.yml -> arenas." + id + ": hiányzik a spawn vagy a safe-center");
                    continue;
                }
                arenas.put(id.toLowerCase(Locale.ROOT), new Arena(id.toLowerCase(Locale.ROOT), section.getString(id + ".name", id),
                        Items.material(section.getString(id + ".icon", "PACKED_ICE"), Material.PACKED_ICE),
                        new ArrayList<>(section.getStringList(id + ".description")), spawn, center, Math.max(3, section.getDouble(id + ".safe-radius", 8))));
            }
        }
        if (arenas.isEmpty()) {
            problems.add("kitpvp.yml -> arenas: nincs egy használható aréna sem");
        }
        defaultArena = config.getString("default-arena", arenas.isEmpty() ? "" : arenas.keySet().iterator().next()).toLowerCase(Locale.ROOT);
        if (!arenas.containsKey(defaultArena) && !arenas.isEmpty()) {
            defaultArena = arenas.keySet().iterator().next();
        }
        streakItems.clear();
        ConfigurationSection rewards = config.getConfigurationSection("streak-items");
        if (rewards != null) {
            for (String key : rewards.getKeys(false)) {
                try {
                    int streak = Integer.parseInt(key);
                    List<ItemStack> items = new ArrayList<>();
                    for (String spec : rewards.getStringList(key)) {
                        ItemStack item = Kit.parse("streak", spec, "kitpvp.yml -> streak-items." + key, problems);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    streakItems.put(streak, items);
                } catch (NumberFormatException exception) {
                    problems.add("kitpvp.yml -> streak-items." + key + ": a kulcs csak szám lehet");
                }
            }
        }
    }

    private void upgrade(YamlConfiguration config) {
        org.bukkit.configuration.Configuration defaults = config.getDefaults();
        if (defaults == null || config.getBoolean("migrated.flat-v1", false)) {
            return;
        }
        config.set("arenas", null);
        copy(defaults, config, "arenas");
        config.set("default-arena", defaults.getString("default-arena", "jegmezo"));
        ConfigurationSection kits = defaults.getConfigurationSection("kits");
        if (kits != null) {
            for (String id : kits.getKeys(false)) {
                if (!config.isSet("kits." + id)) {
                    copy(defaults, config, "kits." + id);
                }
            }
        }
        config.set("built", false);
        config.set("migrated.flat-v1", true);
        core.configs().save("kitpvp");
        core.getLogger().info("KitPvP: új lapos aréna (Jégmező) kiemelt lobbyval és új kitek. Az arénát most felépítem.");
    }

    private static void copy(org.bukkit.configuration.Configuration from, YamlConfiguration to, String path) {
        ConfigurationSection section = from.getConfigurationSection(path);
        if (section == null) {
            to.set(path, from.get(path));
            return;
        }
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (!(value instanceof ConfigurationSection)) {
                to.set(path + "." + key, value);
            }
        }
    }

    private void build(YamlConfiguration config) {
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 100);
        int z = config.getInt("center.z", 0);
        core.getLogger().info("Építem a KitPvP arénákat...");
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(x - KitArenaBuild.CLEAR, y - 30, z - KitArenaBuild.CLEAR, x + KitArenaBuild.CLEAR + KitArenaBuild.SECOND_OFFSET, y + 50, z + KitArenaBuild.CLEAR);
        KitArenaBuild build = new KitArenaBuild(placer, x, y, z);
        build.build();
        Layout layout = build.layout();
        for (Map.Entry<String, Layout.Point> entry : layout.points.entrySet()) {
            String key = entry.getKey();
            Layout.Point point = entry.getValue();
            if (key.startsWith("arena:") && key.endsWith(":spawn")) {
                String arena = key.substring(6, key.length() - 6);
                Locs.write(config, "arenas." + arena + ".spawn", x + point.x, y + point.y, z + point.z, point.yaw);
            } else if (key.startsWith("arena:") && key.endsWith(":safe")) {
                String arena = key.substring(6, key.length() - 5);
                Locs.write(config, "arenas." + arena + ".safe-center", x + point.x, y + point.y, z + point.z, 0);
                config.set("arenas." + arena + ".safe-radius", point.yaw);
            }
        }
        config.set("void-y", y - 25);
        config.set("built", true);
        core.configs().save("kitpvp");
        core.getLogger().info("A KitPvP arénák elkészültek (" + placer.placed() + " blokk).");
    }

    public void rebuild() {
        YamlConfiguration config = core.configs().get("kitpvp");
        config.set("built", false);
        for (Player player : world.getPlayers()) {
            core.modes().send(player, "lobby", true);
        }
        build(config);
        load(config, new ArrayList<>());
    }

    public Map<String, Arena> arenas() {
        return arenas;
    }

    private Arena arenaOf(Player player) {
        String id = playerArena.get(player.getUniqueId());
        if (id == null) {
            PlayerProfile profile = core.profiles().profile(player);
            id = profile == null ? defaultArena : profile.setting("kitpvp.arena", defaultArena);
        }
        Arena arena = arenas.get(id);
        return arena != null ? arena : arenas.get(defaultArena);
    }

    @Override
    public Location spawn(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? (world == null ? null : world.getSpawnLocation()) : arena.spawn.clone();
    }

    @Override
    protected Location respawn(Player player) {
        return spawn(player);
    }

    @Override
    protected boolean inSafe(Player player, Location location) {
        if (location.getWorld() != world) {
            return false;
        }
        for (Arena arena : arenas.values()) {
            double dx = location.getX() - arena.safeCenter.getX();
            double dz = location.getZ() - arena.safeCenter.getZ();
            double dy = location.getY() - arena.safeCenter.getY();
            if (dx * dx + dz * dz <= arena.safeRadius * arena.safeRadius && dy > -4 && dy < 12) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void prepare(Player player) {
        upgrades.clear(player);
        resetState(player, GameMode.ADVENTURE);
        giveSafeHotbar(player);
    }

    @Override
    public void enter(Player player) {
        Arena arena = arenaOf(player);
        if (arena != null) {
            playerArena.put(player.getUniqueId(), arena.id);
        }
        prepare(player);
        core.messages().send(player, "kitpvp.welcome", "%arena%", arena == null ? "-" : arena.name);
    }

    @Override
    public void leave(Player player, boolean quitting) {
        super.leave(player, quitting);
        upgrades.clear(player);
        playerArena.remove(player.getUniqueId());
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        super.placeholders(player, values);
        Arena arena = arenaOf(player);
        values.put("%arena%", arena == null ? "-" : Text.color(arena.name));
    }

    @Override
    protected boolean hasArenas() {
        return arenas.size() > 1;
    }

    @Override
    public void openArenas(Player player) {
        Menu menu = new Menu("kitpvp-arenas", 3, core.messages().raw("arena.menu-title"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int slot = 11;
        Arena current = arenaOf(player);
        for (Arena arena : arenas.values()) {
            if (slot > 15) {
                break;
            }
            int count = 0;
            for (Player other : world.getPlayers()) {
                if (arena.id.equals(playerArena.get(other.getUniqueId()))) {
                    count++;
                }
            }
            List<String> lore = new ArrayList<>(arena.description);
            lore.add("");
            lore.add(core.messages().raw("arena.players").replace("%count%", String.valueOf(count)));
            lore.add(core.messages().raw(current != null && current.id.equals(arena.id) ? "arena.current" : "arena.click"));
            Items item = Items.of(arena.icon).name(arena.name).lore(lore).hideAll();
            if (current != null && current.id.equals(arena.id)) {
                item.glow();
            }
            String arenaId = arena.id;
            menu.set(slot, item.build(), clicker -> {
                clicker.closeInventory();
                switchArena(clicker, arenaId);
            });
            slot += 2;
        }
        menu.open(player);
    }

    public void switchArena(Player player, String arenaId) {
        Arena arena = arenas.get(arenaId);
        if (arena == null || player.getWorld() != world) {
            return;
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        if (!inSafe(player, player.getLocation())) {
            core.messages().send(player, "arena.only-safe");
            return;
        }
        playerArena.put(player.getUniqueId(), arena.id);
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.put("kitpvp.arena", arena.id);
        }
        core.guard().allowNext(player);
        player.teleport(arena.spawn);
        prepare(player);
        core.messages().send(player, "arena.switched", "%arena%", arena.name);
    }

    @Override
    protected void onKill(Player killer, Player victim, int streak) {
        List<ItemStack> items = streakItems.get(streak);
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            killer.getInventory().addItem(item.clone());
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
