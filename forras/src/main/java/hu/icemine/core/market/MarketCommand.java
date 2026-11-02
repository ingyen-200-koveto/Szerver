package hu.icemine.core.market;

import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** A piac parancsai: /ah, /trade, /shopgui, /shop, /sell, /baltop, /lottery, /kulcs és az admin /market. */
public final class MarketCommand implements CommandExecutor, TabCompleter {

    private final Market market;

    public MarketCommand(Market market) {
        this.market = market;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("market")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            market.core().messages().send(sender, "general.only-players");
            return true;
        }
        if (!market.ready()) {
            market.send(player, "not-ready");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (name) {
            case "ah" -> {
                if (sub.equals("sell") || sub.equals("elad")) {
                    if (args.length < 2) {
                        market.send(player, "ah-usage");
                    } else {
                        market.auctions().sell(player, args[1]);
                    }
                } else if (sub.equals("lejart") || sub.equals("expired")) {
                    market.auctions().openExpired(player);
                } else {
                    market.auctions().open(player, 0);
                }
            }
            case "trade" -> {
                if (sub.isEmpty()) {
                    market.send(player, "trade-usage");
                } else if (sub.equals("elfogad") || sub.equals("accept")) {
                    market.trades().accept(player);
                } else {
                    market.trades().request(player, args[0]);
                }
            }
            case "shopgui" -> market.shop().open(player);
            case "shop" -> market.core().ranks().openMenu(player);
            case "sell" -> {
                if (sub.equals("all") || sub.equals("mind")) {
                    market.shop().sellCommand(player, true);
                } else if (sub.equals("ha") || sub.equals("hand") || sub.equals("kez")) {
                    market.shop().sellCommand(player, false);
                } else {
                    market.send(player, "sell-usage");
                }
            }
            case "baltop" -> baltop(player);
            case "lottery" -> {
                if (sub.equals("buy") || sub.equals("vesz")) {
                    if (args.length < 2) {
                        market.send(player, "lottery-usage");
                    } else {
                        market.lottery().buy(player, args[1]);
                    }
                } else {
                    market.lottery().status(player);
                }
            }
            case "kulcs" -> market.keys().claimRank(player);
            default -> {
            }
        }
        return true;
    }

    private void baltop(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        market.store().topAsync(scope.currency(), 10).thenAccept(rows -> Bukkit.getScheduler().runTask(market.core(), () -> {
            if (!player.isOnline()) {
                return;
            }
            List<String[]> merged = new ArrayList<>();
            for (String[] row : rows) {
                Player online = row[0] == null ? null : Bukkit.getPlayer(UUID.fromString(row[0]));
                long amount = online != null ? market.balance(online, scope) : Long.parseLong(row[2]);
                merged.add(new String[]{row[1] == null ? "?" : row[1], String.valueOf(amount)});
            }
            merged.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
            player.sendMessage(Text.color(market.text("baltop-header", "%scope%", scope.name())));
            int place = 1;
            for (String[] row : merged) {
                player.sendMessage(Text.color(market.text("baltop-line", "%place%", String.valueOf(place++), "%player%", row[0],
                        "%money%", market.money(scope, Long.parseLong(row[1])))));
            }
            if (merged.isEmpty()) {
                market.send(player, "baltop-empty");
            }
        }));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("icemine.admin.market")) {
            market.core().messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            if (target == null) {
                market.core().messages().sendTagged(sender, market.text("admin-offline", "%player%", args[1]));
                return;
            }
            String what = args[2].toLowerCase(Locale.ROOT);
            boolean ok = what.startsWith("key:") ? market.keys().give(target, what.substring(4), amount) : market.specials().give(target, what, amount);
            market.core().messages().sendTagged(sender, market.text(ok ? "admin-given" : "admin-unknown", "%player%", target.getName(), "%item%", what));
            if (ok) {
                market.core().adminLog(sender, "market-give", target.getName(), what + " x" + amount);
            }
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("draw")) {
            Market.Scope scope = market.scope(args[1]);
            if (scope == null) {
                market.core().messages().sendTagged(sender, market.text("admin-unknown", "%item%", args[1], "%player%", ""));
                return;
            }
            market.lottery().draw(scope);
            market.core().adminLog(sender, "market-draw", scope.id(), null);
            return;
        }
        market.core().messages().sendTagged(sender, market.text("admin-usage"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> options = switch (name) {
            case "ah" -> args.length == 1 ? Arrays.asList("sell", "lejart") : List.of();
            case "trade" -> args.length == 1 ? null : List.of();
            case "sell" -> args.length == 1 ? Arrays.asList("ha", "all") : List.of();
            case "lottery" -> args.length == 1 ? Arrays.asList("buy", "status") : List.of();
            case "market" -> {
                if (args.length == 1) {
                    yield Arrays.asList("give", "draw");
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                    yield null;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("draw")) {
                    List<String> ids = new ArrayList<>();
                    for (Market.Scope scope : market.scopes()) {
                        ids.add(scope.id());
                    }
                    yield ids;
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                    List<String> ids = new ArrayList<>(Arrays.asList(Specials.SELL_CHEST, Specials.CRAFTER, Specials.FARM_BLOCK));
                    for (String key : market.keys().ids()) {
                        ids.add("key:" + key);
                    }
                    yield ids;
                }
                yield List.of();
            }
            default -> List.of();
        };
        if (options == null) {
            return null;
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
