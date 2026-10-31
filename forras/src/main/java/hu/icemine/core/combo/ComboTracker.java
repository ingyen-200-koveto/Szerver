package hu.icemine.core.combo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ComboTracker {

    public static final class State {
        public final UUID target;
        public int count;
        public long lastHit;
        public int groundTicks;

        State(UUID target) {
            this.target = target;
        }
    }

    public static final class Ended {
        public final UUID player;
        public final int count;

        Ended(UUID player, int count) {
            this.player = player;
            this.count = count;
        }
    }

    public static final class Hit {
        public final int count;
        public final List<Ended> ended;

        Hit(int count, List<Ended> ended) {
            this.count = count;
            this.ended = ended;
        }
    }

    private final Map<UUID, State> states = new HashMap<>();
    private long timeout;
    private int max;

    public ComboTracker(long timeout, int max) {
        configure(timeout, max);
    }

    public void configure(long timeout, int max) {
        this.timeout = Math.max(200L, timeout);
        this.max = Math.max(1, max);
    }

    public Hit hit(UUID attacker, UUID victim, long now) {
        List<Ended> ended = new ArrayList<>();
        State victimState = states.remove(victim);
        if (victimState != null) {
            ended.add(new Ended(victim, victimState.count));
        }
        State state = states.get(attacker);
        if (state != null && (!state.target.equals(victim) || now - state.lastHit > timeout)) {
            ended.add(new Ended(attacker, state.count));
            state = null;
        }
        if (state == null) {
            state = new State(victim);
            states.put(attacker, state);
        }
        state.count = Math.min(max, state.count + 1);
        state.lastHit = now;
        state.groundTicks = 0;
        return new Hit(state.count, ended);
    }

    public int count(UUID attacker) {
        State state = states.get(attacker);
        return state == null ? 0 : state.count;
    }

    public State state(UUID attacker) {
        return states.get(attacker);
    }

    public List<Ended> expire(long now) {
        List<Ended> ended = new ArrayList<>();
        Iterator<Map.Entry<UUID, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            if (now - entry.getValue().lastHit > timeout) {
                ended.add(new Ended(entry.getKey(), entry.getValue().count));
                iterator.remove();
            }
        }
        return ended;
    }

    public List<Ended> endInvolving(UUID player) {
        List<Ended> ended = new ArrayList<>();
        State own = states.remove(player);
        if (own != null) {
            ended.add(new Ended(player, own.count));
        }
        Iterator<Map.Entry<UUID, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            if (entry.getValue().target.equals(player)) {
                ended.add(new Ended(entry.getKey(), entry.getValue().count));
                iterator.remove();
            }
        }
        return ended;
    }

    public Ended end(UUID attacker) {
        State state = states.remove(attacker);
        return state == null ? null : new Ended(attacker, state.count);
    }

    public Map<UUID, State> view() {
        return Collections.unmodifiableMap(states);
    }

    public int size() {
        return states.size();
    }
}
