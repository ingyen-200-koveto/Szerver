package hu.icemine.core.combo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockbackTest {

    private static final double EPS = 1.0E-9;
    private final Knockback.Settings settings = new Knockback.Settings(0.4, 0.36, 0.02, 1.2, 0.6, 6.0);

    @Test
    void pushesAwayFromAttacker() {
        double[] v = Knockback.compute(3, 4, 0, 1, settings, 0);
        assertEquals(0.24, v[0], EPS);
        assertEquals(0.36, v[1], EPS);
        assertEquals(0.32, v[2], EPS);
    }

    @Test
    void usesYawWhenPlayersOverlap() {
        double[] v = Knockback.compute(0, 0, 0, 1, settings, 0);
        assertEquals(0.0, v[0], EPS);
        assertEquals(0.4, v[2], EPS);
        double[] w = Knockback.compute(0, 0, Math.PI / 2, 1, settings, 0);
        assertEquals(-0.4, w[0], EPS);
        assertEquals(0.0, w[2], 1.0E-6);
    }

    @Test
    void verticalGrowsWithComboButIsCapped() {
        assertEquals(0.36, Knockback.compute(1, 0, 0, 1, settings, 0)[1], EPS);
        assertEquals(0.46, Knockback.compute(1, 0, 0, 6, settings, 0)[1], EPS);
        assertEquals(0.6, Knockback.compute(1, 0, 0, 100, settings, 0)[1], EPS);
    }

    @Test
    void heightCapStopsSkyLaunch() {
        assertTrue(Knockback.compute(1, 0, 0, 10, settings, 7)[1] <= 0.0);
        assertEquals(0.18, Knockback.compute(1, 0, 0, 1, settings, 4)[1], EPS);
    }

    @Test
    void horizontalIsClamped() {
        Knockback.Settings strong = new Knockback.Settings(5.0, 0.3, 0.0, 1.2, 0.6, 6.0);
        double[] v = Knockback.compute(1, 0, 0, 1, strong, 0);
        assertEquals(1.2, v[0], EPS);
        Knockback.Settings negative = new Knockback.Settings(-1.0, 0.3, 0.0, 1.2, 0.6, 6.0);
        assertEquals(0.0, Knockback.compute(1, 0, 0, 1, negative, 0)[0], EPS);
    }

    private final Knockback.Settings combo = new Knockback.Settings(0.30, 0.40, 0.004, 0.9, 0.55, 7, 0.8, 1.0, 1.0, 1.1, -0.004);

    @Test
    void groundHitLiftsHarderThanAirHit() {
        double[] ground = Knockback.compute(1, 0, 0, 1, combo, 0);
        double[] air = Knockback.compute(1, 0, 0, 1, combo, 1.5);
        assertEquals(0.44, ground[1], EPS);
        assertEquals(0.40, air[1], EPS);
        assertEquals(0.30, ground[0], EPS);
        assertEquals(0.24, air[0], EPS);
    }

    @Test
    void longCombosKeepTheTargetCloseAndHigh() {
        double[] first = Knockback.compute(1, 0, 0, 1, combo, 1.0);
        double[] tenth = Knockback.compute(1, 0, 0, 10, combo, 1.0);
        assertTrue(tenth[0] < first[0], "a hosszú kombónál kisebb az oldallökés");
        assertTrue(tenth[1] > first[1], "a hosszú kombónál nagyobb a felfelé lökés");
        assertTrue(tenth[1] > tenth[0], "felfelé erősebb, mint hátra");
    }

    @Test
    void horizontalNeverGoesBackwards() {
        assertEquals(0.0, Knockback.compute(1, 0, 0, 500, combo, 1.0)[0], EPS);
    }
}
