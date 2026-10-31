package hu.icemine.core.kitpvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.build.Layout;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KitArenaBuildTest {

    @Test
    void spawnIsOnTheRaisedDeckAndFloorIsFlat() {
        Map<String, String> blocks = new HashMap<>();
        KitArenaBuild build = new KitArenaBuild((x, y, z, d) -> blocks.put(x + " " + y + " " + z, d), 0, 0, 0);
        build.build();
        Layout.Point spawn = build.layout().points.get("arena:jegmezo:spawn");
        assertEquals(KitArenaBuild.DECK + 1, (int) spawn.y);
        assertTrue(blocks.containsKey("0 " + KitArenaBuild.DECK + " 0"));
        assertNull(blocks.get("0 " + (KitArenaBuild.DECK + 1) + " 0"));
        int air = 0;
        for (int x = -20; x <= 20; x += 4) {
            assertTrue(blocks.containsKey(x + " 0 -20"), "floor " + x);
            if (!blocks.containsKey(x + " 1 -20")) {
                air++;
            }
        }
        assertTrue(air >= 9);
        assertEquals("barrier", blocks.get((KitArenaBuild.RADIUS + 2) + " 20 0"));
        assertTrue(build.layout().points.get("arena:jegmezo:safe").yaw > 8);
    }
}
