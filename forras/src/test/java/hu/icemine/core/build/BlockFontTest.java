package hu.icemine.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockFontTest {

    @Test
    void everyLetterIsFiveBySeven() {
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue(BlockFont.supports(c), String.valueOf(c));
            String[] glyph = BlockFont.glyph(c);
            assertEquals(7, glyph.length, String.valueOf(c));
            for (String row : glyph) {
                assertEquals(5, row.length(), String.valueOf(c));
            }
        }
    }

    @Test
    void modeTitlesBuild() {
        for (String word : new String[]{"FULLPVP", "FYLAND", "COMBOFLY", "GENS", "KINGDOMS", "BEDWARS"}) {
            Map<String, String> blocks = new HashMap<>();
            Title title = new Title((x, y, z, d) -> blocks.put(x + " " + y + " " + z, d), 0, 100, 0, 1, "south", word,
                    "red_concrete", "white_concrete", 3, "red_terracotta", "red_terracotta");
            title.build();
            int[] box = title.box();
            assertEquals(word.length() * 5 + (word.length() - 1) * 2 + 2, box[3] - box[0] + 1, word);
            assertTrue(blocks.containsValue("red_concrete"), word);
        }
    }

    @Test
    void icemineDefaultsStayTheSame() {
        Map<String, String> a = new HashMap<>();
        Map<String, String> b = new HashMap<>();
        new Title((x, y, z, d) -> a.put(x + " " + y + " " + z, d), 0, 100, 0, 2, "south").build();
        new Title((x, y, z, d) -> b.put(x + " " + y + " " + z, d), 0, 100, 0, 2, "south", "ICEMINE", "light_blue_concrete",
                "white_concrete", 3, "blue_terracotta", "light_blue_terracotta").build();
        assertEquals(a, b);
    }
}
