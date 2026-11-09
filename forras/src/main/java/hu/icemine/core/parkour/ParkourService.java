package hu.icemine.core.parkour;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.data.ProfileService;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.mode.ModeService;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

public final class ParkourService implements Listener {

    private final IceMineCore core;
    private final Map<UUID, ParkourRun> runs = new HashMap<>();
    private final Set<UUID> internal = new HashSet<>();
    private final List<int[]> blocks = new ArrayList<>();
    private final Map<UUID, Set<Integer>> visible = new HashMap<>();
    private final Map<Integer, Integer> shown = new HashMap<>();
    private Location start;
    private double failY = -1000;
    private int fallDepth = 3;
    private int every = 14;
    private boolean enabled = true;
    private long minimum = 25000;
    private long maximum = 15 * 60000L;
    private String currency = "kristaly";
    private long rewardFirst = 500;
    private long rewardRecord = 100;
    private long rewardRepeat = 25;
    private long repeatCooldown = 12 * 3600000L;

    private static final Material[] COLORS = {
            Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL, Material.CYAN_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL,
            Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL
    };

    public ParkourService(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                stop(player, true);
            }
        }
        clearCourse();
        Values values = new Values("parkour.yml", config::get);
        enabled = values.bool("enabled", true);
        minimum = values.integer("min-time-ms", 25000, 1000, 600000);
        fallDepth = values.integer("fall-depth", 3, 2, 64);
        every = values.integer("checkpoint-every", 14, 0, 1000);
        maximum = values.integer("max-time-minutes", 15, 1, 120) * 60000L;
        currency = values.text("rewards.currency", "kristaly");
        rewardFirst = values.number("rewards.first", 500, 0, 1_000_000);
        rewardRecord = values.number("rewards.record", 100, 0, 1_000_000);
        rewardRepeat = values.number("rewards.repeat", 25, 0, 1_000_000);
        repeatCooldown = values.integer("rewards.repeat-cooldown-hours", 12, 0, 168) * 3600000L;
        if (core.economy().currency(currency) == null) {
            problems.add("parkour.yml -> rewards.currency: ismeretlen pénznem (" + currency + ")");
            currency = core.economy().defaultCurrency();
        }
        start = Locs.read(config.getConfigurationSection("course.start"), core.lobby().world());
        blocks.clear();
        for (String line : config.getStringList("course.blocks")) {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                blocks.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())});
            } catch (NumberFormatException exception) {
                problems.add("parkour.yml -> course.blocks: hibás sor (" + line + ")");
            }
        }
        failY = values.decimal("course.fail-y", -1000, -2048, 2048);
        if (enabled && (start == null || blocks.isEmpty())) {
            problems.add("parkour.yml -> course: nincs beállítva a start vagy a pálya (a lobby újraépítése beállítja: /icem admin rebuild)");
        }
        clearCourse();
        problems.addAll(values.problems());
    }

    public boolean ready() {
        return enabled && start != null && !blocks.isEmpty();
    }

    public boolean running(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    public boolean isInternal(Player player) {
        return internal.contains(player.getUniqueId());
    }

    private void teleport(Player player, Location location) {
        internal.add(player.getUniqueId());
        try {
            core.guard().allowNext(player);
            player.teleport(location);
        } finally {
            internal.remove(player.getUniqueId());
        }
    }

    public void teleportToStart(Player player) {
        if (!ready()) {
            core.messages().send(player, "parkour.not-ready");
            return;
        }
        if (!core.lobby().inLobby(player)) {
            if (core.modes().sendAndReport(player, ModeService.LOBBY)) {
                Bukkit.getScheduler().runTaskLater(core, () -> {
                    if (player.isOnline() && core.lobby().inLobby(player)) {
                        teleport(player, entry());
                    }
                }, 5L);
            }
            return;
        }
        stop(player, true);
        teleport(player, entry());
        core.messages().send(player, "parkour.go-to-start");
    }

    private Location entry() {
        double yaw = Math.toRadians(start.getYaw());
        Location location = start.clone().add(0.5 + Math.sin(yaw) * 2.0, 0.1, 0.5 - Math.cos(yaw) * 2.0);
        location.setYaw(start.getYaw());
        return location;
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private boolean on(Location at, Location point) {
        return point != null && at.getBlockX() == point.getBlockX() && at.getBlockZ() == point.getBlockZ()
                && at.getY() >= point.getBlockY() - 0.1 && at.getY() < point.getBlockY() + 1.5;
    }

    private boolean standingOn(Location at, int[] block) {
        double dx = at.getX() - (block[0] + 0.5);
        double dz = at.getZ() - (block[2] + 0.5);
        double dy = at.getY() - (block[1] + 1);
        return Math.abs(dx) <= 0.85 && Math.abs(dz) <= 0.85 && dy >= -0.05 && dy <= 0.6;
    }

    private Material material(int index) {
        if (index == blocks.size() - 1) {
            return Material.GOLD_BLOCK;
        }
        if (ParkourRun.isCheckpoint(index, blocks.size(), every)) {
            return Material.LIME_WOOL;
        }
        return COLORS[index % COLORS.length];
    }

    private void place(int index, boolean on) {
        int[] block = blocks.get(index);
        org.bukkit.block.Block target = core.lobby().world().getBlockAt(block[0], block[1], block[2]);
        if (on) {
            target.setType(material(index), false);
        } else if (target.getType().name().endsWith("_WOOL") || target.getType() == Material.GOLD_BLOCK) {
            target.setType(Material.AIR, false);
        }
    }

    private void show(UUID id, Set<Integer> wanted) {
        Set<Integer> current = visible.computeIfAbsent(id, key -> new HashSet<>());
        for (Integer index : new ArrayList<>(current)) {
            if (!wanted.contains(index)) {
                current.remove(index);
                int count = shown.getOrDefault(index, 0) - 1;
                if (count <= 0) {
                    shown.remove(index);
                    place(index, false);
                } else {
                    shown.put(index, count);
                }
            }
        }
        for (Integer index : wanted) {
            if (index < 0 || index >= blocks.size() || !current.add(index)) {
                continue;
            }
            int count = shown.getOrDefault(index, 0) + 1;
            shown.put(index, count);
            if (count == 1) {
                place(index, true);
            }
        }
        if (current.isEmpty()) {
            visible.remove(id);
        }
    }

    private void render(Player player, ParkourRun run) {
        Set<Integer> wanted = new HashSet<>();
        int current = run.current();
        if (current >= 0) {
            wanted.add(current);
        }
        if (current + 1 < blocks.size()) {
            wanted.add(current + 1);
        }
        show(player.getUniqueId(), wanted);
    }

    private void hideAll(UUID id) {
        show(id, new HashSet<>());
    }

    private void clearCourse() {
        visible.clear();
        shown.clear();
        if (core.lobby() == null || core.lobby().world() == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            place(i, false);
        }
    }

    public void shutdown() {
        runs.clear();
        clearCourse();
    }

    private void appear(Player player, int index) {
        if (index < 0 || index >= blocks.size()) {
            return;
        }
        int[] block = blocks.get(index);
        Location at = new Location(core.lobby().world(), block[0] + 0.5, block[1] + 0.7, block[2] + 0.5);
        player.spawnParticle(org.bukkit.Particle.CLOUD, at, 6, 0.25, 0.2, 0.25, 0.01);
        player.playSound(at, "minecraft:block.note_block.hat", 0.7f, 1.0f + (index % 12) * 0.08f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!ready()) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() != core.lobby().world()) {
            return;
        }
        Player player = event.getPlayer();
        ParkourRun run = runs.get(player.getUniqueId());
        boolean moved = from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
        if (moved && on(to, start)) {
            begin(player);
            return;
        }
        if (run == null) {
            return;
        }
        int current = run.current();
        double base = current >= 0 ? blocks.get(current)[1] + 1 : start.getY();
        if (to.getY() < failY || to.getY() < base - fallDepth) {
            fell(player);
            return;
        }
        int next = current + 1;
        if (next >= blocks.size() || !standingOn(to, blocks.get(next))) {
            return;
        }
        ParkourRun.Step step = run.land(next);
        if (step == ParkourRun.Step.NONE) {
            return;
        }
        render(player, run);
        if (step == ParkourRun.Step.FINISH) {
            complete(player, run);
            return;
        }
        appear(player, next + 1);
        if (step == ParkourRun.Step.CHECKPOINT) {
            core.messages().send(player, "parkour.checkpoint", "%number%", String.valueOf(run.checkpointsReached()),
                    "%total%", String.valueOf(run.checkpointTotal()), "%time%", Text.timer(run.elapsed(System.nanoTime())));
            player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.8f, 1.6f);
            int[] block = blocks.get(next);
            player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, new Location(core.lobby().world(), block[0] + 0.5, block[1] + 1.2, block[2] + 0.5),
                    15, 0.4, 0.3, 0.4, 0);
        }
    }

    private void begin(Player player) {
        if (!core.auth().isLoggedIn(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            core.messages().actionBar(player, "parkour.creative");
            return;
        }
        boolean restart = runs.containsKey(player.getUniqueId());
        player.setFlying(false);
        player.setAllowFlight(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        ParkourRun run = new ParkourRun(System.nanoTime(), blocks.size(), every);
        runs.put(player.getUniqueId(), run);
        render(player, run);
        appear(player, 0);
        giveItems(player);
        if (!restart) {
            core.messages().send(player, "parkour.started", "%blocks%", String.valueOf(blocks.size()));
        }
        player.playSound(player.getLocation(), "minecraft:block.note_block.bell", 0.8f, 1.2f);
    }

    private void giveItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(3, Items.of(Material.LIGHT_WEIGHTED_PRESSURE_PLATE).name(core.messages().raw("parkour.item.checkpoint")).tag("parkour:checkpoint").build());
        inventory.setItem(4, Items.of(Material.SNOWBALL).name(core.messages().raw("parkour.item.restart")).tag("parkour:restart").build());
        inventory.setItem(5, Items.of(Material.RED_BED).name(core.messages().raw("parkour.item.leave")).tag("parkour:leave").build());
        inventory.setHeldItemSlot(4);
    }

    public boolean isParkourItem(ItemStack item) {
        String tag = Items.tagOf(item);
        return tag != null && tag.startsWith("parkour:");
    }

    public void useItem(Player player, ItemStack item) {
        String tag = Items.tagOf(item);
        ParkourRun run = runs.get(player.getUniqueId());
        if (tag == null || run == null) {
            core.lobby().giveHotbar(player);
            return;
        }
        switch (tag) {
            case "parkour:checkpoint":
                run.back();
                toCheckpoint(player, run);
                break;
            case "parkour:restart":
                runs.remove(player.getUniqueId());
                hideAll(player.getUniqueId());
                teleport(player, entry());
                core.lobby().giveHotbar(player);
                core.messages().send(player, "parkour.restart");
                break;
            case "parkour:leave":
                stop(player, false);
                teleport(player, core.lobby().spawn(player));
                break;
            default:
                break;
        }
    }

    private void toCheckpoint(Player player, ParkourRun run) {
        render(player, run);
        Location target;
        if (run.current() < 0) {
            target = start.clone().add(0.5, 0.1, 0.5);
        } else {
            int[] block = blocks.get(run.current());
            target = new Location(core.lobby().world(), block[0] + 0.5, block[1] + 1.05, block[2] + 0.5);
            int[] next = run.current() + 1 < blocks.size() ? blocks.get(run.current() + 1) : null;
            if (next != null) {
                target.setYaw((float) Math.toDegrees(Math.atan2(-(next[0] - block[0]), next[2] - block[2])));
            }
        }
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFallDistance(0f);
        teleport(player, target);
        player.setFallDistance(0f);
    }

    public boolean fell(Player player) {
        ParkourRun run = runs.get(player.getUniqueId());
        if (run == null) {
            return false;
        }
        run.fell();
        toCheckpoint(player, run);
        core.messages().actionBar(player, "parkour.fell");
        return true;
    }

    private void complete(Player player, ParkourRun run) {
        long time = run.elapsed(System.nanoTime());
        runs.remove(player.getUniqueId());
        int[] goal = blocks.get(blocks.size() - 1);
        player.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, new Location(core.lobby().world(), goal[0] + 0.5, goal[1] + 1.5, goal[2] + 0.5),
                40, 0.5, 0.6, 0.5, 0.3);
        core.lobby().giveHotbar(player);
        core.lobby().applyFlight(player);
        if (!ParkourRun.plausible(time, minimum)) {
            core.messages().send(player, "parkour.suspicious");
            core.getLogger().warning(player.getName() + " gyanúsan gyors parkour időt futott: " + Text.timer(time));
            return;
        }
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        boolean first = profile.stat("parkour3.completions") == 0;
        long previous = profile.stat("parkour3.best_ms");
        profile.addStat("parkour3.completions", 1);
        boolean record = profile.minStat("parkour3.best_ms", time);
        core.messages().send(player, "parkour.finished", "%time%", Text.timer(time), "%falls%", String.valueOf(run.falls()));
        if (record && !first) {
            core.messages().send(player, "parkour.personal-best", "%old%", Text.timer(previous));
        }
        long reward = 0;
        String reason;
        if (first) {
            reward = rewardFirst;
            reason = "parkour:first";
        } else if (record) {
            reward = rewardRecord;
            reason = "parkour:record";
        } else {
            long last = parse(profile.setting("parkour.reward", "0"));
            if (System.currentTimeMillis() - last >= repeatCooldown) {
                reward = rewardRepeat;
            }
            reason = "parkour:repeat";
        }
        if (reward > 0 && core.economy().deposit(player, currency, reward, reason) == Ledger.Result.OK) {
            profile.put("parkour.reward", String.valueOf(System.currentTimeMillis()));
            core.messages().send(player, "parkour.reward", "%reward%", core.economy().format(currency, reward));
        }
        List<ProfileService.Entry> top = core.leaderboards().entries("parkour");
        if (record && (top.isEmpty() || time < top.get(0).value)) {
            String text = core.messages().prefixed("parkour.server-record", "%player%", player.getName(), "%time%", Text.timer(time));
            for (Player other : core.lobby().world().getPlayers()) {
                other.sendMessage(text);
            }
        }
        player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 0.8f, 1.0f);
        java.util.UUID id = player.getUniqueId();
        org.bukkit.Bukkit.getScheduler().runTaskLater(core, () -> {
            Player online = org.bukkit.Bukkit.getPlayer(id);
            if (online != null && core.lobby().inLobby(online) && !running(online)) {
                teleport(online, core.lobby().spawn(online));
            }
            if (!runs.containsKey(id)) {
                hideAll(id);
            }
        }, 60L);
    }

    public void stop(Player player, boolean silent) {
        ParkourRun run = runs.remove(player.getUniqueId());
        if (run == null) {
            return;
        }
        hideAll(player.getUniqueId());
        if (!silent) {
            core.messages().send(player, "parkour.stopped");
        }
        if (core.lobby().inLobby(player) && player.isOnline()) {
            core.lobby().giveHotbar(player);
            core.lobby().applyFlight(player);
        }
    }

    public void tick() {
        if (runs.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player player = Bukkit.getPlayer(id);
            ParkourRun run = runs.get(id);
            if (player == null || run == null) {
                runs.remove(id);
                continue;
            }
            long elapsed = run.elapsed(now);
            if (elapsed > maximum) {
                stop(player, true);
                core.messages().send(player, "parkour.timeout");
                continue;
            }
            core.messages().actionBar(player, "parkour.timer", "%time%", Text.timer(elapsed), "%block%", String.valueOf(run.current() + 1),
                    "%blocks%", String.valueOf(run.length()), "%number%", String.valueOf(run.checkpointsReached()),
                    "%total%", String.valueOf(run.checkpointTotal()));
        }
    }

    public String record() {
        List<ProfileService.Entry> top = core.leaderboards().entries("parkour");
        if (top.isEmpty()) {
            return "-";
        }
        return top.get(0).name + " (" + Text.timer(top.get(0).value) + ")";
    }

    public void setPoint(String kind, Location location) {
        YamlConfiguration config = core.configs().get("parkour");
        Location block = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw(), 0f);
        switch (kind) {
            case "start":
                Locs.write(config, "course.start", block);
                break;
            case "fail":
                config.set("course.fail-y", location.getY());
                break;
            default:
                return;
        }
        core.configs().save("parkour");
        load(config, new ArrayList<>());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && running(event.getPlayer())) {
            stop(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (running(event.getPlayer())) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(core, () -> stop(player, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
        hideAll(event.getPlayer().getUniqueId());
        internal.remove(event.getPlayer().getUniqueId());
    }
}
