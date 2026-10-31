package hu.icemine.core.kitpvp;

import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Kit {

    public static final class HitEffect {
        public final PotionEffectType type;
        public final int amplifier;
        public final int ticks;
        public final double chance;

        HitEffect(PotionEffectType type, int amplifier, int ticks, double chance) {
            this.type = type;
            this.amplifier = amplifier;
            this.ticks = ticks;
            this.chance = chance;
        }
    }

    public final String id;
    public final String name;
    public final Material icon;
    public final List<String> description;
    public final String permission;
    private final List<ItemStack> items = new ArrayList<>();
    private final ItemStack[] armor = new ItemStack[4];
    private final List<PotionEffect> effects = new ArrayList<>();
    public final List<HitEffect> onHit = new ArrayList<>();
    private KitAbility ability;

    private Kit(String id, String name, Material icon, List<String> description, String permission) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.permission = permission;
    }

    public static Kit read(String owner, ConfigurationSection section, String id, List<String> problems) {
        String where = owner + " -> kits." + id;
        Kit kit = new Kit(id.toLowerCase(Locale.ROOT),
                section.getString("name", id),
                Items.material(section.getString("icon", "IRON_SWORD"), Material.IRON_SWORD),
                new ArrayList<>(section.getStringList("description")),
                section.getString("permission", ""));
        for (String spec : section.getStringList("items")) {
            ItemStack item = parse(kit.id, spec, where, problems);
            if (item != null) {
                kit.items.add(item);
            }
        }
        String[] slots = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < slots.length; i++) {
            String spec = section.getString("armor." + slots[i], "");
            if (!spec.isBlank()) {
                kit.armor[i] = parse(kit.id, spec, where, problems);
            }
        }
        for (String spec : section.getStringList("effects")) {
            String[] parts = spec.trim().split(":");
            PotionEffectType type = effect(parts[0]);
            if (type == null) {
                problems.add(where + ".effects: ismeretlen effekt (" + parts[0] + ")");
                continue;
            }
            int amplifier = parts.length > 1 ? Math.max(0, Math.min(4, number(parts[1], 1) - 1)) : 0;
            kit.effects.add(new PotionEffect(type, -1, amplifier, true, false));
        }
        for (String spec : section.getStringList("on-hit")) {
            String[] parts = spec.trim().split(":");
            PotionEffectType type = effect(parts[0]);
            if (type == null || parts.length < 4) {
                problems.add(where + ".on-hit: formátum effekt:szint:másodperc:esély (pl. slowness:1:2:0.2)");
                continue;
            }
            int amplifier = Math.max(0, Math.min(2, number(parts[1], 1) - 1));
            int ticks = Math.max(1, Math.min(10, number(parts[2], 2))) * 20;
            double chance;
            try {
                chance = Math.max(0, Math.min(0.5, Double.parseDouble(parts[3])));
            } catch (NumberFormatException exception) {
                chance = 0.1;
            }
            kit.onHit.add(new HitEffect(type, amplifier, ticks, chance));
        }
        kit.ability = KitAbility.read(where + ".ability", section.getConfigurationSection("ability"), problems);
        if (kit.items.isEmpty()) {
            problems.add(where + ": nincs egy tárgy sem a kitben");
        }
        return kit;
    }

    private static int number(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static PotionEffectType effect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return PotionEffectType.getByKey(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
    }

    public static ItemStack parse(String kit, String spec, String where, List<String> problems) {
        String[] parts = spec.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || material.isAir()) {
            problems.add(where + ": ismeretlen tárgy (" + parts[0] + ")");
            return null;
        }
        int amount = 1;
        int start = 1;
        if (parts.length > 1 && parts[1].matches("\\d{1,2}")) {
            amount = Math.max(1, Math.min(64, Integer.parseInt(parts[1])));
            start = 2;
        }
        Items builder = Items.of(material, amount);
        if (kit != null) {
            builder.kit(kit);
        }
        List<PotionEffect> potion = new ArrayList<>();
        org.bukkit.Color dye = null;
        for (int i = start; i < parts.length; i++) {
            String token = parts[i].toLowerCase(Locale.ROOT);
            if (token.equals("unbreakable")) {
                builder.unbreakable();
                continue;
            }
            if (token.matches("#[0-9a-f]{6}")) {
                dye = org.bukkit.Color.fromRGB(Integer.parseInt(token.substring(1), 16));
                continue;
            }
            if (token.startsWith("effect=")) {
                String[] effect = token.substring(7).split(":");
                PotionEffectType type = effect(effect[0]);
                if (type == null) {
                    problems.add(where + ": ismeretlen bájital effekt (" + effect[0] + ")");
                    continue;
                }
                int level = effect.length > 1 ? Math.max(1, Math.min(5, number(effect[1], 1))) : 1;
                int seconds = effect.length > 2 ? Math.max(1, Math.min(600, number(effect[2], 1))) : 1;
                potion.add(new PotionEffect(type, type.isInstant() ? 1 : seconds * 20, level - 1));
                continue;
            }
            if (token.startsWith("name=")) {
                builder.name(parts[i].substring(5).replace('_', ' '));
                continue;
            }
            String[] enchant = token.split(":");
            if (enchant.length == 2 && Items.enchantment(enchant[0]) != null) {
                builder.enchant(enchant[0], Math.max(1, Math.min(10, number(enchant[1], 1))));
            } else {
                problems.add(where + ": ismeretlen varázslat vagy kapcsoló (" + parts[i] + ")");
            }
        }
        ItemStack stack = builder.build();
        if ((dye != null || !potion.isEmpty()) && stack.getItemMeta() != null) {
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            if (dye != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
                ((org.bukkit.inventory.meta.LeatherArmorMeta) meta).setColor(dye);
            }
            if (!potion.isEmpty() && meta instanceof org.bukkit.inventory.meta.PotionMeta) {
                org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) meta;
                for (PotionEffect effect : potion) {
                    potionMeta.addCustomEffect(effect, true);
                }
                potionMeta.setColor(potion.get(0).getType().getColor());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** A kit képessége, vagy null, ha nincs. */
    public KitAbility ability() {
        return ability;
    }

    public List<ItemStack> items() {
        return java.util.Collections.unmodifiableList(items);
    }

    /**
     * Egy tárgy visszaírása a kitpvp.yml tárgy formátumába (ANYAG darab varázslat:szint ... unbreakable #szín name=Név).
     * Az /icem kit create ezzel menti el a kezedben lévő felszerelést új kitként.
     */
    public static String spec(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        StringBuilder spec = new StringBuilder(item.getType().name());
        if (item.getAmount() > 1) {
            spec.append(' ').append(item.getAmount());
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return spec.toString();
        }
        for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
            spec.append(' ').append(enchant.getKey().getKey().getKey()).append(':').append(enchant.getValue());
        }
        if (meta.isUnbreakable()) {
            spec.append(" unbreakable");
        }
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather) {
            org.bukkit.Color color = leather.getColor();
            if (color.asRGB() != 0xA06540) {
                spec.append(" #").append(String.format(Locale.ROOT, "%06X", color.asRGB()));
            }
        }
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            List<PotionEffect> all = new ArrayList<>(potion.getCustomEffects());
            if (potion.getBasePotionType() != null) {
                all.addAll(potion.getBasePotionType().getPotionEffects());
            }
            for (PotionEffect effect : all) {
                int seconds = Math.max(1, effect.getDuration() / 20);
                spec.append(" effect=").append(effect.getType().getKey().getKey()).append(':').append(effect.getAmplifier() + 1).append(':').append(seconds);
            }
        }
        if (meta.hasDisplayName()) {
            spec.append(" name=").append(meta.getDisplayName().replace('§', '&').replace(' ', '_'));
        }
        return spec.toString();
    }

    public boolean allowed(Player player) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public void equip(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (ItemStack item : items) {
            inventory.addItem(item.clone());
        }
        ItemStack[] copy = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            copy[i] = armor[i] == null ? null : armor[i].clone();
        }
        inventory.setArmorContents(copy);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        inventory.setHeldItemSlot(0);
    }

    public void reapplyEffects(Player player) {
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
    }
}
