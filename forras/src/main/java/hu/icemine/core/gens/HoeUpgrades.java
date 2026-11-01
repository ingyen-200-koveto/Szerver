package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A Gens kapa fejlesztései (gens.yml: hoe). Csak számol, a Bukkit részeket a GensMode végzi, így tesztelhető.
 * <ul>
 *     <li>boseg: szintenként +X% esély egy extra termésre</li>
 *     <li>kaszalas: egyszerre több növény (1 = 3x3, 2 = 5x5, 3 = az egész föld)</li>
 *     <li>aranykez: szintenként +X% eladási ár</li>
 *     <li>szerencse: szintenként +X% esély, hogy aratáskor pénzt találsz (a termény ára × luck-multiplier)</li>
 * </ul>
 */
public final class HoeUpgrades {

    public static final String[] IDS = {"boseg", "kaszalas", "aranykez", "szerencse"};
    public static final String[] MATERIALS = {"WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE"};

    public static final class Enchant {
        public final String id;
        public final String name;
        public final String icon;
        public final int max;
        public final long price;
        public final double growth;
        public final double perLevel;
        public final List<String> description;

        Enchant(String id, String name, String icon, int max, long price, double growth, double perLevel, List<String> description) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.max = max;
            this.price = price;
            this.growth = growth;
            this.perLevel = perLevel;
            this.description = description;
        }

        /** A következő szint ára, vagy -1, ha már maximum. */
        public long priceFor(int level) {
            if (level >= max) {
                return -1;
            }
            return GensProgress.cost(price, growth, level);
        }
    }

    private final Map<String, Enchant> enchants = new LinkedHashMap<>();
    private int[] tiers = {0, 5, 15, 28, 42, 55};
    private double luckMultiplier = 10;

    public HoeUpgrades() {
        defaults();
    }

    private void defaults() {
        enchants.clear();
        enchants.put("boseg", new Enchant("boseg", "&a&lBőség", "WHEAT", 25, 750, 1.42, 10, List.of("&7Szintenként +10% esély egy extra termésre.")));
        enchants.put("kaszalas", new Enchant("kaszalas", "&e&lKaszálás", "SHEARS", 3, 25000, 10, 1,
                List.of("&7Egyszerre több növényt aratsz:", "&73x3, utána 5x5, végül az egész föld.")));
        enchants.put("aranykez", new Enchant("aranykez", "&6&lAranykéz", "GOLD_INGOT", 20, 1500, 1.45, 5, List.of("&7Szintenként +5% eladási ár.")));
        enchants.put("szerencse", new Enchant("szerencse", "&d&lSzerencse", "RABBIT_FOOT", 10, 10000, 1.6, 0.5,
                List.of("&7Szintenként +0,5% esély, hogy aratáskor", "&7pénzt találsz (a termény tízszeresét).")));
    }

    public void load(ConfigurationSection root, List<String> problems) {
        defaults();
        if (root == null) {
            return;
        }
        List<Integer> list = root.getIntegerList("hoe.tiers");
        if (list.size() == MATERIALS.length) {
            int[] read = new int[list.size()];
            for (int i = 0; i < read.length; i++) {
                read[i] = Math.max(0, list.get(i));
            }
            tiers = read;
        } else if (root.isSet("hoe.tiers")) {
            problems.add("gens.yml -> hoe.tiers: pontosan " + MATERIALS.length + " szám kell (fa, kő, vas, arany, gyémánt, netherit)");
        }
        luckMultiplier = Math.max(1, Math.min(1000, root.getDouble("hoe.luck-multiplier", 10)));
        ConfigurationSection section = root.getConfigurationSection("hoe.enchants");
        if (section == null) {
            return;
        }
        for (String id : IDS) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Enchant fallback = enchants.get(id);
            List<String> description = entry.getStringList("description");
            enchants.put(id, new Enchant(id, entry.getString("name", fallback.name), entry.getString("icon", fallback.icon),
                    Math.max(0, Math.min(id.equals("kaszalas") ? 3 : 1000, entry.getInt("max", fallback.max))),
                    Math.max(0, entry.getLong("price", fallback.price)),
                    Math.max(1.0, Math.min(100.0, entry.getDouble("price-multiplier", fallback.growth))),
                    Math.max(0, entry.getDouble("per-level", fallback.perLevel)),
                    description.isEmpty() ? fallback.description : new ArrayList<>(description)));
        }
        for (String key : section.getKeys(false)) {
            if (!enchants.containsKey(key.toLowerCase(Locale.ROOT))) {
                problems.add("gens.yml -> hoe.enchants." + key + ": ismeretlen fejlesztés (boseg, kaszalas, aranykez, szerencse lehet)");
            }
        }
    }

    public Map<String, Enchant> enchants() {
        return Collections.unmodifiableMap(enchants);
    }

    public Enchant get(String id) {
        return enchants.get(id);
    }

    private static int level(Map<String, Integer> levels, String id) {
        return levels == null ? 0 : Math.max(0, levels.getOrDefault(id, 0));
    }

    /** Extra termés esélye százalékban (100 = biztos +1). */
    public double extraPercent(Map<String, Integer> levels) {
        return Math.min(level(levels, "boseg"), enchants.get("boseg").max) * enchants.get("boseg").perLevel;
    }

    /** A kaszálás sugara: 0 = egy növény, 1 = 3x3, 2 = 5x5, 3 = az egész föld. */
    public int radius(Map<String, Integer> levels) {
        return Math.min(3, Math.min(level(levels, "kaszalas"), enchants.get("kaszalas").max));
    }

    public double valuePercent(Map<String, Integer> levels) {
        return Math.min(level(levels, "aranykez"), enchants.get("aranykez").max) * enchants.get("aranykez").perLevel;
    }

    public double luckPercent(Map<String, Integer> levels) {
        return Math.min(25.0, Math.min(level(levels, "szerencse"), enchants.get("szerencse").max) * enchants.get("szerencse").perLevel);
    }

    public double luckMultiplier() {
        return luckMultiplier;
    }

    /** Az összes fejlesztési szint (mindegyik legfeljebb a saját maximumáig számít). */
    public int total(Map<String, Integer> levels) {
        int sum = 0;
        for (String id : IDS) {
            sum += Math.min(level(levels, id), enchants.get(id).max);
        }
        return sum;
    }

    /** Ennyi fejlesztési szint kell az adott kapa anyaghoz. */
    public int levelsFor(int tier) {
        return tiers[Math.max(0, Math.min(tiers.length - 1, tier))];
    }

    /** A kapa anyaga (0 = fa ... 5 = netherit) az összes fejlesztési szint alapján. */
    public int tier(Map<String, Integer> levels) {
        int total = total(levels);
        int result = 0;
        for (int i = 0; i < tiers.length; i++) {
            if (total >= tiers[i]) {
                result = i;
            }
        }
        return result;
    }

    /** Átlagosan ennyi termés jár egy növényért. */
    public static double expected(double multiplier, double extraPercent) {
        return Math.min(64.0, Math.max(1.0, multiplier) * (1.0 + Math.max(0.0, extraPercent) / 100.0));
    }

    /**
     * Hány termés jár egy növényért: a várható érték egész része biztos, a tört rész eséllyel jön.
     * @param roll egy 0 és 1 közötti véletlen szám
     */
    public static int amount(double multiplier, double extraPercent, double roll) {
        double expected = expected(multiplier, extraPercent);
        int base = (int) Math.floor(expected);
        return Math.min(64, base + (roll < expected - base ? 1 : 0));
    }
}
