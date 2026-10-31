package hu.icemine.core.skypvp;

import hu.icemine.core.build.Build;

public final class SkyBuild extends Build {

    public static final int CLEAR = 64;
    static final int SPAWN_Y = 22;
    static final int SPAWN_RADIUS = 9;
    static final int CARDINAL = 44;
    static final int DIAGONAL = 27;

    public SkyBuild(Sink sink, int cx, int cy, int cz) {
        super(sink, cx, cy, cz);
    }

    @Override
    public void build() {
        island(0, 0, 0, 12.5, 16, 3);
        temple();
        int[][] cardinals = {{CARDINAL, 0}, {-CARDINAL, 0}, {0, CARDINAL}, {0, -CARDINAL}};
        for (int i = 0; i < cardinals.length; i++) {
            outpost(cardinals[i][0], cardinals[i][1], i);
        }
        int[][] diagonals = {{DIAGONAL, DIAGONAL}, {-DIAGONAL, DIAGONAL}, {DIAGONAL, -DIAGONAL}, {-DIAGONAL, -DIAGONAL}};
        for (int i = 0; i < diagonals.length; i++) {
            ruin(diagonals[i][0], diagonals[i][1], i);
        }
        for (int[] d : diagonals) {
            bridge(d[0] * 9 / 27, d[1] * 9 / 27, d[0] - Integer.signum(d[0]) * 6, d[1] - Integer.signum(d[1]) * 6, 0);
        }
        for (int[] c : cardinals) {
            for (int[] d : diagonals) {
                boolean near = (c[0] != 0 && Integer.signum(c[0]) == Integer.signum(d[0])) || (c[1] != 0 && Integer.signum(c[1]) == Integer.signum(d[1]));
                if (near) {
                    int sx = c[0] + Integer.signum(d[0] - c[0]) * 8;
                    int sz = c[1] + Integer.signum(d[1] - c[1]) * 8;
                    int ex = d[0] + Integer.signum(c[0] - d[0]) * 6;
                    int ez = d[1] + Integer.signum(c[1] - d[1]) * 6;
                    bridge(sx, sz, ex, ez, 0);
                }
            }
        }
        spawnIsland();
        for (int[] c : cardinals) {
            stairway(c[0], c[1]);
        }
        layout.point("spawn", 0.5, SPAWN_Y + 1, 4.5, 180);
        layout.point("safe", 0.5, SPAWN_Y + 1, 0.5, SPAWN_RADIUS + 1.5);
    }

