package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Verziófrissítés a configokban. A jarban lévő frissites/&lt;verzió&gt;.yml felsorolja, melyik alapérték változott az adott
 * verzióban, és mi volt a régi. Ha a szerveren még a régi alapérték van, kicseréljük az újra; ha valaki átírta, nem nyúlunk
 * hozzá. Minden (verzió, fájl) párost csak egyszer futtatunk le, ezt a .frissitesek fájl jegyzi.
 */
public final class Upgrades {

    /** A kiadott verziók, amelyekhez van frissítési lista, régebbitől az újabb felé. */
    public static final String[] VERSIONS = {"3.2.0", "3.3.0", "3.4.0", "3.5.0", "3.6.0", "3.7.0", "3.8.0", "3.8.1", "3.8.2", "3.9.0", "3.9.1", "3.10.0", "3.10.1", "3.11.0", "3.12.0", "3.12.1", "3.13.0"};
    private static final String DONE_FILE = ".frissitesek";

    public interface Target {
        YamlConfiguration config(String file);

        void save(String file);
    }

    private final File dataFolder;
    private final Function<String, InputStream> resources;

    public Upgrades(File dataFolder, Function<String, InputStream> resources) {
        this.dataFolder = dataFolder;
        this.resources = resources;
    }

    /** Lefuttatja a még nem alkalmazott frissítéseket a megadott fájlokra. Visszaadja, mi változott (a naplóba). */
    public List<String> apply(Set<String> fileNames, Target target) {
        List<String> notes = new ArrayList<>();
        Set<String> done = readDone();
        boolean changedDone = false;
        for (String version : VERSIONS) {
            List<Map<?, ?>> changes = changes(version);
            if (changes == null) {
                continue;
            }
            for (String file : fileNames) {
                String mark = version + ":" + file;
                if (done.contains(mark)) {
                    continue;
                }
                YamlConfiguration config = target.config(file);
                if (config == null) {
                    continue;
                }
                int count = applyTo(config, file, changes);
                if (count > 0) {
                    target.save(file);
                    notes.add(file + ".yml: " + count + " alapérték frissítve (" + version + ")");
                }
                done.add(mark);
                changedDone = true;
            }
        }
        if (changedDone) {
            writeDone(done);
        }
        return notes;
    }

    /** A tényleges csere egy configon. Csak akkor ír, ha a jelenlegi érték pontosan a régi alapérték. */
    static int applyTo(YamlConfiguration config, String file, List<Map<?, ?>> changes) {
        Configuration defaults = config.getDefaults();
        int count = 0;
        for (Map<?, ?> change : changes) {
            if (!file.equals(String.valueOf(change.get("file")))) {
                continue;
            }
            String path = String.valueOf(change.get("path"));
            if (!config.isSet(path) || !same(config.get(path), change.get("old"))) {
                continue;
            }
            Object fresh = defaults == null ? null : defaults.get(path);
            config.set(path, fresh);
            count++;
        }
        return count;
    }

    static boolean same(Object current, Object old) {
        return normalize(current).equals(normalize(old));
    }

    private static Object normalize(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return String.valueOf(value);
    }

    private List<Map<?, ?>> changes(String version) {
        try (InputStream in = resources.apply("frissites/" + version + ".yml")) {
            if (in == null) {
                return null;
            }
            YamlConfiguration list = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            return list.getMapList("changes");
        } catch (IOException exception) {
            return null;
        }
    }

    private Set<String> readDone() {
        File file = new File(dataFolder, DONE_FILE);
        Set<String> done = new LinkedHashSet<>();
        if (file.isFile()) {
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        done.add(line.trim());
                    }
                }
            } catch (IOException ignored) {
                // ha nem olvasható, legfeljebb újra lefut; a csere magától is csak egyszer hat
            }
        }
        return done;
    }

    private void writeDone(Set<String> done) {
        try {
            dataFolder.mkdirs();
            Files.write(new File(dataFolder, DONE_FILE).toPath(), done, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // nem gond: a következő indításkor újra megpróbáljuk
        }
    }
}
