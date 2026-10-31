package hu.icemine.core.duels;

import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DuelKit {
    public final String id;
    public final String name;
    public final Material icon;
    public final List<String> description;
    public final String arena;
    public final DuelKit.Rule rule;
    public final boolean oldCombat;
    public final int hitDelay;
    public final boolean hunger;
    public final boolean regen;
    public final boolean build;
    public final boolean ranked;
    public final int target;
    public final String permission;
    final List<ItemStack> items = new ArrayList<>();
    final ItemStack[] armor = new ItemStack[4];
    final List<PotionEffect> effects = new ArrayList<>();

    private DuelKit(String s, ConfigurationSection configurationsection) {
        this.id = s;
        this.name = configurationsection.getString("name", s);
        this.icon = Items.material(configurationsection.getString("icon", "IRON_SWORD"), Material.IRON_SWORD);
        this.description = new ArrayList<>(configurationsection.getStringList("description"));
        this.arena = configurationsection.getString("arena", "classic").toLowerCase(Locale.ROOT);

        DuelKit.Rule duelkit$rule;
        try {
            duelkit$rule = DuelKit.Rule.valueOf(configurationsection.getString("rule", "normal").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException illegalargumentexception) {
            duelkit$rule = DuelKit.Rule.NORMAL;
        }

        this.rule = duelkit$rule;
        this.oldCombat = configurationsection.getBoolean("old-combat", true);
        this.hitDelay = Math.max(1, Math.min(20, configurationsection.getInt("hit-delay", 20)));
        this.hunger = configurationsection.getBoolean("hunger", false);
        this.regen = configurationsection.getBoolean("natural-regen", true);
        this.build = configurationsection.getBoolean("build", false);
        this.ranked = configurationsection.getBoolean("ranked", true);
        int i = this.rule == DuelKit.Rule.BOXING ? 100 : (this.rule == DuelKit.Rule.BRIDGE ? 5 : 1);
        this.target = Math.max(1, configurationsection.getInt("target", i));
        this.permission = configurationsection.getString("permission", "");
    }

    public static DuelKit read(String s, ConfigurationSection configurationsection, List<String> list) {
        DuelKit duelkit = new DuelKit(s, configurationsection);
        String s1 = "duels.yml -> modes." + s;

        for (String s2 : configurationsection.getStringList("items")) {
            ItemStack itemstack = Kit.parse(null, s2, s1 + ".items", list);
            if (itemstack != null) {
                duelkit.items.add(itemstack);
            }
        }

        String[] astring1 = new String[]{"boots", "leggings", "chestplate", "helmet"};

        for (int j = 0; j < astring1.length; j++) {
            String s3 = configurationsection.getString("armor." + astring1[j], "");
            if (!s3.isBlank()) {
                duelkit.armor[j] = Kit.parse(null, s3, s1 + ".armor", list);
            }
        }

        for (String s4 : configurationsection.getStringList("effects")) {
            String[] astring = s4.trim().split(":");
            PotionEffectType potioneffecttype = Kit.effect(astring[0]);
            if (potioneffecttype == null) {
                list.add(s1 + ".effects: ismeretlen effekt (" + astring[0] + ")");
            } else {
                int i = 1;
                if (astring.length > 1) {
                    try {
                        i = Math.max(1, Math.min(5, Integer.parseInt(astring[1].trim())));
                    } catch (NumberFormatException numberformatexception) {
                    }
                }

                duelkit.effects.add(new PotionEffect(potioneffecttype, -1, i - 1, true, false));
            }
        }

        return duelkit;
    }

    public boolean allowed(Player player) {
        return this.permission == null || this.permission.isBlank() || player.hasPermission(this.permission);
    }

    public boolean usesBlocks() {
        return this.build || this.rule == DuelKit.Rule.BRIDGE;
    }

    public void equip(Player player, DuelKit.Side duelkit$side) {
        PlayerInventory playerinventory = player.getInventory();
        playerinventory.clear();

        for (ItemStack itemstack : this.items) {
            playerinventory.addItem(tint(itemstack.clone(), duelkit$side));
        }

        ItemStack[] aitemstack = new ItemStack[4];

        for (int i = 0; i < 4; i++) {
            aitemstack[i] = this.armor[i] == null ? null : tint(this.armor[i].clone(), duelkit$side);
        }

        playerinventory.setArmorContents(aitemstack);

        for (PotionEffect potioneffect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(potioneffect.getType());
        }

        for (PotionEffect potioneffect1 : this.effects) {
            player.addPotionEffect(potioneffect1);
        }

        playerinventory.setHeldItemSlot(0);
    }

    static ItemStack tint(ItemStack itemstack, DuelKit.Side duelkit$side) {
        if (duelkit$side != null && itemstack != null) {
            String s = itemstack.getType().name();
            if ((!s.endsWith("_TERRACOTTA") || s.equals("TERRACOTTA")) && !s.endsWith("_WOOL")) {
                ItemMeta itemmeta = itemstack.getItemMeta();
                if (itemmeta instanceof LeatherArmorMeta) {
                    ((LeatherArmorMeta)itemmeta).setColor(duelkit$side.color);
                    itemstack.setItemMeta(itemmeta);
                }

                return itemstack;
            } else {
                return new ItemStack(s.endsWith("_WOOL") ? duelkit$side.wool : duelkit$side.terracotta, itemstack.getAmount());
            }
        } else {
            return itemstack;
        }
    }

    public enum Rule {
        NORMAL,
        BOXING,
        SUMO,
        BRIDGE,
        COMBOFLY;
    }

    public enum Side {
        RED("&c", Color.fromRGB(176, 46, 38), Material.RED_TERRACOTTA, Material.RED_WOOL),
        BLUE("&9", Color.fromRGB(60, 68, 170), Material.BLUE_TERRACOTTA, Material.BLUE_WOOL);

        public final String chat;
        public final Color color;
        public final Material terracotta;
        public final Material wool;

        Side(String s1, Color color, Material material, Material material1) {
            this.chat = s1;
            this.color = color;
            this.terracotta = material;
            this.wool = material1;
        }
    }
}
