package hu.icemine.core.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private String rank;
    private final long firstJoin;
    private long lastSeen;
    private long playtime;
    private long sessionStart;
    private final Map<String, Long> balances = new HashMap<>();
    private final Map<String, Long> stats = new HashMap<>();
    private final Map<String, String> settings = new LinkedHashMap<>();
    private final Set<String> dirtyBalances = new HashSet<>();
    private final Set<String> dirtyStats = new HashSet<>();
    private boolean dirtyMain;
    private final boolean fresh;

    public PlayerProfile(UUID uuid, String name, String rank, long firstJoin, long lastSeen, long playtime, boolean fresh) {
        this.uuid = uuid;
        this.name = name;
        this.rank = rank;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.playtime = playtime;
        this.fresh = fresh;
        this.dirtyMain = fresh;
        this.sessionStart = System.currentTimeMillis();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirtyMain = true;
        }
    }

    public String rank() {
        return rank;
    }

    public void rank(String rank) {
        this.rank = rank;
        dirtyMain = true;
    }

    public boolean fresh() {
        return fresh;
    }

    public long firstJoin() {
        return firstJoin;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public long playtime() {
        return playtime + (System.currentTimeMillis() - sessionStart);
    }

    public void closeSession() {
        long now = System.currentTimeMillis();
        playtime += now - sessionStart;
        sessionStart = now;
        lastSeen = now;
        dirtyMain = true;
    }

    public long balance(String currency) {
        Long value = balances.get(currency);
        return value == null ? 0L : value;
    }

    void loadBalance(String currency, long amount) {
        balances.put(currency, amount);
    }

    public void setBalance(String currency, long amount) {
        balances.put(currency, amount);
        dirtyBalances.add(currency);
    }

    public long stat(String key) {
        Long value = stats.get(key);
        return value == null ? 0L : value;
    }

    public boolean hasStat(String key) {
        return stats.containsKey(key);
    }

    void loadStat(String key, long value) {
        stats.put(key, value);
    }

    public void setStat(String key, long value) {
        stats.put(key, value);
        dirtyStats.add(key);
    }

    public long addStat(String key, long delta) {
        long value = stat(key) + delta;
        setStat(key, value);
        return value;
    }

    public boolean maxStat(String key, long value) {
        if (value > stat(key)) {
            setStat(key, value);
            return true;
        }
        return false;
    }

    public boolean minStat(String key, long value) {
        if (!hasStat(key) || stat(key) <= 0 || value < stat(key)) {
            setStat(key, value);
            return true;
        }
        return false;
    }

    public String setting(String key, String fallback) {
        String value = settings.get(key);
        return value == null ? fallback : value;
    }

    public boolean flag(String key, boolean fallback) {
        String value = settings.get(key);
        return value == null ? fallback : "1".equals(value);
    }

    public void put(String key, String value) {
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
        dirtyMain = true;
    }

    public void putFlag(String key, boolean value) {
        put(key, value ? "1" : "0");
    }

    String encodeSettings() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(entry.getKey().replace(";", "").replace("=", "")).append('=').append(entry.getValue().replace(";", "").replace("=", ""));
        }
        return out.toString();
    }

    void decodeSettings(String encoded) {
        settings.clear();
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        for (String part : encoded.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) {
                settings.put(part.substring(0, split), part.substring(split + 1));
            }
        }
    }

    public boolean dirty() {
        return dirtyMain || !dirtyBalances.isEmpty() || !dirtyStats.isEmpty();
    }

    Snapshot snapshot(boolean all) {
        Map<String, Long> balanceCopy = new HashMap<>();
        for (String key : all ? balances.keySet() : dirtyBalances) {
            balanceCopy.put(key, balance(key));
        }
        Map<String, Long> statCopy = new HashMap<>();
        for (String key : all ? stats.keySet() : dirtyStats) {
            statCopy.put(key, stat(key));
        }
        Snapshot snapshot = new Snapshot(uuid, name, rank, firstJoin, System.currentTimeMillis(), playtime(), encodeSettings(), all || dirtyMain, balanceCopy, statCopy);
        dirtyBalances.clear();
        dirtyStats.clear();
        dirtyMain = false;
        return snapshot;
    }

    static final class Snapshot {
        final UUID uuid;
        final String name;
        final String rank;
        final long firstJoin;
        final long lastSeen;
        final long playtime;
        final String settings;
        final boolean main;
        final Map<String, Long> balances;
        final Map<String, Long> stats;

        Snapshot(UUID uuid, String name, String rank, long firstJoin, long lastSeen, long playtime, String settings, boolean main,
                 Map<String, Long> balances, Map<String, Long> stats) {
            this.uuid = uuid;
            this.name = name;
            this.rank = rank;
            this.firstJoin = firstJoin;
            this.lastSeen = lastSeen;
            this.playtime = playtime;
            this.settings = settings;
            this.main = main;
            this.balances = balances;
            this.stats = stats;
        }
    }
}
