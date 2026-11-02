package hu.icemine.core.market;

import hu.icemine.core.economy.Ledger;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Biztonságos csere (/trade &lt;játékos&gt;): egy közös ablak, bal oldalon az egyik, jobb oldalon a másik játékos ajánlata
 * (tárgyak és pénz). Ha mindketten "Kész"-re kattintanak, 3 másodperc múlva megtörténik a csere. Ha bármelyik fél
 * változtat, a "Kész" visszaáll; ha valaki bezárja, mindenki visszakapja a cuccát.
 */
public final class TradeManager implements Listener {

    private static final int[] LEFT = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39};
    private static final int[] RIGHT = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44};
    private static final int READY_A = 45;
    private static final int MONEY_A = 46;
    private static final int MONEY_B = 52;
    private static final int READY_B = 53;

    /** Egy folyamatban lévő csere. */
    final class Trade implements InventoryHolder {
        final UUID a;
        final UUID b;
        final Market.Scope scope;
        Inventory inventory;
        boolean readyA;
        boolean readyB;
        long moneyA;
        long moneyB;
        int countdown = -1;
        boolean finished;

        Trade(UUID a, UUID b, Market.Scope scope) {
            this.a = a;
            this.b = b;
            this.scope = scope;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        boolean isA(UUID player) {
            return a.equals(player);
        }

        int[] side(UUID player) {
            return isA(player) ? LEFT : RIGHT;
        }
    }

    private record Request(UUID from, long until) {
    }

    private final Market market;
    private final Map<UUID, Trade> active = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();

    TradeManager(Market market) {
        this.market = market;
    }

    public void request(Player player, String targetName) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(player) || market.scopeOf(target) != scope) {
            market.send(player, "trade-not-here", "%player%", targetName);
            return;
        }
        if (active.containsKey(player.getUniqueId()) || active.containsKey(target.getUniqueId())) {
            market.send(player, "trade-busy");
            return;
        }
        Request incoming = requests.get(player.getUniqueId());
        if (incoming != null && incoming.from.equals(target.getUniqueId()) && incoming.until > System.currentTimeMillis()) {
            requests.remove(player.getUniqueId());
            open(target, player, scope);
            return;
        }
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis() + 60_000L));
        market.send(player, "trade-sent", "%player%", target.getName());
        market.click(target, "trade-received", "%player%", player.getName());
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    public void accept(Player player) {
        Request request = requests.remove(player.getUniqueId());
        Player from = request == null ? null : Bukkit.getPlayer(request.from);
        if (from == null || request.until < System.currentTimeMillis()) {
            market.send(player, "trade-no-request");
            return;
        }
        Market.Scope scope = market.scopeOf(player);
        if (scope == null || market.scopeOf(from) != scope) {
            market.send(player, "trade-not-here", "%player%", from.getName());
            return;
        }
        if (active.containsKey(player.getUniqueId()) || active.containsKey(from.getUniqueId())) {
            market.send(player, "trade-busy");
            return;
        }
        open(from, player, scope);
    }

    private void open(Player a, Player b, Market.Scope scope) {
        Trade trade = new Trade(a.getUniqueId(), b.getUniqueId(), scope);
        trade.inventory = Bukkit.createInventory(trade, 54, Text.color(market.text("trade-title", "%a%", a.getName(), "%b%", b.getName())));
        active.put(a.getUniqueId(), trade);
        active.put(b.getUniqueId(), trade);
        refresh(trade);
        a.openInventory(trade.inventory);
        b.openInventory(trade.inventory);
    }

    private void refresh(Trade trade) {
        Inventory inventory = trade.inventory;
        ItemStack separator = Items.of(Material.IRON_BARS).name(" ").build();
        for (int row = 0; row < 5; row++) {
            inventory.setItem(row * 9 + 4, separator);
        }
        ItemStack bottom = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, bottom);
        }
        Player a = Bukkit.getPlayer(trade.a);
        Player b = Bukkit.getPlayer(trade.b);
        String nameA = a == null ? "?" : a.getName();
        String nameB = b == null ? "?" : b.getName();
        inventory.setItem(READY_A, ready(trade.readyA, nameA));
        inventory.setItem(READY_B, ready(trade.readyB, nameB));
        inventory.setItem(MONEY_A, money(trade, trade.moneyA, nameA));
        inventory.setItem(MONEY_B, money(trade, trade.moneyB, nameB));
        String status = trade.countdown >= 0 ? market.text("trade-countdown", "%seconds%", String.valueOf(trade.countdown)) : market.text("trade-waiting");
        inventory.setItem(49, Items.of(trade.countdown >= 0 ? Material.CLOCK : Material.PAPER).name(status).lore(market.texts("trade-help")).build());
    }

    private ItemStack ready(boolean ready, String name) {
        return Items.of(ready ? Material.LIME_DYE : Material.RED_DYE).name(market.text(ready ? "trade-ready" : "trade-not-ready", "%player%", name))
                .lore(market.texts("trade-ready-lore")).build();
    }

    private ItemStack money(Trade trade, long amount, String name) {
        return Items.of(Material.GOLD_INGOT).name(market.text("trade-money", "%player%", name, "%money%", market.money(trade.scope, amount)))
                .lore(market.texts("trade-money-lore")).build();
    }

    private static boolean contains(int[] slots, int slot) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private void changed(Trade trade) {
        trade.readyA = false;
        trade.readyB = false;
        trade.countdown = -1;
        Bukkit.getScheduler().runTask(market.core(), () -> {
            if (!trade.finished) {
                refresh(trade);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Trade trade) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (trade.finished) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        int[] own = trade.side(player.getUniqueId());
        boolean top = raw >= 0 && raw < 54;
        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (!top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (moving == null || moving.getType().isAir() || Items.tagOf(moving) != null || Items.kitOf(moving) != null) {
                    return;
                }
                for (int slot : own) {
                    ItemStack there = trade.inventory.getItem(slot);
                    if (there == null || there.getType().isAir()) {
                        trade.inventory.setItem(slot, moving.clone());
                        event.setCurrentItem(null);
                        changed(trade);
                        return;
                    }
                }
            }
            return;
        }
        if (raw == (trade.isA(player.getUniqueId()) ? READY_A : READY_B)) {
            event.setCancelled(true);
            toggleReady(trade, player);
            return;
        }
        if (raw == (trade.isA(player.getUniqueId()) ? MONEY_A : MONEY_B)) {
            event.setCancelled(true);
            long step = event.isShiftClick() ? 10_000L : (event.getClick() == ClickType.MIDDLE ? 1_000L : 100L);
            adjustMoney(trade, player, event.isRightClick() ? -step : step);
            return;
        }
        if (!contains(own, raw)) {
            event.setCancelled(true);
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && (Items.tagOf(cursor) != null || Items.kitOf(cursor) != null)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (hotbar != null && (Items.tagOf(hotbar) != null || Items.kitOf(hotbar) != null)) {
                event.setCancelled(true);
                return;
            }
        }
        changed(trade);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Trade trade) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int[] own = trade.side(player.getUniqueId());
        for (int raw : event.getRawSlots()) {
            if (raw < 54 && !contains(own, raw)) {
                event.setCancelled(true);
                return;
            }
        }
        changed(trade);
    }

    private void toggleReady(Trade trade, Player player) {
        if (trade.isA(player.getUniqueId())) {
            trade.readyA = !trade.readyA;
        } else {
            trade.readyB = !trade.readyB;
        }
        trade.countdown = trade.readyA && trade.readyB ? 3 : -1;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        refresh(trade);
    }

    private void adjustMoney(Trade trade, Player player, long delta) {
        boolean a = trade.isA(player.getUniqueId());
        long current = a ? trade.moneyA : trade.moneyB;
        long next = Math.max(0, Math.min(market.balance(player, trade.scope), current + delta));
        if (a) {
            trade.moneyA = next;
        } else {
            trade.moneyB = next;
        }
        trade.readyA = false;
        trade.readyB = false;
        trade.countdown = -1;
        refresh(trade);
    }

    /** Másodpercenként: visszaszámlálás, ha mindketten készen vannak. */
    void tick() {
        for (Trade trade : new ArrayList<>(new java.util.LinkedHashSet<>(active.values()))) {
            if (trade.finished || trade.countdown < 0) {
                continue;
            }
            if (trade.countdown == 0) {
                complete(trade);
            } else {
                trade.countdown--;
                refresh(trade);
            }
        }
    }

    private List<ItemStack> take(Trade trade, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = trade.inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
            trade.inventory.setItem(slot, null);
        }
        return items;
    }

    private void complete(Trade trade) {
        Player a = Bukkit.getPlayer(trade.a);
        Player b = Bukkit.getPlayer(trade.b);
        if (a == null || b == null) {
            cancel(trade, null);
            return;
        }
        if (market.balance(a, trade.scope) < trade.moneyA || market.balance(b, trade.scope) < trade.moneyB) {
            market.send(a, "trade-money-missing");
            market.send(b, "trade-money-missing");
            trade.readyA = false;
            trade.readyB = false;
            trade.countdown = -1;
            refresh(trade);
            return;
        }
        trade.finished = true;
        if (trade.moneyA > 0 && market.core().economy().transfer(a, b, trade.scope.currency(), trade.moneyA, "market:trade") != Ledger.Result.OK
                || trade.moneyB > 0 && market.core().economy().transfer(b, a, trade.scope.currency(), trade.moneyB, "market:trade") != Ledger.Result.OK) {
            market.core().getLogger().warning("Csere: nem sikerült a pénz átutalása (" + a.getName() + " - " + b.getName() + ")");
        }
        List<ItemStack> fromA = take(trade, LEFT);
        List<ItemStack> fromB = take(trade, RIGHT);
        active.remove(trade.a);
        active.remove(trade.b);
        a.closeInventory();
        b.closeInventory();
        for (ItemStack item : fromB) {
            Market.give(a, item);
        }
        for (ItemStack item : fromA) {
            Market.give(b, item);
        }
        market.send(a, "trade-done", "%player%", b.getName());
        market.send(b, "trade-done", "%player%", a.getName());
        a.playSound(a.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        b.playSound(b.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    /** Megszakítás: mindenki visszakapja, amit betett. */
    private void cancel(Trade trade, Player who) {
        if (trade.finished) {
            return;
        }
        trade.finished = true;
        active.remove(trade.a);
        active.remove(trade.b);
        List<ItemStack> fromA = take(trade, LEFT);
        List<ItemStack> fromB = take(trade, RIGHT);
        Player a = Bukkit.getPlayer(trade.a);
        Player b = Bukkit.getPlayer(trade.b);
        giveBack(a, fromA, trade.a);
        giveBack(b, fromB, trade.b);
        for (Player player : new Player[]{a, b}) {
            if (player == null) {
                continue;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() == trade) {
                player.closeInventory();
            }
            market.send(player, "trade-cancelled", "%player%", who == null ? "?" : who.getName());
        }
    }

    private void giveBack(Player player, List<ItemStack> items, UUID owner) {
        if (player != null) {
            for (ItemStack item : items) {
                Market.give(player, item);
            }
            return;
        }
        // ide csak akkor jutunk, ha a játékos már nincs fent (a kilépésnél még fent van, ott visszakapja)
        if (!items.isEmpty()) {
            market.core().getLogger().warning("Csere: " + owner + " nincs fent, " + items.size() + " tárgyat nem tudtam visszaadni.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Trade trade && !trade.finished && event.getPlayer() instanceof Player player) {
            cancel(trade, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
        Trade trade = active.get(event.getPlayer().getUniqueId());
        if (trade != null) {
            cancel(trade, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorld(PlayerChangedWorldEvent event) {
        Trade trade = active.get(event.getPlayer().getUniqueId());
        if (trade != null) {
            cancel(trade, event.getPlayer());
        }
    }

    void cancelAll() {
        for (Trade trade : new ArrayList<>(new java.util.LinkedHashSet<>(active.values()))) {
            cancel(trade, null);
        }
    }
}
