package hu.icemine.core.bedwars;

import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/** A BedWars tárgyai: kezdő felszerelés, páncél, eszközök, és a jelölések (bw:...), amikből a plugin felismeri őket. */
public final class BwItems {

    public static final String PREFIX = "bw:";
    public static final String[] PICKAXES = {"WOODEN_PICKAXE", "IRON_PICKAXE", "GOLDEN_PICKAXE", "DIAMOND_PICKAXE"};
    public static final String[] AXES = {"WOODEN_AXE", "STONE_AXE", "IRON_AXE", "DIAMOND_AXE"};
    public static final int[] TOOL_EFFICIENCY = {1, 2, 3, 3};
    public static final String[][] ARMOR = {
            {"LEATHER_LEGGINGS", "LEATHER_BOOTS"},
            {"CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS"},
            {"IRON_LEGGINGS", "IRON_BOOTS"},
            {"DIAMOND_LEGGINGS", "DIAMOND_BOOTS"}
    };

    private BwItems() {
    }

    /** A tárgy BedWars jelölése "bw:" nélkül, vagy null. */
    public static String tag(ItemStack item) {
        if (item == null || !item.hasItemMeta() || Items.itemKey() == null) {
            return null;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(Items.itemKey(), PersistentDataType.STRING);
        return tag != null && tag.startsWith(PREFIX) ? tag.substring(PREFIX.length()) : null;
    }

    public static ItemStack tagged(ItemStack item, String tag) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && Items.itemKey() != null) {
            meta.getPersistentDataContainer().set(Items.itemKey(), PersistentDataType.STRING, PREFIX + tag);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack item(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        if (name != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color(name));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /** Eltörhetetlen, jelölt tárgy (halál után újra megkapja). */
    public static ItemStack permanent(ItemStack item, String tag) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return tagged(item, tag);
    }

    public static void enchant(ItemStack item, String name, int level) {
        Enchantment enchantment = Items.enchantment(name);
        if (enchantment == null || level <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
    }

    private static ItemStack leather(Material material, TeamColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color.leather());
            item.setItemMeta(leather);
        }
        return item;
    }

    public static void armor(Player player, BwPlayer data, BwTeam team) {
        int level = Math.max(0, Math.min(3, data.armor));
        Material legs = Material.valueOf(ARMOR[level][0]);
        Material boots = Material.valueOf(ARMOR[level][1]);
        ItemStack[] pieces = {
                permanent(leather(Material.LEATHER_HELMET, team.color), "armor"),
                permanent(leather(Material.LEATHER_CHESTPLATE, team.color), "armor"),
                permanent(level == 0 ? leather(legs, team.color) : new ItemStack(legs), "armor"),
                permanent(level == 0 ? leather(boots, team.color) : new ItemStack(boots), "armor")
        };
        for (ItemStack piece : pieces) {
            enchant(piece, "protection", team.protection);
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(pieces[0]);
        inventory.setChestplate(pieces[1]);
        inventory.setLeggings(pieces[2]);
        inventory.setBoots(pieces[3]);
    }

    public static ItemStack sword(Material material, BwTeam team) {
        ItemStack item = permanent(new ItemStack(material), material == Material.WOODEN_SWORD ? "sword:base" : "sword");
        enchant(item, "sharpness", team.sharpness);
        return item;
    }

    public static ItemStack pickaxe(int level, BwTeam team) {
        int index = Math.max(1, Math.min(4, level)) - 1;
        ItemStack item = permanent(new ItemStack(Material.valueOf(PICKAXES[index])), "pickaxe");
        enchant(item, "efficiency", TOOL_EFFICIENCY[index]);
        return item;
    }

    public static ItemStack axe(int level, BwTeam team) {
        int index = Math.max(1, Math.min(4, level)) - 1;
        ItemStack item = permanent(new ItemStack(Material.valueOf(AXES[index])), "axe");
        enchant(item, "efficiency", TOOL_EFFICIENCY[index]);
        enchant(item, "sharpness", team.sharpness);
        return item;
    }

    public static void spawnKit(Player player, BwPlayer data, BwTeam team) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        armor(player, data, team);
        inventory.addItem(sword(Material.WOODEN_SWORD, team));
        if (data.pickaxe > 0) {
            inventory.addItem(pickaxe(data.pickaxe, team));
        }
        if (data.axe > 0) {
            inventory.addItem(axe(data.axe, team));
        }
        if (data.shears) {
            inventory.addItem(permanent(new ItemStack(Material.SHEARS), "shears"));
        }
        inventory.setHeldItemSlot(0);
    }

    public static void replaceTagged(Player player, String tag, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (tag.equals(tag(contents[i]))) {
                inventory.setItem(i, item);
                return;
            }
        }
        give(player, item);
    }

    public static void removeTagged(Player player, String tag) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (tag.equals(tag(contents[i]))) {
                inventory.setItem(i, null);
            }
        }
    }

    public static void refreshSharpness(Player player, BwTeam team) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            String tag = tag(item);
            if (tag != null && (tag.startsWith("sword") || tag.equals("axe"))) {
                enchant(item, "sharpness", team.sharpness);
            }
        }
    }

    public static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static void take(Player player, Material material, int amount) {
        int left = amount;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int used = Math.min(left, item.getAmount());
                left -= used;
                if (used >= item.getAmount()) {
                    inventory.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - used);
                }
            }
        }
    }

    public static void give(Player player, ItemStack item) {
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }
}
