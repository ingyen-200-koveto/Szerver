package hu.icemine.kingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A Nexuspont beadó láda: amit a játékos belerak, csukáskor NP lesz a királyságnak. Ami nem váltható (vagy egyedi
 * nevű, varázsolt tárgy), az visszakerül hozzá, így semmi nem vész el és nem duplázódik.
 */
public final class Donations implements Listener {

    static final class Holder implements InventoryHolder {
        final UUID kingdom;
        Inventory inventory;

        Holder(UUID kingdom) {
            this.kingdom = kingdom;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final IceMineKingdoms plugin;

    Donations(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Kingdom kingdom) {
        Holder holder = new Holder(kingdom.id);
        Inventory inventory = Bukkit.createInventory(holder, 36, plugin.color(plugin.text("donate-title")));
        holder.inventory = inventory;
        player.openInventory(inventory);
    }

    /** Egy tárgy NP értéke (darabszámmal együtt); 0, ha nem váltható. */
    long value(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasEnchants() || item.getItemMeta().hasLore())) {
            return 0;
        }
        return plugin.manager().pointsFor(item.getType().name()) * item.getAmount();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Kingdom kingdom = plugin.manager().kingdom(holder.kingdom);
        long total = 0;
        Map<Integer, ItemStack> back = new HashMap<>();
        ItemStack[] contents = event.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            long value = kingdom == null || !kingdom.members.containsKey(player.getUniqueId()) ? 0 : value(item);
            if (value > 0) {
                total += value;
            } else {
                back.put(i, item);
            }
        }
        event.getInventory().clear();
        for (ItemStack item : back.values()) {
            for (ItemStack left : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        if (total > 0) {
            plugin.manager().donate(player, kingdom, total);
            kingdom.log(player.getName() + " beadott " + total + " NP-t.");
            plugin.send(player, "donated", "%points%", String.valueOf(total), "%total%", String.valueOf(kingdom.resourcePoints));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        } else if (!back.isEmpty()) {
            plugin.send(player, "donate-nothing");
        }
    }
}
