package hu.icemine.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ClickText {

    public static final class Part {
        public final String text;
        public final String kind;
        public final String value;

        Part(String text, String kind, String value) {
            this.text = text;
            this.kind = kind;
            this.value = value;
        }
    }

    private static final Pattern MARKUP = Pattern.compile("\\[\\[(.+?)\\|(run|suggest|url|copy):(.+?)]]");
    private static final Pattern COMMAND = Pattern.compile("(/[a-zA-Z][a-zA-Z0-9_]*)");
    private static final Pattern LINK = Pattern.compile("(https?://[^\\s&]+)");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ClickText() {
    }

    public static List<Part> split(String line) {
        List<Part> parts = new ArrayList<>();
        Matcher matcher = MARKUP.matcher(line);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                plain(line.substring(last, matcher.start()), parts);
            }
            parts.add(new Part(matcher.group(1), matcher.group(2).toLowerCase(Locale.ROOT), matcher.group(3).trim()));
            last = matcher.end();
        }
        if (last < line.length()) {
            plain(line.substring(last), parts);
        }
        return parts;
    }

    private static void plain(String text, List<Part> parts) {
        Matcher matcher = LINK.matcher(text);
        int last = 0;
        List<Part> found = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.start() > last) {
                commands(text.substring(last, matcher.start()), found);
            }
            found.add(new Part(matcher.group(1), "url", matcher.group(1)));
            last = matcher.end();
        }
        if (last < text.length()) {
            commands(text.substring(last), found);
        }
        parts.addAll(found);
    }

    private static void commands(String text, List<Part> parts) {
        Matcher matcher = COMMAND.matcher(text);
        int last = 0;
        while (matcher.find()) {
            int start = matcher.start(1);
            char before = start == 0 ? ' ' : text.charAt(start - 1);
            boolean colorCode = start >= 2 && text.charAt(start - 2) == '&';
            if (!colorCode && (Character.isLetterOrDigit(before) || before == '/' || before == ':' || before == '.')) {
                continue;
            }
            if (start > last) {
                parts.add(new Part(text.substring(last, start), "", ""));
            }
            parts.add(new Part(matcher.group(1), "run", matcher.group(1)));
            last = matcher.end(1);
        }
        if (last < text.length()) {
            parts.add(new Part(text.substring(last), "", ""));
        }
    }

    public static Component line(String line, String hoverRun, String hoverUrl) {
        TextComponent.Builder builder = Component.text();
        String carry = "";
        for (Part part : split(line)) {
            String colored = carry + part.text;
            carry = lastColors(colored);
            Component piece = LEGACY.deserialize(colored);
            switch (part.kind) {
                case "run":
                    piece = piece.clickEvent(ClickEvent.runCommand(part.value))
                            .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverRun.replace("%value%", part.value))));
                    break;
                case "suggest":
                    piece = piece.clickEvent(ClickEvent.suggestCommand(part.value))
                            .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverRun.replace("%value%", part.value))));
                    break;
                case "url":
                    piece = piece.clickEvent(ClickEvent.openUrl(part.value))
                            .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverUrl.replace("%value%", part.value))));
                    break;
                case "copy":
                    piece = piece.clickEvent(ClickEvent.copyToClipboard(part.value))
                            .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverUrl.replace("%value%", part.value))));
                    break;
                default:
                    break;
            }
            builder.append(piece);
        }
        return builder.build();
    }

    static String lastColors(String text) {
        String color = "";
        String format = "";
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '&') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                color = "&" + code;
                format = "";
            } else if (code == 'r') {
                color = "";
                format = "";
            } else if ("klmno".indexOf(code) >= 0) {
                format += "&" + code;
            }
        }
        return color + format;
    }
}
