package hu.icemine.core.modelobby;

import hu.icemine.core.build.Build;

/**
 * A Gens lobby: nagy farm város. Középen a tér szökőkúttal és kőívekkel, körülötte piros pajták,
 * búza- és répaföld, traktor, szénás szekér, piaci stand, víztorony, a szélén terrakotta dombok
 * lelógó levelű óriásfákkal, egy domb alatt bánya bejárat, délnyugaton tó homokos parttal és stéggel,
 * az égen lebegő hamburgerek és tüskés gömbök.
 */
public final class GensFarmTown extends ThemeBase {

    static final double PLAZA = 22;
    static final int[] LAKE = {-26, 27};
    static final int[] HILL = {33, 29};

    private int[] lakeAt = LAKE;
    private int[] hillAt = HILL;
    private final boolean hub;
    private int farm;

    GensFarmTown(Sink sink, int cx, int cy, int cz, Free free) {
        this(sink, cx, cy, cz, free, false);
    }

    private GensFarmTown(Sink sink, int cx, int cy, int cz, Free free, boolean hub) {
        super(sink, cx, cy, cz, free);
        this.hub = hub;
    }

    /**
     * A Gens központ városa a nagy közös farm körül (a farm négyzete |x|, |z| <= farm, ide nem épít semmit).
     * free: szabad-e a hely (a szigeten belül van-e).
     */
    public static Build hubTown(Sink sink, int x, int y, int z, java.util.function.BiPredicate<Integer, Integer> free, int farm) {
        GensFarmTown town = new GensFarmTown(sink, x, y, z, (fx, fz) -> free.test(fx, fz), true);
        for (int fx = -farm; fx <= farm; fx++) {
            for (int fz = -farm; fz <= farm; fz++) {
                if (dist(fx, fz) <= farm) {
                    town.take(fx, fz, fx, fz);
                }
            }
        }
        town.farm = farm;
        town.span = 230;
        town.patchGrass = 0.06;
        return town;
    }

    /** A tér talaja: barna, sáros minta deszka körökkel és sugarakkal. */
    static String plaza(int x, int z) {
        double d = dist(x, z);
        double a = Math.toDegrees(Math.atan2(z, x));
        double spoke = Math.abs(((a % 45) + 45) % 45 - 22.5);
        if (Math.abs(d - 11) < 0.6 || Math.abs(d - PLAZA + 1) < 0.6 || (spoke > 21.5 && d > 6 && d < PLAZA - 1)) {
            return "stripped_spruce_wood[axis=y]";
        }
        double n = noise(x, z, 600);
        double s = smooth(x, z, 5.0, 601);
        if (s > 0.62) {
            return n < 0.5 ? "coarse_dirt" : "rooted_dirt";
        }
        if (s < 0.32) {
            return n < 0.5 ? "terracotta" : "brown_terracotta";
        }
        return n < 0.45 ? "packed_mud" : (n < 0.75 ? "mud_bricks" : "dirt_path");
    }

    @Override
    public void build() {
        if (hub) {
            buildHub();
            return;
        }
        take(-6, -6, 6, 6);
        fountain();
        barn(-31, -12, 1, 6, 8, "deepslate_tile", "deepslate_tiles");
        barn(31, -12, 3, 6, 8, "smooth_sandstone", "yellow_terracotta");
        barn(-21, -38, 0, 5, 6, "deepslate_tile", "deepslate_tiles");
        barn(21, -38, 0, 5, 6, "dark_oak", "dark_oak_planks");
        hill();
        lake();
        wheat(-36, 9, 7, 6);
        rows(36, 9);
        market(4, 34);
        tractor(-10, 33);
        wagon(-34, -26);
        waterTower(42, -28);
        ruins(new int[][]{{-24, 14}, {-28, 4}, {-17, 25}, {26, 20}, {24, -26}, {-26, -24}});
        lamps();
        props();
        ridges(40, 56);
        int[][] trees = {{-46, -14}, {46, -14}, {-44, 12}, {-8, -48}, {8, -47}, {-2, 46}, {16, 46}, {-38, -34}, {38, -36}, {46, 8}, {-48, 0}};
        for (int i = 0; i < trees.length; i++) {
            bigTree(trees[i][0], trees[i][1], 11 + (int) (noise(i, 1, 602) * 5), i);
        }
        int[][] burgers = {{-34, 34, -26}, {30, 38, -30}, {-44, 31, 18}, {40, 35, 16}, {-6, 44, 40}, {18, 30, 34}};
        for (int[] b : burgers) {
            burger(b[0], b[1], b[2]);
        }
        int[][] balls = {{-20, 33, -40}, {14, 42, -44}, {-42, 38, -4}, {44, 30, -2}, {-24, 36, 40}, {30, 44, 34}, {0, 50, -36}};
        for (int[] b : balls) {
            spiky(b[0], b[1], b[2]);
        }
        scatter((x, z) -> dist(x, z) <= PLAZA + 0.5, new String[]{"poppy", "dandelion", "cornflower", "oxeye_daisy"}, 0.35);
    }

