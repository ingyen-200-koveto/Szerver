package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * A kingdoms.yml számai, beolvasva és ellenőrizve. Csak adatot tárol, Bukkit nélkül is tesztelhető.
 * A viselkedés (mit csinál egy torony vagy építmény) a kódban van, innen csak a számokat kapja.
 */
public final class KConfig {

    /** Egy fejleszthető dolog: szintek, ár (alapár × szorzó^szint), és hogy szintenként mennyit ad. */
    public static final class Upgrade {
        public final String id;
        public final String name;
        public final String icon;
        public final int max;
        public final long cost;
        public final double growth;
        public final double perLevel;
        public final int minLevel;
        public final List<String> description;

        public Upgrade(String id, String name, String icon, int max, long cost, double growth, double perLevel, int minLevel, List<String> description) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.max = max;
            this.cost = cost;
            this.growth = growth;
            this.perLevel = perLevel;
            this.minLevel = minLevel;
            this.description = description;
        }

        /** A következő szint ára Resource Pointban, vagy -1, ha már maximum. */
        public long priceFor(int level) {
            if (level >= max) {
                return -1;
            }
            double value = cost * Math.pow(growth, Math.max(0, level));
            return value >= Long.MAX_VALUE / 2.0 ? Long.MAX_VALUE / 2 : Math.round(value);
        }

