package hu.icemine.core.duels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.build.Layout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DuelArenaBuildTest {

    private static final String[] TYPES = {"classic", "sumo", "bridge", "builduhc", "crystal"};
    private static final String[] THEMES = {"forest", "desert", "ice", "nether"};

    private static Map<String, String> build(String type, String theme, Layout[] out) {
        Map<String, String> blocks = new HashMap<>();
        DuelArenaBuild build = new DuelArenaBuild((x, y, z, d) -> blocks.put(x + "," + y + "," + z, d), 0, 0, 0, type, theme);
        build.build();
        out[0] = build.layout();
        return blocks;
    }

    private static boolean free(String block) {
        return block == null || block.equals("air");
    }

    private static boolean inside(Layout.Area area, double x, double y, double z) {
        return x >= area.x1 && x < area.x2 + 1 && y >= area.y1 && y < area.y2 + 1 && z >= area.z1 && z < area.z2 + 1;
    }

    @Test
    void everyArenaHasSafeSpawnsInsideItsBounds() {
        for (String type : TYPES) {
            for (String theme : THEMES) {
                Layout[] out = new Layout[1];
                Map<String, String> blocks = build(type, theme, out);
                String name = type + "/" + theme;
                Layout.Area bounds = out[0].areas.get("bounds");
                assertNotNull(bounds, name);
                for (String side : new String[] {"spawn:red", "spawn:blue"}) {
                    Layout.Point spawn = out[0].points.get(side);
                    assertNotNull(spawn, name + " " + side);
                    assertTrue(inside(bounds, spawn.x, spawn.y, spawn.z), name + ": " + side + " a pályán belül");
                    int x = (int) Math.floor(spawn.x);
                    int y = (int) Math.floor(spawn.y);
                    int z = (int) Math.floor(spawn.z);
                    String ground = blocks.get(x + "," + (y - 1) + "," + z);
                    assertNotNull(ground, name + ": " + side + " alatt van talaj");
                    assertFalse(ground.equals("air") || ground.equals("water") || ground.equals("end_portal"), name + ": " + side + " alatt " + ground);
                    assertTrue(free(blocks.get(x + "," + y + "," + z)), name + ": " + side + " lába szabad");
                    assertTrue(free(blocks.get(x + "," + (y + 1) + "," + z)), name + ": " + side + " feje szabad");
                }
            }
        }
    }

    @Test
    void bridgeKeepsGoalsAndSpawnsWhereTheGameExpectsThem() {
        Layout[] out = new Layout[1];
        Map<String, String> blocks = build("bridge", "forest", out);
        Layout layout = out[0];
        Layout.Area blue = layout.areas.get("goal:blue");
        Layout.Area red = layout.areas.get("goal:red");
        assertEquals(-32, blue.z1);
        assertEquals(-30, blue.z2);
        assertEquals(30, red.z1);
        assertEquals(32, red.z2);
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                assertEquals("end_portal", blocks.get(x + ",0," + (-31 + z)));
                assertEquals("end_portal", blocks.get(x + ",0," + (31 + z)));
            }
        }
        // a saját kapu a saját sziget mögött van
        assertTrue(layout.points.get("spawn:blue").z < 0);
        assertTrue(layout.points.get("spawn:red").z > 0);
        assertEquals(-8.0, layout.points.get("fall").y);
    }

    @Test
    void classicHasAClearLineBetweenTheSpawns() {
        Layout[] out = new Layout[1];
        Map<String, String> blocks = build("classic", "forest", out);
        assertEquals(DuelArenaBuild.HALF, out[0].areas.get("bounds").x2);
        assertEquals(DuelArenaBuild.CEILING - 1, out[0].areas.get("bounds").y2);
        for (int z = -16; z <= 16; z++) {
            assertTrue(free(blocks.get("0,1," + z)), "z=" + z);
            assertTrue(free(blocks.get("0,2," + z)), "z=" + z);
        }
        // a pályát körbe zárja a fal és a barrier, nem lehet kisétálni
        for (int i = -DuelArenaBuild.HALF; i <= DuelArenaBuild.HALF; i++) {
            for (int y = 1; y < DuelArenaBuild.CEILING; y++) {
                assertFalse(free(blocks.get(i + "," + y + "," + (DuelArenaBuild.HALF + 1))), i + "," + y);
                assertFalse(free(blocks.get((-DuelArenaBuild.HALF - 1) + "," + y + "," + i)), i + "," + y);
            }
        }
    }

    @Test
    void themesReallyLookDifferent() {
        Set<String> floors = new HashSet<>();
        for (String theme : THEMES) {
            Layout[] out = new Layout[1];
            floors.add(build("classic", theme, out).get("10,0,3"));
        }
        assertEquals(THEMES.length, floors.size());
    }

    @Test
    void sumoLosesWhenYouFallIntoTheWater() {
        Layout[] out = new Layout[1];
        Map<String, String> blocks = build("sumo", "forest", out);
        double fall = out[0].points.get("fall").y;
        assertEquals("water", blocks.get("12,-6,0"));
        assertTrue(-6 + 1 < fall, "aki a vízbe esik, az már a leesési magasság alatt van");
    }
}
