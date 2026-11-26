package hu.icemine.core.modelobby;

/**
 * A BedWars lobby körüli szigetek: négy csapatsziget (ház, ágy védelemmel, vasgenerátor, bolt), két gyémántsziget,
 * gyapjúhidak a középső szigethez, ahogy egy igazi BedWars pályán.
 */
final class BedwarsIslands extends ThemeBase {

    static final double TEAM_D = 50;
    static final double DIAMOND_D = 46;
    static final double MAIN_R = 30;
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
            island(tx, tz, 10.5, COLORS[i] + "_terracotta");
            bridgeTo(TEAM_ANGLES[i], MAIN_R - 2, TEAM_D - 10, COLORS[i] + "_wool");
            base(tx, tz, TEAM_ANGLES[i], COLORS[i]);
        }
        for (double a : DIAMOND_ANGLES) {
            int tx = (int) Math.round(Math.cos(a) * DIAMOND_D);
            int tz = (int) Math.round(Math.sin(a) * DIAMOND_D);
            island(tx, tz, 5.5, "light_blue_terracotta");
            bridgeTo(a, MAIN_R - 2, DIAMOND_D - 5, "white_wool");
            generator(tx, tz, "diamond_block", "light_blue_stained_glass");
            shrine(tx, tz);
        }
        bigBed(-18, 16);
        for (int side = -1; side <= 1; side += 2) {
            emeraldShrine(side * 9, -17);
        }
        int[][] rocks = {{-14, -42}, {16, 43}, {-43, 18}, {42, -17}};
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

    /** Csapatbázis: ház a sziget külső felén, előtte az ágy félig felépített védelemmel, oldalt vasgenerátor és bolt. */
    private void base(int tx, int tz, double angle, String color) {
        double ox = Math.cos(angle);
        double oz = Math.sin(angle);
        String in = Math.abs(ox) > Math.abs(oz) ? (ox > 0 ? "west" : "east") : (oz > 0 ? "north" : "south");
        int[] door = step(in);
        int hx = tx + (int) Math.round(ox * 4.5);
        int hz = tz + (int) Math.round(oz * 4.5);
        house(hx, hz, color, in);
        int bx = tx - (int) Math.round(ox * 3);
        int bz = tz - (int) Math.round(oz * 3);
        String out = opposite(in);
        int[] o = step(out);
        put(bx, 1, bz, color + "_bed[part=foot,facing=" + out + "]");
        put(bx + o[0], 1, bz + o[1], color + "_bed[part=head,facing=" + out + "]");
        defense(bx, bz, o, color);
        int sx = tx + (int) Math.round(-oz * 6);
        int sz = tz + (int) Math.round(ox * 6);
        forge(sx, sz, color);
        int kx = tx + (int) Math.round(oz * 6);
        int kz = tz + (int) Math.round(-ox * 6);
        shop(kx, kz, color, in);
        int fx = tx - (int) Math.round(ox * 7) + (int) Math.round(-oz * 3);
        int fz = tz - (int) Math.round(oz * 7) + (int) Math.round(ox * 3);
        flag(fx, fz, color);
    }

    private void house(int hx, int hz, String color, String in) {
        int[] door = step(in);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                put(hx + dx, 0, hz + dz, "oak_planks");
                for (int y = 1; y <= 5; y++) {
                    String block;
                    if (!wall) {
                        block = "air";
                    } else if (corner) {
                        block = "quartz_pillar[axis=y]";
                    } else if (y == 1) {
                        block = "stone_bricks";
                    } else if (y == 5) {
                        block = Math.abs(dx) == 3 ? "stripped_oak_log[axis=z]" : "stripped_oak_log[axis=x]";
                    } else {
                        boolean window = (y == 3) && (Math.abs(dx) == 1 || Math.abs(dz) == 1);
                        block = window ? color + "_stained_glass" : color + "_concrete";
                    }
                    put(hx + dx, y, hz + dz, block);
                }
            }
        }
        for (int k = 0; k <= 4; k++) {
            int r = 4 - k;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                    if (!edge && r > 0) {
                        continue;
                    }
                    String toward = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    put(hx + dx, 6 + k, hz + dz, r == 0 ? color + "_wool" : "dark_oak_stairs[facing=" + toward + ",half=bottom]");
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(hx + dx, 6, hz + dz, "dark_oak_planks");
            }
        }
        for (int y = 1; y <= 3; y++) {
            put(hx + door[0] * 3, y, hz + door[1] * 3, "air");
        }
        put(hx + door[0] * 4, 0, hz + door[1] * 4, "oak_planks");
        put(hx + door[0] * 3 + door[1], 4, hz + door[1] * 3 + door[0], "lantern[hanging=false]");
        put(hx - door[0], 1, hz - door[1], "chest[facing=" + in + "]");
        put(hx - door[0] + door[1], 1, hz - door[1] + door[0], "ender_chest[facing=" + in + "]");
        put(hx - door[0] - door[1], 1, hz - door[1] - door[0], "crafting_table");
        put(hx, 5, hz, "lantern[hanging=true]");
    }

    /** Ágyvédelem: gyapjú, fa és end kő rétegek, a híd felőli oldalon nyitva, hogy az ágy látszódjon. */
    private void defense(int bx, int bz, int[] o, String color) {
        String[] layers = {color + "_wool", "oak_planks", "end_stone"};
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int ex = Math.min(Math.abs(dx), Math.abs(dx - o[0]));
                int ez = Math.min(Math.abs(dz), Math.abs(dz - o[1]));
                int ring = Math.max(ex, ez);
                if (ring == 0 || ring > 3) {
                    continue;
                }
                int front = dx * -o[0] + dz * -o[1];
                if (front > 0) {
                    continue;
                }
                int height = 4 - ring;
                for (int y = 1; y <= height; y++) {
                    put(bx + dx, y, bz + dz, layers[Math.min(2, Math.max(0, ring - 1 + (y - 1) / 2))]);
                }
            }
        }
    }

    private void forge(int x, int z, String color) {
        generator(x, z, "iron_block", "gold_block");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    put(x + dx, 1, z + dz, "cobblestone_wall[up=true]");
                    put(x + dx, 2, z + dz, "cobblestone_wall[up=true]");
                    put(x + dx, 3, z + dz, "lantern[hanging=false]");
                } else if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    put(x + dx, 0, z + dz, "polished_andesite");
                }
            }
        }
        put(x, 4, z, "air");
    }

    private void shop(int x, int z, String color, String in) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(x + dx, 0, z + dz, "spruce_planks");
                put(x + dx, 4, z + dz, Math.floorMod(dx + dz, 2) == 0 ? color + "_wool" : "white_wool");
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    for (int y = 1; y <= 3; y++) {
                        put(x + dx, y, z + dz, "spruce_fence");
                    }
                }
            }
        }
        put(x, 1, z, "smithing_table");
        put(x + 1, 1, z, "crafting_table");
        put(x - 1, 1, z, "barrel[facing=up]");
        put(x, 2, z, "bell[attachment=floor,facing=" + in + "]");
        put(x, 3, z, "lantern[hanging=true]");
    }

    private void flag(int x, int z, String color) {
        for (int y = 1; y <= 8; y++) {
            put(x, y, z, "spruce_fence");
        }
        for (int fx = 1; fx <= 3; fx++) {
            for (int fy = 6; fy <= 8; fy++) {
                put(x + fx, fy, z, fy == 7 && fx == 2 ? "white_wool" : color + "_wool");
            }
        }
        put(x, 9, z, "lantern[hanging=false]");
    }

    /** A gyémántszigeten kis kvarc szentély a generátor fölött. */
    private void shrine(int x, int z) {
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = 1; y <= 4; y++) {
                put(x + c[0], y, z + c[1], "quartz_pillar[axis=y]");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                put(x + dx, 5, z + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 3 ? "quartz_slab[type=bottom]" : "smooth_quartz");
            }
        }
        put(x, 6, z, "diamond_block");
        put(x, 7, z, "light_blue_stained_glass");
        put(x, 4, z, "sea_lantern");
    }

    /** Smaragd generátor a középső szigeten: emelt talapzat üvegbúrával. */
    private void emeraldShrine(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                put(x + dx, 0, z + dz, edge ? "quartz_bricks" : "emerald_block");
                if (edge && (Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                    for (int y = 1; y <= 3; y++) {
                        put(x + dx, y, z + dz, "quartz_pillar[axis=y]");
                    }
                    put(x + dx, 4, z + dz, "end_rod[facing=up]");
                }
            }
        }
        put(x, 1, z, "heavy_weighted_pressure_plate");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(x + dx, 4, z + dz, "lime_stained_glass");
            }
        }
        put(x, 5, z, "emerald_block");
    }

    /** Óriás ágy szobor a középső szigeten. */
    private void bigBed(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean leg = Math.abs(dx) == 2 && Math.abs(dz) == 4;
                put(x + dx, 1, z + dz, leg ? "oak_log[axis=y]" : "oak_planks");
                put(x + dx, 2, z + dz, dz <= -2 ? "white_wool" : "red_wool");
                if (dz == -4) {
                    put(x + dx, 3, z + dz, "oak_planks");
                    put(x + dx, 4, z + dz, "oak_slab[type=bottom]");
                }
            }
        }
        put(x, 3, z - 3, "white_carpet");
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
