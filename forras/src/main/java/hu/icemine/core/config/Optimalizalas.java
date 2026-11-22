package hu.icemine.core.config;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A 3.30.0-s teljesítmény beállítások visszavonása: ha a szerveren lefutottak (plugins/IceMine/.optimalizalas-v1),
 * egyszer visszaírja az eredeti Paper, Spigot és Bukkit értékeket és az arénák spawn területét, majd törli a jelzőt.
 */
public final class Optimalizalas {

    static final String MARKER = ".optimalizalas-v1";

    static final Object[][] PAPER = {
            {"chunks.max-auto-save-chunks-per-tick", 24},
            {"chunks.prevent-moving-into-unloaded-chunks", false},
            {"chunks.entity-per-chunk-save-limit.arrow", -1},
            {"chunks.entity-per-chunk-save-limit.experience_orb", -1},
            {"chunks.entity-per-chunk-save-limit.snowball", -1},
            {"chunks.entity-per-chunk-save-limit.ender_pearl", -1},
            {"chunks.entity-per-chunk-save-limit.egg", -1},
            {"chunks.entity-per-chunk-save-limit.fireball", -1},
            {"chunks.entity-per-chunk-save-limit.small_fireball", -1},
            {"collisions.max-entity-collisions", 8},
            {"entities.armor-stands.do-collision-entity-lookups", true},
            {"environment.optimize-explosions", false},
            {"environment.treasure-maps.enabled", true},
            {"hopper.disable-move-event", false},
            {"hopper.ignore-occluding-blocks", false},
            {"misc.redstone-implementation", "VANILLA"},
            {"tick-rates.grass-spread", 1},
            {"tick-rates.mob-spawner", 1},
    };

    static final Object[][] SPIGOT = {
            {"world-settings.default.entity-activation-range.animals", 32},
            {"world-settings.default.entity-activation-range.monsters", 32},
            {"world-settings.default.entity-activation-range.misc", 16},
            {"world-settings.default.entity-activation-range.water", 16},
            {"world-settings.default.entity-activation-range.villagers", 32},
            {"world-settings.default.entity-activation-range.tick-inactive-villagers", true},
            {"world-settings.default.nerf-spawner-mobs", false},
    };

    static final Object[][] BUKKIT = {
            {"chunk-gc.period-in-ticks", 600},
    };

    private Optimalizalas() {
    }

    /** A szerver elindulása után: ha a 3.30.0 átírta a configokat, egyszer visszaállítja őket. */
    public static void visszavon(File serverRoot, File dataFolder, List<String> worlds, Logger logger) {
        File marker = new File(dataFolder, MARKER);
        if (!marker.exists()) {
            return;
        }
        int db = 0;
        db += patch(new File(serverRoot, "config/paper-world-defaults.yml"), PAPER, logger);
        db += patch(new File(serverRoot, "spigot.yml"), SPIGOT, logger);
        db += patch(new File(serverRoot, "bukkit.yml"), BUKKIT, logger);
        spawnVissza(worlds);
        if (marker.delete()) {
            logger.info("A 3.30.0 teljesítmény beállításai visszaállítva (" + db + " érték), a következő indítástól érvényes.");
        }
    }

    /** Csak azokat a kulcsokat írja, amik már léteznek a fájlban (így verzióváltásnál sem rak bele rossz kulcsot). */
    static int patch(File file, Object[][] values, Logger logger) {
        if (!file.isFile()) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int db = 0;
        for (Object[] v : values) {
            String path = (String) v[0];
            if (yaml.contains(path) && !String.valueOf(yaml.get(path)).equals(String.valueOf(v[1]))) {
                yaml.set(path, v[1]);
                db++;
            }
        }
        if (db > 0) {
            try {
                yaml.save(file);
            } catch (java.io.IOException e) {
                logger.warning("Nem tudtam menteni: " + file.getName() + " (" + e.getMessage() + ")");
                return 0;
            }
        }
        return db;
    }

    /** A spawn terület vissza az alapra (spawnChunkRadius = 2), ha 0-ra volt állítva. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void spawnVissza(List<String> worlds) {
        GameRule rule = GameRule.getByName("spawnChunkRadius");
        if (rule == null) {
            return;
        }
        for (String name : worlds) {
            World w = Bukkit.getWorld(name);
            if (w != null && Integer.valueOf(0).equals(w.getGameRuleValue(rule))) {
                w.setGameRule(rule, 2);
            }
        }
    }

    static Map<String, Object> egyszeru(Object[][] values) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (Object[] v : values) {
            m.put((String) v[0], v[1]);
        }
        return m;
    }
}
