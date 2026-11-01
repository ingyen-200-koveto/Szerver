package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeService;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

public final class LobbyModule implements ModeHandler {

    public static final String VISIBILITY = "lobby.hide-players";
    public static final String FLY = "lobby.fly";

    private final IceMineCore core;
    private final LaunchPads launchPads;
    private final Set<UUID> hiding = new HashSet<>();
    private final InfoTexts info = new InfoTexts();
    private World world;
    private Location spawn;
    private double voidY = 40;
    private boolean joinToLobby = true;
    private boolean pvpItemsCleanup = true;
    private final List<HotbarItem> hotbar = new ArrayList<>();

    public static final class HotbarItem {
        public final String id;
        public final int slot;
        public final ItemStack item;
        public final String action;

        HotbarItem(String id, int slot, ItemStack item, String action) {
            this.id = id;
            this.slot = slot;
            this.item = item;
            this.action = action;
        }
    }

    public LobbyModule(IceMineCore core) {
        this.core = core;
        this.launchPads = new LaunchPads(core);
    }

    @Override
    public String id() {
        return ModeService.LOBBY;
    }

    @Override
    public World world() {
        return world;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Values values = new Values("lobby.yml", config::get);
        String name = values.text("world", "");
        world = name.isBlank() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) {
            problems.add("lobby.yml -> world: nincs ilyen világ (" + name + "), a fő világot használom");
            world = Bukkit.getWorlds().get(0);
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("announceAdvancements", "false");
        world.setGameRuleValue("showDeathMessages", "false");
        world.setGameRuleValue("fallDamage", "false");
        world.setGameRuleValue("randomTickSpeed", "0");
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(values.integer("time", 6000, 0, 23999));
        voidY = values.decimal("void-y", 40, -64, 300);
        joinToLobby = values.bool("join-to-lobby", true);
        pvpItemsCleanup = values.bool("cleanup-foreign-items", true);
        if (!config.getBoolean("built", false) || config.getInt("layout", 1) < LobbyBuild.VERSION) {
            build(config);
        }
        spawn = Locs.read(config.getConfigurationSection("spawn"), world);
        if (spawn == null) {
            spawn = world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        launchPads.load(config, world, problems);
        loadHotbar(config, values);
        info.load(config, problems);
        problems.addAll(values.problems());
    }

    private void build(YamlConfiguration config) {
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 100);
        int z = config.getInt("center.z", 0);
        core.getLogger().info("Építem a lobbyt, ez eltarthat pár másodpercig...");
        long start = System.currentTimeMillis();
        Placer placer = new Placer(world, core.getLogger());
        int cleared = placer.clear(x - LobbyBuild.CLEAR, y - 75, z - LobbyBuild.CLEAR_NORTH, x + LobbyBuild.CLEAR, y + 70, z + LobbyBuild.CLEAR_SOUTH);
        LobbyBuild build = new LobbyBuild(placer, x, y, z);
        build.build();
        applyLayout(config, build.layout(), x, y, z);
        config.set("built", true);
        config.set("layout", LobbyBuild.VERSION);
        core.configs().save("lobby");
        core.configs().save("parkour");
        core.getLogger().info("A lobby elkészült: " + placer.placed() + " blokk, " + cleared + " régi blokk eltávolítva, "
                + (System.currentTimeMillis() - start) + " ms.");
    }

    public void rebuild() {
        YamlConfiguration config = core.configs().get("lobby");
        config.set("built", false);
        build(config);
        launchPads.load(config, world, new ArrayList<>());
        core.reloadLobbyParts();
    }

    private void applyLayout(YamlConfiguration config, Layout layout, int x, int y, int z) {
        Layout.Point spawnPoint = layout.points.get("spawn");
        if (spawnPoint != null) {
            Locs.write(config, "spawn", x + spawnPoint.x, y + spawnPoint.y, z + spawnPoint.z, spawnPoint.yaw);
        }
        config.set("portals", null);
        config.set("holograms", null);
        config.set("npcs", null);
        config.set("launchpads", null);
        writeAnchors(config, layout, x, y, z);
        YamlConfiguration parkour = core.configs().get("parkour");
        Layout.Point start = layout.points.get("parkour:start");
        Layout.Point finish = layout.points.get("parkour:finish");
        if (start != null && finish != null) {
            Locs.write(parkour, "course.start", x + start.x, y + start.y, z + start.z, start.yaw);
            Locs.write(parkour, "course.finish", x + finish.x, y + finish.y, z + finish.z, finish.yaw);
            parkour.set("course.checkpoints", null);
            List<Layout.Point> checkpoints = layout.lists.getOrDefault("parkour:checkpoints", new ArrayList<>());
            for (int i = 0; i < checkpoints.size(); i++) {
                Layout.Point point = checkpoints.get(i);
                Locs.write(parkour, "course.checkpoints." + (i + 1), x + point.x, y + point.y, z + point.z, point.yaw);
            }
            Layout.Point fail = layout.points.get("parkour:fail");
            if (fail != null) {
                parkour.set("course.fail-y", y + fail.y);
            }
        }
    }