    private void island(int ix, int iy, int iz, double radius, int depth, int salt) {
        int max = (int) Math.ceil(radius) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + 1.2 * Math.sin(angle * 5 + salt) + 0.7 * Math.sin(angle * 9 + 2.1 + salt);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                int x = ix + dx;
                int z = iz + dz;
                double n = noise(x, z, salt + 1);
                put(x, iy, z, n < 0.12 ? "packed_ice" : "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.5)) * depth) + 1 + (int) (noise(x, z, salt + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(x, z * 7 + l, salt + 10 + l);
                    String block = l == 1 ? "snow_block" : (l <= 3 ? (m < 0.5 ? "dirt" : "packed_ice") : (m < 0.4 ? "stone" : (m < 0.65 ? "packed_ice" : (m < 0.85 ? "blue_ice" : "andesite"))));
                    put(x, iy - l, z, block);
                }
                if (noise(x, z, salt + 3) < 0.07 && deep > 3) {
                    int length = 2 + (int) (noise(x, z, salt + 4) * 5);
                    for (int l = 1; l <= length; l++) {
                        put(x, iy - deep - l, z, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }

    private void chest(int x, int y, int z, String facing, boolean mid) {
        put(x, y, z, "chest[facing=" + facing + ",type=single]");
        layout.add(mid ? "chests:mid" : "chests:normal", x, y, z, 0);
    }

    private void temple() {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = dist(dx, dz);
                if (d <= 8.3) {
                    put(dx, 0, dz, d < 2.5 ? "blue_ice" : (Math.floorMod(dx + dz, 2) == 0 ? "polished_diorite" : "calcite"));
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(22.5 + 45 * i);
            int x = (int) Math.round(Math.cos(angle) * 7);
            int z = (int) Math.round(Math.sin(angle) * 7);
            int height = 5 + (int) (noise(i, 1, 41) * 5);
            boolean broken = i % 3 == 1;
            for (int y = 1; y <= (broken ? 3 : height); y++) {
                put(x, y, z, y == 1 ? "chiseled_quartz_block" : (noise(x, y + z, 42) < 0.3 ? "packed_ice" : "quartz_pillar[axis=y]"));
            }
            if (!broken) {
                put(x, height + 1, z, "chiseled_quartz_block");
                put(x, height + 2, z, "soul_lantern[hanging=false]");
            } else {
                put(x + (x > 0 ? 1 : -1), 1, z, "quartz_slab[type=bottom]");
            }
        }
        for (int y = 1; y <= 3; y++) {
            put(0, y, 0, y == 3 ? "sea_lantern" : "packed_ice");
        }
        for (int h = 4; h <= 8; h++) {
            put(0, h, 0, h % 2 == 0 ? "light_blue_stained_glass" : "blue_ice");
        }
        put(0, 9, 0, "end_rod[facing=up]");
        chest(2, 1, 0, "east", true);
        chest(-2, 1, 0, "west", true);
        chest(0, 1, 2, "south", true);
        chest(0, 1, -2, "north", true);
        put(2, 1, 2, "blue_ice");
        put(-2, 1, 2, "blue_ice");
        put(2, 1, -2, "blue_ice");
        put(-2, 1, -2, "blue_ice");
    }

    private void outpost(int ox, int oz, int index) {
        island(ox, 0, oz, 10.5, 13, 20 + index);
        String facing = ox > 0 ? "west" : (ox < 0 ? "east" : (oz > 0 ? "north" : "south"));
        int towardX = -Integer.signum(ox);
        int towardZ = -Integer.signum(oz);
        int hx = ox - towardX * 3 + (oz != 0 ? 4 : 0);
        int hz = oz - towardZ * 3 + (ox != 0 ? 4 : 0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int y = 1; y <= 4; y++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean door = (towardX != 0 && dx == 2 * towardX && dz == 0 && y <= 2) || (towardZ != 0 && dz == 2 * towardZ && dx == 0 && y <= 2);
                    boolean ruined = noise(hx + dx, hz + dz + y, 50 + index) < 0.18 && y >= 3 && !corner;
                    if (edge && !door && !ruined) {
                        put(hx + dx, y, hz + dz, corner ? "spruce_log[axis=y]" : (y == 4 ? "spruce_planks" : (noise(dx, y + dz, 51) < 0.5 ? "packed_ice" : "snow_block")));
                    }
                }
                put(hx + dx, 0, hz + dz, "spruce_planks");
                if (!edge || noise(hx + dx, hz + dz, 52) < 0.6) {
                    put(hx + dx, 5, hz + dz, edge ? stairs("spruce", inward(dx, dz), false) : "spruce_slab[type=bottom]");
                }
            }
        }
        chest(hx, 1, hz, facing, false);
        put(hx + 1, 1, hz + 1, "lantern[hanging=false]");
        int sx = ox + towardX * -4 + (oz != 0 ? -5 : 0);
        int sz = oz + towardZ * -4 + (ox != 0 ? -5 : 0);
        spruce(sx, sz, 7, 2.3);
        iceSpike(ox - (oz != 0 ? 5 : 0) + (ox != 0 ? -towardX * 6 : 0), oz - (ox != 0 ? -5 : 0) + (oz != 0 ? -towardZ * 6 : 0), 7, 1.3, 60 + index);
        int px = ox + towardX * 5;
        int pz = oz + towardZ * 5;
        int cx2 = px + (oz != 0 ? -4 : 0);
        int cz2 = pz + (ox != 0 ? -4 : 0);
        chest(cx2, 1, cz2, facing, false);
        put(cx2, 0, cz2, "packed_ice");
    }

    private void ruin(int rx, int rz, int index) {
        island(rx, 0, rz, 8, 10, 40 + index);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    int height = 1 + (int) (noise(rx + dx, rz + dz, 70 + index) * 4);
                    for (int y = 1; y <= height; y++) {
                        put(rx + dx, y, rz + dz, noise(dx, y + dz, 71) < 0.5 ? "stone_bricks" : (noise(dz, y, 72) < 0.5 ? "cracked_stone_bricks" : "packed_ice"));
                    }
                } else {
                    put(rx + dx, 0, rz + dz, noise(dx, dz, 73) < 0.5 ? "stone_bricks" : "mossy_stone_bricks");
                }
            }
        }
        chest(rx, 1, rz, rx > 0 ? "west" : "east", false);
        put(rx + 1, 1, rz + 1, "soul_lantern[hanging=false]");
        crystal(rx - 5 * Integer.signum(rx), rz + 4 * Integer.signum(rz), 5, 80 + index);
    }

    private void crystal(int x, int z, int height, int salt) {
        for (int h = 1; h <= height; h++) {
            String block = h == 1 ? "sea_lantern" : (noise(x, h, salt) < 0.5 ? "light_blue_stained_glass" : "blue_ice");
            put(x, h, z, block);
            if (h <= height / 2) {
                put(x + 1, h, z, "light_blue_stained_glass");
                put(x, h, z + 1, "blue_ice");
            }
        }
        put(x, height + 1, z, "end_rod[facing=up]");
    }

    private void bridge(int x0, int z0, int x1, int z1, int y) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            double fx = x0 + (x1 - x0) * t;
            double fz = z0 + (z1 - z0) * t;
            double sag = Math.sin(Math.PI * t) * 1.2;
            int by = y - (int) Math.round(sag);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    int x = (int) Math.round(fx) + ox;
                    int z = (int) Math.round(fz) + oz;
                    if (Math.abs(ox) + Math.abs(oz) > 1) {
                        continue;
                    }
                    double n = noise(x, z, 90);
                    if (n < 0.06 && i > 2 && i < steps - 2) {
                        continue;
                    }
                    put(x, by, z, n < 0.5 ? "packed_ice" : (n < 0.8 ? "snow_block" : "blue_ice"));
                    put(x, by - 1, z, "packed_ice");
                }
            }
        }
    }

    private void spawnIsland() {
        for (int dx = -SPAWN_RADIUS - 2; dx <= SPAWN_RADIUS + 2; dx++) {
            for (int dz = -SPAWN_RADIUS - 2; dz <= SPAWN_RADIUS + 2; dz++) {
                double d = dist(dx, dz);
                if (d > SPAWN_RADIUS + 0.4) {
                    continue;
                }
                String top = d > SPAWN_RADIUS - 0.6 ? "blue_ice" : (d < 2.5 ? "sea_lantern" : (Math.floorMod(dx + dz, 2) == 0 ? "calcite" : "packed_ice"));
                put(dx, SPAWN_Y, dz, top);
                int deep = (int) ((1 - d / (SPAWN_RADIUS + 1)) * 6) + 1;
                for (int l = 1; l <= deep; l++) {
                    put(dx, SPAWN_Y - l, dz, l == deep ? "blue_ice" : "packed_ice");
                }
                if (d > SPAWN_RADIUS - 0.6 && Math.abs(dx) > 2 && Math.abs(dz) > 2) {
                    put(dx, SPAWN_Y + 1, dz, "diorite_wall[up=true]");
                }
            }
        }
        int[][] posts = {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}};
        for (int[] post : posts) {
            for (int y = 1; y <= 5; y++) {
                put(post[0], SPAWN_Y + y, post[1], y == 5 ? "sea_lantern" : "quartz_pillar[axis=y]");
            }
        }
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = dist(dx, dz);
                if (d <= 7.6 && d > 5.6) {
                    put(dx, SPAWN_Y + 6, dz, "light_blue_stained_glass");
                }
            }
        }
        cone(0, SPAWN_Y + 6, 0, 5.6, 6, "dark_prismarine", "dark_prismarine", "end_rod[facing=up]");
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dist(dx, dz) < 4.6) {
                    put(dx, SPAWN_Y + 6, dz, "air");
                }
            }
        }
        put(0, SPAWN_Y + 11, 0, "dark_prismarine");
        put(0, SPAWN_Y + 10, 0, "chain[axis=y]");
        put(0, SPAWN_Y + 9, 0, "soul_lantern[hanging=true]");
    }

    private void stairway(int ox, int oz) {
        int dx = Integer.signum(ox);
        int dz = Integer.signum(oz);
        boolean alongX = dx != 0;
        int start = SPAWN_RADIUS;
        String up = alongX ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
        int y = SPAWN_Y;
        int distance = start;
        while (y > 0 && distance < CARDINAL - 6) {
            distance++;
            int nextY = Math.max(0, SPAWN_Y - (distance - start));
            for (int w = -2; w <= 2; w++) {
                int x = alongX ? dx * distance : w;
                int z = alongX ? w : dz * distance;
                put(x, nextY, z, Math.abs(w) == 2 ? "packed_ice" : (w == 0 ? "blue_ice" : "calcite"));
                put(x, nextY - 1, z, "packed_ice");
                for (int clear = 1; clear <= 4; clear++) {
                    put(x, nextY + clear, z, "air");
                }
                if (nextY < y) {
                    put(x, nextY + 1, z, Math.abs(w) == 2 ? "quartz_slab[type=bottom]" : stairs("quartz", up, false));
                }
            }
            y = nextY;
        }
        while (distance < CARDINAL - 8) {
            distance++;
            for (int w = -2; w <= 2; w++) {
                int x = alongX ? dx * distance : w;
                int z = alongX ? w : dz * distance;
                put(x, 0, z, Math.abs(w) == 2 ? "packed_ice" : "calcite");
                put(x, -1, z, "packed_ice");
            }
        }
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

    private void iceSpike(int x, int z, int height, double width, int salt) {
        for (int h = 0; h < height; h++) {
            double r = width * (1 - (double) h / height);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dist(dx, dz) <= r + 0.3) {
                        put(x + dx, 1 + h, z + dz, noise(x + dx, h + dz, salt) < 0.3 || h > height * 0.6 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }
}
