package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

public final class GensData {

    public static final class Tier {
        public final int level;
        public final String name;
        public final Material block;
        public final Material drop;
        public final long value;
        public final int interval;
        public final int amount;
        public final long capacity;
        public final long upgrade;

        public Tier(int level, String name, Material block, Material drop, long value, int interval, int amount, long capacity, long upgrade) {
            this.level = level;
            this.name = name;
            this.block = block;
            this.drop = drop;
            this.value = value;
            this.interval = interval;
            this.amount = amount;
            this.capacity = capacity;
            this.upgrade = upgrade;
        }
    }

    public static final class Level {
        public final int level;
        public final int slots;
        public final double multiplier;
        public final long price;

        public Level(int level, int slots, double multiplier, long price) {
            this.level = level;
            this.slots = slots;
            this.multiplier = multiplier;
            this.price = price;
        }
    }

    public static final class Generator {
        public final UUID owner;
        public final int x;
        public final int y;
        public final int z;
        public int tier;
        public long stored;
        public long next;
        public boolean dirty;

        public Generator(UUID owner, int x, int y, int z, int tier, long stored) {
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.stored = stored;
        }

        public long key() {
            return GensData.key(x, y, z);
        }
    }

    public static final class Player {
        public final UUID uuid;
        public int plot;
        public int level;
        public final Set<UUID> trusted = new HashSet<>();
        public long daily;
        public String questDay = "";
        public final Map<String, Long> progress = new LinkedHashMap<>();
        public final Set<String> claimed = new HashSet<>();
        public final List<Generator> generators = new ArrayList<>();
        public boolean dirty;
        public boolean fresh;

        public Player(UUID uuid, int plot, int level) {
            this.uuid = uuid;
            this.plot = plot;
            this.level = level;
        }

        public String encodeQuests() {
            StringBuilder out = new StringBuilder("day=").append(questDay);
            for (Map.Entry<String, Long> entry : progress.entrySet()) {
                out.append(';').append(entry.getKey()).append('=').append(entry.getValue());
            }
            for (String done : claimed) {
                out.append(';').append(done).append("=done");
            }
            return out.toString();
        }

        public void decodeQuests(String text) {
            progress.clear();
            claimed.clear();
            questDay = "";
            if (text == null || text.isEmpty()) {
                return;
            }
            for (String part : text.split(";")) {
                int split = part.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = part.substring(0, split);
                String value = part.substring(split + 1);
                if (key.equals("day")) {
                    questDay = value;
                } else if (value.equals("done")) {
                    claimed.add(key);
                } else {
                    try {
                        progress.put(key, Long.parseLong(value));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        public String encodeTrusted() {
            StringBuilder out = new StringBuilder();
            for (UUID id : trusted) {
                if (out.length() > 0) {
                    out.append(',');
                }
                out.append(id);
            }
            return out.toString();
        }

        public void decodeTrusted(String text) {
            trusted.clear();
            if (text == null || text.isEmpty()) {
                return;
            }
            for (String part : text.split(",")) {
                try {
                    trusted.add(UUID.fromString(part.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private GensData() {
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
