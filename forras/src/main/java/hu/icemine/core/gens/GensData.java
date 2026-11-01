package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;

public final class GensData {

    /**
     * Egy föld a szigeten (gens.yml: fields). A földeket sorban lehet megnyitni, az első ingyenes.
     * value: ennyit ér egy termény eladáskor | regrow: ennyi mp alatt nő vissza | multiplier: ennyi termény jár egy növényért.
     */
    public static final class Field {
        public final int index;
        public final String name;
        public final Crop crop;
        public final long value;
        public final int regrow;
        public final long price;
        public final double multiplier;

        public Field(int index, String name, Crop crop, long value, int regrow, long price, double multiplier) {
            this.index = index;
            this.name = name;
            this.crop = crop;
            this.value = value;
            this.regrow = regrow;
            this.price = price;
            this.multiplier = multiplier;
        }
    }

    /** A régi generátoros Gens alapárai: átálláskor ezek alapján kapja vissza a pénzét, aki generátort vett és fejlesztett. */
    static final long LEGACY_GENERATOR = 300;
    static final long[] LEGACY_UPGRADES = {250, 750, 2000, 5000, 12000, 30000, 75000, 180000, 400000};
    static final Map<String, Long> LEGACY_DROPS = Map.of("COAL", 2L, "IRON_INGOT", 5L, "GOLD_INGOT", 12L, "LAPIS_LAZULI", 25L, "REDSTONE", 45L,
            "DIAMOND", 90L, "EMERALD", 180L, "AMETHYST_SHARD", 350L, "QUARTZ", 650L, "NETHERITE_SCRAP", 1200L);

    /** Egy régi generátor értéke: a vételár és az eddigi fejlesztések. */
    public static long legacyGenerator(int tier) {
        long total = LEGACY_GENERATOR;
        for (int i = 0; i < Math.min(tier - 1, LEGACY_UPGRADES.length); i++) {
            total += LEGACY_UPGRADES[i];
        }
        return total;
    }

    public static long legacyDrop(String material) {
        return LEGACY_DROPS.getOrDefault(material, 0L);
    }

    /** A földek beolvasása a gens.yml-ből (fields.1, fields.2 ...), legfeljebb 8, sorrendben. */
    public static List<Field> loadFields(ConfigurationSection root, List<String> problems) {
        List<Field> result = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("fields");
        if (section == null) {
            problems.add("gens.yml -> fields: nincs egy föld sem, búzával indulok");
            result.add(new Field(0, "Búza", Crop.WHEAT, 2, 5, 0, 1.0));
            return result;
        }
        TreeMap<Integer, ConfigurationSection> sorted = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int number = Integer.parseInt(key.trim());
                if (number < 1 || number > FarmLayout.FIELDS) {
                    problems.add("gens.yml -> fields." + key + ": 1 és " + FarmLayout.FIELDS + " között lehet");
                } else if (section.isConfigurationSection(key)) {
                    sorted.put(number, section.getConfigurationSection(key));
                }
            } catch (NumberFormatException exception) {
                problems.add("gens.yml -> fields." + key + ": a kulcs csak szám lehet (1-" + FarmLayout.FIELDS + ")");
            }
        }
        for (Map.Entry<Integer, ConfigurationSection> entry : sorted.entrySet()) {
            ConfigurationSection field = entry.getValue();
            String where = "gens.yml -> fields." + entry.getKey();
            Crop crop = Crop.of(field.getString("crop", ""));
            if (crop == null) {
                problems.add(where + ".crop: ismeretlen növény (wheat, carrots, potatoes, beetroots, melon, pumpkin, sweet_berry_bush, nether_wart)");
                crop = Crop.WHEAT;
            }
            result.add(new Field(result.size(), field.getString("name", crop.name()), crop,
                    Math.max(0, Math.min(1_000_000_000L, field.getLong("value", 1))),
                    Math.max(1, Math.min(3600, field.getInt("regrow", 5))),
                    result.isEmpty() ? 0 : Math.max(0, field.getLong("price", 0)),
                    Math.max(1.0, Math.min(64.0, field.getDouble("multiplier", 1.0)))));
        }
        if (result.isEmpty()) {
            result.add(new Field(0, "Búza", Crop.WHEAT, 2, 5, 0, 1.0));
        }
        return result;
    }

    /** Sziget tag jogai (bitek). */
    public static final int BUILD = 1;
    public static final int CONTAINERS = 2;
    public static final int COLLECT = 4;
    public static final int MANAGE = 8;
    public static final int DEFAULT_MEMBER = BUILD | CONTAINERS | COLLECT;

    public static final class Player {
        public final UUID uuid;
        public int plot;
        /** Ennyi föld van megnyitva a szigeten (1 = csak a búza). Az adatbázis "level" oszlopában van. */
        public int level;
        public final Set<UUID> trusted = new HashSet<>();
        /** A kapa fejlesztései (a profil tárolja: gens.hoe.boseg ...). */
        public final Map<String, Integer> hoe = new LinkedHashMap<>();
        /** A régi generátorok szintjei (csak az átállásig, a visszatérítéshez). */
        public final List<Integer> legacyGenerators = new ArrayList<>();
        /** Át kell még váltani a régi (generátoros) szigetet a földekre: belépéskor elintézzük a táskát is. */
        public boolean legacyPending;
        /** Tagonként a jogok (ha nincs benne, akkor DEFAULT_MEMBER). */
        public final Map<UUID, Integer> permissions = new LinkedHashMap<>();
        /** Ezeket a profil tárolja (gens.prestige, gens.rebirth, gens.up.*, gens.autosell...), belépéskor töltjük be. */
        public int prestige;
        public int rebirth;
        public final Map<String, Integer> upgrades = new LinkedHashMap<>();
        public boolean autosellOwned;
        public boolean autosell;
        public boolean backpackLarge;
        public boolean locked;
        public long daily;
        public String questDay = "";
        public final Map<String, Long> progress = new LinkedHashMap<>();
        public final Set<String> claimed = new HashSet<>();
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

        public int permissionsOf(UUID member) {
            return permissions.getOrDefault(member, DEFAULT_MEMBER);
        }

        public boolean can(UUID player, int flag) {
            if (uuid.equals(player)) {
                return true;
            }
            return trusted.contains(player) && (permissionsOf(player) & flag) != 0;
        }

        /** "uuid" (alap jogok) vagy "uuid:jogok" vesszővel elválasztva; a régi, csak uuid-s formát is olvassa. */
        public String encodeTrusted() {
            StringBuilder out = new StringBuilder();
            for (UUID id : trusted) {
                if (out.length() > 0) {
                    out.append(',');
                }
                out.append(id);
                int flags = permissionsOf(id);
                if (flags != DEFAULT_MEMBER) {
                    out.append(':').append(flags);
                }
            }
            return out.toString();
        }

        public void decodeTrusted(String text) {
            trusted.clear();
            permissions.clear();
            if (text == null || text.isEmpty()) {
                return;
            }
            for (String part : text.split(",")) {
                String[] pieces = part.trim().split(":");
                try {
                    UUID id = UUID.fromString(pieces[0]);
                    trusted.add(id);
                    if (pieces.length > 1) {
                        permissions.put(id, Integer.parseInt(pieces[1]) & (BUILD | CONTAINERS | COLLECT | MANAGE));
                    }
                } catch (IllegalArgumentException ignored) {
                    // sérült bejegyzés: kihagyjuk
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
