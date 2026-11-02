package hu.icemine.core.market;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Aukciósház (/ah): a kezedben lévő tárgyat fix áron kiteszed (/ah sell &lt;ár&gt;), más megveszi, a pénz (az adó
 * levonása után) a tiéd, akkor is, ha épp nem vagy fent. Ami lejár, a "Lejárt tárgyaim" menüben visszakéred.
 */
public final class AuctionHouse {

    /** Egy hirdetés. */
    static final class Listing {
        final String id;
        final String scope;
        final UUID seller;
        final String sellerName;
        final long price;
        final ItemStack item;
        final String itemText;
        final long created;
        final long expires;
        String status;

        Listing(String id, String scope, UUID seller, String sellerName, long price, ItemStack item, String itemText, long created, long expires, String status) {
            this.id = id;
            this.scope = scope;
            this.seller = seller;
            this.sellerName = sellerName;
            this.price = price;
            this.item = item;
            this.itemText = itemText;
            this.created = created;
            this.expires = expires;
            this.status = status;
        }

        MarketStore.AuctionRow row() {
            return new MarketStore.AuctionRow(id, scope, seller.toString(), sellerName, price, itemText, created, expires, status);
        }
    }

    private static final int PAGE = 45;

    private final Market market;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, String> confirm = new HashMap<>();
    private int maxListings = 5;
    private long duration = 48 * 3_600_000L;
    private double tax = 0.05;
    private long minPrice = 1;
    private long maxPrice = 1_000_000_000L;

    AuctionHouse(Market market) {
        this.market = market;
    }

    void loadAll() throws Exception {
        listings.clear();
        for (MarketStore.AuctionRow row : market.store().loadAuctions()) {
            ItemStack item = market.store().deserialize(row.item());
            UUID seller = parse(row.seller());
            if (item == null || seller == null) {
                continue;
            }
            listings.put(row.id(), new Listing(row.id(), row.scope(), seller, row.sellerName(), row.price(), item, row.item(), row.created(), row.expires(), row.status()));
        }
    }

    void load(YamlConfiguration config, List<String> problems) {
        maxListings = Math.max(1, config.getInt("auction.max-listings", 5));
        duration = Math.max(1, config.getLong("auction.duration-hours", 48)) * 3_600_000L;
        tax = Math.max(0, Math.min(0.5, config.getDouble("auction.tax-percent", 5) / 100.0));
        minPrice = Math.max(1, config.getLong("auction.min-price", 1));
        maxPrice = Math.max(minPrice, config.getLong("auction.max-price", 1_000_000_000L));
    }