        public double value(int level) {
            return Math.min(level, max) * perLevel;
        }
    }

    /** Egy lerakható építmény vagy torony fajta. */
    public static final class Placeable {
        public final String id;
        public final String name;
        public final String block;
        public final long price;
        public final int max;
        public final long upgradeCost;
        public final double upgradeGrowth;
        public final int minLevel;
        public final int limit;
        /** Fajtánként más: hatótáv, sebzés, termelés stb. (a kód a kulcs alapján olvassa). */
        public final Map<String, Double> values;
        public final List<String> description;

        public Placeable(String id, String name, String block, long price, int max, long upgradeCost, double upgradeGrowth, int minLevel, int limit,
                         Map<String, Double> values, List<String> description) {
            this.id = id;
            this.name = name;
            this.block = block;
            this.price = price;
            this.max = max;
            this.upgradeCost = upgradeCost;
            this.upgradeGrowth = upgradeGrowth;
            this.minLevel = minLevel;
            this.limit = limit;
            this.values = values;
            this.description = description;
        }

        public long upgradePrice(int level) {
            if (level >= max) {
                return -1;
            }
            return Math.round(upgradeCost * Math.pow(upgradeGrowth, Math.max(0, level - 1)));
        }

        /** Egy érték az adott szinten: alap + (szint - 1) × "<kulcs>-per-level". */
        public double get(String key, int level) {
            return values.getOrDefault(key, 0.0) + Math.max(0, level - 1) * values.getOrDefault(key + "-per-level", 0.0);
        }
    }

    // ---- általános ----
    public int nameMin = 3;
    public int nameMax = 16;
    public int maxMembersBase = 10;
    public int maxMembersPerLevel = 2;
    public String currency = "";
    public int inviteSeconds = 300;
    public boolean friendlyFire = false;

    // ---- szintek ----
    public List<Long> levels = new ArrayList<>(List.of(0L, 1000L, 3000L, 7000L, 15000L, 30000L, 60000L, 100000L, 160000L, 250000L));
    public long xpPerResourcePoint = 1;
    public long xpPerClaim = 10;
    public long xpPerKill = 20;
    public long xpPerInvasion = 500;

    // ---- földek ----
    public int claimsBase = 6;
    public int claimsPerMember = 2;
    public int claimsPerLevel = 3;
    public int claimsPerUpgrade = 5;
    public int freeClaims = 3;
    public long claimCostRp = 25;
    public long claimCostMoney = 0;
    public boolean claimAdjacent = true;
    public int claimBuffer = 1;
    public long unclaimRefundPercent = 50;
    public boolean protectExplosions = true;
    public boolean landTitles = true;

    // ---- power ----
    public boolean powerEnabled = true;
    public double powerMax = 10;
    public double powerMin = -5;
    public double powerInitial = 5;
    public int powerRegenMinutes = 10;
    public double powerRegenAmount = 1;
    public double powerDeathLoss = 2;
    public double claimsPerPower = 2;

    // ---- Resource Point ----
    public final Map<String, Long> itemPoints = new LinkedHashMap<>();
    public long deathPenalty = 10;
    public long moneyPerPoint = 10;

    // ---- invázió ----
    public long invadeCost = 50;
    public int invasionSeconds = 300;
    public int invasionLeaveSeconds = 20;
    public int minOnlineDefenders = 0;
    public long cooldownFailed = 300;
    public long cooldownSuccess = 0;
    public long defeatShieldMinutes = 30;
    public double landLootRp = 0.05;
    public double landLootBank = 0.05;
    public double nexusLootRp = 0.30;
    public double nexusLootBank = 0.25;
    public String championEntity = "ZOMBIE";
    public double championHealth = 100;
    public double championDamage = 6;
    public double championSpeed = 0.25;
    public boolean pacifismEnabled = true;
    public long pacifistCooldownHours = 24;
    public boolean pacifistNoPvp = true;
    public final Map<Integer, Long> shields = new LinkedHashMap<>();

    // ---- masswar ----
    public boolean masswarEnabled = true;
    public int masswarIntervalHours = 24;
    public int masswarMinutes = 60;
    public boolean masswarNeutralInvade = true;
    public boolean masswarFree = true;

    // ---- nemzetek ----
    public long nationCost = 50000;
    public int nationMinLevel = 3;
    public int nationMaxKingdoms = 8;

    // ---- tornyok ----
    public boolean turretsTargetMonsters = true;
    public long ammoCost = 1;
    public int ammoPerRefill = 64;

    public final Map<String, Upgrade> misc = new LinkedHashMap<>();
    public final Map<String, Upgrade> powerups = new LinkedHashMap<>();
    public final Map<String, Upgrade> champion = new LinkedHashMap<>();
    public final Map<String, Placeable> structures = new LinkedHashMap<>();
    public final Map<String, Placeable> turrets = new LinkedHashMap<>();
    public final List<Rank> defaultRanks = new ArrayList<>(Rank.defaults());
    public final Map<String, Long> outpost = new LinkedHashMap<>();

    public void load(ConfigurationSection root, List<String> problems) {
        if (root == null) {
            defaults();
            return;
        }
        nameMin = clamp(root.getInt("name.min", 3), 1, 32);
        nameMax = clamp(root.getInt("name.max", 16), nameMin, 32);
        maxMembersBase = clamp(root.getInt("members.base", 10), 1, 1000);
        maxMembersPerLevel = clamp(root.getInt("members.per-level", 2), 0, 100);
        currency = root.getString("bank.currency", "");
        inviteSeconds = clamp(root.getInt("invite-seconds", 300), 10, 86400);
        friendlyFire = root.getBoolean("friendly-fire", false);

        List<Long> read = root.getLongList("levels.xp");
        if (!read.isEmpty()) {
            levels = new ArrayList<>(read);
            Collections.sort(levels);
            if (levels.get(0) != 0) {
                levels.add(0, 0L);
            }
        }
        xpPerResourcePoint = Math.max(0, root.getLong("levels.xp-per-resource-point", 1));
        xpPerClaim = Math.max(0, root.getLong("levels.xp-per-claim", 10));
        xpPerKill = Math.max(0, root.getLong("levels.xp-per-kill", 20));
        xpPerInvasion = Math.max(0, root.getLong("levels.xp-per-invasion", 500));

        claimsBase = clamp(root.getInt("claims.base", 6), 0, 100000);
        claimsPerMember = clamp(root.getInt("claims.per-member", 2), 0, 1000);
        claimsPerLevel = clamp(root.getInt("claims.per-level", 3), 0, 1000);
        claimsPerUpgrade = clamp(root.getInt("claims.per-upgrade", 5), 0, 1000);
        freeClaims = clamp(root.getInt("claims.free", 3), 0, 100000);
        claimCostRp = Math.max(0, root.getLong("claims.cost.resource-points", 25));
        claimCostMoney = Math.max(0, root.getLong("claims.cost.bank", 0));
        claimAdjacent = root.getBoolean("claims.adjacent", true);
        claimBuffer = clamp(root.getInt("claims.buffer", 1), 0, 10);
        unclaimRefundPercent = Math.max(0, Math.min(100, root.getLong("claims.unclaim-refund-percent", 50)));
        protectExplosions = root.getBoolean("claims.protect-explosions", true);
        landTitles = root.getBoolean("claims.titles", true);

        powerEnabled = root.getBoolean("power.enabled", true);
        powerMax = root.getDouble("power.max", 10);
        powerMin = Math.min(powerMax, root.getDouble("power.min", -5));
        powerInitial = Math.max(powerMin, Math.min(powerMax, root.getDouble("power.initial", 5)));
        powerRegenMinutes = clamp(root.getInt("power.regen-minutes", 10), 1, 1440);
        powerRegenAmount = Math.max(0, root.getDouble("power.regen-amount", 1));
        powerDeathLoss = Math.max(0, root.getDouble("power.death-loss", 2));
        claimsPerPower = Math.max(0.1, root.getDouble("power.claims-per-power", 2));

        itemPoints.clear();
        ConfigurationSection items = section(root, "resource-points.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                long value = items.getLong(key);
                if (value > 0) {
                    itemPoints.put(key.toUpperCase(Locale.ROOT), value);
                }
            }
        }
        if (itemPoints.isEmpty()) {
            defaultItems();
        }
        deathPenalty = Math.max(0, root.getLong("resource-points.death-penalty", 10));
        moneyPerPoint = Math.max(1, root.getLong("resource-points.money-per-point", 10));

        invadeCost = Math.max(0, root.getLong("invasions.cost", 50));
        invasionSeconds = clamp(root.getInt("invasions.time-seconds", 300), 30, 3600);
        invasionLeaveSeconds = clamp(root.getInt("invasions.leave-seconds", 20), 5, 300);
        minOnlineDefenders = clamp(root.getInt("invasions.min-online-defenders", 0), 0, 100);
        cooldownFailed = Math.max(0, root.getLong("invasions.cooldown-failed-seconds", 300));
        cooldownSuccess = Math.max(0, root.getLong("invasions.cooldown-success-seconds", 0));
        defeatShieldMinutes = Math.max(0, root.getLong("invasions.defeat-shield-minutes", 30));
        landLootRp = fraction(root.getDouble("invasions.loot.land.resource-points", 0.05));
        landLootBank = fraction(root.getDouble("invasions.loot.land.bank", 0.05));
        nexusLootRp = fraction(root.getDouble("invasions.loot.nexus.resource-points", 0.30));
        nexusLootBank = fraction(root.getDouble("invasions.loot.nexus.bank", 0.25));
        championEntity = root.getString("invasions.champion.entity", "ZOMBIE").toUpperCase(Locale.ROOT);
        championHealth = Math.max(10, root.getDouble("invasions.champion.health", 100));
        championDamage = Math.max(1, root.getDouble("invasions.champion.damage", 6));
        championSpeed = Math.max(0.1, Math.min(0.6, root.getDouble("invasions.champion.speed", 0.25)));
        pacifismEnabled = root.getBoolean("pacifism.enabled", true);
        pacifistCooldownHours = Math.max(0, root.getLong("pacifism.cooldown-hours", 24));
        pacifistNoPvp = root.getBoolean("pacifism.disable-pvp", true);
        shields.clear();
        ConfigurationSection shieldSection = section(root, "shields");
        if (shieldSection != null) {
            for (String key : shieldSection.getKeys(false)) {
                try {
                    shields.put(Integer.parseInt(key.trim()), Math.max(0, shieldSection.getLong(key)));
                } catch (NumberFormatException exception) {
                    problems.add("kingdoms.yml -> shields." + key + ": a kulcs az órák száma legyen (pl. 6, 12, 24)");
                }
            }
        }
        if (shields.isEmpty()) {
            shields.put(6, 300L);
            shields.put(12, 550L);
            shields.put(24, 1000L);
        }

        masswarEnabled = root.getBoolean("masswar.enabled", true);
        masswarIntervalHours = clamp(root.getInt("masswar.interval-hours", 24), 1, 24 * 14);
        masswarMinutes = clamp(root.getInt("masswar.minutes", 60), 5, 24 * 60);
        masswarNeutralInvade = root.getBoolean("masswar.neutral-invade", true);
        masswarFree = root.getBoolean("masswar.free-invasions", true);

        nationCost = Math.max(0, root.getLong("nations.cost", 50000));
        nationMinLevel = clamp(root.getInt("nations.min-level", 3), 1, 100);
        nationMaxKingdoms = clamp(root.getInt("nations.max-kingdoms", 8), 2, 100);

        turretsTargetMonsters = root.getBoolean("turret-settings.target-monsters", true);
        ammoCost = Math.max(0, root.getLong("turret-settings.ammo-cost", 1));
        ammoPerRefill = clamp(root.getInt("turret-settings.ammo-per-refill", 64), 1, 10000);

        readUpgrades(section(root, "misc-upgrades"), misc, defaultMisc(), problems, "misc-upgrades");
        readUpgrades(section(root, "powerups"), powerups, defaultPowerups(), problems, "powerups");
        readUpgrades(section(root, "champion-upgrades"), champion, defaultChampion(), problems, "champion-upgrades");
        readPlaceables(section(root, "structures"), structures, defaultStructures(), problems, "structures");
        readPlaceables(section(root, "turrets"), turrets, defaultTurrets(), problems, "turrets");

        defaultRanks.clear();
        ConfigurationSection ranks = section(root, "default-ranks");
        Map<String, Rank> builtIn = new LinkedHashMap<>();
        for (Rank rank : Rank.defaults()) {
            builtIn.put(rank.id, rank);
        }
        if (ranks != null) {
            for (String key : ranks.getKeys(false)) {
                ConfigurationSection entry = child(ranks, key);
                if (entry != null) {
                    String id = key.toLowerCase(Locale.ROOT);
                    defaultRanks.add(Rank.read(id, entry, builtIn.get(id)));
                }
            }
        }
        boolean king = false;
        for (Rank rank : defaultRanks) {
            king |= rank.king();
        }
        if (!king) {
            if (ranks != null) {
                problems.add("kingdoms.yml -> default-ranks: kell egy \"king\" rang, az alap rangokat használom");
            }
            defaultRanks.clear();
            defaultRanks.addAll(Rank.defaults());
        }
        if (defaultRanks.size() < 2) {
            problems.add("kingdoms.yml -> default-ranks: legalább két rang kell (király és tag)");
            defaultRanks.clear();
            defaultRanks.addAll(Rank.defaults());
        }

        outpost.clear();
        ConfigurationSection shop = section(root, "outpost");
        if (shop != null) {
            for (String key : shop.getKeys(false)) {
                long price = shop.getLong(key);
                if (price > 0) {
                    outpost.put(key.toUpperCase(Locale.ROOT), price);
                }
            }
        }
        if (outpost.isEmpty()) {
            defaultOutpost();
        }
        Relation.load(section(root, "relations"));
    }

    /**
     * A config része: ha a fájlban megvan, onnan, különben a jarban lévő alapból. (A Bukkit getConfigurationSection
     * a hiányzó részeknél üres szekciót hozna létre a fájlban, azt kerüljük el így.)
     */
    static ConfigurationSection section(ConfigurationSection root, String path) {
        if (root == null) {
            return null;
        }
        if (root.contains(path, true)) {
            return root.getConfigurationSection(path);
        }
        org.bukkit.configuration.Configuration defaults = root.getRoot() == null ? null : root.getRoot().getDefaults();
        return defaults == null ? null : defaults.getConfigurationSection(path);
    }

    private static ConfigurationSection child(ConfigurationSection section, String key) {
        return section != null && section.contains(key, true) ? section.getConfigurationSection(key) : null;
    }

    private void defaults() {
        defaultItems();
        readUpgrades(null, misc, defaultMisc(), new ArrayList<>(), "");
        readUpgrades(null, powerups, defaultPowerups(), new ArrayList<>(), "");
        readUpgrades(null, champion, defaultChampion(), new ArrayList<>(), "");
        readPlaceables(null, structures, defaultStructures(), new ArrayList<>(), "");
        readPlaceables(null, turrets, defaultTurrets(), new ArrayList<>(), "");
        defaultOutpost();
        shields.put(6, 300L);
        shields.put(12, 550L);
        shields.put(24, 1000L);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double fraction(double value) {
        return Math.max(0, Math.min(1, value));
    }

    // ---- szintek ----

    /** A királyság szintje az XP alapján (1-től). */
    public int level(long xp) {
        int level = 1;
        for (int i = 1; i < levels.size(); i++) {
            if (xp >= levels.get(i)) {
                level = i + 1;
            }
        }
        return level;
    }

    /** A következő szinthez szükséges XP, vagy -1, ha már a legmagasabb. */
    public long nextLevelXp(long xp) {
        int level = level(xp);
        return level >= levels.size() ? -1 : levels.get(level);
    }

    public int maxLevel() {
        return levels.size();
    }

    // ---- alapértékek ----

    private void defaultItems() {
        String[][] values = {{"COAL", "2"}, {"CHARCOAL", "1"}, {"COPPER_INGOT", "3"}, {"IRON_INGOT", "10"}, {"GOLD_INGOT", "15"},
                {"LAPIS_LAZULI", "4"}, {"REDSTONE", "2"}, {"DIAMOND", "50"}, {"EMERALD", "60"}, {"NETHERITE_SCRAP", "150"},
                {"NETHERITE_INGOT", "650"}, {"AMETHYST_SHARD", "3"}, {"QUARTZ", "3"}, {"BLAZE_ROD", "25"}, {"ENDER_PEARL", "15"},
                {"COAL_BLOCK", "18"}, {"IRON_BLOCK", "90"}, {"GOLD_BLOCK", "135"}, {"LAPIS_BLOCK", "36"}, {"REDSTONE_BLOCK", "18"},
                {"DIAMOND_BLOCK", "450"}, {"EMERALD_BLOCK", "540"}, {"COPPER_BLOCK", "27"}, {"WHEAT", "1"}, {"BONE", "1"},
                {"GUNPOWDER", "2"}, {"STRING", "1"}, {"LEATHER", "2"}, {"SLIME_BALL", "3"}};
        for (String[] value : values) {
            itemPoints.put(value[0], Long.parseLong(value[1]));
        }
    }

    private void defaultOutpost() {
        String[][] values = {{"GOLDEN_APPLE", "60"}, {"ENDER_PEARL", "40"}, {"EXPERIENCE_BOTTLE", "20"}, {"ARROW", "2"}, {"TNT", "30"},
                {"COBWEB", "15"}, {"OBSIDIAN", "12"}, {"IRON_INGOT", "25"}, {"BLAZE_ROD", "45"}, {"SADDLE", "80"}};
        for (String[] value : values) {
            outpost.put(value[0], Long.parseLong(value[1]));
        }
    }

    private static Upgrade up(String id, String name, String icon, int max, long cost, double growth, double perLevel, int minLevel, String... lines) {
        return new Upgrade(id, name, icon, max, cost, growth, perLevel, minLevel, List.of(lines));
    }

    static List<Upgrade> defaultMisc() {
        return List.of(
                up("max-claims", "&a&lTöbb föld", "GRASS_BLOCK", 10, 300, 1.6, 1, 1, "&7Szintenként +5 foglalható chunk."),
                up("max-members", "&b&lNagyobb létszám", "PLAYER_HEAD", 5, 500, 1.8, 2, 2, "&7Szintenként +2 tag fér be."),
                up("max-turrets", "&c&lTöbb torony", "DISPENSER", 5, 400, 1.7, 2, 2, "&7Szintenként +2 torony földenként."),
                up("anti-explosion", "&6&lRobbanásvédelem", "TNT", 1, 800, 1, 1, 2, "&7A földjeiteken semmi nem robban szét,", "&7még invázió közben sem."),
                up("anti-trample", "&e&lTaposásvédelem", "FARMLAND", 1, 150, 1, 1, 1, "&7Senki nem tapossa le a szántóföldet."),
                up("nexus-guards", "&4&lNexus őrök", "IRON_SWORD", 3, 600, 1.9, 1, 3, "&7Invázió közben a Nexus földjén", "&7szintenként +1 őr segít a bajnoknak."),
                up("glory", "&d&lDicsőség", "EXPERIENCE_BOTTLE", 5, 350, 1.7, 10, 1, "&7Szintenként +10% királysági XP."),
                up("invasion-alert", "&f&lRiadó", "BELL", 1, 200, 1, 1, 1, "&7Ha valaki inváziót indít ellenetek,", "&7minden tag címet és hangot kap."));
    }

    static List<Upgrade> defaultPowerups() {
        return List.of(
                up("damage-boost", "&c&lErősebb ütés", "DIAMOND_SWORD", 20, 100, 1.25, 1, 1, "&7Szintenként +1% sebzés a saját földeteken."),
                up("damage-reduction", "&9&lVastagabb bőr", "DIAMOND_CHESTPLATE", 20, 100, 1.25, 1, 1, "&7Szintenként -1% kapott sebzés", "&7a saját földeteken."),
                up("arrow-boost", "&e&lÉles nyílvessző", "BOW", 20, 70, 1.25, 1, 1, "&7Szintenként +1% íjsebzés a saját földeteken."),
                up("regeneration-boost", "&d&lGyors gyógyulás", "GOLDEN_APPLE", 20, 70, 1.25, 2, 1, "&7Szintenként +2% életerő visszatöltés", "&7a saját földeteken."));
    }

    static List<Upgrade> defaultChampion() {
        return List.of(
                up("health", "&c&lÉleterő", "RED_DYE", 20, 80, 1.22, 15, 1, "&7Szintenként +15 életerő a bajnoknak."),
                up("damage", "&4&lSebzés", "IRON_SWORD", 15, 100, 1.25, 1, 1, "&7Szintenként +1 sebzés."),
                up("armor", "&7&lPáncél", "IRON_CHESTPLATE", 5, 250, 1.8, 1, 1, "&7Bőr, lánc, vas, gyémánt, végül netherit."),
                up("speed", "&b&lGyorsaság", "SUGAR", 5, 200, 1.6, 0.02, 2, "&7Szintenként gyorsabb a bajnok."),
                up("resistance", "&8&lHátrálás ellen", "SHIELD", 5, 180, 1.6, 0.2, 2, "&7Szintenként 20%-kal kevésbé lökhető."),
                up("thor", "&e&lVillám", "LIGHTNING_ROD", 3, 500, 1.8, 1, 3, "&7Néha villámot szór a közelben", "&7lévő támadókra."),
                up("reinforcements", "&2&lErősítés", "ZOMBIE_HEAD", 3, 450, 1.8, 1, 3, "&7Ha megsebesül, segítőket hív."),
                up("drag", "&5&lVisszarántás", "LEAD", 3, 300, 1.7, 1, 2, "&7Aki elfut tőle, azt visszarántja."),
                up("determination", "&6&lElszántság", "TOTEM_OF_UNDYING", 5, 400, 1.7, 3, 4, "&7Egy ütés legfeljebb ennyit sebez rajta:", "&7szintenként egyre kevesebbet."),
                up("mock", "&f&lGúny", "COBWEB", 1, 350, 1, 1, 2, "&7A közelében a támadók nem rakhatnak blokkot."));
    }

    private static Map<String, Double> values(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    private static Placeable place(String id, String name, String block, long price, int max, long upgradeCost, double growth, int minLevel, int limit,
                                   Map<String, Double> values, String... lines) {
        return new Placeable(id, name, block, price, max, upgradeCost, growth, minLevel, limit, values, List.of(lines));
    }

    static List<Placeable> defaultStructures() {
        return List.of(
                place("nexus", "&b&lNexus", "BEACON", 0, 10, 500, 1.6, 1, 1, values("claims-per-level", 2, "champion-health-per-level", 10),
                        "&7A királyság szíve. Jobb klikkre itt van minden menü.", "&7Ha elfoglalják, sok pénz és RP elvész."),
                place("powercell", "&e&lErőmag", "RESPAWN_ANCHOR", 300, 5, 250, 1.6, 2, 6, values("radius", 1, "radius-per-level", 0.5),
                        "&7A körülötte lévő földeket csak akkor", "&7lehet megtámadni, ha előbb az Erőmag", "&7földjét foglalják el."),
                place("extractor", "&6&lKitermelő", "BLAST_FURNACE", 250, 5, 200, 1.6, 1, 4, values("points", 5, "points-per-level", 4, "minutes", 10, "capacity", 100, "capacity-per-level", 80),
                        "&7Magától Resource Pointot termel.", "&7Jobb klikkel szeded be."),
                place("regenerator", "&d&lGyógyforrás", "CONDUIT", 350, 5, 250, 1.6, 2, 3, values("radius", 8, "radius-per-level", 2, "heal", 1, "heal-per-level", 0.5),
                        "&7A közelében a tagok és a szövetségesek", "&7lassan visszagyógyulnak."),
                place("bastion", "&8&lBástya", "LODESTONE", 400, 5, 300, 1.7, 3, 3, values("radius", 10, "radius-per-level", 2, "strength", 0, "strength-per-level", 0.25),
                        "&7A közelében az ellenség lassul és gyengül,", "&7a tagok pedig ellenállóbbak lesznek."),
                place("outpost", "&a&lKereskedőház", "BARREL", 300, 1, 0, 1, 2, 2, values(),
                        "&7Itt Resource Pointért vásárolhattok", "&7hasznos tárgyakat."),
                place("warppad", "&5&lTeleportkapu", "END_PORTAL_FRAME", 200, 1, 0, 1, 2, 6, values("cooldown", 30),
                        "&7A királyság kapui között lehet vele", "&7teleportálni."));
    }

    static List<Placeable> defaultTurrets() {
        return List.of(
                place("arrow", "&f&lÍjásztorony", "SKELETON_SKULL", 120, 10, 80, 1.35, 1, 0, values("range", 12, "range-per-level", 0.5, "damage", 4, "damage-per-level", 0.5, "cooldown", 20, "cooldown-per-level", -1),
                        "&7Nyilakat lő az ellenségre."),
                place("flame", "&6&lLángszóró", "PIGLIN_HEAD", 200, 10, 120, 1.35, 2, 0, values("range", 8, "range-per-level", 0.4, "fire", 60, "fire-per-level", 10, "damage", 2, "damage-per-level", 0.3, "cooldown", 30, "cooldown-per-level", -1),
                        "&7Felgyújtja a közelben lévő ellenséget."),
                place("cannon", "&c&lÁgyú", "CREEPER_HEAD", 350, 10, 200, 1.4, 3, 0, values("range", 16, "range-per-level", 0.5, "power", 1.5, "power-per-level", 0.15, "cooldown", 80, "cooldown-per-level", -3),
                        "&7Robbanó lövedékkel lő (a blokkokat nem bántja)."),
                place("magic", "&5&lVarázstorony", "WITHER_SKELETON_SKULL", 400, 10, 220, 1.4, 4, 0, values("range", 14, "range-per-level", 0.5, "damage", 3, "damage-per-level", 0.4, "cooldown", 50, "cooldown-per-level", -2),
                        "&7Lassít, gyengít és sebez."),
                place("soldier", "&2&lKaszárnya", "ZOMBIE_HEAD", 300, 10, 180, 1.4, 3, 0, values("range", 14, "range-per-level", 0.5, "soldiers", 1, "soldiers-per-level", 0.34, "cooldown", 200, "cooldown-per-level", -8),
                        "&7Katonákat küld az ellenségre."),
                place("healing", "&a&lGyógytorony", "DRAGON_HEAD", 250, 10, 150, 1.35, 2, 0, values("range", 8, "range-per-level", 0.5, "heal", 2, "heal-per-level", 0.3, "cooldown", 40, "cooldown-per-level", -1),
                        "&7Gyógyítja a közelben lévő tagokat", "&7és szövetségeseket."),
                place("mine", "&7&lAkna", "STONE_PRESSURE_PLATE", 80, 5, 60, 1.4, 1, 0, values("power", 2, "power-per-level", 0.3),
                        "&7Felrobban, ha ellenség lép rá", "&7(a blokkokat nem bántja)."));
    }

    private static void readUpgrades(ConfigurationSection section, Map<String, Upgrade> target, List<Upgrade> defaults, List<String> problems, String path) {
        target.clear();
        for (Upgrade fallback : defaults) {
            ConfigurationSection entry = child(section, fallback.id);
            if (entry == null) {
                target.put(fallback.id, fallback);
                continue;
            }
            if (!entry.getBoolean("enabled", true)) {
                continue;
            }
            List<String> description = entry.getStringList("description");
            target.put(fallback.id, new Upgrade(fallback.id, entry.getString("name", fallback.name), entry.getString("icon", fallback.icon),
                    clamp(entry.getInt("max", fallback.max), 0, 1000), Math.max(0, entry.getLong("cost", fallback.cost)),
                    Math.max(1.0, Math.min(10.0, entry.getDouble("cost-multiplier", fallback.growth))),
                    entry.getDouble("per-level", fallback.perLevel), clamp(entry.getInt("min-level", fallback.minLevel), 1, 1000),
                    description.isEmpty() ? fallback.description : new ArrayList<>(description)));
        }
        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean known = false;
                for (Upgrade fallback : defaults) {
                    known |= fallback.id.equalsIgnoreCase(key);
                }
                if (!known) {
                    problems.add("kingdoms.yml -> " + path + "." + key + ": ismeretlen fejlesztés, kihagyom");
                }
            }
        }
    }

    private static void readPlaceables(ConfigurationSection section, Map<String, Placeable> target, List<Placeable> defaults, List<String> problems, String path) {
        target.clear();
        for (Placeable fallback : defaults) {
            ConfigurationSection entry = child(section, fallback.id);
            if (entry == null) {
                target.put(fallback.id, fallback);
                continue;
            }
            if (!entry.getBoolean("enabled", true) && !fallback.id.equals("nexus")) {
                continue;
            }
            Map<String, Double> values = new LinkedHashMap<>(fallback.values);
            ConfigurationSection numbers = child(entry, "values");
            if (numbers != null) {
                for (String key : numbers.getKeys(false)) {
                    values.put(key, numbers.getDouble(key));
                }
            }
            List<String> description = entry.getStringList("description");
            target.put(fallback.id, new Placeable(fallback.id, entry.getString("name", fallback.name), entry.getString("block", fallback.block),
                    Math.max(0, entry.getLong("price", fallback.price)), clamp(entry.getInt("max-level", fallback.max), 1, 100),
                    Math.max(0, entry.getLong("upgrade-cost", fallback.upgradeCost)),
                    Math.max(1.0, Math.min(10.0, entry.getDouble("upgrade-multiplier", fallback.upgradeGrowth))),
                    clamp(entry.getInt("min-level", fallback.minLevel), 1, 1000), Math.max(0, entry.getInt("limit", fallback.limit)), values,
                    description.isEmpty() ? fallback.description : new ArrayList<>(description)));
        }
        if (section != null) {
            for (String key : section.getKeys(false)) {
                boolean known = false;
                for (Placeable fallback : defaults) {
                    known |= fallback.id.equalsIgnoreCase(key);
                }
                if (!known) {
                    problems.add("kingdoms.yml -> " + path + "." + key + ": ismeretlen fajta, kihagyom");
                }
            }
        }
    }

    /** Csak a tesztekhez és a hibakereséshez: a fejlesztések száma kategóriánként. */
    public Map<String, Integer> sizes() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        sizes.put("misc", misc.size());
        sizes.put("powerups", powerups.size());
        sizes.put("champion", champion.size());
        sizes.put("structures", structures.size());
        sizes.put("turrets", turrets.size());
        return sizes;
    }

    /** Hasznos az EnumMap-es kiírásokhoz: a kapcsolatok jogai táblázatban. */
    public static Map<Relation, Boolean> buildMatrix() {
        Map<Relation, Boolean> map = new EnumMap<>(Relation.class);
        for (Relation relation : Relation.values()) {
            map.put(relation, relation.build);
        }
        return map;
    }
}
