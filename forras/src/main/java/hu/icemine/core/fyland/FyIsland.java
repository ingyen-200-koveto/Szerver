package hu.icemine.core.fyland;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Egy Fyland sziget: a varázsblokk helye, a tagok, a haladás (fázis, kitermelt blokkok) és a fejlesztések. */
final class FyIsland {

    final UUID id;
    UUID owner;
    String ownerName;
    /** A tagok (a tulajdonos nélkül): uuid, név. */
    final Map<UUID, String> members = new LinkedHashMap<>();
    int index;
    int x;
    int y;
    int z;
    double homeX;
    double homeY;
    double homeZ;
    float homeYaw;
    long blocks;
    int phase;
    long phaseBlocks;
    int cycle = 1;
    boolean visitors = true;
    long created;
    long lastReset;
    final Map<String, Integer> upgrades = new HashMap<>();
    /** A következő blokk a fázis jutalom láda legyen. */
    boolean rewardPending;
    /** A szigetre rakott értékes blokkok összértéke (ebből jön a sziget szintje). */
    long worth;
    /** A sziget neve (/fy create <név>); üresen a tulajdonos neve látszik. */
    String name = "";
    /** Moderátorok: meghívhatnak, kirúghatnak (nem moderátort), kitilthatnak, és állíthatják a látogatást. */
    final Set<UUID> moderators = new LinkedHashSet<>();
    /** Kitiltott játékosok: uuid, név. */
    final Map<UUID, String> banned = new LinkedHashMap<>();
    /** A látogatási pont (ide érkeznek a látogatók és a /pw-n át jövők). */
    boolean hasWarp;
    double warpX;
    double warpY;
    double warpZ;
    float warpYaw;
    /** A nyilvános játékos warp neve (/pw); üresen nincs a listában. */
    String warpName = "";
    /** Értékelések a játékos warpra: uuid, 1-5. */
    final Map<UUID, Integer> ratings = new HashMap<>();
    /** Ideiglenes segítők (coop): amíg fent vannak, építhetnek. Nem mentődik. */
    final transient Set<UUID> coops = new HashSet<>();
    transient long lastRecalc;
    transient boolean dirty;

    FyIsland(UUID id, UUID owner, String ownerName) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    boolean member(UUID player) {
        return owner.equals(player) || members.containsKey(player);
    }

    /** Tag, vagy ideiglenes segítő (coop): építhet a szigeten. */
    boolean trusted(UUID player) {
        return member(player) || coops.contains(player);
    }

    /** Tulajdonos vagy moderátor. */
    boolean manager(UUID player) {
        return owner.equals(player) || moderators.contains(player) && members.containsKey(player);
    }

    String display() {
        return name == null || name.isBlank() ? ownerName : name;
    }

