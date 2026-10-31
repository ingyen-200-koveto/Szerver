package hu.icemine.core.command;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.ConfigFiles;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Economy;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.lobby.Holograms;
import hu.icemine.core.lobby.Npcs;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.mode.ModeService;
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

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("help", "reload", "setlobby", "setspawn", "setportal", "stats", "givecurrency",
            "takecurrency", "setcurrency", "parkour", "combofly", "mode", "maintenance", "hologram", "npc", "rebuild", "arena", "combattag", "info");

    private final IceMineCore core;

    public AdminCommand(IceMineCore core) {
        this.core = core;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        core.messages().send(sender, "general.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        core.messages().send(sender, "general.only-players");
        return null;
    }

    private void log(CommandSender sender, String action, String target, String detail) {
        core.adminLog(sender, action, target, detail);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                if (sender.hasPermission("icemine.config.reload") || allowed(sender, core.permissions().sub("reload"))) {
                    reload(sender, args);
                }
                break;
            case "setlobby":
                setLobby(sender);
                break;
            case "setspawn":
                if (allowed(sender, core.permissions().sub("setspawn"))) {
                    setSpawn(sender, args);
                }
                break;
            case "setportal":
                setPortal(sender, args);
                break;
            case "stats":
                stats(sender, args);
                break;
            case "givecurrency":
            case "takecurrency":
            case "setcurrency":
                currency(sender, sub, args);
                break;
            case "parkour":
                parkour(sender, args);
                break;
            case "combofly":
                combofly(sender, args);
                break;
            case "mode":
                mode(sender, args);
                break;
            case "maintenance":
                maintenance(sender, args);
                break;
            case "hologram":
                hologram(sender, args);
                break;
            case "npc":
                npc(sender, args);
                break;
            case "rebuild":
                rebuild(sender, args);
                break;
            case "arena":
                arena(sender, args);
                break;
            case "combattag":
                combatTag(sender, args);
                break;
            case "info":
                info(sender);
                break;
            default:
                core.messages().sendList(sender, "admin.help");
        }
        return true;
    }

    void reload(CommandSender sender, String[] args) {
        String file = args.length > 1 ? args[1].toLowerCase(Locale.ROOT).replace(".yml", "") : "all";
        if (!file.equals("all") && !Arrays.asList(ConfigFiles.NAMES).contains(file)) {
            core.messages().send(sender, "admin.reload-unknown", "%files%", String.join(", ", ConfigFiles.NAMES));
            return;
        }
        List<String> problems = core.reloadConfigs(file);
        if (problems.isEmpty()) {
            core.messages().send(sender, "admin.reloaded", "%file%", file);
        } else {
            core.messages().send(sender, "admin.reloaded-problems", "%file%", file, "%count%", String.valueOf(problems.size()));
            for (int i = 0; i < Math.min(8, problems.size()); i++) {
                sender.sendMessage(Text.color("&8- &e" + problems.get(i)));
            }
        }
        log(sender, "reload", file, problems.size() + " hiba");
    }

    private void setLobby(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "icemine.admin.setlobby")) {
            return;
        }
        if (player.getWorld() != core.lobby().world()) {
            core.messages().send(sender, "admin.wrong-world");
            return;
        }
        core.lobby().setSpawn(player.getLocation());
        core.messages().send(sender, "admin.lobby-set");
        log(sender, "setlobby", null, location(player));
    }

    private static String location(Player player) {
        return player.getWorld().getName() + " " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ();
    }

    void setSpawn(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem setspawn <skypvp|kitpvp <aréna>|combofly|gens>");
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (!core.setModeSpawn(player, mode, args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null)) {
            core.messages().send(sender, "admin.spawn-failed");
            return;
        }
        core.messages().send(sender, "admin.spawn-set", "%mode%", mode);
        log(sender, "setspawn", mode, location(player));
    }

    private void setPortal(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "icemine.admin.setportal")) {
            return;
        }
        if (args.length < 2 || core.modes().info(args[1]) == null) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin setportal <mód> [sugár] [magasság]");
            return;
        }
        if (player.getWorld() != core.lobby().world()) {
            core.messages().send(sender, "admin.wrong-world");
            return;
        }
        int radius = args.length > 2 ? parse(args[2], 2, 0, 10) : 2;
        int height = args.length > 3 ? parse(args[3], 4, 1, 10) : 4;
        String mode = args[1].toLowerCase(Locale.ROOT);
        core.portals().set(mode, player.getLocation(), radius, height);
        core.messages().send(sender, "admin.portal-set", "%mode%", mode);
        log(sender, "setportal", mode, location(player) + " r=" + radius + " h=" + height);
    }

    private static int parse(String text, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(text)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void stats(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.mod.stats")) {
            return;
        }
        if (args.length < 2) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem stats <játékos>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null && core.profiles().profile(online) != null) {
            printStats(sender, core.profiles().profile(online));
            return;
        }
        core.profiles().lookup(args[1]).whenComplete((profile, error) -> Bukkit.getScheduler().runTask(core, () -> {
            if (profile == null) {
                core.messages().send(sender, "general.no-player");
            } else {
                printStats(sender, profile);
            }
        }));
    }

    /** Statisztika kiírása a chatre/konzolra (a /icem stats <játékos> konzolról ezt használja). */
    void printStatsOf(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && core.profiles().profile(online) != null) {
            printStats(sender, core.profiles().profile(online));
            return;
        }
        core.profiles().lookup(name).whenComplete((profile, error) -> Bukkit.getScheduler().runTask(core, () -> {
            if (profile == null) {
                core.messages().send(sender, "general.no-player");
            } else {
                printStats(sender, profile);
            }
        }));
    }

    private void printStats(CommandSender sender, PlayerProfile profile) {
        sender.sendMessage(core.messages().get("admin.stats-header", "%player%", profile.name(), "%rank%", profile.rank()));
        for (Economy.Currency currency : core.economy().currencies()) {
            sender.sendMessage(Text.color(" &7" + currency.name + ": " + core.economy().format(currency.id, profile.balance(currency.id))));
        }
        for (String mode : new String[]{"kitpvp", "skypvp", "combofly"}) {
            long kills = profile.stat(mode + ".kills");
            long deaths = profile.stat(mode + ".deaths");
            sender.sendMessage(Text.color(" &7" + mode + ": &a" + kills + " &7ölés, &c" + deaths + " &7halál, K/D &f" + Text.ratio(kills, deaths)
                    + "&7, sorozat &e" + profile.stat(mode + ".best_streak")));
        }
        sender.sendMessage(Text.color(" &7Gens bevétel: &e" + Text.number(profile.stat("gens.earned")) + " &8| &7Parkour: &f"
                + (profile.stat("parkour2.best_ms") > 0 ? Text.timer(profile.stat("parkour2.best_ms")) : "-") + " &8| &7Kombó: &b" + profile.stat("combofly.best") + "x"));
        sender.sendMessage(Text.color(" &7Játékidő: &f" + Text.duration(profile.playtime())));
    }

    private void currency(CommandSender sender, String sub, String[] args) {
        if (!allowed(sender, "icemine.admin.currency")) {
            return;
        }
        if (args.length < 3) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin " + sub + " <játékos> <összeg> [pénznem]");
            return;
        }
        long amount = Ledger.parseAmount(args[2]);
        String currency = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : core.economy().defaultCurrency();
        if (amount < 0 || (amount == 0 && !sub.equals("setcurrency"))) {
            core.messages().send(sender, "economy.invalid-amount");
            return;
        }
        if (core.economy().currency(currency) == null) {
            core.messages().send(sender, "economy.unknown-currency");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null && core.profiles().profile(target) != null) {
            Ledger.Result result;
            String reason = "admin:" + sender.getName();
            switch (sub) {
                case "givecurrency":
                    result = core.economy().deposit(target, currency, amount, reason);
                    break;
                case "takecurrency":
                    result = core.economy().withdraw(target, currency, amount, reason);
                    break;
                default:
                    result = core.economy().set(target, currency, amount, reason);
            }
            report(sender, result, target.getName(), currency, core.economy().balance(target, currency));
            log(sender, sub, target.getName(), amount + " " + currency + " -> " + result);
            return;
        }
        if (sub.equals("setcurrency")) {
            core.messages().send(sender, "admin.offline-set");
            return;
        }
        long delta = sub.equals("givecurrency") ? amount : -amount;
        core.profiles().lookup(args[1]).whenComplete((profile, error) -> {
            if (profile == null) {
                Bukkit.getScheduler().runTask(core, () -> core.messages().send(sender, "general.no-player"));
                return;
            }
            core.economy().adjustOffline(profile.uuid(), currency, delta, "admin:" + sender.getName()).whenComplete((ok, failure) ->
                    Bukkit.getScheduler().runTask(core, () -> {
                        core.messages().send(sender, Boolean.TRUE.equals(ok) ? "admin.offline-done" : "admin.offline-failed", "%player%", profile.name());
                        log(sender, sub, profile.name(), delta + " " + currency + " (offline)");
                    }));
        });
    }

    private void report(CommandSender sender, Ledger.Result result, String name, String currency, long balance) {
        if (result == Ledger.Result.OK) {
            core.messages().send(sender, "admin.currency-done", "%player%", name, "%balance%", core.economy().format(currency, balance));
        } else {
            core.messages().send(sender, "economy.failed", "%reason%", result.name());
        }
    }

    private void parkour(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "icemine.admin.parkour")) {
            return;
        }
        if (args.length < 2 || !Arrays.asList("start", "checkpoint", "finish", "clear", "fail").contains(args[1].toLowerCase(Locale.ROOT))) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin parkour <start|checkpoint|finish|clear|fail>");
            return;
        }
        if (player.getWorld() != core.lobby().world()) {
            core.messages().send(sender, "admin.wrong-world");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        core.parkour().setPoint(kind, player.getLocation());
        core.messages().send(sender, "admin.parkour-set", "%kind%", kind);
        log(sender, "parkour", kind, location(player));
    }

    private void combofly(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.combofly")) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload":
                core.reloadConfigs("combofly");
                core.messages().send(sender, "combo.reloaded");
                break;
            case "on":
            case "off":
                core.combo().setEnabled(action.equals("on"));
                core.messages().send(sender, action.equals("on") ? "combo.enabled" : "combo.disabled");
                break;
            default:
                core.messages().send(sender, "admin.usage", "%usage%", "/icem admin combofly <reload|on|off>");
                return;
        }
        log(sender, "combofly", action, null);
    }

    private void mode(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.mode")) {
            return;
        }
        if (args.length < 3) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin mode <játékos> <mód|lobby> [-f]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            core.messages().send(sender, "general.no-player");
            return;
        }
        boolean force = args.length > 3 && args[3].equalsIgnoreCase("-f");
        ModeService.SendResult result = core.modes().send(target, args[2].toLowerCase(Locale.ROOT), force);
        core.messages().send(sender, "admin.mode-result", "%player%", target.getName(), "%result%", result.name());
        log(sender, "mode", target.getName(), args[2] + (force ? " -f" : "") + " -> " + result);
    }

    private void maintenance(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.maintenance")) {
            return;
        }
        if (args.length < 3 || core.modes().info(args[1]) == null) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin maintenance <mód> <on|off|closed>");
            return;
        }
        ModeInfo.Status status;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on":
                status = ModeInfo.Status.MAINTENANCE;
                break;
            case "closed":
                status = ModeInfo.Status.CLOSED;
                break;
            default:
                status = ModeInfo.Status.OPEN;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        core.modes().setStatus(mode, status);
        core.messages().send(sender, "admin.status-set", "%mode%", mode, "%status%", status.name().toLowerCase(Locale.ROOT));
        log(sender, "maintenance", mode, status.name());
    }

    private void hologram(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.hologram")) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("move") && args.length > 2) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (!core.holograms().all().containsKey(args[2])) {
                core.messages().send(sender, "admin.unknown-id");
                return;
            }
            core.holograms().move(args[2], player.getLocation().add(0, 2, 0));
            core.messages().send(sender, "admin.moved", "%id%", args[2]);
            log(sender, "hologram", args[2], location(player));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Holograms.Hologram hologram : core.holograms().all().values()) {
            ids.add(hologram.id + " (" + hologram.type + ")");
        }
        core.messages().send(sender, "admin.list", "%list%", String.join(", ", ids));
    }

    private void npc(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.npc")) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("move") && args.length > 2) {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            if (!core.npcs().all().containsKey(args[2])) {
                core.messages().send(sender, "admin.unknown-id");
                return;
            }
            core.npcs().move(args[2], player.getLocation());
            core.messages().send(sender, "admin.moved", "%id%", args[2]);
            log(sender, "npc", args[2], location(player));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Npcs.Npc npc : core.npcs().all().values()) {
            ids.add(npc.id + " (" + npc.action + ")");
        }
        core.messages().send(sender, "admin.list", "%list%", String.join(", ", ids));
    }

    private void rebuild(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.rebuild")) {
            return;
        }
        if (args.length < 2 || !Arrays.asList("lobby", "skypvp", "kitpvp", "combofly", "gens").contains(args[1].toLowerCase(Locale.ROOT))) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin rebuild <lobby|skypvp|kitpvp|combofly|gens> confirm");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            core.messages().send(sender, "admin.rebuild-confirm", "%target%", args[1].toLowerCase(Locale.ROOT));
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        core.messages().send(sender, "admin.rebuilding", "%target%", target);
        core.rebuild(target);
        core.messages().send(sender, "admin.rebuilt", "%target%", target);
        log(sender, "rebuild", target, null);
    }

    void arena(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.admin.arena")) {
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("reset") || !args[2].equalsIgnoreCase("skypvp")) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem arena reset skypvp");
            return;
        }
        core.skypvp().reset();
        core.messages().send(sender, "admin.arena-reset");
        log(sender, "arena", "skypvp", "reset");
    }

    private void combatTag(CommandSender sender, String[] args) {
        if (!allowed(sender, "icemine.mod.combattag")) {
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) {
            core.messages().send(sender, "admin.usage", "%usage%", "/icem admin combattag clear <játékos>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            core.messages().send(sender, "general.no-player");
            return;
        }
        core.combat().clear(target);
        core.messages().send(sender, "admin.combat-cleared", "%player%", target.getName());
        log(sender, "combattag", target.getName(), "clear");
    }

    private void info(CommandSender sender) {
        if (!allowed(sender, "icemine.admin.info")) {
            return;
        }
        core.messages().sendList(sender, "admin.info",
                "%version%", core.getDescription().getVersion(),
                "%online%", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "%profiles%", String.valueOf(core.profiles().online().size()),
                "%tagged%", String.valueOf(core.combat().count()),
                "%combos%", String.valueOf(core.combo().tracker().size()),
                "%database%", core.database().mysql() ? "MySQL" : "SQLite");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("icemine.admin.tab")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "reload":
                    List<String> files = new ArrayList<>(Arrays.asList(ConfigFiles.NAMES));
                    files.add("all");
                    return filter(files, args[1]);
                case "setspawn":
                    return filter(Arrays.asList("skypvp", "kitpvp", "combofly", "gens"), args[1]);
                case "setportal":
                case "maintenance":
                    return filter(core.modes().ids(), args[1]);
                case "parkour":
                    return filter(Arrays.asList("start", "checkpoint", "finish", "clear", "fail"), args[1]);
                case "combofly":
                    return filter(Arrays.asList("reload", "on", "off"), args[1]);
                case "hologram":
                case "npc":
                    return filter(Arrays.asList("list", "move"), args[1]);
                case "rebuild":
                    return filter(Arrays.asList("lobby", "skypvp", "kitpvp", "combofly", "gens"), args[1]);
                case "arena":
                    return filter(Collections.singletonList("reset"), args[1]);
                case "combattag":
                    return filter(Collections.singletonList("clear"), args[1]);
                default:
                    return null;
            }
        }
        if (args.length == 3) {
            switch (sub) {
                case "mode":
                    List<String> modes = new ArrayList<>(core.modes().ids());
                    modes.add("lobby");
                    return filter(modes, args[2]);
                case "maintenance":
                    return filter(Arrays.asList("on", "off", "closed"), args[2]);
                case "hologram":
                    return filter(new ArrayList<>(core.holograms().all().keySet()), args[2]);
                case "npc":
                    return filter(new ArrayList<>(core.npcs().all().keySet()), args[2]);
                case "arena":
                    return filter(Collections.singletonList("skypvp"), args[2]);
                case "rebuild":
                    return filter(Collections.singletonList("confirm"), args[2]);
                case "combattag":
                    return null;
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 4 && sub.endsWith("currency")) {
            List<String> ids = new ArrayList<>();
            for (Economy.Currency currency : core.economy().currencies()) {
                ids.add(currency.id);
            }
            return filter(ids, args[3]);
        }
        return Collections.emptyList();
    }

    static List<String> filter(List<String> options, String typed) {
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
