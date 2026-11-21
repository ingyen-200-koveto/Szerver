package hu.icemine.core.anticheat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CsalasGyanuTest {

    @Test
    void alertsAfterThresholdThenWaits() {
        CsalasGyanu.Allapot a = new CsalasGyanu.Allapot();
        UUID id = UUID.randomUUID();
        long t = 1_000_000;
        String json = null;
        for (int i = 0; i < 5; i++) {
            json = CsalasGyanu.lep(a, id, "Gyanus", i % 2 == 0 ? "Simulation" : "Reach", i, t + i * 1000, 300_000, 5, 600_000);
        }
        assertNotNull(json);
        assertTrue(json.contains("\"jatekos\":\"Gyanus\""), json);
        assertTrue(json.contains("\"nev\":\"Simulation\",\"db\":3"), json);
        for (int i = 0; i < 10; i++) {
            assertNull(CsalasGyanu.lep(a, id, "Gyanus", "Reach", 1, t + 10_000 + i * 1000, 300_000, 5, 600_000));
        }
        assertNotNull(CsalasGyanu.lep(a, id, "Gyanus", "Reach", 1, t + 700_000, 300_000, 1, 600_000));
    }

    @Test
    void resetsAfterQuietWindow() {
        CsalasGyanu.Allapot a = new CsalasGyanu.Allapot();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            assertNull(CsalasGyanu.lep(a, id, "X", "Timer", 1, i * 1000L, 300_000, 5, 600_000));
        }
        assertNull(CsalasGyanu.lep(a, id, "X", "Timer", 1, 1_000_000L, 300_000, 5, 600_000));
    }
}
