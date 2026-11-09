package hu.icemine.core.parkour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.parkour.ParkourRun.Step;
import org.junit.jupiter.api.Test;

class ParkourRunTest {

    @Test
    void blocksMustBeReachedInOrder() {
        ParkourRun run = new ParkourRun(0, 56, 14);
        assertEquals(Step.NONE, run.land(1));
        assertEquals(Step.NEXT, run.land(0));
        assertEquals(Step.NONE, run.land(0));
        assertEquals(Step.NEXT, run.land(1));
        assertEquals(1, run.current());
    }

    @Test
    void checkpointsEveryFourteenBlocks() {
        ParkourRun run = new ParkourRun(0, 56, 14);
        assertEquals(3, run.checkpointTotal());
        for (int i = 0; i < 13; i++) {
            assertEquals(Step.NEXT, run.land(i));
        }
        assertEquals(Step.CHECKPOINT, run.land(13));
        assertEquals(1, run.checkpointsReached());
        run.land(14);
        run.land(15);
        run.fell();
        assertEquals(13, run.current());
        assertEquals(1, run.falls());
        assertEquals(Step.NEXT, run.land(14));
        assertTrue(ParkourRun.isCheckpoint(27, 56, 14));
        assertTrue(ParkourRun.isCheckpoint(41, 56, 14));
        assertFalse(ParkourRun.isCheckpoint(55, 56, 14));
    }

    @Test
    void fallingBeforeTheFirstCheckpointGoesBackToTheStart() {
        ParkourRun run = new ParkourRun(0, 56, 14);
        run.land(0);
        run.land(1);
        run.fell();
        assertEquals(-1, run.current());
        assertEquals(Step.NEXT, run.land(0));
    }

    @Test
    void lastBlockFinishes() {
        ParkourRun run = new ParkourRun(0, 3, 14);
        run.land(0);
        run.land(1);
        assertEquals(Step.FINISH, run.land(2));
    }

    @Test
    void elapsedTimeAndPlausibility() {
        ParkourRun run = new ParkourRun(1_000_000_000L, 56, 14);
        assertEquals(1500L, run.elapsed(2_500_000_000L));
        assertEquals(0L, run.elapsed(0L));
        assertTrue(ParkourRun.plausible(30000, 25000));
        assertFalse(ParkourRun.plausible(20000, 25000));
    }
}
