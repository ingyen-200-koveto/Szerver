package hu.icemine.core.build;

public abstract class Build {

    public interface Sink {
        void set(int x, int y, int z, String data);
    }

    private final Sink sink;
    protected final int cx;
    protected final int cy;
    protected final int cz;
    protected final Layout layout = new Layout();

    protected Build(Sink sink, int cx, int cy, int cz) {
        this.sink = sink;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
    }

    public abstract void build();

    public Layout layout() {
        return layout;
    }

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

    protected static double smooth(double x, double z, double scale, int salt) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = noise(x0, z0, salt);
        double b = noise(x0 + 1, z0, salt);
        double c = noise(x0, z0 + 1, salt);
        double d = noise(x0 + 1, z0 + 1, salt);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
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
        return opposite(inward(dx, dz));
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

    protected void disc(int tx, int y, int tz, double radius, String data) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dist(dx, dz) <= radius) {
                    put(tx + dx, y, tz + dz, data);
                }
            }
        }
    }

    protected void ring(int tx, int y, int tz, double inner, double outer, String data) {
        int r = (int) Math.ceil(outer);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d > inner && d <= outer) {
                    put(tx + dx, y, tz + dz, data);
                }
            }
        }
    }

    protected void line(int x0, int y0, int z0, int x1, int y1, int z1, String data) {
        int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            put((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), (int) Math.round(z0 + (z1 - z0) * t), data);
        }
    }
}
