package hu.icemine.core.combo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComboTrackerTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void hitsOnSameTargetCount() {
        ComboTracker tracker = new ComboTracker(2000, 100);
        assertEquals(1, tracker.hit(a, b, 0).count);
        assertEquals(2, tracker.hit(a, b, 500).count);
        assertEquals(3, tracker.hit(a, b, 1000).count);
        assertEquals(3, tracker.count(a));
    }

    @Test
    void switchingTargetEndsCombo() {
        ComboTracker tracker = new ComboTracker(2000, 100);
        tracker.hit(a, b, 0);
        tracker.hit(a, b, 100);
        ComboTracker.Hit hit = tracker.hit(a, c, 200);
        assertEquals(1, hit.count);
        assertEquals(1, hit.ended.size());
        assertEquals(a, hit.ended.get(0).player);
        assertEquals(2, hit.ended.get(0).count);
    }

    @Test
    void gettingHitEndsOwnCombo() {
        ComboTracker tracker = new ComboTracker(2000, 100);
        tracker.hit(a, b, 0);
        tracker.hit(a, b, 100);
        ComboTracker.Hit hit = tracker.hit(b, a, 200);
        assertEquals(1, hit.count);
        assertEquals(0, tracker.count(a));
        assertEquals(a, hit.ended.get(0).player);
        assertEquals(2, hit.ended.get(0).count);
    }

    @Test
    void timeoutStartsNewCombo() {
        ComboTracker tracker = new ComboTracker(1000, 100);
        tracker.hit(a, b, 0);
        tracker.hit(a, b, 900);
        ComboTracker.Hit hit = tracker.hit(a, b, 1901);
        assertEquals(1, hit.count);
        assertEquals(2, hit.ended.get(0).count);
    }

    @Test
    void countIsCapped() {
        ComboTracker tracker = new ComboTracker(2000, 3);
        for (int i = 0; i < 10; i++) {
            tracker.hit(a, b, i * 10L);
        }
        assertEquals(3, tracker.count(a));
    }

    @Test
    void expireRemovesOldCombos() {
        ComboTracker tracker = new ComboTracker(1000, 100);
        tracker.hit(a, b, 0);
        tracker.hit(c, b, 800);
        List<ComboTracker.Ended> ended = tracker.expire(1500);
        assertEquals(1, ended.size());
        assertEquals(a, ended.get(0).player);
        assertEquals(1, tracker.size());
    }

    @Test
    void endInvolvingClearsBothDirections() {
        ComboTracker tracker = new ComboTracker(2000, 100);
        tracker.hit(a, b, 0);
        tracker.hit(c, a, 10);
        List<ComboTracker.Ended> ended = tracker.endInvolving(a);
        assertEquals(1, ended.size());
        assertEquals(c, ended.get(0).player);
        assertEquals(0, tracker.size());
    }

    @Test
    void endReturnsNullWhenNothingRuns() {
        ComboTracker tracker = new ComboTracker(2000, 100);
        assertNull(tracker.end(a));
        tracker.hit(a, b, 0);
        assertEquals(1, tracker.end(a).count);
    }

    @Test
    void timeoutHasFloor() {
        ComboTracker tracker = new ComboTracker(10, 100);
        tracker.hit(a, b, 0);
        assertTrue(tracker.expire(150).isEmpty());
        assertEquals(1, tracker.expire(201).size());
    }
}
