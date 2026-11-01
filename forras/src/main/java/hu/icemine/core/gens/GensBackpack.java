package hu.icemine.core.gens;

import hu.icemine.core.util.Text;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A Gens hátizsák. A tartalom mindig a legnagyobb méretben van tárolva, így ha valaki elveszíti a nagy hátizsákot
 * (pl. lejár a rangja), a hátsó sorokban lévő tárgyai nem vesznek el, csak addig nem látja őket.
 */
public final class GensBackpack implements InventoryHolder {

    private final UUID owner;
    private final Inventory inventory;

    GensBackpack(UUID owner, String title, ItemStack[] stored, int size) {
        this.owner = owner;
        this.inventory = Bukkit.createInventory(this, size, Text.color(title));
        for (int i = 0; i < size && i < stored.length; i++) {
            inventory.setItem(i, stored[i]);
        }
    }

    public UUID owner() {
        return owner;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** A látható rész visszamásolása a tárolt tömbbe (a hátsó, nem látható sorok maradnak). */
    void copyInto(ItemStack[] stored) {
        for (int i = 0; i < inventory.getSize() && i < stored.length; i++) {
            stored[i] = inventory.getItem(i);
        }
    }
}
