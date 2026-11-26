package hu.icemine.core.modelobby;

import hu.icemine.core.build.Build;

/**
 * A Gens lobby faluja: réteges oromzatú, piros tetős házak boltíves ablakokkal egy terrakotta tér körül,
 * egy kis tó stéggel, veteményes és nagy fák.
 */
final class GensVillage extends Build {

    interface Free {
        boolean test(int x, int z);
    }

    private final Free free;
    private final java.util.Set<Long> taken = new java.util.HashSet<>();

    GensVillage(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz);
        this.free = free;
    }

    @Override
    public void build() {
        house(-27, -2, 1, 10, 5);
        house(27, -2, 3, 10, 5);
        house(-20, -25, 0, 5, 5);
        house(20, -25, 0, 5, 5);
        dock(-21, 27);
        garden(17, 22);
        planters();
        stall(-18, 16, "lime_wool", new String[]{"melon", "pumpkin", "hay_block[axis=y]"});
        stall(18, 15, "yellow_wool", new String[]{"pumpkin", "carved_pumpkin[facing=north]", "melon"});
        int[][] trees = {{-34, 14}, {34, 14}, {-8, -36}, {8, -36}, {-33, -22}, {33, -22}, {4, 33}, {-39, -6}, {39, -6},
                {-12, 38}, {14, 37}, {31, 25}, {-34, 22}, {24, 33}};
        for (int[] t : trees) {
            tree(t[0], t[1], 9 + (int) Math.round(dist(t[0], t[1]) % 4));
        }
        scatter();
    }

    private void take(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                taken.add(((long) x << 32) ^ (z & 0xffffffffL));
            }
        }
    }

    private boolean open(int x, int z) {
        return free.test(x, z) && !taken.contains(((long) x << 32) ^ (z & 0xffffffffL));
    }

    /** Fű, virágok és bokrok a falun kívüli füves részeken. */
    private void scatter() {
        String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "red_tulip"};
        for (int x = -44; x <= 44; x++) {
            for (int z = -44; z <= 44; z++) {
                if (Math.abs(x) <= 23 && z >= -21 && z <= 21 || !open(x, z)) {
                    continue;
                }
                double n = noise(x, z, 410);
                double patch = smooth(x, z, 7.0, 411);
                if (n < 0.025) {
                    put(x, 1, z, "oak_leaves[persistent=true]");
                    if (noise(x, z, 412) < 0.4) {
                        put(x, 2, z, "azalea_leaves[persistent=true]");
                    }
                } else if (n < 0.025 + 0.08 * patch) {
                    put(x, 1, z, flowers[(int) (noise(x, z, 413) * flowers.length)]);
                } else if (n < 0.3 + 0.2 * patch) {
                    put(x, 1, z, noise(x, z, 414) < 0.85 ? "short_grass" : "fern");
                }
            }
        }
    }

    // ---- piaci standok ----

    private void stall(int sx, int sz, String cloth, String[] goods) {
        take(sx - 3, sz - 2, sx + 3, sz + 2);
        for (int[] c : new int[][]{{-2, -1}, {2, -1}, {-2, 1}, {2, 1}}) {
            for (int y = 1; y <= 3; y++) {
                put(sx + c[0], y, sz + c[1], "spruce_fence");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                String c = Math.floorMod(dx, 2) == 0 ? cloth : "white_wool";
                put(sx + dx, Math.abs(dz) == 2 ? 3 : 4, sz + dz, Math.abs(dz) == 2 ? c.replace("_wool", "_carpet") : c);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            put(sx + dx, 1, sz - 1, "spruce_planks");
            put(sx + dx, 2, sz - 1, goods[dx + 1]);
        }
        put(sx - 1, 1, sz + 1, "barrel[facing=up]");
        put(sx + 1, 1, sz + 1, "crafting_table");
        put(sx, 3, sz, "lantern[hanging=true]");
    }

    // ---- házak ----

    /** Egy ház; rot: 0 = dél felé néz, 1 = kelet, 2 = észak, 3 = nyugat. hw/hd: fél szélesség és mélység. */
    private void house(int cx, int cz, int rot, int hw, int hd) {
        House h = new House(cx, cz, rot);
        int r = Math.max(hw, hd) + 4;
        take(cx - r, cz - r, cx + r, cz + r);
        for (int x = -hw - 1; x <= hw + 1; x++) {
            for (int z = -hd - 1; z <= hd + 2; z++) {
                h.put(x, 0, z, "stone_bricks");
                for (int y = 1; y <= 30; y++) {
                    h.put(x, y, z, "air");
                }
            }
        }
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hd; z <= hd; z++) {
                boolean wall = Math.abs(x) == hw || Math.abs(z) == hd;
                boolean post = wall && (Math.abs(x) % 5 == hw % 5 && Math.abs(z) == hd || Math.abs(z) % 5 == hd % 5 && Math.abs(x) == hw
                        || Math.abs(x) == hw && Math.abs(z) == hd);
                h.put(x, 1, z, wall ? "polished_andesite" : "dark_oak_planks");
                for (int y = 2; y <= 12; y++) {
                    String block;
                    if (!wall) {
                        block = y == 7 ? "dark_oak_planks" : "air";
                    } else if (post) {
                        block = "dark_oak_log[axis=y]";
                    } else if (y == 7) {
                        block = Math.abs(x) == hw ? "dark_oak_log[axis=z]" : "dark_oak_log[axis=x]";
                    } else {
                        block = y == 2 ? "stone_bricks" : "smooth_quartz";
                    }
                    h.put(x, y, z, block);
                }
            }
        }
        // a két szint közötti kiugró gerenda
        for (int x = -hw - 1; x <= hw + 1; x++) {
            for (int z = -hd - 1; z <= hd + 1; z++) {
                if (Math.abs(x) != hw + 1 && Math.abs(z) != hd + 1) {
                    continue;
                }
                String out = Math.abs(z) == hd + 1 ? (z > 0 ? "south" : "north") : (x > 0 ? "east" : "west");
                h.put(x, 7, z, "spruce_stairs[facing=" + opposite(out) + ",half=top]");
                h.put(x, 8, z, "spruce_slab[type=bottom]");
            }
        }
        // ablakok: minden mezőben egy boltíves ablak a két szinten
        for (int side = 0; side < 4; side++) {
            int half = side % 2 == 0 ? hw : hd;
            for (int bay = -half + 1; bay + 3 <= half; bay += 5) {
                for (int level = 0; level < 2; level++) {
                    int y0 = level == 0 ? 3 : 9;
                    window(h, side, hw, hd, bay + 1, y0, level == 0 && side == 0 && bay + 1 <= 3 && bay + 2 >= -3);
                }
            }
        }
        // bejárat: kőboltív
        for (int y = 2; y <= 5; y++) {
            for (int x = -1; x <= 1; x++) {
                h.put(x, y, hd, "air");
                h.put(x, y, hd - 1, "air");
            }
            h.put(-2, y, hd + 1, "stone_bricks");
            h.put(2, y, hd + 1, "stone_bricks");
        }
        h.put(-2, 6, hd + 1, "chiseled_stone_bricks");
        h.put(2, 6, hd + 1, "chiseled_stone_bricks");
        h.put(-1, 5, hd + 1, "stone_brick_stairs[facing=west,half=top]");
        h.put(1, 5, hd + 1, "stone_brick_stairs[facing=east,half=top]");
        h.put(-1, 6, hd + 1, "stone_bricks");
        h.put(0, 6, hd + 1, "chiseled_stone_bricks");
        h.put(1, 6, hd + 1, "stone_bricks");
        for (int x = -2; x <= 2; x++) {
            h.put(x, 1, hd + 1, "stone_bricks");
            h.put(x, 0, hd + 2, "stone_bricks");
            h.put(x, 1, hd + 2, "stone_brick_stairs[facing=north,half=bottom]");
        }
        h.put(-3, 1, hd + 2, "barrel[facing=up]");
        h.put(3, 1, hd + 2, "barrel[facing=up]");
        h.put(3, 2, hd + 2, "lantern[hanging=false]");
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hd; z <= hd; z++) {
                h.put(x, 12, z, "dark_oak_planks");
            }
        }
        if (hw >= 8) {
            roof(h, hw - 4, hw - 6, hd, 0);
            roof(h, -4, 6, hd, 0);
        } else {
            roof(h, 0, hw, hd, 0);
        }
    }

    private void window(House h, int side, int hw, int hd, int a, int y0, boolean skip) {
        if (skip) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            int c = a + i;
            for (int y = y0; y <= y0 + 1; y++) {
                h.wall(side, hw, hd, c, y, 0, "black_stained_glass");
            }
            h.wall(side, hw, hd, c, y0 - 1, 1, "smooth_quartz_slab[type=top]");
        }
        h.wall(side, hw, hd, a, y0 + 2, 0, h.along(side, "polished_diorite_stairs", false));
        h.wall(side, hw, hd, a + 1, y0 + 2, 0, h.along(side, "polished_diorite_stairs", true));
        h.wall(side, hw, hd, a - 1, y0 + 1, 0, "polished_diorite");
        h.wall(side, hw, hd, a + 2, y0 + 1, 0, "polished_diorite");
    }

    /** Nyeregtető: a gerinc a ház elejétől a hátuljáig fut, elöl és hátul réteges oromzat. */
    private void roof(House h, int xc, int hw, int hd, int lift) {
        int base = 13 + lift;
        int e0 = hw + 2;
        for (int k = 0; k <= e0; k++) {
            int y = base + k;
            int e = e0 - k;
            for (int z = -hd - 2; z <= hd + 2; z++) {
                int az = Math.abs(z);
                boolean edge = az == hd + 2;
                for (int lx = -e; lx <= e; lx++) {
                    int x = xc + lx;
                    int t = e - Math.abs(lx);
                    String block = null;
                    if (t == 0) {
                        String mat = edge ? "smooth_sandstone" : "mangrove";
                        block = e == 0 ? (edge ? "smooth_sandstone" : "mangrove_planks")
                                : mat + "_stairs[facing=" + (lx < 0 ? "east" : "west") + ",half=bottom]";
                    } else if (t == 1) {
                        block = edge ? "cut_sandstone" : "mangrove_planks";
                    } else if (az == hd + 1) {
                        block = t == 2 ? "terracotta" : (t == 3 ? "white_terracotta" : null);
                    } else if (az == hd) {
                        block = t == 4 ? "birch_planks" : (t > 4 ? gable(lx, k) : "terracotta");
                    }
                    if (block != null) {
                        h.put(x, y, z, block);
                    }
                }
                if (edge && z > 0 && e >= 2 && k % 2 == 1) {
                    h.put(xc - e + 1, y, z + 1, "birch_button[face=wall,facing=south]");
                    h.put(xc + e - 1, y, z + 1, "birch_button[face=wall,facing=south]");
                }
            }
        }
    }

    private static String gable(int x, int k) {
        return k == 2 && Math.abs(x) <= 1 ? "black_stained_glass" : (k == 3 && x == 0 ? "black_stained_glass" : "dark_oak_planks");
    }

    /** Egy ház helyi koordinátái: x jobbra-balra, z előre (a bejárat felé), elforgatva a világba. */
    private final class House {
        final int cx;
        final int cz;
        final int rot;

        House(int cx, int cz, int rot) {
            this.cx = cx;
            this.cz = cz;
            this.rot = rot;
        }

        void put(int x, int y, int z, String data) {
            int wx = x;
            int wz = z;
            for (int i = 0; i < rot; i++) {
                int t = wx;
                wx = wz;
                wz = -t;
            }
            GensVillage.this.put(cx + wx, y, cz + wz, turn(data));
        }

        /** Fal mezője: side 0 = elöl, 1 = jobb, 2 = hátul, 3 = bal; c a falon belüli hely, out kifelé lépés. */
        void wall(int side, int hw, int hd, int c, int y, int out, String data) {
            switch (side) {
                case 0:
                    put(c, y, hd + out, data);
                    break;
                case 1:
                    put(hw + out, y, -c, data);
                    break;
                case 2:
                    put(-c, y, -hd - out, data);
                    break;
                default:
                    put(-hw - out, y, c, data);
            }
        }

        /** Boltív lépcső a fal síkjában: left = a boltív bal fele (kívülről nézve). */
        String along(int side, String mat, boolean right) {
            String[] leftFacing = {"west", "south", "east", "north"};
            String f = leftFacing[side];
            return mat + "[facing=" + (right ? opposite(f) : f) + ",half=top]";
        }

        private String turn(String data) {
            String out = data;
            for (int i = 0; i < rot; i++) {
                out = out.replace("facing=south", "facing=#E").replace("facing=east", "facing=#N")
                        .replace("facing=north", "facing=#W").replace("facing=west", "facing=#S")
                        .replace("#E", "east").replace("#N", "north").replace("#W", "west").replace("#S", "south")
                        .replace("axis=x", "axis=#Z").replace("axis=z", "axis=x").replace("#Z", "z");
            }
            return out;
        }
    }

    // ---- tó stéggel ----

    private void dock(int px, int pz) {
        take(px - 10, pz - 7, px + 10, pz + 10);
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -6; dz <= 9; dz++) {
                double d = dist(dx / 1.3, dz);
                int x = px + dx;
                int z = pz + dz;
                if (d > 7.5 || !free.test(x, z)) {
                    continue;
                }
                put(x, 0, z, "water");
                put(x, -1, z, d > 5.5 ? "water" : "water");
                put(x, -2, z, noise(x, z, 401) < 0.3 ? "clay" : "mud");
                put(x, 1, z, "air");
                if (noise(x, z, 402) < 0.06) {
                    put(x, 1, z, "lily_pad");
                }
            }
        }
        for (int i = -3; i <= 4; i++) {
            for (int w = 0; w <= 1; w++) {
                put(px + 4 + w, 1, pz + i, "spruce_slab[type=bottom]");
            }
            if (i % 3 == 0) {
                put(px + 3, 1, pz + i, "stripped_spruce_log[axis=y]");
                put(px + 3, 2, pz + i, "stripped_spruce_log[axis=y]");
                put(px + 6, 1, pz + i, "stripped_spruce_log[axis=y]");
                put(px + 6, 2, pz + i, "stripped_spruce_log[axis=y]");
                put(px + 3, 0, pz + i, "stripped_spruce_log[axis=y]");
                put(px + 6, 0, pz + i, "stripped_spruce_log[axis=y]");
            }
        }
        for (int i = -1; i <= 2; i++) {
            put(px + i, 1, pz + 4, "spruce_slab[type=bottom]");
            put(px + i, 1, pz + 5, "spruce_slab[type=bottom]");
        }
        put(px - 2, 1, pz + 4, "stripped_spruce_log[axis=y]");
        put(px - 2, 2, pz + 4, "stripped_spruce_log[axis=y]");
        put(px - 2, 0, pz + 4, "stripped_spruce_log[axis=y]");
        put(px + 5, 2, pz - 2, "barrel[facing=up]");
        put(px + 4, 2, pz - 1, "birch_planks");
        put(px + 4, 2, pz + 3, "lantern[hanging=false]");
        put(px, 2, pz + 5, "barrel[facing=up]");
        put(px + 1, 2, pz + 5, "birch_planks");
        put(px + 1, 3, pz + 5, "birch_slab[type=bottom]");
    }

    // ---- veteményes ----

    private void garden(int gx, int gz) {
        take(gx - 7, gz - 5, gx + 6, gz + 6);
        String[] crops = {"wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]"};
        for (int row = 0; row < 4; row++) {
            for (int i = -4; i <= 4; i++) {
                int x = gx + i;
                int z = gz + row * 2 - 3;
                if (!free.test(x, z)) {
                    continue;
                }
                put(x, 0, z, "farmland[moisture=7]");
                put(x, 1, z, crops[row]);
                if (free.test(x, z + 1) && row < 3) {
                    put(x, 0, z + 1, i == 0 ? "water" : "packed_mud");
                }
            }
        }
        for (int i = -5; i <= 5; i++) {
            for (int[] p : new int[][]{{gx + i, gz - 4}, {gx + i, gz + 5}, {gx - 5, gz + i}, {gx + 5, gz + i}}) {
                if (Math.abs(p[1] - gz) <= 5 && free.test(p[0], p[1])) {
                    put(p[0], 1, p[1], "oak_fence");
                }
            }
        }
        put(gx, 1, gz - 4, "air");
        put(gx - 6, 1, gz - 2, "hay_block[axis=y]");
        put(gx - 6, 2, gz - 2, "hay_block[axis=y]");
        put(gx - 6, 1, gz - 1, "composter[level=0]");
    }

    // ---- ültetők, oszlopok, hordók a téren ----

    private void planters() {
        int[][] boxes = {{-15, 9}, {15, 9}, {-15, -12}, {15, -12}, {-8, 18}, {8, 18}};
        for (int[] b : boxes) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    put(b[0] + dx, 1, b[1] + dz, rim ? "mud_bricks" : "rooted_dirt");
                    if (!rim) {
                        put(b[0], 2, b[1], "rose_bush[half=lower]");
                        put(b[0], 3, b[1], "rose_bush[half=upper]");
                    } else if (Math.abs(dx) + Math.abs(dz) == 1) {
                        put(b[0] + dx, 2, b[1] + dz, "azalea_leaves[persistent=true]");
                    }
                }
            }
        }
        int[][] pillars = {{-17, 5}, {17, 5}, {-17, -8}, {17, -8}};
        for (int[] p : pillars) {
            for (int y = 1; y <= 3; y++) {
                put(p[0], y, p[1], "terracotta");
            }
            put(p[0], 4, p[1], "mud_brick_slab[type=bottom]");
            put(p[0], 5, p[1], "lantern[hanging=false]");
        }
        int[][] barrels = {{-20, 12}, {20, 12}, {-11, -17}, {11, -17}};
        for (int[] b : barrels) {
            put(b[0], 1, b[1], "barrel[facing=up]");
            put(b[0] + 1, 1, b[1], "barrel[facing=up]");
            put(b[0], 2, b[1], "barrel[facing=up]");
            put(b[0], 1, b[1] + 1, "hay_block[axis=y]");
        }
    }

    // ---- nagy fák ----

    private void tree(int x, int z, int height) {
        if (!open(x, z)) {
            return;
        }
        take(x - 2, z - 2, x + 2, z + 2);
        for (int y = 1; y <= height; y++) {
            put(x, y, z, "oak_log[axis=y]");
        }
        for (int[] r : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            put(x + r[0], 1, z + r[1], "oak_log[axis=y]");
        }
        blob(x, height, z, 3.6);
        blob(x + 2, height - 3, z + 1, 2.6);
        blob(x - 2, height - 2, z - 1, 2.4);
        blob(x, height + 3, z, 2.2);
    }

    private void blob(int bx, int by, int bz, double r) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (d <= r - noise(bx + dx, bz + dz + dy * 5, 403) * 0.9) {
                        put(bx + dx, by + dy, bz + dz, noise(bx + dx, by + dy + bz + dz, 404) < 0.2
                                ? "azalea_leaves[persistent=true]" : "oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }
}
