package hu.icemine.core.skywars;

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

/** /sw: menü, beszállás, kilépés, néző, statisztika, toplista és admin parancsok. */
public final class SwCommand implements CommandExecutor, TabCompleter {

    private final Skywars plugin;

    public SwCommand(Skywars plugin) {
        this.plugin = plugin;
    }

    private void say(CommandSender s, String text) {
        plugin.core().messages().sendTagged(s, text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (plugin.world() == null) {
            say(sender, "&cA SkyWars most nem elérhető.");
            return true;
        }
        Player p = sender instanceof Player pl ? pl : null;
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (p != null) {
                    plugin.menu(p);
                } else {
                    help(sender);
                }
            }
            case "play", "jatek" -> {
                if (p == null) {
                    return true;
                }
                SwMode m = args.length > 1 ? plugin.mode(args[1]) : null;
                if (m == null) {
                    plugin.menu(p);
                } else {
                    plugin.play(p, m);
                }
            }
            case "leave", "kilep" -> {
                if (p == null) {
                    return true;
                }
                SwArena a = plugin.arenaOf(p);
                if (a == null) {
                    say(p, "&cNem vagy SkyWars meccsben.");
                } else {
                    a.leave(p, true);
                }
            }
            case "nez", "spectate" -> {
                if (p != null && args.length > 1) {
                    plugin.spectate(p, Bukkit.getPlayerExact(args[1]));
                }
            }
            case "stats", "stat" -> stats(sender, args.length > 1 ? args[1] : null);
            case "ketrec", "ketrecek", "cage", "cages" -> {
                if (sender instanceof Player player) {
                    plugin.cages().menu(player);
                }
            }
            case "top" -> {
                for (String board : new String[]{"skywarswins", "skywarskills"}) {
                    for (String line : plugin.core().leaderboards().lines(board)) {
                        sender.sendMessage(hu.icemine.core.util.Text.color(line));
                    }
                }
            }
            case "list", "lista" -> {
                for (SwArena a : plugin.allArenas()) {
                    sender.sendMessage(hu.icemine.core.util.Text.color("&7" + a.id + " &8- &f" + a.map.display + " &8- &e" + a.state() + " &8- &a" + a.size() + "/" + a.capacity()));
                }
            }
            case "start", "stop", "rebuild" -> admin(sender, sub, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        say(s, "&b&lSkyWars");
        for (String line : new String[]{"&f/sw &7- mód választó", "&f/sw play <solo|duo> &7- beszállás", "&f/sw leave &7- kilépés",
                "&f/sw nez <játékos> &7- néző", "&f/sw stats [játékos]", "&f/sw top", "&f/sw ketrec &7- ketrecek"}) {
            s.sendMessage(hu.icemine.core.util.Text.color(line));
        }
        if (s.hasPermission(Skywars.ADMIN)) {
            s.sendMessage(hu.icemine.core.util.Text.color("&c/sw start|stop|rebuild <aréna>&7, &c/sw list"));
        }
    }

    private void stats(CommandSender s, String name) {
        OfflinePlayer target = name == null ? (s instanceof Player p ? p : null) : Bukkit.getOfflinePlayer(name);
        hu.icemine.core.data.PlayerProfile pr = target == null ? null : plugin.core().profiles().profile(target.getUniqueId());
        if (pr == null) {
            say(s, "&cErről a játékosról nincs adat (csak fent lévőkét látom).");
            return;
        }
        say(s, "&b&lSkyWars &7- &f" + pr.name());
        long k = pr.stat("skywars.kills");
        long d = pr.stat("skywars.deaths");
        s.sendMessage(hu.icemine.core.util.Text.color("&fGyőzelmek: &a" + pr.stat("skywars.wins") + " &8| &fMeccsek: &f" + pr.stat("skywars.games")));
        s.sendMessage(hu.icemine.core.util.Text.color("&fÖlések: &c" + k + " &8| &fHalálok: &7" + d + " &8| &fK/D: &f" + hu.icemine.core.util.Text.ratio(k, d)));
        s.sendMessage(hu.icemine.core.util.Text.color("&fGyőzelmi sorozat: &e" + pr.stat("skywars.streak") + " &8(legjobb: " + pr.stat("skywars.best_streak") + ")"));
    }

    private void admin(CommandSender s, String sub, String[] args) {
        if (!s.hasPermission(Skywars.ADMIN)) {
            say(s, "&cEhhez nincs jogod.");
            return;
        }
        SwArena a = args.length > 1 ? plugin.arena(args[1]) : (s instanceof Player p ? plugin.arenaOf(p) : null);
        if (a == null) {
            say(s, "&cAdd meg az arénát (pl. solo-1). Lista: &f/sw list");
            return;
        }
        switch (sub) {
            case "start" -> {
                a.forceStart();
                say(s, "&aIndítás: " + a.id);
            }
            case "stop" -> {
                a.forceStop();
                say(s, "&eLeállítva: " + a.id);
            }
            default -> {
                if (a.size() > 0) {
                    say(s, "&cMost játszanak rajta.");
                } else {
                    plugin.queueBuild(a);
                    say(s, "&aÚjraépítés sorban: " + a.id);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String a = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("play", "leave", "nez", "stats", "top", "ketrec"));
            if (sender.hasPermission(Skywars.ADMIN)) {
                subs.addAll(List.of("start", "stop", "rebuild", "list"));
            }
            for (String s : subs) {
                if (s.startsWith(a)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            for (SwMode m : plugin.modes().values()) {
                if (m.id.startsWith(a)) {
                    out.add(m.id);
                }
            }
        } else if (args.length == 2 && List.of("start", "stop", "rebuild").contains(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission(Skywars.ADMIN)) {
            for (SwArena ar : plugin.allArenas()) {
                if (ar.id.startsWith(a)) {
                    out.add(ar.id);
                }
            }
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
