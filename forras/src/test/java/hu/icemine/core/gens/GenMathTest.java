package hu.icemine.core.gens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenMathTest {

    @Test
    void produceAddsUntilCapacity() {
        assertEquals(2, GenMath.produce(0, 10, 2, 1.0));
        assertEquals(10, GenMath.produce(9, 10, 2, 1.0));
        assertEquals(10, GenMath.produce(10, 10, 2, 1.0));
        assertEquals(15, GenMath.produce(15, 10, 2, 1.0));
    }

    @Test
    void produceUsesMultiplierButAtLeastOne() {
        assertEquals(3, GenMath.produce(0, 100, 2, 1.5));
        assertEquals(1, GenMath.produce(0, 100, 1, 0.1));
        assertEquals(1, GenMath.produce(0, 100, 3, -2.0));
    }

    @Test
    void spiralStartsAtCenter() {
        int[] first = GenMath.spiral(0);
        assertEquals(0, first[0]);
        assertEquals(0, first[1]);
        int[] negative = GenMath.spiral(-5);
        assertEquals(0, negative[0]);
        assertEquals(0, negative[1]);
    }

    @Test
    void spiralNeverRepeats() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            int[] p = GenMath.spiral(i);
            assertTrue(seen.add(((long) p[0] << 32) ^ (p[1] & 0xFFFFFFFFL)), "ismétlődő pont: " + i);
        }
    }

    @Test
    void spiralFillsRingsCompletely() {
        for (int layer = 0; layer <= 6; layer++) {
            int size = 2 * layer + 1;
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < size * size; i++) {
                int[] p = GenMath.spiral(i);
                assertTrue(Math.abs(p[0]) <= layer && Math.abs(p[1]) <= layer, "gyűrűn kívül: " + i);
                seen.add(((long) p[0] << 32) ^ (p[1] & 0xFFFFFFFFL));
            }
            assertEquals(size * size, seen.size());
        }
    }

    @Test
    void sellValueHandlesEdges() {
        assertEquals(0, GenMath.sellValue(0, 10));
        assertEquals(0, GenMath.sellValue(10, 0));
        assertEquals(250, GenMath.sellValue(25, 10));
        assertEquals(Long.MAX_VALUE, GenMath.sellValue(Long.MAX_VALUE / 2, 3));
    }

    @Test
    void blockKeyIsUniqueAroundPlot() {
        Set<Long> keys = new HashSet<>();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                for (int y = -64; y <= 320; y += 32) {
                    assertTrue(keys.add(GensData.key(x + 100_000, y, z - 100_000)));
                }
            }
        }
    }
}
