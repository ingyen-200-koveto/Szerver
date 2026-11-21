package hu.icemine.core.skywars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A ládák tartalma. A szigeten három láda van: együtt mindig adnak egy kardot, blokkokat, kaját és pár páncélt,
 * a többi véletlen. A középső ládákban jobb cuccok vannak (gyémánt, varázsolt kard, gyöngy, aranyalma).
 */
public final class SwLoot {

    private SwLoot() {
    }

    static ItemStack i(Material m, int n) {
        return new ItemStack(m, n);
    }

    static ItemStack ench(Material m, Enchantment e, int level) {
        ItemStack item = new ItemStack(m);
        if (e != null) {
            item.addUnsafeEnchantment(e, level);
        }
        return item;
    }

    static <T> T pick(Random r, List<T> list) {
        return list.get(r.nextInt(list.size()));
    }

    static Material armor(Random r, int slot, boolean center) {
        String[] tiers = center ? new String[]{"IRON", "IRON", "DIAMOND", "DIAMOND"} : new String[]{"LEATHER", "CHAINMAIL", "CHAINMAIL", "IRON", "GOLDEN"};
        String[] parts = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        return Material.valueOf(tiers[r.nextInt(tiers.length)] + "_" + parts[slot]);
    }

    static Material block(Random r) {
        return pick(r, List.of(Material.OAK_PLANKS, Material.STONE, Material.COBBLESTONE, Material.WHITE_WOOL, Material.SANDSTONE));
    }

    /** A sziget három ládájának tartalma, együtt. */
    public static List<List<ItemStack>> island(Random r) {
        List<List<ItemStack>> chests = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            chests.add(new ArrayList<>());
        }
        chests.get(0).add(i(r.nextInt(10) < 7 ? Material.STONE_SWORD : Material.IRON_SWORD, 1));
        chests.get(0).add(i(block(r), 24 + r.nextInt(25)));
        chests.get(0).add(r.nextBoolean() ? i(Material.BREAD, 4 + r.nextInt(4)) : i(Material.COOKED_BEEF, 3 + r.nextInt(3)));
        List<Integer> slots = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(slots, r);
        chests.get(1).add(i(armor(r, slots.get(0), false), 1));
        chests.get(1).add(i(armor(r, slots.get(1), false), 1));
        chests.get(1).add(i(block(r), 16 + r.nextInt(17)));
        chests.get(1).add(i(r.nextBoolean() ? Material.STONE_PICKAXE : Material.IRON_PICKAXE, 1));
        chests.get(2).add(i(armor(r, slots.get(2), false), 1));
        List<ItemStack> extra = new ArrayList<>(List.of(
                i(Material.SNOWBALL, 8 + r.nextInt(9)),
                i(Material.EGG, 8 + r.nextInt(9)),
                i(Material.GOLDEN_APPLE, 1),
                i(Material.STONE_AXE, 1),
                i(Material.COOKED_PORKCHOP, 3),
                i(Material.BOW, 1),
                i(Material.ARROW, 8 + r.nextInt(9)),
                i(Material.ENDER_PEARL, 1),
                i(Material.WATER_BUCKET, 1),
                i(block(r), 16),
                i(Material.FISHING_ROD, 1),
                i(Material.EXPERIENCE_BOTTLE, 4)));
        Collections.shuffle(extra, r);
        int db = 2 + r.nextInt(3);
        for (int k = 0; k < db; k++) {
            chests.get(r.nextInt(3)).add(extra.get(k));
        }
        if (chests.stream().anyMatch(c -> c.stream().anyMatch(x -> x.getType() == Material.BOW))
                && chests.stream().noneMatch(c -> c.stream().anyMatch(x -> x.getType() == Material.ARROW))) {
            chests.get(2).add(i(Material.ARROW, 8));
        }
        return chests;
    }

    /** Egy középső láda tartalma. */
    public static List<ItemStack> center(Random r) {
        List<ItemStack> out = new ArrayList<>();
        out.add(i(armor(r, r.nextInt(4), true), 1));
        if (r.nextInt(3) == 0) {
            out.add(i(armor(r, r.nextInt(4), true), 1));
        }
        List<ItemStack> pool = new ArrayList<>(List.of(
                ench(Material.DIAMOND_SWORD, null, 0),
                ench(Material.IRON_SWORD, hu.icemine.core.util.Items.enchantment("sharpness"), 1 + r.nextInt(2)),
                ench(Material.BOW, hu.icemine.core.util.Items.enchantment("power"), 1),
                i(Material.ARROW, 16),
                i(Material.ENDER_PEARL, 2),
                i(Material.GOLDEN_APPLE, 2 + r.nextInt(2)),
                i(Material.DIAMOND_AXE, 1),
                i(Material.DIAMOND_PICKAXE, 1),
                i(Material.COOKED_BEEF, 6),
                i(Material.TNT, 3),
                i(Material.FLINT_AND_STEEL, 1),
                i(Material.LAVA_BUCKET, 1),
                ench(Material.DIAMOND_CHESTPLATE, hu.icemine.core.util.Items.enchantment("protection"), 1),
                i(Material.EXPERIENCE_BOTTLE, 8),
                i(block(r), 32)));
        Collections.shuffle(pool, r);
        int db = 2 + r.nextInt(3);
        out.addAll(pool.subList(0, db));
        return out;
    }

    /** Véletlen helyekre teszi a tárgyakat a ládában. */
    public static void fill(Inventory inv, List<ItemStack> items, Random r) {
        inv.clear();
        List<Integer> free = new ArrayList<>();
        for (int s = 0; s < inv.getSize(); s++) {
            free.add(s);
        }
        Collections.shuffle(free, r);
        int k = 0;
        for (ItemStack item : items) {
            if (k < free.size()) {
                inv.setItem(free.get(k++), item);
            }
        }
    }
}
