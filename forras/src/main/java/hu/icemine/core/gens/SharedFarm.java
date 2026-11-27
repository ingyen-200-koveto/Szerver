package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A közös farm a Gens központjában: középen a tér (spawn, eladó hordó, kapa üllő, portál), körülötte nagy körben
 * 8 szabálytalan, hullámos szélű föld, mint egy igazi nagy farmon, köztük utak, rét és kellékek.
 * A koordináták a központ közepéhez képest, a talaj y = 0, a növények y = 1.
 */
public final class SharedFarm {

    public static final int FIELDS = 8;
    /** A földek ilyen távol vannak a tér közepétől. */
    static final double RING = 104;
    /** Ekkora négyzetben van minden, ami a farmhoz tartozik (földek, utak). */
    public static final int EXTENT = 146;
    public static final int[] SELL = {6, 11};
    public static final int[] HOE = {-6, 11};

    private static final double[][] SHAPE = new double[FIELDS][];
    private static final byte[] MAP = new byte[(EXTENT * 2 + 1) * (EXTENT * 2 + 1)];
    private static final List<List<int[]>> CELLS = new ArrayList<>();

    static {
        for (int i = 0; i < FIELDS; i++) {
            // az első föld (búza) a spawn előtt, északon van, a többi körben, kicsit elcsúsztatva
            double angle = -Math.PI / 2 + Math.PI / FIELDS + i * Math.PI * 2 / FIELDS + Math.sin(i * 2.3) * 0.04;
            double dist = RING + Math.sin(i * 1.7 + 0.5) * 7;
            double radius = 27 + Math.sin(i * 2.9 + 1.1) * 2.5;
            SHAPE[i] = new double[]{Math.cos(angle) * dist, Math.sin(angle) * dist, radius,
                    i * 1.37 + 0.4, i * 2.11 + 1.3, i * 0.73 + 2.2, angle};
        }
        java.util.Arrays.fill(MAP, (byte) -1);
        for (int i = 0; i < FIELDS; i++) {
            List<int[]> cells = new ArrayList<>();
            double[] s = SHAPE[i];
            int r = (int) Math.ceil(s[2] * 1.35) + 1;
            int cx = (int) Math.round(s[0]);
            int cz = (int) Math.round(s[1]);
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (Math.abs(x) > EXTENT || Math.abs(z) > EXTENT || MAP[index(x, z)] >= 0) {
                        continue;
                    }
                    double dx = x - s[0];
                    double dz = z - s[1];
                    double a = Math.atan2(dz, dx);
                    if (Math.sqrt(dx * dx + dz * dz) <= edge(i, a)) {
                        MAP[index(x, z)] = (byte) i;
                        cells.add(new int[]{x, z});
                    }
                }
            }
            CELLS.add(Collections.unmodifiableList(cells));
        }
    }

    private SharedFarm() {
    }

    private static int index(int x, int z) {
        return (x + EXTENT) * (EXTENT * 2 + 1) + (z + EXTENT);
    }

    /** A föld széle az adott irányban: hullámos, szabálytalan forma. */
    static double edge(int field, double angle) {
        double[] s = SHAPE[field];
        return s[2] * (1 + 0.16 * Math.sin(3 * angle + s[3]) + 0.09 * Math.sin(5 * angle + s[4]) + 0.06 * Math.sin(2 * angle + s[5]));
    }

    public static int[] center(int field) {
        return new int[]{(int) Math.round(SHAPE[field][0]), (int) Math.round(SHAPE[field][1])};
    }

    public static double radius(int field) {
        return SHAPE[field][2];
    }

    /** A föld széle a tér felé (ide kerül a felirat és ide fut be az út). */
    public static double[] gate(int field, double extra) {
        double[] s = SHAPE[field];
        double toward = Math.atan2(-s[1], -s[0]);
        double r = edge(field, toward) + extra;
        return new double[]{s[0] + Math.cos(toward) * r, s[1] + Math.sin(toward) * r};
    }

    public static int fieldAt(int dx, int dz) {
        if (Math.abs(dx) > EXTENT || Math.abs(dz) > EXTENT) {
            return -1;
        }
        return MAP[index(dx, dz)];
    }

    public static List<int[]> cells(int field) {
        return CELLS.get(field);
    }

    /** A kaszálás: a földön belül a kattintott növény körüli négyzet (0 = csak az az egy, 2 = 5x5). */
    public static List<int[]> around(int field, int dx, int dz, int radius) {
        List<int[]> result = new ArrayList<>();
        for (int x = dx - radius; x <= dx + radius; x++) {
            for (int z = dz - radius; z <= dz + radius; z++) {
                if (fieldAt(x, z) == field) {
                    result.add(new int[]{x, z});
                }
            }
        }
        return result;
    }
}
