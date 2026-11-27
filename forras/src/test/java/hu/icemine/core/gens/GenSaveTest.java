package hu.icemine.core.gens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenSaveTest {

    @Test
    void newFormatSplits() {
        assertEquals(List.of("1,101,2,1,0", "3,101,4,2,5"), GensGenerators.parts("1,101,2,1,0|3,101,4,2,5", null));
    }

    @Test
    void mergedLegacyIsRecovered() {
        GensData.Player data = new GensData.Player(UUID.randomUUID(), 1, 1);
        int[] cell = GenMath.spiral(1);
        int x = cell[0] * 1000;
        int z = cell[1] * 1000;
        String a = (x + 12) + ",101," + (z - 4) + ",1,64";
        String b = (x + 13) + ",101," + (z - 2) + ",2,7";
        String c = (x - 5) + ",101," + (z + 9) + ",3,0";
        String merged = (a + ";" + b + ";" + c).replace(";", "");
        assertEquals(List.of(a, b, c), GensGenerators.parts(merged, data));
    }
}
