package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class QuestBook {

    public static final class Quest {
        public final String id;
        public final String type;
        public final long amount;
        public final long reward;
        public final String name;

        public Quest(String id, String type, long amount, long reward, String name) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.reward = reward;
            this.name = name;
        }
    }

    private final List<Quest> pool = new ArrayList<>();
    private int perDay = 3;

    public void configure(List<Quest> quests, int perDay) {
        pool.clear();
        pool.addAll(quests);
        this.perDay = Math.max(1, perDay);
    }

    public List<Quest> pool() {
        return Collections.unmodifiableList(pool);
    }

    public List<Quest> today(UUID uuid, String day) {
        List<Quest> copy = new ArrayList<>(pool);
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ ((long) day.hashCode() << 16);
        Collections.shuffle(copy, new Random(seed));
        return copy.subList(0, Math.min(perDay, copy.size()));
    }

    public void roll(GensData.Player data, String day) {
        if (!day.equals(data.questDay)) {
            data.questDay = day;
            data.progress.clear();
            data.claimed.clear();
            data.dirty = true;
        }
    }

    public List<Quest> add(GensData.Player data, String day, String type, long amount) {
        roll(data, day);
        List<Quest> completed = new ArrayList<>();
        if (amount <= 0) {
            return completed;
        }
        for (Quest quest : today(data.uuid, day)) {
            if (!quest.type.equals(type) || data.claimed.contains(quest.id)) {
                continue;
            }
            long value = data.progress.getOrDefault(quest.id, 0L) + amount;
            if (value >= quest.amount) {
                data.progress.remove(quest.id);
                data.claimed.add(quest.id);
                completed.add(quest);
            } else {
                data.progress.put(quest.id, value);
            }
            data.dirty = true;
        }
        return completed;
    }

    public long progress(GensData.Player data, Quest quest) {
        if (data.claimed.contains(quest.id)) {
            return quest.amount;
        }
        return Math.min(quest.amount, data.progress.getOrDefault(quest.id, 0L));
    }
}
