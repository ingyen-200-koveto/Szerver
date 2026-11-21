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

    /**
     * Melyik sorban ér véget az érték: a több soros szöveg (\"\"\" vagy \'\'\') és a több soros tömb is számít.
     * -1, ha nem találjuk a végét (ilyenkor inkább nem nyúlunk a fájlhoz).
     */
    static int valueEnd(List<String> lines, int start, String value) {
        String v = value.strip();
        for (String quote : new String[] {"\"\"\"", "'''"}) {
            if (v.startsWith(quote)) {
                if (v.indexOf(quote, 3) >= 0) {
                    return start;
                }
                for (int j = start + 1; j < lines.size(); j++) {
                    if (lines.get(j).contains(quote)) {
                        return j;
                    }
                }
                return -1;
            }
        }
        if (v.startsWith("[")) {
            int depth = 0;
            for (int j = start; j < lines.size(); j++) {
                String part = j == start ? v : lines.get(j);
                boolean inString = false;
                char q = 0;
                for (int k = 0; k < part.length(); k++) {
                    char c = part.charAt(k);
                    if (inString) {
                        if (c == '\\' && q == '"') {
                            k++;
                        } else if (c == q) {
                            inString = false;
                        }
                    } else if (c == '"' || c == '\'') {
                        inString = true;
                        q = c;
                    } else if (c == '#') {
                        break;
                    } else if (c == '[') {
                        depth++;
                    } else if (c == ']') {
                        depth--;
                    }
                }
                if (depth <= 0) {
                    return j;
                }
            }
            return -1;
        }
        return start;
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
                int end = valueEnd(lines, i, m.group(4));
                if (end < 0) {
                    return text;
                }
                if (end == i && m.group(4).strip().equals(value.strip())) {
                    return text;
                }
                for (int j = end; j > i; j--) {
                    lines.remove(j);
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
