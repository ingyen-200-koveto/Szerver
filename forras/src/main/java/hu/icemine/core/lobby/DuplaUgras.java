package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

/**
 * Dupla ugrás a lobbyban: ugrás közben még egy szóköz előrelök és felemel. Aki repülhet a lobbyban (VIP repülés
 * bekapcsolva), az repül; parkour közben nincs dupla ugrás.
 */
public final class DuplaUgras implements Listener {

    private final IceMineCore core;
    private final Map<UUID, Long> utolso = new HashMap<>();
    private boolean enabled = true;
    private double power = 1.25;
    private double height = 0.85;

    public DuplaUgras(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("double-jump.enabled", true);
        power = Math.max(0.2, Math.min(3, config.getDouble("double-jump.power", 1.25)));
        height = Math.max(0.2, Math.min(2, config.getDouble("double-jump.height", 0.85)));
    }

    boolean hasznalhatja(Player p) {
        if (!enabled || core.lobby() == null || !core.lobby().inLobby(p) || p.isFlying()) {
            return false;
        }
        if (p.getGameMode() != GameMode.ADVENTURE && p.getGameMode() != GameMode.SURVIVAL) {
            return false;
        }
        if (core.parkour() != null && core.parkour().running(p)) {
            return false;
        }
        return !core.lobby().flies(p) && core.auth().isLoggedIn(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggle(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (!event.isFlying() || !hasznalhatja(p)) {
            return;
        }
        event.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        long most = System.currentTimeMillis();
        Long last = utolso.get(p.getUniqueId());
        if (last != null && most - last < 600) {
            return;
        }
        utolso.put(p.getUniqueId(), most);
        Vector v = p.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.01) {
            v = new Vector(0, 0, 0);
        } else {
            v.normalize().multiply(power);
        }
        p.setVelocity(v.setY(height));
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.6F, 1.2F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getAllowFlight() || !((org.bukkit.entity.Entity) p).isOnGround() || !hasznalhatja(p)) {
            return;
        }
        p.setAllowFlight(true);
    }

    public void forget(UUID id) {
        utolso.remove(id);
    }
}
