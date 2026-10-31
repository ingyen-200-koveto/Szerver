package hu.icemine.core.kitpvp;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Attributes;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * KitPvP fejlesztések (kitpvp.yml -> upgrades). Kristályért (az ölésért kapott pénzért) lehet venni, szintenként egyre drágábban,
 * és minden kitre érvényesek. A szintet a játékos profilja tárolja (kitpvp.upgrade.&lt;id&gt;).
 */
public final class KitUpgrades {

    public enum Effect {
        /** +élesség a kardokon és baltákon (per-level szint / fejlesztési szint). */
        SHARPNESS,
        /** +védelem minden páncéldarabon. */
        PROTECTION,
        /** +max élet (per-level fél szív). */
        HEALTH,
        /** -képesség cooldown (per-level százalék). */
        COOLDOWN,
        /** +pénz ölésért (per-level százalék). */
        REWARD;

        static Effect parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    public static final class Upgrade {
        public final String id;
        public final String name;
        public final String icon;
        public final List<String> description;
        public final Effect effect;
        public final double perLevel;
        public final List<Long> prices;

        Upgrade(String id, String name, String icon, List<String> description, Effect effect, double perLevel, List<Long> prices) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.effect = effect;
            this.perLevel = perLevel;
            this.prices = prices;
        }

        public int maxLevel() {
            return prices.size();
        }

        /** A következő szint ára, vagy -1, ha már maximumon van. */
        public long priceFor(int currentLevel) {
            return currentLevel >= prices.size() ? -1 : prices.get(currentLevel);
        }
    }

    private static final String KEY = "kitpvp.upgrade.";

    private final IceMineCore core;
    private final Map<String, Upgrade> upgrades = new LinkedHashMap<>();
    private String currency = "kristaly";

    public KitUpgrades(IceMineCore core) {
        this.core = core;
    }

