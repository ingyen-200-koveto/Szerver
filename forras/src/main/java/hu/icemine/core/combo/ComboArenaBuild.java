package hu.icemine.core.combo;

import hu.icemine.core.build.Build;

public final class ComboArenaBuild extends Build {

    public static final int CLEAR = 42;
    static final double DOME = 23.0;
    static final double HEIGHT = 17.0;
    static final int ISLAND = 31;
    static final int BALCONY_Y = 5;
    static final int BALCONY_Z = -16;

    public ComboArenaBuild(Sink sink, int cx, int cy, int cz) {
        super(sink, cx, cy, cz);
    }

    @Override
    public void build() {
        island();
        floor();
        dome();
        balcony();
        crystals();
        spikes();
        layout.point("spawn", 0.5, BALCONY_Y + 1, BALCONY_Z + 0.5, 0);
        layout.point("safe", 0.5, BALCONY_Y + 1, BALCONY_Z + 0.5, 7.5);
    }

    private static double shape(double x, double y, double z) {
        return Math.sqrt((x * x + z * z) / (DOME * DOME) + (y * y) / (HEIGHT * HEIGHT));
    }

    private static boolean inside(int x, int y, int z) {
        return shape(x, y, z) <= 1.0;
    }

    private static boolean shell(int x, int y, int z) {
        return inside(x, y, z) && (!inside(x + 1, y, z) || !inside(x - 1, y, z) || !inside(x, y + 1, z) || !inside(x, y, z + 1) || !inside(x, y, z - 1));
    }

    private static boolean open(int x, int y, int z) {
        return inside(x, y, z) && !shell(x, y, z);
    }

    private void island() {
        for (int x = -ISLAND - 3; x <= ISLAND + 3; x++) {
            for (int z = -ISLAND - 3; z <= ISLAND + 3; z++) {
                double d = dist(x, z);
                double angle = Math.atan2(z, x);
                double edge = ISLAND + 1.6 * Math.sin(angle * 5 + 2) + 0.9 * Math.sin(angle * 12 + 0.4);
                if (d > edge) {
                    continue;
                }
                if (d > DOME + 0.5) {
                    put(x, 0, z, noise(x, z, 61) < 0.15 ? "packed_ice" : "snow_block");
                    if (noise(x, z, 62) < 0.18) {
                        put(x, 1, z, "snow[layers=" + (1 + (int) (noise(x, z, 63) * 3)) + "]");
                    }
                }
                int deep = (int) ((1 - Math.pow(d / edge, 1.7)) * 18) + 2 + (int) (noise(x, z, 64) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(x, z * 3 + l, 65 + l);
                    String block;
                    if (l == 1) {
                        block = "snow_block";
                    } else if (l <= 3) {
                        block = m < 0.5 ? "packed_ice" : "calcite";
                    } else if (m < 0.35) {
                        block = "packed_ice";
                    } else if (m < 0.6) {
                        block = "blue_ice";
                    } else if (m < 0.82) {
                        block = "stone";
                    } else {
                        block = "diorite";
                    }
                    put(x, -l, z, block);
                }
                if (noise(x, z, 66) < 0.07 && deep > 5) {
                    int length = 2 + (int) (noise(x, z, 67) * 7);
                    for (int l = 1; l <= length; l++) {
                        put(x, -deep - l, z, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }

    private void floor() {
        int r = (int) Math.ceil(DOME);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d > DOME + 0.5) {
                    continue;
                }
                double n = noise(x, z, 70);
                String block = n < 0.45 ? "calcite" : (n < 0.8 ? "white_concrete" : "smooth_quartz");
                if (Math.abs(d - 18.5) < 0.55) {
                    block = "light_blue_concrete";
                } else if (Math.abs(d - 20) < 0.5) {
                    block = "cyan_concrete";
                }
                put(x, 0, z, block);
                for (int y = 1; y <= 20; y++) {
                    if (inside(x, y, z)) {
                        put(x, y, z, "air");
                    }
                }
            }
        }
        disc(0, 0, 0, 3.6, "light_blue_concrete");
        disc(0, 0, 0, 1.5, "sea_lantern");
        for (int arm = 0; arm < 6; arm++) {
            double angle = Math.toRadians(arm * 60 + 30);
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            for (double t = 3.0; t <= 14.0; t += 0.5) {
                int width = t < 9 ? 1 : 0;
                for (int s = -width; s <= width; s++) {
                    put((int) Math.round(ux * t - uz * s * 0.9), 0, (int) Math.round(uz * t + ux * s * 0.9), "light_blue_concrete");
                }
            }
            put((int) Math.round(ux * 14.5), 0, (int) Math.round(uz * 14.5), "sea_lantern");
            for (double base : new double[]{7.0, 11.0}) {
                for (int side = -1; side <= 1; side += 2) {
                    double branch = angle + side * Math.toRadians(50);
                    for (double t = 0.5; t <= (base < 8 ? 3.5 : 2.5); t += 0.5) {
                        int x = (int) Math.round(ux * base + Math.cos(branch) * t);
                        int z = (int) Math.round(uz * base + Math.sin(branch) * t);
                        put(x, 0, z, "cyan_concrete");
                    }
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 + 15);
            put((int) Math.round(Math.cos(angle) * 18.5), 0, (int) Math.round(Math.sin(angle) * 18.5), "sea_lantern");
        }
    }

    private void dome() {
        int r = (int) Math.ceil(DOME) + 1;
        int top = (int) Math.ceil(HEIGHT) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 1; y <= top; y++) {
                    if (!shell(x, y, z)) {
                        continue;
                    }
                    double h = dist(x, z);
                    double degrees = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
                    double off = Math.abs(((degrees + 22.5) % 45) - 22.5);
                    boolean rib = h > 2 && off * Math.PI / 180 * h < 0.75;
                    String block;
                    if (y <= 3) {
                        block = y == 1 ? "packed_ice" : (rib ? "sea_lantern" : (noise(x, z + y, 71) < 0.5 ? "packed_ice" : "blue_ice"));
                    } else if (rib) {
                        block = y % 5 == 0 ? "sea_lantern" : "white_stained_glass";
                    } else if (y >= HEIGHT - 1 && h < 3) {
                        block = "white_stained_glass";
                    } else {
                        block = noise(x * 7 + y, z, 72) < 0.22 ? "glass" : "light_blue_stained_glass";
                    }
                    put(x, y, z, block);
                }
            }
        }
        put(0, (int) HEIGHT, 0, "sea_lantern");
        put(0, (int) HEIGHT + 1, 0, "end_rod[facing=up]");
        put(0, (int) HEIGHT - 1, 0, "end_rod[facing=down]");
    }

