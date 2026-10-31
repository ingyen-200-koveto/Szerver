package hu.icemine.core.duels;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class DuelsCommand implements CommandExecutor, TabCompleter {
    private final IceMineCore core;
    private final Duels duels;

    public DuelsCommand(IceMineCore iceminecore, Duels duels) {
        this.core = iceminecore;
        this.duels = duels;
    }

    @Override
    public boolean onCommand(CommandSender commandsender, Command command, String s, String[] astring) {
        if (!(commandsender instanceof Player player)) {
            this.core.messages().send(commandsender, "general.only-players");
            return true;
        } else {
            if (!this.core.auth().isLoggedIn(player)) {
                this.core.messages().send(player, "auth.login-first");
                return true;
            }

            if (command.getName().equalsIgnoreCase("duel")) {
                this.duel(player, astring);
            } else {
                this.duels(player, astring);
            }

            return true;
        }
    }

    private void duel(Player player, String[] astring) {
        if (astring.length == 0) {
            this.core.messages().sendList(player, "duels.help");
        } else {
            String s = astring[0].toLowerCase(Locale.ROOT);
            switch (s) {
                case "accept":
                case "elfogad":
                    this.duels.accept(player, astring.length > 1 ? astring[1] : null);
                    return;
                case "deny":
                case "elutasit":
                    this.duels.deny(player, astring.length > 1 ? astring[1] : null);
                    return;
                case "rematch":
                case "visszavago":
                    this.duels.rematch(player);
                    return;
                default:
                    Player player1 = Bukkit.getPlayerExact(astring[0]);
                    if (player1 == null) {
                        this.core.messages().send(player, "general.no-player");
                    } else {
                        int i = astring.length > 2 && astring[2].equalsIgnoreCase("bo3") ? 3 : 1;
                        if (astring.length > 1 && astring[1].equalsIgnoreCase("bo3")) {
                            byte b0 = 3;
                            this.duels.menu().chooseForInvite(player, player1, b0);
                        } else if (astring.length > 1) {
                            this.duels.invite(player, player1, astring[1], i);
                        } else {
                            this.duels.menu().chooseForInvite(player, player1, 1);
                        }
                    }
            }
        }
    }

    private void duels(Player player, String[] astring) {
        if (astring.length == 0) {
            this.duels.menu().open(player);
        } else {
            switch (astring[0].toLowerCase(Locale.ROOT)) {
                case "queue":
                case "sor":
                    if (astring.length < 2) {
                        this.duels.menu().open(player);
                        return;
                    }

                    this.duels.join(player, astring[1], astring.length > 2 && astring[2].equalsIgnoreCase("ranked"));
                    return;
                case "leave":
                case "kilep":
                    if (this.duels.spectating(player)) {
                        this.duels.stopSpectating(player);
                    } else if (!this.duels.leaveQueue(player, true)) {
                        if (this.duels.match(player) != null) {
                            this.duels.forfeit(player);
                            this.duels.sendHome(player);
                        } else {
                            this.core.messages().send(player, "duels.not-in-queue");
                        }
                    }

                    return;
                case "spectate":
                case "nez":
                    if (astring.length < 2) {
                        this.duels.menu().spectate(player);
                        return;
                    } else {
                        Player player1 = Bukkit.getPlayerExact(astring[1]);
                        if (player1 == null) {
                            this.core.messages().send(player, "general.no-player");
                            return;
                        }

                        this.duels.spectate(player, player1);
                        return;
                    }
                case "stats":
                    this.duels.menu().stats(player);
                    return;
                case "top":
                    this.core.messages().send(player, "duels.top-header");
                    for (String s : new String[]{"duelswins", "duelsstreak", "duelselo"}) {
                        for (String s1 : this.core.leaderboards().lines(s)) {
                            player.sendMessage(Text.color(s1));
                        }
                    }

                    return;
                case "rebuild":
                    if (!player.hasPermission("icemine.admin.duels")) {
                        this.core.messages().send(player, "general.no-permission");
                        return;
                    }

                    int i = this.duels.rebuildFree();
                    this.core.messages().send(player, "duels.rebuilt", "%count%", String.valueOf(i));
                    return;
                default:
                    this.core.messages().sendList(player, "duels.help");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender commandsender, Command command, String s1, String[] astring) {
        List<String> arraylist = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("duel")) {
            if (astring.length == 1) {
                arraylist.addAll(Arrays.asList("accept", "deny", "rematch"));

                for (Player player : Bukkit.getOnlinePlayers()) {
                    arraylist.add(player.getName());
                }
            } else if (astring.length == 2 && !Arrays.asList("accept", "deny", "rematch").contains(astring[0].toLowerCase(Locale.ROOT))) {
                arraylist.addAll(this.duels.kits().keySet());
                arraylist.add("bo3");
            } else if (astring.length == 3) {
                arraylist.add("bo3");
            }
        } else if (astring.length == 1) {
            arraylist.addAll(Arrays.asList("queue", "leave", "spectate", "stats", "top"));
        } else if (astring.length == 2 && astring[0].equalsIgnoreCase("queue")) {
            arraylist.addAll(this.duels.kits().keySet());
        } else if (astring.length == 3 && astring[0].equalsIgnoreCase("queue")) {
            arraylist.add("ranked");
        }

        String s = astring.length == 0 ? "" : astring[astring.length - 1].toLowerCase(Locale.ROOT);
        arraylist.removeIf(s2 -> !s2.toLowerCase(Locale.ROOT).startsWith(s));
        return arraylist;
    }
}