    public void load(ConfigurationSection section, String currency, List<String> problems) {
        this.currency = currency;
        upgrades.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String where = "kitpvp.yml -> upgrades." + key;
            Effect effect = Effect.parse(entry.getString("effect", ""));
            if (effect == null) {
                problems.add(where + ".effect: sharpness, protection, health, cooldown vagy reward lehet");
                continue;
            }
            List<Long> prices = new ArrayList<>();
            for (Object value : entry.getList("prices", Collections.emptyList())) {
                try {
                    prices.add(Math.max(0L, Long.parseLong(String.valueOf(value).trim())));
                } catch (NumberFormatException exception) {
                    problems.add(where + ".prices: csak számok lehetnek (" + value + ")");
                }
            }
            if (prices.isEmpty() || prices.size() > 10) {
                problems.add(where + ".prices: 1-10 ár kell (szintenként egy)");
                continue;
            }
            upgrades.put(key.toLowerCase(Locale.ROOT), new Upgrade(key.toLowerCase(Locale.ROOT), entry.getString("name", key),
                    entry.getString("icon", "EXPERIENCE_BOTTLE"), new ArrayList<>(entry.getStringList("description")), effect,
                    Math.max(0.0, Math.min(100.0, entry.getDouble("per-level", 1.0))), prices));
        }
    }

    public Map<String, Upgrade> all() {
        return Collections.unmodifiableMap(upgrades);
    }

    public int level(Player player, String id) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return 0;
        }
        Upgrade upgrade = upgrades.get(id);
        try {
            int level = Integer.parseInt(profile.setting(KEY + id, "0"));
            return upgrade == null ? level : Math.max(0, Math.min(upgrade.maxLevel(), level));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** Az adott hatás összesített értéke (pl. COOLDOWN: 20 = -20%). Több fejlesztés is adhatja ugyanazt. */
    public double total(Player player, Effect effect) {
        double sum = 0;
        for (Upgrade upgrade : upgrades.values()) {
            if (upgrade.effect == effect) {
                sum += level(player, upgrade.id) * upgrade.perLevel;
            }
        }
        return sum;
    }

    public double cooldownMultiplier(Player player) {
        return Math.max(0.3, 1.0 - total(player, Effect.COOLDOWN) / 100.0);
    }

    public double rewardMultiplier(Player player) {
        return 1.0 + total(player, Effect.REWARD) / 100.0;
    }

    /** A kit felvétele után: varázslatok a fegyverekre és a páncélra, extra élet. */
    public void apply(Player player) {
        int sharpness = (int) Math.round(total(player, Effect.SHARPNESS));
        int protection = (int) Math.round(total(player, Effect.PROTECTION));
        PlayerInventory inventory = player.getInventory();
        if (sharpness > 0) {
            for (ItemStack item : inventory.getContents()) {
                if (item != null && (item.getType().name().endsWith("_SWORD") || item.getType().name().endsWith("_AXE"))) {
                    boost(item, "sharpness", sharpness);
                }
            }
        }
        if (protection > 0) {
            ItemStack[] armor = inventory.getArmorContents();
            for (ItemStack piece : armor) {
                if (piece != null && !piece.getType().isAir()) {
                    boost(piece, "protection", protection);
                }
            }
            inventory.setArmorContents(armor);
        }
        double extra = total(player, Effect.HEALTH);
        Attributes.setMaxHealth(player, 20.0 + extra);
        if (extra > 0) {
            player.setHealth(Math.min(20.0 + extra, player.getHealth() + extra));
        }
    }

    /** Kilépéskor / spawnra visszaérve: vissza az alap 10 szívre. */
    public void clear(Player player) {
        Attributes.setMaxHealth(player, 20.0);
    }

    private static void boost(ItemStack item, String enchantName, int extra) {
        Enchantment enchantment = Items.enchantment(enchantName);
        ItemMeta meta = item.getItemMeta();
        if (enchantment == null || meta == null) {
            return;
        }
        int current = meta.getEnchantLevel(enchantment);
        meta.addEnchant(enchantment, Math.min(10, current + extra), true);
        item.setItemMeta(meta);
    }

    /** Vásárlás: levonja a pénzt és növeli a szintet. */
    public boolean buy(Player player, String id) {
        Upgrade upgrade = upgrades.get(id);
        PlayerProfile profile = core.profiles().profile(player);
        if (upgrade == null || profile == null) {
            return false;
        }
        int level = level(player, id);
        long price = upgrade.priceFor(level);
        if (price < 0) {
            core.messages().send(player, "upgrade.max", "%name%", upgrade.name);
            return false;
        }
        Ledger.Result result = price == 0 ? Ledger.Result.OK : core.economy().withdraw(player, currency, price, "kitpvp:upgrade:" + id);
        if (result != Ledger.Result.OK) {
            core.messages().send(player, "upgrade.no-money", "%price%", core.economy().format(currency, price));
            return false;
        }
        profile.put(KEY + id, String.valueOf(level + 1));
        core.messages().send(player, "upgrade.bought", "%name%", upgrade.name, "%level%", String.valueOf(level + 1),
                "%max%", String.valueOf(upgrade.maxLevel()), "%price%", core.economy().format(currency, price));
        player.playSound(player.getLocation(), "minecraft:block.anvil.use", 0.6f, 1.4f);
        return true;
    }

    public void open(Player player, Runnable back) {
        int rows = Math.max(3, Math.min(6, 2 + (upgrades.size() + 6) / 7));
        Menu menu = new Menu("kitpvp-upgrades", rows, core.messages().raw("upgrade.menu-title"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int index = 0;
        for (Upgrade upgrade : upgrades.values()) {
            int row = 1 + index / 7;
            if (row >= rows - 1) {
                break;
            }
            int slot = row * 9 + 1 + index % 7;
            index++;
            int level = level(player, upgrade.id);
            List<String> lore = new ArrayList<>(upgrade.description);
            lore.add("");
            lore.add(core.messages().raw("upgrade.level").replace("%level%", String.valueOf(level)).replace("%max%", String.valueOf(upgrade.maxLevel()))
                    + " " + Text.bar(level / (double) upgrade.maxLevel(), upgrade.maxLevel() * 2, "&b▌", "&8▌"));
            long price = upgrade.priceFor(level);
            if (price < 0) {
                lore.add(core.messages().raw("upgrade.lore-max"));
            } else {
                lore.add(core.messages().raw("upgrade.price").replace("%price%", core.economy().format(currency, price)));
                lore.add(core.messages().raw("upgrade.click"));
            }
            Items item = Items.of(Items.material(upgrade.icon, Material.EXPERIENCE_BOTTLE)).name(upgrade.name).lore(lore).hideAll();
            if (level > 0) {
                item.glow();
            }
            String id = upgrade.id;
            menu.set(slot, item.build(), clicker -> {
                if (buy(clicker, id)) {
                    open(clicker, back);
                }
            });
        }
        menu.set(rows * 9 - 5, Items.of(Material.EMERALD).name(core.messages().raw("upgrade.balance")
                .replace("%balance%", core.economy().format(currency, core.economy().balance(player, currency)))).build());
        if (back != null) {
            menu.set(rows * 9 - 9, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), clicker -> back.run());
        }
        menu.open(player);
    }
}
