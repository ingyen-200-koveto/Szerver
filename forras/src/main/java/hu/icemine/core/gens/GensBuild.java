package hu.icemine.core.gens;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Layout;

public final class GensBuild extends Build {

    public static final int HUB_CLEAR = 30;
    public static final int PLOT_CLEAR = 20;
    static final int HUB_RADIUS = 20;
    static final int PLOT_RADIUS = 14;

    private final boolean hub;
    private final int salt;

    public GensBuild(Sink sink, int cx, int cy, int cz, boolean hub, int salt) {
        super(sink, cx, cy, cz);
        this.hub = hub;
        this.salt = salt;
    }

    public static Layout.Point plotSpawn() {
        return new Layout.Point(0.5, 1, 9.5, 180);
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

    private void plot() {
        island(PLOT_RADIUS, 12, 200 + Math.floorMod(salt, 50));
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = dist(dx, dz);
                if (d <= 7.5) {
                    put(dx, 0, dz, Math.floorMod(dx + dz, 2) == 0 ? "calcite" : "snow_block");
                } else if (d <= 8.3) {
                    put(dx, 0, dz, "packed_ice");
                }
            }
        }
        for (int z = 8; z <= 12; z++) {
            for (int x = -1; x <= 1; x++) {
                put(x, 0, z, x == 0 ? "blue_ice" : "packed_ice");
            }
        }
        cabin(-9, -8);
        spruce(9, -9, 7, 2.4);
        spruce(-11, 6, 6, 2.1);
        lamp(5, 8);
        lamp(-5, 8);
        lamp(8, 2);
        lamp(-8, 2);
        put(9, 1, 6, "barrel[facing=up]");
        put(10, 1, 6, "barrel[facing=up]");
        put(9, 2, 6, "lantern[hanging=false]");
        put(0, 1, -6, "chiseled_quartz_block");
        put(0, 2, -6, "sea_lantern");
        put(0, 3, -6, "end_rod[facing=up]");
    }

    private void cabin(int hx, int hz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(hx + dx, 0, hz + dz, "spruce_planks");
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 2;
                for (int y = 1; y <= 3; y++) {
                    if (!edge) {
                        put(hx + dx, y, hz + dz, "air");
                        continue;
                    }
                    boolean door = dz == 2 && dx == 0 && y <= 2;
                    boolean window = y == 2 && !corner && (Math.abs(dx) == 2 || Math.abs(dz) == 1);
                    if (door) {
                        put(hx + dx, y, hz + dz, "air");
                    } else if (corner) {
                        put(hx + dx, y, hz + dz, "spruce_log[axis=y]");
                    } else if (window) {
                        put(hx + dx, y, hz + dz, "light_blue_stained_glass");
                    } else {
                        put(hx + dx, y, hz + dz, y == 1 ? "stone_bricks" : "packed_ice");
                    }
                }
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int step = 0; step <= 3; step++) {
                int y = 4 + step;
                put(hx + dx, y, hz - 3 + step, stairs("spruce", "south", false));
                put(hx + dx, y, hz + 3 - step, stairs("spruce", "north", false));
                if (step == 3) {
                    put(hx + dx, y, hz, "spruce_slab[type=bottom]");
                }
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int y = 4; y <= 6 - Math.abs(dz); y++) {
                put(hx - 3, y, hz + dz, "spruce_planks");
                put(hx + 3, y, hz + dz, "spruce_planks");
            }
        }
        put(hx, 3, hz, "lantern[hanging=true]");
        put(hx - 2, 1, hz - 1, "crafting_table");
        put(hx + 2, 1, hz - 1, "chest[facing=south,type=single]");
        for (int y = 1; y <= 8; y++) {
            put(hx + 2, y, hz - 2, y <= 6 ? (y <= 1 ? "stone_bricks" : "bricks") : "bricks");
        }
        put(hx + 2, 9, hz - 2, "campfire[lit=true,signal_fire=false,facing=north]");
    }

    private void lamp(int x, int z) {
        put(x, 1, z, "stone_brick_wall[up=true]");
        put(x, 2, z, "spruce_fence");
        put(x, 3, z, "spruce_fence");
        put(x, 4, z, "lantern[hanging=false]");
    }

    private void hub() {
        island(HUB_RADIUS, 18, 300);
        for (int dx = -HUB_RADIUS; dx <= HUB_RADIUS; dx++) {
            for (int dz = -HUB_RADIUS; dz <= HUB_RADIUS; dz++) {
                double d = dist(dx, dz);
                if (d > 13.5) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(dz, dx));
                double star = Math.abs(((angle % 45) + 45) % 45 - 22.5) / 22.5;
                String block;
                if (d > 12.8) {
                    block = "emerald_block";
                } else if (Math.abs(d - 7) < 0.55) {
                    block = "prismarine_bricks";
                } else if (d < 7 && d > 2.5 && star > 0.75) {
                    block = "cyan_concrete";
                } else {
                    block = Math.floorMod(dx + dz, 2) == 0 ? "calcite" : "smooth_quartz";
                }
                put(dx, 0, dz, block);
            }
        }
        String[] tiers = {"coal_block", "iron_block", "gold_block", "lapis_block", "redstone_block", "diamond_block", "emerald_block", "amethyst_block", "quartz_block", "netherite_block"};
        for (int i = 0; i < tiers.length; i++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(dx, 1 + i, dz, dx == 0 && dz == 0 ? "sea_lantern" : tiers[i]);
                }
            }
        }
        put(0, 11, 0, "end_rod[facing=up]");
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(22.5 + 45 * i);
            int x = (int) Math.round(Math.cos(angle) * 11);
            int z = (int) Math.round(Math.sin(angle) * 11);
            put(x, 1, z, "dark_prismarine");
            put(x, 2, z, "prismarine_wall[up=true]");
            put(x, 3, z, "prismarine_wall[up=true]");
            put(x, 4, z, "sea_lantern");
        }
        stall(-15, 0, "lime_wool", "white_wool");
        stall(15, 0, "cyan_wool", "white_wool");
        int[][] trees = {{-8, -15}, {8, -15}, {-14, 10}, {14, 10}};
        for (int[] tree : trees) {
            spruce(tree[0], tree[1], 7, 2.4);
        }
        layout.point("spawn", 0.5, 1, 9.5, 180);
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
