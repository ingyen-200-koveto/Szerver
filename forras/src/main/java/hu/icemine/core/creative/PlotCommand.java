package hu.icemine.core.creative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /plot (rövidebben /p, /telek): a Creative telkek parancsai. */
public final class PlotCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("auto", "claim", "home", "visit", "info", "trust", "untrust", "deny", "undeny", "kick",
            "name", "clear", "delete", "list", "hub", "help");

    private final CreativeMode mode;

    public PlotCommand(CreativeMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            mode.core().messages().send(sender, "general.only-players");
            return true;
        }
        if (mode.world() == null) {
            mode.core().messages().send(player, "mode.unavailable", "%mode%", "Creative");
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        String third = args.length > 2 ? args[2] : null;
        switch (sub) {
            case "menu", "menü" -> mode.menu(player);
            case "auto", "uj", "új" -> mode.auto(player);
            case "claim", "foglal" -> mode.claim(player);
            case "home", "h", "haza" -> mode.home(player, arg);
            case "hub", "spawn", "kozpont", "lobby" -> mode.hub(player);
            case "visit", "v", "latogat", "tp" -> need(player, arg, "/plot visit <játékos> [szám]", () -> mode.visit(player, arg, third));
            case "info", "i" -> mode.info(player);
            case "trust", "add", "hozzaad" -> need(player, arg, "/plot trust <játékos>", () -> mode.trust(player, arg, true));
            case "untrust", "remove", "elvesz" -> need(player, arg, "/plot untrust <játékos>", () -> mode.trust(player, arg, false));
            case "deny", "ban", "kitilt" -> need(player, arg, "/plot deny <játékos>", () -> mode.deny(player, arg, true));
            case "undeny", "unban", "felold" -> need(player, arg, "/plot undeny <játékos>", () -> mode.deny(player, arg, false));
            case "kick", "kirug" -> need(player, arg, "/plot kick <játékos>", () -> mode.kick(player, arg));
            case "name", "nev", "név" -> need(player, arg, "/plot name <név>", () -> mode.rename(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            case "clear", "torol", "reset" -> mode.clear(player);
            case "delete", "felad", "unclaim" -> mode.delete(player);
            case "list", "lista" -> list(player);
            case "admin" -> admin(player, args);
            default -> mode.help(player);
        }
        return true;
    }

    private void need(Player player, String arg, String usage, Runnable action) {
        if (arg == null) {
            mode.send(player, "usage", "%usage%", usage);
        } else {
            action.run();
        }
    }

    private void list(Player player) {
        List<Plot> mine = mode.plotsOf(player.getUniqueId());
        mode.send(player, "list-header", "%have%", String.valueOf(mine.size()), "%max%", mode.limitText(player));
        int index = 1;
        for (Plot plot : mine) {
            mode.click(player, "list-line", "%index%", String.valueOf(index), "%plot%", plot.display(), "%x%", String.valueOf(plot.px), "%z%", String.valueOf(plot.pz));
            index++;
        }
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("icemine.admin.creative")) {
            mode.core().messages().send(player, "general.no-permission");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("hub")) {
            mode.buildHub();
            mode.core().adminLog(player, "creative-hub", null, null);
            mode.send(player, "hub-rebuilt");
            return;
        }
        Plot plot = mode.plotAt(player.getLocation());
        if (plot == null || !(action.equals("clear") || action.equals("delete"))) {
            mode.send(player, "admin-usage");
            return;
        }
        if (action.equals("clear")) {
            mode.regenerate(plot, () -> mode.send(player, "cleared"));
        } else {
            mode.remove(plot);
        }
        mode.core().adminLog(player, "creative-" + action, plot.ownerName, plot.id());
        mode.send(player, "admin-done", "%plot%", plot.display());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBS);
            if (sender.hasPermission("icemine.admin.creative")) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Arrays.asList("visit", "trust", "deny", "kick", "v", "tp").contains(sub)) {
                return null;
            }
            if (sub.equals("untrust") || sub.equals("undeny")) {
                Plot plot = sender instanceof Player player ? mode.plotAt(player.getLocation()) : null;
                if (plot != null) {
                    return filter(new ArrayList<>((sub.equals("untrust") ? plot.trusted : plot.denied).values()), args[1]);
                }
            }
            if (sub.equals("admin") && sender.hasPermission("icemine.admin.creative")) {
                return filter(Arrays.asList("clear", "delete", "hub"), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
