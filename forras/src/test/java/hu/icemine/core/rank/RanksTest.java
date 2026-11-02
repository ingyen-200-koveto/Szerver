package hu.icemine.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RanksTest {

    private static Ranks shipped(List<String> problems) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RanksTest.class.getResourceAsStream("/ranks.yml"), StandardCharsets.UTF_8));
        Ranks ranks = new Ranks(null);
        ranks.load(config, problems);
        return ranks;
    }

    @Test
    void shippedRanksLoadCleanly() {
        List<String> problems = new ArrayList<>();
        Ranks ranks = shipped(problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(15, ranks.all().size());
        assertEquals("default", ranks.base().id);
        assertEquals("tulajdonos", ranks.all().get(14).id);
    }

    @Test
    void freeKitsAreNotBoundAndRankKitsAre() {
        Ranks ranks = shipped(new ArrayList<>());
        assertTrue(ranks.base().kits.isEmpty());
        assertNull(ranks.requiredFor("harcos"));
        assertNull(ranks.requiredFor("combo"));
    }

    @Test
    void strongerRanksInheritEverything() {
        Ranks ranks = shipped(new ArrayList<>());
        Ranks.Rank legend = ranks.byId("yeti");
        assertTrue(legend.granted().contains("icemine.cosmetic.trail.felho"));
        assertTrue(legend.granted().contains("icemine.cosmetic.fly"));
        assertTrue(ranks.byId("tulajdonos").granted().contains("icemine.admin.*"));
        assertTrue(ranks.byId("tulajdonos").granted().contains("icemine.cosmetic.trail.felho"), "a csapat rangjai a Yeti jutalmait is megkapják");
        assertFalse(ranks.byId("bronz").granted().contains("icemine.cosmetic.fly"));
        assertTrue(ranks.byId("iceking").multiplier > ranks.byId("bronz").multiplier);
    }

    @Test
    void badGroupNamesAreReported() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("ranks.default.group", "default");
        config.set("ranks.vipp.group", "VIP+");
        List<String> problems = new ArrayList<>();
        new Ranks(null).load(config, problems);
        assertEquals(1, problems.size());
    }
}
