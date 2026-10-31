package hu.icemine.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private Text() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(hex.toString()));
        }
        matcher.appendTail(builder);
        char[] chars = builder.toString().toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static List<String> color(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(color(line));
        }
        return result;
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < input.length() && CODES.indexOf(input.charAt(i + 1)) > -1) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static String replace(String text, String... pairs) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return result;
    }

    public static String number(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            out.append(digits.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                out.append(' ');
            }
        }
        if (value < 0) {
            out.append('-');
        }
        return out.reverse().toString();
    }

    public static String timer(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long minutes = millis / 60000;
        long seconds = (millis / 1000) % 60;
        long rest = millis % 1000;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, rest);
    }

    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60000;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return days + " nap " + (hours % 24) + " óra";
        }
        if (hours > 0) {
            return hours + " óra " + (minutes % 60) + " perc";
        }
        return minutes + " perc";
    }

    /** Szorzók kiírása: 1.0 -> "1", 1.25 -> "1,25". */
    public static String decimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.replace('.', ',');
    }

    public static String ratio(long kills, long deaths) {
        double value = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String bar(double progress, int length, String full, String empty) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * length);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            out.append(i < filled ? full : empty);
        }
        return out.toString();
    }
}
