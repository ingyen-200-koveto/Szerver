package hu.icemine.core.report;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /jelent és /segitseg: a jelzés a weboldalon át a Discord staff csatornájába kerül, és a fent lévő staff is látja. */
public final class ReportCommands implements CommandExecutor, TabCompleter {

    public static final String STAFF_PERMISSION = "icemine.mod.reports";
    private static final long REPORT_COOLDOWN = 60_000L;
    private static final long ADMIN_COOLDOWN = 300_000L;

    private final IceMineCore core;
    private final Map<UUID, Long> lastReport = new HashMap<>();
    private final Map<UUID, Long> lastAdmin = new HashMap<>();

    public ReportCommands(IceMineCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            core.messages().send(sender, "general.only-players");
            return true;
        }
        Player player = (Player) sender;
        if (core.auth() != null && !core.auth().isLoggedIn(player)) {
            say(player, "&cElőbb jelentkezz be.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("segitseg")) {
            admin(player, args);
        } else {
            report(player, args);
        }
        return true;
    }

    private void report(Player player, String[] args) {
        if (args.length < 2) {
            say(player, "&7Használat: &f/jelent <játékos> <indok>");
            return;
        }
        String target = args[0];
        if (!target.matches("\\.?[A-Za-z0-9_]{1,16}")) {
            say(player, "&cEz nem érvényes játékosnév.");
            return;
        }
        if (target.equalsIgnoreCase(player.getName())) {
            say(player, "&cMagadat nem jelentheted.");
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (reason.length() < 3) {
            say(player, "&cÍrd le röviden, mi történt. Pl.: &f/jelent " + target + " repül");
            return;
        }
        if (reason.length() > 300) {
            reason = reason.substring(0, 300);
        }
        if (cooling(player, lastReport, REPORT_COOLDOWN)) {
            return;
        }
        Player online = Bukkit.getPlayerExact(target);
        String targetName = online != null ? online.getName() : target;
        String mode = modeName(player);
        String json = "{\"tipus\":\"report\",\"jatekos\":" + q(player.getName()) + ",\"celpont\":" + q(targetName)
                + ",\"celpontOnline\":" + (online != null) + ",\"szoveg\":" + q(reason) + ",\"mod\":" + q(mode)
                + ",\"vilag\":" + q(player.getWorld().getName()) + "}";
        final String why = reason;
        send(player, json, "&aKöszönjük! A jelentésedet elküldtük a staffnak.", lastReport);
        notifyStaff(player, "&c[Jelentés] &f" + player.getName() + " &7→ &f" + targetName + " &8(" + mode + ")&7: &f" + why);
    }

    private void admin(Player player, String[] args) {
        String text = String.join(" ", args).trim();
        if (text.length() < 3) {
            say(player, "&7Használat: &f/segitseg <üzenet>&7, pl. &f/segitseg beragadtam a falba");
            return;
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        if (cooling(player, lastAdmin, ADMIN_COOLDOWN)) {
            return;
        }
        String mode = modeName(player);
        String json = "{\"tipus\":\"admin\",\"jatekos\":" + q(player.getName()) + ",\"szoveg\":" + q(text) + ",\"mod\":" + q(mode)
                + ",\"vilag\":" + q(player.getWorld().getName()) + "}";
        send(player, json, "&aSzóltunk a staffnak, hamarosan jön valaki. Kérjük, várj türelemmel.", lastAdmin);
        notifyStaff(player, "&6[Segítségkérés] &f" + player.getName() + " &8(" + mode + ")" + (text.isEmpty() ? "" : "&7: &f" + text));
    }

    private void send(Player player, String json, String ok, Map<UUID, Long> cooldown) {
        cooldown.put(player.getUniqueId(), System.currentTimeMillis());
        UUID id = player.getUniqueId();
        core.iceCoins().post("/api/mc/jelzes", json, status -> {
            Player p = Bukkit.getPlayer(id);
            if (status >= 200 && status < 300) {
                if (p != null) {
                    say(p, ok);
                }
                return;
            }
            if (status == 429) {
                if (p != null) {
                    say(p, "&cTúl gyakran próbálod. Várj egy kicsit.");
                }
                return;
            }
            cooldown.remove(id);
            core.getLogger().warning("Jelzés: a weboldal nem fogadta (HTTP " + status + "). Ellenőrizd a config.yml icecoin.url és icecoin.api-key beállítását.");
            if (p != null) {
                say(p, "&cMost nem sikerült elküldeni. Próbáld újra, vagy írj nekünk a Discordon.");
            }
        });
    }

    private boolean cooling(Player player, Map<UUID, Long> map, long cooldown) {
        Long last = map.get(player.getUniqueId());
        long left = last == null ? 0 : last + cooldown - System.currentTimeMillis();
        if (left > 0) {
            say(player, "&cEzt " + Math.max(1, (left + 999) / 1000) + " másodperc múlva használhatod újra.");
            return true;
        }
        return false;
    }

    private void notifyStaff(Player from, String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != from && p.hasPermission(STAFF_PERMISSION)) {
                p.sendMessage(Text.color(text));
            }
        }
    }

    private String modeName(Player player) {
        if (core.modes() == null) {
            return "Lobby";
        }
        String id = core.modes().current(player);
        ModeInfo info = id == null ? null : core.modes().info(id);
        if (info == null) {
            return "Lobby";
        }
        String name = Text.strip(Text.color(info.name == null ? id : info.name)).trim();
        return name.isEmpty() ? id : name;
    }

    private void say(Player player, String text) {
        core.messages().sendTagged(player, text);
    }

    public static String q(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : String.valueOf(value).toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("jelent") && args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && !p.hasMetadata("NPC") && p.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