    private static void writeAnchors(YamlConfiguration config, Layout layout, int x, int y, int z) {
        for (Map.Entry<String, Layout.Area> entry : layout.areas.entrySet()) {
            if (!entry.getKey().startsWith("portal:")) {
                continue;
            }
            String mode = entry.getKey().substring("portal:".length());
            Layout.Area area = entry.getValue();
            new Locs.Box(x + area.x1, y + area.y1, z + area.z1, x + area.x2, y + area.y2, z + area.z2).write(config, "portals." + mode + ".area");
            config.set("portals." + mode + ".mode", mode);
            config.set("portals." + mode + ".trigger", "walk");
        }
        for (Map.Entry<String, Layout.Point> entry : layout.points.entrySet()) {
            String key = entry.getKey();
            Layout.Point point = entry.getValue();
            if (key.startsWith("holo:")) {
                String id = key.substring("holo:".length());
                Locs.write(config, "holograms." + id + ".location", x + point.x, y + point.y, z + point.z, point.yaw);
                LobbyDefaults.hologram(config, id);
            } else if (key.startsWith("npc:")) {
                String id = key.substring("npc:".length());
                Locs.write(config, "npcs." + id + ".location", x + point.x, y + point.y, z + point.z, point.yaw);
                LobbyDefaults.npc(config, id);
            } else if (key.startsWith("launch:")) {
                String rest = key.substring("launch:".length());
                if (rest.endsWith(":target")) {
                    Locs.write(config, "launchpads." + rest.substring(0, rest.length() - ":target".length()) + ".target", x + point.x, y + point.y, z + point.z, 0);
                } else {
                    Locs.write(config, "launchpads." + rest + ".location", x + point.x, y + point.y, z + point.z, 0);
                }
            }
        }
    }

    private void loadHotbar(YamlConfiguration config, Values values) {
        hotbar.clear();
        if (!values.bool("hotbar.enabled", true)) {
            return;
        }
        ConfigurationSection section = config.getConfigurationSection("hotbar.items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String base = "hotbar.items." + id + ".";
            int slot = values.integer(base + "slot", 0, 0, 8);
            Material material = Items.material(values.text(base + "material", "PAPER"), Material.PAPER);
            ItemStack item = Items.of(material).name(values.text(base + "name", id)).lore(config.getStringList(base + "lore")).hideAll().tag("lobby:" + id).build();
            hotbar.add(new HotbarItem(id, slot, item, values.text(base + "action", "")));
        }
    }

    public List<HotbarItem> hotbarItems() {
        return hotbar;
    }

