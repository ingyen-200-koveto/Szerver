package hu.icemine.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TitleTest {

    private static Map<String, String> build(int x, int y, int z, int scale, String facing, int[] box) {
        Map<String, String> blocks = new HashMap<>();
        Title title = new Title((bx, by, bz, data) -> blocks.put(bx + " " + by + " " + bz, data), x, y, z, scale, facing);
        title.build();
        int[] bounds = title.box();
        System.arraycopy(bounds, 0, box, 0, 6);
        return blocks;
    }

    private static boolean inside(String key, int[] box) {
        String[] parts = key.split(" ");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    @Test
    void everyBlockIsInsideTheBox() {
        for (String facing : new String[]{"south", "north", "east", "west"}) {
            int[] box = new int[6];
            Map<String, String> blocks = build(10, 150, -40, 3, facing, box);
            assertFalse(blocks.isEmpty(), facing);
            for (String key : blocks.keySet()) {
                assertTrue(inside(key, box), facing + " " + key);
            }
        }
    }

    @Test
    void southTitleIsThreeDeepTowardsNorth() {
        int[] box = new int[6];
        build(0, 100, -70, 2, "south", box);
        assertEquals(-72, box[2]);
        assertEquals(-70, box[5]);
        assertEquals(100, box[1]);
        assertEquals(100 + 7 * 2 + 1, box[4]);
    }

    @Test
    void frontFaceIsColoredAndBackIsTerracotta() {
        int[] box = new int[6];
        Map<String, String> blocks = build(0, 100, -70, 2, "south", box);
        boolean concrete = false;
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            String z = entry.getKey().split(" ")[2];
            if (z.equals("-70") && entry.getValue().endsWith("_concrete")) {
                concrete = true;
            }
            if (z.equals("-72")) {
                assertEquals("blue_terracotta", entry.getValue());
            }
        }
        assertTrue(concrete);
    }

    @Test
    void readsLeftToRightForTheViewer() {
        int[] box = new int[6];
        Map<String, String> south = build(0, 100, 0, 2, "south", box);
        Map<String, String> north = build(0, 100, 0, 2, "north", box);
        String topLeftSouth = south.get("-41 114 0");
        String topLeftNorth = north.get("41 114 0");
        assertEquals("light_blue_concrete", topLeftSouth);
        assertEquals(topLeftSouth, topLeftNorth);
    }

    @Test
    void facingValidation() {
        assertTrue(Title.validFacing("South"));
        assertTrue(Title.validFacing("west"));
        assertFalse(Title.validFacing("up"));
        assertFalse(Title.validFacing(null));
    }
}
