package hu.icemine.core.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KaracsonyTest {

    @Test
    void season() {
        assertTrue(Karacsony.szezon(LocalDate.of(2026, 12, 1)));
        assertTrue(Karacsony.szezon(LocalDate.of(2027, 1, 6)));
        assertFalse(Karacsony.szezon(LocalDate.of(2027, 1, 7)));
        assertFalse(Karacsony.szezon(LocalDate.of(2026, 11, 30)));
    }

    @Test
    void overlayRestoresToLobbyBlocks() {
        Map<String, String> overlay = Karacsony.overlay(0, 100, 0);
        assertTrue(overlay.size() > 300, "fa és fények: " + overlay.size());
        Map<String, String> lobby = new HashMap<>();
        new LobbyBuild((x, y, z, d) -> lobby.put(x + ";" + y + ";" + z, d), 0, 100, 0).build();
        int housesTouched = 0;
        for (String k : overlay.keySet()) {
            String orig = lobby.get(k);
            if (orig != null && orig.startsWith("stripped_spruce_log")) {
                housesTouched++;
            }
        }
        assertTrue(housesTouched > 100, "a fények a gerendákon vannak: " + housesTouched);
        assertEquals("end_rod[facing=up]", lobby.get("0;" + (100 + LobbyBuild.PLATFORM + 8) + ";" + LobbyBuild.FOUNTAIN_Z));
    }
}
