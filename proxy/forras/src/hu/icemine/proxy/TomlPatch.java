package hu.icemine.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Egy kulcs átírása egy TOML fájlban (pl. velocity.toml). A "szakasz.kulcs" a [szakasz] alatti kulcs. */
final class TomlPatch {

    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*(#.*)?$");
    private static final Pattern KEY = Pattern.compile("^(\\s*)([A-Za-z0-9_\\-]+)(\\s*=\\s*)(.*)$");

    private TomlPatch() {
    }

    static String set(String text, String path, String value) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r?\\n", -1)));
        int dot = path.lastIndexOf('.');
        String section = dot < 0 ? "" : path.substring(0, dot);
        String key = path.substring(dot + 1);
        String current = "";
        int sectionEnd = section.isEmpty() ? -1 : -2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher s = SECTION.matcher(line);
            if (s.matches()) {
                if (current.equals(section) && sectionEnd == -1) {
                    sectionEnd = i;
                }
                current = s.group(1).strip();
                if (current.equals(section)) {
                    sectionEnd = -1;
                }
                continue;
            }
            if (!current.equals(section)) {
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (m.matches() && m.group(2).equals(key)) {
                if (m.group(4).strip().equals(value.strip())) {
                    return text;
                }
                lines.set(i, m.group(1) + key + m.group(3) + value);
                return String.join(newline, lines);
            }
        }
        if (sectionEnd == -2) {
            return text;
        }
        int at = sectionEnd >= 0 ? sectionEnd : lines.size();
        if (section.isEmpty()) {
            at = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (SECTION.matcher(lines.get(i)).matches()) {
                    break;
                }
                if (!lines.get(i).isBlank()) {
                    at = i + 1;
                }
            }
        } else {
            while (at > 0 && lines.get(at - 1).isBlank()) {
                at--;
            }
        }
        lines.add(at, key + " = " + value);
        return String.join(newline, lines);
    }
}
