package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * A GrimAC szerint a ViaVersion fix-1_21-placement-rotation beállítása téves riasztásokat és kijátszást okoz,
 * ezért kikapcsoljuk. A ViaVersion a következő indítástól használja.
 */
public final class ViaFix {

    static final String KEY = "fix-1_21-placement-rotation:";

    private ViaFix() {
    }

    public static void run(File pluginsFolder, Logger logger) {
        File file = new File(new File(pluginsFolder, "ViaVersion"), "config.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String changed = patch(text);
            if (!changed.equals(text)) {
                Files.writeString(file.toPath(), changed, StandardCharsets.UTF_8);
                logger.info("ViaVersion: fix-1_21-placement-rotation kikapcsolva (a GrimAC miatt), a következő indítástól érvényes.");
            }
        } catch (IOException exception) {
            logger.warning("Nem tudtam átírni a ViaVersion configot: " + exception.getMessage());
        }
    }

    static String patch(String text) {
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith(KEY) && trimmed.substring(KEY.length()).strip().replace("\r", "").equals("true")) {
                String indent = line.substring(0, line.length() - trimmed.length());
                line = indent + KEY + " false" + (line.endsWith("\r") ? "\r" : "");
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
