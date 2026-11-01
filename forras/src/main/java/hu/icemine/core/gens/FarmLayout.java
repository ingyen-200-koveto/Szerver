package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.List;

/**
 * A Gens sziget elrendezése: középen a tér (spawn, eladó hordó, kapa üllő), körülötte 8 föld (7x7), köztük 2 széles utak.
 * Minden koordináta a sziget közepéhez képest értendő, a talaj y = 0, a növények y = 1.
 *
 * <pre>
 *   8 | 1 | 2
 *   7 | * | 3
 *   6 | 5 | 4
 * </pre>
 */
public final class FarmLayout {

    public static final int FIELDS = 8;
    public static final int HALF = 3;
    public static final int SIZE = (HALF * 2 + 1) * (HALF * 2 + 1);
    static final int[][] CENTERS = {{0, -9}, {9, -9}, {9, 0}, {9, 9}, {0, 9}, {-9, 9}, {-9, 0}, {-9, -9}};
    /** Eladó hordó és kapa üllő a téren. */
    public static final int[] SELL = {2, -2};
    public static final int[] HOE = {-2, -2};

    private FarmLayout() {
    }

    public static int[] center(int field) {
        return CENTERS[field];
    }

    /** Melyik földön van ez a pont (0-7), vagy -1. */
    public static int fieldAt(int dx, int dz) {
        for (int i = 0; i < CENTERS.length; i++) {
            if (Math.abs(dx - CENTERS[i][0]) <= HALF && Math.abs(dz - CENTERS[i][1]) <= HALF) {
                return i;
            }
        }
        return -1;
    }

    public static List<int[]> cells(int field) {
        List<int[]> result = new ArrayList<>(SIZE);
        int[] c = CENTERS[field];
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                result.add(new int[]{c[0] + dx, c[1] + dz});
            }
        }
        return result;
    }

    /** A kaszálás: a földön belül a kattintott blokk körüli négyzet (radius 0 = csak az az egy). */
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
