package hu.icemine.core.modelobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.mode.ModeService;
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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class ModeLobbies implements Listener {

    public static final String FILE = "modelobbies";
    public static final String PREFIX = "lobby_";
    public static final String TAG = "icemine_modelobby";
    public static final String ITEM = "modelobby:";

    final class Holo {
        final String id;
        final Location location;
        final List<String> lines;
        final String board;
        final String action;
        TextDisplay display;
        Interaction hitbox;
        String shown = "";

        Holo(String id, Location location, List<String> lines, String board, String action) {
            this.id = id;
            this.location = location;
            this.lines = lines;
            this.board = board;
            this.action = action == null ? "" : action.trim();
        }
    }

    public final class ModeLobby implements ModeHandler {
        final String mode;
        final String handlerId;
        World world;
        Location spawn;
        Locs.Box portal;
        String playCommand = "";
        Material icon = Material.COMPASS;
        double voidY = -30;
        final List<Holo> holograms = new ArrayList<>();

        ModeLobby(String mode) {
            this.mode = mode;
            this.handlerId = PREFIX + mode;
        }

        @Override
        public String id() {
            return handlerId;
        }

        @Override
        public World world() {
            return world;
        }

        @Override
        public Location spawn(Player player) {
            return spawn == null ? null : spawn.clone();
        }

        @Override
        public void enter(Player player) {
            core.lobby().reset(player);
            giveHotbar(player, this);
            core.cosmetics().refresh(player);
            ModeInfo info = core.modes().info(mode);
            core.messages().send(player, "modelobby.welcome", "%mode%", info == null ? mode : info.name);
        }

        @Override
        public void leave(Player player, boolean quitting) {
            if (!quitting) {
                clearItems(player);
            }
        }

        @Override
        public void placeholders(Player player, Map<String, String> values) {
            ModeInfo info = core.modes().info(mode);
            values.put("%mode%", (info == null ? mode : info.name) + core.messages().raw("modelobby.suffix"));
        }

        public String mode() {
            return mode;
        }

        public boolean playCommandEmpty() {
            return playCommand.isEmpty();
        }
    }

    private static final String NPC_GROUP = "modelobby_";

    private final IceMineCore core;
    private final Map<String, ModeLobby> lobbies = new LinkedHashMap<>();
    private final java.util.Set<String> npcGroups = new java.util.HashSet<>();
    private final Map<UUID, Long> clicked = new HashMap<>();
    private final Map<UUID, Long> entered = new HashMap<>();
    private boolean enabled = true;

    public ModeLobbies(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        despawn();
        enabled = config.getBoolean("enabled", true);
        ConfigurationSection section = config.getConfigurationSection("lobbies");
        if (!enabled || section == null) {
            for (ModeLobby lobby : lobbies.values()) {
                lobby.world = null;
            }
            lobbies.clear();
            return;
        }
        Map<String, ModeLobby> fresh = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            String mode = id.toLowerCase(Locale.ROOT);
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || !entry.getBoolean("enabled", true)) {
                continue;
            }
            if (core.modes().info(mode) == null) {
                problems.add("modelobbies.yml -> lobbies." + id + ": nincs ilyen játékmód a modes.yml-ben, kihagyom");
                continue;
            }
            ModeLobby lobby = lobbies.containsKey(mode) ? lobbies.get(mode) : new ModeLobby(mode);
            String worldName = entry.getString("world", PREFIX + mode);
            if (lobby.world == null || !lobby.world.getName().equals(worldName)) {
                lobby.world = createWorld(worldName);
            }
            if (lobby.world == null) {
                problems.add("modelobbies.yml -> lobbies." + id + ".world: nem sikerült létrehozni a világot (" + worldName + ")");
                continue;
            }
            prepare(lobby.world, entry.getLong("time", 6000));
            if (!entry.getBoolean("built", false)) {
                build(config, "lobbies." + id, entry, lobby.world);
            }
            lobby.spawn = Locs.read(entry.getConfigurationSection("spawn"), lobby.world);
            if (lobby.spawn == null) {
                lobby.spawn = lobby.world.getSpawnLocation().add(0.5, 0, 0.5);
            }
            lobby.world.setSpawnLocation(lobby.spawn.getBlockX(), lobby.spawn.getBlockY(), lobby.spawn.getBlockZ());
            lobby.portal = Locs.Box.read(entry.getConfigurationSection("portal"));
            lobby.playCommand = entry.getString("play-command", "").trim();
            if (lobby.playCommand.startsWith("/")) {
                lobby.playCommand = lobby.playCommand.substring(1);
            }
            ModeInfo info = core.modes().info(mode);
            lobby.icon = Items.material(entry.getString("icon", info.icon == null ? "COMPASS" : info.icon.name()), Material.COMPASS);
            lobby.voidY = lobby.spawn.getY() - entry.getDouble("void-depth", 30);
            lobby.holograms.clear();
            ConfigurationSection holos = entry.getConfigurationSection("holograms");
            Layout defaults = null;
            if (holos != null) {
                for (String holo : holos.getKeys(false)) {
                    ConfigurationSection h = holos.getConfigurationSection(holo);
                    Location at = h == null ? null : Locs.read(h.getConfigurationSection("location"), lobby.world);
                    if (at == null && h != null) {
                        if (defaults == null) {
                            ModeLobbyBuild sample = new ModeLobbyBuild((bx, by, bz, data) -> { }, 0, 0, 0, "white", "", 0);
                            sample.build();
                            defaults = sample.layout();
                        }
                        Layout.Point point = defaults.points.get("holo:" + h.getString("spot", holo));
                        if (point != null) {
                            at = new Location(lobby.world, entry.getInt("center.x", 0) + point.x, entry.getInt("center.y", 100) + point.y,
                                    entry.getInt("center.z", 0) + point.z);
                        }
                    }
                    if (at == null) {
                        continue;
                    }
                    lobby.holograms.add(new Holo(holo, at, new ArrayList<>(h.getStringList("lines")), h.getString("toplist", ""),
                            h.getString("action", "")));
                }
            }
            // szobrok (pl. a BedWars lobbyban a négy mód), a lobby NPC rendszere rakja le őket
            String group = NPC_GROUP + mode;
            core.npcs().loadGroup(group, FILE, "lobbies." + id + ".npcs", lobby.world, entry.getConfigurationSection("npcs"), problems);
            npcGroups.add(group);
            fresh.put(mode, lobby);
        }
        for (String group : new ArrayList<>(npcGroups)) {
            if (!fresh.containsKey(group.substring(NPC_GROUP.length()))) {
                core.npcs().loadGroup(group, FILE, "", null, null, problems);
                npcGroups.remove(group);
            }
        }
        lobbies.clear();
        lobbies.putAll(fresh);
        for (ModeLobby lobby : lobbies.values()) {
            core.modes().register(lobby);
        }
    }

    private World createWorld(String name) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);
        return creator.createWorld();
    }

    private static void prepare(World world, long time) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("announceAdvancements", "false");
        world.setGameRuleValue("showDeathMessages", "false");
        world.setGameRuleValue("randomTickSpeed", "0");
        world.setStorm(false);
        world.setThundering(false);
        world.setTime(time);
    }

    private void build(YamlConfiguration config, String path, ConfigurationSection entry, World world) {
        int x = entry.getInt("center.x", 0);
        int y = entry.getInt("center.y", 100);
        int z = entry.getInt("center.z", 0);
        core.getLogger().info("Építem a(z) " + entry.getName() + " lobbyt (" + world.getName() + ")...");
        Placer placer = new Placer(world, core.getLogger());
        placer.clear(x - ModeLobbyBuild.CLEAR, y - 30, z - ModeLobbyBuild.CLEAR, x + ModeLobbyBuild.CLEAR, y + 30, z + ModeLobbyBuild.CLEAR);
        ModeLobbyBuild build = new ModeLobbyBuild(placer, x, y, z, entry.getString("color", "light_blue"),
                entry.getString("title", entry.getName().toUpperCase(Locale.ROOT)), entry.getInt("title-split", 0),
                entry.getString("theme", entry.getName()));
        build.build();
        Layout layout = build.layout();
        Layout.Point spawnPoint = layout.points.get("spawn");
        Locs.write(config, path + ".spawn", x + spawnPoint.x, y + spawnPoint.y, z + spawnPoint.z, spawnPoint.yaw);
        Layout.Area area = layout.areas.get("portal");
        new Locs.Box(x + area.x1, y + area.y1, z + area.z1, x + area.x2, y + area.y2, z + area.z2).write(config, path + ".portal");
        ConfigurationSection holos = config.getConfigurationSection(path + ".holograms");
        if (holos != null) {
            for (String holo : holos.getKeys(false)) {
                String spot = holos.getString(holo + ".spot", holo);
                Layout.Point point = layout.points.get("holo:" + spot);
                if (point == null) {
                    continue;
                }
                if (spot.startsWith("top") && holos.getString(holo + ".toplist", "").isEmpty() && holos.getString(holo + ".action", "").isEmpty()) {
                    continue;
                }
                Locs.write(config, path + ".holograms." + holo + ".location", x + point.x, y + point.y, z + point.z, 0);
            }
        }
        ConfigurationSection npcs = config.getConfigurationSection(path + ".npcs");
        if (npcs != null) {
            for (String npc : npcs.getKeys(false)) {
                Layout.Point point = layout.points.get("npc:" + npcs.getString(npc + ".spot", npc));
                if (point != null) {
                    Locs.write(config, path + ".npcs." + npc + ".location", x + point.x, y + point.y, z + point.z, point.yaw);
                }
            }
        }
        config.set(path + ".built", true);
        core.configs().save(FILE);
        core.getLogger().info("Kész: " + world.getName() + " (" + placer.placed() + " blokk).");
    }

    public void rebuild(String mode) {
        ModeLobby lobby = lobbies.get(mode);
        YamlConfiguration config = core.configs().get(FILE);
        if (lobby == null || config == null) {
            return;
        }
        for (Player player : new ArrayList<>(lobby.world.getPlayers())) {
            core.modes().send(player, ModeService.LOBBY, true);
        }
        config.set("lobbies." + mode + ".built", false);
        core.configs().save(FILE);
        load(config, new ArrayList<>());
        spawnAll();
    }

    public boolean setSpawn(Player player, String mode) {
        ModeLobby lobby = lobbies.get(mode);
        if (lobby == null || player.getWorld() != lobby.world) {
            return false;
        }
        YamlConfiguration config = core.configs().get(FILE);
        Locs.write(config, "lobbies." + mode + ".spawn", player.getLocation());
        core.configs().save(FILE);
        lobby.spawn = player.getLocation().clone();
        return true;
    }

    public boolean has(String mode) {
        return mode != null && lobbies.containsKey(mode);
    }

    public ModeLobby lobby(String mode) {
        return lobbies.get(mode);
    }

    public ModeLobby lobbyOf(World world) {
        if (world == null) {
            return null;
        }
        for (ModeLobby lobby : lobbies.values()) {
            if (lobby.world == world) {
                return lobby;
            }
        }
        return null;
    }

    public boolean isLobbyWorld(World world) {
        return lobbyOf(world) != null;
    }

    public static String modeOf(String handlerId) {
        return handlerId != null && handlerId.startsWith(PREFIX) ? handlerId.substring(PREFIX.length()) : null;
    }

    public int online(String mode) {
        ModeLobby lobby = lobbies.get(mode);
        return lobby == null ? 0 : lobby.online();
    }

    public List<String> modes() {
        return new ArrayList<>(lobbies.keySet());
    }

    public Location entry(String mode) {
        ModeLobby lobby = lobbies.get(mode);
        return lobby == null ? null : lobby.spawn(null);
    }

    public void play(Player player, String mode) {
        ModeLobby lobby = lobbies.get(mode);
        if (lobby == null) {
            core.modes().sendAndReport(player, mode);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = entered.get(player.getUniqueId());
        if (last != null && now - last < 1500) {
            return;
        }
        entered.put(player.getUniqueId(), now);
        if (!lobby.playCommand.isEmpty()) {
            if (Bukkit.getPluginCommand(lobby.playCommand.split(" ")[0]) == null && Bukkit.getServer().getCommandMap().getCommand(lobby.playCommand.split(" ")[0]) == null) {
                core.messages().send(player, "modelobby.not-ready");
                return;
            }
            player.performCommand(lobby.playCommand);
            return;
        }
        core.modes().sendAndReport(player, mode);
    }

    private void giveHotbar(Player player, ModeLobby lobby) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        ModeInfo info = core.modes().info(lobby.mode);
        String name = info == null ? lobby.mode : info.name;
        inventory.setItem(0, Items.of(lobby.icon).name(core.messages().raw("modelobby.item.play").replace("%mode%", name))
                .lore(core.messages().list("modelobby.item.play-lore")).hideAll().tag(ITEM + "play").build());
        inventory.setItem(1, Items.of(Material.COMPASS).name(core.messages().raw("modelobby.item.selector"))
                .lore(core.messages().list("modelobby.item.selector-lore")).hideAll().tag(ITEM + "selector").build());
        inventory.setItem(4, Items.of(Material.AMETHYST_SHARD).name(core.messages().raw("modelobby.item.cosmetics"))
                .lore(core.messages().list("modelobby.item.cosmetics-lore")).hideAll().tag(ITEM + "cosmetics").build());
        inventory.setItem(8, Items.of(Material.RED_BED).name(core.messages().raw("modelobby.item.back"))
                .lore(core.messages().list("modelobby.item.back-lore")).hideAll().tag(ITEM + "back").build());
        inventory.setHeldItemSlot(0);
    }

    public static boolean isItem(ItemStack item) {
        String tag = Items.tagOf(item);
        return tag != null && tag.startsWith(ITEM);
    }

    public void clearItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isItem(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    private void use(Player player, ModeLobby lobby, String action) {
        switch (action) {
            case "play":
                play(player, lobby.mode);
                break;
            case "selector":
                core.menus().open(player, "selector");
                break;
            case "cosmetics":
                core.menus().cosmetics(player);
                break;
            case "main":
                core.menus().open(player, "selector");
                break;
            case "back":
                core.modes().sendAndReport(player, ModeService.LOBBY);
                break;
            default:
                break;
        }
    }

    private static boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    public void spawnAll() {
        for (ModeLobby lobby : lobbies.values()) {
            for (Holo holo : lobby.holograms) {
                spawn(lobby, holo);
            }
        }
    }

    public void despawn() {
        for (ModeLobby lobby : lobbies.values()) {
            for (Holo holo : lobby.holograms) {
                if (holo.display != null) {
                    holo.display.remove();
                }
                if (holo.hitbox != null) {
                    holo.hitbox.remove();
                }
                holo.display = null;
                holo.hitbox = null;
            }
            if (lobby.world != null) {
                for (Entity entity : lobby.world.getEntities()) {
                    if (entity.getScoreboardTags().contains(TAG)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    private void spawn(ModeLobby lobby, Holo holo) {
        World world = holo.location.getWorld();
        if (world == null || !world.isChunkLoaded(holo.location.getBlockX() >> 4, holo.location.getBlockZ() >> 4)) {
            return;
        }
        if (holo.display != null) {
            holo.display.remove();
        }
        TextDisplay display = world.spawn(holo.location, TextDisplay.class);
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(110, 8, 18, 30));
        display.setShadowed(true);
        display.setLineWidth(260);
        holo.display = display;
        holo.shown = "";
        update(holo);
        if (!holo.action.isEmpty()) {
            if (holo.hitbox != null) {
                holo.hitbox.remove();
            }
            Interaction box = world.spawn(holo.location.clone().add(0, -0.05, 0), Interaction.class);
            box.setPersistent(false);
            box.addScoreboardTag(TAG);
            box.setInteractionWidth(2.0f);
            box.setInteractionHeight((float) (Math.max(1, holo.shown.split("\n", -1).length) * 0.27 + 0.15));
            box.setResponsive(true);
            holo.hitbox = box;
        }
    }

    private void update(Holo holo) {
        List<String> lines = new ArrayList<>(holo.lines);
        if (!holo.board.isEmpty()) {
            lines.addAll(core.leaderboards().lines(holo.board));
        }
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(core.boards().global(line));
        }
        String value = Text.color(text.toString());
        if (!value.equals(holo.shown)) {
            holo.display.setText(value);
            holo.shown = value;
        }
    }

    public void tick() {
        for (ModeLobby lobby : lobbies.values()) {
            for (Holo holo : lobby.holograms) {
                if (holo.display == null || !holo.display.isValid() || (!holo.action.isEmpty() && (holo.hitbox == null || !holo.hitbox.isValid()))) {
                    spawn(lobby, holo);
                    continue;
                }
                update(holo);
            }
        }
    }

    private Holo holoOf(Entity entity) {
        if (!(entity instanceof Interaction) || !entity.getScoreboardTags().contains(TAG)) {
            return null;
        }
        ModeLobby lobby = lobbyOf(entity.getWorld());
        if (lobby == null) {
            return null;
        }
        for (Holo holo : lobby.holograms) {
            if (holo.hitbox != null && holo.hitbox.getUniqueId().equals(entity.getUniqueId())) {
                return holo;
            }
        }
        return null;
    }

    private void click(Player player, Holo holo) {
        long now = System.currentTimeMillis();
        Long last = clicked.get(player.getUniqueId());
        if (last != null && now - last < 700) {
            return;
        }
        clicked.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), "minecraft:ui.button.click", 0.6f, 1.4f);
        ModeLobby lobby = lobbyOf(player.getWorld());
        if (holo.action.equalsIgnoreCase("play") && lobby != null) {
            play(player, lobby.mode);
            return;
        }
        core.lobby().handleAction(player, holo.action);
    }

    /** A kattintható hologramok dobozai a szerver oldali kattintáshoz (LobbyClicks). */
    public void targets(World world, List<hu.icemine.core.lobby.LobbyClicks.Target> out) {
        ModeLobby lobby = lobbyOf(world);
        if (lobby == null) {
            return;
        }
        for (Holo holo : lobby.holograms) {
            if (holo.action.isEmpty()) {
                continue;
            }
            double height = Math.max(1, holo.shown.split("\n", -1).length) * 0.27 + 0.25;
            Location at = holo.location;
            out.add(new hu.icemine.core.lobby.LobbyClicks.Target(new org.bukkit.util.BoundingBox(at.getX() - 1.2, at.getY() - 0.15, at.getZ() - 1.2,
                    at.getX() + 1.2, at.getY() + height, at.getZ() + 1.2), player -> click(player, holo)));
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHoloRight(PlayerInteractEntityEvent event) {
        Holo holo = holoOf(event.getRightClicked());
        if (holo == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            click(event.getPlayer(), holo);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHoloLeft(EntityDamageByEntityEvent event) {
        Holo holo = holoOf(event.getEntity());
        if (holo == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player) {
            click((Player) event.getDamager(), holo);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ModeLobby lobby = lobbyOf(player.getWorld());
        if (lobby == null || core.lobbyClicks() != null && core.lobbyClicks().handled(event)) {
            return;
        }
        String tag = Items.tagOf(event.getItem());
        if (tag != null && tag.startsWith(ITEM)) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND && event.getAction() != Action.PHYSICAL) {
                use(player, lobby, tag.substring(ITEM.length()));
            }
            return;
        }
        if (!builder(player) && event.getClickedBlock() != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (event.getAction() == Action.PHYSICAL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        ModeLobby lobby = lobbyOf(to.getWorld());
        if (lobby == null) {
            return;
        }
        Player player = event.getPlayer();
        if (to.getY() < lobby.voidY) {
            core.guard().allowNext(player);
            player.teleport(lobby.spawn(player));
            player.setFallDistance(0f);
            return;
        }
        if (lobby.portal != null && lobby.portal.contains(to) && !builder(player)) {
            play(player, lobby.mode);
            Location back = lobby.spawn(player);
            if (lobby.playCommand.isEmpty() || player.getWorld() == lobby.world) {
                Bukkit.getScheduler().runTaskLater(core, () -> {
                    if (player.isOnline() && player.getWorld() == lobby.world && lobby.portal.contains(player.getLocation())) {
                        core.guard().allowNext(player);
                        player.teleport(back);
                    }
                }, 10L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (isLobbyWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isLobbyWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (isLobbyWorld(event.getBlock().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (isLobbyWorld(event.getBlock().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (isLobbyWorld(event.getEntity().getWorld()) && !(event.getEntity() instanceof Interaction)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                ModeLobby lobby = lobbyOf(player.getWorld());
                core.guard().allowNext(player);
                player.teleport(lobby.spawn(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFood(FoodLevelChangeEvent event) {
        if (isLobbyWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
            event.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLobbyWorld(event.getPlayer().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isLobbyWorld(event.getPlayer().getWorld()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!isLobbyWorld(player.getWorld()) || builder(player)) {
            return;
        }
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(true);
        } else if (isItem(event.getCurrentItem()) || isItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHanging(HangingBreakEvent event) {
        if (isLobbyWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clicked.remove(event.getPlayer().getUniqueId());
        entered.remove(event.getPlayer().getUniqueId());
    }
}
