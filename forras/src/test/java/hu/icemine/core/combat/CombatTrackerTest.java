package hu.icemine.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatTrackerTest {

    private final UUID victim = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();

    @Test
    void tagsBothPlayers() {
        CombatTracker tracker = new CombatTracker(15_000);
        assertTrue(tracker.tag(victim, attacker, 0));
        assertTrue(tracker.tagged(victim, 1));
        assertTrue(tracker.tagged(attacker, 1));
        assertEquals(15_000, tracker.remaining(victim, 0));
    }

    @Test
    void secondHitIsNotFreshButRefreshes() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, attacker, 0);
        assertFalse(tracker.tag(victim, attacker, 10_000));
        assertTrue(tracker.tagged(victim, 20_000));
        assertFalse(tracker.tagged(victim, 25_000));
    }

    @Test
    void expireReturnsFinishedTags() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, attacker, 0);
        assertTrue(tracker.expire(14_999).isEmpty());
        List<UUID> expired = tracker.expire(15_000);
        assertEquals(2, expired.size());
        assertTrue(tracker.active().isEmpty());
    }

    @Test
    void lastAttackerRespectsWindow() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, attacker, 1000);
        assertEquals(attacker, tracker.lastAttacker(victim, 5000, 10_000));
        assertNull(tracker.lastAttacker(victim, 12_000, 10_000));
        assertNull(tracker.lastAttacker(attacker, 2000, 10_000));
    }

    @Test
    void selfDamageDoesNotCountAsAttacker() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, victim, 0);
        assertTrue(tracker.tagged(victim, 1));
        assertNull(tracker.lastAttacker(victim, 1, 10_000));
    }

    @Test
    void environmentDamageTagsOnlyVictim() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, null, 0);
        assertTrue(tracker.tagged(victim, 1));
        assertEquals(1, tracker.active().size());
    }

    @Test
    void clearRemovesEverything() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.tag(victim, attacker, 0);
        tracker.clear(victim);
        assertFalse(tracker.tagged(victim, 1));
        assertNull(tracker.lastAttacker(victim, 1, 10_000));
        assertEquals(0, tracker.remaining(victim, 1));
    }

    @Test
    void durationHasFloor() {
        CombatTracker tracker = new CombatTracker(15_000);
        tracker.duration(10);
        assertEquals(1000, tracker.duration());
    }
}
