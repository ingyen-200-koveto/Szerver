package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A Gens hosszú távú fejlődése (gens.yml): prestige, rebirth, sziget fejlesztések, automatikus eladás és hátizsák.
 * Csak számol és beállításokat tárol, a Bukkit részeket a GensMode végzi, így tesztelhető.
 *
 * <ul>
 *     <li>Prestige: ha minden föld nyitva van, pénzért újrakezded (a pénz elvész, a földek újra lezárulnak),
 *     cserébe minden prestige után tartósan több termény jár egy növényért.</li>
 *     <li>Rebirth: ha megvan a szükséges prestige, még nagyobb újrakezdés: a prestige, a kapa és a sziget
 *     fejlesztések is nullázódnak, de nagy bónusz jár érte.</li>
 * </ul>
 */
public final class GensProgress {

    public enum Effect {
        /** +% esély egy extra termésre aratáskor (a configban "capacity", a régi név maradt). */
        CAPACITY,
        /** -% visszanövési idő (gyorsabb). */
        SPEED,
        /** +% eladási érték. */
        VALUE;

        static Effect parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    public static final class IslandUpgrade {
        public final String id;
        public final String name;
        public final String icon;
        public final List<String> description;
        public final Effect effect;
        public final double perLevel;
        public final List<Long> prices;

        IslandUpgrade(String id, String name, String icon, List<String> description, Effect effect, double perLevel, List<Long> prices) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.effect = effect;
            this.perLevel = perLevel;
            this.prices = prices;
        }

        public int maxLevel() {
            return prices.size();
        }

        public long priceFor(int level) {
            return level >= prices.size() ? -1 : prices.get(level);
        }
    }

    int prestigeMax = 10;
    long prestigePrice = 1_000_000;
    double prestigeGrowth = 2.0;
    int prestigeLevel = 0;
    double prestigeBonus = 10;
    int rebirthPrestige = 10;
    long rebirthPrice = 50_000_000;
    double rebirthGrowth = 3.0;
    double rebirthBonus = 50;
    int rebirthMax = 0;
    long autosellPrice = 250_000;
    double autosellRate = 1.0;
    long backpackPrice = 100_000;
    int backpackSize = 27;
    int backpackLargeSize = 54;
    private final Map<String, IslandUpgrade> upgrades = new LinkedHashMap<>();

