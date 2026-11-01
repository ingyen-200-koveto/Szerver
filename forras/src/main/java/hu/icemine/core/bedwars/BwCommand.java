package hu.icemine.core.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /bw (bedwars): beszállás, kilépés, néző mód, statisztika és admin parancsok. */
public final class BwCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER = List.of("play", "join", "leave", "list", "stats", "nez", "top", "help");
    private static final List<String> ADMIN = List.of("start", "stop", "rebuild", "reload");

    private final Bedwars plugin;

    public BwCommand(Bedwars plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender sender, String key, String... pairs) {
        plugin.core().messages().send(sender, "bedwars." + key, pairs);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (plugin.world() == null) {
            send(sender, "disabled");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.menus().open(player);
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "play":
            case "jatek":
            case "jatszok":
                play(sender, args);
                return true;
            case "join":
            case "belep":
                join(sender, args);
                return true;
            case "leave":
            case "kilep":
                leave(sender);
                return true;
            case "list":
            case "lista":
                list(sender);
                return true;
            case "stats":
            case "stat":
                stats(sender, args);
                return true;
            case "nez":
            case "spectate":
                spectate(sender, args);
                return true;
            case "top":
                for (String board : new String[]{"bedwarswins", "bedwarsfinals"}) {
                    for (String line : plugin.core().leaderboards().lines(board)) {
                        sender.sendMessage(hu.icemine.core.util.Text.color(line));
                    }
                }
                return true;
            case "start":
            case "stop":
            case "rebuild":
            case "reload":
                admin(sender, sub, args);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    private void help(CommandSender sender) {
        plugin.core().messages().sendList(sender, "bedwars.help");
        if (sender.hasPermission(Bedwars.ADMIN)) {
            for (String line : plugin.core().messages().list("bedwars.help-admin")) {
                sender.sendMessage(line);
            }
        }
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.core().messages().send(sender, "general.only-players");
        return null;
    }

    private void play(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().open(player);
            return;
        }
        BwMode mode = plugin.mode(args[1]);
        if (mode == null) {
            send(player, "join.unknown-mode");
            return;
        }
        plugin.play(player, mode);
    }

    private void join(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            send(player, "usage", "%usage%", "/bw join <aréna>");
            return;
        }
        BwArena arena = plugin.arena(args[1]);
        if (arena == null) {
            BwMode mode = plugin.mode(args[1]);
            if (mode != null) {
                plugin.play(player, mode);
                return;
            }
            send(player, "join.unknown");
            return;
        }
        plugin.join(player, arena);
    }

    private void leave(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            send(player, "join.not-in-game");
            return;
        }
        arena.leave(player, true);
        send(player, "join.you-left");
    }

    private void list(CommandSender sender) {
        send(sender, "list.header");
        for (BwArena arena : plugin.allArenas()) {
            String state;
            if (!arena.ready()) {
                state = plugin.core().messages().raw("bedwars.list.building");
            } else {
                state = plugin.core().messages().raw("bedwars.list." + arena.state().name().toLowerCase(Locale.ROOT));
            }
            sender.sendMessage(plugin.core().messages().get("bedwars.list.line", "%id%", arena.id, "%mode%", arena.mode.name, "%map%", arena.mapName(),
                    "%players%", String.valueOf(arena.size()), "%max%", String.valueOf(arena.capacity()), "%state%", state));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().stats(player);
            return;
        }
        OfflinePlayer target = BwMenus.find(args[1]);
        if (target == null) {
            plugin.core().messages().send(player, "general.no-player");
            return;
        }
        plugin.menus().stats(player, target);
    }

    private void spectate(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.menus().spectate(player);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        BwArena arena = target == null ? null : plugin.arenaOf(target);
        if (arena == null || arena.data(target) == null) {
            send(player, "spectate.not-playing");
            return;
        }
        plugin.spectate(player, arena);
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(Bedwars.ADMIN)) {
            plugin.core().messages().send(sender, "general.no-permission");
            return;
        }
        if (sub.equals("reload")) {
            List<String> problems = plugin.core().reloadConfigs(Bedwars.FILE);
            send(sender, "admin.reloaded", "%problems%", String.valueOf(problems.size()));
            for (String problem : problems) {
                sender.sendMessage(hu.icemine.core.util.Text.color("&e- &7" + problem));
            }
            return;
        }
        if (args.length < 2) {
            send(sender, "usage", "%usage%", "/bw " + sub + " <aréna>");
            return;
        }
        BwArena arena = plugin.arena(args[1]);
        if (arena == null) {
            send(sender, "join.unknown");
            return;
        }
        switch (sub) {
            case "start":
                arena.forceStart(sender);
                break;
            case "stop":
                arena.forceStop();
                send(sender, "admin.stopped", "%arena%", arena.id);
                break;
            default:
                send(sender, plugin.rebuild(arena) ? "admin.rebuilding" : "admin.busy", "%arena%", arena.id);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PLAYER);
            if (sender.hasPermission(Bedwars.ADMIN)) {
                options.addAll(ADMIN);
            }
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
            return result;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("play")) {
                for (BwMode mode : plugin.modes().values()) {
                    if (mode.id.startsWith(typed)) {
                        result.add(mode.id);
                    }
                }
            } else if (sub.equals("join") || ADMIN.contains(sub)) {
                for (BwArena arena : plugin.allArenas()) {
                    if (arena.id.startsWith(typed)) {
                        result.add(arena.id);
                    }
                }
            } else if (sub.equals("nez") || sub.equals("stats")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        result.add(online.getName());
                    }
                }
            }
        }
        return result;
    }
}
