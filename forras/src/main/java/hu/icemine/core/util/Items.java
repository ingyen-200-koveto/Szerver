package hu.icemine.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class Items {

    private static NamespacedKey itemKey;
    private static NamespacedKey kitKey;
    private static NamespacedKey valueKey;

    private final ItemStack stack;
    private final ItemMeta meta;

    private Items(ItemStack stack) {
        this.stack = stack;
        this.meta = stack.getItemMeta();
    }

    public static void init(Plugin plugin) {
        itemKey = new NamespacedKey("iceminecore", "item");
        kitKey = new NamespacedKey("iceminecore", "kit");
        valueKey = new NamespacedKey("iceminecore", "value");
    }

    public static NamespacedKey itemKey() {
        return itemKey;
    }

    public static NamespacedKey kitKey() {
        return kitKey;
    }

    public static NamespacedKey valueKey() {
        return valueKey;
    }

    public static Items of(Material material) {
        return new Items(new ItemStack(material == null ? Material.STONE : material));
    }

    public static Items of(Material material, int amount) {
        return new Items(new ItemStack(material == null ? Material.STONE : material, Math.max(1, Math.min(64, amount))));
    }

    public static Items head(OfflinePlayer owner) {
        Items items = new Items(new ItemStack(Material.PLAYER_HEAD));
        if (items.meta instanceof SkullMeta && owner != null) {
            ((SkullMeta) items.meta).setOwningPlayer(owner);
        }
        return items;
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material == null || material.isAir() ? fallback : material;
    }

    public static Enchantment enchantment(String name) {
        if (name == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        for (Object value : (Iterable<?>) Registry.ENCHANTMENT) {
            if (value instanceof Enchantment && value instanceof Keyed && key.equals(((Keyed) value).getKey())) {
                return (Enchantment) value;
            }
        }
        return null;
    }

    public Items name(String name) {
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
        }
        return this;
    }

    public Items lore(List<String> lines) {
        if (meta != null && lines != null) {
            meta.setLore(Text.color(lines));
        }
        return this;
    }

    public Items lore(String... lines) {
        return lore(new ArrayList<>(Arrays.asList(lines)));
    }

    public Items glow() {
        if (meta != null) {
            Enchantment glow = enchantment("unbreaking");
            if (glow != null) {
                meta.addEnchant(glow, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public Items enchant(String name, int level) {
        Enchantment enchantment = enchantment(name);
        if (meta != null && enchantment != null && level > 0) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public Items unbreakable() {
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        return this;
    }

    public Items hideAll() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        }
        return this;
    }

    public Items tag(String id) {
        if (meta != null && itemKey != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        }
        return this;
    }

    public Items kit(String kit) {
        if (meta != null && kitKey != null) {
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit);
        }
        return this;
    }

    public Items value(int value) {
        if (meta != null && valueKey != null) {
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.INTEGER, value);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static String tagOf(ItemStack item) {
        return read(item, itemKey);
    }

    public static String kitOf(ItemStack item) {
        return read(item, kitKey);
    }

    public static int valueOf(ItemStack item) {
        if (item == null || valueKey == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    private static String read(ItemStack item, NamespacedKey key) {
        if (item == null || key == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(key, PersistentDataType.STRING);
    }
}
