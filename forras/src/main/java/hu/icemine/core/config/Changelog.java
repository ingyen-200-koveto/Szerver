package hu.icemine.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Changelog a Discordra: a jarban lévő changelog.yml nagyobb frissítései (x.y.0, vagy ahol nagy: true) a weboldalon
 * át a Discord hírek / frissítések csatornájába kerülnek, amikor a szerver először fut az új verzióval.
 * Ha közben több verzió kimaradt, egy üzenetben jön az összes. A már elküldött verziót a changelog.txt jegyzi meg.
 */
public final class Changelog {

    public record Entry(String version, String title, List<String> points) {
    }

    private Changelog() {
    }

    public static List<Entry> load(InputStream in) throws IOException {
        List<Entry> result = new ArrayList<>();
        if (in == null) {
            return result;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        try {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IOException("changelog.yml hibás: " + exception.getMessage());
        }
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null || compare(key, "0.0.0") <= 0) {
                continue;
            }
            boolean big = section.getBoolean("nagy", key.endsWith(".0"));
            if (!big) {
                continue;
            }
            result.add(new Entry(key, section.getString("cim", ""), section.getStringList("pontok")));
        }
        result.sort((a, b) -> compare(a.version, b.version));
        return result;
    }

    /** Mit kell most elküldeni: első futáskor csak a mostani (vagy az utolsó előtte lévő) nagy frissítést, utána ami azóta jött. */
    public static List<Entry> pending(List<Entry> all, String current, String last) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all) {
            if (compare(entry.version, current) > 0) {
                continue;
            }
            if (last == null || compare(entry.version, last) > 0) {
                result.add(entry);
            }
        }
        if (last == null && result.size() > 1) {
            return List.of(result.get(result.size() - 1));
        }
        return result;
    }

    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0;
            int q = i < y.length ? number(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    static String json(List<Entry> entries) {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (Entry entry : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("verzio", entry.version);
            o.addProperty("cim", entry.title);
            JsonArray points = new JsonArray();
            for (String point : entry.points) {
                points.add(point.replaceAll("&[0-9a-fk-or]", ""));
            }
            o.add("pontok", points);
            list.add(o);
        }
        root.add("bejegyzesek", list);
        return root.toString();
    }

    /** Elküldi, ami még nem ment ki. post: (útvonal, json, kész(státusz)). */
    public static void run(InputStream resource, Path store, String current, Logger logger, PostFn post) {
        try {
            List<Entry> all = load(resource);
            String last = Files.isRegularFile(store) ? Files.readString(store, StandardCharsets.UTF_8).trim() : null;
            if (last != null && last.isEmpty()) {
                last = null;
            }
            List<Entry> todo = pending(all, current, last);
            if (todo.isEmpty()) {
                if (last == null || compare(current, last) > 0) {
                    Files.writeString(store, current, StandardCharsets.UTF_8);
                }
                return;
            }
            post.post("/api/mc/changelog", json(todo), status -> {
                if (status == 200) {
                    try {
                        Files.writeString(store, current, StandardCharsets.UTF_8);
                    } catch (IOException ignored) {
                        // következő indításkor újra próbálja, a weboldal úgyis kiszűri a duplát
                    }
                    logger.info("Changelog elküldve a Discordra: " + todo.get(todo.size() - 1).version());
                }
            });
        } catch (IOException exception) {
            logger.warning("Changelog: " + exception.getMessage());
        }
    }

    @FunctionalInterface
    public interface PostFn {
        void post(String path, String json, IntConsumer done);
    }
}
