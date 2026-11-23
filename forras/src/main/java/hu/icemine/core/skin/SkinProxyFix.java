package hu.icemine.core.skin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A SkinsRestorer csak ezen a szerveren fut, a proxyn nincs. Ha a Paper Velocity módban van, a SkinsRestorer
 * magától "proxy módba" kapcsol, és akkor semmilyen skint nem tesz fel (a proxytól várná). Ezért induláskor,
 * még a SkinsRestorer betöltése előtt, a beállításában kikapcsoljuk a proxy módot (detection: DISABLED).
 * config.yml -> skins.backend-only: false esetén nem nyúlunk hozzá (ha a proxyra is felkerül a SkinsRestorer).
 */
public final class SkinProxyFix {

    private static final Pattern DETECTION = Pattern.compile("(proxyMode:\\s*\\n(?:[ \\t]*#[^\\n]*\\n)*[ \\t]*detection:)[ \\t]*(AUTO|ENABLED)");

    private SkinProxyFix() {
    }

    public static void apply(File pluginsFolder, Logger logger) {
        File[] jars = pluginsFolder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).startsWith("skinsrestorer") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return;
        }
        File config = new File(pluginsFolder, "SkinsRestorer/config.yml");
        try {
            if (!config.exists()) {
                Files.createDirectories(config.getParentFile().toPath());
                Files.writeString(config.toPath(), "server:\n    proxyMode:\n        detection: DISABLED\n        api: true\n", StandardCharsets.UTF_8);
                logger.info("SkinsRestorer: proxy mód kikapcsolva, hogy a weboldalas skinek működjenek.");
                return;
            }
            String text = Files.readString(config.toPath(), StandardCharsets.UTF_8);
            Matcher m = DETECTION.matcher(text);
            if (m.find()) {
                Files.writeString(config.toPath(), m.replaceFirst("$1 DISABLED"), StandardCharsets.UTF_8);
                logger.info("SkinsRestorer: proxy mód kikapcsolva (" + m.group(2) + " -> DISABLED), hogy a skinek működjenek.");
            }
        } catch (Exception exception) {
            logger.warning("SkinsRestorer beállítás javítása nem sikerült: " + exception.getMessage());
        }
    }
}
