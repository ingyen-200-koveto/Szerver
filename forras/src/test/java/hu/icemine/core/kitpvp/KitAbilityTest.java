package hu.icemine.core.kitpvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class KitAbilityTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(KitAbilityTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void rankKitsExistInSomeMode() {
        Set<String> known = new HashSet<>();
        for (String file : new String[]{"fullpvp.yml", "comboarena.yml"}) {
            ConfigurationSection kits = resource(file).getConfigurationSection("kits");
            if (kits != null) {
                known.addAll(kits.getKeys(false));
            }
        }
        ConfigurationSection ranks = resource("ranks.yml").getConfigurationSection("ranks");
        int bound = 0;
        for (String rank : ranks.getKeys(false)) {
            for (String kit : ranks.getStringList(rank + ".kits")) {
                assertTrue(known.contains(kit), "ranks.yml -> " + rank + ": ismeretlen kit " + kit);
                bound++;
            }
        }
        assertTrue(bound >= 6);
        assertEquals(0, ranks.getStringList("default.kits").size());
    }

    @Test
    void unknownTypeIsReported() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ability.type", "teleport");
        List<String> problems = new ArrayList<>();
        assertNull(KitAbility.read("x", config.getConfigurationSection("ability"), problems));
        assertEquals(1, problems.size());
        assertNull(KitAbility.read("x", null, problems));
    }

    @Test
    void valuesAreClamped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ability.type", "DASH");
        config.set("ability.cooldown", 0);
        config.set("ability.power", 99);
        KitAbility ability = KitAbility.read("x", config.getConfigurationSection("ability"), new ArrayList<>());
        assertEquals(KitAbility.Type.DASH, ability.type);
        assertEquals(1, ability.cooldown);
        assertEquals(10.0, ability.power, 1e-9);
    }

    @Test
    void cooldownUpgradeShortensButNeverBelowOneSecond() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ability.type", "heal");
        config.set("ability.cooldown", 30);
        KitAbility ability = KitAbility.read("x", config.getConfigurationSection("ability"), new ArrayList<>());
        assertEquals(30000L, ability.cooldownMillis(1.0));
        assertEquals(21000L, ability.cooldownMillis(0.7));
        assertEquals(3000L, ability.cooldownMillis(0.0));
    }

    @Test
    void arrowSpreadRotatesAroundTheVerticalAxis() {
        Vector rotated = AbilityRunner.rotate(new Vector(1, 0.5, 0), Math.PI / 2);
        assertEquals(0.0, rotated.getX(), 1e-9);
        assertEquals(0.5, rotated.getY(), 1e-9);
        assertEquals(1.0, rotated.getZ(), 1e-9);
    }
}
