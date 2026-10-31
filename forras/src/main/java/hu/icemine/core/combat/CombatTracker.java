package hu.icemine.core.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatTracker {

    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, UUID> attackers = new HashMap<>();
    private final Map<UUID, Long> attackedAt = new HashMap<>();
    private long duration;

    public CombatTracker(long durationMillis) {
        this.duration = durationMillis;
    }

    public void duration(long millis) {
        this.duration = Math.max(1000L, millis);
    }

    public long duration() {
        return duration;
    }

    public boolean tag(UUID victim, UUID attacker, long now) {
        boolean fresh = !tagged(victim, now);
        until.put(victim, now + duration);
        if (attacker != null && !attacker.equals(victim)) {
            until.put(attacker, now + duration);
            attackers.put(victim, attacker);
            attackedAt.put(victim, now);
        }
        return fresh;
    }

    public boolean tagged(UUID id, long now) {
        Long end = until.get(id);
        return end != null && end > now;
    }

    public long remaining(UUID id, long now) {
        Long end = until.get(id);
        return end == null ? 0L : Math.max(0L, end - now);
    }

    public UUID lastAttacker(UUID victim, long now, long window) {
        Long time = attackedAt.get(victim);
        if (time == null || now - time > window) {
            return null;
        }
        return attackers.get(victim);
    }

    public void clear(UUID id) {
        until.remove(id);
        attackers.remove(id);
        attackedAt.remove(id);
    }

    public List<UUID> expire(long now) {
        List<UUID> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> iterator = until.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                expired.add(entry.getKey());
                iterator.remove();
            }
        }
        return expired;
    }

    public Set<UUID> active() {
        return until.keySet();
    }
}
