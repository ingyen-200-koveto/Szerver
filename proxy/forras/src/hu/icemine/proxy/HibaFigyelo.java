package hu.icemine.proxy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * A proxy figyelmeztetései és hibái (Velocity, Geyser, Sonar, minden plugin) a weboldalon át a Discordra. Ugyanaz a hiba 30 percen belül csak egyszer megy ki, a többi csak
 * számolódik.
 */
public final class HibaFigyelo {

    static final String NAME = "IceMineProxyHibaFigyelo";
    static final long ISMETLES = 30L * 60 * 1000;
    static final int MAX_TETEL = 500;
    static final List<String> ALAP_KIHAGY = List.of(
            "/api/mc/hibak", "Connection reset by peer", "ReadTimeoutException", "read timed out", "has disconnected",
            "lost connection", "QuietDecoderException", "CorruptedFrameException", "DecoderException",
            "Advanced terminal features", "An exception occurred in this connection", "Broken pipe",
            "OFFLINE/INSECURE MODE", "*****");

    static final class Tetel {
        final String szint;
        final String logger;
        final String uzenet;
        final String verem;
        final long elso;
        int uj;
        long kuldve;

        Tetel(String szint, String logger, String uzenet, String verem, long elso) {
            this.szint = szint;
            this.logger = logger;
            this.uzenet = uzenet;
            this.verem = verem;
            this.elso = elso;
        }
    }

    private static final Map<String, Tetel> TETELEK = new ConcurrentHashMap<>();
    private static volatile List<String> kihagy = ALAP_KIHAGY;
    private static volatile boolean csakHibak;
    private static Appender appender;

    private HibaFigyelo() {
    }

    public static synchronized void start(List<String> extraKihagy, boolean onlyErrors) {
        List<String> list = new ArrayList<>(ALAP_KIHAGY);
        if (extraKihagy != null) {
            for (String s : extraKihagy) {
                if (s != null && !s.isBlank()) {
                    list.add(s);
                }
            }
        }
        kihagy = List.copyOf(list);
        csakHibak = onlyErrors;
        if (appender != null) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Appender a = new Appender();
        a.start();
        context.getConfiguration().addAppender(a);
        context.getConfiguration().getRootLogger().addAppender(a, Level.WARN, null);
        context.updateLoggers();
        appender = a;
    }

    public static synchronized void stop() {
        if (appender == null) {
            return;
        }
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().removeAppender(NAME);
            context.updateLoggers();
        } catch (Throwable ignored) {
        }
        appender.stop();
        appender = null;
    }

    static boolean kihagyando(String text) {
        for (String s : kihagy) {
            if (text.contains(s)) {
                return true;
            }
        }
        return false;
    }

    static String kulcs(String logger, String uzenet) {
        String norm = uzenet.replaceAll("\\d+", "#");
        return logger + "|" + (norm.length() > 200 ? norm.substring(0, 200) : norm);
    }

    static void rogzit(String szint, String logger, String uzenet, String verem, long most) {
        if (uzenet == null || uzenet.isBlank() || kihagyando(uzenet) || (verem != null && kihagyando(verem))) {
            return;
        }
        if (csakHibak && !szint.equals("ERROR")) {
            return;
        }
        String k = kulcs(logger, uzenet);
        Tetel t = TETELEK.get(k);
        if (t == null) {
            if (TETELEK.size() >= MAX_TETEL) {
                return;
            }
            t = TETELEK.computeIfAbsent(k, x -> new Tetel(szint, logger, vag(uzenet, 900), verem == null ? "" : vag(verem, 900), most));
        }
        synchronized (t) {
            t.uj++;
        }
    }

    /** A kiküldendő tételek JSON-ja (null, ha nincs mit küldeni). Utána ezek "elküldöttnek" számítanak. */
    static String kovetkezo(String forras, long most, int max) {
        List<String> items = new ArrayList<>();
        Iterator<Map.Entry<String, Tetel>> it = TETELEK.entrySet().iterator();
        while (it.hasNext() && items.size() < max) {
            Tetel t = it.next().getValue();
            synchronized (t) {
                if (t.uj == 0) {
                    if (t.kuldve > 0 && most - t.kuldve > 6 * ISMETLES) {
                        it.remove();
                    }
                    continue;
                }
                if (t.kuldve > 0 && most - t.kuldve < ISMETLES) {
                    continue;
                }
                items.add("{\"szint\":\"" + t.szint + "\",\"logger\":" + q(t.logger) + ",\"uzenet\":" + q(t.uzenet) + ",\"verem\":" + q(t.verem)
                        + ",\"db\":" + t.uj + ",\"elso\":" + (t.kuldve > 0 ? t.kuldve : t.elso) + "}");
                t.uj = 0;
                t.kuldve = most;
            }
        }
        return items.isEmpty() ? null : "{\"forras\":" + q(forras) + ",\"hibak\":[" + String.join(",", items) + "]}";
    }

    static void torol() {
        TETELEK.clear();
    }

    static String verem(Throwable thrown) {
        if (thrown == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Throwable t = thrown;
        int depth = 0;
        while (t != null && depth < 3) {
            if (depth > 0) {
                sb.append("Caused by: ");
            }
            sb.append(t.getClass().getName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
            }
            sb.append('\n');
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(depth == 0 ? 4 : 2, st.length); i++) {
                sb.append("  at ").append(st[i]).append('\n');
            }
            t = t.getCause() == t ? null : t.getCause();
            depth++;
        }
        return sb.toString().trim();
    }

    static String rovidLogger(String name) {
        if (name == null || name.isEmpty()) {
            return "Proxy";
        }
        if (name.startsWith("com.velocitypowered")) {
            return "Velocity";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.length() > 30 ? name.substring(dot + 1) : name;
    }

    static String vag(String text, int n) {
        return text.length() > n ? text.substring(0, n) + "…" : text;
    }

    static String q(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                case '\t' -> sb.append("  ");
                default -> {
                    if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Időzítőből hívandó: elküldi, ami összegyűlt. */
    public static void kuld(String forras, Consumer<String> post) {
        String json = kovetkezo(forras, System.currentTimeMillis(), 10);
        if (json != null) {
            post.accept(json);
        }
    }

    static final class Appender extends AbstractAppender {

        Appender() {
            super(NAME, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            try {
                Level level = event.getLevel();
                if (level == null || !level.isMoreSpecificThan(Level.WARN)) {
                    return;
                }
                String msg = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
                msg = msg.replaceAll("\u001B\\[[;\\d]*m", "").replaceAll("§.", "").trim();
                String szint = level.isMoreSpecificThan(Level.ERROR) ? "ERROR" : "WARN";
                rogzit(szint, rovidLogger(event.getLoggerName()), msg, verem(event.getThrown()), System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
        }
    }

    static String szintNev(String level) {
        return level.toUpperCase(Locale.ROOT);
    }
}
