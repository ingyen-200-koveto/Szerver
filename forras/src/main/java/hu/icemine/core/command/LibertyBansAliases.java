package hu.icemine.core.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Az Essentials és a LibertyBans is lefoglalja a /ban, /mute, /kick, /unban, /unbanip parancsot, és a LibertyBans
 * minden indításkor hosszan panaszkodik rá. Ezeket a LibertyBans configjából kivesszük, és helyettük mi irányítjuk
 * át a /libertybans parancsra, így a büntetés mindig a LibertyBansé (magyar üzenetekkel).
 */
public final class LibertyBansAliases {

    static final Map<String, String> ALIASES = new LinkedHashMap<>();
    static final Pattern CONFLICT = Pattern.compile("^\\s*- '(ban|mute|kick|unban|unbanip)'\\s*$");

    static {
        ALIASES.put("ban", "ban");
        ALIASES.put("tempban", "ban");
        ALIASES.put("mute", "mute");
        ALIASES.put("tempmute", "mute");
        ALIASES.put("kick", "kick");
        ALIASES.put("unban", "unban");
        ALIASES.put("unbanip", "unbanip");
    }

    private LibertyBansAliases() {
    }

    /** A LibertyBans config.yml: ütköző aliasok ki, magyar nyelv be. Az onLoad-ban fut, a LibertyBans indulása előtt. */
    public static void patchConfig(File pluginsFolder, Logger logger) {
        File file = new File(new File(pluginsFolder, "LibertyBans"), "config.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String changed = patch(text);
            if (!changed.equals(text)) {
                Files.writeString(file.toPath(), changed, StandardCharsets.UTF_8);
                logger.info("LibertyBans: magyar nyelv és parancsok beállítva.");
            }
        } catch (IOException exception) {
            logger.warning("Nem tudtam átírni a LibertyBans configot: " + exception.getMessage());
        }
    }

    static String patch(String text) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            if (CONFLICT.matcher(line).matches()) {
                continue;
            }
            out.add(line.equals("lang-file: 'en'") ? "lang-file: 'hu'" : line);
        }
        return String.join(newline, out);
    }

    /** A parancsok átirányítása. A szerver teljes betöltése után fut, hogy az Essentials ne írja felül. */
    public static void register() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LibertyBans")) {
            return;
        }
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            Forward command = new Forward(entry.getKey(), entry.getValue());
            known.put(entry.getKey(), command);
            known.put("icemine:" + entry.getKey(), command);
        }
    }

    static String permission(String sub) {
        return switch (sub) {
            case "unban" -> "libertybans.ban.undo.target.uuid";
            case "unbanip" -> "libertybans.ban.undo.target.ip";
            default -> "libertybans." + sub + ".do.target.uuid";
        };
    }

    static final class Forward extends Command {

        private final String sub;

        Forward(String name, String sub) {
            super(name);
            this.sub = sub;
            setPermission(permission(sub));
            setDescription("LibertyBans: /libertybans " + sub);
            setUsage("/" + name + " <játékos> " + (sub.startsWith("un") ? "" : "[idő] ") + "[indok]");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return Bukkit.dispatchCommand(sender, "libertybans " + sub + (args.length == 0 ? "" : " " + String.join(" ", args)));
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            Command base = Bukkit.getCommandMap().getCommand("libertybans");
            if (base == null) {
                return List.of();
            }
            String[] full = new String[args.length + 1];
            full[0] = sub;
            System.arraycopy(args, 0, full, 1, args.length);
            try {
                return base.tabComplete(sender, "libertybans", full);
            } catch (RuntimeException error) {
                return List.of();
            }
        }
    }

    static List<String> labels() {
        return new ArrayList<>(Arrays.asList(ALIASES.keySet().toArray(new String[0])));
    }
}