    /** @param maxIslandLevel a földek száma: a prestige-hez alapból mindnek nyitva kell lennie */
    public void load(ConfigurationSection root, int maxIslandLevel, List<String> problems) {
        prestigeMax = clamp(root.getInt("prestige.max", 10), 1, 1000);
        prestigePrice = Math.max(0, root.getLong("prestige.price", 1_000_000));
        prestigeGrowth = Math.max(1.0, Math.min(10.0, root.getDouble("prestige.price-multiplier", 2.0)));
        int level = root.getInt("prestige.required-level", 0);
        prestigeLevel = level <= 0 ? maxIslandLevel : Math.min(level, maxIslandLevel);
        prestigeBonus = Math.max(0, Math.min(1000, root.getDouble("prestige.bonus", 10)));
        rebirthPrestige = clamp(root.getInt("rebirth.required-prestige", prestigeMax), 1, prestigeMax);
        rebirthPrice = Math.max(0, root.getLong("rebirth.price", 50_000_000));
        rebirthGrowth = Math.max(1.0, Math.min(10.0, root.getDouble("rebirth.price-multiplier", 3.0)));
        rebirthBonus = Math.max(0, Math.min(10000, root.getDouble("rebirth.bonus", 50)));
        rebirthMax = Math.max(0, root.getInt("rebirth.max", 0));
        autosellPrice = Math.max(0, root.getLong("autosell.price", 250_000));
        autosellRate = Math.max(0.1, Math.min(1.0, root.getDouble("autosell.rate", 1.0)));
        backpackPrice = Math.max(0, root.getLong("backpack.large-price", 100_000));
        backpackSize = roundRows(root.getInt("backpack.size", 27));
        backpackLargeSize = Math.max(backpackSize, roundRows(root.getInt("backpack.large-size", 54)));
        upgrades.clear();
        ConfigurationSection section = root.getConfigurationSection("island-upgrades");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String where = "gens.yml -> island-upgrades." + key;
            Effect effect = Effect.parse(entry.getString("effect", ""));
            if (effect == null) {
                problems.add(where + ".effect: capacity, speed vagy value lehet");
                continue;
            }
            List<Long> prices = new ArrayList<>();
            for (Object value : entry.getList("prices", Collections.emptyList())) {
                try {
                    prices.add(Math.max(0L, Long.parseLong(String.valueOf(value).trim())));
                } catch (NumberFormatException exception) {
                    problems.add(where + ".prices: csak számok lehetnek (" + value + ")");
                }
            }
            if (prices.isEmpty() || prices.size() > 20) {
                problems.add(where + ".prices: 1-20 ár kell");
                continue;
            }
            upgrades.put(key.toLowerCase(Locale.ROOT), new IslandUpgrade(key.toLowerCase(Locale.ROOT), entry.getString("name", key),
                    entry.getString("icon", "ANVIL"), new ArrayList<>(entry.getStringList("description")), effect,
                    Math.max(0, Math.min(100, entry.getDouble("per-level", 10))), prices));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int roundRows(int size) {
        int rows = Math.max(1, Math.min(6, (size + 8) / 9));
        return rows * 9;
    }

    public Map<String, IslandUpgrade> upgrades() {
        return Collections.unmodifiableMap(upgrades);
    }

    /** A következő prestige ára (az eddigiek száma alapján egyre drágább). */
    public long prestigeCost(int done, int rebirths) {
        return cost(prestigePrice, prestigeGrowth, done) * (1L + rebirths);
    }

    public long rebirthCost(int done) {
        return cost(rebirthPrice, rebirthGrowth, done);
    }

    static long cost(long base, double growth, int times) {
        double value = base * Math.pow(growth, Math.max(0, times));
        return value >= Long.MAX_VALUE / 2.0 ? Long.MAX_VALUE / 2 : Math.round(value);
    }

    public boolean canPrestige(int islandLevel, int prestige) {
        return prestige < prestigeMax && islandLevel >= prestigeLevel;
    }

    public boolean canRebirth(int prestige, int rebirth) {
        return prestige >= rebirthPrestige && (rebirthMax <= 0 || rebirth < rebirthMax);
    }

    /** Termelési szorzó a prestige és a rebirth után (ennyiszer több termény jár egy növényért). */
    public double productionMultiplier(int prestige, int rebirth) {
        return (1.0 + prestige * prestigeBonus / 100.0) * (1.0 + rebirth * rebirthBonus / 100.0);
    }

    /** Az adott hatás összesen hány százalék a sziget fejlesztésekből. */
    public double percent(Map<String, Integer> levels, Effect effect) {
        double sum = 0;
        for (IslandUpgrade upgrade : upgrades.values()) {
            if (upgrade.effect == effect) {
                sum += Math.max(0, Math.min(upgrade.maxLevel(), levels.getOrDefault(upgrade.id, 0))) * upgrade.perLevel;
            }
        }
        return sum;
    }

    public long capacity(long base, Map<String, Integer> levels) {
        return Math.max(1L, Math.round(base * (1.0 + percent(levels, Effect.CAPACITY) / 100.0)));
    }

    /** Visszanövési idő milliszekundumban (a gyorsítás legfeljebb 75%). */
    public long intervalMillis(int seconds, Map<String, Integer> levels) {
        double cut = Math.min(75.0, percent(levels, Effect.SPEED));
        return Math.max(500L, Math.round(seconds * 1000.0 * (1.0 - cut / 100.0)));
    }

    public double valueMultiplier(Map<String, Integer> levels) {
        return 1.0 + percent(levels, Effect.VALUE) / 100.0;
    }

    public int prestigeMax() {
        return prestigeMax;
    }

    public int prestigeLevel() {
        return prestigeLevel;
    }

    public int rebirthPrestige() {
        return rebirthPrestige;
    }

    public double prestigeBonus() {
        return prestigeBonus;
    }

    public double rebirthBonus() {
        return rebirthBonus;
    }

    public long autosellPrice() {
        return autosellPrice;
    }

    public double autosellRate() {
        return autosellRate;
    }

    public long backpackPrice() {
        return backpackPrice;
    }

    public int backpackSize(boolean large) {
        return large ? backpackLargeSize : backpackSize;
    }

    public int backpackMax() {
        return Math.max(backpackSize, backpackLargeSize);
    }
}