    private void balcony() {
        int y = BALCONY_Y;
        int front = BALCONY_Z + 4;
        for (int x = -6; x <= 6; x++) {
            for (int z = BALCONY_Z - 6; z <= front; z++) {
                if (!inside(x, y, z)) {
                    continue;
                }
                boolean edge = Math.abs(x) == 6 || z == front;
                put(x, y, z, edge ? "blue_ice" : (Math.floorMod(x + z, 2) == 0 ? "calcite" : "polished_diorite"));
                for (int down = y - 1; down >= 1; down--) {
                    put(x, down, z, down == y - 1 ? "packed_ice" : (Math.floorMod(x, 4) == 0 && z == front ? "quartz_pillar[axis=y]" : "quartz_bricks"));
                }
                for (int up = 1; up <= 4; up++) {
                    if (open(x, y + up, z)) {
                        put(x, y + up, z, "air");
                    }
                }
                if (open(x, y + 1, z) && ((z == front && Math.abs(x) > 2) || Math.abs(x) == 6)) {
                    put(x, y + 1, z, "diorite_wall[up=true]");
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 5;
            int z = front - 1;
            put(x, y + 1, z, "quartz_pillar[axis=y]");
            put(x, y + 2, z, "quartz_pillar[axis=y]");
            put(x, y + 3, z, "sea_lantern");
            put(x, y + 4, z, "end_rod[facing=up]");
        }
        for (int step = 1; step <= y; step++) {
            int z = front + step;
            int sy = y - step;
            for (int w = -2; w <= 2; w++) {
                if (sy >= 1) {
                    put(w, sy, z, stairs("quartz", "north", false));
                    for (int down = sy - 1; down >= 1; down--) {
                        put(w, down, z, "quartz_bricks");
                    }
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                if (sy >= 1) {
                    put(side * 3, sy, z, "quartz_bricks");
                    put(side * 3, sy + 1, z, "diorite_wall[up=true]");
                    for (int down = sy - 1; down >= 1; down--) {
                        put(side * 3, down, z, "quartz_bricks");
                    }
                }
            }
        }
    }

    private void crystals() {
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 + 22.5);
            int x = (int) Math.round(Math.cos(angle) * 21);
            int z = (int) Math.round(Math.sin(angle) * 21);
            if (z < -12 && Math.abs(x) < 10) {
                continue;
            }
            int height = 2 + (int) (noise(x, z, 80) * 3);
            for (int h = 1; h <= height; h++) {
                put(x, h, z, h == 1 ? "packed_ice" : (h == height ? "light_blue_stained_glass" : "blue_ice"));
            }
            int ox = x - (int) Math.signum(x);
            int oz = z - (int) Math.signum(z);
            put(ox, 1, z, "blue_ice");
            put(x, 1, oz, "packed_ice");
            put(x, height + 1, z, "end_rod[facing=up]");
        }
    }

    private void spikes() {
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(i * 36 + 9);
            double radius = 27.5 + noise(i, 3, 81) * 1.5;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            int height = 7 + (int) (noise(i, 4, 82) * 8);
            double width = 1.6 + noise(i, 5, 83) * 0.8;
            for (int h = 0; h < height; h++) {
                double r = width * (1 - (double) h / height);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dist(dx, dz) <= r + 0.3) {
                            put(x + dx, 1 + h, z + dz, noise(x + dx, h + dz, 84) < 0.3 || h > height * 0.6 ? "blue_ice" : "packed_ice");
                        }
                    }
                }
            }
            put(x, height + 1, z, "light_blue_stained_glass");
        }
    }
}
