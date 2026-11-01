package hu.icemine.core.bedwars;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A bolt, a csapatfejlesztések és a csapdák menüje. */
public final class ShopMenus {

    public static final int[] ITEM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    public static final int[] PROTECTION_PRICE = {2, 4, 8, 16};
    public static final int[] HASTE_PRICE = {2, 4};
    public static final int[] FORGE_PRICE = {2, 4, 6, 8};
    public static final int SHARP_PRICE = 4;
    public static final int HEAL_PRICE = 1;
    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V"};

    private final Bedwars plugin;

    public ShopMenus(Bedwars plugin) {
        this.plugin = plugin;
    }

    static String roman(int value) {
        return value >= 0 && value < ROMAN.length ? ROMAN[value] : String.valueOf(value);
    }

    private String msg(String key, String... pairs) {
        return plugin.core().messages().get("bedwars." + key, pairs);
    }

    private String raw(String key) {
        return plugin.core().messages().raw("bedwars." + key);
    }

    private List<String> list(String key) {
        return plugin.core().messages().list("bedwars." + key);
    }

    // ---- bolt ----

    public void openShop(Player player, BwArena arena, String category) {
        BwPlayer data = arena.data(player);
        BwTeam team = data != null && data.team != null ? arena.team(data.team) : null;
        if (team == null) {
            return;
        }
        Menu menu = new Menu("bw-shop", 6, raw("shop.title"));
        for (int i = 0; i < Shop.CATEGORIES.length; i++) {
            String[] entry = Shop.CATEGORIES[i];
            Material icon = entry[0].equals("blokk") ? team.color.terracotta() : Material.valueOf(entry[2]);
            boolean open = entry[0].equals(category);
            List<String> lore = new ArrayList<>();
            lore.add(msg(open ? "shop.category-open" : "shop.category-click"));
            menu.set(i, BwMenus.icon(icon, (open ? "&a&l" : "&e") + entry[1], lore), clicker -> openShop(clicker, arena, entry[0]));
            menu.set(9 + i, BwMenus.icon(open ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        menu.set(17, BwMenus.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        menu.set(8, BwMenus.icon(Material.BOOK, raw("shop.wallet-title"), wallet(player)));
        List<Shop.Offer> shown = new ArrayList<>();
        Shop shop = plugin.shop();
        if (category.equals("gyors")) {
            for (String id : shop.quick()) {
                Shop.Offer offer = shop.offers().get(id);
                if (offer != null) {
                    shown.add(offer);
                }
            }
        } else {
            for (Shop.Offer offer : shop.offers().values()) {
                if (offer.category.equals(category)) {
                    shown.add(offer);
                }
            }
        }
        for (int i = 0; i < shown.size() && i < ITEM_SLOTS.length; i++) {
            Shop.Offer offer = shown.get(i);
            menu.set(ITEM_SLOTS[i], display(player, data, team, offer), clicker -> {
                buy(clicker, arena, offer);
                openShop(clicker, arena, category);
            });
        }
        menu.open(player);
    }

    private List<String> wallet(Player player) {
        List<String> lines = new ArrayList<>();
        for (Generator.Type type : Generator.Type.values()) {
            lines.add(Text.color(Shop.currencyColor(type) + Shop.currencyName(type) + "&7: &f" + BwItems.count(player, type.item())));
        }
        return lines;
    }

    /** {ár, pénznem sorszáma}, vagy null, ha már nincs jobb szint. */
    private int[] price(Shop.Offer offer, BwPlayer data) {
        if (offer.kind != Shop.Kind.PICKAXE && offer.kind != Shop.Kind.AXE) {
            return new int[]{offer.price, offer.currency.ordinal()};
        }
        int next = (offer.kind == Shop.Kind.PICKAXE ? data.pickaxe : data.axe) + 1;
        return next > 4 ? null : new int[]{Shop.TOOL_PRICE[next - 1], Shop.TOOL_CURRENCY[next - 1].ordinal()};
    }

    private String name(Shop.Offer offer, BwPlayer data) {
        if (offer.kind == Shop.Kind.PICKAXE) {
            return Shop.PICKAXE_NAMES[Math.min(4, data.pickaxe + 1) - 1] + " csákány";
        }
        if (offer.kind == Shop.Kind.AXE) {
            return Shop.AXE_NAMES[Math.min(4, data.axe + 1) - 1] + " balta";
        }
        return offer.name + (offer.amount > 1 ? " &7x" + offer.amount : "");
    }

    private ItemStack display(Player player, BwPlayer data, BwTeam team, Shop.Offer offer) {
        int[] price = price(offer, data);
        boolean owned = price == null || (offer.kind == Shop.Kind.ARMOR && data.armor >= offer.value) || (offer.kind == Shop.Kind.SHEARS && data.shears);
        List<String> lore = new ArrayList<>();
        if (price != null) {
            lore.add(Text.color("&7Ár: " + Shop.priceText(Generator.Type.values()[price[1]], price[0])));
        }
        for (String extra : offer.extra) {
            if (!extra.contains(":")) {
                lore.add(Text.color("&7" + extra));
            }
        }
        lore.add("");
        if (owned) {
            lore.add(msg("shop.owned"));
        } else {
            Generator.Type type = Generator.Type.values()[price[1]];
            lore.add(BwItems.count(player, type.item()) >= price[0] ? msg("shop.click") : msg("shop.not-enough"));
        }
        Material material = plugin.shop().display(offer, data, team.color);
        ItemStack icon = BwMenus.icon(material, (owned ? "&a" : "&f") + name(offer, data), lore);
        icon.setAmount(Math.max(1, Math.min(64, offer.amount)));
        return icon;
    }

    private boolean pay(Player player, Generator.Type type, int amount) {
        int have = BwItems.count(player, type.item());
        if (have < amount) {
            plugin.core().messages().send(player, "bedwars.shop.missing", "%missing%", String.valueOf(amount - have), "%currency%",
                    Shop.currencyColor(type) + Shop.currencyName(type));
            player.playSound(player.getLocation(), "minecraft:entity.enderman.teleport", 0.8F, 0.5F);
            return false;
        }
        BwItems.take(player, type.item(), amount);
        return true;
    }

    private void bought(Player player, String item) {
        player.sendActionBar(msg("shop.bought", "%item%", item));
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.8F, 1.6F);
    }

    public void buy(Player player, BwArena arena, Shop.Offer offer) {
        BwPlayer data = arena.data(player);
        if (data == null || data.team == null || !data.playing()) {
            return;
        }
        BwTeam team = arena.team(data.team);
        int[] price = price(offer, data);
        if (price == null) {
            plugin.core().messages().send(player, "bedwars.shop.max-tier");
            return;
        }
        Generator.Type type = Generator.Type.values()[price[1]];
        String label = Text.color(name(offer, data));
        switch (offer.kind) {
            case ARMOR:
                if (data.armor >= offer.value) {
                    plugin.core().messages().send(player, "bedwars.shop.already-owned");
                    return;
                }
                if (!pay(player, type, price[0])) {
                    return;
                }
                data.armor = offer.value;
                BwItems.armor(player, data, team);
                break;
            case SHEARS:
                if (data.shears) {
                    plugin.core().messages().send(player, "bedwars.shop.already-owned");
                    return;
                }
                if (!pay(player, type, price[0])) {
                    return;
                }
                data.shears = true;
                BwItems.give(player, BwItems.permanent(new ItemStack(Material.SHEARS), "shears"));
                break;
            case PICKAXE:
                if (!pay(player, type, price[0])) {
                    return;
                }
                data.pickaxe = Math.min(4, data.pickaxe + 1);
                BwItems.replaceTagged(player, "pickaxe", BwItems.pickaxe(data.pickaxe, team));
                break;
            case AXE:
                if (!pay(player, type, price[0])) {
                    return;
                }
                data.axe = Math.min(4, data.axe + 1);
                BwItems.replaceTagged(player, "axe", BwItems.axe(data.axe, team));
                break;
            case SWORD:
                if (!pay(player, type, price[0])) {
                    return;
                }
                BwItems.removeTagged(player, "sword:base");
                BwItems.give(player, plugin.shop().product(offer, team));
                break;
            default:
                if (!pay(player, type, price[0])) {
                    return;
                }
                BwItems.give(player, plugin.shop().product(offer, team));
        }
        bought(player, label);
    }

    // ---- csapatfejlesztések és csapdák ----

    public void openUpgrades(Player player, BwArena arena) {
        BwPlayer data = arena.data(player);
        BwTeam team = data != null && data.team != null ? arena.team(data.team) : null;
        if (team == null) {
            return;
        }
        Menu menu = new Menu("bw-upgrades", 5, raw("upgrade.title"));
        menu.frame(Material.GRAY_STAINED_GLASS_PANE, null);
        menu.set(4, BwMenus.icon(Material.BOOK, raw("shop.wallet-title"), wallet(player)));
        menu.set(10, upgradeIcon(player, Material.IRON_SWORD, "upgrade.sharp", team.sharpness, 1, new int[]{SHARP_PRICE}), clicker -> upgrade(clicker, arena, "sharp"));
        menu.set(11, upgradeIcon(player, Material.IRON_CHESTPLATE, "upgrade.protection", team.protection, 4, PROTECTION_PRICE), clicker -> upgrade(clicker, arena, "protection"));
        menu.set(12, upgradeIcon(player, Material.GOLDEN_PICKAXE, "upgrade.haste", team.haste, 2, HASTE_PRICE), clicker -> upgrade(clicker, arena, "haste"));
        menu.set(13, upgradeIcon(player, Material.FURNACE, "upgrade.forge", team.forge, 4, FORGE_PRICE), clicker -> upgrade(clicker, arena, "forge"));
        menu.set(14, upgradeIcon(player, Material.BEACON, "upgrade.heal", team.healPool ? 1 : 0, 1, new int[]{HEAL_PRICE}), clicker -> upgrade(clicker, arena, "heal"));
        menu.set(16, BwMenus.icon(Material.TRIPWIRE_HOOK, raw("trap.menu-title"), list("trap.menu-lore")));
        int price = Trap.price(team.traps.size());
        boolean full = team.traps.size() >= plugin.settings().maxTraps;
        int slot = 28;
        for (Trap trap : Trap.values()) {
            List<String> lore = new ArrayList<>(list("trap." + trap.id() + ".lore"));
            lore.add("");
            lore.add(Text.color("&7Ár: &b" + price + " Gyémánt"));
            lore.add("");
            lore.add(full ? msg("trap.full") : (BwItems.count(player, Material.DIAMOND) >= price ? msg("shop.click") : msg("shop.not-enough")));
            menu.set(slot++, BwMenus.icon(Material.valueOf(trap.icon), "&e" + raw("trap." + trap.id() + ".name"), lore), clicker -> buyTrap(clicker, arena, trap));
        }
        List<Trap> queue = new ArrayList<>(team.traps);
        for (int i = 0; i < plugin.settings().maxTraps && i < 3; i++) {
            ItemStack icon;
            if (i < queue.size()) {
                Trap trap = queue.get(i);
                icon = BwMenus.icon(Material.valueOf(trap.icon), msg("trap.queued", "%number%", String.valueOf(i + 1)) + " " + raw("trap." + trap.id() + ".name"),
                        list("trap.queued-lore"));
            } else {
                icon = BwMenus.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, msg("trap.empty", "%number%", String.valueOf(i + 1)), list("trap.empty-lore"));
            }
            menu.set(33 + i, icon);
        }
        menu.open(player);
    }

    private ItemStack upgradeIcon(Player player, Material material, String key, int level, int max, int[] prices) {
        List<String> lore = new ArrayList<>(list(key + "-lore"));
        lore.add("");
        if (max > 1) {
            for (int i = 0; i < max; i++) {
                lore.add(Text.color((level > i ? "&a" : "&7") + roman(i + 1) + " &8- &b" + prices[i] + " Gyémánt"));
            }
        } else {
            lore.add(Text.color("&7Ár: &b" + prices[0] + " Gyémánt"));
        }
        lore.add("");
        if (level >= max) {
            lore.add(msg("upgrade.maxed"));
        } else {
            int next = prices[Math.min(level, prices.length - 1)];
            lore.add(BwItems.count(player, Material.DIAMOND) >= next ? msg("shop.click") : msg("shop.not-enough"));
        }
        String title = raw(key) + (max > 1 && level > 0 ? " " + roman(level) : "");
        return BwMenus.icon(material, (level >= max ? "&a" : "&e") + title, lore);
    }

    public void upgrade(Player player, BwArena arena, String which) {
        BwPlayer data = arena.data(player);
        if (data == null || data.team == null || !data.playing()) {
            return;
        }
        BwTeam team = arena.team(data.team);
        int level;
        int max;
        int[] prices;
        switch (which) {
            case "sharp":
                level = team.sharpness;
                max = 1;
                prices = new int[]{SHARP_PRICE};
                break;
            case "protection":
                level = team.protection;
                max = 4;
                prices = PROTECTION_PRICE;
                break;
            case "haste":
                level = team.haste;
                max = 2;
                prices = HASTE_PRICE;
                break;
            case "forge":
                level = team.forge;
                max = 4;
                prices = FORGE_PRICE;
                break;
            default:
                level = team.healPool ? 1 : 0;
                max = 1;
                prices = new int[]{HEAL_PRICE};
        }
        if (level >= max) {
            plugin.core().messages().send(player, "bedwars.upgrade.already");
            openUpgrades(player, arena);
            return;
        }
        if (!pay(player, Generator.Type.DIAMOND, prices[level])) {
            openUpgrades(player, arena);
            return;
        }
        switch (which) {
            case "sharp":
                team.sharpness = 1;
                break;
            case "protection":
                team.protection++;
                break;
            case "haste":
                team.haste++;
                break;
            case "forge":
                team.forge++;
                break;
            default:
                team.healPool = true;
        }
        arena.applyUpgrades(team);
        String name = raw("upgrade." + which) + (max > 1 ? " " + roman(level + 1) : "");
        arena.teamMessage(team, "upgrade.bought", "%player%", player.getName(), "%upgrade%", name);
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.8F, 1.8F);
        openUpgrades(player, arena);
    }

    public void buyTrap(Player player, BwArena arena, Trap trap) {
        BwPlayer data = arena.data(player);
        if (data == null || data.team == null || !data.playing()) {
            return;
        }
        BwTeam team = arena.team(data.team);
        if (team.traps.size() >= plugin.settings().maxTraps) {
            plugin.core().messages().send(player, "bedwars.trap.full");
            openUpgrades(player, arena);
            return;
        }
        if (!pay(player, Generator.Type.DIAMOND, Trap.price(team.traps.size()))) {
            openUpgrades(player, arena);
            return;
        }
        team.traps.add(trap);
        arena.teamMessage(team, "trap.bought", "%player%", player.getName(), "%trap%", raw("trap." + trap.id() + ".name"));
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.8F, 1.8F);
        openUpgrades(player, arena);
    }
}
