package hu.icemine.kingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Egy királyság minden adata. A logika (foglalás, háború, fejlesztés) a KingdomManagerben van, ez csak tárol és számol.
 */
public final class Kingdom {

    public static final int LOG_SIZE = 60;

    public final UUID id;
    public String name;
    public String color = "&6";
    public String description = "";
    public UUID leader;
    public long created;
    /** Tag -> rang azonosító. */
    public final Map<UUID, String> members = new LinkedHashMap<>();
    public final Map<String, Rank> ranks = new LinkedHashMap<>();
    public final Set<Long> claims = new HashSet<>();
    /** Ki foglalta az adott chunkot (a rangok foglalási limitjéhez). */
    public final Map<Long, UUID> claimers = new HashMap<>();
    /** Otthon: x, y, z, yaw (a Kingdoms világban). */
    public double[] home;
    /** A Nexus blokk helye, vagy null, ha még nincs lerakva. */
    public int[] nexus;
    public long bank;
    public long resourcePoints;
    public long xp;
    public UUID nation;
    public boolean pacifist;
    /** Bárki csatlakozhat meghívó nélkül. */
    public boolean open;
    public final Map<UUID, Relation> relations = new HashMap<>();
    public final Map<String, Integer> misc = new HashMap<>();
    public final Map<String, Integer> powerups = new HashMap<>();
    public final Map<String, Integer> champion = new HashMap<>();
    public final List<Placed> placed = new ArrayList<>();
    public long shieldUntil;
    public long invadeCooldownUntil;
    public long pacifistChanged;
    public long kills;
    public long deaths;
    public long invasionsWon;
    public long invasionsLost;
    public long landsTaken;
    public long landsLost;
    public final Deque<String> log = new ArrayDeque<>();
    public final Deque<String> bankLog = new ArrayDeque<>();

    public transient boolean dirty;
    /** Beérkezett kapcsolat kérések: ki -> mit szeretne. */
    public final transient Map<UUID, Relation> relationRequests = new HashMap<>();

