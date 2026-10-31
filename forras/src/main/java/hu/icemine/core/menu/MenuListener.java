package hu.icemine.core.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class MenuListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public MenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        int slot = event.getRawSlot();
        Menu menu = (Menu) holder;
        if (slot < 0 || slot >= menu.size() || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(who.getUniqueId());
        if (last != null && now - last < 150) {
            return;
        }
        lastClick.put(who.getUniqueId(), now);
        Player player = (Player) who;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                menu.click(player, slot);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
