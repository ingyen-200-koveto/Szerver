package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** A YAML-ban az idézőjel nélküli on/off/yes/no kulcs logikai értékké válik, és az üzenet "hiányzik". */
class YamlKeysTest {

    private static final Pattern BAD = Pattern.compile("^\\s*(on|off|yes|no|On|Off|Yes|No|ON|OFF|YES|NO):");

    @Test
    void noBooleanKeys() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (BAD.matcher(lines.get(i)).find()) {
                        bad.add(file.getFileName() + ":" + (i + 1) + " " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "Idézőjel nélküli on/off kulcs: " + bad);
    }

    /** A régi (élő) fájlban true/false kulcsok vannak: az alapértékből akkor is meg kell lennie. */
    @Test
    void oldLiveFileFallsBackToDefaults() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration defaults = new org.bukkit.configuration.file.YamlConfiguration();
        defaults.load(new java.io.File("src/main/resources/messages_hu.yml"));
        org.bukkit.configuration.file.YamlConfiguration live = new org.bukkit.configuration.file.YamlConfiguration();
        live.loadFromString("gens:\n  lock:\n    on: a\n    off: b\n  autosell:\n    on: c\n    off: d\nsettings:\n  on: e\n  off: f\n");
        live.setDefaults(defaults);
        for (String key : List.of("gens.lock.on", "gens.lock.off", "gens.autosell.on", "gens.autosell.off", "settings.on", "settings.off")) {
            assertTrue(live.getString(key) != null, key);
        }
    }
}
