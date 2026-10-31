package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A többi plugin (AuthMe, EssentialsX) üzeneteit is IceMine prefixszel látja a játékos. A jarban lévő kulso/ mappából a
 * megfelelő helyre másoljuk a magyar üzenetfájlokat, mielőtt azok a pluginok elindulnának (onLoad). Mindegyik fájlt csak
 * egyszer írjuk ki változatonként; ha valaki utána átírja, nem nyúlunk hozzá. A felülírt régi fájl .regi néven megmarad.
 */
public final class ForeignMessages {

    /** Ha egy kulso fájl tartalma változik, itt kell növelni a számát, akkor újra kikerül. */
    static final String[][] FILES = {
            {"AuthMe/messages/messages_hu.yml", "1"},
            {"Essentials/messages/messages_hu.properties", "1"},
    };
    static final String ESSENTIALS_CONFIG = "essentials-config:1";
    private static final String DONE_FILE = ".kulso-uzenetek";

    private ForeignMessages() {
    }

    /**
     * @param pluginsFolder a szerver plugins mappája
     * @param dataFolderName az IceMine adatmappa neve (általában IceMine)
     */
    public static List<String> run(File pluginsFolder, String dataFolderName, Function<String, InputStream> resources, Logger logger) {
        List<String> notes = new ArrayList<>();
        Path plugins = pluginsFolder.toPath();
        Path doneFile = plugins.resolve(dataFolderName).resolve(DONE_FILE);
        if (!enabled(plugins.resolve(dataFolderName).resolve("config.yml"))) {
            return notes;
        }
        Set<String> done = read(doneFile);
        boolean changed = false;
        for (String[] entry : FILES) {
            String mark = entry[0] + ":" + entry[1];
            if (done.contains(mark)) {
                continue;
            }
            String owner = entry[0].substring(0, entry[0].indexOf('/'));
            if (!Files.isDirectory(plugins.resolve(owner)) && !jarPresent(plugins, owner)) {
                continue;
            }
            try (InputStream in = resources.apply("kulso/" + entry[0])) {
                if (in == null) {
                    continue;
                }
                Path target = plugins.resolve(entry[0]);
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) {
                    Path backup = target.resolveSibling(target.getFileName() + ".regi");
                    if (!Files.exists(backup)) {
                        Files.copy(target, backup);
                    }
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                done.add(mark);
                changed = true;
                notes.add(entry[0]);
            } catch (IOException exception) {
                logger.warning("Nem sikerült kiírni: plugins/" + entry[0] + " (" + exception.getMessage() + ")");
            }
        }
        if (!done.contains(ESSENTIALS_CONFIG)) {
            Path config = plugins.resolve("Essentials").resolve("config.yml");
            if (Files.isRegularFile(config)) {
                try {
                    String text = Files.readString(config, StandardCharsets.UTF_8);
                    String patched = patchEssentials(text);
                    if (!patched.equals(text)) {
                        Files.writeString(config, patched, StandardCharsets.UTF_8);
                        notes.add("Essentials/config.yml (magyar nyelv, IceMine színek)");
                    }
                    done.add(ESSENTIALS_CONFIG);
                    changed = true;
                } catch (IOException exception) {
                    logger.warning("Nem sikerült átírni: plugins/Essentials/config.yml (" + exception.getMessage() + ")");
                }
            }
        }
        if (changed) {
            write(doneFile, done);
        }
        for (String note : notes) {
            logger.info("IceMine üzenetek beállítva: plugins/" + note);
        }
        return notes;
    }

    private static boolean jarPresent(Path plugins, String owner) {
        String lower = owner.toLowerCase();
        try (var files = Files.list(plugins)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".jar") && (name.startsWith(lower) || (lower.equals("essentials") && name.startsWith("essentialsx")));
            });
        } catch (IOException exception) {
            return false;
        }
    }

    /** A config.yml "kulso-uzenetek: false" sorával ki lehet kapcsolni. A configot itt még kézzel olvassuk (onLoad). */
    static boolean enabled(Path config) {
        if (!Files.isRegularFile(config)) {
            return true;
        }
        try {
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("kulso-uzenetek:")) {
                    return !trimmed.substring("kulso-uzenetek:".length()).trim().toLowerCase().startsWith("false");
                }
            }
        } catch (IOException ignored) {
            // olvashatatlan config: maradunk az alapnál
        }
        return true;
    }

    private static final Pattern LOCALE = Pattern.compile("(?m)^locale:.*$");
    private static final Pattern COMMENTED_LOCALE = Pattern.compile("(?m)^#\\s?locale:.*$");
    private static final Pattern PRIMARY = Pattern.compile("(?m)^(\\s+primary:\\s*)'#ffaa00'\\s*$");
    private static final Pattern SECONDARY = Pattern.compile("(?m)^(\\s+secondary:\\s*)'#ff5555'\\s*$");

    /** Magyar nyelv, és az alap narancs/piros helyett IceMine színek (szürke szöveg, fehér kiemelés). Csak az alapértékeket írja át. */
    static String patchEssentials(String text) {
        String result = text;
        Matcher locale = LOCALE.matcher(result);
        if (locale.find()) {
            String current = locale.group().substring("locale:".length()).trim().replace("'", "").replace("\"", "");
            if (current.isEmpty() || current.equals("en")) {
                result = locale.replaceFirst("locale: hu");
            }
        } else {
            Matcher commented = COMMENTED_LOCALE.matcher(result);
            if (commented.find()) {
                result = commented.replaceFirst("locale: hu");
            } else {
                result = result + (result.endsWith("\n") ? "" : "\n") + "locale: hu\n";
            }
        }
        result = PRIMARY.matcher(result).replaceFirst("$1'#aaaaaa'");
        result = SECONDARY.matcher(result).replaceFirst("$1'#ffffff'");
        return result;
    }

    private static Set<String> read(Path file) {
        Set<String> done = new LinkedHashSet<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        done.add(line.trim());
                    }
                }
            } catch (IOException ignored) {
                // újra lefut, de a .regi mentés miatt nem veszik el semmi
            }
        }
        return done;
    }

    private static void write(Path file, Set<String> done) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, done, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // következő indításkor újra próbáljuk
        }
    }
}