    double rating() {
        if (ratings.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (int value : ratings.values()) {
            total += value;
        }
        return total / ratings.size();
    }

    int size() {
        return members.size() + 1;
    }

    int level(String upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    String name(UUID player) {
        return owner.equals(player) ? ownerName : members.getOrDefault(player, "?");
    }

    void write(YamlConfiguration yaml) {
        yaml.set("owner", owner.toString());
        yaml.set("owner-name", ownerName);
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            yaml.set("members." + entry.getKey(), entry.getValue());
        }
        yaml.set("index", index);
        yaml.set("x", x);
        yaml.set("y", y);
        yaml.set("z", z);
        yaml.set("home.x", homeX);
        yaml.set("home.y", homeY);
        yaml.set("home.z", homeZ);
        yaml.set("home.yaw", (double) homeYaw);
        yaml.set("blocks", blocks);
        yaml.set("phase", phase);
        yaml.set("phase-blocks", phaseBlocks);
        yaml.set("cycle", cycle);
        yaml.set("visitors", visitors);
        yaml.set("created", created);
        yaml.set("last-reset", lastReset);
        yaml.set("reward-pending", rewardPending);
        yaml.set("worth", worth);
        yaml.set("name", name);
        java.util.List<String> mods = new java.util.ArrayList<>();
        for (UUID id : moderators) {
            mods.add(id.toString());
        }
        yaml.set("moderators", mods);
        for (Map.Entry<UUID, String> entry : banned.entrySet()) {
            yaml.set("banned." + entry.getKey(), entry.getValue());
        }
        if (hasWarp) {
            yaml.set("warp.x", warpX);
            yaml.set("warp.y", warpY);
            yaml.set("warp.z", warpZ);
            yaml.set("warp.yaw", (double) warpYaw);
        }
        yaml.set("warp-name", warpName);
        for (Map.Entry<UUID, Integer> entry : ratings.entrySet()) {
            yaml.set("ratings." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
            yaml.set("upgrades." + entry.getKey(), entry.getValue());
        }
    }

    static FyIsland read(UUID id, YamlConfiguration yaml) {
        UUID owner = uuid(yaml.getString("owner"));
        if (owner == null) {
            return null;
        }
        FyIsland island = new FyIsland(id, owner, yaml.getString("owner-name", "?"));
        ConfigurationSection members = yaml.getConfigurationSection("members");
        if (members != null) {
            for (String key : members.getKeys(false)) {
                UUID member = uuid(key);
                if (member != null && !member.equals(owner)) {
                    island.members.put(member, members.getString(key, "?"));
                }
            }
        }
        island.index = yaml.getInt("index");
        island.x = yaml.getInt("x");
        island.y = yaml.getInt("y", 100);
        island.z = yaml.getInt("z");
        island.homeX = yaml.getDouble("home.x", island.x + 0.5);
        island.homeY = yaml.getDouble("home.y", island.y + 1);
        island.homeZ = yaml.getDouble("home.z", island.z + 0.5);
        island.homeYaw = (float) yaml.getDouble("home.yaw", 0);
        island.blocks = Math.max(0, yaml.getLong("blocks"));
        island.phase = Math.max(0, yaml.getInt("phase"));
        island.phaseBlocks = Math.max(0, yaml.getLong("phase-blocks"));
        island.cycle = Math.max(1, yaml.getInt("cycle", 1));
        island.visitors = yaml.getBoolean("visitors", true);
        island.created = yaml.getLong("created");
        island.lastReset = yaml.getLong("last-reset");
        island.rewardPending = yaml.getBoolean("reward-pending", false);
        island.worth = Math.max(0, yaml.getLong("worth"));
        island.name = yaml.getString("name", "");
        for (String text : yaml.getStringList("moderators")) {
            UUID mod = uuid(text);
            if (mod != null) {
                island.moderators.add(mod);
            }
        }
        ConfigurationSection banned = yaml.getConfigurationSection("banned");
        if (banned != null) {
            for (String key : banned.getKeys(false)) {
                UUID ban = uuid(key);
                if (ban != null) {
                    island.banned.put(ban, banned.getString(key, "?"));
                }
            }
        }
        if (yaml.isSet("warp.x")) {
            island.hasWarp = true;
            island.warpX = yaml.getDouble("warp.x");
            island.warpY = yaml.getDouble("warp.y");
            island.warpZ = yaml.getDouble("warp.z");
            island.warpYaw = (float) yaml.getDouble("warp.yaw");
        }
        island.warpName = yaml.getString("warp-name", "");
        ConfigurationSection ratings = yaml.getConfigurationSection("ratings");
        if (ratings != null) {
            for (String key : ratings.getKeys(false)) {
                UUID rater = uuid(key);
                if (rater != null) {
                    island.ratings.put(rater, Math.max(1, Math.min(5, ratings.getInt(key))));
                }
            }
        }
        ConfigurationSection upgrades = yaml.getConfigurationSection("upgrades");
        if (upgrades != null) {
            for (String key : upgrades.getKeys(false)) {
                island.upgrades.put(key, Math.max(0, upgrades.getInt(key)));
            }
        }
        return island;
    }

    static UUID uuid(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** A sziget rácspontja (index) a világban: négyzetes spirál a középpont körül, a 0 a középső (hub). */
    static int[] grid(int n) {
        if (n <= 0) {
            return new int[]{0, 0};
        }
        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        int t = 2 * k + 1;
        int m = t * t;
        t = t - 1;
        if (n >= m - t) {
            return new int[]{k - (m - n), -k};
        }
        m -= t;
        if (n >= m - t) {
            return new int[]{-k, -k + (m - n)};
        }
        m -= t;
        if (n >= m - t) {
            return new int[]{-k + (m - n), k};
        }
        return new int[]{k, k - (m - n - t)};
    }
}
