package hu.icemine.core.fyland;

import hu.icemine.core.util.ClickText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /fyland (rövidebben /fy, /sziget): a sziget parancsai. */
public final class FyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("menu", "haza", "sethome", "meghiv", "elfogad", "kilep", "kirug", "atad", "latogat", "top",
            "fazisok", "fejlesztes", "piac", "elad", "tagok", "beallitasok", "latogatok", "reset", "create", "go", "info", "szint", "promote", "demote",
            "coop", "uncoop", "ban", "unban", "lock", "unlock", "setwarp", "delwarp", "warp", "csatater", "enchanter", "help");

    private final FylandMode mode;

    public FyCommand(FylandMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("fyland")) {
            other(sender, name, args);
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            mode.core().messages().send(sender, "general.only-players");
            return true;
        }
        if (mode.world() == null) {
            mode.core().messages().send(player, "mode.unavailable", "%mode%", "OneBlock");
            return true;
        }
        String arg = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "menu", "menü" -> {
                if (mode.inWorld(player)) {
                    mode.menus().main(player);
                } else {
                    mode.core().modes().sendAndReport(player, FylandMode.ID);
                }
            }
            case "haza", "home", "h", "go" -> mode.goHome(player);
            case "create", "nev", "name" -> mode.social().create(player, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null);
            case "info" -> mode.social().info(player, arg);
            case "szint", "level", "ertek", "value" -> mode.worth().recalc(player);
            case "promote", "eloleptet" -> needArg(player, arg, "/fy promote <tag>", () -> mode.social().promote(player, arg, true));
            case "demote", "lefokoz" -> needArg(player, arg, "/fy demote <tag>", () -> mode.social().promote(player, arg, false));
            case "coop" -> needArg(player, arg, "/fy coop <játékos>", () -> mode.social().coop(player, arg, true));
            case "uncoop" -> needArg(player, arg, "/fy uncoop <játékos>", () -> mode.social().coop(player, arg, false));
            case "ban", "kitilt" -> needArg(player, arg, "/fy ban <játékos>", () -> mode.social().ban(player, arg, true));
            case "unban", "felold" -> needArg(player, arg, "/fy unban <játékos>", () -> mode.social().ban(player, arg, false));
            case "lock", "zar" -> mode.social().lock(player, true);
            case "unlock", "nyit" -> mode.social().lock(player, false);
            case "setwarp" -> mode.social().setWarp(player, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null);
            case "delwarp" -> mode.social().delWarp(player);
            case "warp", "warps" -> mode.battle().warpMenu(player);
            case "csatater", "pvp", "arena" -> mode.battle().teleport(player);
            case "enchanter", "varazslo" -> inWorld(player, () -> mode.enchants().open(player));
            case "sethome", "otthon" -> mode.setHome(player);
            case "meghiv", "invite" -> {
                if (arg == null) {
                    mode.send(player, "usage", "%usage%", "/fy meghiv <játékos>");
                } else {
                    mode.invite(player, arg);
                }
            }
            case "elfogad", "accept" -> mode.accept(player);
            case "kilep", "leave" -> mode.leaveIsland(player);
            case "kirug", "kick" -> {
                if (arg == null) {
                    mode.send(player, "usage", "%usage%", "/fy kirug <játékos>");
                } else {
                    mode.kick(player, arg);
                }
            }
            case "atad", "transfer" -> {
                if (arg == null) {
                    mode.send(player, "usage", "%usage%", "/fy atad <játékos>");
                } else {
                    mode.transfer(player, arg);
                }
            }
            case "latogat", "visit" -> {
                if (arg == null) {
                    if (mode.inWorld(player)) {
                        mode.menus().visitList(player);
                    } else {
                        mode.send(player, "usage", "%usage%", "/fy latogat <játékos>");
                    }
                } else {
                    mode.visit(player, arg);
                }
            }
            case "top" -> inWorld(player, () -> mode.menus().top(player));
            case "fazisok", "fazis", "phases" -> inWorld(player, () -> mode.menus().phases(player));
            case "fejlesztes", "fejlesztesek", "upgrades" -> inWorld(player, () -> mode.menus().upgrades(player));
            case "piac", "bolt", "market" -> inWorld(player, () -> mode.menus().market(player));
            case "elad", "sell" -> mode.sell(player, null);
            case "tagok", "members" -> inWorld(player, () -> mode.menus().members(player));
            case "beallitasok", "settings" -> inWorld(player, () -> mode.menus().settings(player));
            case "latogatok", "visitors" -> mode.toggleVisitors(player);
            case "reset" -> mode.reset(player);
            default -> help(player);
        }
        return true;
    }

    private void needArg(Player player, String arg, String usage, Runnable action) {
        if (arg == null) {
            mode.send(player, "usage", "%usage%", usage);
        } else {
            action.run();
        }
    }

    /** A külön parancsok: /pw (játékos warpok), /enchanter, /fywarp. */
    private void other(CommandSender sender, String name, String[] args) {
        if (!(sender instanceof Player player)) {
            mode.core().messages().send(sender, "general.only-players");
            return;
        }
        if (mode.world() == null) {
            mode.core().messages().send(player, "mode.unavailable", "%mode%", "OneBlock");
            return;
        }
        switch (name) {
            case "pw" -> {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "" -> inWorld(player, () -> mode.social().warps(player, 0));
                    case "set" -> mode.social().setWarp(player, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null);
                    case "del", "delete", "torol" -> mode.social().delWarp(player);
                    case "rate", "ertekel" -> {
                        if (args.length < 3) {
                            mode.send(player, "usage", "%usage%", "/pw rate <warp> <1-5>");
                        } else {
                            mode.social().rate(player, args[1], args[2]);
                        }
                    }
                    default -> mode.social().goWarp(player, String.join(" ", args));
                }
            }
            case "enchanter" -> inWorld(player, () -> mode.enchants().open(player));
            case "fywarp" -> mode.battle().warpMenu(player);
            default -> {
            }
        }
    }

    private void inWorld(Player player, Runnable action) {
        if (mode.inWorld(player)) {
            action.run();
        } else {
            mode.send(player, "only-here");
        }
    }

    private void help(Player player) {
        for (String line : mode.texts("help")) {
            player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("icemine.admin.fyland")) {
            mode.core().messages().send(sender, "general.no-permission");
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("fazis")) {
            mode.adminSetPhase(sender, args[2], args[3]);
        } else if (args.length >= 3 && args[1].equalsIgnoreCase("torol")) {
            mode.adminDelete(sender, args[2]);
        } else if (args.length >= 3 && args[1].equalsIgnoreCase("tp") && sender instanceof Player player) {
            mode.visit(player, mode.findByName(args[2]), args[2]);
        } else {
            mode.core().messages().sendTagged(sender, mode.text("admin-usage"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pw")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(Arrays.asList("set", "del", "rate"));
                for (FyIsland island : mode.islands()) {
                    if (!island.warpName.isBlank()) {
                        options.add(island.warpName);
                    }
                }
                return filter(options, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("rate")) {
                List<String> options = new ArrayList<>();
                for (FyIsland island : mode.islands()) {
                    if (!island.warpName.isBlank()) {
                        options.add(island.warpName);
                    }
                }
                return filter(options, args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("rate")) {
                return filter(Arrays.asList("1", "2", "3", "4", "5"), args[2]);
            }
            return List.of();
        }
        if (!name.equals("fyland")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBS);
            if (sender.hasPermission("icemine.admin.fyland")) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("admin") && sender.hasPermission("icemine.admin.fyland")) {
                return filter(Arrays.asList("fazis", "torol", "tp"), args[1]);
            }
            if (Arrays.asList("meghiv", "invite", "latogat", "visit", "info", "coop", "uncoop", "ban").contains(sub)) {
                return null;
            }
            if (sub.equals("unban") && sender instanceof Player player) {
                FyIsland island = mode.islandOf(player);
                return island == null ? List.of() : filter(new ArrayList<>(island.banned.values()), args[1]);
            }
            if (Arrays.asList("kirug", "kick", "atad", "transfer", "promote", "demote").contains(sub) && sender instanceof Player player) {
                FyIsland island = mode.islandOf(player);
                return island == null ? List.of() : filter(new ArrayList<>(island.members.values()), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return null;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("fazis")) {
            List<String> ids = new ArrayList<>();
            for (FyPhase phase : mode.phases()) {
                ids.add(phase.id);
            }
            return filter(ids, args[3]);
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
