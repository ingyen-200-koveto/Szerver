package hu.icemine.core.kitpvp;

import hu.icemine.core.build.Build;

public final class KitArenaBuild extends Build {

    public static final int CLEAR = 46;
    public static final int SECOND_OFFSET = 110;
    static final int RADIUS = 38;
    static final int TOWER = 6;
    static final int DECK = 9;
    static final double DECK_RADIUS = 8.6;
    static final int BARRIER_HEIGHT = 30;

    public KitArenaBuild(Sink sink, int cx, int cy, int cz) {
        super(sink, cx, cy, cz);
    }

    @Override
    public void build() {
        island();
        floor();
        wall();
        decorations();
        tower();
        deck();
        layout.point("arena:jegmezo:spawn", 0.5, DECK + 1, 0.5, 180);
        layout.point("arena:jegmezo:safe", 0.5, DECK + 1, 0.5, DECK_RADIUS);
    }

    private double edge(int x, int z) {
        double angle = Math.atan2(z, x);
        return RADIUS + 0.8 * Math.sin(angle * 5 + 0.7) + 0.5 * Math.sin(angle * 11);
    }

    private void island() {
        for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) {
            for (int z = -RADIUS - 3; z <= RADIUS + 3; z++) {
                double d = dist(x, z);
                double rim = edge(x, z) + 1.5;
                if (d > rim) {
                    continue;
                }
                double t = 1 - d / rim;
                int depth = 4 + (int) Math.round(t * t * 22 + smooth(x, z, 6.0, 41) * 5 * t);
                for (int y = -depth; y <= -2; y++) {
                    double n = noise(x + y, z - y, 42);
                    String block;
                    if (y > -5) {
                        block = n < 0.55 ? "snow_block" : "packed_ice";
                    } else if (y < -depth + 2) {
                        block = n < 0.5 ? "blue_ice" : "packed_ice";
                    } else {
                        block = n < 0.45 ? "stone" : (n < 0.75 ? "packed_ice" : "andesite");
                    }
                    put(x, y, z, block);
                }
            }
        }
    }

    private void floor() {
        for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) {
            for (int z = -RADIUS - 3; z <= RADIUS + 3; z++) {
                double d = dist(x, z);
                if (d > edge(x, z) + 1.5) {
                    continue;
                }
                double vein = smooth(x, z, 7.0, 43);
                double n = noise(x, z, 44);
                String top;
                if (d < DECK_RADIUS + 3.5) {
                    top = n < 0.5 ? "polished_andesite" : "andesite";
                } else if (vein > 0.72) {
                    top = "light_blue_concrete_powder";
                } else if (vein > 0.66) {
                    top = n < 0.5 ? "white_concrete_powder" : "light_blue_concrete_powder";
                } else {
                    top = n < 0.07 ? "white_concrete_powder" : "snow_block";
                }
                put(x, 0, z, top);
                put(x, -1, z, "snow_block");
            }
        }
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i + Math.PI / 4;
            for (int r = (int) DECK_RADIUS + 4; r < RADIUS - 2; r++) {
                int x = (int) Math.round(Math.cos(angle) * r);
                int z = (int) Math.round(Math.sin(angle) * r);
                put(x, 0, z, r % 6 == 0 ? "sea_lantern" : "light_blue_concrete");
            }
        }
        ring(0, 0, 0, 23.2, 24.2, "light_blue_concrete");
    }

    private void wall() {
        for (int x = -RADIUS - 4; x <= RADIUS + 4; x++) {
            for (int z = -RADIUS - 4; z <= RADIUS + 4; z++) {
                double d = dist(x, z);
                double e = edge(x, z);
                if (d <= e + 0.5 || d > e + 2.2) {
                    continue;
                }
                boolean outer = d > e + 1.4;
                int height = outer ? 4 : 2;
                for (int y = 1; y <= height; y++) {
                    double n = noise(x, z + y, 45);
                    put(x, y, z, y == height && !outer ? "snow_block" : (n < 0.4 ? "packed_ice" : (n < 0.7 ? "stone_bricks" : "snow_block")));
                }
                if (outer) {
                    put(x, 5, z, noise(x, z, 46) < 0.15 ? "lantern[hanging=false]" : "snow[layers=2]");
                    for (int y = 6; y <= BARRIER_HEIGHT; y++) {
                        put(x, y, z, "barrier");
                    }
                }
            }
        }
    }

    private void decorations() {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + 0.2;
            int x = (int) Math.round(Math.cos(angle) * 31);
            int z = (int) Math.round(Math.sin(angle) * 31);
            iceSpike(x, 0, z, 7 + (int) (noise(x, z, 47) * 5), 1.4, 48 + i);
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            int x = (int) Math.round(Math.cos(angle) * 17);
            int z = (int) Math.round(Math.sin(angle) * 17);
            put(x, 1, z, "stone_brick_wall[up=true]");
            put(x, 2, z, "lantern[hanging=false]");
        }
    }

    private void iceSpike(int x, int base, int z, int height, double width, int salt) {
        for (int y = 1; y <= height; y++) {
            double t = (double) y / height;
            double r = width * (1 - t) + 0.3;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dist(dx, dz) <= r) {
                        put(x + dx, base + y, z + dz, noise(x + dx, z + dz + y, salt) < 0.6 ? "packed_ice" : "blue_ice");
                    }
                }
            }
        }
    }

    private void tower() {
        for (int y = 1; y < DECK; y++) {
            double r = TOWER + (y >= DECK - 2 ? (y - (DECK - 3)) * 0.9 : 0);
            int ri = (int) Math.ceil(r);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double d = dist(x, z);
                    if (d > r) {
                        continue;
                    }
                    String block;
                    if (d > r - 1.2) {
                        double angle = Math.atan2(z, x);
                        boolean rib = Math.abs(Math.sin(angle * 6)) > 0.93;
                        block = rib ? "polished_diorite" : (y % 3 == 0 ? "light_blue_concrete" : "packed_ice");
                    } else {
                        block = "stone_bricks";
                    }
                    put(x, y, z, block);
                }
            }
        }
    }

    private void deck() {
        int ri = (int) Math.ceil(DECK_RADIUS);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = dist(x, z);
                if (d > DECK_RADIUS) {
                    continue;
                }
                String top;
                if (d < 1.6) {
                    top = "sea_lantern";
                } else if (d < 3.2) {
                    top = "light_blue_concrete";
                } else if (d > DECK_RADIUS - 1.1) {
                    top = "polished_diorite";
                } else {
                    top = (Math.floorMod(x + z, 2) == 0) ? "smooth_quartz" : "calcite";
                }
                put(x, DECK, z, top);
                put(x, DECK - 1, z, "packed_ice");
            }
        }
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d = dist(x, z);
                if (d <= DECK_RADIUS - 1.1 || d > DECK_RADIUS) {
                    continue;
                }
                boolean gap = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                if (!gap) {
                    put(x, DECK + 1, z, "diorite_wall");
                }
            }
        }
        int[][] posts = {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}};
        for (int[] post : posts) {
            put(post[0], DECK + 1, post[1], "polished_diorite");
            put(post[0], DECK + 2, post[1], "diorite_wall");
            put(post[0], DECK + 3, post[1], "lantern[hanging=false]");
        }
    }
}
