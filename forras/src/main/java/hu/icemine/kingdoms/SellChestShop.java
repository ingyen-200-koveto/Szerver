package hu.icemine.kingdoms;

import hu.icemine.core.economy.IceCoins;
import hu.icemine.core.market.Specials;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Eladó ládák IceCoinért a Kingdomsban: sima, 1,5x, 2x és 5x szorzós. Az árak a kingdoms.yml-ben
 * (sellchest-shop), az IceCoint a weboldal vonja le, a láda csak sikeres levonás után kerül a játékoshoz.
 */
public final class SellChestShop {

    private static final String[] TYPES = {"sellchest", "sellchest_15", "sellchest_2", "sellchest_5"};
    private static final Material[] ICONS = {Material.CHEST, Material.CHEST, Material.CHEST, Material.CHEST};

    private final IceMineKingdoms plugin;
    private final Map<String, Long> prices = new LinkedHashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    SellChestShop(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    void load() {
        prices.clear();
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        if (config.getLong("sellchest-shop.sellchest", -1) == 300 && config.getLong("sellchest-shop.sellchest_5", -1) == 4000) {
            config.set("sellchest-shop.sellchest", 590);
            config.set("sellchest-shop.sellchest_15", 990);
            config.set("sellchest-shop.sellchest_2", 1450);
            config.set("sellchest-shop.sellchest_5", 2100);
            plugin.saveConfig();
        }
        long[] defaults = {590, 990, 1450, 2100};
        for (int i = 0; i < TYPES.length; i++) {
            prices.put(TYPES[i], Math.max(0, plugin.getConfig().getLong("sellchest-shop." + TYPES[i], defaults[i])));
        }
    }

    private Specials specials() {
        return plugin.core().market() == null ? null : plugin.core().market().specials();
    }

    /** A /shop Különleges kategóriájában mutatott ikon: a láda IceCoin árral. */
    public ItemStack icon(Player player, String type) {
        Specials specials = specials();
        ItemStack base = specials == null ? null : specials.item(type);
        if (base == null) {
            return new ItemStack(Material.CHEST);
        }
        IceCoins coins = plugin.core().iceCoins();
        List<String> lore = new ArrayList<>();
        lore.add("&7Eladja, amit beleteszel vagy a");
        lore.add("&7közelébe esik, és a pénz a tiéd.");
        lore.add("&eEladási szorzó: &6&l" + Specials.multiplierText(type));
        lore.add("");
        lore.add("&7Ár: &b" + prices.getOrDefault(type, 0L) + " IceCoin");
        lore.add("&7IceCoinod: &b" + (coins == null ? "-" : coins.display(player)));
        lore.add("");
        lore.add("&e» Kattints a vásárláshoz");
        ItemStack icon = Items.of(Material.CHEST).name(displayName(base)).lore(lore).glow().hideAll().build();
        icon.setAmount(switch (type) {
            case "sellchest_2" -> 2;
            case "sellchest_5" -> 5;
            default -> 1;
        });
        return icon;
    }

    public void open(Player player) {
        Specials specials = specials();
        if (specials == null) {
            player.sendMessage(Text.color("&cA piac most nem elérhető."));
            return;
        }
        IceCoins coins = plugin.core().iceCoins();
        Menu menu = new Menu("kingdoms-sellchest", 3, Text.color("&6&lEladó ládák &8» &fIceCoin"));
        menu.frame(Material.ORANGE_STAINED_GLASS_PANE, null);
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < TYPES.length; i++) {
            String type = TYPES[i];
            long price = prices.get(type);
            if (price <= 0) {
                continue;
            }
            ItemStack base = specials.item(type);
            if (base == null) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Eladja, amit beleteszel vagy a");
            lore.add("&7közelébe esik, és a pénz a tiéd.");
            lore.add("&eEladási szorzó: &6&l" + Specials.multiplierText(type));
            lore.add("");
            lore.add("&7Ár: &b" + price + " IceCoin");
            lore.add("&7IceCoinod: &b" + (coins == null ? "-" : coins.display(player)));
            lore.add("");
            lore.add("&e» Kattints a vásárláshoz");
            ItemStack icon = Items.of(ICONS[i]).name(displayName(base)).lore(lore).glow().hideAll().build();
            icon.setAmount(new int[]{1, 1, 2, 5}[i]);
            menu.set(slots[i], icon, p -> confirm(p, type, () -> open(p)));
        }
        menu.set(22, Items.of(Material.BOOK).name("&e&lMi ez?").lore(List.of("&7Az IceCoint a weboldalon kapod", "&7(vásárlás, szavazás, kerék).", "&7A láda csak a Kingdomsban rakható le.")).build());
        menu.open(player);
    }

    @SuppressWarnings("deprecation")
    private static String displayName(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "Eladó láda";
    }

    public void confirm(Player player, String type, Runnable back) {
        long price = prices.getOrDefault(type, 0L);
        Specials specials = specials();
        if (price <= 0 || specials == null) {
            return;
        }
        Menu menu = new Menu("kingdoms-sellchest-confirm", 3, Text.color("&8Biztosan megveszed?"));
        menu.frame(Material.GRAY_STAINED_GLASS_PANE, null);
        menu.set(13, specials.item(type));
        menu.set(11, Items.of(Material.LIME_CONCRETE).name("&a&lMegveszem").lore(List.of("&7Ár: &b" + price + " IceCoin")).build(), p -> buy(p, type, price));
        menu.set(15, Items.of(Material.RED_CONCRETE).name("&c&lMégse").build(), p -> back.run());
        menu.open(player);
    }

    private void buy(Player player, String type, long price) {
        player.closeInventory();
        IceCoins coins = plugin.core().iceCoins();
        Specials specials = specials();
        if (coins == null || specials == null || !coins.hasKey()) {
            player.sendMessage(Text.color("&cMost nem tudok IceCoint levonni, próbáld később."));
            return;
        }
        Long have = coins.balance(player);
        if (have != null && have < price) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 1F);
            player.sendMessage(Text.color("&cNincs elég IceCoinod. Kell: &b" + price + "&c, van: &b" + have + "&c."));
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage(Text.color("&7Vásárlás folyamatban..."));
        String name = hu.icemine.core.util.Text.strip(displayName(specials.item(type)));
        coins.spend(player, price, name + " (Kingdoms)", (result, balance) -> {
            busy.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            switch (result) {
                case OK -> {
                    ItemStack item = specials.item(type);
                    for (ItemStack left : player.getInventory().addItem(item).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.2F);
                    player.sendMessage(Text.color("&aMegvetted: " + displayName(item) + "&a! Maradt: &b" + (balance == null ? "?" : balance) + " IceCoin"));
                }
                case NOT_ENOUGH -> player.sendMessage(Text.color("&cNincs elég IceCoinod" + (balance == null ? "." : " (van: &b" + balance + "&c).")));
                case NO_ACCOUNT -> player.sendMessage(Text.color("&cNincs megerősített fiókod a weboldalon ezzel a névvel."));
                default -> player.sendMessage(Text.color("&cNem értem el a weboldalt, nem vontam le semmit. Próbáld újra később."));
            }
        });
    }
}
