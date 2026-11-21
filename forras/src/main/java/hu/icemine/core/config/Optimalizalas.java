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
 * Egyszeri teljesítmény beállítások a Paper, Spigot és Bukkit configban (a következő indítástól érvényesek),
 * és az arénavilágok spawn területének kikapcsolása (üresen ne legyen betöltve). Amit utána kézzel átírsz,
 * azt többet nem bántja (plugins/IceMine/.optimalizalas-v1).
 */
public final class Optimalizalas {

    static final String MARKER = ".optimalizalas-v1";

    static final Object[][] PAPER = {
            {"chunks.max-auto-save-chunks-per-tick", 12},
            {"chunks.prevent-moving-into-unloaded-chunks", true},
            {"chunks.entity-per-chunk-save-limit.arrow", 16},
            {"chunks.entity-per-chunk-save-limit.experience_orb", 16},
            {"chunks.entity-per-chunk-save-limit.snowball", 8},
            {"chunks.entity-per-chunk-save-limit.ender_pearl", 8},
            {"chunks.entity-per-chunk-save-limit.egg", 8},
            {"chunks.entity-per-chunk-save-limit.fireball", 8},
            {"chunks.entity-per-chunk-save-limit.small_fireball", 8},
            {"collisions.max-entity-collisions", 2},
            {"entities.armor-stands.do-collision-entity-lookups", false},
            {"environment.optimize-explosions", true},
            {"environment.treasure-maps.enabled", false},
            {"hopper.disable-move-event", true},
            {"hopper.ignore-occluding-blocks", true},
            {"misc.redstone-implementation", "ALTERNATE_CURRENT"},
            {"tick-rates.grass-spread", 4},
            {"tick-rates.mob-spawner", 2},
    };

    static final Object[][] SPIGOT = {
            {"world-settings.default.entity-activation-range.animals", 16},
            {"world-settings.default.entity-activation-range.monsters", 24},
            {"world-settings.default.entity-activation-range.misc", 8},
            {"world-settings.default.entity-activation-range.water", 8},
            {"world-settings.default.entity-activation-range.villagers", 16},
            {"world-settings.default.entity-activation-range.tick-inactive-villagers", false},
            {"world-settings.default.nerf-spawner-mobs", true},
    };

    static final Object[][] BUKKIT = {
            {"chunk-gc.period-in-ticks", 400},
    };

    private Optimalizalas() {
    }

    /** onLoad-ban: a configok átírása egyszer. */
    public static void configok(File serverRoot, File dataFolder, boolean enabled, Logger logger) {
        File marker = new File(dataFolder, MARKER);
        if (!enabled || marker.exists()) {
            return;
        }
        int db = 0;
        db += patch(new File(serverRoot, "config/paper-world-defaults.yml"), PAPER, logger);
        db += patch(new File(serverRoot, "spigot.yml"), SPIGOT, logger);
        db += patch(new File(serverRoot, "bukkit.yml"), BUKKIT, logger);
        try {
            dataFolder.mkdirs();
            marker.createNewFile();
        } catch (java.io.IOException ignored) {
        }
        if (db > 0) {
            logger.info("Teljesítmény beállítások: " + db + " érték átírva a Paper/Spigot/Bukkit configban, a következő indítástól érvényes.");
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

    /** A megadott világokban nincs mindig betöltött spawn terület (spawnChunkRadius = 0). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void spawnNelkul(List<String> worlds) {
        GameRule rule = GameRule.getByName("spawnChunkRadius");
        if (rule == null) {
            return;
        }
        for (String name : worlds) {
            World w = Bukkit.getWorld(name);
            if (w != null && !Integer.valueOf(0).equals(w.getGameRuleValue(rule))) {
                w.setGameRule(rule, 0);
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
