package hu.icemine.kingdoms;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class KingdomCommands implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "icemine.kingdoms.admin";
    private static final List<String> SUBS = Arrays.asList("create", "invite", "join", "leave", "kick", "promote", "demote", "king",
            "disband", "claim", "unclaim", "home", "sethome", "spawn", "info", "list", "top", "map", "chat", "help");
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        String[][] pairs = {{"alapit", "create"}, {"alapitas", "create"}, {"meghiv", "invite"}, {"csatlakoz", "join"}, {"belep", "join"},
                {"kilep", "leave"}, {"kirug", "kick"}, {"eloleptet", "promote"}, {"lefokoz", "demote"}, {"kiraly", "king"}, {"atad", "king"},
                {"feloszlat", "disband"}, {"foglal", "claim"}, {"felszabadit", "unclaim"}, {"otthon", "home"}, {"h", "home"},
                {"otthonbeallit", "sethome"}, {"infó", "info"}, {"lista", "list"}, {"terkep", "map"}, {"c", "chat"}, {"segitseg", "help"}};
        for (String[] pair : pairs) {
            ALIASES.put(pair[0], pair[1]);
        }
    }

    private final IceMineKingdoms plugin;
    private final Map<UUID, Long> disbandConfirm = new HashMap<>();

    KingdomCommands(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private String msg(String key) {
        return plugin.message(key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kadmin")) {
            admin(sender, args);
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        sub = ALIASES.getOrDefault(sub, sub);
        if (sub.equals("info")) {
            info(sender, args);
            return true;
        }
        if (sub.equals("list") || sub.equals("top")) {
            list(sender, sub.equals("top"));
            return true;
        }
        if (sub.equals("help") || !SUBS.contains(sub)) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("only-players"));
            return true;
        }
        Player player = (Player) sender;
        plugin.remember(player);
        switch (sub) {
            case "create":
                create(player, args);
                break;
            case "invite":
                invite(player, args);
                break;
            case "join":
                join(player, args);
                break;
            case "leave":
                leave(player);
                break;
            case "kick":
                kick(player, args);
                break;
            case "promote":
                promote(player, args, true);
                break;
            case "demote":
                promote(player, args, false);
                break;
            case "king":
                crown(player, args);
                break;
            case "disband":
                disband(player);
                break;
            case "claim":
                claim(player);
                break;
            case "unclaim":
                unclaim(player);
                break;
            case "home":
                home(player);
                break;
            case "sethome":
                setHome(player);
                break;
            case "spawn":
                spawn(player);
                break;
            case "map":
                map(player);
                break;
            case "chat":
                chat(player);
                break;
            default:
                help(sender);
                break;
        }
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : plugin.getConfig().getStringList("help")) {
            sender.sendMessage(Text.color(line));
        }
    }

    private Kingdom mine(Player player) {
        Kingdom kingdom = plugin.kingdomOf(player.getUniqueId());
        if (kingdom == null) {
            player.sendMessage(msg("no-kingdom"));
        }
        return kingdom;
    }

    private boolean inWorld(Player player) {
        if (!plugin.inKingdoms(player)) {
            player.sendMessage(msg("only-in-kingdoms"));
            return false;
        }
        return true;
    }

    private void broadcast(Kingdom kingdom, String text) {
        for (UUID id : kingdom.members.keySet()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                member.sendMessage(text);
            }
        }
    }

    private UUID target(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg("need-player"));
            return null;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        UUID id = online != null ? online.getUniqueId() : plugin.idOf(args[1]);
        if (id == null) {
            player.sendMessage(msg("no-player"));
        }
        return id;
    }

    private void create(Player player, String[] args) {
        if (plugin.kingdomOf(player.getUniqueId()) != null) {
            player.sendMessage(msg("already-in-kingdom"));
            return;
        }
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(msg("bad-name"));
            return;
        }
        if (plugin.kingdom(args[1]) != null) {
            player.sendMessage(msg("name-taken"));
            return;
        }
        Kingdom kingdom = plugin.create(player, args[1]);
        player.sendMessage(msg("created").replace("%kingdom%", kingdom.name));
        String announce = msg("created-broadcast").replace("%player%", player.getName()).replace("%kingdom%", kingdom.name);
        for (Player other : plugin.world().getPlayers()) {
            if (!other.equals(player)) {
                other.sendMessage(announce);
            }
        }
    }

    private void invite(Player player, String[] args) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.canManage(player.getUniqueId())) {
            player.sendMessage(msg("not-manager"));
            return;
        }
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (target == null) {
            player.sendMessage(msg("no-player"));
            return;
        }
        if (plugin.kingdomOf(target.getUniqueId()) != null) {
            player.sendMessage(msg("player-has-kingdom").replace("%player%", target.getName()));
            return;
        }
        int max = plugin.getConfig().getInt("max-members", 20);
        if (kingdom.members.size() >= max) {
            player.sendMessage(msg("kingdom-full").replace("%max%", String.valueOf(max)));
            return;
        }
        plugin.invite(target.getUniqueId(), kingdom);
        player.sendMessage(msg("invited").replace("%player%", target.getName()));
        target.sendMessage(msg("invite-received").replace("%kingdom%", kingdom.name).replace("%player%", player.getName()));
    }

    private void join(Player player, String[] args) {
        if (plugin.kingdomOf(player.getUniqueId()) != null) {
            player.sendMessage(msg("already-in-kingdom"));
            return;
        }
        String invited = plugin.invitation(player.getUniqueId());
        Kingdom kingdom = args.length > 1 ? plugin.kingdom(args[1]) : (invited == null ? null : plugin.kingdom(invited));
        if (kingdom == null || invited == null || !invited.equals(kingdom.name.toLowerCase(Locale.ROOT))) {
            player.sendMessage(msg("no-invite"));
            return;
        }
        int max = plugin.getConfig().getInt("max-members", 20);
        if (kingdom.members.size() >= max) {
            player.sendMessage(msg("kingdom-full").replace("%max%", String.valueOf(max)));
            return;
        }
        plugin.addMember(kingdom, player.getUniqueId());
        broadcast(kingdom, msg("joined").replace("%player%", player.getName()).replace("%kingdom%", kingdom.name));
    }

    private void leave(Player player) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (kingdom.role(player.getUniqueId()) == Kingdom.Role.KING) {
            player.sendMessage(msg("king-cant-leave"));
            return;
        }
        plugin.removeMember(kingdom, player.getUniqueId());
        player.sendMessage(msg("you-left"));
        broadcast(kingdom, msg("left").replace("%player%", player.getName()));
    }

    private void kick(Player player, String[] args) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.canManage(player.getUniqueId())) {
            player.sendMessage(msg("not-manager"));
            return;
        }
        UUID id = target(player, args);
        if (id == null) {
            return;
        }
        String name = plugin.nameOf(id);
        Kingdom.Role role = kingdom.role(id);
        if (role == null) {
            player.sendMessage(msg("not-member").replace("%player%", name));
            return;
        }
        if (role.power >= kingdom.role(player.getUniqueId()).power) {
            player.sendMessage(msg("cant-kick"));
            return;
        }
        plugin.removeMember(kingdom, id);
        broadcast(kingdom, msg("kicked").replace("%player%", name));
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            online.sendMessage(msg("you-were-kicked").replace("%kingdom%", kingdom.name));
        }
    }

    private void promote(Player player, String[] args, boolean up) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (kingdom.role(player.getUniqueId()) != Kingdom.Role.KING) {
            player.sendMessage(msg("not-king"));
            return;
        }
        UUID id = target(player, args);
        if (id == null) {
            return;
        }
        String name = plugin.nameOf(id);
        Kingdom.Role role = kingdom.role(id);
        if (role == null || role == Kingdom.Role.KING) {
            player.sendMessage(msg("not-member").replace("%player%", name));
            return;
        }
        if (up && role == Kingdom.Role.KNIGHT) {
            player.sendMessage(msg("already-knight").replace("%player%", name));
            return;
        }
        if (!up && role == Kingdom.Role.MEMBER) {
            player.sendMessage(msg("not-knight").replace("%player%", name));
            return;
        }
        kingdom.members.put(id, up ? Kingdom.Role.KNIGHT : Kingdom.Role.MEMBER);
        plugin.markDirty();
        broadcast(kingdom, msg(up ? "promoted" : "demoted").replace("%player%", name));
    }

    private void crown(Player player, String[] args) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (kingdom.role(player.getUniqueId()) != Kingdom.Role.KING) {
            player.sendMessage(msg("not-king"));
            return;
        }
        UUID id = target(player, args);
        if (id == null) {
            return;
        }
        if (id.equals(player.getUniqueId()) || kingdom.role(id) == null) {
            player.sendMessage(msg("not-member").replace("%player%", plugin.nameOf(id)));
            return;
        }
        kingdom.members.put(player.getUniqueId(), Kingdom.Role.KNIGHT);
        kingdom.members.put(id, Kingdom.Role.KING);
        kingdom.leader = id;
        plugin.markDirty();
        broadcast(kingdom, msg("new-king").replace("%player%", plugin.nameOf(id)));
    }

    private void disband(Player player) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (kingdom.role(player.getUniqueId()) != Kingdom.Role.KING) {
            player.sendMessage(msg("not-king"));
            return;
        }
        long now = System.currentTimeMillis();
        Long asked = disbandConfirm.get(player.getUniqueId());
        if (asked == null || now - asked > 15000) {
            disbandConfirm.put(player.getUniqueId(), now);
            player.sendMessage(msg("disband-confirm"));
            return;
        }
        disbandConfirm.remove(player.getUniqueId());
        String text = msg("disbanded").replace("%kingdom%", kingdom.name);
        broadcast(kingdom, text);
        plugin.disband(kingdom);
    }

    private void claim(Player player) {
        if (!inWorld(player)) {
            return;
        }
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.canManage(player.getUniqueId())) {
            player.sendMessage(msg("not-manager"));
            return;
        }
        Location location = player.getLocation();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        if (plugin.chunkNearSpawn(cx, cz)) {
            player.sendMessage(msg("claim-spawn"));
            return;
        }
        if (!plugin.insideBorder(cx, cz)) {
            player.sendMessage(msg("claim-border"));
            return;
        }
        Kingdom owner = plugin.ownerOf(cx, cz);
        if (owner == kingdom) {
            player.sendMessage(msg("claim-own"));
            return;
        }
        if (owner != null) {
            player.sendMessage(msg("claim-taken").replace("%kingdom%", owner.name));
            return;
        }
        int max = plugin.maxClaims(kingdom);
        if (kingdom.claims.size() >= max) {
            player.sendMessage(msg("claim-limit").replace("%max%", String.valueOf(max)));
            return;
        }
        if (!kingdom.claims.isEmpty() && !plugin.adjacent(kingdom, cx, cz)) {
            player.sendMessage(msg("claim-adjacent"));
            return;
        }
        boolean first = kingdom.claims.isEmpty();
        plugin.claim(kingdom, cx, cz);
        if (first && kingdom.home == null) {
            kingdom.home = new double[]{location.getX(), location.getY(), location.getZ(), location.getYaw()};
        }
        player.sendMessage(msg("claimed").replace("%claims%", String.valueOf(kingdom.claims.size())).replace("%max%", String.valueOf(max)));
        plugin.checkArea(player, location);
    }

    private void unclaim(Player player) {
        if (!inWorld(player)) {
            return;
        }
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.canManage(player.getUniqueId())) {
            player.sendMessage(msg("not-manager"));
            return;
        }
        Location location = player.getLocation();
        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        if (plugin.ownerOf(cx, cz) != kingdom) {
            player.sendMessage(msg("not-your-claim"));
            return;
        }
        plugin.unclaim(kingdom, cx, cz);
        if (kingdom.home != null && ((int) Math.floor(kingdom.home[0]) >> 4) == cx && ((int) Math.floor(kingdom.home[2]) >> 4) == cz) {
            kingdom.home = null;
        }
        player.sendMessage(msg("unclaimed"));
    }

    private void home(Player player) {
        if (!inWorld(player)) {
            return;
        }
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        Location home = plugin.home(kingdom);
        if (home == null) {
            player.sendMessage(msg("home-not-set"));
            return;
        }
        plugin.teleport(player, home);
    }

    private void setHome(Player player) {
        if (!inWorld(player)) {
            return;
        }
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.canManage(player.getUniqueId())) {
            player.sendMessage(msg("not-manager"));
            return;
        }
        Location location = player.getLocation();
        if (plugin.owner(location) != kingdom) {
            player.sendMessage(msg("home-outside"));
            return;
        }
        kingdom.home = new double[]{location.getX(), location.getY(), location.getZ(), location.getYaw()};
        plugin.markDirty();
        player.sendMessage(msg("home-set"));
    }

    private void spawn(Player player) {
        if (inWorld(player)) {
            plugin.teleport(player, plugin.spawn());
        }
    }

    private void chat(Player player) {
        if (mine(player) == null) {
            return;
        }
        player.sendMessage(msg(plugin.toggleChat(player.getUniqueId()) ? "chat-on" : "chat-off"));
    }

    private void info(CommandSender sender, String[] args) {
        Kingdom kingdom;
        if (args.length > 1) {
            kingdom = plugin.kingdom(args[1]);
            if (kingdom == null) {
                UUID id = plugin.idOf(args[1]);
                kingdom = id == null ? null : plugin.kingdomOf(id);
            }
            if (kingdom == null) {
                sender.sendMessage(msg("not-found"));
                return;
            }
        } else if (sender instanceof Player) {
            kingdom = mine((Player) sender);
            if (kingdom == null) {
                return;
            }
        } else {
            sender.sendMessage(msg("only-players"));
            return;
        }
        List<String> names = new ArrayList<>();
        for (Kingdom.Role role : Kingdom.Role.values()) {
            for (Map.Entry<UUID, Kingdom.Role> entry : kingdom.members.entrySet()) {
                if (entry.getValue() != role) {
                    continue;
                }
                String color = Bukkit.getPlayer(entry.getKey()) != null ? "&a" : "&7";
                String mark = role == Kingdom.Role.KING ? "♛" : (role == Kingdom.Role.KNIGHT ? "⚔" : "");
                names.add(color + mark + plugin.nameOf(entry.getKey()));
            }
        }
        String date = new SimpleDateFormat("yyyy.MM.dd.").format(new Date(kingdom.created));
        for (String line : plugin.getConfig().getStringList("info")) {
            sender.sendMessage(Text.color(line
                    .replace("%kingdom%", kingdom.name)
                    .replace("%king%", plugin.nameOf(kingdom.leader))
                    .replace("%date%", date)
                    .replace("%members%", String.valueOf(kingdom.members.size()))
                    .replace("%list%", String.join("&8, ", names))
                    .replace("%claims%", String.valueOf(kingdom.claims.size()))
                    .replace("%max%", String.valueOf(plugin.maxClaims(kingdom)))));
        }
    }

    private void list(CommandSender sender, boolean byLand) {
        List<Kingdom> all = plugin.all();
        if (all.isEmpty()) {
            sender.sendMessage(msg("no-kingdoms"));
            return;
        }
        all.sort((a, b) -> byLand ? Integer.compare(b.claims.size(), a.claims.size()) : Integer.compare(b.members.size(), a.members.size()));
        String header = plugin.getConfig().getString(byLand ? "top-header" : "list-header", "");
        sender.sendMessage(Text.color(header.replace("%count%", String.valueOf(all.size()))));
        int place = 1;
        for (Kingdom kingdom : all) {
            if (place > 10) {
                break;
            }
            sender.sendMessage(Text.color(plugin.getConfig().getString("list-line", "")
                    .replace("%place%", String.valueOf(place++))
                    .replace("%kingdom%", kingdom.name)
                    .replace("%king%", plugin.nameOf(kingdom.leader))
                    .replace("%members%", String.valueOf(kingdom.members.size()))
                    .replace("%claims%", String.valueOf(kingdom.claims.size()))));
        }
    }

    private void map(Player player) {
        if (!inWorld(player)) {
            return;
        }
        Kingdom kingdom = plugin.kingdomOf(player.getUniqueId());
        Location location = player.getLocation();
        int pcx = location.getBlockX() >> 4;
        int pcz = location.getBlockZ() >> 4;
        player.sendMessage(Text.color(plugin.getConfig().getString("map.header", "")));
        for (int dz = -4; dz <= 4; dz++) {
            StringBuilder row = new StringBuilder(" ");
            for (int dx = -8; dx <= 8; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                Kingdom owner = plugin.ownerOf(cx, cz);
                String cell;
                if (dx == 0 && dz == 0) {
                    cell = "&e◆";
                } else if (owner != null) {
                    cell = owner == kingdom ? "&a■" : "&c■";
                } else if (plugin.chunkNearSpawn(cx, cz)) {
                    cell = "&b■";
                } else {
                    cell = "&8■";
                }
                row.append(cell);
            }
            player.sendMessage(Text.color(row.toString()));
        }
        player.sendMessage(Text.color(plugin.getConfig().getString("map.legend", "")));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(msg("no-permission"));
            return;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "reload":
                plugin.reload();
                sender.sendMessage(msg("reloaded"));
                return;
            case "disband": {
                Kingdom kingdom = args.length > 1 ? plugin.kingdom(args[1]) : null;
                if (kingdom == null) {
                    sender.sendMessage(msg("not-found"));
                    return;
                }
                broadcast(kingdom, msg("disbanded").replace("%kingdom%", kingdom.name));
                plugin.disband(kingdom);
                sender.sendMessage(msg("admin-disbanded").replace("%kingdom%", kingdom.name));
                return;
            }
            case "unclaim": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(msg("only-players"));
                    return;
                }
                Location location = ((Player) sender).getLocation();
                Kingdom owner = plugin.owner(location);
                if (owner == null || location.getWorld() != plugin.world()) {
                    sender.sendMessage(msg("not-found"));
                    return;
                }
                plugin.unclaim(owner, location.getBlockX() >> 4, location.getBlockZ() >> 4);
                sender.sendMessage(msg("admin-unclaimed"));
                return;
            }
            case "rebuild":
                sender.sendMessage(msg("rebuilding"));
                plugin.rebuildSpawn();
                sender.sendMessage(msg("rebuilt"));
                return;
            default:
                for (String line : plugin.getConfig().getStringList("admin-help")) {
                    sender.sendMessage(Text.color(line));
                }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kadmin")) {
            if (!sender.hasPermission(ADMIN)) {
                return Collections.emptyList();
            }
            if (args.length == 1) {
                return filter(Arrays.asList("reload", "disband", "unclaim", "rebuild"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("disband")) {
                return filter(kingdomNames(), args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            String sub = ALIASES.getOrDefault(args[0].toLowerCase(Locale.ROOT), args[0].toLowerCase(Locale.ROOT));
            switch (sub) {
                case "invite":
                case "kick":
                case "promote":
                case "demote":
                case "king":
                    return null;
                case "info":
                case "join":
                    return filter(kingdomNames(), args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private List<String> kingdomNames() {
        List<String> names = new ArrayList<>();
        for (Kingdom kingdom : plugin.all()) {
            names.add(kingdom.name);
        }
        return names;
    }

    private List<String> filter(List<String> options, String typed) {
        List<String> result = new ArrayList<>();
        String lower = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
