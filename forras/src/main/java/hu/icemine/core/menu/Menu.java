package hu.icemine.core.menu;

import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
    private final Map<Integer, Consumer<Player>> rightActions = new HashMap<>();
    private final String id;

    public Menu(String id, int rows, String title) {
        this.id = id;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.color(title));
    }

    public String id() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    public Menu set(int slot, ItemStack item, Consumer<Player> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
        return this;
    }

    public Menu set(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    /** Külön művelet bal és jobb klikkre (pl. +1 / -1, vagy kiválasztás / törlés). */
    public Menu set(int slot, ItemStack item, Consumer<Player> left, Consumer<Player> right) {
        set(slot, item, left);
        if (slot >= 0 && slot < inventory.getSize()) {
            if (right == null) {
                rightActions.remove(slot);
            } else {
                rightActions.put(slot, right);
            }
        }
        return this;
    }

    public Menu frame(Material edge, Material inner) {
        ItemStack border = Items.of(edge).name(" ").build();
        ItemStack fill = inner == null ? null : Items.of(inner).name(" ").build();
        int rows = inventory.getSize() / 9;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean edgeSlot = row == 0 || row == rows - 1 || column == 0 || column == 8;
            if (edgeSlot) {
                inventory.setItem(slot, border);
            } else if (fill != null) {
                inventory.setItem(slot, fill);
            }
        }
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void click(Player player, int slot) {
        click(player, slot, false);
    }

    public void click(Player player, int slot, boolean right) {
        Consumer<Player> action = right && rightActions.containsKey(slot) ? rightActions.get(slot) : actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }
}
