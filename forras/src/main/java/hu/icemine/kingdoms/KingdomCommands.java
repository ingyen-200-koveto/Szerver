package hu.icemine.kingdoms;

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

/** /k (játékosoknak) és /kadmin (/ka) parancsok. A /k magában a királyság menüt nyitja meg. */
final class KingdomCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("create", "disband", "invite", "join", "kick", "leave", "info", "home", "sethome", "spawn",
            "claim", "unclaim", "unclaimall", "autoclaim", "map", "nexus", "bank", "deposit", "withdraw", "np", "donate", "upgrade", "ally", "truce",
            "neutral", "enemy", "relations", "nation", "invade", "surrender", "shield", "pacifist", "top", "list", "members", "ranks", "rank",
            "promote", "demote", "king", "chat", "announce", "lore", "structures", "turrets", "sell", "extractor", "fly", "champion", "war", "settings", "open", "help");
    private static final List<String> NATION_SUBS = Arrays.asList("create", "invite", "join", "leave", "kick", "disband", "info", "donate");
    private static final List<String> ADMIN_SUBS = Arrays.asList("reload", "rebuild", "disband", "unclaim", "give", "rp", "bank", "xp", "level",
            "masswar", "stopinvasion", "join", "setking", "info");

    private final IceMineKingdoms plugin;

    KingdomCommands(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private KingdomManager km() {
        return plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kadmin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "only-players");
            return true;
        }
        if (player.getWorld() != plugin.world() && !(args.length > 0 && Arrays.asList("info", "top", "list", "help").contains(args[0].toLowerCase(Locale.ROOT)))) {
            plugin.send(player, "only-in-kingdoms");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().main(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        switch (sub) {
            case "create", "alapit" -> {
                if (arg == null) {
                    plugin.send(player, "usage", "%usage%", "/k create <név>");
                } else {
                    km().create(player, arg);
                }
            }
            case "disband" -> km().disband(player);
            case "invite" -> needArg(player, arg, "/k invite <játékos>", () -> km().invite(player, arg));
            case "join" -> needArg(player, arg, "/k join <királyság>", () -> km().join(player, arg));
            case "kick" -> needArg(player, arg, "/k kick <játékos>", () -> km().kick(player, arg));
            case "leave" -> km().leave(player);
            case "info" -> {
                Kingdom target = arg == null ? km().kingdomOf(player.getUniqueId()) : km().kingdom(arg);
                if (target == null && arg != null) {
                    java.util.UUID id = km().idOf(arg);
                    target = id == null ? null : km().kingdomOf(id);
                }
                if (target == null) {
                    plugin.send(player, arg == null ? "no-kingdom" : "no-such-kingdom");
                } else {
                    plugin.menus().info(player, target);
                }
            }
            case "home" -> km().home(player, arg);
            case "sethome" -> km().setHome(player);
            case "spawn" -> {
                if (plugin.combatTagged(player)) {
                    plugin.send(player, "in-combat");
                } else {
                    plugin.teleport(player, plugin.spawn());
                }
            }
            case "claim" -> km().claim(player, false);
            case "unclaim" -> km().unclaim(player);
            case "unclaimall" -> km().unclaimAll(player);
            case "autoclaim" -> {
                KPlayer data = km().player(player);
                data.autoClaim = !data.autoClaim;
                plugin.send(player, data.autoClaim ? "autoclaim-on" : "autoclaim-off");
                if (data.autoClaim) {
                    km().claim(player, true);
                }
            }
            case "map" -> plugin.showMap(player);
            case "nexus" -> {
                Kingdom kingdom = km().mine(player);
                if (kingdom != null && arg != null && (arg.equalsIgnoreCase("open") || arg.equalsIgnoreCase("menu"))) {
                    if (kingdom.can(player.getUniqueId(), KPerm.NEXUS)) {
                        plugin.menus().main(player);
                    } else {
                        plugin.send(player, "no-kingdom-permission", "%permission%", KPerm.NEXUS.title);
                    }
                } else if (kingdom != null && !kingdom.hasNexus()) {
                    plugin.structures().placeNexus(player);
                } else if (kingdom != null) {
                    plugin.menus().main(player);
                }
            }
            case "bank" -> plugin.menus().bank(player);
            case "deposit" -> needArg(player, arg, "/k deposit <összeg>", () -> km().deposit(player, plugin.amount(player, arg)));
            case "withdraw" -> needArg(player, arg, "/k withdraw <összeg>", () -> km().withdraw(player, plugin.amount(player, arg)));
            case "rp", "np", "donate" -> {
                Kingdom kingdom = km().mine(player);
                if (kingdom != null) {
                    plugin.donations().open(player, kingdom);
                }
            }
            case "upgrade", "upgrades" -> plugin.menus().upgrades(player);
            case "ally" -> needArg(player, arg, "/k ally <királyság>", () -> km().requestRelation(player, arg, Relation.ALLY));
            case "truce" -> needArg(player, arg, "/k truce <királyság>", () -> km().requestRelation(player, arg, Relation.TRUCE));
            case "neutral" -> needArg(player, arg, "/k neutral <királyság>", () -> km().requestRelation(player, arg, Relation.NEUTRAL));
            case "enemy" -> needArg(player, arg, "/k enemy <királyság>", () -> km().requestRelation(player, arg, Relation.ENEMY));
            case "relations" -> plugin.menus().relations(player, 0);
            case "nation" -> nation(player, args);
            case "invade" -> plugin.invasions().start(player);
            case "surrender" -> plugin.invasions().surrender(player);
            case "shield" -> {
                if (arg == null) {
                    plugin.menus().war(player);
                } else {
                    try {
                        km().buyShield(player, Integer.parseInt(arg));
                    } catch (NumberFormatException exception) {
                        plugin.send(player, "usage", "%usage%", "/k shield <óra>");
                    }
                }
            }
            case "pacifist" -> km().togglePacifist(player);
            case "top", "list" -> plugin.menus().top(player, "might");
            case "members" -> plugin.menus().members(player, 0);
            case "ranks" -> plugin.menus().ranks(player);
            case "rank" -> {
                if (args.length < 3) {
                    plugin.send(player, "usage", "%usage%", "/k rank <játékos> <rang>");
                } else {
                    java.util.UUID target = km().idOf(args[1]);
                    if (target == null) {
                        plugin.send(player, "no-player");
                    } else {
                        km().setRank(player, target, args[2].toLowerCase(Locale.ROOT));
                    }
                }
            }
            case "promote" -> needArg(player, arg, "/k promote <játékos>", () -> km().shift(player, arg, true));
            case "demote" -> needArg(player, arg, "/k demote <játékos>", () -> km().shift(player, arg, false));
            case "king" -> needArg(player, arg, "/k king <játékos>", () -> km().transfer(player, arg));
            case "chat", "c" -> {
                Kingdom kingdom = km().mine(player);
                if (kingdom != null) {
                    KPlayer data = km().player(player);
                    String mode = arg == null ? "" : arg.toLowerCase(Locale.ROOT);
                    switch (mode) {
                        case "k", "kingdom", "kiralysag" -> {
                            data.kingdomChat = true;
                            data.allyChat = false;
                        }
                        case "a", "ally", "szovetseg" -> {
                            data.kingdomChat = false;
                            data.allyChat = true;
                        }
                        case "g", "global", "p", "public", "kozos" -> {
                            data.kingdomChat = false;
                            data.allyChat = false;
                        }
                        default -> {
                            data.kingdomChat = !data.kingdomChat && !data.allyChat;
                            data.allyChat = false;
                        }
                    }
                    plugin.send(player, data.allyChat ? "chat-ally" : data.kingdomChat ? "chat-on" : "chat-off");
                }
            }
            case "lore", "describe", "leiras" -> {
                if (rest == null || rest.isBlank()) {
                    plugin.send(player, "usage", "%usage%", "/k lore <leírás>");
                } else {
                    km().describe(player, rest);
                }
            }
            case "sell", "elad" -> plugin.structures().sell(player);
            case "extractor", "extractors", "kitermelo" -> {
                if (!player.hasPermission("icemine.kingdoms.extractor")) {
                    plugin.send(player, "extractor-no-permission");
                } else {
                    plugin.structures().collectAll(player);
                }
            }
            case "fly", "repules" -> plugin.fly().toggle(player);
            case "announce" -> km().announce(player, rest);
            case "structures" -> plugin.menus().structures(player);
            case "turrets" -> plugin.menus().turrets(player);
            case "champion" -> plugin.menus().champion(player);
            case "war" -> plugin.menus().war(player);
            case "settings" -> plugin.menus().settings(player);
            case "open" -> km().toggleOpen(player);
            default -> help(player);
        }
        return true;
    }

    private void needArg(Player player, String arg, String usage, Runnable action) {
        if (arg == null) {
            plugin.send(player, "usage", "%usage%", usage);
        } else {
            action.run();
        }
    }

    private void nation(Player player, String[] args) {
        if (args.length < 2) {
            plugin.menus().nation(player);
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        String arg = args.length > 2 ? args[2] : null;
        switch (sub) {
            case "create" -> needArg(player, arg, "/k nation create <név>", () -> km().createNation(player, arg));
            case "invite" -> needArg(player, arg, "/k nation invite <királyság>", () -> km().inviteToNation(player, arg));
            case "join", "accept" -> needArg(player, arg, "/k nation join <nemzet>", () -> km().joinNation(player, arg));
            case "leave" -> km().leaveNation(player);
            case "kick" -> needArg(player, arg, "/k nation kick <királyság>", () -> km().kickFromNation(player, arg));
            case "disband" -> km().disbandNation(player);
            case "donate" -> needArg(player, arg, "/k nation donate <NP>", () -> km().donateToNation(player, plugin.amount(player, arg)));
            default -> plugin.menus().nation(player);
        }
    }

    private void help(CommandSender sender) {
        for (String line : plugin.getConfig().getStringList("help")) {
            sender.sendMessage(Text.color(line));
        }
    }

    // ---- admin ----

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("icemine.kingdoms.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        if (args.length == 0) {
            for (String line : plugin.getConfig().getStringList("admin-help")) {
                sender.sendMessage(Text.color(line));
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Kingdom target = args.length > 1 ? km().kingdom(args[1]) : null;
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                plugin.send(sender, "reloaded");
            }
            case "rebuild" -> {
                plugin.rebuildSpawn();
                plugin.send(sender, "rebuilt");
            }
            case "disband" -> {
                if (target == null) {
                    plugin.send(sender, "no-such-kingdom");
                    return;
                }
                String name = target.display();
                km().disband(target);
                plugin.broadcast("disbanded", "%kingdom%", name);
            }
            case "unclaim" -> {
                if (!(sender instanceof Player player)) {
                    plugin.send(sender, "only-players");
                    return;
                }
                long chunk = Lands.key(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
                Kingdom owner = km().owner(chunk);
                if (owner == null) {
                    plugin.send(sender, "not-claimed");
                    return;
                }
                plugin.structures().destroyChunk(owner, chunk);
                km().release(owner, chunk, false);
                plugin.send(sender, "admin-unclaimed", "%kingdom%", owner.display());
            }
            case "give" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    plugin.send(sender, "usage", "%usage%", "/ka give <structure|turret> <fajta> [szint]");
                    return;
                }
                String kind = args[1].toLowerCase(Locale.ROOT);
                String type = args[2].toLowerCase(Locale.ROOT);
                if (plugin.structures().def(kind, type) == null) {
                    plugin.send(sender, "unknown-type");
                    return;
                }
                int level = args.length > 3 ? parseInt(args[3], 1) : 1;
                player.getInventory().addItem(plugin.structures().item(kind, type, level));
            }
            case "rp", "bank", "xp" -> {
                if (target == null || args.length < 3) {
                    plugin.send(sender, "usage", "%usage%", "/ka " + sub + " <királyság> <+/-mennyiség>");
                    return;
                }
                long amount = parseLong(args[2]);
                switch (sub) {
                    case "rp" -> target.resourcePoints = Math.max(0, target.resourcePoints + amount);
                    case "bank" -> target.bank = Math.max(0, target.bank + amount);
                    default -> {
                        if (amount > 0) {
                            km().addXp(target, amount);
                        } else {
                            target.xp = Math.max(0, target.xp + amount);
                        }
                    }
                }
                target.dirty = true;
                plugin.send(sender, "admin-changed", "%kingdom%", target.display());
            }
            case "level" -> {
                if (target == null || args.length < 3) {
                    plugin.send(sender, "usage", "%usage%", "/ka level <királyság> <szint>");
                    return;
                }
                int level = Math.max(1, Math.min(plugin.settings().maxLevel(), parseInt(args[2], 1)));
                target.xp = plugin.settings().levels.get(level - 1);
                target.dirty = true;
                plugin.send(sender, "admin-changed", "%kingdom%", target.display());
            }
            case "masswar" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    plugin.invasions().stopMasswar();
                } else {
                    plugin.invasions().startMasswar();
                }
            }
            case "stopinvasion" -> {
                if (!(sender instanceof Player player)) {
                    plugin.send(sender, "only-players");
                    return;
                }
                plugin.invasions().adminStop(Lands.key(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4));
                plugin.send(sender, "admin-changed", "%kingdom%", "-");
            }
            case "join" -> {
                if (!(sender instanceof Player player) || target == null) {
                    plugin.send(sender, "usage", "%usage%", "/ka join <királyság>");
                    return;
                }
                if (km().kingdomOf(player.getUniqueId()) != null) {
                    plugin.send(sender, "already-in-kingdom");
                    return;
                }
                target.open = true;
                km().join(player, target.name);
                target.open = false;
                target.dirty = true;
            }
            case "setking" -> {
                if (target == null || args.length < 3) {
                    plugin.send(sender, "usage", "%usage%", "/ka setking <királyság> <játékos>");
                    return;
                }
                java.util.UUID id = km().idOf(args[2]);
                if (id == null || !target.members.containsKey(id)) {
                    plugin.send(sender, "not-member", "%player%", args[2]);
                    return;
                }
                List<Rank> sorted = target.sortedRanks();
                target.members.put(target.leader, sorted.size() > 1 ? sorted.get(1).id : target.lowest().id);
                target.members.put(id, Rank.KING);
                target.leader = id;
                target.dirty = true;
                plugin.send(sender, "admin-changed", "%kingdom%", target.display());
            }
            case "info" -> {
                sender.sendMessage(Text.color("&6Királyságok: &f" + km().all().size() + " &8| &6Nemzetek: &f" + km().nations().size()
                        + " &8| &6Masswar: &f" + (plugin.invasions().masswar() ? "most van" : plugin.duration(plugin.invasions().masswarNext() - System.currentTimeMillis()) + " múlva")));
            }
            default -> {
                for (String line : plugin.getConfig().getStringList("admin-help")) {
                    sender.sendMessage(Text.color(line));
                }
            }
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.replace("+", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    // ---- tab ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kadmin")) {
            if (args.length == 1) {
                return filter(ADMIN_SUBS, args[0]);
            }
            if (args.length == 2 && Arrays.asList("disband", "rp", "bank", "xp", "level", "join", "setking").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(kingdomNames(), args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return filter(Arrays.asList("structure", "turret"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return filter(new ArrayList<>(args[1].equalsIgnoreCase("turret") ? plugin.settings().turrets.keySet() : plugin.settings().structures.keySet()), args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("masswar")) {
                return filter(Arrays.asList("start", "stop"), args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "join", "info", "ally", "truce", "neutral", "enemy", "home":
                    return filter(kingdomNames(), args[1]);
                case "invite":
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        names.add(online.getName());
                    }
                    return filter(names, args[1]);
                case "kick", "promote", "demote", "king", "rank":
                    Kingdom kingdom = sender instanceof Player player ? km().kingdomOf(player.getUniqueId()) : null;
                    List<String> members = new ArrayList<>();
                    if (kingdom != null) {
                        for (java.util.UUID id : kingdom.members.keySet()) {
                            members.add(km().nameOf(id));
                        }
                    }
                    return filter(members, args[1]);
                case "nation":
                    return filter(NATION_SUBS, args[1]);
                case "chat", "c":
                    return filter(Arrays.asList("k", "a", "g"), args[1]);
                case "nexus":
                    return filter(Collections.singletonList("open"), args[1]);
                case "shield":
                    List<String> hours = new ArrayList<>();
                    for (int value : plugin.settings().shields.keySet()) {
                        hours.add(String.valueOf(value));
                    }
                    return filter(hours, args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3 && sub.equals("rank") && sender instanceof Player player) {
            Kingdom kingdom = km().kingdomOf(player.getUniqueId());
            return kingdom == null ? Collections.emptyList() : filter(new ArrayList<>(kingdom.ranks.keySet()), args[2]);
        }
        if (args.length == 3 && sub.equals("nation") && Arrays.asList("invite", "kick").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(kingdomNames(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> kingdomNames() {
        List<String> names = new ArrayList<>();
        for (Kingdom kingdom : km().all()) {
            names.add(kingdom.name);
        }
        return names;
    }

    private static List<String> filter(List<String> options, String start) {
        List<String> result = new ArrayList<>();
        String lower = start.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
