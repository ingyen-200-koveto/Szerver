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
        assertEquals(12, ranks.all().size());
        assertEquals("default", ranks.base().id);
        assertEquals("owner", ranks.all().get(11).id);
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
        Ranks.Rank legend = ranks.byId("legend");
        assertTrue(legend.granted().contains("icemine.cosmetic.trail.felho"));
        assertTrue(legend.granted().contains("icemine.cosmetic.fly"));
        assertTrue(ranks.byId("owner").granted().contains("icemine.admin.*"));
        assertFalse(ranks.byId("vip").granted().contains("icemine.cosmetic.fly"));
        assertTrue(ranks.byId("mvpplus").multiplier > ranks.byId("vip").multiplier);
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