    public Kingdom(UUID id, String name, UUID leader, long created) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.created = created;
    }

    public String display() {
        return color + name;
    }

    public Rank rankOf(UUID player) {
        String id = members.get(player);
        Rank rank = id == null ? null : ranks.get(id);
        return rank == null && id != null ? lowest() : rank;
    }

    public boolean can(UUID player, KPerm perm) {
        if (player.equals(leader)) {
            return true;
        }
        Rank rank = rankOf(player);
        return rank != null && rank.has(perm);
    }

    /** A rangok sorrendben, a királytól lefelé. */
    public List<Rank> sortedRanks() {
        List<Rank> list = new ArrayList<>(ranks.values());
        list.sort(Comparator.comparingInt((Rank r) -> r.priority).thenComparing(r -> r.id));
        return list;
    }

    /** A legalacsonyabb rang: ezt kapják az új tagok. */
    public Rank lowest() {
        List<Rank> sorted = sortedRanks();
        return sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
    }

    public Rank king() {
        return ranks.get(Rank.KING);
    }

    /** Magasabb rangú-e a (kisebb priority számú) a másiknál. */
    public boolean outranks(UUID a, UUID b) {
        if (a.equals(leader)) {
            return !b.equals(leader);
        }
        Rank ra = rankOf(a);
        Rank rb = rankOf(b);
        return ra != null && rb != null && ra.priority < rb.priority;
    }

    public Relation stance(UUID other) {
        return relations.getOrDefault(other, Relation.NEUTRAL);
    }

    public int level(Map<String, Integer> map, String id) {
        return Math.max(0, map.getOrDefault(id, 0));
    }

    public boolean hasNexus() {
        return nexus != null;
    }

    public long nexusChunk() {
        return nexus == null ? Long.MIN_VALUE : Lands.key(nexus[0] >> 4, nexus[2] >> 4);
    }

    public int claimedBy(UUID member) {
        int count = 0;
        for (UUID who : claimers.values()) {
            if (who.equals(member)) {
                count++;
            }
        }
        return count;
    }

    public int count(String kind, String type) {
        int count = 0;
        for (Placed p : placed) {
            if (p.kind.equals(kind) && (type == null || p.type.equals(type))) {
                count++;
            }
        }
        return count;
    }

    public Placed placedAt(int x, int y, int z) {
        for (Placed p : placed) {
            if (p.at(x, y, z)) {
                return p;
            }
        }
        return null;
    }

    public void log(String line) {
        log.addFirst(System.currentTimeMillis() + "|" + line);
        while (log.size() > LOG_SIZE) {
            log.removeLast();
        }
        dirty = true;
    }

    public void bankLog(String player, long amount, String what) {
        bankLog.addFirst(System.currentTimeMillis() + "|" + player + "|" + amount + "|" + what);
        while (bankLog.size() > LOG_SIZE) {
            bankLog.removeLast();
        }
        dirty = true;
    }

    // ---- mentés ----

    void write(ConfigurationSection section) {
        section.set("name", name);
        section.set("color", color);
        section.set("description", description);
        section.set("leader", leader.toString());
        section.set("created", created);
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            section.set("members." + entry.getKey(), entry.getValue());
        }
        for (Rank rank : ranks.values()) {
            rank.write(section.createSection("ranks." + rank.id));
        }
        List<String> chunks = new ArrayList<>();
        for (long key : claims) {
            chunks.add(Lands.text(key));
        }
        section.set("claims", chunks);
        List<String> by = new ArrayList<>();
        for (Map.Entry<Long, UUID> entry : claimers.entrySet()) {
            if (claims.contains(entry.getKey())) {
                by.add(Lands.text(entry.getKey()) + ";" + entry.getValue());
            }
        }
        section.set("claimers", by);
        if (home != null) {
            section.set("home", List.of(home[0], home[1], home[2], home[3]));
        }
        if (nexus != null) {
            section.set("nexus", List.of(nexus[0], nexus[1], nexus[2]));
        }
        section.set("bank", bank);
        section.set("resource-points", resourcePoints);
        section.set("xp", xp);
        section.set("nation", nation == null ? null : nation.toString());
        section.set("pacifist", pacifist);
        section.set("open", open);
        for (Map.Entry<UUID, Relation> entry : relations.entrySet()) {
            if (entry.getValue() != Relation.NEUTRAL) {
                section.set("relations." + entry.getKey(), entry.getValue().name());
            }
        }
        writeLevels(section, "misc", misc);
        writeLevels(section, "powerups", powerups);
        writeLevels(section, "champion", champion);
        for (Placed p : placed) {
            p.write(section.createSection("placed." + p.id));
        }
        section.set("shield-until", shieldUntil);
        section.set("invade-cooldown-until", invadeCooldownUntil);
        section.set("pacifist-changed", pacifistChanged);
        section.set("stats.kills", kills);
        section.set("stats.deaths", deaths);
        section.set("stats.invasions-won", invasionsWon);
        section.set("stats.invasions-lost", invasionsLost);
        section.set("stats.lands-taken", landsTaken);
        section.set("stats.lands-lost", landsLost);
        section.set("log", new ArrayList<>(log));
        section.set("bank-log", new ArrayList<>(bankLog));
    }

    private static void writeLevels(ConfigurationSection section, String path, Map<String, Integer> map) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() > 0) {
                section.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private static void readLevels(ConfigurationSection section, String path, Map<String, Integer> map) {
        ConfigurationSection levels = section.getConfigurationSection(path);
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                map.put(key.toLowerCase(Locale.ROOT), Math.max(0, levels.getInt(key)));
            }
        }
    }

    static UUID uuid(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Beolvasás; a defaults az alap rangok (ha egy régi mentésben nincsenek rangok). */
    static Kingdom read(UUID id, ConfigurationSection section, List<Rank> defaults) {
        Kingdom kingdom = new Kingdom(id, section.getString("name", "?"), uuid(section.getString("leader")), section.getLong("created"));
        kingdom.color = section.getString("color", "&6");
        kingdom.description = section.getString("description", "");
        ConfigurationSection ranks = section.getConfigurationSection("ranks");
        if (ranks != null) {
            for (String key : ranks.getKeys(false)) {
                ConfigurationSection entry = ranks.getConfigurationSection(key);
                if (entry != null) {
                    String rankId = key.toLowerCase(Locale.ROOT);
                    kingdom.ranks.put(rankId, Rank.read(rankId, entry, null));
                }
            }
        }
        if (kingdom.ranks.isEmpty() || !kingdom.ranks.containsKey(Rank.KING)) {
            for (Rank rank : defaults) {
                kingdom.ranks.putIfAbsent(rank.id, rank.copy());
            }
        }
        ConfigurationSection members = section.getConfigurationSection("members");
        if (members != null) {
            for (String key : members.getKeys(false)) {
                UUID member = uuid(key);
                if (member != null) {
                    String rank = members.getString(key, "").toLowerCase(Locale.ROOT);
                    kingdom.members.put(member, kingdom.ranks.containsKey(rank) ? rank : kingdom.lowest().id);
                }
            }
        }
        if (kingdom.leader != null) {
            kingdom.members.put(kingdom.leader, Rank.KING);
        }
        for (String text : section.getStringList("claims")) {
            Long key = Lands.parse(text);
            if (key != null) {
                kingdom.claims.add(key);
            }
        }
        for (String text : section.getStringList("claimers")) {
            int split = text.lastIndexOf(';');
            Long key = split <= 0 ? null : Lands.parse(text.substring(0, split));
            UUID who = split <= 0 ? null : uuid(text.substring(split + 1));
            if (key != null && who != null && kingdom.claims.contains(key)) {
                kingdom.claimers.put(key, who);
            }
        }
        List<Double> home = section.getDoubleList("home");
        if (home.size() >= 4) {
            kingdom.home = new double[]{home.get(0), home.get(1), home.get(2), home.get(3)};
        }
        List<Integer> nexus = section.getIntegerList("nexus");
        if (nexus.size() >= 3) {
            kingdom.nexus = new int[]{nexus.get(0), nexus.get(1), nexus.get(2)};
        }
        kingdom.bank = section.getLong("bank");
        kingdom.resourcePoints = section.getLong("resource-points");
        kingdom.xp = section.getLong("xp");
        kingdom.nation = uuid(section.getString("nation"));
        kingdom.pacifist = section.getBoolean("pacifist");
        kingdom.open = section.getBoolean("open");
        ConfigurationSection relations = section.getConfigurationSection("relations");
        if (relations != null) {
            for (String key : relations.getKeys(false)) {
                UUID other = uuid(key);
                Relation relation = Relation.of(relations.getString(key, ""));
                if (other != null && relation != null) {
                    kingdom.relations.put(other, relation);
                }
            }
        }
        readLevels(section, "misc", kingdom.misc);
        readLevels(section, "powerups", kingdom.powerups);
        readLevels(section, "champion", kingdom.champion);
        ConfigurationSection placed = section.getConfigurationSection("placed");
        if (placed != null) {
            for (String key : placed.getKeys(false)) {
                ConfigurationSection entry = placed.getConfigurationSection(key);
                if (entry != null) {
                    kingdom.placed.add(Placed.read(key, entry));
                }
            }
        }
        kingdom.shieldUntil = section.getLong("shield-until");
        kingdom.invadeCooldownUntil = section.getLong("invade-cooldown-until");
        kingdom.pacifistChanged = section.getLong("pacifist-changed");
        kingdom.kills = section.getLong("stats.kills");
        kingdom.deaths = section.getLong("stats.deaths");
        kingdom.invasionsWon = section.getLong("stats.invasions-won");
        kingdom.invasionsLost = section.getLong("stats.invasions-lost");
        kingdom.landsTaken = section.getLong("stats.lands-taken");
        kingdom.landsLost = section.getLong("stats.lands-lost");
        kingdom.log.addAll(section.getStringList("log"));
        kingdom.bankLog.addAll(section.getStringList("bank-log"));
        return kingdom;
    }
}
