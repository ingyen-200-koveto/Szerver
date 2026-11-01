package hu.icemine.core.fullpvp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /fullpvp (rövidebben /fp): menü, bolt, kitek, széf, fejpénz. */
public final class FpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("menu", "bolt", "kit", "szef", "fejpenz", "fejpenzek", "help");

    private final FullPvpMode mode;

    public FpCommand(FullPvpMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!mode.inWorld(player)) {
            if (args.length == 0) {
                mode.core().modes().sendAndReport(player, FullPvpMode.ID);
            } else {
                mode.send(player, "only-here");
            }
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "menü" -> mode.openMain(player);
            case "bolt", "shop" -> mode.openShop(player);
            case "kit", "kitek", "kits" -> {
                if (args.length > 1) {
                    mode.claim(player, args[1]);
                } else {
                    mode.openKits(player);
                }
            }
            case "szef", "széf", "vault" -> mode.openVault(player);
            case "fejpenz", "fejpénz", "bounty" -> {
                if (args.length < 3) {
                    mode.send(player, "bounty-usage");
                } else {
                    mode.placeBounty(player, args[1], args[2]);
                }
            }
            case "fejpenzek", "bounties" -> mode.openBounties(player);
            default -> mode.help(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            List<String> result = new ArrayList<>();
            for (String id : mode.fullKits().keySet()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(id);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fejpenz")) {
            return null;
        }
        return List.of();
    }
}
