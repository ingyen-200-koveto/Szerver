package hu.icemine.core.combo;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.mode.ModeInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public final class ComboFly implements Listener {

    public static final String DISPLAY = "combo.display";
    public static final String EFFECTS = "combo.effects";

    private final IceMineCore core;
    private final ComboTracker tracker = new ComboTracker(2500L, 50);
    private final Map<String, Knockback.Settings> perMode = new HashMap<>();
    private final Map<String, Double> damage = new HashMap<>();
    private final Set<String> allowedModes = new HashSet<>();
    private final TreeMap<Integer, String> tiers = new TreeMap<>();
    private final Set<Integer> milestones = new HashSet<>();
    private final Map<UUID, Long> shownUntil = new HashMap<>();
    private boolean enabled = true;
    private boolean particles = true;
    private boolean sounds = true;
    private boolean actionbar = true;
    private boolean combatTagRequired = true;
    private boolean resetOnGround = true;
    private int groundGrace = 6;
    private int hitDelay = 4;
    private double damageMultiplier = 1.0;
    private double maxReach = 4.2;
    private int endMessageFrom = 5;
    private Knockback.Settings defaults = new Knockback.Settings(0.30, 0.40, 0.004, 0.9, 0.55, 7, 0.8, 1.0, 1.0, 1.1, -0.004);

    public ComboFly(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Values values = new Values("combofly.yml", config::get);
        enabled = values.bool("enabled", true);
        tracker.configure(values.integer("combo-timeout", 2500, 300, 10000), values.integer("max-combo", 50, 2, 500));
        particles = values.bool("particles-enabled", true);
        sounds = values.bool("sounds-enabled", true);
        actionbar = values.bool("actionbar-enabled", true);
        combatTagRequired = values.bool("combat-tag-required", true);
        resetOnGround = values.bool("reset-on-ground", true);
        groundGrace = values.integer("ground-grace-ticks", 6, 0, 40);
        hitDelay = values.integer("hit-delay-ticks", 4, 1, 20);
        damageMultiplier = values.decimal("damage-multiplier", 1.0, 0.1, 5.0);
        maxReach = values.decimal("max-reach", 4.2, 3.0, 6.0);
        endMessageFrom = values.integer("end-message-from", 5, 2, 500);
        defaults = settings(values, "");
        allowedModes.clear();
        for (String mode : config.getStringList("allowed-modes")) {
            allowedModes.add(mode.toLowerCase(Locale.ROOT));
        }
        perMode.clear();
        damage.clear();
        ConfigurationSection modes = config.getConfigurationSection("modes");
        if (modes != null) {
            for (String mode : modes.getKeys(false)) {
                perMode.put(mode.toLowerCase(Locale.ROOT), settings(values, "modes." + mode + "."));
                damage.put(mode.toLowerCase(Locale.ROOT), values.decimal("modes." + mode + ".damage-multiplier", damageMultiplier, 0.1, 5.0));
            }
        }
        tiers.clear();
        ConfigurationSection tierSection = config.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                int from = values.integer("tiers." + key + ".from", 1, 1, 1000);
                tiers.put(from, values.text("tiers." + key + ".text", "&b&lICE COMBO &f» &b%combo%x"));
            }
        }
        if (tiers.isEmpty()) {
            tiers.put(1, "&b&lICE COMBO &f» &b%combo%x");
        }
        milestones.clear();
        for (Integer value : config.getIntegerList("milestones")) {
            milestones.add(value);
        }
        problems.addAll(values.problems());
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyHitDelay(player);
        }
    }

    private Knockback.Settings settings(Values values, String base) {
        Knockback.Settings fallback = defaults;
        return new Knockback.Settings(
                values.decimal(base + "knockback-horizontal", fallback.horizontal, 0.0, 2.0),
                values.decimal(base + "knockback-vertical", fallback.vertical, 0.0, 1.5),
                values.decimal(base + "combo-scaling", fallback.scaling, -0.1, 0.1),
                values.decimal(base + "max-horizontal", fallback.maxHorizontal, 0.1, 3.0),
                values.decimal(base + "max-vertical", fallback.maxVertical, 0.1, 2.0),
                values.decimal(base + "max-height", fallback.maxHeight, 2.0, 64.0),
                values.decimal(base + "knockback-air-horizontal", fallback.airHorizontal, 0.0, 3.0),
                values.decimal(base + "knockback-air-vertical", fallback.airVertical, 0.0, 3.0),
                values.decimal(base + "knockback-ground-horizontal", fallback.groundHorizontal, 0.0, 3.0),
                values.decimal(base + "knockback-ground-vertical", fallback.groundVertical, 0.0, 3.0),
                values.decimal(base + "knockback-combo-horizontal", fallback.comboHorizontal, -0.1, 0.1));
    }

    public Knockback.Settings settingsFor(String mode) {
        return perMode.getOrDefault(mode, defaults);
    }

    public double damageFor(String mode) {
        return damage.getOrDefault(mode, damageMultiplier);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean activeIn(String mode) {
        if (!enabled || mode == null || !allowedModes.contains(mode)) {
            return false;
        }
        ModeInfo info = core.modes().info(mode);
        if (info == null || !info.comboFly) {
            return false;
        }
        return !combatTagRequired || info.combatTag;
    }

    public boolean displaying(Player player) {
        Long until = shownUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public int current(Player player) {
        return tracker.count(player.getUniqueId());
    }

    public void applyHitDelay(Player player) {
        String mode = core.modes().current(player);
        player.setMaximumNoDamageTicks(activeIn(mode) ? hitDelay : 20);
    }

    private double heightAboveGround(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int y = location.getBlockY();
        int limit = (int) Math.ceil(defaults.maxHeight) + 4;
        for (int i = 0; i <= limit; i++) {
            if (world.getBlockAt(x, y - i - 1, z).getType().isSolid()) {
                return i + (location.getY() - y);
            }
        }
        return limit + 1;
    }

    private boolean inReach(Player attacker, Player victim) {
        Location eye = attacker.getEyeLocation();
        Location center = victim.getLocation().add(0, 0.9, 0);
        if (eye.getWorld() != center.getWorld()) {
            return false;
        }
        return eye.distance(center) - 0.9 <= maxReach;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        if (attacker.equals(victim) || attacker.getWorld() != victim.getWorld()) {
            return;
        }
        String mode = core.modes().current(victim);
        if (!activeIn(mode) || !inReach(attacker, victim)) {
            return;
        }
        double multiplier = damage.getOrDefault(mode, damageMultiplier);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
        ComboTracker.Hit hit = tracker.hit(attacker.getUniqueId(), victim.getUniqueId(), System.currentTimeMillis());
        finish(hit.ended, mode);
        Knockback.Settings settings = perMode.getOrDefault(mode, defaults);
        int combo = hit.count;
        UUID victimId = victim.getUniqueId();
        Bukkit.getScheduler().runTask(core, () -> {
            Player target = Bukkit.getPlayer(victimId);
            if (target == null || target.isDead() || !target.isValid() || target.getWorld() != attacker.getWorld()) {
                return;
            }
            Location from = attacker.getLocation();
            Location to = target.getLocation();
            double[] velocity = Knockback.compute(to.getX() - from.getX(), to.getZ() - from.getZ(), Math.toRadians(from.getYaw()), combo, settings, heightAboveGround(target));
            target.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
        });
        show(attacker, combo);
        if (milestones.contains(combo)) {
            effects(attacker, victim, combo);
        }
    }

    private void show(Player attacker, int combo) {
        if (!actionbar || combo < 2) {
            return;
        }
        PlayerProfile profile = core.profiles().profile(attacker);
        if (profile != null && !profile.flag(DISPLAY, true)) {
            return;
        }
        String text = tiers.floorEntry(Math.max(1, combo)).getValue().replace("%combo%", String.valueOf(combo));
        attacker.sendActionBar(hu.icemine.core.util.Text.color(text));
        shownUntil.put(attacker.getUniqueId(), System.currentTimeMillis() + 1500L);
    }

    private void effects(Player attacker, Player victim, int combo) {
        PlayerProfile profile = core.profiles().profile(attacker);
        if (profile != null && !profile.flag(EFFECTS, true)) {
            return;
        }
        Location at = victim.getLocation().add(0, 1, 0);
        if (particles) {
            Particle particle = combo >= 20 ? Particle.FIREWORK : (combo >= 10 ? Particle.END_ROD : Particle.SNOWFLAKE);
            at.getWorld().spawnParticle(particle, at.getX(), at.getY(), at.getZ(), combo >= 20 ? 24 : 12, 0.4, 0.5, 0.4, 0.02);
        }
        if (sounds) {
            String sound = combo >= 20 ? "minecraft:entity.firework_rocket.twinkle" : (combo >= 10 ? "minecraft:entity.player.levelup" : "minecraft:block.amethyst_block.chime");
            attacker.playSound(attacker.getLocation(), sound, 0.7f, combo >= 10 ? 1.6f : 1.2f);
        }
    }

    private void finish(List<ComboTracker.Ended> ended, String mode) {
        for (ComboTracker.Ended end : ended) {
            if (end.count < 2) {
                continue;
            }
            Player player = Bukkit.getPlayer(end.player);
            if (player == null) {
                continue;
            }
            PlayerProfile profile = core.profiles().profile(player);
            if (profile != null) {
                profile.addStat("combofly.combos", 1);
                profile.maxStat("combofly.best", end.count);
                String where = mode != null ? mode : core.modes().current(player);
                if (where != null) {
                    profile.maxStat(where + ".best_combo", end.count);
                }
            }
            if (end.count >= endMessageFrom && actionbar && (profile == null || profile.flag(DISPLAY, true))) {
                player.sendActionBar(core.messages().get("combo.ended", "%combo%", String.valueOf(end.count)));
            }
        }
    }

    public void tick() {
        if (tracker.size() == 0) {
            return;
        }
        finish(tracker.expire(System.currentTimeMillis()), null);
        if (!resetOnGround) {
            return;
        }
        List<UUID> landed = new ArrayList<>();
        for (Map.Entry<UUID, ComboTracker.State> entry : tracker.view().entrySet()) {
            Player victim = Bukkit.getPlayer(entry.getValue().target);
            if (victim == null) {
                landed.add(entry.getKey());
                continue;
            }
            if (victim.isOnGround()) {
                entry.getValue().groundTicks += 5;
                if (entry.getValue().groundTicks > groundGrace) {
                    landed.add(entry.getKey());
                }
            } else {
                entry.getValue().groundTicks = 0;
            }
        }
        List<ComboTracker.Ended> ended = new ArrayList<>();
        for (UUID id : landed) {
            ComboTracker.Ended end = tracker.end(id);
            if (end != null) {
                ended.add(end);
            }
        }
        finish(ended, null);
    }

    private void endFor(Player player) {
        finish(tracker.endInvolving(player.getUniqueId()), null);
        shownUntil.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        endFor(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        endFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > 4)) {
            endFor(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        endFor(event.getPlayer());
        applyHitDelay(event.getPlayer());
    }

    public ComboTracker tracker() {
        return tracker;
    }
}
