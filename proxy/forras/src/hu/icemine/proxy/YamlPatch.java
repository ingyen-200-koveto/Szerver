package hu.icemine.proxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YamlPatch {

    private static final Pattern KEY = Pattern.compile("^( *)([A-Za-z0-9_.-]+):(.*)$");

    private YamlPatch() {
    }

    static String set(String text, String path, String value) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r?\\n", -1)));
        String parent = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
        String leaf = path.substring(path.lastIndexOf('.') + 1);
        int parentLine = -1;
        int parentIndent = -1;
        int childIndent = -1;
        Deque<int[]> indents = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            while (!indents.isEmpty() && indents.peekLast()[0] >= indent) {
                indents.removeLast();
                keys.removeLast();
            }
            String current = keys.isEmpty() ? m.group(2) : String.join(".", keys) + "." + m.group(2);
            if (parentLine >= 0 && childIndent < 0 && indent > parentIndent) {
                childIndent = indent;
            }
            if (current.equals(path)) {
                String old = m.group(3).strip();
                if (unquote(old).equals(unquote(value))) {
                    return text;
                }
                lines.set(i, m.group(1) + m.group(2) + ": " + value);
                return String.join(newline, lines);
            }
            if (current.equals(parent)) {
                parentLine = i;
                parentIndent = indent;
            }
            indents.addLast(new int[] {indent});
            keys.addLast(m.group(2));
        }
        if (parent.isEmpty()) {
            int at = lines.size();
            while (at > 0 && lines.get(at - 1).isEmpty()) {
                at--;
            }
            lines.add(at, leaf + ": " + value);
            return String.join(newline, lines);
        }
        if (parentLine < 0) {
            return text;
        }
        int indent = childIndent > parentIndent ? childIndent : parentIndent + 2;
        lines.add(parentLine + 1, " ".repeat(indent) + leaf + ": " + value);
        return String.join(newline, lines);
    }

    private static String unquote(String value) {
        String v = value.strip();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
