package hu.icemine.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WebRanksTest {

    @Test
    void websiteRankNamesMapToGroups() {
        Map<String, String> groups = Map.of("iceking", "iceking", "wizard", "wizard", "abyss", "wizard");
        assertEquals("iceking", WebRanks.target(groups, "IceKing"));
        assertEquals("wizard", WebRanks.target(groups, "Abyss"));
        assertNull(WebRanks.target(groups, "default"));
        assertNull(WebRanks.target(groups, null));
    }
}
