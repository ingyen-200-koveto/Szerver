package hu.icemine.core.economy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * A weboldal kulcsa (MC_API_KEY). A config.yml mellett a plugins/IceMine/kulcs.txt fájlban is lehet,
 * és ha a config.yml-ben van, ide is elmentjük. Így akkor sem vész el, ha a config.yml újraíródik.
 * A kulcs.txt nincs a GitHub repóban (a .gitignore kizárja), a Git Pull nem nyúl hozzá.
 */
public final class ApiKulcs {

    public static final String FILE = "kulcs.txt";

    private ApiKulcs() {
    }

    public static String resolve(File dataFolder, String fromConfig, Logger logger) {
        File file = new File(dataFolder, FILE);
        String key = fromConfig == null ? "" : fromConfig.trim();
        if (!key.isEmpty()) {
            try {
                String saved = file.exists() ? read(file) : "";
                if (!key.equals(saved)) {
                    Files.writeString(file.toPath(), key + System.lineSeparator(), StandardCharsets.UTF_8);
                }
            } catch (IOException exception) {
                logger.warning("Nem sikerült elmenteni a kulcsot a " + FILE + " fájlba: " + exception.getMessage());
            }
            return key;
        }
        if (!file.exists()) {
            return "";
        }
        try {
            return read(file);
        } catch (IOException exception) {
            logger.warning("Nem tudom olvasni: " + FILE + " (" + exception.getMessage() + ")");
            return "";
        }
    }

    private static String read(File file) throws IOException {
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                return t;
            }
        }
        return "";
    }
}
