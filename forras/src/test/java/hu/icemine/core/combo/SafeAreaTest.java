package hu.icemine.core.combo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeAreaTest {

    private static final double[] MIDDLE = {0.5, 50, 0.5, 5, 10};

    @Test
    void standingOnTheRedWoolIsSafe() {
        assertTrue(ComboArenaMode.insideArea(MIDDLE, 0.5, 51, 0.5));
        assertTrue(ComboArenaMode.insideArea(MIDDLE, -1.5, 51, -3.5));
        assertTrue(ComboArenaMode.insideArea(MIDDLE, 4.9, 51, 0.5));
    }

    @Test
    void outsideOrFarAboveIsNotSafe() {
        assertFalse(ComboArenaMode.insideArea(MIDDLE, 6.0, 51, 0.5));
        assertFalse(ComboArenaMode.insideArea(MIDDLE, 0.5, 61, 0.5));
        assertFalse(ComboArenaMode.insideArea(MIDDLE, 0.5, 47, 0.5));
    }
}
