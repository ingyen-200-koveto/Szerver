package hu.icemine.core.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** A bedwars.yml játékbeállításai (idők, generátorok, jutalmak, események). Az időket tickben tárolja. */
public final class BwSettings {

    public int countdown = 30;
    public int fullCountdown = 10;
    public int respawn = 5;
    public int voidDepth = 25;
    public int buildLimit = 30;
    public int ironInterval = 24;
    public int goldInterval = 160;
    public int teamEmerald = 1200;
    public int[] diamondTiers = {600, 460, 240};
    public int[] emeraldTiers = {1300, 1000, 700};
    public int trapCooldown = 300;
    public int maxTraps = 3;
    public int endSeconds = 10;
    public long rewardWin = 50L;
    public long rewardKill = 3L;
    public long rewardFinal = 8L;
    public long rewardBed = 15L;
    public long rewardPlay = 5L;
    public final List<Event> timeline = new ArrayList<>();

    public static final class Event {
        /** Hányadik másodpercben történik. */
        public final int at;
        public final String type;
        public final String name;

        Event(int at, String type, String name) {
            this.at = at;
            this.type = type;
            this.name = name;
        }
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int ticks(ConfigurationSection config, String path, double fallback) {
        return (int) Math.round(Math.max(0.1, config.getDouble(path, fallback)) * 20.0);
    }

    private static int[] tiers(ConfigurationSection config, String path, int[] fallback) {
        List<Double> values = config.getDoubleList(path);
        if (values.size() < 3) {
            return fallback;
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (int) Math.round(Math.max(0.5, values.get(i)) * 20.0);
        }
        return result;
    }

    public static BwSettings read(ConfigurationSection config) {
        BwSettings settings = new BwSettings();
        if (config == null) {
            settings.defaultTimeline();
            return settings;
        }
        settings.countdown = clamp(config.getInt("lobby.countdown", 30), 5, 300);
        settings.fullCountdown = clamp(config.getInt("lobby.full-countdown", 10), 3, 120);
        settings.respawn = clamp(config.getInt("game.respawn-seconds", 5), 1, 30);
        settings.voidDepth = clamp(config.getInt("game.void-depth", 25), 5, 100);
        settings.buildLimit = clamp(config.getInt("game.build-limit", 30), 5, 120);
        settings.trapCooldown = clamp(config.getInt("game.trap-cooldown-seconds", 15), 0, 300) * 20;
        settings.maxTraps = clamp(config.getInt("game.max-traps", 3), 1, 6);
        settings.endSeconds = clamp(config.getInt("game.end-seconds", 10), 3, 60);
        settings.ironInterval = ticks(config, "generators.iron", 1.2);
        settings.goldInterval = ticks(config, "generators.gold", 8.0);
        settings.teamEmerald = ticks(config, "generators.team-emerald", 60.0);
        settings.diamondTiers = tiers(config, "generators.diamond", settings.diamondTiers);
        settings.emeraldTiers = tiers(config, "generators.emerald", settings.emeraldTiers);
        settings.rewardWin = Math.max(0L, config.getLong("rewards.win", 50L));
        settings.rewardKill = Math.max(0L, config.getLong("rewards.kill", 3L));
        settings.rewardFinal = Math.max(0L, config.getLong("rewards.final-kill", 8L));
        settings.rewardBed = Math.max(0L, config.getLong("rewards.bed", 15L));
        settings.rewardPlay = Math.max(0L, config.getLong("rewards.play", 5L));
        for (Map<?, ?> entry : config.getMapList("timeline")) {
            Object at = entry.get("at");
            Object type = entry.get("event");
            Object name = entry.get("name");
            if (at == null || type == null) {
                continue;
            }
            try {
                int seconds = (int) Math.round(Double.parseDouble(at.toString()) * 60.0);
                settings.timeline.add(new Event(seconds, type.toString(), name == null ? type.toString() : name.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (settings.timeline.isEmpty()) {
            settings.defaultTimeline();
        }
        return settings;
    }

    private void defaultTimeline() {
        timeline.add(new Event(360, "diamond-2", "Gyémánt II"));
        timeline.add(new Event(720, "emerald-2", "Smaragd II"));
        timeline.add(new Event(1080, "diamond-3", "Gyémánt III"));
        timeline.add(new Event(1440, "emerald-3", "Smaragd III"));
        timeline.add(new Event(1800, "beds", "Ágyak pusztulása"));
        timeline.add(new Event(2400, "end", "Játék vége"));
    }

    /** A következő esemény a megadott másodperc után, vagy null. */
    public Event next(int elapsed) {
        Event best = null;
        for (Event event : timeline) {
            if (event.at > elapsed && (best == null || event.at < best.at)) {
                best = event;
            }
        }
        return best;
    }
}
