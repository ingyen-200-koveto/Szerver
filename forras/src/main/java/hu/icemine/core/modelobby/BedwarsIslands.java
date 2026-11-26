package hu.icemine.core.modelobby;

/**
 * A BedWars lobby körüli szigetek: négy csapatsziget (ház, ágy védelemmel, vasgenerátor, bolt), két gyémántsziget,
 * gyapjúhidak a középső szigethez, ahogy egy igazi BedWars pályán.
 */
final class BedwarsIslands extends ThemeBase {

    static final double TEAM_D = 44;
    static final double DIAMOND_D = 44;
    static final double MAIN_R = 26;
    static final String[] COLORS = {"red", "blue", "lime", "yellow"};
    /** Csapatszigetek iránya (szög) és a gyémántszigeteké. */
    static final double[] TEAM_ANGLES = {Math.toRadians(225), Math.toRadians(315), Math.toRadians(135), Math.toRadians(45)};
    static final double[] DIAMOND_ANGLES = {0, Math.PI};

    BedwarsIslands(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    /** Híd megy-e erre (a középső sziget peremén itt nincs korlát). */
    static boolean bridge(int x, int z) {
        double a = Math.atan2(z, x);
        double d = dist(x, z);
        for (double t : TEAM_ANGLES) {
            if (Math.abs(Math.IEEEremainder(a - t, Math.PI * 2)) * d < 2.6) {
                return true;
            }
        }
        for (double t : DIAMOND_ANGLES) {
            if (Math.abs(Math.IEEEremainder(a - t, Math.PI * 2)) * d < 2.6) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void build() {
        for (int i = 0; i < 4; i++) {
            int tx = (int) Math.round(Math.cos(TEAM_ANGLES[i]) * TEAM_D);
            int tz = (int) Math.round(Math.sin(TEAM_ANGLES[i]) * TEAM_D);
            island(tx, tz, 8.5, COLORS[i] + "_terracotta");
            bridgeTo(TEAM_ANGLES[i], MAIN_R - 2, TEAM_D - 8, COLORS[i] + "_wool");
            base(tx, tz, TEAM_ANGLES[i], COLORS[i]);
        }
        for (double a : DIAMOND_ANGLES) {
            int tx = (int) Math.round(Math.cos(a) * DIAMOND_D);
            int tz = (int) Math.round(Math.sin(a) * DIAMOND_D);
            island(tx, tz, 5.5, "light_blue_terracotta");
            bridgeTo(a, MAIN_R - 2, DIAMOND_D - 5, "white_wool");
            generator(tx, tz, "diamond_block", "light_blue_stained_glass");
        }
        int[][] rocks = {{-12, -40}, {14, 41}, {-41, 16}, {40, -15}};
        for (int[] r : rocks) {
            rock(r[0], r[1]);
        }
    }

    private void island(int ix, int iz, double r, String trim) {
        int ri = (int) Math.ceil(r) + 1;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = dist(dx, dz);
                double edge = r + 0.6 * Math.sin(Math.atan2(dz, dx) * 5 + ix);
                if (d > edge) {
                    continue;
                }
                double t = 1 - d / edge;
                int depth = 2 + (int) Math.round(t * t * 11 + noise(ix + dx, iz + dz, 550) * 3 * t);
                int x = ix + dx;
                int z = iz + dz;
                for (int y = -depth; y <= 0; y++) {
                    String block;
                    if (y == 0) {
                        block = d > edge - 1.2 ? trim : (noise(x, z, 551) < 0.08 ? "moss_block" : "grass_block");
                    } else if (y > -3) {
                        block = "dirt";
                    } else {
                        double n = noise(x + y, z, 552);
                        block = n < 0.5 ? "stone" : (n < 0.75 ? "andesite" : (n < 0.85 ? "iron_ore" : "cobblestone"));
                    }
                    put(x, y, z, block);
                }
                if (d > edge - 1.2) {
                    for (int y = 1; y <= 4; y++) {
                        put(x, y, z, "barrier");
                    }
                } else if (noise(x, z, 553) < 0.12) {
                    put(x, 1, z, "short_grass");
                }
            }
        }
    }

    private void bridgeTo(double angle, double from, double to, String wool) {
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        for (double s = from; s <= to + 1.5; s += 0.4) {
            for (double w = -1.5; w <= 1.5; w += 0.5) {
                int x = (int) Math.round(ca * s - sa * w);
                int z = (int) Math.round(sa * s + ca * w);
                boolean edge = Math.abs(w) >= 1.5;
                put(x, 0, z, wool);
                put(x, 1, z, "air");
                for (int y = 2; y <= 4; y++) {
                    put(x, y, z, "air");
                }
                if (edge && s > MAIN_R - 0.5 && s < to - 0.5) {
                    for (int y = 1; y <= 4; y++) {
                        put(x, y, z, "barrier");
                    }
                }
            }
        }
    }

    /** Csapatbázis: ház a sziget külső felén, előtte az ágy védelemmel, oldalt vasgenerátor és bolt. */
    private void base(int tx, int tz, double angle, String color) {
        double ox = Math.cos(angle);
        double oz = Math.sin(angle);
        String in = Math.abs(ox) > Math.abs(oz) ? (ox > 0 ? "west" : "east") : (oz > 0 ? "north" : "south");
        int hx = tx + (int) Math.round(ox * 3.5);
        int hz = tz + (int) Math.round(oz * 3.5);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int y = 1; y <= 4; y++) {
                    String block;
                    if (!wall) {
                        block = "air";
                    } else if (corner) {
                        block = "stripped_oak_log[axis=y]";
                    } else if (y == 2 || y == 3) {
                        block = Math.abs(dx) == 1 || Math.abs(dz) == 1 ? color + "_stained_glass" : color + "_wool";
                    } else {
                        block = y == 1 ? color + "_terracotta" : color + "_wool";
                    }
                    put(hx + dx, y, hz + dz, block);
                }
                put(hx + dx, 0, hz + dz, "oak_planks");
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring <= 4) {
                    put(hx + dx, 5 + Math.max(0, 3 - ring), hz + dz, ring == 4 ? "oak_slab[type=bottom]" : (ring == 0 ? color + "_wool" : "oak_planks"));
                }
            }
        }
        int[] door = step(in);
        for (int y = 1; y <= 2; y++) {
            put(hx + door[0] * 3, y, hz + door[1] * 3, "air");
        }
        put(hx - door[0], 1, hz - door[1], "chest[facing=" + in + "]");
        put(hx - door[0] + 1, 1, hz - door[1], "ender_chest[facing=" + in + "]");
        put(hx, 3, hz, "lantern[hanging=true]");
        int bx = tx - (int) Math.round(ox * 3);
        int bz = tz - (int) Math.round(oz * 3);
        String out = opposite(in);
        int[] o = step(out);
        put(bx, 1, bz, color + "_bed[part=foot,facing=" + out + "]");
        put(bx + o[0], 1, bz + o[1], color + "_bed[part=head,facing=" + out + "]");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int px = bx + dx;
                int pz = bz + dz;
                int ex = px - bx;
                int ez = pz - bz;
                boolean onBed = (ex == 0 && ez == 0) || (ex == o[0] && ez == o[1]);
                if (onBed) {
                    put(px, 2, pz, "end_stone");
                    continue;
                }
                int dist = Math.max(Math.min(Math.abs(ex), Math.abs(ex - o[0])), Math.min(Math.abs(ez), Math.abs(ez - o[1])));
                if (dist == 1) {
                    put(px, 1, pz, color + "_wool");
                }
            }
        }
        int sx = tx + (int) Math.round(-oz * 5);
        int sz = tz + (int) Math.round(ox * 5);
        generator(sx, sz, "iron_block", "gold_block");
        int kx = tx + (int) Math.round(oz * 5);
        int kz = tz + (int) Math.round(-ox * 5);
        put(kx, 1, kz, "crafting_table");
        put(kx, 2, kz, color + "_banner[rotation=0]");
        put(kx + 1, 1, kz, "smithing_table");
        put(kx - 1, 1, kz, "barrel[facing=up]");
        put(kx, 1, kz + 1, "anvil[facing=north]");
        put(kx - 1, 2, kz, "lantern[hanging=false]");
    }

    private void generator(int gx, int gz, String core, String trim) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(gx + dx, 0, gz + dz, dx == 0 && dz == 0 ? core : trim);
            }
        }
        put(gx, 1, gz, core.startsWith("diamond") ? "heavy_weighted_pressure_plate" : "light_weighted_pressure_plate");
    }

    private void rock(int rx, int rz) {
        int y0 = -6 + (int) (noise(rx, rz, 554) * 5);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double d = dist(dx, dz);
                if (d > 2.4) {
                    continue;
                }
                int depth = (int) Math.round((2.5 - d) * 1.6);
                for (int y = y0 - depth; y <= y0; y++) {
                    put(rx + dx, y, rz + dz, y == y0 ? "grass_block" : (noise(rx + dx, y, 555) < 0.5 ? "stone" : "andesite"));
                }
            }
        }
    }

    private static int[] step(String facing) {
        switch (facing) {
            case "north":
                return new int[]{0, -1};
            case "south":
                return new int[]{0, 1};
            case "east":
                return new int[]{1, 0};
            default:
                return new int[]{-1, 0};
        }
    }
}
