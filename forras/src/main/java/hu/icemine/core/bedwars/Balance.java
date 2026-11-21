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

    /**
     * Partyk: az egy partyban lévők ugyanazt a csapatot kérik (ha elférnek egy csapatban). Ha valamelyikük már
     * választott és ott elférnek, oda mennek, különben a legüresebb csapatba. A wanted térképet módosítja.
     */
    public static void party(Map<UUID, TeamColor> wanted, List<List<UUID>> groups, List<TeamColor> colors, int teamSize) {
        Map<TeamColor, Integer> foglalt = new LinkedHashMap<>();
        for (TeamColor c : colors) {
            foglalt.put(c, 0);
        }
        java.util.Set<UUID> csoportban = new java.util.HashSet<>();
        for (List<UUID> g : groups) {
            csoportban.addAll(g);
        }
        for (Map.Entry<UUID, TeamColor> e : wanted.entrySet()) {
            if (e.getValue() != null && !csoportban.contains(e.getKey()) && foglalt.containsKey(e.getValue())) {
                foglalt.merge(e.getValue(), 1, Integer::sum);
            }
        }
        List<List<UUID>> sorrend = new ArrayList<>(groups);
        sorrend.sort((x, y) -> y.size() - x.size());
        for (List<UUID> g : sorrend) {
            if (g.size() < 2 || g.size() > teamSize) {
                continue;
            }
            TeamColor cel = null;
            for (UUID id : g) {
                TeamColor w = wanted.get(id);
                if (w != null && foglalt.containsKey(w) && foglalt.get(w) + g.size() <= teamSize) {
                    cel = w;
                    break;
                }
            }
            if (cel == null) {
                for (TeamColor c : colors) {
                    if (foglalt.get(c) + g.size() <= teamSize && (cel == null || foglalt.get(c) < foglalt.get(cel))) {
                        cel = c;
                    }
                }
            }
            if (cel == null) {
                continue;
            }
            for (UUID id : g) {
                if (wanted.containsKey(id)) {
                    wanted.put(id, cel);
                }
            }
            foglalt.merge(cel, g.size(), Integer::sum);
        }
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
