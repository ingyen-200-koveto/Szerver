package hu.icemine.core.combat;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.Values;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatTag implements Listener {

    private final IceMineCore core;
    private final CombatTracker tracker = new CombatTracker(15000L);
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<String> blockedCommands = new HashSet<>();
    private boolean enabled = true;
    private boolean bossbar = true;
    private boolean actionbar = false;
    private BarColor barColor = BarColor.RED;
    private BarStyle barStyle = BarStyle.SEGMENTED_10;
    private String punishment = "kill";
    private boolean broadcastLogout = true;
    private double maxTeleport = 8.0;
    private long creditWindow = 10000L;

    public CombatTag(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Values values = new Values("combat-tag.yml", config::get);
        enabled = values.bool("enabled", true);
        tracker.duration(values.integer("duration-seconds", 15, 3, 120) * 1000L);
        bossbar = values.bool("bossbar.enabled", true);
        actionbar = values.bool("actionbar", false);
        try {
            barColor = BarColor.valueOf(values.text("bossbar.color", "RED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            problems.add("combat-tag.yml -> bossbar.color: ismeretlen szín, pirosat használok");
            barColor = BarColor.RED;
        }
        try {
            barStyle = BarStyle.valueOf(values.text("bossbar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            problems.add("combat-tag.yml -> bossbar.style: ismeretlen stílus, SEGMENTED_10-et használok");
            barStyle = BarStyle.SEGMENTED_10;
        }
        punishment = values.choice("logout-punishment", "kill", "kill", "none");
        broadcastLogout = values.bool("broadcast-logout", true);
        maxTeleport = values.decimal("max-teleport-distance", 8.0, 0.0, 64.0);
        creditWindow = values.integer("kill-credit-seconds", 10, 1, 60) * 1000L;
        blockedCommands.clear();
        for (String command : config.getStringList("blocked-commands")) {
            blockedCommands.add(command.toLowerCase(Locale.ROOT).replace("/", "").trim());
        }
        problems.addAll(values.problems());
        for (BossBar bar : bars.values()) {
            bar.setColor(barColor);
            bar.setStyle(barStyle);
        }
    }

    public boolean tagged(Player player) {
        return enabled && tracker.tagged(player.getUniqueId(), System.currentTimeMillis());
    }

    public long remainingMillis(Player player) {
        return tracker.remaining(player.getUniqueId(), System.currentTimeMillis());
    }

    public long remainingSeconds(Player player) {
        return (tracker.remaining(player.getUniqueId(), System.currentTimeMillis()) + 999) / 1000;
    }

    public double maxTeleportDistanceSquared() {
        return maxTeleport * maxTeleport;
    }

    public Player lastAttacker(Player victim) {
        UUID id = tracker.lastAttacker(victim.getUniqueId(), System.currentTimeMillis(), creditWindow);
        return id == null ? null : Bukkit.getPlayer(id);
    }

    public int count() {
        return tracker.active().size();
    }

    public void clear(Player player) {
        tracker.clear(player.getUniqueId());
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public boolean activeIn(Player player) {
        String mode = core.modes().current(player);
        ModeInfo info = core.modes().info(mode);
        return enabled && info != null && info.combatTag;
    }

    public static Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        tag(attacker(event.getDamager()), (Player) event.getEntity());
    }

    /** Harcba teszi mindkettőt. A sebzés nélküli támadások (pl. kit képességek: fagyasztás, behúzás) is ezt hívják. */
    public void tag(Player attacker, Player victim) {
        if (!enabled || attacker == null || victim == null || attacker.equals(victim) || attacker.getWorld() != victim.getWorld() || !activeIn(victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean victimFresh = !tracker.tagged(victim.getUniqueId(), now);
        boolean attackerFresh = !tracker.tagged(attacker.getUniqueId(), now);
        tracker.tag(victim.getUniqueId(), attacker.getUniqueId(), now);
        if (victimFresh) {
            core.messages().send(victim, "combat.tagged", "%seconds%", String.valueOf(tracker.duration() / 1000));
        }
        if (attackerFresh) {
            core.messages().send(attacker, "combat.tagged", "%seconds%", String.valueOf(tracker.duration() / 1000));
        }
        show(victim);
        show(attacker);
    }

    private void show(Player player) {
        long remaining = tracker.remaining(player.getUniqueId(), System.currentTimeMillis());
        String title = core.messages().get("combat.bar", "%seconds%", String.valueOf((remaining + 999) / 1000));
        if (bossbar) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                bar = Bukkit.createBossBar(title, barColor, barStyle);
                bar.addPlayer(player);
                bars.put(player.getUniqueId(), bar);
            }
            bar.setTitle(title);
            bar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) tracker.duration())));
        }
        if (actionbar && !core.combo().displaying(player)) {
            player.sendActionBar(title);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : tracker.expire(now)) {
            BossBar bar = bars.remove(id);
            if (bar != null) {
                bar.removeAll();
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                core.messages().send(player, "combat.untagged");
            }
        }
        for (UUID id : new ArrayList<>(tracker.active())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                tracker.clear(id);
                BossBar bar = bars.remove(id);
                if (bar != null) {
                    bar.removeAll();
                }
                continue;
            }
            show(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!tagged(player)) {
            return;
        }
        String message = event.getMessage();
        String label = message.length() > 1 ? message.substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (blockedCommands.contains(label)) {
            event.setCancelled(true);
            core.messages().send(player, "combat.blocked-command", "%seconds%", String.valueOf(remainingSeconds(player)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (tagged(player) && punishment.equals("kill") && !player.isDead()) {
            Player killer = lastAttacker(player);
            if (broadcastLogout) {
                String text = core.messages().prefixed("combat.logout-broadcast", "%player%", player.getName());
                for (Player other : player.getWorld().getPlayers()) {
                    if (!other.equals(player)) {
                        other.sendMessage(text);
                    }
                }
            }
            core.getLogger().info(player.getName() + " harc közben lépett ki" + (killer == null ? "" : " (" + killer.getName() + " ellen)") + ".");
            player.setHealth(0.0);
        }
        clear(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
        Bukkit.getScheduler().runTask(core, () -> {
            tracker.clear(id);
            BossBar left = bars.remove(id);
            if (left != null) {
                left.removeAll();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!activeIn(player)) {
            clear(player);
        }
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}
