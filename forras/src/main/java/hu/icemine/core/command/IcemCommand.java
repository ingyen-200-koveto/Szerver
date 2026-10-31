package hu.icemine.core.command;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.kitpvp.PvpMode;
import hu.icemine.core.rank.Ranks;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Az /icem parancs (régi neve /icemine, /im). A játékosoknak szánt alparancsok (help, stats, kit, arena, rank) és az
 * adminisztráció (setspawn, reload, admin ...) egy helyen, mindegyik a saját jogával (permissions.yml -> icem).
 */
public final class IcemCommand implements CommandExecutor, TabCompleter {

    /** A súgóban ebben a sorrendben jelennek meg. */
    static final List<String> SUBS = List.of("help", "stats", "kit", "arena", "rank", "setspawn", "reload", "admin");
    /** Régi formában (/icemine setlobby ...) is elfogadjuk az admin alparancsokat, hogy a megszokott parancsok működjenek. */
    static final List<String> LEGACY_ADMIN = List.of("setlobby", "setportal", "givecurrency", "takecurrency", "setcurrency", "parkour",
            "combofly", "mode", "maintenance", "hologram", "npc", "rebuild", "combattag", "info");

    private final IceMineCore core;
    private final AdminCommand admin;

    public IcemCommand(IceMineCore core, AdminCommand admin) {
        this.core = core;
        this.admin = admin;
    }

    private String permission(String sub) {
        return core.permissions().sub(sub);
    }

    private boolean allowed(CommandSender sender, String sub) {
        if (sender.hasPermission(permission(sub)) || (sub.equals("reload") && sender.hasPermission("icemine.config.reload"))) {
            return true;
        }
        core.messages().send(sender, "general.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!core.auth().isLoggedIn(player)) {
                core.messages().send(player, "auth.login-first");
                return null;
            }
            return player;
        }
        core.messages().send(sender, "general.only-players");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("?") || sub.equals("segitseg")) {
            sub = "help";
        }
        switch (sub) {
            case "help":
                if (allowed(sender, "help")) {
                    help(sender);
                }
                return true;
            case "stats":
                stats(sender, args);
                return true;
            case "kit":
                kit(sender, args);
                return true;
            case "arena":
                arena(sender, args);
                return true;
            case "rank":
                rank(sender, args);
                return true;
            case "setspawn":
                if (allowed(sender, "setspawn")) {
                    admin.setSpawn(sender, args);
                }
                return true;
            case "reload":
                if (allowed(sender, "reload")) {
                    admin.reload(sender, args);
                }
                return true;
            case "admin":
                if (allowed(sender, "admin")) {
                    admin.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                }
                return true;
            default:
                if (LEGACY_ADMIN.contains(sub)) {
                    if (allowed(sender, "admin")) {
                        admin.onCommand(sender, command, label, args);
                    }
                    return true;
                }
                core.messages().send(sender, "icem.unknown");
                return true;
        }
    }

    /** Csak azokat a sorokat mutatja, amikhez a küldőnek joga van. */
    private void help(CommandSender sender) {
        core.messages().send(sender, "icem.help-header");
        for (String sub : SUBS) {
            if (!sender.hasPermission(permission(sub))) {
                continue;
            }
            List<String> line = core.configs().get("messages_hu").getStringList("icem.help." + sub);
            if (line.size() < 2) {
                continue;
            }
            sender.sendMessage(core.messages().get("icem.help-line", "%usage%", line.get(0), "%description%", line.get(1)));
        }
        sender.sendMessage(Text.color(core.messages().raw("icem.help-footer")));
    }

    private void stats(CommandSender sender, String[] args) {
        if (!allowed(sender, "stats")) {
            return;
        }
        if (args.length > 1) {
            if (!sender.hasPermission(permission("stats-others"))) {
                core.messages().send(sender, "general.no-permission");
                return;
            }
            if (sender instanceof Player player) {
                core.menus().openProfileOf(player, args[1]);
            } else {
                admin.printStatsOf(sender, args[1]);
            }
            return;
        }
        Player player = player(sender);
        if (player != null) {
            core.menus().open(player, "profile");
        }
    }

    private void kit(CommandSender sender, String[] args) {
        if (!allowed(sender, "kit")) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        PvpMode mode = core.pvpModeOf(player);
        if (mode == null) {
            core.messages().send(player, "kit.only-pvp");
            return;
        }
        if (args.length > 1) {
            Kit kit = mode.kits().get(args[1].toLowerCase(Locale.ROOT));
            if (kit == null) {
                core.messages().send(player, "kit.unknown", "%kits%", String.join(", ", mode.kits().keySet()));
                return;
            }
            mode.selectKit(player, kit.id);
            return;
        }
        mode.openKits(player);
    }

    private void arena(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
            if (sender.hasPermission("icemine.admin.arena")) {
                admin.arena(sender, args);
            } else {
                core.messages().send(sender, "general.no-permission");
            }
            return;
        }
        if (!allowed(sender, "arena")) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (core.kitpvp().inWorld(player)) {
            core.kitpvp().openArenas(player);
        } else {
            core.messages().send(player, "arena.only-kitpvp");
        }
    }

    private void rank(CommandSender sender, String[] args) {
        if (!allowed(sender, "rank")) {
            return;
        }
        if (args.length > 1) {
            if (!sender.hasPermission(permission("rank-others"))) {
                core.messages().send(sender, "general.no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                core.messages().send(sender, "general.no-player");
                return;
            }
            Ranks.Rank rank = core.ranks().of(target);
            core.messages().send(sender, "rank.info", "%player%", target.getName(), "%rank%", rank.name);
            return;
        }
        if (sender instanceof Player player) {
            if (!core.prefixes().hooked() && core.ranks().all().size() > 1) {
                core.messages().send(player, "rank.no-luckperms");
            }
            Ranks.Rank rank = core.ranks().of(player);
            core.messages().send(player, "rank.your-rank", "%rank%", rank.name, "%multiplier%", Text.decimal(rank.multiplier));
            core.ranks().openMenu(player);
            return;
        }
        for (Ranks.Rank rank : core.ranks().all()) {
            sender.sendMessage(core.messages().tagged(rank.name + " &8(" + rank.group + ") &7x" + Text.decimal(rank.multiplier)
                    + " &8| &7kitek: &f" + String.join(", ", rank.allKits())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> visible = new ArrayList<>();
            for (String sub : SUBS) {
                if (sender.hasPermission(permission(sub))) {
                    visible.add(sub);
                }
            }
            return AdminCommand.filter(visible, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub) || !sender.hasPermission(permission(sub))) {
            return Collections.emptyList();
        }
        switch (sub) {
            case "admin":
                return admin.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
            case "setspawn":
            case "reload":
                return admin.onTabComplete(sender, command, alias, args);
            case "kit":
                if (args.length == 2 && sender instanceof Player player) {
                    PvpMode mode = core.pvpModeOf(player);
                    return mode == null ? Collections.emptyList() : AdminCommand.filter(new ArrayList<>(mode.kits().keySet()), args[1]);
                }
                return Collections.emptyList();
            case "stats":
            case "rank":
                return args.length == 2 ? null : Collections.emptyList();
            case "arena":
                return args.length == 2 && sender.hasPermission("icemine.admin.arena")
                        ? AdminCommand.filter(List.of("reset"), args[1]) : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
