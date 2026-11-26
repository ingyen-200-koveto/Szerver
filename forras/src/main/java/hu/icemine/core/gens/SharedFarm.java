package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.List;

/**
 * A közös farm a Gens központjában: középen a tér (spawn, eladó hordó, kapa üllő), körülötte 8 nagy föld (23x23).
 * Mindenki itt arat, de csak azokon a földeken, amelyeket már megvett (jobb klikk a földre vagy a menü).
 * A koordináták a központ (0, 0) közepéhez képest, a talaj y = 0, a növények y = 1.
 */
public final class SharedFarm {

    public static final int HALF = 11;
    static final int[][] CENTERS = {{0, -28}, {28, -28}, {28, 0}, {28, 28}, {0, 28}, {-28, 28}, {-28, 0}, {-28, -28}};
    /** A földek és az utak négyzete a középtől. */
    public static final int EXTENT = 41;
    public static final int[] SELL = {3, -3};
    public static final int[] HOE = {-3, -3};

    private SharedFarm() {
    }

    public static int[] center(int field) {
        return CENTERS[field];
    }

    public static int fieldAt(int dx, int dz) {
        for (int i = 0; i < CENTERS.length; i++) {
            if (Math.abs(dx - CENTERS[i][0]) <= HALF && Math.abs(dz - CENTERS[i][1]) <= HALF) {
                return i;
            }
        }
        return -1;
    }

    public static List<int[]> cells(int field) {
        List<int[]> result = new ArrayList<>();
        int[] c = CENTERS[field];
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                result.add(new int[]{c[0] + dx, c[1] + dz});
            }
        }
        return result;
    }

    /** A kaszálás: a földön belül a kattintott növény körüli négyzet (0 = csak az az egy, 2 = 5x5). */
    public static List<int[]> around(int field, int dx, int dz, int radius) {
        List<int[]> result = new ArrayList<>();
        int[] c = CENTERS[field];
        for (int x = dx - radius; x <= dx + radius; x++) {
            for (int z = dz - radius; z <= dz + radius; z++) {
                if (Math.abs(x - c[0]) <= HALF && Math.abs(z - c[1]) <= HALF) {
                    result.add(new int[]{x, z});
                }
            }
        }
        return result;
    }
}
