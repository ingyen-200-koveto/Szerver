package hu.icemine.core.modelobby;

/**
 * A SkyWars lobby körüli lebegő havas szigetek: mindegyiken egy feloldható ketrec (jég, smaragd, TNT, arany...),
 * láda, kis fenyő és érc, ahogy egy SkyWars pálya kezdőszigetei; köztük lebegő jégtüskék.
 */
final class SkywarsIslands extends ThemeBase {

    /** {padló, fal, tető} a ketrec stílusokból. */
    private static final String[][] CAGES = {
            {"packed_ice", "light_blue_stained_glass", "blue_ice"},
            {"emerald_block", "lime_stained_glass", "emerald_block"},
            {"tnt", "red_stained_glass", "tnt"},
            {"gold_block", "yellow_stained_glass", "gold_block"},
            {"amethyst_block", "purple_stained_glass", "amethyst_block"},
            {"prismarine", "cyan_stained_glass", "sea_lantern"},
            {"snow_block", "white_stained_glass", "snow_block"},
            {"redstone_block", "red_stained_glass", "redstone_lamp[lit=true]"}
    };

    SkywarsIslands(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    @Override
    public void build() {
        for (int i = 0; i < CAGES.length; i++) {
            double a = Math.PI * 2 * i / CAGES.length + Math.PI / 8;
            int ix = (int) Math.round(Math.cos(a) * 40);
            int iz = (int) Math.round(Math.sin(a) * 40);
            int iy = (int) Math.round(Math.sin(i * 1.7) * 4);
            island(ix, iy, iz, 5.2);
            cage(ix, iy, iz, CAGES[i]);
            String toward = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
            int sx = ix - (int) Math.round(Math.cos(a) * 3);
            int sz = iz - (int) Math.round(Math.sin(a) * 3);
            put(sx, iy + 1, sz, "chest[facing=" + toward + "]");
            int tx = ix + (int) Math.round(-Math.sin(a) * 3);
            int tz = iz + (int) Math.round(Math.cos(a) * 3);
            fir(tx, iy, tz, 5);
            int ox = ix + (int) Math.round(Math.sin(a) * 3);
            int oz = iz + (int) Math.round(-Math.cos(a) * 3);
            put(ox, iy + 1, oz, i % 2 == 0 ? "iron_ore" : "coal_ore");
            put(ox, iy + 2, oz, "snow[layers=2]");
        }
        int[][] spikes = {{-20, -38}, {22, 37}, {-37, 20}, {38, -20}, {0, 44}, {-44, -2}, {43, 4}, {6, -44}};
        for (int i = 0; i < spikes.length; i++) {
            spike(spikes[i][0], -4 + (int) (noise(i, 7, 560) * 8), spikes[i][1]);
        }
    }

    private void island(int ix, int iy, int iz, double r) {
        int ri = (int) Math.ceil(r) + 1;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = dist(dx, dz);
                double edge = r + 0.6 * Math.sin(Math.atan2(dz, dx) * 4 + ix);
                if (d > edge) {
                    continue;
                }
                double t = 1 - d / edge;
                int depth = 2 + (int) Math.round(t * t * 9 + noise(ix + dx, iz + dz, 561) * 3 * t);
                for (int y = -depth; y <= 0; y++) {
                    String block;
                    double n = noise(ix + dx + y, iz + dz, 562);
                    if (y == 0) {
                        block = n < 0.15 ? "powder_snow" : "snow_block";
                    } else if (y > -2) {
                        block = "dirt";
                    } else if (y < -depth + 2) {
                        block = n < 0.5 ? "blue_ice" : "packed_ice";
                    } else {
                        block = n < 0.45 ? "stone" : (n < 0.75 ? "packed_ice" : "andesite");
                    }
                    put(ix + dx, iy + y, iz + dz, block);
                }
            }
        }
    }

    private void cage(int x, int y, int z, String[] style) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(x + dx, y + 1, z + dz, style[0]);
                put(x + dx, y + 6, z + dz, style[2]);
                for (int dy = 2; dy <= 5; dy++) {
                    boolean wall = dx != 0 || dz != 0;
                    put(x + dx, y + dy, z + dz, wall ? style[1] : "air");
                }
            }
        }
    }

    private void fir(int x, int y, int z, int height) {
        for (int dy = 1; dy <= height; dy++) {
            put(x, y + dy, z, "spruce_log[axis=y]");
        }
        for (int dy = 2; dy <= height + 1; dy++) {
            int r = dy >= height ? 0 : (dy % 2 == 0 ? 2 : 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0 || dy > height) && Math.abs(dx) + Math.abs(dz) <= r + (r == 2 ? 1 : 0)) {
                        put(x + dx, y + dy, z + dz, "spruce_leaves[persistent=true]");
                        if (dy % 2 == 0 && Math.abs(dx) + Math.abs(dz) >= r) {
                            put(x + dx, y + dy + 1, z + dz, "snow[layers=1]");
                        }
                    }
                }
            }
        }
        put(x, y + height + 2, z, "snow[layers=2]");
    }

    private void spike(int x, int y, int z) {
        int height = 6 + (int) (noise(x, z, 563) * 6);
        for (int dy = -height; dy <= height / 2; dy++) {
            double r = dy < 0 ? 1.8 * (1 + dy / (double) height) + 0.3 : 1.8 * (1 - dy / (height / 2.0 + 1)) + 0.3;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dist(dx, dz) <= r) {
                        put(x + dx, y + dy, z + dz, noise(x + dx, y + dy + z + dz, 564) < 0.4 ? "packed_ice" : "blue_ice");
                    }
                }
            }
        }
    }
}
