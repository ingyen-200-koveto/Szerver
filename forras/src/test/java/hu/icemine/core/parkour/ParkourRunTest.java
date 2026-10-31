package hu.icemine.core.parkour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.parkour.ParkourRun.Reach;
import org.junit.jupiter.api.Test;

class ParkourRunTest {

    @Test
    void checkpointsMustGoInOrder() {
        ParkourRun run = new ParkourRun(0, 4);
        assertEquals(Reach.SKIPPED, run.reach(1));
        assertEquals(Reach.OK, run.reach(0));
        assertEquals(Reach.ALREADY, run.reach(0));
        assertEquals(Reach.OK, run.reach(1));
        assertEquals(Reach.SKIPPED, run.reach(3));
        assertEquals(1, run.last());
        assertEquals(2, run.reached());
        assertFalse(run.complete());
    }

    @Test
    void completesAfterAllCheckpoints() {
        ParkourRun run = new ParkourRun(0, 3);
        run.reach(0);
        run.reach(1);
        run.reach(2);
        assertTrue(run.complete());
    }

    @Test
    void courseWithoutCheckpointsIsCompleteImmediately() {
        assertTrue(new ParkourRun(0, 0).complete());
        assertTrue(new ParkourRun(0, -3).complete());
    }

    @Test
    void elapsedIsInMillis() {
        ParkourRun run = new ParkourRun(1_000_000_000L, 1);
        assertEquals(1500, run.elapsed(2_500_000_000L));
        assertEquals(0, run.elapsed(0));
    }

    @Test
    void fallsAreCounted() {
        ParkourRun run = new ParkourRun(0, 1);
        run.fell();
        run.fell();
        assertEquals(2, run.falls());
    }

    @Test
    void tooFastRunIsRejected() {
        assertFalse(ParkourRun.plausible(4_999, 5_000));
        assertTrue(ParkourRun.plausible(5_000, 5_000));
    }
}
