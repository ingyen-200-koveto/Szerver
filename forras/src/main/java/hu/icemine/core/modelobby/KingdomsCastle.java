package hu.icemine.core.modelobby;

/**
 * A Kingdoms lobby: királyi vár udvara. Várfal tornyokkal, kapuház, lakótorony a portál mögött,
 * kovácsműhely, gyakorlótér, istálló, sátrak, kút; a falakon kívül erdő.
 */
final class KingdomsCastle extends ThemeBase {

    static final int X = 30;
    static final int N = -30;
    static final int S = 26;

    KingdomsCastle(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    static boolean inside(int x, int z) {
        return Math.abs(x) < X - 1 && z > N + 1 && z < S - 1;
    }

    /** Az udvar talaja: kőutak a tengelyek mentén és a fal tövében, köztük füves, kavicsos, macskaköves foltok. */
    static String ground(int x, int z) {
        double n = noise(x, z, 530);
        double s = smooth(x, z, 5.0, 531);
        boolean road = Math.abs(x) <= 3 || Math.abs(z) <= 2 || Math.abs(x) >= X - 4 || z <= N + 4 || z >= S - 4;
        if (road) {
            return n < 0.15 ? "andesite" : (n < 0.3 ? "cobblestone" : (n < 0.36 ? "cracked_stone_bricks" : "stone_bricks"));
        }
        if (s > 0.55) {
            return n < 0.1 ? "moss_block" : "grass_block";
        }
        return s < 0.3 ? (n < 0.5 ? "gravel" : "coarse_dirt") : (n < 0.6 ? "cobblestone" : (n < 0.8 ? "mossy_cobblestone" : "gravel"));
    }

    @Override
    public void build() {
        take(-X - 2, N - 2, X + 2, S + 2);
        walls();
        int[][] towers = {{-X, N}, {X, N}, {-X, S}, {X, S}, {-X, -2}, {X, -2}};
        for (int[] t : towers) {
            roundTower(t[0], t[1], 4.4, 14, 8, "deepslate_tile", "deepslate_tiles", "yellow_wool");
        }
        gatehouse();
        keep();
        forge(-22, 13);
        training(22, 13);
        stable(21, -17);
        tent(-22, -18, "yellow_wool");
        tent(-21, -10, "red_wool");
        well(-17, 2);
        int[][] outside = {{-38, -12}, {38, -12}, {-36, 14}, {36, 14}, {-12, 36}, {12, 36}, {-24, 34}, {24, 34}, {0, -40}, {-20, -38},
                {20, -38}, {-39, 2}, {39, 2}};
        for (int[] t : outside) {
            tree(t[0], t[1], 6 + (int) (noise(t[0], t[1], 501) * 4),
                    noise(t[0], t[1], 502) < 0.5 ? "dark_oak_log[axis=y]" : "spruce_log[axis=y]",
                    noise(t[0], t[1], 502) < 0.5 ? "dark_oak_leaves[persistent=true]" : "spruce_leaves[persistent=true]");
        }
        for (int x = -X + 2; x <= X - 2; x++) {
            for (int z = N + 2; z <= S - 2; z++) {
                if (ground(x, z).equals("grass_block") && open(x, z) && free.test(x, z) && noise(x, z, 532) < 0.35) {
                    put(x, 1, z, noise(x, z, 533) < 0.1 ? "poppy" : "short_grass");
                }
            }
        }
        scatter((x, z) -> x >= -X - 2 && x <= X + 2 && z >= N - 2 && z <= S + 2, new String[]{"poppy", "dandelion", "cornflower"}, 0.3);
    }

    // ---- várfal ----

    private String stone(int x, int y, int z) {
        double n = noise(x, y * 31 + z, 510);
        if (y <= 1) {
            return n < 0.3 ? "mossy_cobblestone" : "cobblestone";
        }
        return n < 0.12 ? "mossy_stone_bricks" : (n < 0.22 ? "cracked_stone_bricks" : (n < 0.27 ? "andesite" : "stone_bricks"));
    }

    private void walls() {
        for (int x = -X; x <= X; x++) {
            for (int z = N; z <= S; z++) {
                boolean outer = Math.abs(x) == X || z == N || z == S;
                boolean inner = !outer && (Math.abs(x) == X - 1 || z == N + 1 || z == S - 1);
                if (!outer && !inner) {
                    continue;
                }
                for (int y = 0; y <= 8; y++) {
                    put(x, y, z, stone(x, y, z));
                }
                if (outer) {
                    boolean merlon = Math.floorMod(x + z, 2) == 0;
                    put(x, 9, z, merlon ? stone(x, 9, z) : "air");
                    if (merlon && noise(x, z, 511) < 0.04) {
                        put(x, 10, z, "lantern[hanging=false]");
                    }
                    boolean slit = Math.floorMod(x + z, 6) == 3;
                    if (slit) {
                        put(x, 5, z, "air");
                    }
                } else {
                    put(x, 9, z, "stone_brick_slab[type=bottom]");
                    boolean banner = Math.floorMod(Math.abs(x) + Math.abs(z), 8) == 0;
                    String facing = Math.abs(x) == X - 1 ? (x < 0 ? "east" : "west") : (z < 0 ? "south" : "north");
                    if (banner) {
                        String side = Math.abs(x) == X - 1 ? (x < 0 ? "1" : "-1") : (z < 0 ? "s" : "n");
                        int bx = side.equals("1") ? x + 1 : (side.equals("-1") ? x - 1 : x);
                        int bz = side.equals("s") ? z + 1 : (side.equals("n") ? z - 1 : z);
                        put(bx, 7, bz, "yellow_wall_banner[facing=" + facing + "]");
                    }
                }
            }
        }
    }

    // ---- kapuház ----

    private void gatehouse() {
        for (int side = -1; side <= 1; side += 2) {
            int gx = side * 5;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    for (int y = 0; y <= 12; y++) {
                        put(gx + dx, y, S + dz, edge || y == 0 ? stone(gx + dx, y, S + dz) : "air");
                    }
                    put(gx + dx, 13, S + dz, edge && Math.floorMod(dx + dz, 2) == 0 ? stone(gx + dx, 13, S + dz) : (edge ? "air" : "spruce_planks"));
                }
            }
            put(gx, 6, S + 2, "black_stained_glass");
            put(gx, 10, S + 2, "black_stained_glass");
            put(gx, 10, S - 2, "black_stained_glass");
            put(gx, 6, S - 3, "yellow_wall_banner[facing=north]");
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = S - 1; z <= S; z++) {
                for (int y = 1; y <= 5; y++) {
                    put(x, y, z, "air");
                }
                put(x, 0, z, "stone_bricks");
                put(x, 6, z, Math.abs(x) == 2 ? "stone_bricks" : "chiseled_stone_bricks");
                put(x, 5, z, x == -2 ? "stone_brick_stairs[facing=west,half=top]" : (x == 2 ? "stone_brick_stairs[facing=east,half=top]" : "iron_bars"));
            }
        }
        for (int z = S + 1; z <= S + 18; z++) {
            for (int x = -2; x <= 2; x++) {
                if (free.test(x, z)) {
                    put(x, 0, z, Math.abs(x) == 2 ? "stone_bricks" : (noise(x, z, 512) < 0.3 ? "gravel" : "cobblestone"));
                    put(x, 1, z, "air");
                }
            }
            if (Math.floorMod(z, 6) == 0) {
                for (int side = -1; side <= 1; side += 2) {
                    if (free.test(side * 3, z)) {
                        put(side * 3, 1, z, "spruce_fence");
                        put(side * 3, 2, z, "spruce_fence");
                        put(side * 3, 3, z, "lantern[hanging=false]");
                    }
                }
            }
        }
    }

    // ---- lakótorony ----

    private void keep() {
        int x0 = -12;
        int x1 = 12;
        int z0 = N + 2;
        int z1 = -22;
        int h = 15;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 0; y <= h; y++) {
                    String block;
                    if (!wall) {
                        block = y == 0 || y == 7 ? "spruce_planks" : "air";
                    } else {
                        boolean pilaster = Math.floorMod(x, 4) == 0 && z == z1;
                        boolean window = !pilaster && z == z1 && Math.floorMod(x, 4) == 2 && (y >= 3 && y <= 5 || y >= 9 && y <= 12);
                        block = window ? "black_stained_glass" : (pilaster ? (y % 7 == 0 ? "chiseled_stone_bricks" : "polished_andesite") : stone(x, y, z));
                        if (window && (y == 5 || y == 12)) {
                            block = Math.floorMod(x, 8) == 2 ? "stone_brick_stairs[facing=west,half=top]" : "stone_brick_stairs[facing=west,half=top]";
                        }
                    }
                    put(x, y, z, block);
                }
                if (wall) {
                    boolean merlon = Math.floorMod(x + z, 2) == 0;
                    put(x, h + 1, z, merlon ? stone(x, h + 1, z) : "air");
                } else {
                    put(x, h, z, "stone_bricks");
                }
            }
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            put(x, 7, z1 + 1, "stone_brick_stairs[facing=north,half=top]");
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 6; y++) {
                put(x, y, z1, "air");
            }
            put(x, 0, z1 + 1, "stone_bricks");
            put(x, 1, z1 + 1, "stone_brick_stairs[facing=north,half=bottom]");
        }
        put(-2, 6, z1, "stone_brick_stairs[facing=west,half=top]");
        put(2, 6, z1, "stone_brick_stairs[facing=east,half=top]");
        put(-1, 1, z1 - 1, "dark_oak_door[facing=south,half=lower,hinge=left,open=false]");
        put(-1, 2, z1 - 1, "dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
        put(0, 1, z1 - 1, "dark_oak_door[facing=south,half=lower,hinge=right,open=false]");
        put(0, 2, z1 - 1, "dark_oak_door[facing=south,half=upper,hinge=right,open=false]");
        put(1, 1, z1 - 1, "dark_oak_planks");
        put(1, 2, z1 - 1, "dark_oak_planks");
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 8; y <= 12; y++) {
                put(side * 4, y, z1 + 1, y == 8 ? "yellow_wall_banner[facing=south]" : "air");
            }
            put(side * 4, 10, z1 + 1, "yellow_wall_banner[facing=south]");
            put(side * 8, 10, z1 + 1, "yellow_wall_banner[facing=south]");
            put(side * 3, 3, z1 + 1, "lantern[hanging=false]");
            put(side * 3, 2, z1 + 1, "stone_brick_wall[up=true]");
            put(side * 3, 1, z1 + 1, "stone_bricks");
        }
        roundTower(0, z0 + 3, 4.2, 26, 9, "deepslate_tile", "deepslate_tiles", "yellow_wool");
        roundTower(x0, z1, 2.6, 20, 6, "deepslate_tile", "deepslate_tiles", "yellow_wool");
        roundTower(x1, z1, 2.6, 20, 6, "deepslate_tile", "deepslate_tiles", "yellow_wool");
    }

    // ---- kovácsműhely ----

    private void forge(int fx, int fz) {
        take(fx - 4, fz - 4, fx + 4, fz + 4);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                put(fx + dx, 0, fz + dz, noise(fx + dx, fz + dz, 520) < 0.4 ? "polished_andesite" : "stone_bricks");
                put(fx + dx, 6 - (Math.abs(dz) + 1) / 2, fz + dz,
                        dz == 0 ? "spruce_planks" : "spruce_stairs[facing=" + (dz < 0 ? "south" : "north") + ",half=bottom]");
            }
        }
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int y = 1; y <= 4; y++) {
                put(fx + c[0], y, fz + c[1], "stripped_spruce_log[axis=y]");
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            put(fx - 3, 1, fz + dz, "cobblestone");
            put(fx - 3, 2, fz + dz, dz == 0 ? "blast_furnace[facing=east,lit=true]" : "cobblestone_wall[up=true]");
        }
        put(fx - 2, 1, fz, "lava_cauldron");
        put(fx, 1, fz - 1, "anvil[facing=north]");
        put(fx + 1, 1, fz + 1, "smithing_table");
        put(fx - 1, 1, fz + 2, "grindstone[face=floor,facing=north]");
        put(fx + 2, 1, fz - 2, "barrel[facing=up]");
        put(fx + 2, 2, fz - 2, "lantern[hanging=false]");
        put(fx - 2, 1, fz - 2, "chest[facing=east]");
        put(fx, 4, fz, "lantern[hanging=true]");
    }

    // ---- gyakorlótér ----

    private void training(int tx, int tz) {
        take(tx - 5, tz - 5, tx + 5, tz + 5);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                put(tx + dx, 0, tz + dz, noise(tx + dx, tz + dz, 521) < 0.5 ? "coarse_dirt" : "dirt_path");
                if (edge && !(dx == -4 && Math.abs(dz) <= 1)) {
                    put(tx + dx, 1, tz + dz, "spruce_fence");
                }
            }
        }
        int[][] dummies = {{-1, -2}, {2, -2}, {-1, 2}, {2, 2}};
        for (int[] d : dummies) {
            put(tx + d[0], 1, tz + d[1], "spruce_fence");
            put(tx + d[0], 2, tz + d[1], "hay_block[axis=y]");
            put(tx + d[0], 3, tz + d[1], "carved_pumpkin[facing=west]");
            put(tx + d[0], 2, tz + d[1] - 1, "spruce_fence");
            put(tx + d[0], 2, tz + d[1] + 1, "spruce_fence");
        }
        put(tx + 3, 1, tz, "target");
        put(tx + 3, 2, tz, "target");
        put(tx - 3, 1, tz + 3, "barrel[facing=up]");
        put(tx - 3, 1, tz - 3, "hay_block[axis=y]");
    }

    // ---- istálló ----

    private void stable(int sx, int sz) {
        take(sx - 5, sz - 4, sx + 5, sz + 4);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                put(sx + dx, 0, sz + dz, noise(sx + dx, sz + dz, 522) < 0.6 ? "spruce_planks" : "coarse_dirt");
                int ry = 5 + 3 - Math.abs(dz);
                put(sx + dx, ry, sz + dz, dz == 0 ? "spruce_planks" : "spruce_stairs[facing=" + (dz < 0 ? "south" : "north") + ",half=bottom]");
                if (Math.abs(dx) == 4) {
                    for (int y = 5; y < ry; y++) {
                        put(sx + dx, y, sz + dz, "spruce_planks");
                    }
                }
                if (dz == -3 && (dx == -4 || dx == 4 || dx == 0)) {
                    for (int y = 1; y <= 4; y++) {
                        put(sx + dx, y, sz + dz, "stripped_spruce_log[axis=y]");
                    }
                }
                if (dz == 3) {
                    for (int y = 1; y <= 4; y++) {
                        put(sx + dx, y, sz + dz, Math.abs(dx) == 4 || dx == 0 ? "stripped_spruce_log[axis=y]" : "spruce_planks");
                    }
                }
                if (Math.abs(dx) == 4 && dz > -3 && dz < 3) {
                    for (int y = 1; y <= 4; y++) {
                        put(sx + dx, y, sz + dz, "spruce_planks");
                    }
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            put(sx + dx, 1, sz - 2, dx == 0 ? "air" : "spruce_fence");
            put(sx + dx, 1, sz + 2, "hay_block[axis=y]");
        }
        put(sx - 2, 2, sz + 2, "hay_block[axis=y]");
        put(sx + 2, 2, sz + 2, "hay_block[axis=y]");
        put(sx, 1, sz + 1, "water_cauldron[level=3]");
        put(sx, 6, sz, "chain[axis=y]");
        put(sx, 5, sz, "lantern[hanging=true]");
    }

    // ---- sátor ----

    private void tent(int tx, int tz, String wool) {
        take(tx - 4, tz - 4, tx + 4, tz + 4);
        for (int y = 0; y <= 5; y++) {
            double r = 3.6 - y * 0.7;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double d = dist(dx, dz);
                    if (d <= r + 0.4 && d > r - 0.8) {
                        int seg = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 4));
                        put(tx + dx, y + 1, tz + dz, seg % 2 == 0 ? wool : "white_wool");
                    }
                }
            }
        }
        put(tx, 7, tz, "spruce_fence");
        put(tx, 8, tz, wool);
        for (int y = 1; y <= 2; y++) {
            put(tx, y, tz + 3, "air");
            put(tx, y, tz + 2, "air");
        }
        put(tx - 1, 1, tz, "barrel[facing=up]");
        put(tx + 1, 1, tz - 1, "red_carpet");
    }

    // ---- kút ----

    private void well(int wx, int wz) {
        take(wx - 2, wz - 2, wx + 2, wz + 2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, 0, wz + dz, dx == 0 && dz == 0 ? "water" : "cobblestone");
                put(wx + dx, 1, wz + dz, dx == 0 && dz == 0 ? "air" : "cobblestone_wall[up=true]");
            }
        }
        for (int[] p : new int[][]{{-1, -1}, {1, 1}}) {
            put(wx + p[0], 2, wz + p[1], "spruce_fence");
            put(wx + p[0], 3, wz + p[1], "spruce_fence");
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, 4, wz + dz, "spruce_slab[type=bottom]");
            }
        }
        put(wx, 3, wz, "chain[axis=y]");
    }
}
