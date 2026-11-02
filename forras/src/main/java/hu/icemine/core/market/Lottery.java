package hu.icemine.core.market;

import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Lottó (/lottery buy &lt;db&gt;, /lottery status): szelvényt veszel, és a sorsoláskor valaki megnyeri az összes
 * szelvény árát, plusz kulcsokat. Minél több szelvényed van, annál nagyobb az esélyed. Piaconként külön lottó van.
 * A szelvények és a sorsolás ideje az adatbázisban vannak, így újraindítás után is megmaradnak.
 */
public final class Lottery implements Listener {

    private final Market market;
    private final Random random = new Random();
    /** piac -> (játékos -> szelvények) */
    private final Map<String, Map<UUID, Integer>> tickets = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, Long> nextDraw = new HashMap<>();
    /** Függő kulcs jutalmak: "piac:uuid" -> "kulcs:darab" (ha a nyertes épp nem volt fent). */
    private final Map<String, String> pending = new HashMap<>();
    private long ticketPrice = 10_000;
    private int maxTickets = 3;
    private long interval = 24 * 3_600_000L;
    private String rewardKey = "galactic";
    private int rewardKeys = 2;

    Lottery(Market market) {
        this.market = market;
    }

    void loadState() throws Exception {
        tickets.clear();
        nextDraw.clear();
        pending.clear();
        for (Map.Entry<String, String> entry : market.store().loadMeta().entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":");
            try {
                if (key.startsWith("lottery-next:") && parts.length == 2) {
                    nextDraw.put(parts[1], Long.parseLong(entry.getValue()));
                } else if (key.startsWith("lottery-ticket:") && parts.length == 3) {
                    String[] value = entry.getValue().split(":", 2);
                    UUID player = UUID.fromString(parts[2]);
                    tickets.computeIfAbsent(parts[1], scope -> new LinkedHashMap<>()).put(player, Integer.parseInt(value[0]));
                    if (value.length > 1) {
                        names.put(player, value[1]);
                    }
                } else if (key.startsWith("lottery-pending:") && parts.length == 3) {
                    pending.put(parts[1] + ":" + parts[2], entry.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // sérült sor, kihagyjuk
            }
        }
    }

    void load(YamlConfiguration config, List<String> problems) {
        ticketPrice = Math.max(1, config.getLong("lottery.ticket-price", 10_000));
        maxTickets = Math.max(1, config.getInt("lottery.max-tickets", 3));
        interval = Math.max(1, config.getLong("lottery.draw-hours", 24)) * 3_600_000L;
        rewardKey = config.getString("lottery.reward-key", "galactic");
        rewardKeys = Math.max(0, config.getInt("lottery.reward-keys", 2));
    }

    private long next(Market.Scope scope) {
        Long next = nextDraw.get(scope.id());
        if (next == null) {
            next = System.currentTimeMillis() + interval;
            nextDraw.put(scope.id(), next);
            market.store().saveMeta("lottery-next:" + scope.id(), String.valueOf(next));
        }
        return next;
    }

    private long pot(String scope) {
        long total = 0;
        for (int count : tickets.getOrDefault(scope, Map.of()).values()) {
            total += count * ticketPrice;
        }
        return total;
    }

    public void buy(Player player, String amountText) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(amountText);
        } catch (NumberFormatException exception) {
            market.send(player, "lottery-usage");
            return;
        }
        Map<UUID, Integer> mine = tickets.computeIfAbsent(scope.id(), key -> new LinkedHashMap<>());
        int have = mine.getOrDefault(player.getUniqueId(), 0);
        if (amount < 1 || have + amount > maxTickets) {
            market.send(player, "lottery-max", "%max%", String.valueOf(maxTickets), "%have%", String.valueOf(have));
            return;
        }
        long price = ticketPrice * amount;
        if (!market.withdraw(player, scope, price, "market:lottery")) {
            market.send(player, "not-enough-money", "%price%", market.money(scope, price));
            return;
        }
        mine.put(player.getUniqueId(), have + amount);
        names.put(player.getUniqueId(), player.getName());
        market.store().saveMeta("lottery-ticket:" + scope.id() + ":" + player.getUniqueId(), (have + amount) + ":" + player.getName());
        market.send(player, "lottery-bought", "%amount%", String.valueOf(amount), "%price%", market.money(scope, price), "%pot%", market.money(scope, pot(scope.id())));
    }

    public void status(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        Map<UUID, Integer> all = tickets.getOrDefault(scope.id(), Map.of());
        int total = 0;
        for (int count : all.values()) {
            total += count;
        }
        long left = Math.max(0, next(scope) - System.currentTimeMillis());
        for (String line : market.texts("lottery-status", "%pot%", market.money(scope, pot(scope.id())), "%tickets%", String.valueOf(total),
                "%players%", String.valueOf(all.size()), "%mine%", String.valueOf(all.getOrDefault(player.getUniqueId(), 0)),
                "%max%", String.valueOf(maxTickets), "%time%", Text.duration(left), "%price%", market.money(scope, ticketPrice),
                "%keys%", String.valueOf(rewardKeys), "%key%", market.keys().name(rewardKey))) {
            player.sendMessage(hu.icemine.core.util.ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
        }
    }

    /** Percenként: ha eljött az ideje, sorsolás. */
    void check() {
        long now = System.currentTimeMillis();
        for (Market.Scope scope : market.scopes()) {
            if (next(scope) <= now) {
                draw(scope);
            }
        }
    }

    void draw(Market.Scope scope) {
        Map<UUID, Integer> all = tickets.getOrDefault(scope.id(), new LinkedHashMap<>());
        long pot = pot(scope.id());
        long next = System.currentTimeMillis() + interval;
        nextDraw.put(scope.id(), next);
        market.store().saveMeta("lottery-next:" + scope.id(), String.valueOf(next));
        if (all.isEmpty()) {
            return;
        }
        int total = 0;
        for (int count : all.values()) {
            total += count;
        }
        int roll = random.nextInt(total);
        UUID winner = null;
        for (Map.Entry<UUID, Integer> entry : all.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                winner = entry.getKey();
                break;
            }
        }
        for (UUID player : all.keySet()) {
            market.store().saveMeta("lottery-ticket:" + scope.id() + ":" + player, null);
        }
        tickets.remove(scope.id());
        if (winner == null) {
            return;
        }
        String name = names.getOrDefault(winner, "?");
        market.pay(winner, scope, pot, "market:lottery-win");
        Player online = Bukkit.getPlayer(winner);
        if (rewardKeys > 0) {
            if (online != null && market.scopeOf(online) == scope) {
                market.keys().give(online, rewardKey, rewardKeys);
            } else {
                pending.put(scope.id() + ":" + winner, rewardKey + ":" + rewardKeys);
                market.store().saveMeta("lottery-pending:" + scope.id() + ":" + winner, rewardKey + ":" + rewardKeys);
            }
        }
        market.broadcast(scope, "lottery-winner", "%player%", name, "%pot%", market.money(scope, pot), "%keys%", String.valueOf(rewardKeys),
                "%key%", market.keys().name(rewardKey));
    }

    private void deliver(Player player) {
        Market.Scope scope = market.scopeOf(player);
        if (scope == null) {
            return;
        }
        String reward = pending.remove(scope.id() + ":" + player.getUniqueId());
        if (reward == null) {
            return;
        }
        market.store().saveMeta("lottery-pending:" + scope.id() + ":" + player.getUniqueId(), null);
        String[] parts = reward.split(":");
        try {
            market.keys().give(player, parts[0], Integer.parseInt(parts[1]));
            market.send(player, "lottery-pending-given");
        } catch (RuntimeException ignored) {
            // hibás sor: a jutalom elveszne, de a pénzt már megkapta
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(market.core(), () -> {
            if (event.getPlayer().isOnline()) {
                deliver(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(market.core(), () -> {
            if (event.getPlayer().isOnline()) {
                deliver(event.getPlayer());
            }
        }, 60L);
    }
}
