package hu.icemine.core.bedwars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Csapatba osztás a meccs elején: aki választott, oda kerül (ha van hely), a többiek a legkisebb csapatokba. */
public final class Balance {

    private Balance() {
    }

    public static Map<UUID, TeamColor> assign(Map<UUID, TeamColor> wanted, List<TeamColor> colors, int teamSize, Random random) {
        Map<TeamColor, List<UUID>> teams = new LinkedHashMap<>();
        for (TeamColor color : colors) {
            teams.put(color, new ArrayList<>());
        }
        List<UUID> rest = new ArrayList<>();
        for (Map.Entry<UUID, TeamColor> entry : wanted.entrySet()) {
            List<UUID> chosen = entry.getValue() == null ? null : teams.get(entry.getValue());
            if (chosen != null && chosen.size() < teamSize) {
                chosen.add(entry.getKey());
            } else {
                rest.add(entry.getKey());
            }
        }
        Collections.shuffle(rest, random);
        for (UUID id : rest) {
            TeamColor best = null;
            for (TeamColor color : colors) {
                int size = teams.get(color).size();
                if (size < teamSize && (best == null || size < teams.get(best).size())) {
                    best = color;
                }
            }
            if (best != null) {
                teams.get(best).add(id);
            }
        }
        // legalább két csapat legyen, ha legalább ketten vannak (pl. mindenki ugyanazt választotta)
        int used = 0;
        for (List<UUID> members : teams.values()) {
            if (!members.isEmpty()) {
                used++;
            }
        }
        if (used < 2 && wanted.size() >= 2) {
            outer:
            for (TeamColor full : colors) {
                List<UUID> members = teams.get(full);
                if (members.size() >= 2) {
                    for (TeamColor empty : colors) {
                        if (empty != full && teams.get(empty).isEmpty()) {
                            int move = members.size() / 2;
                            for (int i = 0; i < move; i++) {
                                teams.get(empty).add(members.remove(members.size() - 1));
                            }
                            break outer;
                        }
                    }
                }
            }
        }
        Map<UUID, TeamColor> result = new LinkedHashMap<>();
        for (Map.Entry<TeamColor, List<UUID>> entry : teams.entrySet()) {
            for (UUID id : entry.getValue()) {
                result.put(id, entry.getKey());
            }
        }
        return result;
    }
}
