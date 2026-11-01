package hu.icemine.core.fyland;

import java.util.HashMap;
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
    transient boolean dirty;

    FyIsland(UUID id, UUID owner, String ownerName) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    boolean member(UUID player) {
        return owner.equals(player) || members.containsKey(player);
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
