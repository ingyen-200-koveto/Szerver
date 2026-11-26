package hu.icemine.core.gens;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Layout;

public final class GensBuild extends Build {

    public static final int HUB_CLEAR = 96;
    public static final int PLOT_CLEAR = 26;
    static final int HUB_RADIUS = 34;
    static final int PLOT_RADIUS = 22;
    /** A földek és az utak négyzete a sziget közepétől (a 8 föld 12-ig tart, körülötte 2 széles út). */
    static final int FARM = 14;

    private final boolean hub;
    private final int salt;

    public GensBuild(Sink sink, int cx, int cy, int cz, boolean hub, int salt) {
        super(sink, cx, cy, cz);
        this.hub = hub;
        this.salt = salt;
    }

    /** A sziget közepén, a téren; északra (az első föld felé) néz. */
    public static Layout.Point plotSpawn() {
        return new Layout.Point(0.5, 1, 0.5, 180);
    }

    @Override
    public void build() {
        if (hub) {
            hub();
        } else {
            plot();
        }
    }

    private void island(double radius, int depth, int seed) {
        int max = (int) Math.ceil(radius) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + 1.2 * Math.sin(angle * 5 + seed) + 0.7 * Math.sin(angle * 11 + 1.3 + seed);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(cx + dx, cz + dz, seed) < 0.1 ? "packed_ice" : "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.6)) * depth) + 1 + (int) (noise(cx + dx, cz + dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(cx + dx, cz + dz * 3 + l, seed + 10 + l);
                    String block = l == 1 ? "dirt" : (l <= 3 ? (m < 0.6 ? "dirt" : "stone") : (m < 0.4 ? "stone" : (m < 0.7 ? "packed_ice" : (m < 0.88 ? "andesite" : "blue_ice"))));
                    put(dx, -l, dz, block);
                }
                if (noise(cx + dx, cz + dz, seed + 3) < 0.06 && deep > 3) {
                    int length = 2 + (int) (noise(cx + dx, cz + dz, seed + 4) * 5);
                    for (int l = 1; l <= length; l++) {
                        put(dx, -deep - l, dz, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }

    /**
     * A farm sziget alapja: utak, a középső tér az eladó hordóval és a kapa üllővel, díszek a szélén.
     * A 8 földet a FieldBuild rakja ki (a megnyitottakat beültetve, a többit lezárva).
     */
    private void plot() {
        island(PLOT_RADIUS, 12, 200 + Math.floorMod(salt, 50));
        for (int dx = -FARM; dx <= FARM; dx++) {
            for (int dz = -FARM; dz <= FARM; dz++) {
                put(dx, 1, dz, "air");
                put(dx, 2, dz, "air");
                if (FarmLayout.fieldAt(dx, dz) >= 0) {
                    put(dx, -1, dz, "dirt");
                    put(dx, 0, dz, islandGround(cx + dx, cz + dz));
                    continue;
                }
                put(dx, -1, dz, "dirt");
                boolean plaza = Math.abs(dx) <= 3 && Math.abs(dz) <= 3;
                if (plaza) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    put(dx, 0, dz, edge ? "stripped_spruce_wood[axis=y]" : (Math.floorMod(dx + dz, 2) == 0 ? "spruce_planks" : "birch_planks"));
                } else {
                    double n = noise(cx + dx, cz + dz, 410 + salt);
                    put(dx, 0, dz, n < 0.12 ? "coarse_dirt" : (n < 0.18 ? "gravel" : "dirt_path"));
                }
            }
        }
        put(FarmLayout.SELL[0], 1, FarmLayout.SELL[1], "barrel[facing=up]");
        put(FarmLayout.SELL[0], 2, FarmLayout.SELL[1], "lantern[hanging=false]");
        put(FarmLayout.HOE[0], 1, FarmLayout.HOE[1], "anvil[facing=east]");
        put(2, 1, 2, "hay_block[axis=y]");
        put(-2, 1, 2, "composter[level=8]");
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                put(3 * sx, 1, 3 * sz, "spruce_fence");
                put(3 * sx, 2, 3 * sz, "lantern[hanging=false]");
                lamp(FARM * sx, FARM * sz);
            }
        }
        well(0, 18);
        spruce(17, -7, 7, 2.4);
        spruce(-17, 7, 6, 2.1);
        spruce(-8, -17, 6, 2.2);
        spruce(9, 17, 7, 2.3);
        put(-17, 1, -4, "hay_block[axis=y]");
        put(-17, 1, -3, "hay_block[axis=y]");
        put(-17, 2, -4, "hay_block[axis=y]");
        put(17, 1, 4, "pumpkin");
        put(17, 1, 5, "melon");
        put(16, 1, 5, "barrel[facing=up]");
    }

    private void well(int wx, int wz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, -1, wz + dz, "stone");
                if (dx == 0 && dz == 0) {
                    put(wx, 0, wz, "water");
                    continue;
                }
                put(wx + dx, 0, wz + dz, "mossy_cobblestone");
                put(wx + dx, 1, wz + dz, Math.abs(dx) + Math.abs(dz) == 2 ? "mossy_cobblestone" : "cobblestone_slab[type=bottom]");
                if (Math.abs(dx) + Math.abs(dz) == 2) {
                    put(wx + dx, 2, wz + dz, "spruce_fence");
                    put(wx + dx, 3, wz + dz, "spruce_fence");
                }
                put(wx + dx, 4, wz + dz, "spruce_slab[type=bottom]");
            }
        }
        put(wx, 4, wz, "spruce_planks");
        put(wx, 3, wz, "chain[axis=y]");
    }

    private void lamp(int x, int z) {
        put(x, 1, z, "stone_brick_wall[up=true]");
        put(x, 2, z, "spruce_fence");
        put(x, 3, z, "spruce_fence");
        put(x, 4, z, "lantern[hanging=false]");
    }

    /** A sziget régi földjeinek helye: itt most fű van, ide lehet generátort rakni. */
    static String islandGround(int x, int z) {
        double n = noise(x, z, 720);
        return n < 0.08 ? "moss_block" : (n < 0.14 ? "coarse_dirt" : "grass_block");
    }

    /**
     * A központ: a közös farm. Középen a tér szélmalommal, eladó hordóval és kapa üllővel, körülötte a 8 nagy föld
     * (ezeket a FieldBuild rakja ki), köztük széles utak lámpákkal, a szélén standok és fenyők.
     */
    /** A központ szigete: lekerekített négyzet, hogy a nagy földek elférjenek (a telkek 48 blokkon túl kezdődnek). */
    private void squareIsland(double half, int depth, int seed) {
        int max = (int) Math.ceil(half) + 2;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double p = 6;
                double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
                double edge = half + 1.0 * Math.sin(Math.atan2(dz, dx) * 7 + seed) + noise(dx, dz, seed + 1) * 0.8;
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(cx + dx, cz + dz, seed) < 0.1 ? "packed_ice" : "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.6)) * depth) + 1 + (int) (noise(cx + dx, cz + dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(cx + dx, cz + dz * 3 + l, seed + 10 + l);
                    String block = l == 1 ? "dirt" : (l <= 3 ? (m < 0.6 ? "dirt" : "stone") : (m < 0.4 ? "stone" : (m < 0.7 ? "packed_ice" : (m < 0.88 ? "andesite" : "blue_ice"))));
                    put(dx, -l, dz, block);
                }
            }
        }
    }

    /** A központ nagy szigete (a farm és körülötte a város), füves tetővel. */
    static final double TOWN = 86;

    static boolean onIsland(int dx, int dz) {
        double p = 3;
        double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
        return d <= TOWN - 3;
    }

    private void bigIsland(double half, int depth, int seed) {
        int max = (int) Math.ceil(half) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double p = 3;
                double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
                double edge = half + 1.4 * Math.sin(Math.atan2(dz, dx) * 9 + seed) + noise(dx, dz, seed + 1) * 0.8;
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(dx, dz, seed) < 0.06 ? "moss_block" : "grass_block");
                double t = 1 - Math.pow(d / edge, 1.6);
                int deep = (int) (t * depth) + 1 + (int) (noise(dx, dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(dx, dz * 3 + l, seed + 10 + l);
                    String block = l <= 2 ? "dirt" : (m < 0.45 ? "stone" : (m < 0.7 ? "terracotta" : (m < 0.88 ? "andesite" : "packed_mud")));
                    put(dx, -l, dz, block);
                }
                if (d > edge - 1.2) {
                    put(dx, 1, dz, "oak_fence");
                    for (int y = 2; y <= 5; y++) {
                        put(dx, y, dz, "barrier");
                    }
                }
            }
        }
    }

    private void hub() {
        bigIsland(TOWN, 26, 300);
        int e = SharedFarm.EXTENT;
        for (int dx = -e; dx <= e; dx++) {
            for (int dz = -e; dz <= e; dz++) {
                for (int y = 1; y <= 12; y++) {
                    put(dx, y, dz, "air");
                }
                put(dx, -1, dz, "dirt");
                if (SharedFarm.fieldAt(dx, dz) >= 0) {
                    continue;
                }
                boolean plaza = Math.abs(dx) <= 8 && Math.abs(dz) <= 8;
                String block;
                if (plaza) {
                    double d = dist(dx, dz);
                    boolean edge = Math.abs(dx) == 8 || Math.abs(dz) == 8;
                    block = edge ? "prismarine_bricks" : (Math.abs(d - 4) < 0.55 ? "cyan_concrete" : (Math.floorMod(dx + dz, 2) == 0 ? "calcite" : "smooth_quartz"));
                } else {
                    boolean border = SharedFarm.fieldAt(dx + 1, dz) >= 0 || SharedFarm.fieldAt(dx - 1, dz) >= 0
                            || SharedFarm.fieldAt(dx, dz + 1) >= 0 || SharedFarm.fieldAt(dx, dz - 1) >= 0
                            || SharedFarm.fieldAt(dx + 1, dz + 1) >= 0 || SharedFarm.fieldAt(dx - 1, dz - 1) >= 0
                            || SharedFarm.fieldAt(dx + 1, dz - 1) >= 0 || SharedFarm.fieldAt(dx - 1, dz + 1) >= 0;
                    double n = noise(dx, dz, 721);
                    block = border ? "stripped_spruce_wood[axis=y]" : (n < 0.1 ? "coarse_dirt" : (n < 0.18 ? "gravel" : "dirt_path"));
                }
                put(dx, 0, dz, block);
            }
        }
        windmill();
        put(SharedFarm.SELL[0], 1, SharedFarm.SELL[1], "barrel[facing=up]");
        put(SharedFarm.SELL[0], 2, SharedFarm.SELL[1], "lantern[hanging=false]");
        put(SharedFarm.HOE[0], 1, SharedFarm.HOE[1], "anvil[facing=east]");
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                lamp(14 * sx, 14 * sz);
                lamp(14 * sx, 28 * sz);
                lamp(28 * sx, 14 * sz);
                lamp(14 * sx, 40 * sz);
                lamp(40 * sx, 14 * sz);
                put(8 * sx, 1, 8 * sz, "prismarine_wall[up=true]");
                put(8 * sx, 2, 8 * sz, "sea_lantern");
            }
        }
        stall(-11, 3, "lime_wool", "white_wool");
        stall(11, 3, "cyan_wool", "white_wool");
        int[][] trees = {{-43, -14}, {43, 14}, {14, -43}, {-14, 43}};
        for (int[] tree : trees) {
            spruce(tree[0], tree[1], 6, 1.8);
        }
        portal(0, 12);
        hu.icemine.core.modelobby.GensFarmTown.hubTown((x, y, z, data) -> put(x, y, z, data), 0, 0, 0, GensBuild::onIsland, 44).build();
        new hu.icemine.core.build.Title((x, y, z, data) -> put(x, y, z, data), 0, 22, -50, 2, "south", "GENS", "lime_concrete", "white_concrete",
                4, "green_terracotta", "green_terracotta").build();
        layout.point("spawn", 0.5, 1, 4.5, 180);
    }

    /** Portál a téren: aki belelép, a saját szigetére kerül. */
    static final int[] PORTAL = {0, 12};

    private void portal(int px, int pz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int y = 0; y <= 6; y++) {
                boolean outer = Math.abs(dx) == 3 || y == 6;
                boolean frame = Math.abs(dx) == 2 || y == 0 || y == 5;
                String block = outer ? (noise(px + dx, y, 650) < 0.4 ? "mossy_stone_bricks" : "stone_bricks")
                        : (frame ? "obsidian" : "nether_portal[axis=x]");
                put(px + dx, y, pz, block);
            }
        }
        put(px - 3, 7, pz, "lantern[hanging=false]");
        put(px + 3, 7, pz, "lantern[hanging=false]");
    }

    /** A központ közepén egy szélmalom, a vitorlák a spawn felé (délre) néznek. */
    private void windmill() {
        for (int y = 1; y <= 9; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    boolean inside = dx == 0 && dz == 0;
                    String block = inside ? "hay_block[axis=y]" : (corner ? "stripped_spruce_log[axis=y]" : (y <= 2 ? "cobblestone" : "spruce_planks"));
                    if (!inside && !corner && y == 6) {
                        block = "light_blue_stained_glass";
                    }
                    put(dx, y, dz, block);
                }
            }
        }
        put(0, 1, 1, "spruce_door[facing=south,half=lower,hinge=left]");
        put(0, 2, 1, "spruce_door[facing=south,half=upper,hinge=left]");
        cone(0, 10, 0, 2.3, 4, "dark_oak", "dark_oak_planks", "lightning_rod[facing=up]");
        put(0, 8, 2, "stripped_spruce_log[axis=z]");
        put(0, 8, 3, "stripped_spruce_log[axis=z]");
        for (int i = 1; i <= 4; i++) {
            put(0, 8 + i, 3, "stripped_spruce_log[axis=y]");
            put(1, 8 + i, 3, "white_wool");
            put(i, 8, 3, "stripped_spruce_log[axis=x]");
            put(i, 7, 3, "white_wool");
            put(0, 8 - i, 3, "stripped_spruce_log[axis=y]");
            put(-1, 8 - i, 3, "white_wool");
            put(-i, 8, 3, "stripped_spruce_log[axis=x]");
            put(-i, 9, 3, "white_wool");
        }
    }

    private void stall(int sx, int sz, String a, String b) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(sx + dx, 0, sz + dz, "spruce_planks");
                put(sx + dx, 4, sz + dz, Math.floorMod(dz, 2) == 0 ? a : b);
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    for (int y = 1; y <= 3; y++) {
                        put(sx + dx, y, sz + dz, "spruce_fence");
                    }
                }
            }
        }
        int dir = sx > 0 ? -1 : 1;
        for (int dz = -1; dz <= 1; dz++) {
            put(sx + 2 * dir, 1, sz + dz, "spruce_slab[type=top]");
            put(sx - 2 * dir, 1, sz + dz, "barrel[facing=up]");
        }
        put(sx, 3, sz, "lantern[hanging=true]");
        put(sx + 2 * dir, 2, sz, "emerald_block");
    }

    private void spruce(int tx, int tz, int height, double width) {
        for (int y = 1; y <= height; y++) {
            put(tx, y, tz, "spruce_log[axis=y]");
        }
        int layers = height - 2;
        for (int i = 0; i < layers; i++) {
            int y = 3 + i;
            double r = width * (1 - (double) i / layers) + (i % 2 == 0 ? 0.6 : -0.2);
            if (r < 0.6) {
                r = 0.6;
            }
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if ((dx == 0 && dz == 0) || dist(dx, dz) > r) {
                        continue;
                    }
                    put(tx + dx, y, tz + dz, "spruce_leaves[persistent=true]");
                }
            }
        }
        put(tx, height + 1, tz, "spruce_leaves[persistent=true]");
        put(tx, height + 2, tz, "snow[layers=2]");
    }
}
