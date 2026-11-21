package hu.icemine.core.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class SkywarsTest {

    @Test
    void islandsAreApartAndInsideReach() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int[] c = SwMap.island(i, 8);
            assertTrue(seen.add(c[0] + ":" + c[1]));
            assertTrue(Math.abs(c[0]) + SwMap.ISLAND < SwMap.REACH && Math.abs(c[1]) + SwMap.ISLAND < SwMap.REACH);
            for (int j = 0; j < i; j++) {
                int[] d = SwMap.island(j, 8);
                double dist = Math.hypot(c[0] - d[0], c[1] - d[1]);
                assertTrue(dist > 2 * SwMap.ISLAND + 8, "túl közel: " + i + " " + j);
            }
        }
    }

    @Test
    void buildIsDeterministicAndHasChests() {
        Map<String, String> a = new HashMap<>();
        Map<String, String> b = new HashMap<>();
        new SwMap((x, y, z, d) -> a.put(x + ":" + y + ":" + z, d), 0, 100, 0, SwMap.Theme.ERDO, 8).build();
        new SwMap((x, y, z, d) -> b.put(x + ":" + y + ":" + z, d), 0, 100, 0, SwMap.Theme.ERDO, 8).build();
        assertEquals(a, b);
        long chests = a.values().stream().filter("chest"::equals).count();
        assertEquals(8 * 3 + 6, chests);
    }

    @Test
    void islandLootAlwaysHasSwordAndBlocks() {
        for (int seed = 0; seed < 200; seed++) {
            List<List<ItemStack>> chests = SwLoot.island(new Random(seed));
            assertEquals(3, chests.size());
            boolean sword = chests.stream().flatMap(List::stream).anyMatch(i -> i.getType().name().endsWith("_SWORD"));
            int blocks = chests.stream().flatMap(List::stream).filter(i -> java.util.Set.of("OAK_PLANKS", "STONE", "COBBLESTONE", "WHITE_WOOL", "SANDSTONE").contains(i.getType().name())).mapToInt(ItemStack::getAmount).sum();
            assertTrue(sword);
            assertTrue(blocks >= 40);
        }
    }
}
