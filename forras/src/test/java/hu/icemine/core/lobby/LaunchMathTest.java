package hu.icemine.core.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LaunchMathTest {

    private static void lands(double dx, double dy, double dz) {
        double[] velocity = LaunchMath.velocity(dx, dy, dz);
        int ticks = LaunchMath.ticks(Math.sqrt(dx * dx + dz * dz), dy);
        double[] end = LaunchMath.simulate(velocity, ticks);
        assertEquals(dx, end[0], 0.05, "x");
        assertEquals(dy, end[1], 0.05, "y");
        assertEquals(dz, end[2], 0.05, "z");
    }

    @Test
    void reachesTargetUpwards() {
        lands(-3.0, 5.1, 24.0);
        lands(3.0, 5.1, 24.0);
    }

    @Test
    void reachesTargetDownwards() {
        lands(-12.0, -11.9, -24.0);
    }

    @Test
    void reachesShortTarget() {
        lands(0.0, 1.0, 6.0);
    }

    @Test
    void straightUpHasNoSideSpeed() {
        double[] velocity = LaunchMath.velocity(0, 6, 0);
        assertEquals(0.0, velocity[0], 1.0E-9);
        assertEquals(0.0, velocity[2], 1.0E-9);
        assertTrue(velocity[1] > 0);
    }

    @Test
    void speedIsCapped() {
        double[] velocity = LaunchMath.velocity(500, 200, 0);
        assertTrue(velocity[0] <= LaunchMath.MAX_HORIZONTAL + 1.0E-9);
        assertTrue(velocity[1] <= LaunchMath.MAX_VERTICAL + 1.0E-9);
    }

    @Test
    void flightTimeStaysInRange() {
        assertEquals(16, LaunchMath.ticks(0, 0));
        assertEquals(45, LaunchMath.ticks(200, 50));
    }
}
