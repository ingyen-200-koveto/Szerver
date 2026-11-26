package hu.icemine.core.modelobby;

/**
 * A Duels lobby: kolosszeum. Homokos küzdőtér, körben dobogófal piros-kék zászlókkal és tűztartókkal,
 * felfelé lépcsőző lelátók, kívül háromszintes boltíves homlokzat zászlórudakkal; kívül ciprusok.
 */
final class DuelsColosseum extends ThemeBase {

    static final double ARENA = 22;
    static final double PODIUM = 23.5;
    static final double TIERS = 34;
    static final double OUTER = 36;
    static final int TOP = 20;

    DuelsColosseum(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    static double d(int x, int z) {
        return Math.sqrt(x * x + z * 1.08 * z * 1.08);
    }

    /** A küzdőtér talaja. */
    static String ground(int x, int z) {
        double dd = d(x, z);
        double n = noise(x, z, 540);
        double s = smooth(x, z, 6.0, 541);
        if (dd < ARENA) {
            return s > 0.66 ? "red_sand" : (n < 0.06 ? "coarse_dirt" : (n < 0.12 ? "smooth_sandstone" : "sand"));
        }
        if (dd < OUTER + 0.5) {
            return "sandstone";
        }
        return s > 0.62 ? "sand" : (n < 0.06 ? "coarse_dirt" : "grass_block");
    }

    @Override
    public void build() {
        take(-40, -40, 40, 40);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                double dd = d(x, z);
                if (dd < ARENA || dd >= OUTER) {
                    continue;
                }
                double angle = Math.atan2(z, x);
                if (dd < PODIUM) {
                    podium(x, z, angle);
                } else if (dd < TIERS) {
                    tier(x, z, dd, angle);
                } else {
                    facade(x, z, dd, angle);
                }
            }
        }
        gates();
        int[][] cypress = {{-40, -6}, {40, -6}, {-38, 14}, {38, 14}, {-20, 37}, {20, 37}, {-10, 40}, {10, 40}, {-30, 28}, {30, 28},
                {-30, -28}, {30, -28}, {0, -41}};
        for (int[] c : cypress) {
            cypress(c[0], c[1], 6 + (int) (noise(c[0], c[1], 542) * 4));
        }
        scatter((x, z) -> d(x, z) < OUTER + 1, new String[]{"poppy", "dandelion"}, 0.22);
    }

    private void podium(int x, int z, double angle) {
        for (int y = 1; y <= 4; y++) {
            String block = y == 1 ? "cut_red_sandstone" : (y == 4 ? "cut_sandstone" : "smooth_sandstone");
            put(x, y, z, block);
        }
        int seg = (int) Math.floor((angle + Math.PI) / (Math.PI / 16));
        boolean inner = d(x, z) < ARENA + 0.8;
        put(x, 5, z, "sandstone_wall");
        if (inner && seg % 4 == 0) {
            String facing = facingIn(x, z);
            put(x + dir(facing)[0], 3, z + dir(facing)[1], (seg % 8 == 0 ? "red" : "blue") + "_wall_banner[facing=" + facing + "]");
        }
        if (inner && seg % 4 == 2) {
            put(x, 5, z, "chiseled_sandstone");
            put(x, 6, z, "campfire[lit=true]");
        }
    }

    private void tier(int x, int z, double dd, double angle) {
        int row = (int) Math.floor(dd - PODIUM);
        int h = 5 + row;
        for (int y = 1; y < h; y++) {
            put(x, y, z, noise(x, y + z, 544) < 0.3 ? "sandstone" : "smooth_sandstone");
        }
        double arc = Math.abs(Math.IEEEremainder(angle, Math.PI / 4)) * dd;
        String out = outwardDir(x, z);
        if (arc < 1.1) {
            put(x, h, z, "red_sandstone_stairs[facing=" + out + ",half=bottom]");
        } else if (row % 4 == 3) {
            put(x, h, z, "smooth_sandstone_slab[type=bottom]");
        } else {
            put(x, h, z, "smooth_sandstone_stairs[facing=" + out + ",half=bottom]");
        }
        for (int y = h + 1; y <= TOP + 2; y++) {
            put(x, y, z, "air");
        }
    }

    private void facade(int x, int z, double dd, double angle) {
        boolean outerSkin = dd >= OUTER - 1;
        double s = (angle + Math.PI) * OUTER;
        int pos = Math.floorMod((int) Math.floor(s), 4);
        for (int y = 1; y <= TOP; y++) {
            int level = (y - 1) / 6;
            int ly = (y - 1) % 6;
            boolean opening = pos != 0 && ly >= 1 && ly <= 4 && level < 3;
            boolean archTop = pos != 0 && ly == 4 && pos != 2;
            String block;
            if (ly == 0) {
                block = "cut_sandstone";
            } else if (pos == 0) {
                block = ly == 5 ? "chiseled_sandstone" : "smooth_sandstone";
            } else if (outerSkin && opening && !archTop) {
                block = "air";
            } else if (outerSkin && archTop) {
                block = "smooth_sandstone";
            } else if (!outerSkin && opening) {
                block = "terracotta";
            } else {
                block = "smooth_sandstone";
            }
            put(x, y, z, block);
        }
        put(x, TOP + 1, z, outerSkin ? "smooth_sandstone_slab[type=bottom]" : "cut_sandstone");
        int seg = (int) Math.floor(s / 4);
        if (outerSkin && pos == 0 && seg % 3 == 0) {
            for (int y = TOP + 1; y <= TOP + 4; y++) {
                put(x, y, z, "spruce_fence");
            }
            put(x, TOP + 4, z, seg % 2 == 0 ? "red_wool" : "yellow_wool");
            put(x, TOP + 3, z, seg % 2 == 0 ? "red_wool" : "yellow_wool");
        }
    }

    private void gates() {
        int[][] dirs = {{1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            for (int r = 21; r <= 37; r++) {
                for (int w = -2; w <= 2; w++) {
                    int x = d[0] * r;
                    int z = w;
                    for (int y = 1; y <= 6; y++) {
                        boolean arch = y == 6 || (y == 5 && Math.abs(w) == 2);
                        put(x, y, z, arch ? (r <= 24 || r >= 35 ? "chiseled_sandstone" : "smooth_sandstone") : "air");
                    }
                    put(x, 0, z, Math.abs(w) == 2 ? "cut_sandstone" : "smooth_sandstone");
                }
            }
            for (int w = -1; w <= 1; w++) {
                for (int y = 4; y <= 5; y++) {
                    put(d[0] * 22, y, w, "iron_bars");
                }
            }
        }
    }

    private void cypress(int x, int z, int height) {
        if (!open(x, z)) {
            return;
        }
        take(x - 1, z - 1, x + 1, z + 1);
        put(x, 1, z, "spruce_log[axis=y]");
        for (int y = 2; y <= height + 1; y++) {
            put(x, y, z, "spruce_log[axis=y]");
            int r = y > height - 1 || y < 3 ? 0 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0) && Math.abs(dx) + Math.abs(dz) <= 1 + (y % 2)) {
                        put(x + dx, y, z + dz, "spruce_leaves[persistent=true]");
                    }
                }
            }
        }
        put(x, height + 2, z, "spruce_leaves[persistent=true]");
        put(x, height + 3, z, "spruce_leaves[persistent=true]");
    }

    private static String facingIn(int x, int z) {
        return Math.abs(x) * 1.0 >= Math.abs(z) * 1.08 ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
    }

    private static String outwardDir(int x, int z) {
        return Math.abs(x) * 1.0 >= Math.abs(z) * 1.08 ? (x > 0 ? "east" : "west") : (z > 0 ? "south" : "north");
    }

    private static int[] dir(String facing) {
        switch (facing) {
            case "north":
                return new int[]{0, -1};
            case "south":
                return new int[]{0, 1};
            case "east":
                return new int[]{1, 0};
            default:
                return new int[]{-1, 0};
        }
    }
}
