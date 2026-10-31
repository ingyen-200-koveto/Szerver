package hu.icemine.kingdoms;

abstract class Build {

    interface Sink {
        void set(int x, int y, int z, String data);
    }

    private final Sink sink;
    protected final int cx;
    protected final int cy;
    protected final int cz;

    Build(Sink sink, int cx, int cy, int cz) {
        this.sink = sink;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
    }

    abstract void build();

    protected void put(int dx, int dy, int dz, String data) {
        sink.set(cx + dx, cy + dy, cz + dz, data);
    }

    protected void fill(int x0, int y0, int z0, int x1, int y1, int z1, String data) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    put(x, y, z, data);
                }
            }
        }
    }

    protected static double noise(int x, int z, int salt) {
        long h = x * 341873128712L + z * 132897987541L + salt * 6364136223846793005L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (h >>> 11) / (double) (1L << 53);
    }

    protected static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    protected static String stairs(String material, String facing, boolean top) {
        return material + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    protected static String slab(String material, boolean top) {
        return material + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    protected static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    protected static String outward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    protected static String opposite(String facing) {
        switch (facing) {
            case "north":
                return "south";
            case "south":
                return "north";
            case "east":
                return "west";
            default:
                return "east";
        }
    }

    protected String brick(int x, int y, int z) {
        double n = noise(x * 7 + y, z * 3 - y, 91);
        return n < 0.12 ? "cracked_deepslate_bricks" : "deepslate_bricks";
    }

    protected String white(int x, int y, int z) {
        double n = noise(x + y * 5, z - y * 3, 92);
        if (n < 0.45) {
            return "quartz_bricks";
        }
        return n < 0.8 ? "calcite" : "smooth_quartz";
    }

    protected void cone(int tx, int baseY, int tz, double radius, int height, String stairs, String full, String tip) {
        for (int h = 0; h < height; h++) {
            double r = radius * (1 - (double) h / height);
            double next = radius * (1 - (double) (h + 1) / height);
            int ri = (int) Math.ceil(r) + 1;
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = dist(dx, dz);
                    if (d > r + 0.35) {
                        continue;
                    }
                    if (d > next + 0.35 && (dx != 0 || dz != 0)) {
                        put(tx + dx, baseY + h, tz + dz, stairs(stairs, inward(dx, dz), false));
                    } else {
                        put(tx + dx, baseY + h, tz + dz, full);
                    }
                }
            }
        }
        put(tx, baseY + height, tz, full);
        if (tip != null) {
            put(tx, baseY + height + 1, tz, tip);
        }
    }

    protected void pyramid(int x0, int z0, int x1, int z1, int baseY, String stairs, String full, String tip) {
        int h = 0;
        while (x0 + h <= x1 - h && z0 + h <= z1 - h) {
            int ax = x0 + h;
            int bx = x1 - h;
            int az = z0 + h;
            int bz = z1 - h;
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean edge = x == ax || x == bx || z == az || z == bz;
                    if (!edge) {
                        put(x, baseY + h, z, full);
                        continue;
                    }
                    String facing;
                    if (z == az && x != ax && x != bx) {
                        facing = "south";
                    } else if (z == bz && x != ax && x != bx) {
                        facing = "north";
                    } else if (x == ax) {
                        facing = "east";
                    } else {
                        facing = "west";
                    }
                    if (ax == bx || az == bz) {
                        put(x, baseY + h, z, full);
                    } else {
                        put(x, baseY + h, z, stairs(stairs, facing, false));
                    }
                }
            }
            h++;
        }
        if (tip != null) {
            put((x0 + x1) / 2, baseY + h, (z0 + z1) / 2, tip);
        }
    }

    protected void spruce(int tx, int tz, int height, double width) {
        for (int y = 1; y <= height; y++) {
            put(tx, y, tz, "spruce_log");
        }
        int[][] top = new int[13][13];
        int layers = height - 2;
        for (int i = 0; i < layers; i++) {
            int y = 3 + i;
            double progress = (double) i / layers;
            double r = width * (1 - progress) + (i % 2 == 0 ? 0.6 : -0.2);
            if (r < 0.6) {
                r = 0.6;
            }
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx == 0 && dz == 0 && y <= height) {
                        continue;
                    }
                    if (dist(dx, dz) <= r) {
                        put(tx + dx, y, tz + dz, "spruce_leaves[persistent=true]");
                        top[dx + 6][dz + 6] = y;
                    }
                }
            }
        }
        put(tx, height + 1, tz, "spruce_leaves[persistent=true]");
        put(tx, height + 2, tz, "spruce_leaves[persistent=true]");
        top[6][6] = height + 2;
        for (int dx = 0; dx < 13; dx++) {
            for (int dz = 0; dz < 13; dz++) {
                if (top[dx][dz] > 0) {
                    put(tx + dx - 6, top[dx][dz] + 1, tz + dz - 6, "snow[layers=" + (1 + (int) (noise(tx + dx, tz + dz, 31) * 3)) + "]");
                }
            }
        }
    }

    protected void lampPost(int x, int z, int height, String lantern) {
        for (int y = 1; y <= height; y++) {
            put(x, y, z, "deepslate_brick_wall[up=true]");
        }
        put(x, height + 1, z, lantern + "[hanging=false]");
    }

    protected void iceSpike(int x, int z, int height, double width, int salt) {
        for (int h = 0; h < height; h++) {
            double r = width * (1 - (double) h / height);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dist(dx, dz) <= r + 0.3) {
                        put(x + dx, 1 + h, z + dz, noise(x + dx, h + dz, salt) < 0.3 || h > height * 0.6 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }

    protected void boulder(int x, int z, double radius, int salt) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= r; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                    if (d <= radius - noise(x + dx, z + dz + dy, salt) * 0.8) {
                        String m = noise(x + dx, dz + dy * 3, salt + 1) < 0.5 ? "cobbled_deepslate" : "deepslate";
                        put(x + dx, 1 + dy, z + dz, m);
                    }
                }
            }
        }
        put(x, 2 + (int) (radius * 0.6), z, "snow[layers=2]");
    }

    protected void island(int radius, double wobble, int depth, int salt) {
        int max = radius + 4;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + wobble * Math.sin(angle * 5 + salt) + wobble * 0.6 * Math.sin(angle * 11 + 1.3 + salt);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.7)) * depth) + 1 + (int) (noise(dx, dz, salt + 2) * 2.5);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(dx, dz, salt + 10 + l);
                    String block;
                    if (l <= 1) {
                        block = "snow_block";
                    } else if (l <= 3) {
                        block = m < 0.6 ? "dirt" : "cobbled_deepslate";
                    } else if (m < 0.45) {
                        block = "deepslate";
                    } else if (m < 0.7) {
                        block = "cobbled_deepslate";
                    } else if (m < 0.88) {
                        block = "packed_ice";
                    } else {
                        block = "blue_ice";
                    }
                    put(dx, -l, dz, block);
                }
                if (noise(dx, dz, salt + 3) < 0.05 && deep > 3) {
                    int length = 2 + (int) (noise(dx, dz, salt + 4) * 5);
                    for (int l = 1; l <= length; l++) {
                        put(dx, -deep - l, dz, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }
}
