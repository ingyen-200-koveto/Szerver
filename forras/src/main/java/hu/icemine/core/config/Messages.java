package hu.icemine.core.config;

import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class Messages {

    /** Ez a prefix kerül minden chat üzenet elé, ha a messages_hu.yml-ben nincs megadva más. */
    public static final String DEFAULT_PREFIX = "&b[IceMine] &7";
    /** A prefix felismerhető része: ha egy szöveg már tartalmazza, nem tesszük elé még egyszer. */
    public static final String TAG = "[IceMine]";

    private final ConfigFiles files;
    private final Logger logger;
    private final Set<String> missing = new HashSet<>();

    public Messages(ConfigFiles files, Logger logger) {
        this.files = files;
        this.logger = logger;
    }

    private YamlConfiguration config() {
        return files.get("messages_hu");
    }

    public String raw(String key) {
        String value = config().getString(key);
        if (value == null) {
            if (missing.add(key)) {
                logger.warning("Hiányzó üzenet a messages_hu.yml-ben: " + key);
            }
            return "&c[" + key + "]";
        }
        return value;
    }

    public String get(String key, String... pairs) {
        return Text.color(Text.replace(raw(key), pairs));
    }

    public String prefix() {
        YamlConfiguration config = config();
        return config == null ? DEFAULT_PREFIX : config.getString("prefix", DEFAULT_PREFIX);
    }

    public String prefixed(String key, String... pairs) {
        return Text.color(prefix() + Text.replace(raw(key), pairs));
    }

    /**
     * Prefixet tesz egy kész szöveg elé, kivéve, ha már benne van. Ezzel mennek ki a configból jövő szabad szövegek is
     * (pl. NPC vagy hotbar "message" akciók), így a játékos sosem lát prefix nélküli üzenetet.
     */
    public String tagged(String text) {
        String value = text == null ? "" : text;
        if (hasTag(value)) {
            return Text.color(value);
        }
        return Text.color(prefix() + value);
    }

    static boolean hasTag(String text) {
        return Text.strip(Text.color(text)).contains(TAG);
    }

    public void sendTagged(CommandSender sender, String text) {
        if (sender != null && text != null && !text.isBlank()) {
            sender.sendMessage(tagged(text));
        }
    }

    public List<String> list(String key, String... pairs) {
        List<String> lines = config().getStringList(key);
        if (lines.isEmpty() && !config().contains(key)) {
            if (missing.add(key)) {
                logger.warning("Hiányzó üzenet a messages_hu.yml-ben: " + key);
            }
        }
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(Text.color(Text.replace(line, pairs)));
        }
        return result;
    }

    public void send(CommandSender sender, String key, String... pairs) {
        if (sender != null) {
            sender.sendMessage(prefixed(key, pairs));
        }
    }

    public void sendList(CommandSender sender, String key, String... pairs) {
        if (sender == null) {
            return;
        }
        for (String line : withHeader(list(key, pairs), Text.color(prefix()))) {
            sender.sendMessage(line);
        }
    }

    /**
     * Több soros blokkoknál (súgó, statisztika) az első nem üres sor kapja a prefixet, ha a blokkban sehol nincs benne.
     * Így a blokk egy üzenetként néz ki, de a játékos mindig látja, hogy az IceMine írja.
     */
    public static List<String> withHeader(List<String> lines, String prefix) {
        for (String line : lines) {
            if (hasTag(line)) {
                return lines;
            }
        }
        List<String> result = new ArrayList<>(lines);
        for (int i = 0; i < result.size(); i++) {
            String line = result.get(i);
            if (!Text.strip(Text.color(line)).isBlank()) {
                result.set(i, prefix + line.stripLeading());
                break;
            }
        }
        return result;
    }

    public void actionBar(Player player, String key, String... pairs) {
        player.sendActionBar(get(key, pairs));
    }
}
