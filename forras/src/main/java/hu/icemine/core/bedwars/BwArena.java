package hu.icemine.core.bedwars;

import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Egy BedWars aréna: egy pálya egy példánya, rajta egyszerre egy meccs megy. */
public final class BwArena {

    public static final String TAG = "icemine_bw";

    public enum State {
        WAITING, STARTING, PLAYING, ENDING
    }

    private final Bedwars plugin;
    public final String id;
    public final BwMode mode;
    public final BwMap.Theme map;
    public final World world;
    public final int cx;
    public final int cy;
    public final int cz;
    public final int extent;
    private final Map<UUID, BwPlayer> players = new LinkedHashMap<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<TeamColor, BwTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, BwBoard> boards = new HashMap<>();
    private final Set<Long> placed = new HashSet<>();
    private final List<Generator> generators = new ArrayList<>();
    private final List<Location> protectedSpots = new ArrayList<>();
    private final List<Location> shops = new ArrayList<>();
    private final List<Location> upgrades = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Random random = new Random();
    private Location lobby;
    private Location spectator;
    private State state = State.WAITING;
    private boolean ready;
    private int countdown = -1;
    private int elapsed;
    private long ticks;
    private int endTimer;
    private TeamColor winner;

    public BwArena(Bedwars plugin, String id, BwMode mode, BwMap.Theme map, World world, int cx, int cy, int cz) {
        this.plugin = plugin;
        this.id = id;
        this.mode = mode;
        this.map = map;
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.extent = BwMap.extent(map.teams);
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < mode.teams && i < colors.length; i++) {
            teams.put(colors[i], new BwTeam(colors[i]));
        }
        BwMap build = new BwMap((x, y, z, data) -> {
        }, cx, cy, cz, map);
        build.build();
        for (BwMap.Spot spot : build.spots()) {
            Location at = new Location(world, cx + spot.x, cy + spot.y, cz + spot.z, spot.yaw, 0);
            BwTeam team = spot.team >= 0 && spot.team < colors.length ? teams.get(colors[spot.team]) : null;
            switch (spot.kind) {
                case "lobby":
                    lobby = at;
                    break;
                case "spectator":
                    spectator = at;
                    break;
                case "spawn":
                    if (team != null) {
                        team.spawn = at;
                    }
                    protectedSpots.add(at);
                    break;
                case "bed":
                    if (team != null) {
                        team.bedHead = at;
                        team.bedFacing = spot.facing;
                    }
                    break;
                case "bedfoot":
                    if (team != null) {
                        team.bedFoot = at;
                    }
                    break;
                case "base":
                    if (team != null) {
                        team.base = at;
                    }
                    break;
                case "shop":
                    shops.add(at);
                    protectedSpots.add(at);
                    break;
                case "upgrade":
                    upgrades.add(at);
                    protectedSpots.add(at);
                    break;
                case "generator":
                    if (team != null) {
                        generators.add(new Generator(Generator.Type.IRON, at, team));
                        generators.add(new Generator(Generator.Type.GOLD, at, team));
                    }
                    protectedSpots.add(at);
                    break;
                case "diamond":
                    generators.add(new Generator(Generator.Type.DIAMOND, at, null));
                    protectedSpots.add(at);
                    break;
                case "emerald":
                    generators.add(new Generator(Generator.Type.EMERALD, at, null));
                    protectedSpots.add(at);
                    break;
                default:
                    break;
            }
        }
    }

    // ---- lekérdezések ----

    public State state() {
        return state;
    }

    public boolean ready() {
        return ready;
    }

    void setReady(boolean ready) {
        this.ready = ready;
    }

    public int size() {
        return players.size();
    }

    public int capacity() {
        return mode.capacity();
    }

    public boolean joinable() {
        return ready && (state == State.WAITING || state == State.STARTING) && players.size() < capacity();
    }

    public BwPlayer data(Player player) {
        return players.get(player.getUniqueId());
    }

    public BwTeam team(TeamColor color) {
        return color == null ? null : teams.get(color);
    }

    public Collection<BwTeam> teams() {
        return teams.values();
    }

    public Collection<BwPlayer> players() {
        return players.values();
    }

    public boolean watching(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean contains(Location location) {
        return location.getWorld() == world && Math.abs(location.getX() - cx) <= extent && Math.abs(location.getZ() - cz) <= extent;
    }

    public boolean contains(Block block) {
        return contains(block.getLocation());
    }

    public Location lobby() {
        return lobby;
    }

    public Location spectator() {
        return spectator;
    }

    public String mapName() {
        return map.display;
    }

    private BwSettings settings() {
        return plugin.settings();
    }

    // ---- üzenetek ----

    private String msg(String key, String... pairs) {
        return plugin.core().messages().get("bedwars." + key, pairs);
    }

    private String prefixed(String key, String... pairs) {
        return plugin.core().messages().prefixed("bedwars." + key, pairs);
    }

    private List<String> list(String key, String... pairs) {
        return plugin.core().messages().list("bedwars." + key, pairs);
    }

    void send(CommandSender sender, String key, String... pairs) {
        plugin.core().messages().send(sender, "bedwars." + key, pairs);
    }

    private void title(Player player, String title, String subtitle, int in, int stay, int out, String... pairs) {
        player.sendTitle(title == null ? "" : msg(title, pairs), subtitle == null ? "" : msg(subtitle, pairs), in, stay, out);
    }

    private List<Player> audience() {
        List<Player> result = new ArrayList<>();
        for (UUID id : players.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        for (UUID id : spectators) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    public void broadcast(String key, String... pairs) {
        String text = prefixed(key, pairs);
        for (Player player : audience()) {
            player.sendMessage(text);
        }
    }

    private void broadcastRaw(String text) {
        for (Player player : audience()) {
            player.sendMessage(Text.color(text));
        }
    }

    public void teamMessage(BwTeam team, String key, String... pairs) {
        String text = prefixed(key, pairs);
        for (UUID id : team.members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(text);
            }
        }
    }

    private void sound(String sound, float pitch) {
        for (Player player : audience()) {
            player.playSound(player.getLocation(), sound, 1.0F, pitch);
        }
    }

    // ---- játékos kezelés ----

    static void resetPlayer(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        try {
            player.setHealth(player.getMaxHealth());
        } catch (IllegalArgumentException ignored) {
        }
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setExp(0.0F);
        player.setLevel(0);
        player.setVelocity(new Vector());
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    private void teleport(Player player, Location location) {
        plugin.core().guard().allowNext(player);
        player.teleport(location);
    }

    public boolean join(Player player) {
        if (!joinable()) {
            send(player, state == State.PLAYING || state == State.ENDING ? "join.running" : "join.full", "%arena%", mapName());
            return false;
        }
        BwPlayer data = new BwPlayer(player.getUniqueId(), player.getName());
        players.put(player.getUniqueId(), data);
        plugin.track(player, this);
        teleport(player, lobby);
        resetPlayer(player, GameMode.ADVENTURE);
        giveLobbyItems(player);
        boards.put(player.getUniqueId(), new BwBoard(player, plugin.core().messages().raw("bedwars.board.title")));
        broadcast("join.joined", "%player%", player.getName(), "%count%", String.valueOf(players.size()), "%max%", String.valueOf(capacity()));
        updateBoards();
        return true;
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().setItem(0, BwItems.tagged(BwMenus.icon(Material.WHITE_WOOL, msg("item.team"), list("item.team-lore")), "menu:team"));
        player.getInventory().setItem(8, BwItems.tagged(BwMenus.icon(Material.RED_BED, msg("item.leave"), list("item.leave-lore")), "menu:leave"));
        player.getInventory().setHeldItemSlot(0);
    }

    private void giveEndItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, BwItems.tagged(BwMenus.icon(Material.PAPER, msg("item.again"), list("item.again-lore")), "menu:again"));
        player.getInventory().setItem(8, BwItems.tagged(BwMenus.icon(Material.RED_BED, msg("item.leave"), list("item.leave-lore")), "menu:leave"));
    }

    /** Kilépés az arénából. home = hazaküldjük-e a BedWars lobbyba (false, ha már úton van máshova). */
    public void leave(Player player, boolean home) {
        if (spectators.remove(player.getUniqueId())) {
            plugin.untrack(player);
            boards.remove(player.getUniqueId());
            cleanUp(player, home);
            return;
        }
        BwPlayer data = players.remove(player.getUniqueId());
        plugin.untrack(player);
        boards.remove(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (data.team != null) {
            teams.get(data.team).members.remove(player.getUniqueId());
        }
        for (BwBoard board : boards.values()) {
            board.color(player.getName(), null);
        }
        cleanUp(player, home);
        if (state == State.WAITING || state == State.STARTING) {
            broadcast("join.left", "%player%", player.getName(), "%count%", String.valueOf(players.size()), "%max%", String.valueOf(capacity()));
            if (state == State.STARTING && players.size() < mode.minPlayers) {
                state = State.WAITING;
                countdown = -1;
                broadcast("start.cancelled");
            }
            updateBoards();
            return;
        }
        if (state == State.PLAYING && data.team != null) {
            // aki játék közben lép ki (vagy már kiesett és most megy el), annak ez vereség, a meccs végén már nem számoljuk
            plugin.stat(player.getUniqueId(), "bedwars.games", 1);
            plugin.stat(player.getUniqueId(), "bedwars." + mode.id + ".games", 1);
            plugin.stat(player.getUniqueId(), "bedwars.losses", 1);
            plugin.resetStreak(player.getUniqueId());
            broadcast("game.quit", "%player%", data.team.chat + player.getName());
            checkTeam(teams.get(data.team));
            checkWin();
        }
        if (players.isEmpty()) {
            kickAll();
        }
    }

    /**
     * A BedWars tárgyak, effektek és a scoreboard levétele kilépéskor. Akkor is, ha a játékos máshova ment (pl. /lobby),
     * hogy ne vigye magával a gyapjút és a kardot. home = küldjük-e a BedWars lobbyba.
     */
    private void cleanUp(Player player, boolean home) {
        if (!player.isOnline()) {
            return;
        }
        resetPlayer(player, GameMode.ADVENTURE);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (home) {
            teleport(player, plugin.returnLocation(player));
        }
    }

    /** Néző beállítása egy futó meccshez (/bw nez). */
    public boolean spectate(Player player) {
        if (state != State.PLAYING && state != State.ENDING) {
            return false;
        }
        spectators.add(player.getUniqueId());
        plugin.track(player, this);
        teleport(player, spectator);
        resetPlayer(player, GameMode.SPECTATOR);
        BwBoard board = new BwBoard(player, plugin.core().messages().raw("bedwars.board.title"));
        for (BwPlayer other : players.values()) {
            board.color(other.name, other.team);
        }
        boards.put(player.getUniqueId(), board);
        send(player, "spectate.started", "%arena%", mapName());
        updateBoards();
        return true;
    }

    /** Mindenkit (játékosok, nézők) hazaküld statisztika nélkül (leállításnál, újratöltésnél), és alaphelyzetbe teszi az arénát. */
    public void kickAll() {
        for (Player player : audience()) {
            plugin.untrack(player);
            if (player.isOnline()) {
                resetPlayer(player, GameMode.ADVENTURE);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                teleport(player, plugin.returnLocation(player));
            }
        }
        reset();
    }

    public void openTeamMenu(Player player) {
        BwPlayer data = data(player);
        if (data == null || state == State.PLAYING || state == State.ENDING) {
            return;
        }
        int rows = teams.size() > 4 ? 2 : 1;
        hu.icemine.core.menu.Menu menu = new hu.icemine.core.menu.Menu("bw-team", rows, plugin.core().messages().raw("bedwars.team.menu-title"));
        int index = 0;
        for (BwTeam team : teams.values()) {
            TeamColor color = team.color;
            List<String> lore = new ArrayList<>();
            int count = 0;
            for (BwPlayer other : players.values()) {
                if (other.wanted == color) {
                    count++;
                    lore.add(Text.color("&7- &f" + other.name));
                }
            }
            lore.add(0, Text.color("&7Létszám: &f" + count + "/" + mode.teamSize));
            lore.add("");
            lore.add(msg(data.wanted == color ? "team.selected" : (count >= mode.teamSize ? "team.full-lore" : "team.click")));
            ItemStack icon = BwMenus.icon(color.wool(), color.chat + "&l" + color.display, lore);
            icon.setAmount(Math.max(1, count));
            int full = count;
            int slot = rows == 1 ? 1 + index * 2 : (index < 4 ? 1 + index * 2 : 9 + 1 + (index - 4) * 2);
            menu.set(slot, icon, clicker -> {
                if (data.wanted == color) {
                    return;
                }
                if (full >= mode.teamSize) {
                    send(clicker, "team.full", "%team%", color.colored());
                    return;
                }
                data.wanted = color;
                send(clicker, "team.chosen", "%team%", color.colored());
                clicker.playSound(clicker.getLocation(), "minecraft:ui.button.click", 0.8F, 1.2F);
                clicker.closeInventory();
                updateBoards();
            });
            index++;
        }
        menu.open(player);
    }

    public void forceStart(CommandSender sender) {
        if (state != State.WAITING && state != State.STARTING) {
            send(sender, "admin.not-waiting");
        } else if (players.size() < 2) {
            send(sender, "admin.need-two");
        } else {
            start();
        }
    }

    public void forceStop() {
        if (state == State.PLAYING) {
            end(null);
            return;
        }
        for (UUID id : new ArrayList<>(players.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                leave(player, true);
            }
        }
        reset();
    }

    // ---- időzítés ----

    /** 5 tickenként. */
    public void tick() {
        ticks += 5;
        if (state == State.PLAYING) {
            tickGenerators(5);
        }
        if (ticks % 20 == 0) {
            second();
        }
    }

    /** 2 tickenként: a gyémánt és smaragd generátor forgó blokkja. */
    public void spin() {
        for (Generator generator : generators) {
            if (generator.spinner != null && generator.spinner.isValid()) {
                generator.spin = (generator.spin + 6.0F) % 360.0F;
                generator.spinner.setRotation(generator.spin, 0.0F);
            }
        }
    }

    private void second() {
        switch (state) {
            case WAITING:
                if (players.size() >= mode.minPlayers) {
                    state = State.STARTING;
                    countdown = settings().countdown;
                    broadcast("start.countdown", "%seconds%", String.valueOf(countdown));
                }
                updateBoards();
                break;
            case STARTING:
                if (players.size() >= capacity() && countdown > settings().fullCountdown) {
                    countdown = settings().fullCountdown;
                    broadcast("start.full", "%seconds%", String.valueOf(countdown));
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                    break;
                }
                if (countdown <= 5 || countdown == 10 || countdown == 20) {
                    broadcast("start.countdown", "%seconds%", String.valueOf(countdown));
                    sound("minecraft:block.note_block.hat", 1.2F);
                    if (countdown <= 5) {
                        for (Player player : audience()) {
                            player.sendTitle(Text.color((countdown <= 3 ? "&c&l" : "&e&l") + countdown), "", 0, 22, 2);
                        }
                    }
                }
                updateBoards();
                break;
            case PLAYING:
                elapsed++;
                timeline();
                if (state != State.PLAYING) {
                    return;
                }
                respawns();
                effects();
                traps();
                updateGeneratorLabels();
                updateBoards();
                break;
            case ENDING:
                endTimer--;
                fireworks();
                if (endTimer <= 0) {
                    for (UUID id : new ArrayList<>(players.keySet())) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null) {
                            leave(player, true);
                        }
                    }
                    for (UUID id : new ArrayList<>(spectators)) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null) {
                            leave(player, true);
                        }
                    }
                    reset();
                }
                break;
            default:
                break;
        }
    }

    private void start() {
        Map<UUID, TeamColor> wanted = new LinkedHashMap<>();
        for (BwPlayer data : players.values()) {
            wanted.put(data.id, data.wanted);
        }
        Map<UUID, TeamColor> assigned = Balance.assign(wanted, new ArrayList<>(teams.keySet()), mode.teamSize, random);
        for (BwTeam team : teams.values()) {
            team.reset();
        }
        for (Map.Entry<UUID, TeamColor> entry : assigned.entrySet()) {
            BwPlayer data = players.get(entry.getKey());
            data.team = entry.getValue();
            teams.get(entry.getValue()).members.add(entry.getKey());
        }
        state = State.PLAYING;
        elapsed = 0;
        countdown = -1;
        winner = null;
        placed.clear();
        restoreBeds();
        for (BwTeam team : teams.values()) {
            if (team.members.isEmpty()) {
                team.bedAlive = false;
                team.out = true;
                removeBed(team);
            }
        }
        for (Generator generator : generators) {
            generator.tier = 1;
            boolean base = generator.type == Generator.Type.IRON || generator.type == Generator.Type.GOLD;
            generator.timer = base ? 40 : interval(generator);
            generator.emeraldTimer = settings().teamEmerald;
        }
        spawnVillagers();
        spawnGeneratorLabels();
        for (BwPlayer data : players.values()) {
            Player player = Bukkit.getPlayer(data.id);
            if (player == null || data.team == null) {
                continue;
            }
            BwTeam team = teams.get(data.team);
            teleport(player, team.spawn);
            resetPlayer(player, GameMode.SURVIVAL);
            BwItems.spawnKit(player, data, team);
            player.sendTitle(msg("start.title"), msg("start.subtitle", "%team%", team.color.colored()), 0, 40, 10);
            send(player, "start.started", "%team%", team.color.colored());
            for (String line : list("start.tips")) {
                player.sendMessage(line);
            }
        }
        for (BwBoard board : boards.values()) {
            for (BwPlayer data : players.values()) {
                board.color(data.name, data.team);
            }
        }
        sound("minecraft:entity.ender_dragon.growl", 1.2F);
        updateBoards();
    }

    private void spawnVillagers() {
        for (Location at : shops) {
            spawnKeeper(at, true);
        }
        for (Location at : upgrades) {
            spawnKeeper(at, false);
        }
    }

    private void spawnKeeper(Location at, boolean shop) {
        Location location = at.clone();
        Villager villager = world.spawn(location, Villager.class);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setPersistent(false);
        villager.setRemoveWhenFarAway(false);
        villager.addScoreboardTag(TAG);
        villager.addScoreboardTag(TAG + (shop ? ":shop" : ":upgrade"));
        villager.setRotation(location.getYaw(), 0.0F);
        try {
            villager.setProfession(shop ? Villager.Profession.WEAPONSMITH : Villager.Profession.LIBRARIAN);
            villager.setVillagerLevel(5);
        } catch (Throwable ignored) {
        }
        spawned.add(villager);
        TextDisplay label = world.spawn(location.clone().add(0.0, 2.25, 0.0), TextDisplay.class);
        label.setText(msg(shop ? "shop.label" : "upgrade.label"));
        label.setBillboard(Display.Billboard.CENTER);
        label.setPersistent(false);
        label.setDefaultBackground(false);
        label.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        label.setShadowed(true);
        label.addScoreboardTag(TAG);
        spawned.add(label);
    }

    private void spawnGeneratorLabels() {
        for (Generator generator : generators) {
            if (generator.type != Generator.Type.DIAMOND && generator.type != Generator.Type.EMERALD) {
                continue;
            }
            TextDisplay label = world.spawn(generator.location.clone().add(0.0, 3.2, 0.0), TextDisplay.class);
            label.setBillboard(Display.Billboard.CENTER);
            label.setPersistent(false);
            label.setDefaultBackground(false);
            label.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            label.setShadowed(true);
            label.addScoreboardTag(TAG);
            generator.label = label;
            spawned.add(label);
            ArmorStand stand = world.spawn(generator.location.clone().add(0.0, 0.9, 0.0), ArmorStand.class);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
            stand.addScoreboardTag(TAG);
            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(generator.type == Generator.Type.DIAMOND ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK));
            }
            generator.spinner = stand;
            spawned.add(stand);
        }
        updateGeneratorLabels();
    }

    private int interval(Generator generator) {
        double speed = 1.0;
        if (generator.team != null) {
            speed = generator.team.forgeSpeed() * mode.generatorSpeed;
        }
        BwSettings s = settings();
        return Generator.interval(generator.type, generator.tier, speed, s.diamondTiers, s.emeraldTiers, s.ironInterval, s.goldInterval);
    }

    private void tickGenerators(int step) {
        for (Generator generator : generators) {
            if (generator.team != null) {
                if (generator.team.members.isEmpty()) {
                    continue;
                }
                if (generator.type == Generator.Type.IRON && generator.team.forge >= 3) {
                    generator.emeraldTimer -= step;
                    if (generator.emeraldTimer <= 0) {
                        generator.emeraldTimer = settings().teamEmerald;
                        drop(generator.location, Generator.Type.EMERALD);
                    }
                }
            }
            generator.timer -= step;
            if (generator.timer <= 0) {
                generator.timer = interval(generator);
                drop(generator.location, generator.type);
            }
        }
    }

    private void drop(Location location, Generator.Type type) {
        Material material = type.item();
        int count = 0;
        for (Entity entity : world.getNearbyEntities(location, 1.6, 1.6, 1.6)) {
            if (entity instanceof Item item && item.getItemStack().getType() == material) {
                count += item.getItemStack().getAmount();
            }
        }
        if (count >= type.max) {
            return;
        }
        Item item = world.dropItem(location.clone().add(0.0, 0.2, 0.0), new ItemStack(material));
        item.setVelocity(new Vector());
        item.setPickupDelay(0);
    }

    private void updateGeneratorLabels() {
        for (Generator generator : generators) {
            if (generator.label == null || !generator.label.isValid()) {
                continue;
            }
            boolean diamond = generator.type == Generator.Type.DIAMOND;
            int seconds = Math.max(1, (generator.timer + 19) / 20);
            generator.label.setText(msg(diamond ? "generator.diamond" : "generator.emerald", "%tier%", ShopMenus.roman(generator.tier),
                    "%seconds%", String.valueOf(seconds)));
        }
    }

    private void timeline() {
        for (BwSettings.Event event : settings().timeline) {
            if (event.at != elapsed) {
                continue;
            }
            switch (event.type) {
                case "diamond-2":
                case "diamond-3":
                    upgradeGenerators(Generator.Type.DIAMOND, event.type.endsWith("2") ? 2 : 3, event.name);
                    break;
                case "emerald-2":
                case "emerald-3":
                    upgradeGenerators(Generator.Type.EMERALD, event.type.endsWith("2") ? 2 : 3, event.name);
                    break;
                case "beds":
                    for (BwTeam team : teams.values()) {
                        if (team.bedAlive) {
                            team.bedAlive = false;
                            removeBed(team);
                        }
                    }
                    broadcast("event.beds");
                    sound("minecraft:entity.wither.death", 1.0F);
                    for (Player player : audience()) {
                        title(player, "event.beds-title", "event.beds-subtitle", 5, 50, 10);
                    }
                    break;
                case "end":
                    broadcast("event.time-up");
                    end(null);
                    return;
                default:
                    break;
            }
        }
    }

    private void upgradeGenerators(Generator.Type type, int tier, String name) {
        for (Generator generator : generators) {
            if (generator.type == type) {
                generator.tier = tier;
            }
        }
        broadcast("event.generator", "%name%", name);
        sound("minecraft:block.note_block.chime", 1.4F);
    }

    public BwSettings.Event nextEvent() {
        return settings().next(elapsed);
    }

    private void respawns() {
        for (BwPlayer data : players.values()) {
            if (!data.respawning() || data.eliminated) {
                continue;
            }
            Player player = Bukkit.getPlayer(data.id);
            if (player == null) {
                continue;
            }
            if (data.respawnSeconds > 0) {
                player.sendTitle(msg("death.title"), msg("death.subtitle", "%seconds%", String.valueOf(data.respawnSeconds)), 0, 25, 0);
                data.respawnSeconds--;
                continue;
            }
            data.respawnSeconds = -1;
            BwTeam team = teams.get(data.team);
            teleport(player, team.spawn);
            resetPlayer(player, GameMode.SURVIVAL);
            BwItems.spawnKit(player, data, team);
            applyEffects(player, team);
            player.sendTitle(msg("death.respawned"), "", 0, 25, 8);
        }
    }

    public void applyUpgrades(BwTeam team) {
        for (UUID id : team.members) {
            Player player = Bukkit.getPlayer(id);
            BwPlayer data = players.get(id);
            if (player != null && data != null && data.playing()) {
                BwItems.armor(player, data, team);
                BwItems.refreshSharpness(player, team);
                applyEffects(player, team);
            }
        }
    }

    private void applyEffects(Player player, BwTeam team) {
        if (team.haste > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, team.haste - 1, true, false));
        }
        if (team.healPool && team.base != null && player.getWorld() == world && player.getLocation().distanceSquared(team.base) <= 144.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
        }
    }

    private void effects() {
        for (BwPlayer data : players.values()) {
            if (!data.playing()) {
                continue;
            }
            Player player = Bukkit.getPlayer(data.id);
            if (player != null) {
                applyEffects(player, teams.get(data.team));
            }
        }
    }

    private static boolean nearBase(Location location, Location base, double radius) {
        double dx = location.getX() - base.getX();
        double dz = location.getZ() - base.getZ();
        double dy = location.getY() - base.getY();
        return dx * dx + dz * dz <= radius * radius && dy > -8 && dy < 14;
    }

    private void traps() {
        for (BwTeam team : teams.values()) {
            if (team.traps.isEmpty() || team.base == null || team.members.isEmpty() || ticks < team.trapReadyAt) {
                continue;
            }
            for (BwPlayer data : players.values()) {
                if (!data.playing() || data.team == team.color) {
                    continue;
                }
                Player intruder = Bukkit.getPlayer(data.id);
                if (intruder == null || !nearBase(intruder.getLocation(), team.base, BwMap.ISLAND)) {
                    continue;
                }
                Trap trap = team.traps.poll();
                team.trapReadyAt = ticks + settings().trapCooldown;
                trigger(team, trap, intruder, data);
                break;
            }
        }
    }

    private void trigger(BwTeam team, Trap trap, Player intruder, BwPlayer data) {
        String trapName = msg("trap." + trap.id() + ".name");
        switch (trap) {
            case VAKITO:
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0, false, true));
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 1, false, true));
                break;
            case ELLENTAMADAS:
                for (UUID id : team.members) {
                    Player member = Bukkit.getPlayer(id);
                    BwPlayer memberData = players.get(id);
                    if (member != null && memberData != null && memberData.playing() && member.getLocation().distanceSquared(team.base) <= 900) {
                        member.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1, false, true));
                        member.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 1, false, true));
                    }
                }
                break;
            case RIASZTO:
                intruder.removePotionEffect(PotionEffectType.INVISIBILITY);
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, true));
                break;
            case BANYASZ:
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 0, false, true));
                break;
            default:
                break;
        }
        send(intruder, "trap.caught", "%team%", team.color.colored(), "%trap%", trapName);
        intruder.playSound(intruder.getLocation(), "minecraft:entity.elder_guardian.curse", 0.6F, 1.4F);
        for (UUID id : team.members) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                title(member, "trap.title", "trap.subtitle", 0, 50, 10, "%player%", data.team.chat + intruder.getName(), "%trap%", trapName);
                member.playSound(member.getLocation(), "minecraft:block.note_block.pling", 1.0F, 0.5F);
            }
        }
    }

    private static String time(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    private int alive(BwTeam team) {
        int count = 0;
        for (UUID id : team.members) {
            BwPlayer data = players.get(id);
            if (data != null && !data.eliminated) {
                count++;
            }
        }
        return count;
    }

    public void updateBoards() {
        for (Map.Entry<UUID, BwBoard> entry : boards.entrySet()) {
            BwPlayer data = players.get(entry.getKey());
            List<String> lines = new ArrayList<>();
            if (state == State.WAITING || state == State.STARTING) {
                String status = state == State.STARTING ? msg("board.starting", "%seconds%", String.valueOf(countdown)) : msg("board.waiting");
                String team = data == null || data.wanted == null ? msg("board.random") : data.wanted.colored();
                lines.addAll(list("board.lobby", "%map%", mapName(), "%players%", String.valueOf(players.size()), "%max%", String.valueOf(capacity()),
                        "%status%", status, "%mode%", Text.color(mode.name), "%team%", team));
            } else {
                BwSettings.Event next = nextEvent();
                lines.add(next == null ? msg("board.no-event") : msg("board.event", "%event%", next.name, "%time%", time(next.at - elapsed)));
                lines.add("");
                for (BwTeam team : teams.values()) {
                    String mark;
                    if (team.bedAlive) {
                        mark = "&a✔";
                    } else if (alive(team) > 0) {
                        mark = "&e" + alive(team);
                    } else {
                        mark = "&c✘";
                    }
                    boolean you = data != null && team.color == data.team;
                    lines.add(team.color.chat + "&l" + team.color.letter + " &f" + team.color.display + ": " + mark + (you ? msg("board.you") : ""));
                }
                lines.add("");
                if (data != null) {
                    lines.add(msg("board.kills", "%kills%", String.valueOf(data.kills), "%finals%", String.valueOf(data.finals)));
                    lines.add(msg("board.beds", "%beds%", String.valueOf(data.beds)));
                } else {
                    lines.add(msg("board.spectating"));
                }
                lines.add("");
                lines.add(msg("board.footer"));
            }
            entry.getValue().lines(lines);
        }
    }

    // ---- ágyak ----

    public boolean isBed(Block block) {
        return block.getType().name().endsWith("_BED");
    }

    public BwTeam bedOwner(Block block) {
        for (BwTeam team : teams.values()) {
            if (team.isBed(block.getX(), block.getY(), block.getZ())) {
                return team;
            }
        }
        return null;
    }

    public void breakBed(Player player, BwTeam team) {
        BwPlayer data = data(player);
        if (data == null || !team.bedAlive) {
            return;
        }
        team.bedAlive = false;
        removeBed(team);
        data.beds++;
        plugin.stat(player.getUniqueId(), "bedwars.beds", 1);
        plugin.reward(player, settings().rewardBed, "bedwars:bed");
        plugin.killEffect(player, team.bedHead);
        broadcastRaw("");
        broadcast("bed.destroyed", "%team%", team.color.colored(), "%player%", data.team.chat + player.getName());
        broadcastRaw("");
        for (Player other : audience()) {
            BwPlayer otherData = players.get(other.getUniqueId());
            if (otherData != null && otherData.team == team.color) {
                title(other, "bed.title", "bed.subtitle", 5, 50, 10);
                other.playSound(other.getLocation(), "minecraft:entity.wither.death", 1.0F, 1.0F);
            } else {
                other.playSound(other.getLocation(), "minecraft:entity.ender_dragon.growl", 0.8F, 1.2F);
            }
        }
        updateBoards();
    }

    private void removeBed(BwTeam team) {
        if (team.bedHead != null) {
            team.bedHead.getBlock().setType(Material.AIR, false);
        }
        if (team.bedFoot != null) {
            team.bedFoot.getBlock().setType(Material.AIR, false);
        }
    }

    private void restoreBeds() {
        for (BwTeam team : teams.values()) {
            if (team.bedHead == null || team.bedFoot == null) {
                continue;
            }
            String base = "minecraft:" + team.color.block + "_bed[facing=" + team.bedFacing + ",part=";
            team.bedFoot.getBlock().setBlockData(Bukkit.createBlockData(base + "foot]"), false);
            team.bedHead.getBlock().setBlockData(Bukkit.createBlockData(base + "head]"), false);
        }
    }

    // ---- építés ----

    public boolean placed(Block block) {
        return placed.contains(Keys.of(block.getX(), block.getY(), block.getZ()));
    }

    public void markPlaced(Block block) {
        placed.add(Keys.of(block.getX(), block.getY(), block.getZ()));
    }

    public void unmark(Block block) {
        placed.remove(Keys.of(block.getX(), block.getY(), block.getZ()));
    }

    /** Ha nem lehet ide építeni, az üzenet kulcsa (bedwars.build.*), különben null. */
    public String placeProblem(Block block) {
        if (!contains(block)) {
            return "build.outside";
        }
        if (block.getY() > cy + settings().buildLimit || block.getY() < cy - 20) {
            return "build.height";
        }
        for (Location spot : protectedSpots) {
            double dx = block.getX() + 0.5 - spot.getX();
            double dz = block.getZ() + 0.5 - spot.getZ();
            double dy = block.getY() - spot.getBlockY();
            if (dx * dx + dz * dz <= 6.25 && dy >= -1 && dy <= 3) {
                return "build.protected";
            }
        }
        return null;
    }

    // ---- halál, győzelem ----

    public Player resolveKiller(BwPlayer victim, Player direct) {
        if (direct != null) {
            return direct;
        }
        if (victim.lastDamager != null && System.currentTimeMillis() - victim.lastDamageAt < 15000L) {
            Player last = Bukkit.getPlayer(victim.lastDamager);
            BwPlayer lastData = last == null ? null : data(last);
            if (lastData != null && lastData.team != victim.team) {
                return last;
            }
        }
        return null;
    }

    public void die(Player player, Player direct, String cause) {
        BwPlayer data = data(player);
        if (data == null || !data.playing() || state != State.PLAYING) {
            return;
        }
        Player killer = resolveKiller(data, direct);
        BwPlayer killerData = killer != null && !killer.equals(player) ? data(killer) : null;
        if (killerData == null) {
            killer = null;
        }
        BwTeam team = teams.get(data.team);
        boolean finalKill = !team.bedAlive;
        if (killer != null) {
            transferResources(player, killer);
        }
        String victimName = data.team.chat + player.getName();
        String key = killer == null ? "kill.self-" + cause : "kill." + cause;
        String text = prefixed(key, "%victim%", victimName, "%killer%", killer == null ? "" : killerData.team.chat + killer.getName());
        if (finalKill) {
            text = text + msg("kill.final");
        }
        for (Player other : audience()) {
            other.sendMessage(text);
        }
        data.died();
        data.lastDamager = null;
        plugin.stat(player.getUniqueId(), "bedwars.deaths", 1);
        if (finalKill) {
            plugin.stat(player.getUniqueId(), "bedwars.final_deaths", 1);
        }
        if (killer != null) {
            if (finalKill) {
                killerData.finals++;
                plugin.stat(killer.getUniqueId(), "bedwars.final_kills", 1);
                plugin.reward(killer, settings().rewardFinal, "bedwars:final");
            } else {
                killerData.kills++;
                plugin.stat(killer.getUniqueId(), "bedwars.kills", 1);
                plugin.reward(killer, settings().rewardKill, "bedwars:kill");
            }
            killer.playSound(killer.getLocation(), "minecraft:entity.experience_orb.pickup", 1.0F, 1.2F);
            plugin.killEffect(killer, player.getLocation());
        }
        resetPlayer(player, GameMode.SPECTATOR);
        teleport(player, spectator);
        if (finalKill) {
            data.eliminated = true;
            data.respawnSeconds = -1;
            title(player, "death.final-title", "death.final-subtitle", 5, 60, 10);
            send(player, "death.eliminated");
            checkTeam(team);
            checkWin();
        } else {
            data.respawnSeconds = settings().respawn;
            player.sendTitle(msg("death.title"), msg("death.subtitle", "%seconds%", String.valueOf(data.respawnSeconds)), 0, 25, 0);
        }
        updateBoards();
    }

    private void transferResources(Player from, Player to) {
        List<String> parts = new ArrayList<>();
        for (Generator.Type type : Generator.Type.values()) {
            int amount = BwItems.count(from, type.item());
            if (amount > 0) {
                BwItems.give(to, new ItemStack(type.item(), amount));
                parts.add(Shop.currencyColor(type) + "+" + amount + " " + Shop.currencyName(type));
            }
        }
        if (!parts.isEmpty()) {
            to.sendMessage(Text.color(String.join("&7, ", parts)));
        }
    }

    private void checkTeam(BwTeam team) {
        if (team.out || alive(team) > 0) {
            return;
        }
        team.out = true;
        if (team.bedAlive) {
            team.bedAlive = false;
            removeBed(team);
        }
        broadcastRaw("");
        broadcast("team.eliminated", "%team%", team.color.colored());
        broadcastRaw("");
    }

    private void checkWin() {
        if (state != State.PLAYING) {
            return;
        }
        List<TeamColor> left = new ArrayList<>();
        for (BwTeam team : teams.values()) {
            if (alive(team) > 0) {
                left.add(team.color);
            }
        }
        if (left.size() <= 1) {
            end(left.isEmpty() ? null : left.get(0));
        }
    }

    private void end(TeamColor won) {
        state = State.ENDING;
        winner = won;
        endTimer = settings().endSeconds;
        List<BwPlayer> ranking = new ArrayList<>(players.values());
        ranking.sort((a, b) -> Integer.compare(b.kills + b.finals * 2 + b.beds * 3, a.kills + a.finals * 2 + a.beds * 3));
        broadcastRaw("&8&m                                             ");
        broadcastRaw(won == null ? msg("end.draw") : msg("end.winner", "%team%", won.colored()));
        broadcastRaw("");
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            BwPlayer data = ranking.get(i);
            broadcastRaw(msg("end.rank", "%place%", String.valueOf(i + 1), "%player%", (data.team == null ? "&f" : data.team.chat) + data.name,
                    "%kills%", String.valueOf(data.kills), "%finals%", String.valueOf(data.finals), "%beds%", String.valueOf(data.beds)));
        }
        broadcastRaw("&8&m                                             ");
        for (BwPlayer data : players.values()) {
            Player player = Bukkit.getPlayer(data.id);
            boolean won2 = won != null && data.team == won;
            if (data.team != null) {
                plugin.stat(data.id, "bedwars.games", 1);
                plugin.stat(data.id, "bedwars." + mode.id + ".games", 1);
                if (won2) {
                    plugin.stat(data.id, "bedwars.wins", 1);
                    plugin.stat(data.id, "bedwars." + mode.id + ".wins", 1);
                    plugin.winStreak(data.id);
                } else {
                    plugin.stat(data.id, "bedwars.losses", 1);
                    plugin.resetStreak(data.id);
                }
            }
            if (player == null) {
                continue;
            }
            plugin.reward(player, settings().rewardPlay, "bedwars:play");
            if (won2) {
                plugin.reward(player, settings().rewardWin, "bedwars:win");
                title(player, "end.win-title", "end.win-subtitle", 5, 80, 20);
                player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 1.0F, 1.0F);
            } else {
                title(player, "end.lose-title", won == null ? "end.draw-subtitle" : "end.lose-subtitle", 5, 80, 20, "%team%", won == null ? "" : won.colored());
            }
            if (player.getGameMode() == GameMode.SURVIVAL) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
            giveEndItems(player);
        }
        updateBoards();
    }

    private void fireworks() {
        if (winner == null) {
            return;
        }
        for (UUID id : teams.get(winner).members) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || player.getWorld() != world) {
                continue;
            }
            Firework firework = world.spawn(player.getLocation().add(random.nextDouble() * 4 - 2, 1, random.nextDouble() * 4 - 2), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(winner.leather(), Color.WHITE)
                    .withFade(Color.AQUA).trail(true).flicker(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            firework.addScoreboardTag(TAG);
        }
    }

    /** Minden vissza alaphelyzetbe: lerakott blokkok el, ágyak vissza, entitások el. */
    public void reset() {
        for (long key : placed) {
            Block block = world.getBlockAt(Keys.x(key), Keys.y(key), Keys.z(key));
            if (!block.getType().isAir()) {
                block.setType(Material.AIR, false);
            }
        }
        placed.clear();
        restoreBeds();
        for (Entity entity : spawned) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        spawned.clear();
        clearEntities();
        for (Generator generator : generators) {
            generator.label = null;
            generator.spinner = null;
            generator.tier = 1;
        }
        for (BwTeam team : teams.values()) {
            team.reset();
        }
        for (UUID id : new ArrayList<>(players.keySet())) {
            plugin.untrackId(id);
        }
        for (UUID id : new ArrayList<>(spectators)) {
            plugin.untrackId(id);
        }
        state = State.WAITING;
        countdown = -1;
        elapsed = 0;
        winner = null;
        players.clear();
        spectators.clear();
        boards.clear();
    }

    public void clearEntities() {
        Location center = new Location(world, cx, cy + 10, cz);
        for (Entity entity : world.getNearbyEntities(center, extent, 70, extent)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    public boolean isKeeper(Entity entity, String kind) {
        return entity.getScoreboardTags().contains(TAG + ":" + kind) && contains(entity.getLocation());
    }
}