    /** A központ városa: a farm körüli gyűrűben pajták, domb bányával, tó, mező, kellékek, fák, dombok, lebegő burgerek. */
    private void buildHub() {
        // a régi, kisebb város elrendezése, felnagyítva és kétszer körbe forgatva, hogy az egész nagy farmot körbeérje
        double scale = 2.65;
        for (int turn = 0; turn < 3; turn++) {
            double a = turn * Math.PI / 4 - (turn == 2 ? Math.PI / 8 * 5 : 0);
            java.util.function.IntBinaryOperator rx = (x, z) -> (int) Math.round((x * Math.cos(a) - z * Math.sin(a)) * scale);
            java.util.function.IntBinaryOperator rz = (x, z) -> (int) Math.round((x * Math.sin(a) + z * Math.cos(a)) * scale);
            int[][] barns = turn == 0 ? new int[][]{{-62, -12, 1}, {62, 12, 3}, {-22, -64, 0}, {22, -64, 0}, {16, 64, 2}}
                    : turn == 1 ? new int[][]{{-62, 10, 1}, {62, -10, 3}, {-18, 64, 2}}
                    : new int[][]{{-64, 0, 1}, {64, 0, 3}, {0, -64, 0}, {0, 64, 2}};
            String[][] roofs = {{"deepslate_tile", "deepslate_tiles"}, {"smooth_sandstone", "yellow_terracotta"}, {"dark_oak", "dark_oak_planks"}};
            for (int i = 0; i < barns.length; i++) {
                int[] b = barns[i];
                int rot = Math.floorMod(b[2] + (turn == 1 ? 0 : 0), 4);
                String[] roof = roofs[(i + turn) % roofs.length];
                barn(rx.applyAsInt(b[0], b[1]), rz.applyAsInt(b[0], b[1]), rot, 6, 8, roof[0], roof[1]);
            }
            int[][] ruinsAt = {{-50, 22}, {-52, -30}, {50, -26}, {48, 26}, {26, -50}, {-30, 50}, {0, -55}, {0, 55}};
            int[][] ruinsWorld = new int[ruinsAt.length][];
            for (int i = 0; i < ruinsAt.length; i++) {
                ruinsWorld[i] = new int[]{rx.applyAsInt(ruinsAt[i][0], ruinsAt[i][1]), rz.applyAsInt(ruinsAt[i][0], ruinsAt[i][1])};
            }
            ruins(ruinsWorld);
            int[][] propsAt = {{-58, 8}, {58, -8}, {8, 58}, {-8, -58}, {-48, -48}, {48, 48}};
            int[][] propsWorld = new int[propsAt.length][];
            for (int i = 0; i < propsAt.length; i++) {
                propsWorld[i] = new int[]{rx.applyAsInt(propsAt[i][0], propsAt[i][1]), rz.applyAsInt(propsAt[i][0], propsAt[i][1])};
            }
            props(propsWorld);
        }
        hillAt = new int[]{161, -154};
        hill();
        hillAt = new int[]{-172, -138};
        hill();
        lakeAt = new int[]{-154, 154};
        lake();
        lakeAt = new int[]{172, 129};
        lake();
        wheat(-166, -100, 9, 7);
        rows(160, 104);
        market(-36, 166);
        tractor(-70, 166);
        wagon(96, -150);
        waterTower(180, 64);
        waterTower(-70, -172);
        waterTower(70, 172);
        ridges(188, 230);
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36 + 0.17;
            int r = 162 + (int) (noise(i, 9, 640) * 34);
            bigTree((int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), 11 + (int) (noise(i, 1, 602) * 5), i);
        }
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18 + 0.4;
            int r = 70 + (int) (noise(i, 4, 641) * 130);
            burger((int) Math.round(Math.cos(a) * r), 42 + (int) (noise(i, 5, 642) * 24), (int) Math.round(Math.sin(a) * r));
        }
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18 + 0.05;
            int r = 60 + (int) (noise(i, 6, 643) * 140);
            spiky((int) Math.round(Math.cos(a) * r), 46 + (int) (noise(i, 7, 644) * 26), (int) Math.round(Math.sin(a) * r));
        }
        scatter((x, z) -> dist(x, z) <= farm, new String[]{"poppy", "dandelion", "cornflower", "oxeye_daisy"}, 0.04);
    }

    // ---- elforgatható helyi koordináták (0 = dél felé néz, 1 = kelet, 2 = észak, 3 = nyugat) ----

    private final class Rot {
        final int ox;
        final int oz;
        final int rot;

        Rot(int ox, int oz, int rot) {
            this.ox = ox;
            this.oz = oz;
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
            GensFarmTown.this.put(ox + wx, y, oz + wz, turn(data));
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

    // ---- szökőkút kőívekkel ----

    private void fountain() {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = dist(dx, dz);
                if (d > 5.3) {
                    continue;
                }
                put(dx, -1, dz, "stone_bricks");
                if (d > 4.4) {
                    put(dx, 0, dz, "stone_bricks");
                    put(dx, 1, dz, noise(dx, dz, 603) < 0.3 ? "mossy_stone_brick_slab[type=bottom]" : "stone_brick_slab[type=bottom]");
                } else {
                    put(dx, 0, dz, "water");
                    put(dx, -1, dz, d < 2 ? "sea_lantern" : "mossy_stone_bricks");
                }
            }
        }
        for (int y = 0; y <= 2; y++) {
            put(0, y, 0, y == 2 ? "chiseled_stone_bricks" : "stone_bricks");
        }
        put(0, 3, 0, "lantern[hanging=false]");
        int[][] pillars = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] p : pillars) {
            for (int y = 0; y <= 6; y++) {
                put(p[0], y, p[1], y == 0 ? "stone_bricks" : (noise(p[0], y + p[1], 604) < 0.35 ? "mossy_stone_bricks" : "stone_bricks"));
            }
            put(p[0], 7, p[1], "stone_brick_wall[up=true]");
            put(p[0], 8, p[1], "lantern[hanging=false]");
        }
        for (int i = -3; i <= 3; i++) {
            for (int[] side : new int[][]{{i, -4}, {i, 4}, {-4, i}, {4, i}}) {
                boolean horizontal = Math.abs(side[1]) == 4;
                int along = horizontal ? side[0] : side[1];
                String block;
                if (Math.abs(along) == 3) {
                    String facing = horizontal ? (along < 0 ? "west" : "east") : (along < 0 ? "north" : "south");
                    block = "stone_brick_stairs[facing=" + facing + ",half=top]";
                    put(side[0], 5, side[1], block);
                }
                put(side[0], 6, side[1], noise(side[0], side[1], 605) < 0.3 ? "mossy_stone_bricks" : "stone_bricks");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3 && noise(dx, dz, 606) < 0.35) {
                    put(dx, 6, dz, "moss_carpet");
                }
            }
        }
    }

    // ---- piros pajta, tört (gambrel) tetővel ----

    private static final int[] PROFILE = {0, 2, 4, 5, 6, 7, 7, 8, 8};

    private void barn(int bx, int bz, int rot, int w, int d, String roofMat, String roofFull) {
        int reach = Math.max(w, d) + 3;
        for (int[] probe : new int[][]{{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
            if (!open(bx + probe[0], bz + probe[1]) || !free.test(bx + probe[0], bz + probe[1])) {
                return;
            }
        }
        take(bx - reach, bz - reach, bx + reach, bz + reach);
        Rot r = new Rot(bx, bz, rot);
        for (int x = -w - 1; x <= w + 1; x++) {
            for (int z = -d - 1; z <= d + 2; z++) {
                r.put(x, 0, z, Math.abs(x) <= w && Math.abs(z) <= d ? "spruce_planks" : "stone_bricks");
                for (int y = 1; y <= 22; y++) {
                    r.put(x, y, z, "air");
                }
            }
        }
        int wall = 6;
        for (int x = -w; x <= w; x++) {
            for (int z = -d; z <= d; z++) {
                boolean edge = Math.abs(x) == w || Math.abs(z) == d;
                if (!edge) {
                    continue;
                }
                boolean corner = Math.abs(x) == w && Math.abs(z) == d;
                for (int y = 1; y <= wall; y++) {
                    String block;
                    if (y == 1) {
                        block = "stone_bricks";
                    } else if (corner || y == wall) {
                        block = "calcite";
                    } else {
                        int along = Math.abs(x) == w ? z : x;
                        block = Math.floorMod(along, 2) == 0 ? "red_concrete" : "red_terracotta";
                        if (Math.abs(x) == w && Math.floorMod(z, 4) == 2 && (y == 3 || y == 4)) {
                            block = "light_gray_stained_glass";
                        }
                    }
                    r.put(x, y, z, block);
                }
            }
        }
        int base = wall + 1;
        for (int x = -w - 1; x <= w + 1; x++) {
            int e = w + 1 - Math.abs(x);
            int h = PROFILE[Math.min(PROFILE.length - 1, e)];
            int prev = e == 0 ? -1 : PROFILE[Math.min(PROFILE.length - 1, e - 1)];
            int next = PROFILE[Math.min(PROFILE.length - 1, e + 1)];
            for (int z = -d - 1; z <= d + 1; z++) {
                boolean end = Math.abs(z) == d + 1;
                String full = end ? "calcite" : roofFull;
                String stair = (end ? "quartz" : roofMat) + "_stairs[facing=" + (x < 0 ? "east" : "west") + ",half=bottom]";
                for (int y = base + prev + 1; y < base + h; y++) {
                    r.put(x, y, z, full);
                }
                boolean ridge = x == 0;
                r.put(x, base + h, z, ridge || next - h >= 2 || next == h ? full : stair);
                if (Math.abs(z) == d && Math.abs(x) <= w) {
                    for (int y = base; y < base + h; y++) {
                        r.put(x, y, z, Math.floorMod(x, 2) == 0 ? "red_concrete" : "red_terracotta");
                    }
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                r.put(x, y, d, Math.abs(x) == 2 || y == 4 ? "calcite" : (x == 0 ? "stripped_dark_oak_log[axis=y]" : "dark_oak_planks"));
            }
        }
        r.put(-1, 2, d, "white_concrete");
        r.put(1, 3, d, "white_concrete");
        r.put(1, 2, d, "white_concrete");
        r.put(-1, 3, d, "white_concrete");
        for (int x = -1; x <= 1; x++) {
            for (int y = base + 2; y <= base + 3; y++) {
                r.put(x, y, d, x == 0 && y == base + 2 ? "hay_block[axis=y]" : "calcite");
            }
        }
        r.put(0, base + 3, d, "dark_oak_planks");
        r.put(0, base + 5, d + 1, "lantern[hanging=false]");
        for (int x = -3; x <= 3; x++) {
            r.put(x, 0, d + 1, "spruce_planks");
            r.put(x, 0, d + 2, "coarse_dirt");
        }
        r.put(-3, 1, d + 1, "hay_block[axis=y]");
        r.put(-4, 1, d + 1, "hay_block[axis=y]");
        r.put(-4, 2, d + 1, "hay_block[axis=y]");
        r.put(3, 1, d + 1, "barrel[facing=up]");
        r.put(4, 1, d + 1, "barrel[facing=up]");
        r.put(4, 2, d + 1, "lantern[hanging=false]");
    }

    // ---- domb bánya bejárattal, rajta egy kis pajta ----

    private void hill() {
        int hx = hillAt[0];
        int hz = hillAt[1];
        take(hx - 13, hz - 13, hx + 13, hz + 13);
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                double d = dist(dx, dz);
                if (d > 12.5 || !free.test(hx + dx, hz + dz) && d > 9) {
                    continue;
                }
                int h = (int) Math.round(10 * Math.cos(Math.min(1, d / 12.5) * Math.PI / 2) + smooth(hx + dx, hz + dz, 4.0, 607) * 2);
                for (int y = 1; y <= h; y++) {
                    put(hx + dx, y, hz + dz, y == h ? "grass_block" : (y % 3 == 0 ? "brown_terracotta" : (y % 3 == 1 ? "terracotta" : "packed_mud")));
                }
            }
        }
        double ax = -1 / Math.sqrt(2);
        double az = -1 / Math.sqrt(2);
        for (int s = 0; s <= 13; s++) {
            for (int w = -5; w <= 5; w++) {
                for (int y = 1; y <= 6; y++) {
                    double rr = Math.sqrt(w * w + (y - 1) * (y - 1) * 1.1);
                    int x = hx + (int) Math.round(ax * (13 - s) - az * w);
                    int z = hz + (int) Math.round(az * (13 - s) + ax * w);
                    if (rr < 4.2) {
                        put(x, y, z, s > 9 ? "black_concrete" : "air");
                        if (y == 1) {
                            put(x, 0, z, noise(x, z, 608) < 0.5 ? "gravel" : "stone");
                        }
                    } else if (rr < 5.4) {
                        double n = noise(x, y + z, 609);
                        put(x, y, z, n < 0.4 ? "stone" : (n < 0.7 ? "andesite" : (n < 0.85 ? "cobblestone" : "mossy_cobblestone")));
                    }
                }
            }
        }
        for (int dx = -9; dx <= -3; dx++) {
            for (int dz = -9; dz <= -3; dz++) {
                int x = hx + dx;
                int z = hz + dz;
                double n = noise(x, z, 610);
                if (dist(dx, dz) > 6 && dist(dx, dz) < 10 && open(x, z) || dist(dx, dz) <= 10 && n < 0.15) {
                    put(x, 0, z, n < 0.5 ? "stone" : (n < 0.8 ? "andesite" : "gravel"));
                }
            }
        }
        put(hx - 7, 1, hz - 3, "lantern[hanging=false]");
        put(hx - 3, 1, hz - 7, "lantern[hanging=false]");
        put(hx - 7, 1, hz - 4, "chest[facing=south]");
        int top = 10;
        Rot r = new Rot(hx + 2, hz + 2, 2);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                r.put(x, top, z, "spruce_planks");
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                for (int y = top + 1; y <= top + 4; y++) {
                    r.put(x, y, z, edge ? (Math.abs(x) == 3 && Math.abs(z) == 3 ? "calcite" : "red_concrete") : "air");
                }
            }
        }
        for (int k = 0; k <= 4; k++) {
            for (int z = -4; z <= 4; z++) {
                for (int s = -1; s <= 1; s += 2) {
                    int x = s * (4 - k);
                    r.put(x, top + 5 + k, z, k == 4 ? "deepslate_tiles" : "deepslate_tile_stairs[facing=" + (s < 0 ? "east" : "west") + ",half=bottom]");
                }
                if (Math.abs(z) == 3) {
                    for (int x = -(3 - k); x <= 3 - k; x++) {
                        r.put(x, top + 5 + k, z, "red_concrete");
                    }
                }
            }
        }
        r.put(0, top + 1, 3, "air");
        r.put(0, top + 2, 3, "air");
        r.put(0, top + 6, 3, "calcite");
    }

    // ---- tó homokos parttal és stéggel ----

    private void lake() {
        int lx = lakeAt[0];
        int lz = lakeAt[1];
        take(lx - 13, lz - 13, lx + 13, lz + 13);
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                double d = dist(dx * 0.9, dz);
                int x = lx + dx;
                int z = lz + dz;
                if (d > 12.5 || !free.test(x, z) || hub && Math.abs(x) <= 44 && Math.abs(z) <= 44) {
                    continue;
                }
                if (d <= 9) {
                    int depth = d < 5 ? 3 : (d < 7.5 ? 2 : 1);
                    for (int y = -depth; y <= 0; y++) {
                        put(x, y, z, "water");
                    }
                    put(x, -depth - 1, z, noise(x, z, 611) < 0.3 ? "clay" : "sand");
                    if (d > 7.5 && noise(x, z, 612) < 0.1) {
                        put(x, 1, z, "lily_pad");
                    }
                } else {
                    put(x, 0, z, noise(x, z, 613) < 0.2 ? "sandstone" : "sand");
                    put(x, -1, z, "sandstone");
                    if (noise(x, z, 614) < 0.06) {
                        put(x, 1, z, "sugar_cane");
                    }
                }
            }
        }
        for (int s = 0; s <= 10; s++) {
            for (int w = -1; w <= 1; w++) {
                int x = lx + 3 + w;
                int z = lz - 4 + s;
                put(x, 1, z, "spruce_slab[type=bottom]");
                if (s % 3 == 0 && w != 0) {
                    for (int y = -2; y <= 2; y++) {
                        put(x, y, z, "stripped_spruce_log[axis=y]");
                    }
                }
            }
        }
        for (int s = 0; s <= 5; s++) {
            for (int w = -1; w <= 1; w++) {
                put(lx + 2 - s, 1, lz + 4 + w, "spruce_slab[type=bottom]");
            }
        }
        put(lx + 4, 2, lz + 1, "barrel[facing=up]");
        put(lx + 2, 2, lz + 3, "birch_planks");
        put(lx + 2, 3, lz + 3, "birch_slab[type=bottom]");
        put(lx - 3, 2, lz + 3, "lantern[hanging=false]");
        put(lx + 4, 3, lz + 6, "lantern[hanging=false]");
        put(lx + 4, 2, lz + 6, "spruce_fence");
    }

    // ---- földek ----

    private void wheat(int fx, int fz, int hw, int hd) {
        take(fx - hw - 1, fz - hd - 1, fx + hw + 1, fz + hd + 1);
        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                int x = fx + dx;
                int z = fz + dz;
                if (!free.test(x, z)) {
                    continue;
                }
                boolean water = dz == 0 && Math.floorMod(dx, 4) == 0;
                put(x, 0, z, water ? "water" : "farmland[moisture=7]");
                put(x, 1, z, water ? "air" : (noise(x, z, 615) < 0.15 ? "wheat[age=5]" : "wheat[age=7]"));
            }
        }
        for (int dx = -hw - 1; dx <= hw + 1; dx++) {
            for (int dz = -hd - 1; dz <= hd + 1; dz++) {
                if ((Math.abs(dx) == hw + 1 || Math.abs(dz) == hd + 1) && free.test(fx + dx, fz + dz)) {
                    put(fx + dx, 0, fz + dz, "stripped_spruce_wood[axis=y]");
                }
            }
        }
        scarecrow(fx + 2, fz - 2);
    }

    private void rows(int fx, int fz) {
        take(fx - 7, fz - 7, fx + 7, fz + 7);
        String[] crops = {"carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]", "carrots[age=7]", "wheat[age=7]"};
        for (int row = 0; row < 5; row++) {
            for (int dx = -6; dx <= 6; dx++) {
                int x = fx + dx;
                int z = fz - 6 + row * 3;
                if (!free.test(x, z) || !free.test(x, z + 1)) {
                    continue;
                }
                put(x, 0, z, "farmland[moisture=7]");
                put(x, 1, z, crops[row]);
                put(x, 0, z + 1, "farmland[moisture=7]");
                put(x, 1, z + 1, crops[row]);
                if (row < 4) {
                    put(x, 0, z + 2, dx == 0 ? "water" : "spruce_planks");
                }
            }
        }
        for (int dz = -7; dz <= 7; dz++) {
            for (int side = -1; side <= 1; side += 2) {
                if (free.test(fx + side * 7, fz + dz)) {
                    put(fx + side * 7, 1, fz + dz, "spruce_fence");
                }
            }
        }
        put(fx - 7, 1, fz, "spruce_fence_gate[facing=east]");
        scarecrow(fx + 3, fz + 1);
    }

    private void scarecrow(int x, int z) {
        put(x, 1, z, "spruce_fence");
        put(x, 2, z, "hay_block[axis=y]");
        put(x, 3, z, "carved_pumpkin[facing=south]");
        put(x - 1, 2, z, "spruce_fence");
        put(x + 1, 2, z, "spruce_fence");
    }

    // ---- piaci stand, traktor, szekér, víztorony ----

    private void market(int mx, int mz) {
        take(mx - 4, mz - 3, mx + 4, mz + 3);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(mx + dx, 0, mz + dz, "spruce_planks");
                boolean edge = Math.abs(dz) == 2;
                String cloth = Math.floorMod(dx, 2) == 0 ? "red_wool" : "white_wool";
                put(mx + dx, edge ? 4 : 5, mz + dz, edge ? cloth.replace("_wool", "_carpet") : cloth);
                put(mx + dx, 4, mz + dz, edge ? cloth.replace("_wool", "_carpet") : cloth);
            }
        }
        for (int[] c : new int[][]{{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) {
            for (int y = 1; y <= 3; y++) {
                put(mx + c[0], y, mz + c[1], "spruce_fence");
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            put(mx + dx, 1, mz - 1, "spruce_planks");
            put(mx + dx, 2, mz - 1, dx % 2 == 0 ? "pumpkin" : "melon");
        }
        put(mx - 2, 1, mz + 1, "barrel[facing=up]");
        put(mx + 2, 1, mz + 1, "hay_block[axis=y]");
        put(mx, 3, mz, "lantern[hanging=true]");
        int[][] pile = {{mx - 5, mz - 1}, {mx - 5, mz}, {mx - 6, mz}, {mx + 5, mz + 1}};
        for (int[] p : pile) {
            put(p[0], 1, p[1], "pumpkin");
        }
        put(mx - 5, 2, mz, "jack_o_lantern[facing=north]");
    }

    private void tractor(int tx, int tz) {
        take(tx - 4, tz - 3, tx + 4, tz + 3);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(tx + dx, 2, tz + dz, dx >= 1 ? "red_concrete" : "red_terracotta");
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            put(tx + 2, 3, tz + dz, "red_concrete");
            put(tx - 1, 3, tz + dz, dz == 0 ? "dark_oak_stairs[facing=east,half=bottom]" : "red_terracotta");
        }
        put(tx - 2, 3, tz, "red_terracotta");
        put(tx - 1, 5, tz, "red_concrete");
        for (int y = 3; y <= 4; y++) {
            put(tx - 2, y, tz - 1, "iron_bars");
            put(tx - 2, y, tz + 1, "iron_bars");
        }
        put(tx - 2, 5, tz - 1, "red_concrete");
        put(tx - 2, 5, tz + 1, "red_concrete");
        put(tx - 2, 5, tz, "red_concrete");
        put(tx + 3, 3, tz, "iron_bars");
        put(tx + 2, 4, tz, "polished_blackstone_wall[up=true]");
        for (int side = -1; side <= 1; side += 2) {
            put(tx - 2, 1, tz + side * 2, "black_concrete");
            put(tx - 2, 2, tz + side * 2, "black_concrete");
            put(tx - 1, 1, tz + side * 2, "black_concrete");
            put(tx - 1, 2, tz + side * 2, "black_concrete");
            put(tx + 2, 1, tz + side * 2, "polished_blackstone_button[face=wall,facing=" + (side < 0 ? "north" : "south") + "]");
            put(tx + 2, 1, tz + side, "black_concrete");
        }
        put(tx + 4, 1, tz, "hay_block[axis=x]");
    }

    private void wagon(int wx, int wz) {
        take(wx - 4, wz - 3, wx + 4, wz + 3);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, 2, wz + dz, "spruce_slab[type=top]");
                if (Math.abs(dz) == 1) {
                    put(wx + dx, 3, wz + dz, "spruce_trapdoor[facing=" + (dz < 0 ? "north" : "south") + ",half=bottom,open=true]");
                }
                if (Math.abs(dx) <= 2 && dz == 0) {
                    put(wx + dx, 3, wz + dz, "hay_block[axis=x]");
                }
            }
        }
        put(wx, 4, wz, "hay_block[axis=x]");
        put(wx - 1, 4, wz, "hay_block[axis=x]");
        for (int side = -1; side <= 1; side += 2) {
            put(wx - 2, 1, wz + side * 2, "dark_oak_trapdoor[facing=" + (side < 0 ? "north" : "south") + ",half=bottom,open=true]");
            put(wx + 2, 1, wz + side * 2, "dark_oak_trapdoor[facing=" + (side < 0 ? "north" : "south") + ",half=bottom,open=true]");
        }
        put(wx + 4, 2, wz, "spruce_fence");
        put(wx + 5, 2, wz, "spruce_fence");
    }

    private void waterTower(int x, int z) {
        if (!open(x, z)) {
            return;
        }
        take(x - 4, z - 4, x + 4, z + 4);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = 1; y <= 9; y++) {
                put(x + c[0], y, z + c[1], "stripped_spruce_log[axis=y]");
            }
        }
        for (int y = 10; y <= 15; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = dist(dx, dz);
                    if (d <= 3.2 && (d > 2.2 || y == 10)) {
                        put(x + dx, y, z + dz, y % 2 == 0 ? "spruce_planks" : "stripped_spruce_log[axis=y]");
                    } else if (d <= 2.2) {
                        put(x + dx, y, z + dz, "water");
                    }
                }
            }
        }
        cone(x, 16, z, 3.6, 4, "dark_oak", "dark_oak_planks", "lightning_rod[facing=up]");
        for (int y = 1; y <= 9; y++) {
            put(x - 3, y, z, "ladder[facing=west]");
        }
    }

    // ---- romos kőoszlopok, lámpák, apró díszek ----

    private void ruins(int[][] pillars) {
        for (int i = 0; i < pillars.length; i++) {
            int x = pillars[i][0];
            int z = pillars[i][1];
            if (!open(x, z)) {
                continue;
            }
            take(x - 1, z - 1, x + 1, z + 1);
            int h = 4 + (int) (noise(i, 2, 616) * 4);
            for (int y = 1; y <= h; y++) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        if (y == h && noise(x + dx, z + dz, 617) < 0.4) {
                            continue;
                        }
                        put(x + dx, y, z + dz, noise(x + dx, y + z + dz, 618) < 0.4 ? "mossy_stone_bricks" : "stone_bricks");
                    }
                }
            }
            put(x, h + 1, z, "moss_carpet");
            put(x + 2, 1, z, "mossy_cobblestone_slab[type=bottom]");
        }
    }

    private void lamps() {
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(a) * (PLAZA - 0.5));
            int z = (int) Math.round(Math.sin(a) * (PLAZA - 0.5));
            if (!free.test(x, z) || !open(x, z)) {
                continue;
            }
            for (int y = 1; y <= 4; y++) {
                put(x, y, z, "stripped_spruce_log[axis=y]");
            }
            put(x, 5, z, "spruce_slab[type=bottom]");
            put(x + 1, 4, z, "spruce_fence");
            put(x - 1, 4, z, "spruce_fence");
            put(x + 1, 3, z, "lantern[hanging=true]");
            put(x - 1, 3, z, "lantern[hanging=true]");
        }
    }

    private void props() {
        props(new int[][]{{-12, 17}, {12, 17}, {-17, -10}, {17, -10}, {-8, -19}, {8, -19}});
    }

    private void props(int[][] crates) {
        for (int i = 0; i < crates.length; i++) {
            int x = crates[i][0];
            int z = crates[i][1];
            if (!free.test(x, z) || !open(x, z)) {
                continue;
            }
            put(x, 1, z, i % 2 == 0 ? "barrel[facing=up]" : "hay_block[axis=y]");
            put(x + 1, 1, z, "spruce_planks");
            put(x, 2, z, i % 3 == 0 ? "pumpkin" : "composter[level=8]");
            put(x, 1, z + 1, "pumpkin");
        }
    }

    // ---- terrakotta dombok a szélén ----

    private void ridges(int inner, int outer) {
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double d = dist(x, z);
                if (d < inner || !open(x, z) || !free.test(x, z)) {
                    continue;
                }
                double s = smooth(x, z, 9.0, 619);
                if (s < 0.38) {
                    continue;
                }
                double rise = Math.min(1, (d - inner) / 4.0);
                int h = (int) Math.round(rise * (2 + (s - 0.38) * 14));
                if (h <= 0) {
                    continue;
                }
                for (int y = 1; y <= h; y++) {
                    put(x, y, z, y == h ? "grass_block" : (y % 3 == 0 ? "brown_terracotta" : (y % 3 == 1 ? "terracotta" : "packed_mud")));
                }
                if (noise(x, z, 620) < 0.2) {
                    put(x, h + 1, z, "short_grass");
                }
                take(x, z, x, z);
            }
        }
    }

    // ---- óriásfa lelógó levelekkel ----

    private void bigTree(int x, int z, int height, int seed) {
        if (!free.test(x, z)) {
            return;
        }
        double lean = noise(seed, 3, 621) * Math.PI * 2;
        double lx = Math.cos(lean);
        double lz = Math.sin(lean);
        int topX = x;
        int topZ = z;
        int base = groundHeight(x, z);
        for (int y = 1; y <= height; y++) {
            double t = y / (double) height;
            int ox = (int) Math.round(lx * 3 * t * t);
            int oz = (int) Math.round(lz * 3 * t * t);
            topX = x + ox;
            topZ = z + oz;
            put(topX, base + y, topZ, "dark_oak_log[axis=y]");
            if (y < 3) {
                put(topX + 1, base + y, topZ, "dark_oak_log[axis=y]");
                put(topX, base + y, topZ + 1, "dark_oak_log[axis=y]");
                put(topX + 1, base + y, topZ + 1, "dark_oak_log[axis=y]");
            }
        }
        int cy = base + height;
        blob(topX, cy, topZ, 3.0, "azalea_leaves[persistent=true]");
        for (int f = 0; f < 11; f++) {
            double a = Math.PI * 2 * f / 11 + noise(seed, f, 622) * 0.5;
            frond(topX, cy, topZ, Math.cos(a), Math.sin(a), 6 + (int) (noise(seed, f, 623) * 3));
        }
    }

    private void frond(int x, int y, int z, double dx, double dz, int length) {
        for (int s = 1; s <= length; s++) {
            int px = x + (int) Math.round(dx * s);
            int pz = z + (int) Math.round(dz * s);
            int py = y + (int) Math.round(1.2 * s - 0.22 * s * s);
            put(px, py, pz, noise(px, pz, 624) < 0.3 ? "azalea_leaves[persistent=true]" : "jungle_leaves[persistent=true]");
            put(px, py + 1, pz, "oak_leaves[persistent=true]");
            if (s > 1 && s < length) {
                int sx = (int) Math.round(-dz);
                int sz = (int) Math.round(dx);
                put(px + sx, py, pz + sz, "jungle_leaves[persistent=true]");
                put(px - sx, py, pz - sz, "jungle_leaves[persistent=true]");
                if (s > length / 2) {
                    put(px + sx, py - 1, pz + sz, "oak_leaves[persistent=true]");
                    put(px - sx, py - 2, pz - sz, "jungle_leaves[persistent=true]");
                }
            }
            if (s == length) {
                for (int k = 1; k <= 3; k++) {
                    put(px, py - k, pz, "jungle_leaves[persistent=true]");
                }
            } else if (s > length / 2 && noise(px, pz, 625) < 0.5) {
                put(px, py - 1, pz, "oak_leaves[persistent=true]");
            }
        }
    }

    private final java.util.Map<Long, Integer> heights = new java.util.HashMap<>();

    @Override
    protected void put(int dx, int dy, int dz, String data) {
        if (dy > 0 && !data.equals("air") && (data.endsWith("grass_block") || data.contains("terracotta") || data.equals("packed_mud"))) {
            heights.merge(((long) dx << 32) ^ (dz & 0xffffffffL), dy, Math::max);
        }
        super.put(dx, dy, dz, data);
    }

    private int groundHeight(int x, int z) {
        return heights.getOrDefault(((long) x << 32) ^ (z & 0xffffffffL), 0);
    }

    // ---- lebegő hamburger és tüskés gömb ----

    private void burger(int bx, int by, int bz) {
        double r = 4.5;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dist(dx, dz);
                int x = bx + dx;
                int z = bz + dz;
                if (d <= r - 0.5) {
                    put(x, by, z, "orange_terracotta");
                }
                if (d <= r - 0.2) {
                    put(x, by + 1, z, noise(x, z, 626) < 0.5 ? "brown_concrete" : "brown_terracotta");
                }
                if (d <= r + 0.6 + noise(x, z, 627) * 0.9) {
                    put(x, by + 2, z, noise(x, z, 628) < 0.5 ? "lime_concrete" : "green_concrete");
                }
                if (d <= r && Math.abs(dx) == Math.abs(dz) && d > r - 1.5) {
                    put(x, by + 2, z, "yellow_concrete");
                }
                if (d <= r - 0.6) {
                    put(x, by + 3, z, "red_concrete");
                }
                for (int k = 0; k <= 2; k++) {
                    double rr = (r + 0.2) * Math.sqrt(1 - (k / 3.2) * (k / 3.2));
                    if (d <= rr) {
                        boolean seed = d > rr - 1.2 && noise(x, z + k, 629) < 0.12;
                        put(x, by + 4 + k, z, seed ? "white_concrete" : (noise(x, z + k, 630) < 0.3 ? "terracotta" : "orange_terracotta"));
                    }
                }
            }
        }
    }

    private void spiky(int sx, int sy, int sz) {
        double r = 2.6;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= r) {
                        put(sx + dx, sy + dy, sz + dz, noise(sx + dx, sy + dy + sz + dz, 631) < 0.5 ? "orange_concrete" : "yellow_concrete");
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 1}, {-1, 1, 1}, {1, -1, 1}, {1, 1, -1},
                {-1, -1, 1}, {-1, 1, -1}, {1, -1, -1}, {-1, -1, -1}, {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {0, 1, 1}, {0, -1, -1},
                {1, 0, 1}, {-1, 0, -1}};
        for (int[] d : dirs) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (int s = 3; s <= 6; s++) {
                int x = sx + (int) Math.round(d[0] / len * s);
                int y = sy + (int) Math.round(d[1] / len * s);
                int z = sz + (int) Math.round(d[2] / len * s);
                put(x, y, z, s >= 5 ? "yellow_terracotta" : "yellow_wool");
            }
        }
    }
}