    public HotbarItem hotbarItem(ItemStack stack) {
        String tag = Items.tagOf(stack);
        if (tag == null || !tag.startsWith("lobby:")) {
            return null;
        }
        String id = tag.substring("lobby:".length());
        for (HotbarItem item : hotbar) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    public boolean joinToLobby() {
        return joinToLobby;
    }

    public boolean cleanupForeignItems() {
        return pvpItemsCleanup;
    }

    public double voidY() {
        return voidY;
    }

    public void setSpawn(Location location) {
        spawn = location.clone();
        YamlConfiguration config = core.configs().get("lobby");
        Locs.write(config, "spawn", location);
        core.configs().save("lobby");
        world.setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public Location spawn(Player player) {
        return spawn.clone();
    }

    public InfoTexts info() {
        return info;
    }

    public boolean inLobby(Player player) {
        return player.getWorld() == world;
    }

    public void reset(Player player) {
        player.setGameMode(player.hasPermission("icemine.admin.build") && player.getGameMode() == GameMode.CREATIVE ? GameMode.CREATIVE : GameMode.ADVENTURE);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        try {
            player.setHealth(player.getMaxHealth());
        } catch (IllegalArgumentException ignored) {
        }
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setLevel(0);
        player.setExp(0f);
        player.setMaximumNoDamageTicks(20);
    }

    public void giveHotbar(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        for (HotbarItem item : hotbar) {
            ItemStack stack = item.item.clone();
            if (stack.getType() == Material.PLAYER_HEAD) {
                stack = Items.head(player).name(stack.getItemMeta() == null ? "" : stack.getItemMeta().getDisplayName())
                        .lore(stack.getItemMeta() == null || stack.getItemMeta().getLore() == null ? new ArrayList<>() : stack.getItemMeta().getLore())
                        .tag("lobby:" + item.id).build();
            }
            inventory.setItem(item.slot, stack);
        }
        inventory.setHeldItemSlot(0);
    }

    public void applyFlight(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        boolean fly = player.hasPermission("icemine.cosmetic.fly") && profile != null && profile.flag(FLY, false);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setAllowFlight(fly);
        if (!fly) {
            player.setFlying(false);
        }
    }

    public void applyVisibility(Player viewer) {
        PlayerProfile profile = core.profiles().profile(viewer);
        boolean hide = profile != null && profile.flag(VISIBILITY, false);
        if (hide) {
            hiding.add(viewer.getUniqueId());
        } else {
            hiding.remove(viewer.getUniqueId());
        }
        for (Player other : world.getPlayers()) {
            if (other.equals(viewer) || other.hasMetadata("NPC")) {
                continue;
            }
            if (hide && !other.hasPermission("icemine.mod.always-visible")) {
                viewer.hidePlayer(core, other);
            } else {
                viewer.showPlayer(core, other);
            }
        }
    }

    public void hideFromHiders(Player joined) {
        for (UUID id : hiding) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.getWorld() == world && !joined.equals(viewer) && !joined.hasPermission("icemine.mod.always-visible")) {
                viewer.hidePlayer(core, joined);
            }
        }
    }

    private void showAll(Player player) {
        hiding.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                player.showPlayer(core, other);
                other.showPlayer(core, player);
            }
        }
    }

    @Override
    public void enter(Player player) {
        reset(player);
        giveHotbar(player);
        applyFlight(player);
        applyVisibility(player);
        hideFromHiders(player);
        core.cosmetics().refresh(player);
    }

    @Override
    public void leave(Player player, boolean quitting) {
        core.parkour().stop(player, true);
        showAll(player);
        if (!quitting) {
            clearLobbyItems(player);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    public void clearLobbyItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            String tag = Items.tagOf(contents[i]);
            if (tag != null && (tag.startsWith("lobby:") || tag.startsWith("parkour:"))) {
                inventory.setItem(i, null);
            }
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        values.put("%mode%", core.messages().get("mode.lobby-name"));
    }

    public void handleAction(Player player, String action) {
        if (!core.auth().isLoggedIn(player)) {
            core.messages().send(player, "auth.login-first");
            return;
        }
        String[] parts = action.split(":", 2);
        String kind = parts[0].toLowerCase(Locale.ROOT);
        String value = parts.length > 1 ? parts[1] : "";
        switch (kind) {
            case "menu":
                core.menus().open(player, value);
                break;
            case "mode":
                core.modes().sendAndReport(player, value);
                break;
            case "book":
            case "contacts":
                core.contacts().open(player);
                break;
            case "info":
                if (!info.send(player, value, core.boards()::global)) {
                    core.messages().send(player, "general.no-info");
                }
                break;
            case "parkour":
                if (value.equalsIgnoreCase("start") || value.isEmpty()) {
                    core.parkour().teleportToStart(player);
                }
                break;
            case "command":
                if (!value.isBlank()) {
                    player.performCommand(value);
                }
                break;
            case "bedwars":
                // bedwars:solo -> beszállás a Solo módba, bedwars: (üres) -> a BedWars menü
                if (core.bedwars() != null) {
                    hu.icemine.core.bedwars.BwMode bwMode = value.isBlank() ? null : core.bedwars().mode(value);
                    if (bwMode == null) {
                        core.bedwars().menus().open(player);
                    } else {
                        core.bedwars().play(player, bwMode);
                    }
                }
                break;
            case "message":
                if (!value.isBlank()) {
                    core.messages().sendTagged(player, value);
                }
                break;
            default:
                break;
        }
    }

    public LaunchPads launchPads() {
        return launchPads;
    }

    public void quit(Player player) {
        hiding.remove(player.getUniqueId());
        launchPads.forget(player.getUniqueId());
    }
}
