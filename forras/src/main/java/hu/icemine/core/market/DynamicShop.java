package hu.icemine.core.market;

import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Dinamikus bolt (/shop): az árak a kereslettől függnek. Amiből sokat vesznek, az drágul, amiből sokat adnak el,
 * az olcsóbb lesz, és idővel minden ár lassan visszaáll az alapra. Piaconként (Kingdoms, Fyland) külön számol.
 */
public final class DynamicShop {

    /** Egy tétel a boltban: egy köteg (pl. 16 zúzottkő) vételi és eladási alapára. */
    static final class Entry {
        final String key;
        final ItemStack item;
        final String special;
        final long buy;
        final long sell;
        final List<String> scopes;

        Entry(String key, ItemStack item, String special, long buy, long sell, List<String> scopes) {
            this.key = key;
            this.item = item;
            this.special = special;
            this.buy = buy;
            this.sell = sell;
            this.scopes = scopes;
        }

        boolean in(String scope) {
            return scopes.isEmpty() || scopes.contains(scope);
        }
    }

    static final class Category {
        final String id;
        final String name;
        final Material icon;
        final int slot;
        final List<Entry> entries = new ArrayList<>();

        Category(String id, String name, Material icon, int slot) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
        }
    }

    private static final int[] INNER = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Market market;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Entry> byMaterial = new HashMap<>();
    private final Map<String, Double> net = new HashMap<>();
    private double elasticity = 5000;
    private double minFactor = 0.5;
    private double maxFactor = 2.0;
    private double recovery = 0.1;
    private int minutes;

    DynamicShop(Market market) {
        this.market = market;
    }

    void loadCounters() throws Exception {
        net.clear();
        net.putAll(market.store().loadShop());
    }

    void saveCounters() {
        market.store().saveShop(net);
    }

    void load(YamlConfiguration config, List<String> problems) {
        elasticity = Math.max(10, config.getDouble("shop.elasticity", 5000));
        minFactor = Math.max(0.05, Math.min(1, config.getDouble("shop.min-factor", 0.5)));
        maxFactor = Math.max(1, Math.min(20, config.getDouble("shop.max-factor", 2.0)));
        recovery = Math.max(0, Math.min(1, config.getDouble("shop.recovery-per-hour", 0.1)));
        categories.clear();
        byMaterial.clear();
        ConfigurationSection section = config.getConfigurationSection("shop.categories");
        if (section == null) {
            return;
        }
        int index = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Category category = new Category(id, entry.getString("name", id), Items.material(entry.getString("icon", "CHEST"), Material.CHEST),
                    entry.getInt("slot", 10 + index * 2));
            index++;
            for (Map<?, ?> raw : entry.getMapList("items")) {
                long buy = raw.get("buy") instanceof Number number ? Math.max(0, number.longValue()) : 0;
                long sell = raw.get("sell") instanceof Number number ? Math.max(0, number.longValue()) : 0;
                List<String> scopes = new ArrayList<>();
                if (raw.get("scopes") instanceof List<?> list) {
                    for (Object scope : list) {
                        scopes.add(String.valueOf(scope).toLowerCase(Locale.ROOT));
                    }
                }
                if (raw.get("special") != null) {
                    String special = String.valueOf(raw.get("special")).toLowerCase(Locale.ROOT);
                    ItemStack item = market.specials().item(special);
                    if (item == null) {
                        problems.add("market.yml -> shop.categories." + id + ": ismeretlen különleges tárgy (" + special + ")");
                        continue;
                    }
                    category.entries.add(new Entry("special_" + special, item, special, buy, 0, scopes));
                    continue;
                }
                Object spec = raw.get("item");
                if (spec == null) {
                    problems.add("market.yml -> shop.categories." + id + ": hiányzik az item");
                    continue;
                }
                ItemStack item = Kit.parse(null, String.valueOf(spec), "market.yml -> shop.categories." + id, problems);
                if (item == null) {
                    continue;
                }
                Entry parsed = new Entry(item.getType().name().toLowerCase(Locale.ROOT), item, null, buy, sell, scopes);
                category.entries.add(parsed);
                if (sell > 0 && plain(item)) {
                    byMaterial.putIfAbsent(item.getType(), parsed);
                }
            }
            categories.put(id, category);
        }
    }

    private static boolean plain(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasLore() && !meta.hasEnchants());
    }

    double factor(String scope, String key) {
        double value = net.getOrDefault(scope + ":" + key, 0.0);
        return Math.max(minFactor, Math.min(maxFactor, 1.0 + value / elasticity));
    }

    long buyPrice(String scope, Entry entry) {
        return Math.max(1, Math.round(entry.buy * (entry.special != null ? 1.0 : factor(scope, entry.key))));
    }

    long sellPrice(String scope, Entry entry) {
        return Math.max(0, Math.round(entry.sell * factor(scope, entry.key)));
    }

    /** Egy darab ára eladáskor (eladó láda, /sell), vagy 0, ha nem eladható. */
    double unitSellPrice(String scope, ItemStack item) {
        if (item == null || item.getType().isAir() || !plain(item)) {
            return 0;
        }
        Entry entry = byMaterial.get(item.getType());
        if (entry == null || !entry.in(scope) || entry.sell <= 0) {
            return 0;
        }
        return entry.sell * factor(scope, entry.key) / Math.max(1, entry.item.getAmount());
    }

    /** Eladás után a kereslet frissítése. */
    void sold(String scope, ItemStack item, int amount) {
        Entry entry = byMaterial.get(item.getType());
        if (entry != null) {
            net.merge(scope + ":" + entry.key, -amount / (double) Math.max(1, entry.item.getAmount()), Double::sum);
        }
    }

    /** Percenként: az árak lassan visszaállnak; ötpercenként mentés. */
    void drift() {
        double keep = 1.0 - recovery / 60.0;
        net.replaceAll((key, value) -> Math.abs(value * keep) < 0.01 ? 0.0 : value * keep);
        minutes++;
        if (minutes % 5 == 0) {
            saveCounters();
        }
    }

    public void open(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        Menu menu = new Menu("market-shop", 4, market.text("shop-title"));
        menu.frame(Material.CYAN_STAINED_GLASS_PANE, null);
        for (Category category : categories.values()) {
            long count = category.entries.stream().filter(entry -> entry.in(scope.id())).count();
            if (count == 0 || category.slot < 0 || category.slot >= 36) {
                continue;
            }
            menu.set(category.slot, Items.of(category.icon).name(category.name).lore(market.texts("shop-category-lore", "%count%", String.valueOf(count))).hideAll().build(),
                    clicker -> openCategory(clicker, category.id, 0));
        }
        menu.set(31, Items.of(Material.GOLD_INGOT).name(market.text("balance", "%money%", market.money(scope, market.balance(player, scope)))).build());
        menu.open(player);
    }

    void openCategory(Player player, String id, int page) {
        Market.Scope scope = market.require(player);
        Category category = categories.get(id);
        if (scope == null || category == null) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : category.entries) {
            if (entry.in(scope.id())) {
                entries.add(entry);
            }
        }
        int pages = Math.max(1, (entries.size() + INNER.length - 1) / INNER.length);
        int current = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu("market-shop-" + id, 6, market.text("shop-category-title", "%category%", Text.strip(Text.color(category.name))));
        menu.frame(Material.CYAN_STAINED_GLASS_PANE, null);
        for (int i = 0; i < INNER.length; i++) {
            int index = current * INNER.length + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            menu.set(INNER[i], display(scope, entry), clicker -> {
                buy(clicker, scope, entry);
                openCategory(clicker, id, current);
            }, clicker -> {
                if (entry.sell > 0) {
                    sellType(clicker, scope, entry);
                }
                openCategory(clicker, id, current);
            });
        }
        if (current > 0) {
            menu.set(45, Items.of(Material.ARROW).name(market.text("prev")).build(), clicker -> openCategory(clicker, id, current - 1));
        }
        if (current < pages - 1) {
            menu.set(53, Items.of(Material.ARROW).name(market.text("next")).build(), clicker -> openCategory(clicker, id, current + 1));
        }
        menu.set(49, Items.of(Material.BARRIER).name(market.text("back")).build(), this::open);
        menu.set(50, Items.of(Material.GOLD_INGOT).name(market.text("balance", "%money%", market.money(scope, market.balance(player, scope)))).build());
        menu.open(player);
    }

    private ItemStack display(Market.Scope scope, Entry entry) {
        ItemStack shown = entry.item.clone();
        ItemMeta meta = shown.getItemMeta();
        if (meta == null) {
            return shown;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color(market.text("shop-buy", "%price%", market.money(scope, buyPrice(scope.id(), entry)), "%amount%", String.valueOf(entry.item.getAmount()))));
        if (entry.sell > 0) {
            lore.add(Text.color(market.text("shop-sell", "%price%", market.money(scope, sellPrice(scope.id(), entry)), "%amount%", String.valueOf(entry.item.getAmount()))));
        }
        if (entry.special == null) {
            double factor = factor(scope.id(), entry.key);
            String trend = factor > 1.05 ? "shop-trend-up" : (factor < 0.95 ? "shop-trend-down" : "shop-trend-flat");
            lore.add(Text.color(market.text(trend, "%percent%", String.valueOf(Math.round(Math.abs(factor - 1) * 100)))));
        }
        lore.add("");
        lore.add(Text.color(market.text(entry.sell > 0 ? "shop-click-both" : "shop-click-buy")));
        meta.setLore(lore);
        shown.setItemMeta(meta);
        return shown;
    }

    private void buy(Player player, Market.Scope scope, Entry entry) {
        if (player.getInventory().firstEmpty() < 0) {
            market.send(player, "inventory-full");
            return;
        }
        long price = buyPrice(scope.id(), entry);
        if (!market.withdraw(player, scope, price, "market:shop-buy")) {
            market.send(player, "not-enough-money", "%price%", market.money(scope, price));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        Market.give(player, entry.item.clone());
        if (entry.special == null) {
            net.merge(scope.id() + ":" + entry.key, 1.0, Double::sum);
        }
        market.send(player, "shop-bought", "%amount%", String.valueOf(entry.item.getAmount()), "%item%", market.itemName(entry.item), "%price%", market.money(scope, price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
    }

    private void sellType(Player player, Market.Scope scope, Entry entry) {
        Material type = entry.item.getType();
        int amount = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == type && plain(item)) {
                amount += item.getAmount();
                contents[i] = null;
            }
        }
        if (amount == 0) {
            market.send(player, "sell-nothing");
            return;
        }
        player.getInventory().setStorageContents(contents);
        payOut(player, scope, new ItemStack(type), amount);
    }

    /** /sell ha (a kézben lévő köteg) és /sell all (minden eladható). */
    public void sellCommand(Player player, boolean all) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        long total = 0;
        int amount = 0;
        Map<Material, Integer> counts = new HashMap<>();
        if (!all) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            double unit = unitSellPrice(scope.id(), hand);
            if (unit <= 0) {
                market.send(player, "sell-not-sellable");
                return;
            }
            int handAmount = hand.getAmount();
            counts.put(hand.getType(), handAmount);
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (unitSellPrice(scope.id(), item) > 0) {
                    counts.merge(item.getType(), item.getAmount(), Integer::sum);
                    contents[i] = null;
                }
            }
            if (counts.isEmpty()) {
                market.send(player, "sell-nothing");
                return;
            }
            player.getInventory().setStorageContents(contents);
        }
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            ItemStack sample = new ItemStack(entry.getKey());
            total += Math.round(unitSellPrice(scope.id(), sample) * entry.getValue());
            amount += entry.getValue();
            sold(scope.id(), sample, entry.getValue());
        }
        long earned = market.core().ranks().boost(player, total);
        market.core().economy().deposit(player, scope.currency(), earned, "market:sell");
        market.send(player, "sold", "%amount%", Text.number(amount), "%money%", market.money(scope, earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private void payOut(Player player, Market.Scope scope, ItemStack sample, int amount) {
        long total = Math.round(unitSellPrice(scope.id(), sample) * amount);
        sold(scope.id(), sample, amount);
        long earned = market.core().ranks().boost(player, total);
        market.core().economy().deposit(player, scope.currency(), earned, "market:shop-sell");
        market.send(player, "sold", "%amount%", Text.number(amount), "%money%", market.money(scope, earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }
}
