package hu.icemine.core.mode;

import hu.icemine.core.IceMineCore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TeleportGuard implements Listener {

    private final IceMineCore core;
    private final Map<UUID, Long> allowed = new HashMap<>();

    public TeleportGuard(IceMineCore core) {
        this.core = core;
    }

    public void allowNext(Player player) {
        allowed.put(player.getUniqueId(), System.currentTimeMillis() + 1500L);
    }

    public boolean teleport(Player player, Location location) {
        allowNext(player);
        return player.teleport(location);
    }

    private boolean consume(Player player) {
        Long until = allowed.remove(player.getUniqueId());
        return until != null && until >= System.currentTimeMillis();
    }

    private static boolean escapeCause(String cause) {
        switch (cause) {
            case "COMMAND":
            case "PLUGIN":
            case "UNKNOWN":
            case "SPECTATE":
            case "NETHER_PORTAL":
            case "END_PORTAL":
            case "END_GATEWAY":
                return true;
            default:
                return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        String cause = event.getCause().name();
        boolean internal = consume(player);
        if (!internal && ModeService.LOBBY.equals(core.modes().modeOf(from.getWorld()))
                && (cause.equals("ENDER_PEARL") || cause.equals("CHORUS_FRUIT"))) {
            event.setCancelled(true);
            return;
        }
        if (!internal && core.combat().tagged(player) && escapeCause(cause)) {
            boolean far = from.getWorld() != to.getWorld() || from.distanceSquared(to) > core.combat().maxTeleportDistanceSquared();
            if (far) {
                event.setCancelled(true);
                core.messages().send(player, "combat.blocked-teleport", "%seconds%", String.valueOf(core.combat().remainingSeconds(player)));
                return;
            }
        }
        if (!core.parkour().isInternal(player)) {
            core.parkour().stop(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        allowed.remove(event.getPlayer().getUniqueId());
    }
}
