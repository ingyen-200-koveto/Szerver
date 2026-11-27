package hu.icemine.core.modelobby;

import hu.icemine.core.build.Build;
import java.util.HashSet;
import java.util.Set;

/** Közös segédek a részletes módlobby témákhoz (foglalt helyek, növényzet, kerek torony, fák). */
abstract class ThemeBase extends Build {

    interface Free {
        boolean test(int x, int z);
    }

    protected final Free free;
    /** A növényzet szórásának fél mérete (a középponttól ennyi blokkig). */
    protected int span = 46;
    /** A foltos fű sűrűsége (0 = egyenletesen ritka). */
    protected double patchGrass = 0.2;
    private final Set<Long> taken = new HashSet<>();

    ThemeBase(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz);
        this.free = free;
    }

    protected void take(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                taken.add(key(x, z));
            }
        }
    }

    protected boolean open(int x, int z) {
        return free.test(x, z) && !taken.contains(key(x, z));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Fű, virágok, bokrok a szabad füves helyeken; inside: ide nem tesz semmit. */
    protected void scatter(Free inside, String[] flowers, double grass) {
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                if (inside.test(x, z) || !open(x, z)) {
                    continue;
                }
                double n = noise(x, z, 410);
                double patch = smooth(x, z, 7.0, 411);
                if (n < 0.02) {
                    put(x, 1, z, "oak_leaves[persistent=true]");
                } else if (flowers.length > 0 && n < 0.02 + 0.07 * patch) {
                    put(x, 1, z, flowers[(int) (noise(x, z, 413) * flowers.length)]);
                } else if (n < grass + patchGrass * patch) {
                    put(x, 1, z, noise(x, z, 414) < 0.85 ? "short_grass" : "fern");
                }
            }
        }
    }

    /** Kerek torony kúptetővel és zászlóval. */
    protected void roundTower(int tx, int tz, double radius, int height, int roofHeight, String roofStairs, String roofFull, String flag) {
        int r = (int) Math.ceil(radius) + 2;
        take(tx - r, tz - r, tx + r, tz + r);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d > radius + 0.4) {
                    continue;
                }
                boolean shell = d > radius - 0.9;
                for (int y = 0; y <= height; y++) {
                    if (!shell) {
                        put(tx + dx, y, tz + dz, y == 0 ? "spruce_planks" : "air");
                        continue;
                    }
                    boolean window = y % 6 == 4 && (dx == 0 || dz == 0) && y < height - 2;
                    double n = noise(tx + dx, y * 13 + tz + dz, 60);
                    String block = window ? "black_stained_glass" : (y <= 1 ? "cobblestone"
                            : (y % 6 == 0 ? "polished_andesite" : (n < 0.14 ? "mossy_stone_bricks" : (n < 0.24 ? "cracked_stone_bricks" : "stone_bricks"))));
                    put(tx + dx, y, tz + dz, block);
                }
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d <= radius + 1.2) {
                    put(tx + dx, height + 1, tz + dz, d > radius + 0.4 ? stairs("stone_brick", inward(dx, dz), true) : "stone_bricks");
                }
            }
        }
        cone(tx, height + 2, tz, radius + 1.0, roofHeight, roofStairs, roofFull, "spruce_fence");
        int tip = height + 2 + roofHeight + 1;
        put(tx, tip + 1, tz, "spruce_fence");
        put(tx, tip + 2, tz, "spruce_fence");
        for (int fx = 1; fx <= 3; fx++) {
            put(tx + fx, tip + 2, tz, flag);
            if (fx <= 2) {
                put(tx + fx, tip + 1, tz, flag);
            }
        }
    }

    /** Lombos fa (tölgy vagy sötét tölgy). */
    protected void tree(int x, int z, int height, String log, String leaves) {
        if (!open(x, z)) {
            return;
        }
        take(x - 2, z - 2, x + 2, z + 2);
        for (int y = 1; y <= height; y++) {
            put(x, y, z, log);
        }
        for (int[] rr : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(x + rr[0], 1, z + rr[1], log);
        }
        blob(x, height, z, 3.6, leaves);
        blob(x + 2, height - 3, z + 1, 2.6, leaves);
        blob(x - 2, height - 2, z - 1, 2.4, leaves);
        blob(x, height + 3, z, 2.2, leaves);
    }

    protected void blob(int bx, int by, int bz, double r, String leaves) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d <= r - noise(bx + dx, bz + dz + dy * 5, 403) * 0.9) {
                        put(bx + dx, by + dy, bz + dz, leaves);
                    }
                }
            }
        }
    }
}
