package hu.icemine.core.gens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GensProgressTest {

    private static GensProgress shipped(List<String> problems) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                GensProgressTest.class.getResourceAsStream("/gens.yml"), StandardCharsets.UTF_8));
        GensProgress progress = new GensProgress();
        progress.load(config, 6, problems);
        return progress;
    }

    @Test
    void shippedConfigLoads() {
        List<String> problems = new ArrayList<>();
        GensProgress progress = shipped(problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(3, progress.upgrades().size());
        assertEquals(10, progress.prestigeMax());
        assertEquals(6, progress.prestigeLevel(), "0 = a legmagasabb sziget szint");
        assertEquals(27, progress.backpackSize(false));
        assertEquals(54, progress.backpackSize(true));
    }

    @Test
    void prestigeGetsMoreExpensiveAndRebirthMultipliesIt() {
        GensProgress progress = shipped(new ArrayList<>());
        long first = progress.prestigeCost(0, 0);
        long second = progress.prestigeCost(1, 0);
        assertEquals(2_000_000L, first);
        assertEquals(3_600_000L, second);
        assertEquals(first * 2, progress.prestigeCost(0, 1), "rebirth után drágább a prestige");
        assertTrue(progress.rebirthCost(1) > progress.rebirthCost(0));
    }

    @Test
    void requirements() {
        GensProgress progress = shipped(new ArrayList<>());
        assertFalse(progress.canPrestige(5, 0));
        assertTrue(progress.canPrestige(6, 0));
        assertFalse(progress.canPrestige(6, 10));
        assertFalse(progress.canRebirth(9, 0));
        assertTrue(progress.canRebirth(10, 0));
    }

    @Test
    void multipliersStack() {
        GensProgress progress = shipped(new ArrayList<>());
        assertEquals(1.0, progress.productionMultiplier(0, 0), 1e-9);
        assertEquals(1.15, progress.productionMultiplier(1, 0), 1e-9);
        assertEquals(1.75, progress.productionMultiplier(0, 1), 1e-9);
        assertEquals(2.5 * 1.75, progress.productionMultiplier(10, 1), 1e-9);
        assertEquals(6, progress.extraSlots(2));
    }

    @Test
    void islandUpgradesChangeCapacitySpeedAndValue() {
        GensProgress progress = shipped(new ArrayList<>());
        Map<String, Integer> none = Map.of();
        Map<String, Integer> some = Map.of("tarolo", 2, "sebesseg", 5, "ertek", 1);
        assertEquals(64, progress.capacity(64, none));
        assertEquals(96, progress.capacity(64, some));
        assertEquals(6000, progress.intervalMillis(6, none));
        assertEquals(3600, progress.intervalMillis(6, some));
        assertEquals(1.06, progress.valueMultiplier(some), 1e-9);
        assertEquals(64 * 2.25, progress.capacity(64, Map.of("tarolo", 99)), 1e-9, "a szint nem lehet több a maximumnál");
    }

    @Test
    void speedIsCapped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("island-upgrades.x.effect", "speed");
        config.set("island-upgrades.x.per-level", 50);
        config.set("island-upgrades.x.prices", List.of(1, 1, 1));
        GensProgress progress = new GensProgress();
        progress.load(config, 6, new ArrayList<>());
        assertEquals(1500, progress.intervalMillis(6, Map.of("x", 3)), "legfeljebb 75% gyorsítás");
        assertEquals(500, progress.intervalMillis(1, Map.of("x", 3)));
    }

    @Test
    void memberPermissionsRoundTrip() {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        UUID helper = UUID.randomUUID();
        GensData.Player data = new GensData.Player(owner, 1, 1);
        data.trusted.add(builder);
        data.trusted.add(helper);
        data.permissions.put(helper, GensData.COLLECT | GensData.MANAGE);
        GensData.Player copy = new GensData.Player(owner, 1, 1);
        copy.decodeTrusted(data.encodeTrusted());
        assertTrue(copy.can(owner, GensData.MANAGE));
        assertTrue(copy.can(builder, GensData.BUILD));
        assertFalse(copy.can(builder, GensData.MANAGE));
        assertTrue(copy.can(helper, GensData.MANAGE));
        assertFalse(copy.can(helper, GensData.BUILD));
        assertFalse(copy.can(UUID.randomUUID(), GensData.BUILD));
    }

    @Test
    void oldTrustedFormatStillWorks() {
        UUID member = UUID.randomUUID();
        GensData.Player data = new GensData.Player(UUID.randomUUID(), 1, 1);
        data.decodeTrusted(member.toString());
        assertEquals(GensData.DEFAULT_MEMBER, data.permissionsOf(member));
        assertEquals(member.toString(), data.encodeTrusted());
    }
}
