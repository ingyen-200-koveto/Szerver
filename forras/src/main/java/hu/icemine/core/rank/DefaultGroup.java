package hu.icemine.core.rank;

import hu.icemine.core.IceMineCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Az alap rang (ranks.yml első rangja, alapból a "jatekos" csoport): aki belép, és a LuckPermsben még csak a
 * "default" csoportban van, megkapja. Így az új játékosok automatikusan Játékos rangot kapnak.
 */
public final class DefaultGroup implements Listener {

    private final IceMineCore core;

    public DefaultGroup(IceMineCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(core, () -> give(player), 60L);
    }

    void give(Player player) {
        if (!player.isOnline() || !core.prefixes().hooked() || core.ranks().all().isEmpty()) {
            return;
        }
        String group = core.ranks().all().get(0).group;
        if (group == null || group.isBlank() || group.equalsIgnoreCase("default")) {
            return;
        }
        if (!core.prefixes().group(player).equalsIgnoreCase("default") || player.hasPermission("group." + group)) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getUniqueId() + " parent add " + group);
        Bukkit.getScheduler().runTaskLater(core, () -> {
            if (player.isOnline()) {
                core.ranks().apply(player);
            }
        }, 40L);
    }
}
