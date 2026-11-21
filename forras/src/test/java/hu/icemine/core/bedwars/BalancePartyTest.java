package hu.icemine.core.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalancePartyTest {

    @Test
    void partyEndsUpInSameTeam() {
        List<TeamColor> colors = List.of(TeamColor.values()).subList(0, 4);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, TeamColor> wanted = new LinkedHashMap<>();
        wanted.put(a, null);
        wanted.put(b, colors.get(2));
        wanted.put(c, null);
        for (int i = 0; i < 5; i++) {
            wanted.put(UUID.randomUUID(), null);
        }
        Balance.party(wanted, List.of(List.of(a, b, c)), new ArrayList<>(colors), 4);
        assertEquals(colors.get(2), wanted.get(a));
        assertEquals(colors.get(2), wanted.get(c));
        for (int seed = 0; seed < 20; seed++) {
            Map<UUID, TeamColor> result = Balance.assign(new LinkedHashMap<>(wanted), new ArrayList<>(colors), 4, new Random(seed));
            assertEquals(result.get(a), result.get(b));
            assertEquals(result.get(a), result.get(c));
        }
    }

    @Test
    void picksEmptiestTeamWhenWantedIsFull() {
        List<TeamColor> colors = List.of(TeamColor.values()).subList(0, 2);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), x = UUID.randomUUID();
        Map<UUID, TeamColor> wanted = new LinkedHashMap<>();
        wanted.put(x, colors.get(0));
        wanted.put(a, colors.get(0));
        wanted.put(b, null);
        Balance.party(wanted, List.of(List.of(a, b)), new ArrayList<>(colors), 2);
        assertEquals(colors.get(1), wanted.get(a));
        assertEquals(colors.get(1), wanted.get(b));
        assertNotEquals(wanted.get(x), wanted.get(a));
    }
}
