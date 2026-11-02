package hu.icemine.kingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * /k fly: repülés a saját királyságod földjén (jogosultság kell hozzá: icemine.kingdoms.fly). Kikapcsol, ha
 * elhagyod a földet, ha harcba keveredsz, vagy ha a királyságod inváziója zajlik. Utána egy ideig nem sebez az esés.
 */
final class KingdomFly implements Listener {

    static final String PERMISSION = "icemine.kingdoms.fly";

    private final IceMineKingdoms plugin;
    private final Set<UUID> flying = new HashSet<>();
    private final Map<UUID, Long> combat = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();

    KingdomFly(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private long combatMillis() {
        return Math.max(0, plugin.getConfig().getLong("fly.combat-seconds", 10)) * 1000L;
    }

    boolean flying(Player player) {
        return flying.contains(player.getUniqueId());
    }

    /** Miért nem repülhet most (üzenet kulcs), vagy null, ha repülhet. */
    private String problem(Player player) {
        if (!plugin.inKingdoms(player)) {
            return "only-in-kingdoms";
        }
        Kingdom kingdom = plugin.manager().kingdomOf(player.getUniqueId());
        if (kingdom == null) {
            return "no-kingdom";
        }
        if (plugin.manager().owner(player.getLocation()) != kingdom) {
            return "fly-own-land";
        }
        Long last = combat.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < combatMillis()) {
            return "fly-combat";
        }
        if (plugin.invasions().defending(kingdom) || plugin.invasions().attacking(kingdom)) {
            return "fly-invasion";
        }
        return null;
    }

    void toggle(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            plugin.send(player, "fly-no-permission");
            return;
        }
        if (flying(player)) {
            stop(player, "fly-off");
            return;
        }
        String problem = problem(player);
        if (problem != null) {
            plugin.send(player, problem);
            return;
        }
        flying.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
        plugin.send(player, "fly-on");
    }

    /** Kikapcsolja a repülést (ha a /k fly kapcsolta be). A message lehet null. */
    void stop(Player player, String message) {
        if (!flying.remove(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            if (player.isFlying()) {
                noFall.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
            }
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (message != null) {
            plugin.send(player, message);
        }
    }

    /** Félmásodpercenként: aki már nem repülhetne, annak kikapcsoljuk. */
    void tick() {
        for (UUID id : new HashSet<>(flying)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                flying.remove(id);
                continue;
            }
            String problem = problem(player);
            if (problem != null) {
                stop(player, problem.equals("only-in-kingdoms") || problem.equals("no-kingdom") ? null : problem);
            }
        }
        long now = System.currentTimeMillis();
        noFall.values().removeIf(until -> until < now);
        combat.values().removeIf(at -> now - at > combatMillis());
    }

    void forget(Player player) {
        stop(player, null);
        combat.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !plugin.inKingdoms(victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null || attacker == victim) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : new Player[]{victim, attacker}) {
            combat.put(player.getUniqueId(), now);
            if (flying(player)) {
                stop(player, "fly-combat");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player) {
            Long until = noFall.remove(player.getUniqueId());
            if (until != null && until >= System.currentTimeMillis()) {
                event.setCancelled(true);
            }
        }
    }
}
