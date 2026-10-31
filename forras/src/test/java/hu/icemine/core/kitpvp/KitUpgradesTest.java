package hu.icemine.core.kitpvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KitUpgradesTest {

    @Test
    void shippedUpgradesLoad() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                KitUpgradesTest.class.getResourceAsStream("/kitpvp.yml"), StandardCharsets.UTF_8));
        KitUpgrades upgrades = new KitUpgrades(null);
        List<String> problems = new ArrayList<>();
        upgrades.load(config.getConfigurationSection("upgrades"), "kristaly", problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(5, upgrades.all().size());
        KitUpgrades.Upgrade sharpness = upgrades.all().get("elesseg");
        assertEquals(KitUpgrades.Effect.SHARPNESS, sharpness.effect);
        assertEquals(3, sharpness.maxLevel());
        assertEquals(500L, sharpness.priceFor(0));
        assertEquals(4000L, sharpness.priceFor(2));
        assertEquals(-1L, sharpness.priceFor(3));
        for (KitUpgrades.Effect effect : KitUpgrades.Effect.values()) {
            assertTrue(upgrades.all().values().stream().anyMatch(upgrade -> upgrade.effect == effect), effect + " nincs a fejlesztések között");
        }
    }

    @Test
    void badEntriesAreReported() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("upgrades.a.effect", "repules");
        config.set("upgrades.a.prices", List.of(1));
        config.set("upgrades.b.effect", "health");
        config.set("upgrades.b.prices", List.of());
        config.set("upgrades.c.effect", "reward");
        config.set("upgrades.c.prices", List.of(100, "sok"));
        KitUpgrades upgrades = new KitUpgrades(null);
        List<String> problems = new ArrayList<>();
        upgrades.load(config.getConfigurationSection("upgrades"), "kristaly", problems);
        assertEquals(3, problems.size());
        assertEquals(1, upgrades.all().size());
        assertEquals(1, upgrades.all().get("c").maxLevel());
    }
}
