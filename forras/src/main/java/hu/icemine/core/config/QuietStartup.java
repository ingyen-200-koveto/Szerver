package hu.icemine.core.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.apache.logging.log4j.message.Message;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Csendes indítás: amíg a szerver elindul, a konzolon csak a figyelmeztetések, a hibák és a "Done" sor látszik.
 * A logs/latest.log fájlba ilyenkor is minden bekerül. Kikapcsolás: config.yml -> quiet-startup: false
 */
public final class QuietStartup {

    static final String KEEP = "✔";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicInteger HIDDEN = new AtomicInteger();
    private static final AtomicInteger WARNINGS = new AtomicInteger();
    private static final List<Runnable> UNDO = new ArrayList<>();

    private QuietStartup() {
    }

    public static void start(File dataFolder, java.util.logging.Logger logger) {
        try {
            File file = new File(dataFolder, "config.yml");
            if (file.isFile() && !YamlConfiguration.loadConfiguration(file).getBoolean("quiet-startup", true)) {
                return;
            }
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            java.util.List<Appender> consoles = new java.util.ArrayList<>();
            for (Appender appender : context.getConfiguration().getAppenders().values()) {
                if (console(appender.getClass().getSimpleName(), appender.getName())) {
                    consoles.add(appender);
                }
            }
            consoles.sort(java.util.Comparator.comparing(appender -> appender.getClass().getSimpleName().contains("Terminal") ? 0 : 1));
            for (Appender appender : consoles) {
                if (appender instanceof AbstractFilterable) {
                    Gate gate = new Gate(UNDO.isEmpty());
                    ((AbstractFilterable) appender).addFilter(gate);
                    UNDO.add(() -> ((AbstractFilterable) appender).removeFilter(gate));
                }
            }
            ACTIVE.set(!UNDO.isEmpty());
        } catch (Throwable error) {
            ACTIVE.set(false);
            logger.fine("Csendes indítás nem elérhető: " + error);
        }
    }

    /**
     * Csak a konzol appenderekre teszünk szűrőt (TerminalConsole, a GUI konzol), a fájlra és a köztes
     * Async/Rewrite appenderekre nem, különben a logs/latest.log-ból is kimaradnának a sorok.
     */
    static boolean console(String kind, String name) {
        String k = kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (k.contains("file") || n.contains("file") || k.contains("async") || k.contains("rewrite") || n.contains("async") || n.contains("rewrite")) {
            return false;
        }
        return k.contains("console") || k.contains("terminal") || n.contains("console") || n.contains("terminal");
    }

    /** A szerver elindult: innentől minden látszik. Visszaadja, hány sort rejtett el. */
    public static int finish() {
        if (!ACTIVE.getAndSet(false)) {
            return -1;
        }
        for (Runnable undo : UNDO) {
            try {
                undo.run();
            } catch (Throwable ignored) {
                // ha az appendert közben lecserélték, nincs mit visszaállítani
            }
        }
        UNDO.clear();
        return HIDDEN.get();
    }

    static void activateForTest() {
        ACTIVE.set(true);
    }

    public static int warnings() {
        return WARNINGS.get();
    }

    static boolean keep(String text) {
        return text != null && (text.contains(KEEP) || text.startsWith("Done (") || text.contains("For help, type"));
    }

    static final class Gate extends AbstractFilter {

        private final boolean counts;

        Gate(boolean counts) {
            super(Result.NEUTRAL, Result.DENY);
            this.counts = counts;
        }

        @Override
        public Result filter(LogEvent event) {
            if (!ACTIVE.get()) {
                return Result.NEUTRAL;
            }
            Level level = event.getLevel();
            if (level != null && level.isMoreSpecificThan(Level.WARN)) {
                if (counts) {
                    WARNINGS.incrementAndGet();
                }
                return Result.NEUTRAL;
            }
            Message message = event.getMessage();
            if (keep(message == null ? null : message.getFormattedMessage())) {
                return Result.NEUTRAL;
            }
            if (counts) {
                HIDDEN.incrementAndGet();
            }
            return Result.DENY;
        }

        @Override
        public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Message msg, Throwable t) {
            return Result.NEUTRAL;
        }

        @Override
        public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Object msg, Throwable t) {
            return Result.NEUTRAL;
        }

        @Override
        public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, String msg, Object... params) {
            return Result.NEUTRAL;
        }
    }
}