    private static UUID parse(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private List<Listing> active(String scope) {
        long now = System.currentTimeMillis();
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.scope.equals(scope) && listing.status.equals("active") && listing.expires > now) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparingLong((Listing listing) -> listing.created).reversed());
        return result;
    }

    private List<Listing> mine(String scope, UUID player, String status) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.scope.equals(scope) && listing.seller.equals(player) && listing.status.equals(status)) {
                result.add(listing);
            }
        }
        return result;
    }

    /** Percenként: a lejárt hirdetések átkerülnek a "Lejárt tárgyaim" közé. */
    void expire() {
        long now = System.currentTimeMillis();
        for (Listing listing : listings.values()) {
            if (listing.status.equals("active") && listing.expires <= now) {
                listing.status = "expired";
                market.store().setAuctionStatus(listing.id, "expired");
            }
        }
    }

    public void sell(Player player, String priceText) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        long price;
        try {
            price = Long.parseLong(priceText.replace(".", "").replace(" ", "").replace("k", "000"));
        } catch (NumberFormatException exception) {
            market.send(player, "ah-usage");
            return;
        }
        if (price < minPrice || price > maxPrice) {
            market.send(player, "ah-price-range", "%min%", market.money(scope, minPrice), "%max%", market.money(scope, maxPrice));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            market.send(player, "ah-empty-hand");
            return;
        }
        if (Items.tagOf(hand) != null || Items.kitOf(hand) != null) {
            market.send(player, "ah-not-allowed");
            return;
        }
        int limit = maxListings + (player.hasPermission("icemine.market.ah.extra") ? maxListings : 0);
        if (mine(scope.id(), player.getUniqueId(), "active").size() >= limit) {
            market.send(player, "ah-limit", "%max%", String.valueOf(limit));
            return;
        }
        ItemStack item = hand.clone();
        player.getInventory().setItemInMainHand(null);
        long now = System.currentTimeMillis();
        Listing listing = new Listing(UUID.randomUUID().toString(), scope.id(), player.getUniqueId(), player.getName(), price, item,
                MarketStore.serialize(item), now, now + duration, "active");
        listings.put(listing.id, listing);
        market.store().saveAuction(listing.row());
        market.send(player, "ah-listed", "%item%", market.itemName(item), "%amount%", String.valueOf(item.getAmount()), "%price%", market.money(scope, price),
                "%hours%", String.valueOf(duration / 3_600_000L));
    }

    public void open(Player player, int page) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        List<Listing> all = active(scope.id());
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int current = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu("market-ah", 6, market.text("ah-title", "%page%", String.valueOf(current + 1), "%pages%", String.valueOf(pages)));
        for (int i = 0; i < PAGE; i++) {
            int index = current * PAGE + i;
            if (index >= all.size()) {
                break;
            }
            Listing listing = all.get(index);
            boolean own = listing.seller.equals(player.getUniqueId());
            menu.set(i, display(listing, scope, own ? "ah-lore-own" : "ah-lore-buy"), clicker -> {
                if (own) {
                    cancel(clicker, listing.id);
                } else {
                    buy(clicker, listing.id);
                }
                open(clicker, current);
            });
        }
        ItemStack filler = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = 45; slot < 54; slot++) {
            menu.set(slot, filler);
        }
        if (current > 0) {
            menu.set(45, Items.of(Material.ARROW).name(market.text("prev")).build(), clicker -> open(clicker, current - 1));
        }
        if (current < pages - 1) {
            menu.set(53, Items.of(Material.ARROW).name(market.text("next")).build(), clicker -> open(clicker, current + 1));
        }
        menu.set(48, Items.of(Material.CHEST).name(market.text("ah-mine")).lore(market.texts("ah-mine-lore",
                "%count%", String.valueOf(mine(scope.id(), player.getUniqueId(), "active").size()))).build(), clicker -> openMine(clicker));
        menu.set(49, Items.of(Material.BOOK).name(market.text("ah-help")).lore(market.texts("ah-help-lore",
                "%tax%", Text.decimal(tax * 100), "%hours%", String.valueOf(duration / 3_600_000L))).build());
        int expired = mine(scope.id(), player.getUniqueId(), "expired").size();
        menu.set(50, Items.of(expired > 0 ? Material.HOPPER : Material.MINECART).name(market.text("ah-expired")).lore(market.texts("ah-expired-lore",
                "%count%", String.valueOf(expired))).build(), clicker -> openExpired(clicker));
        menu.open(player);
    }

    private ItemStack display(Listing listing, Market.Scope scope, String loreKey) {
        ItemStack shown = listing.item.clone();
        ItemMeta meta = shown.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            long left = Math.max(0, listing.expires - System.currentTimeMillis());
            for (String line : market.texts(loreKey, "%seller%", listing.sellerName, "%price%", market.money(scope, listing.price), "%time%", Text.duration(left))) {
                lore.add(Text.color(line));
            }
            meta.setLore(lore);
            shown.setItemMeta(meta);
        }
        return shown;
    }

    void openMine(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        Menu menu = new Menu("market-ah-mine", 3, market.text("ah-mine-title"));
        int slot = 0;
        for (Listing listing : mine(scope.id(), player.getUniqueId(), "active")) {
            if (slot >= 18) {
                break;
            }
            menu.set(slot++, display(listing, scope, "ah-lore-own"), clicker -> {
                cancel(clicker, listing.id);
                openMine(clicker);
            });
        }
        menu.set(22, Items.of(Material.ARROW).name(market.text("back")).build(), clicker -> open(clicker, 0));
        menu.open(player);
    }

    void openExpired(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        Menu menu = new Menu("market-ah-expired", 3, market.text("ah-expired-title"));
        int slot = 0;
        for (Listing listing : mine(scope.id(), player.getUniqueId(), "expired")) {
            if (slot >= 18) {
                break;
            }
            menu.set(slot++, display(listing, scope, "ah-lore-claim"), clicker -> {
                claim(clicker, listing.id);
                openExpired(clicker);
            });
        }
        menu.set(22, Items.of(Material.ARROW).name(market.text("back")).build(), clicker -> open(clicker, 0));
        menu.open(player);
    }

    private boolean roomFor(Player player) {
        return player.getInventory().firstEmpty() >= 0;
    }

    void buy(Player player, String id) {
        Listing listing = listings.get(id);
        Market.Scope scope = market.scopeOf(player);
        if (listing == null || scope == null || !listing.scope.equals(scope.id()) || !listing.status.equals("active") || listing.expires <= System.currentTimeMillis()) {
            market.send(player, "ah-gone");
            return;
        }
        if (listing.seller.equals(player.getUniqueId())) {
            return;
        }
        if (!roomFor(player)) {
            market.send(player, "inventory-full");
            return;
        }
        if (!id.equals(confirm.get(player.getUniqueId()))) {
            confirm.put(player.getUniqueId(), id);
            market.send(player, "ah-confirm", "%item%", market.itemName(listing.item), "%price%", market.money(scope, listing.price));
            return;
        }
        confirm.remove(player.getUniqueId());
        if (!market.withdraw(player, scope, listing.price, "market:ah-buy")) {
            market.send(player, "not-enough-money", "%price%", market.money(scope, listing.price));
            return;
        }
        listing.status = "sold";
        market.store().setAuctionStatus(listing.id, "sold");
        listings.remove(listing.id);
        Market.give(player, listing.item.clone());
        long earned = listing.price - Math.round(listing.price * tax);
        market.pay(listing.seller, scope, earned, "market:ah-sold");
        market.send(player, "ah-bought", "%item%", market.itemName(listing.item), "%price%", market.money(scope, listing.price), "%seller%", listing.sellerName);
        Player seller = org.bukkit.Bukkit.getPlayer(listing.seller);
        if (seller != null) {
            market.send(seller, "ah-sold", "%item%", market.itemName(listing.item), "%buyer%", player.getName(), "%money%", market.money(scope, earned));
        }
    }

    void cancel(Player player, String id) {
        Listing listing = listings.get(id);
        if (listing == null || !listing.seller.equals(player.getUniqueId()) || !listing.status.equals("active")) {
            return;
        }
        Market.Scope scope = market.scopeOf(player);
        if (scope == null || !scope.id().equals(listing.scope)) {
            market.send(player, "only-in-scope");
            return;
        }
        if (!roomFor(player)) {
            market.send(player, "inventory-full");
            return;
        }
        listing.status = "cancelled";
        market.store().setAuctionStatus(listing.id, "cancelled");
        listings.remove(listing.id);
        Market.give(player, listing.item.clone());
        market.send(player, "ah-cancelled", "%item%", market.itemName(listing.item));
    }

    void claim(Player player, String id) {
        Listing listing = listings.get(id);
        Market.Scope scope = market.scopeOf(player);
        if (listing == null || scope == null || !listing.seller.equals(player.getUniqueId()) || !listing.status.equals("expired") || !scope.id().equals(listing.scope)) {
            return;
        }
        if (!roomFor(player)) {
            market.send(player, "inventory-full");
            return;
        }
        listing.status = "claimed";
        market.store().setAuctionStatus(listing.id, "claimed");
        listings.remove(listing.id);
        Market.give(player, listing.item.clone());
        market.send(player, "ah-claimed", "%item%", market.itemName(listing.item));
    }

    int count(String scope) {
        return active(scope).size();
    }
}
