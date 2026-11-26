package hu.icemine.core.modelobby;

/**
 * A ComboFly lobby: End város. Purpur tornyok bíbor üvegablakokkal és end rodokkal, körben obszidián oszlopok
 * (mint az End sárkány szigetén), kórusvirág ligetek, lebegő end kő szigetek.
 */
final class ComboflyEnd extends ThemeBase {

    ComboflyEnd(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    @Override
    public void build() {
        tower(-34, -10, 3);
        tower(34, -10, 3);
        tower(0, -33, 4);
        int[][] pillars = {{-28, -28, 18}, {28, -28, 22}, {-14, -38, 26}, {14, -38, 24}, {-32, 24, 14}, {32, 24, 16}};
        for (int i = 0; i < pillars.length; i++) {
            pillar(pillars[i][0], pillars[i][1], pillars[i][2], i % 3 == 0);
        }
        int[][] chorus = {{-31, -4}, {31, -5}, {-30, 16}, {30, 15}, {-18, 32}, {18, 32}, {0, 38}, {-36, -10}, {36, -11}};
        for (int i = 0; i < chorus.length; i++) {
            chorus(chorus[i][0], chorus[i][1], 3 + (int) (noise(i, 3, 570) * 4));
        }
        int[][] floating = {{-44, -24}, {44, -24}, {-46, 18}, {46, 18}, {-20, 46}, {22, 45}};
        for (int[] f : floating) {
            floating(f[0], -6 + (int) (noise(f[0], f[1], 571) * 10), f[1]);
        }
    }

    // ---- End város torony ----

    private void tower(int tx, int tz, int levels) {
        take(tx - 6, tz - 6, tx + 6, tz + 6);
        int y = 1;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int yy = 0; yy <= 3; yy++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    put(tx + dx, yy, tz + dz, edge || yy == 0 ? "end_stone_bricks" : "air");
                }
            }
        }
        y = 4;
        for (int level = 0; level < levels; level++) {
            int r = level % 2 == 0 ? 4 : 3;
            int h = 6;
            room(tx, y, tz, r, h);
            y += h;
            if (level < levels - 1) {
                int sr = 2;
                for (int yy = 0; yy < 4; yy++) {
                    for (int dx = -sr; dx <= sr; dx++) {
                        for (int dz = -sr; dz <= sr; dz++) {
                            boolean edge = Math.abs(dx) == sr || Math.abs(dz) == sr;
                            boolean corner = Math.abs(dx) == sr && Math.abs(dz) == sr;
                            put(tx + dx, y + yy, tz + dz, corner ? "purpur_pillar[axis=y]" : (edge ? (yy == 1 || yy == 2) && (dx == 0 || dz == 0)
                                    ? "magenta_stained_glass" : "purpur_block" : "air"));
                        }
                    }
                }
                y += 4;
            }
        }
        for (int k = 0; k <= 4; k++) {
            int r = 4 - k;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                    if (!edge && r > 0) {
                        put(tx + dx, y + k, tz + dz, "purpur_block");
                        continue;
                    }
                    String out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    put(tx + dx, y + k, tz + dz, r == 0 ? "purpur_block" : "purpur_stairs[facing=" + out + ",half=bottom]");
                }
            }
        }
        put(tx, y + 5, tz, "end_rod[facing=up]");
        put(tx, y + 6, tz, "end_rod[facing=up]");
    }

    private void room(int tx, int y0, int tz, int r, int h) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                boolean rim = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                if (rim) {
                    String out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
                    put(tx + dx, y0, tz + dz, "purpur_stairs[facing=" + opposite(out) + ",half=top]");
                    put(tx + dx, y0 + h, tz + dz, "purpur_slab[type=bottom]");
                    continue;
                }
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                for (int yy = 0; yy < h; yy++) {
                    String block;
                    if (yy == 0 || yy == h - 1) {
                        block = edge ? "purpur_block" : (yy == 0 ? "purpur_block" : "air");
                    } else if (corner) {
                        block = "purpur_pillar[axis=y]";
                    } else if (edge) {
                        boolean window = (yy == 2 || yy == 3) && (Math.abs(dx) == 1 || Math.abs(dz) == 1 || dx == 0 || dz == 0);
                        block = window ? "magenta_stained_glass" : "purpur_block";
                    } else {
                        block = "air";
                    }
                    put(tx + dx, y0 + yy, tz + dz, block);
                }
                if (!edge) {
                    put(tx + dx, y0 + h - 1, tz + dz, "purpur_block");
                }
            }
        }
        for (int[] c : new int[][]{{-r - 1, -r - 1}, {r + 1, -r - 1}, {-r - 1, r + 1}, {r + 1, r + 1}}) {
            put(tx + c[0], y0 + h + 1, tz + c[1], "end_rod[facing=up]");
        }
        put(tx, y0 + 4, tz + r + 1, "magenta_wall_banner[facing=south]");
        put(tx, y0 + 4, tz - r - 1, "magenta_wall_banner[facing=north]");
        put(tx, y0 + h - 2, tz, "end_rod[facing=down]");
    }

    // ---- obszidián oszlop ----

    private void pillar(int px, int pz, int height, boolean cage) {
        if (!open(px, pz)) {
            return;
        }
        take(px - 4, pz - 4, px + 4, pz + 4);
        double r = 1.9;
        for (int y = -3; y <= height; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dist(dx, dz) <= r) {
                        put(px + dx, y, pz + dz, noise(px + dx, y + pz + dz, 572) < 0.12 ? "crying_obsidian" : "obsidian");
                    }
                }
            }
        }
        put(px, height + 1, pz, "bedrock");
        put(px, height + 2, pz, "end_rod[facing=up]");
        if (cage) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    for (int y = height + 1; y <= height + 4; y++) {
                        if (edge) {
                            put(px + dx, y, pz + dz, "iron_bars");
                        }
                    }
                    put(px + dx, height + 5, pz + dz, "iron_bars");
                }
            }
        }
    }

    // ---- kórusvirág ----

    private void chorus(int x, int z, int height) {
        if (!open(x, z)) {
            return;
        }
        take(x - 2, z - 2, x + 2, z + 2);
        for (int y = 1; y <= height; y++) {
            boolean branch = y == height - 1 && height > 3;
            put(x, y, z, "chorus_plant[up=" + (y < height) + ",down=true,east=" + branch + ",west=" + branch
                    + ",north=false,south=false]");
        }
        put(x, height + 1, z, "chorus_flower[age=5]");
        if (height > 3) {
            int y = height - 1;
            for (int side = -1; side <= 1; side += 2) {
                String toward = side > 0 ? "west" : "east";
                put(x + side, y, z, "chorus_plant[up=true,down=false," + toward + "=true,north=false,south=false,"
                        + (side > 0 ? "east" : "west") + "=false]");
                put(x + side, y + 1, z, "chorus_plant[up=true,down=true,east=false,west=false,north=false,south=false]");
                put(x + side, y + 2, z, "chorus_flower[age=5]");
            }
        }
    }

    // ---- lebegő end kő sziget ----

    private void floating(int fx, int fy, int fz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = dist(dx, dz);
                if (d > 3.6) {
                    continue;
                }
                int depth = (int) Math.round((3.8 - d) * 1.5);
                for (int y = fy - depth; y <= fy; y++) {
                    put(fx + dx, y, fz + dz, noise(fx + dx, y + fz, 573) < 0.15 ? "end_stone_bricks" : "end_stone");
                }
            }
        }
        put(fx, fy + 1, fz, "chorus_plant[up=true,down=true,east=false,west=false,north=false,south=false]");
        put(fx, fy + 2, fz, "chorus_flower[age=5]");
        put(fx + 2, fy + 1, fz - 1, "end_rod[facing=up]");
    }
}
