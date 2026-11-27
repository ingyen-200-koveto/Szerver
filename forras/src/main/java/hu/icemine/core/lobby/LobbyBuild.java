package hu.icemine.core.lobby;

import hu.icemine.core.build.Build;
import java.util.ArrayList;
import java.util.List;

public final class LobbyBuild extends Build {

    public static final int VERSION = 15;
    public static final int COURSE_LENGTH = 56;
    public static final int CLEAR = 136;
    public static final int CLEAR_NORTH = 100;
    public static final int CLEAR_SOUTH = 112;

    static final int PLATFORM = 5;
    static final double ARC = 20.5;
    /** A tér közepén lévő hópehely-minta közepe (z) és a szobrok köre. */
    static final int FOUNTAIN_Z = -5;
    static final int STATUE_R = 16;
    /** A sziget fél szélessége (x irányban). */
    static final double WIDTH = 120.0;
    /** Nyugati szárny: tó. Keleti szárny: kilátó és PvP aréna. */
    static final int LAKE_X = -96;
    static final int LAKE_Z = -30;
    static final int TOWER_X = 102;
    static final int TOWER_Z = -40;
    static final int ARENA_X = 92;
    static final int ARENA_Z = -14;
    public static final double ARENA_R = 9.5;
    static final int TERRACE = 24;
    static final int BACK = 8;

    private static final int MIN_X = -130;
    private static final int MAX_X = 130;
    private static final int MIN_Z = -100;
    private static final int MAX_Z = 16;

    private static final String[] I = {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"};
    private static final String[] C = {".####", "#....", "#....", "#....", "#....", "#....", ".####"};
    private static final String[] E = {"#####", "#....", "#....", "####.", "#....", "#....", "#####"};
    private static final String[] M = {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"};
    private static final String[] N = {"#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"};

    static final String[][] SLOTS = {
            {"creative", "-20", "magenta"},
            {"fyland", "20", "lime"},
            {"combofly", "-41", "purple"},
            {"kingdoms", "41", "yellow"},
            {"gens", "-62", "lime"},
            {"duels", "62", "yellow"},
            {"bedwars", "-83", "orange"},
            {"skywars", "83", "light_blue"}
    };

    private final int[][] height = new int[MAX_X - MIN_X + 1][MAX_Z - MIN_Z + 1];
    private final boolean[][] used = new boolean[MAX_X - MIN_X + 1][MAX_Z - MIN_Z + 1];
    private final List<int[]> course = new ArrayList<>();

    public LobbyBuild(Sink sink, int cx, int cy, int cz) {
        super(sink, cx, cy, cz);
    }

    public List<int[]> course() {
        return course;
    }

    @Override
    public void build() {
        terrain();
        platform();
        plazaLamps();
        stairs();
        roads();
        fountain();
        houses();
        castle();
        letters();
        launchPads();
        wings();
        districts();
        trees();
        meadow();
        barrier();
        parkour();
        anchors();
    }

    private static double smoothstep(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double shape(double x, double z) {
        double dz = z + 40;
        double p = dz > 0 ? 3.2 : 2.0;
        double rz = dz > 0 ? 49.5 : 56;
        return Math.pow(Math.pow(Math.abs(x) / WIDTH, p) + Math.pow(Math.abs(dz) / rz, p), 1.0 / p);
    }

    private static boolean onIsland(int x, int z) {
        double angle = Math.atan2(z + 40, x);
        double wobble = 0.018 * Math.sin(angle * 7 + 1) + 0.012 * Math.sin(angle * 17);
        return shape(x, z) <= 1.0 + wobble;
    }

    private static double castleMask(int x, int z) {
        double fx = 1 - smoothstep(30, 38, Math.abs(x));
        double fz = smoothstep(-97, -89, z) * (1 - smoothstep(-48, -42, z));
        return fx * fz;
    }

    /** A szárnyak sík részei (tó, kilátó, aréna) és az oda vezető ösvények: itt nincs hegy. */
    static double wingMask(int x, int z) {
        double lake = 1 - smoothstep(17, 25, dist(x - LAKE_X, z - LAKE_Z));
        double east = 1 - smoothstep(19, 27, dist(x - 96, z + 27));
        double road = Math.abs(x) > 40 ? 1 - smoothstep(4, 9, Math.abs(z + 38)) : 0;
        return Math.max(lake, Math.max(east, road));
    }

    private static int ground(int x, int z) {
        double bluff = (PLATFORM - 1) * smoothstep(-26, -20, z) * (1 - smoothstep(27, 41, Math.abs(x)));
        double r = shape(x, z);
        double ring = smoothstep(0.6, 0.93, r);
        double north = 1 - smoothstep(-26, -8, z);
        double rough = 0.55 + 0.45 * smooth(x, z, 13.0, 5) + 0.25 * smooth(x, z, 5.0, 6);
        double mountain = ring * north * 30 * rough * (1 - castleMask(x, z)) * (1 - wingMask(x, z));
        return Math.max(0, (int) Math.round(Math.max(bluff, mountain)));
    }

    private void use(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z) {
                    used[x - MIN_X][z - MIN_Z] = true;
                }
            }
        }
    }

    private boolean free(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int px = x + dx;
                int pz = z + dz;
                if (px < MIN_X || px > MAX_X || pz < MIN_Z || pz > MAX_Z || used[px - MIN_X][pz - MIN_Z]) {
                    return false;
                }
            }
        }
        return true;
    }

    private int h(int x, int z) {
        if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z) {
            return -100;
        }
        return height[x - MIN_X][z - MIN_Z];
    }

    private void terrain() {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                height[x - MIN_X][z - MIN_Z] = onIsland(x, z) ? ground(x, z) : -100;
            }
        }
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                int top = h(x, z);
                if (top < -50) {
                    continue;
                }
                int steep = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int n = h(x + d[0], z + d[1]);
                    if (n > -50) {
                        steep = Math.max(steep, Math.abs(n - top));
                    }
                }
                double r = shape(x, z);
                int depth = 4 + (int) ((1 - Math.pow(Math.min(1, r), 1.6)) * 17 + noise(x, z, 3) * 4);
                for (int y = -depth; y <= top; y++) {
                    String block;
                    if (y == top) {
                        if (top >= 22 && noise(x, z, 4) < 0.85) {
                            block = "snow_block";
                        } else if (steep >= 3) {
                            double n = noise(x, z + y, 5);
                            block = n < 0.45 ? "stone" : (n < 0.75 ? "andesite" : (n < 0.9 ? "cobblestone" : "mossy_cobblestone"));
                        } else if (top >= 18 && noise(x, z, 6) < 0.5) {
                            block = "snow_block";
                        } else {
                            block = noise(x, z, 7) < 0.06 ? "coarse_dirt" : "grass_block";
                        }
                    } else if (y > top - 3) {
                        block = steep >= 3 ? "stone" : "dirt";
                    } else if (y < -depth + 3) {
                        block = noise(x, z + y, 8) < 0.5 ? "packed_ice" : "stone";
                    } else {
                        double n = noise(x + y, z - y, 9);
                        block = n < 0.6 ? "stone" : (n < 0.75 ? "andesite" : (n < 0.88 ? "diorite" : "coal_ore"));
                    }
                    put(x, y, z, block);
                }
                if (noise(x, z, 10) < 0.045 && depth > 8) {
                    int length = 2 + (int) (noise(x, z, 11) * 8);
                    for (int l = 1; l <= length; l++) {
                        put(x, -depth - l, z, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
                if (top >= 20 && noise(x, z, 12) < 0.35) {
                    put(x, top + 1, z, "snow[layers=" + (1 + (int) (noise(x, z, 13) * 3)) + "]");
                } else if (top <= 14 && steep < 2) {
                    double n = noise(x, z, 14);
                    if (n < 0.12) {
                        put(x, top + 1, z, "short_grass");
                    } else if (n < 0.126) {
                        put(x, top + 1, z, n < 0.123 ? "dandelion" : "poppy");
                    }
                }
            }
        }
    }

    static boolean onPlatform(int x, int z) {
        if (z >= 1 && z <= BACK && Math.abs(x) <= TERRACE) {
            return true;
        }
        return z <= 0 && dist(x, z) <= ARC;
    }

    /** Virágágyások a tér közepe és a szobrok között, az utak (küllők) kihagyásával. */
    static final double[] BED_ANGLES = {-72.5, -51.5, -30.5, 30.5, 51.5, 72.5};

    static boolean bed(int x, int z) {
        for (double a : BED_ANGLES) {
            double bx = Math.sin(Math.toRadians(a)) * STATUE_R;
            double bz = -Math.cos(Math.toRadians(a)) * STATUE_R;
            if (dist(x - bx, z - bz) <= 2.2) {
                return true;
            }
        }
        return false;
    }

    /** Az utak a tér közepéből a szobrokhoz. */
    static boolean spoke(int x, int z) {
        for (String[] slot : SLOTS) {
            double a = Math.toRadians(Double.parseDouble(slot[1]));
            double ux = Math.sin(a);
            double uz = -Math.cos(a);
            double t = x * ux + z * uz;
            if (t > 0 && t < STATUE_R && Math.abs(x * uz - z * ux) <= 1.1) {
                return true;
            }
        }
        return false;
    }

    static String plazaFloor(int x, int z) {
        double d = dist(x, z);
        double f = dist(x, z - FOUNTAIN_Z);
        if (dist(x, z - 3) <= 1.2) {
            return "sea_lantern";
        }
        if (dist(x, z - 3) <= 2.2) {
            return "light_blue_concrete";
        }
        if (f <= 6.4) {
            int zz = z - FOUNTAIN_Z;
            if (f <= 1.0) {
                return "sea_lantern";
            }
            boolean ray = x == 0 || zz == 0 || Math.abs(x) == Math.abs(zz);
            if (ray && f <= 5.2) {
                return f > 3.9 && f <= 4.6 ? "blue_ice" : "light_blue_concrete";
            }
            return f > 5.6 ? "polished_diorite" : "smooth_quartz";
        }
        if (f > 6.4 && f <= 8.0) {
            return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(x, z - FOUNTAIN_Z)) / 15), 2) == 0 ? "polished_diorite" : "smooth_quartz";
        }
        if (Math.abs(d - STATUE_R) <= 0.8 && z <= 2) {
            return "smooth_stone";
        }
        if (spoke(x, z) && f > 6.4) {
            return Math.floorMod(x + z, 4) == 0 ? "chiseled_stone_bricks" : "stone_bricks";
        }
        if (Math.abs(x) <= 1 && z > 3) {
            return Math.floorMod(z, 3) == 0 ? "chiseled_stone_bricks" : "stone_bricks";
        }
        if (Math.abs(x) <= 2 && z < -(int) ARC + 4) {
            return Math.floorMod(z, 3) == 0 ? "chiseled_stone_bricks" : "stone_bricks";
        }
        return noise(x, z, 23) < 0.15 ? "andesite" : "polished_andesite";
    }

    /** Négy lámpa a tér közepén lévő minta sarkainál. */
    private void plazaLamps() {
        int fz = FOUNTAIN_Z;
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 7}, {8, 7}}) {
            put(c[0], PLATFORM + 1, fz + c[1], "chiseled_stone_bricks");
            put(c[0], PLATFORM + 2, fz + c[1], "spruce_fence");
            put(c[0], PLATFORM + 3, fz + c[1], "lantern[hanging=false]");
        }
    }

    private void platform() {
        int r = TERRACE + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -(int) ARC - 2; z <= BACK + 1; z++) {
                if (!onPlatform(x, z)) {
                    continue;
                }
                use(x, z, x, z);
                int base = Math.max(-2, h(x, z));
                for (int y = base; y < PLATFORM; y++) {
                    double n = noise(x, z + y, 20);
                    put(x, y, z, y >= PLATFORM - 1 ? "stone_bricks" : (n < 0.2 ? "mossy_stone_bricks" : (n < 0.35 ? "cracked_stone_bricks" : "stone_bricks")));
                }
                String floor = plazaFloor(x, z);
                if (bed(x, z)) {
                    boolean edge = !bed(x + 1, z) || !bed(x - 1, z) || !bed(x, z + 1) || !bed(x, z - 1);
                    floor = edge ? "polished_andesite" : "grass_block";
                    if (!edge) {
                        double n = noise(x, z, 21);
                        if (n < 0.18) {
                            put(x, PLATFORM + 1, z, "short_grass");
                        } else if (n < 0.3) {
                            String[] flowers = {"cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley"};
                            put(x, PLATFORM + 1, z, flowers[(int) (noise(x, z, 22) * flowers.length)]);
                        }
                    }
                }
                put(x, PLATFORM, z, floor);
                if (!bed(x, z)) {
                    put(x, PLATFORM + 1, z, "air");
                }
                for (int y = PLATFORM + 2; y <= PLATFORM + 7; y++) {
                    put(x, y, z, "air");
                }
            }
        }
        for (double a : BED_ANGLES) {
            int bx = (int) Math.round(Math.sin(Math.toRadians(a)) * STATUE_R);
            int bz = (int) Math.round(-Math.cos(Math.toRadians(a)) * STATUE_R);
            put(bx, PLATFORM + 1, bz, "flowering_azalea_leaves[persistent=true]");
            put(bx, PLATFORM + 2, bz, "azalea_leaves[persistent=true]");
        }
        for (int x = -r; x <= r; x++) {
            for (int z = -(int) ARC - 2; z <= BACK + 1; z++) {
                if (!onPlatform(x, z)) {
                    continue;
                }
                boolean edge = !onPlatform(x + 1, z) || !onPlatform(x - 1, z) || !onPlatform(x, z + 1) || !onPlatform(x, z - 1);
                if (!edge || (z < -10 && Math.abs(x) <= 2) || (z == BACK && Math.abs(x) <= 1)) {
                    continue;
                }
                boolean post = Math.floorMod(x * 7 + z * 3, 6) == 0;
                if (post) {
                    put(x, PLATFORM + 1, z, "chiseled_sandstone");
                    put(x, PLATFORM + 2, z, "lantern[hanging=false]");
                } else {
                    put(x, PLATFORM + 1, z, "smooth_sandstone");
                }
            }
        }
        for (String[] slot : SLOTS) {
            double angle = Math.toRadians(Double.parseDouble(slot[1]));
            int px = (int) Math.round(Math.sin(angle) * STATUE_R);
            int pz = (int) Math.round(-Math.cos(angle) * STATUE_R);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(px + dx, PLATFORM, pz + dz, dx == 0 && dz == 0 ? slot[2] + "_concrete" : "polished_andesite");
                    put(px + dx, PLATFORM + 1, pz + dz, "air");
                }
            }
            double yaw = Math.toDegrees(Math.atan2(px, -pz));
            String id = slot[0].startsWith("soon") ? slot[0] : "mode_" + slot[0];
            layout.point("npc:" + id, px + 0.5, PLATFORM + 1, pz + 0.5, yaw);
        }
        int[] spots = {7, 12, 17, 22};
        for (int side = -1; side <= 1; side += 2) {
            for (int s : spots) {
                put(side * s, PLATFORM + 1, BACK - 2, "chiseled_stone_bricks");
                put(side * s, PLATFORM + 2, BACK - 2, s <= 12 ? "stone_brick_slab[type=bottom]" : "stone_brick_wall[up=true]");
                if (s > 12) {
                    put(side * s, PLATFORM + 3, BACK - 2, "lantern[hanging=false]");
                }
            }
            for (int y = PLATFORM + 1; y <= PLATFORM + 5; y++) {
                put(side * 2, y, BACK, y == PLATFORM + 5 ? "chiseled_stone_bricks" : "stone_bricks");
            }
            put(side * 2, PLATFORM + 6, BACK, "lantern[hanging=false]");
        }
        for (int x = -1; x <= 1; x++) {
            put(x, PLATFORM + 5, BACK, "stone_brick_slab[type=top]");
            put(x, PLATFORM + 6, BACK, "stone_brick_slab[type=bottom]");
        }
        put(0, PLATFORM + 4, BACK, "lantern[hanging=true]");
        put(0, PLATFORM + 1, BACK - 2, "light_weighted_pressure_plate");
        layout.point("parkour:start", 0, PLATFORM + 1, BACK - 2, 0);
        layout.point("parkour:fail", 0, PLATFORM - 3, 0, 0);
    }

    private void bush(int x, int y, int z) {
        put(x, y, z, "flowering_azalea_leaves[persistent=true]");
        put(x + 1, y, z, "azalea_leaves[persistent=true]");
        put(x, y, z + 1, "azalea_leaves[persistent=true]");
        put(x, y + 1, z, "flowering_azalea_leaves[persistent=true]");
    }

    private void stairs() {
        for (int k = 0; k < PLATFORM; k++) {
            int z = -(int) ARC - 1 - k;
            int y = PLATFORM - 1 - k;
            boolean lamp = k == 1;
            for (int x = -3; x <= 3; x++) {
                use(x, z, x, z);
                int base = Math.max(-1, h(x, z));
                for (int fill = base; fill < y; fill++) {
                    put(x, fill, z, "stone_bricks");
                }
                if (Math.abs(x) == 3) {
                    put(x, y, z, "stone_bricks");
                    put(x, y + 1, z, lamp ? "stone_bricks" : "stone_brick_wall[up=true]");
                    if (lamp) {
                        put(x, y + 2, z, "lantern[hanging=false]");
                    }
                } else {
                    put(x, y, z, stairs("stone_brick", "south", false));
                    for (int up = 1; up <= 4; up++) {
                        put(x, y + up, z, "air");
                    }
                }
            }
        }
    }

    private void street(int x, int z) {
        double n = noise(x, z, 30);
        put(x, 0, z, n < 0.45 ? "stone_bricks" : (n < 0.7 ? "andesite" : (n < 0.88 ? "cobblestone" : "mossy_stone_bricks")));
        put(x, 1, z, "air");
        use(x, z, x, z);
    }

    private void path(int x, int z) {
        put(x, 0, z, noise(x, z, 31) < 0.7 ? "dirt_path" : "coarse_dirt");
        put(x, 1, z, "air");
        use(x, z, x, z);
    }

    private void roads() {
        for (int z = -51; z <= -28; z++) {
            for (int x = -3; x <= 3; x++) {
                street(x, z);
            }
        }
        for (int x = -46; x <= 46; x++) {
            for (int z = -40; z <= -36; z++) {
                street(x, z);
            }
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = -47; z <= -29; z++) {
                if (dist(x, z + 38) <= 9.2) {
                    street(x, z);
                }
            }
        }
        int[][] lamps = {{-14, -34}, {14, -34}, {-26, -34}, {26, -34}, {-38, -34}, {38, -34},
                {-14, -42}, {14, -42}, {-26, -42}, {26, -42}, {-38, -42}, {38, -42}, {-5, -50}, {5, -50}};
        for (int[] lamp : lamps) {
            put(lamp[0], 1, lamp[1], "stone_bricks");
            put(lamp[0], 2, lamp[1], "spruce_fence");
            put(lamp[0], 3, lamp[1], "spruce_fence");
            put(lamp[0], 4, lamp[1], "lantern[hanging=false]");
            use(lamp[0], lamp[1], lamp[0], lamp[1]);
        }
    }

    private void fountain() {
        int fz = -38;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double d = dist(x, z);
                if (d > 4.6) {
                    continue;
                }
                put(x, -1, fz + z, "stone_bricks");
                if (d > 3.7) {
                    put(x, 0, fz + z, "stone_bricks");
                    put(x, 1, fz + z, "stone_brick_slab[type=bottom]");
                } else {
                    put(x, 0, fz + z, "water");
                    put(x, 1, fz + z, "air");
                }
            }
        }
        for (int y = 0; y <= 3; y++) {
            put(0, y, fz, y == 3 ? "chiseled_stone_bricks" : "stone_bricks");
        }
        String[] faces = {"west", "east", "north", "south"};
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < around.length; i++) {
            put(around[i][0], 1, fz + around[i][1], "stone_brick_stairs[facing=" + faces[i] + ",half=bottom]");
        }
        put(0, 4, fz, "blue_ice");
        put(0, 5, fz, "packed_ice");
        put(0, 6, fz, "light_blue_stained_glass");
        put(0, 7, fz, "end_rod[facing=up]");
        for (int[] d : around) {
            put(d[0], 4, fz + d[1], "water");
        }
        for (int[] p : new int[][]{{-6, -32}, {6, -32}, {-6, -44}, {6, -44}}) {
            put(p[0], 1, p[1], "stone_bricks");
            put(p[0], 2, p[1], "spruce_fence");
            put(p[0], 3, p[1], "lantern[hanging=false]");
        }
    }

    static final class House {
        final int x;
        final int z;
        final int sx;
        final int sz;
        final int floors;
        final String door;
        final String roof;
        final String wall;

        House(int x, int z, int sx, int sz, int floors, String door, String roof, String wall) {
            this.x = x;
            this.z = z;
            this.sx = sx;
            this.sz = sz;
            this.floors = floors;
            this.door = door;
            this.roof = roof;
            this.wall = wall;
        }
    }

    static final House[] HOUSES = {
                new House(-11, -28, 9, 7, 2, "east", "acacia", "white_terracotta"),
                new House(11, -28, 9, 7, 2, "west", "acacia", "smooth_sandstone"),
                new House(-24, -28, 7, 9, 1, "north", "spruce", "smooth_sandstone"),
                new House(24, -28, 7, 9, 1, "north", "acacia", "white_terracotta"),
                new House(-36, -28, 9, 7, 2, "north", "acacia", "white_terracotta"),
                new House(36, -28, 9, 7, 2, "north", "prismarine_brick", "smooth_sandstone"),
                new House(-13, -47, 11, 7, 2, "south", "prismarine_brick", "white_terracotta"),
                new House(13, -47, 11, 7, 2, "south", "acacia", "white_terracotta"),
                new House(-25, -47, 7, 7, 1, "south", "acacia", "smooth_sandstone"),
                new House(25, -47, 7, 7, 1, "south", "spruce", "white_terracotta"),
                new House(-37, -47, 9, 7, 2, "south", "acacia", "smooth_sandstone"),
                new House(37, -47, 9, 7, 2, "south", "acacia", "white_terracotta")
        };

    private void houses() {
        House[] list = HOUSES;
        for (House house : list) {
            house(house);
        }
    }

    private void house(House house) {
        int x0 = house.x - house.sx / 2;
        int x1 = x0 + house.sx - 1;
        int z0 = house.z - house.sz / 2;
        int z1 = z0 + house.sz - 1;
        int top = house.floors * 4;
        use(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                put(x, 0, z, wall ? "cobblestone" : "spruce_planks");
                for (int y = 1; y <= top; y++) {
                    if (!wall) {
                        put(x, y, z, y == 4 && house.floors > 1 ? "spruce_planks" : "air");
                        continue;
                    }
                    boolean alongX = z == z0 || z == z1;
                    int along = alongX ? x - x0 : z - z0;
                    int length = alongX ? house.sx : house.sz;
                    boolean post = corner || (house.floors > 1 && y > 4 && along % 4 == 0);
                    boolean beam = y == 4 || y == top;
                    boolean window = !corner && along % 2 == 0 && along > 0 && along < length - 1 && (y == 2 || y == 3 || y == 6 || y == 7);
                    String block;
                    if (post) {
                        block = "stripped_spruce_log[axis=y]";
                    } else if (beam) {
                        block = "stripped_spruce_log[axis=" + (alongX ? "x" : "z") + "]";
                    } else if (window) {
                        block = alongX ? "glass_pane[east=true,west=true]" : "glass_pane[north=true,south=true]";
                    } else if (y <= 3 && house.floors > 1) {
                        block = noise(x, y + z, 40) < 0.3 ? "mossy_cobblestone" : "stone_bricks";
                    } else if (y == 1) {
                        block = "stone_bricks";
                    } else {
                        block = house.wall;
                    }
                    put(x, y, z, block);
                }
            }
        }
        int dx = 0;
        int dz = 0;
        int doorX;
        int doorZ;
        switch (house.door) {
            case "north":
                doorX = house.x;
                doorZ = z0;
                dz = -1;
                break;
            case "south":
                doorX = house.x;
                doorZ = z1;
                dz = 1;
                break;
            case "east":
                doorX = x1;
                doorZ = house.z;
                dx = 1;
                break;
            default:
                doorX = x0;
                doorZ = house.z;
                dx = -1;
                break;
        }
        put(doorX, 1, doorZ, "spruce_door[facing=" + house.door + ",half=lower,hinge=left,open=false]");
        put(doorX, 2, doorZ, "spruce_door[facing=" + house.door + ",half=upper,hinge=left,open=false]");
        put(doorX, 3, doorZ, "stripped_spruce_log[axis=y]");
        for (int step = 1; step <= 6; step++) {
            int px = doorX + dx * step;
            int pz = doorZ + dz * step;
            if ((Math.abs(px) <= 3 && pz >= -51 && pz <= -28) || (pz >= -40 && pz <= -36)
                    || (step >= 2 && px >= MIN_X && px <= MAX_X && pz >= MIN_Z && pz <= MAX_Z && used[px - MIN_X][pz - MIN_Z])) {
                break;
            }
            path(px, pz);
            if (step == 1 && house.floors > 1) {
                put(px + (dz != 0 ? 1 : 0), 1, pz + (dx != 0 ? 1 : 0), "barrel[facing=up]");
            }
        }
        put(x0 + 1, top - 1, z0 + 1, "lantern[hanging=true]");
        boolean ridgeX = house.sx >= house.sz;
        int span = ridgeX ? house.sz : house.sx;
        String low = ridgeX ? "south" : "east";
        String high = ridgeX ? "north" : "west";
        for (int i = 0; i <= span / 2 + 1; i++) {
            int y = top + 1 + i;
            int a = (ridgeX ? z0 : x0) - 1 + i;
            int b = (ridgeX ? z1 : x1) + 1 - i;
            if (a > b) {
                break;
            }
            int from = (ridgeX ? x0 : z0) - 1;
            int to = (ridgeX ? x1 : z1) + 1;
            for (int t = from; t <= to; t++) {
                if (a == b) {
                    put(ridgeX ? t : a, y, ridgeX ? a : t, house.roof + "_slab[type=bottom]");
                    continue;
                }
                put(ridgeX ? t : a, y, ridgeX ? a : t, stairs(house.roof, low, false));
                put(ridgeX ? t : b, y, ridgeX ? b : t, stairs(house.roof, high, false));
                boolean gable = t == (ridgeX ? x0 : z0) || t == (ridgeX ? x1 : z1);
                for (int c = a + 1; c < b; c++) {
                    if (gable) {
                        boolean window = c == (a + b) / 2 && b - a > 3;
                        put(ridgeX ? t : c, y, ridgeX ? c : t, window ? "glass_pane[" + (ridgeX ? "north=true,south=true" : "east=true,west=true") + "]" : house.wall);
                    } else if (t > from && t < to) {
                        put(ridgeX ? t : c, y, ridgeX ? c : t, "air");
                    }
                }
            }
        }
        int chimneyX = x1 - 1;
        int chimneyZ = z1 - 1;
        int peak = top + 1 + span / 2 + 1;
        for (int y = top + 1; y <= peak + 1; y++) {
            put(chimneyX, y, chimneyZ, "bricks");
        }
        put(chimneyX, peak + 2, chimneyZ, "campfire[lit=true,signal_fire=false,facing=north]");
    }

    private void castle() {
        for (int x = -26; x <= 26; x++) {
            for (int z = -86; z <= -50; z++) {
                boolean inner = Math.abs(x) <= 22 && z >= -82 && z <= -54;
                boolean moat = Math.abs(x) <= 25 && z >= -85 && z <= -51;
                if (inner || !moat) {
                    continue;
                }
                use(x, z, x, z);
                if (Math.abs(x) <= 2 && z >= -54) {
                    put(x, -1, z, "water");
                    put(x, 0, z, Math.abs(x) == 2 ? "spruce_log[axis=z]" : "spruce_planks");
                    put(x, 1, z, Math.abs(x) == 2 ? "spruce_fence" : "air");
                    continue;
                }
                put(x, -2, z, "gravel");
                put(x, -1, z, "water");
                put(x, 0, z, "water");
                put(x, 1, z, "air");
            }
        }
        use(-22, -82, 22, -54);
        for (int x = -22; x <= 22; x++) {
            for (int z = -82; z <= -54; z++) {
                put(x, 0, z, "grass_block");
                put(x, 1, z, "air");
            }
        }
        for (int z = -66; z <= -54; z++) {
            for (int x = -2; x <= 2; x++) {
                put(x, 0, z, noise(x, z, 50) < 0.6 ? "stone_bricks" : "cobblestone");
            }
        }
        int wallTop = 9;
        for (int x = -21; x <= 21; x++) {
            for (int z = -81; z <= -55; z++) {
                boolean ring = Math.abs(x) >= 20 || z <= -80 || z >= -56;
                if (!ring) {
                    continue;
                }
                boolean gate = Math.abs(x) <= 2 && z >= -56;
                for (int y = 1; y <= wallTop; y++) {
                    if (gate && y <= 5 && !(y == 5 && Math.abs(x) == 2)) {
                        put(x, y, z, "air");
                        continue;
                    }
                    double n = noise(x, y * 31 + z, 51);
                    String block;
                    if (y <= 2) {
                        block = n < 0.35 ? "mossy_cobblestone" : "cobblestone";
                    } else if (y == 3) {
                        block = "polished_andesite";
                    } else {
                        block = n < 0.18 ? "mossy_stone_bricks" : (n < 0.3 ? "cracked_stone_bricks" : "stone_bricks");
                    }
                    put(x, y, z, block);
                }
                boolean outer = Math.abs(x) == 21 || z == -81 || z == -55;
                if (outer && Math.floorMod(x + z, 2) == 0) {
                    put(x, wallTop + 1, z, "stone_bricks");
                    put(x, wallTop + 2, z, "stone_brick_slab[type=bottom]");
                }
            }
        }
        int[][] corners = {{-21, -81}, {21, -81}, {-21, -55}, {21, -55}};
        for (int[] corner : corners) {
            tower(corner[0], corner[1], 3.6, 14, 7);
        }
        tower(-5, -55, 2.6, 13, 5);
        tower(5, -55, 2.6, 13, 5);
        for (int side = -1; side <= 1; side += 2) {
            put(side * 3, 7, -54, "light_blue_wall_banner[facing=south]");
        }
        keep();
        well(-13, -61);
        castleDetails(wallTop);
        int[][] trees = {{-16, -74}, {16, -75}};
        for (int[] tree : trees) {
            oak(tree[0], 1, tree[1], 5 + (int) (noise(tree[0], tree[1], 53) * 2), false);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && noise(tree[0] + dx, tree[1] + dz, 54) < 0.6) {
                        String[] flowers = {"cornflower", "azure_bluet", "lily_of_the_valley", "allium"};
                        put(tree[0] + dx, 1, tree[1] + dz, flowers[(int) (noise(tree[0] + dz, tree[1] + dx, 55) * flowers.length)]);
                    }
                }
            }
        }
    }

    /** A vár díszei: pillérek, lőrések, párkány, kapu csapóráccsal, udvari utak, standok, lámpák, tavirózsák. */
    private void castleDetails(int wallTop) {
        // pillérek és lőrések a külső falon
        for (int x = -21; x <= 21; x++) {
            for (int z = -81; z <= -55; z++) {
                boolean outer = Math.abs(x) == 21 || z == -81 || z == -55;
                if (!outer || (Math.abs(x) <= 7 && z == -55)) {
                    continue;
                }
                int along = Math.abs(x) == 21 ? z : x;
                int ox = Math.abs(x) == 21 ? Integer.signum(x) : 0;
                int oz = z == -81 ? -1 : (z == -55 ? 1 : 0);
                boolean corner = Math.abs(x) >= 17 && (z <= -77 || z >= -59);
                if (corner) {
                    continue;
                }
                if (Math.floorMod(along, 6) == 0) {
                    for (int y = 1; y <= wallTop - 1; y++) {
                        put(x + ox, y, z + oz, y <= 2 ? "cobblestone" : "stone_bricks");
                    }
                    put(x + ox, wallTop, z + oz, stairs("stone_brick", facing(-ox, -oz), true));
                } else {
                    put(x + ox, wallTop, z + oz, stairs("stone_brick", facing(-ox, -oz), true));
                    if (Math.floorMod(along, 6) == 3) {
                        put(x, 6, z, "iron_bars");
                        put(x, 7, z, "iron_bars");
                    }
                }
            }
        }
        // lámpások a fal tetején, belül
        for (int x = -19; x <= 19; x += 6) {
            put(x, wallTop + 1, -80, "lantern[hanging=false]");
        }
        for (int z = -76; z <= -60; z += 6) {
            put(-20, wallTop + 1, z, "lantern[hanging=false]");
            put(20, wallTop + 1, z, "lantern[hanging=false]");
        }
        // kapu: csúcsív, csapórács, kaputorony
        for (int side = -1; side <= 1; side += 2) {
            put(side * 2, 5, -55, stairs("stone_brick", side < 0 ? "east" : "west", true));
            put(side * 2, 5, -56, stairs("stone_brick", side < 0 ? "east" : "west", true));
        }
        for (int x = -1; x <= 1; x++) {
            put(x, 5, -55, "iron_bars");
            put(x, 4, -55, "iron_bars");
            put(x, 5, -56, "air");
        }
        for (int x = -4; x <= 4; x++) {
            for (int y = wallTop + 1; y <= wallTop + 3; y++) {
                put(x, y, -55, y == wallTop + 3 ? (Math.floorMod(x, 2) == 0 ? "stone_bricks" : "air") : "stone_bricks");
                put(x, y, -56, y == wallTop + 3 ? "air" : "stone_bricks");
            }
            if (Math.abs(x) <= 3) {
                put(x, wallTop + 1, -54, stairs("stone_brick", "north", true));
            }
        }
        put(0, wallTop + 2, -54, "light_blue_wall_banner[facing=south]");
        put(0, wallTop + 1, -54, "chiseled_stone_bricks");
        put(-3, 4, -54, "lantern[hanging=false]");
        put(3, 4, -54, "lantern[hanging=false]");
        for (int side = -1; side <= 1; side += 2) {
            put(side * 2, 2, -50, "lantern[hanging=false]");
            put(side * 2, 2, -53, "lantern[hanging=false]");
        }
        // udvar: keresztút, lámpák, standok
        for (int x = -18; x <= 18; x++) {
            for (int z = -62; z <= -60; z++) {
                if (Math.abs(x) > 2 && !(x >= -15 && x <= -11)) {
                    put(x, 0, z, noise(x, z, 56) < 0.5 ? "gravel" : (noise(x, z, 57) < 0.6 ? "cobblestone" : "stone_bricks"));
                }
            }
        }
        for (int[] l : new int[][]{{-3, -58}, {3, -58}, {-3, -64}, {3, -64}, {-9, -63}, {9, -63}}) {
            put(l[0], 1, l[1], "cobblestone_wall[up=true]");
            put(l[0], 2, l[1], "spruce_fence");
            put(l[0], 3, l[1], "lantern[hanging=false]");
        }
        stall(7, -58, "light_blue_wool", "white_wool");
        stall(13, -58, "red_wool", "white_wool");
        stall(-8, -58, "yellow_wool", "white_wool");
        for (int[] h : new int[][]{{17, -58}, {18, -58}, {17, -59}, {-18, -64}, {-18, -65}}) {
            put(h[0], 1, h[1], "hay_block[axis=y]");
        }
        put(18, 2, -58, "hay_block[axis=x]");
        put(-17, 1, -64, "barrel[facing=up]");
        put(-17, 1, -65, "barrel[facing=up]");
        // tavirózsák a várárokban
        for (int x = -25; x <= 25; x++) {
            for (int z = -85; z <= -51; z++) {
                boolean inner = Math.abs(x) <= 22 && z >= -82 && z <= -54;
                if (!inner && !(Math.abs(x) <= 2 && z >= -54) && noise(x, z, 58) < 0.06) {
                    put(x, 1, z, "lily_pad");
                }
            }
        }
    }

    private static String facing(int dx, int dz) {
        if (dx > 0) {
            return "east";
        }
        if (dx < 0) {
            return "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /** Piaci stand: négy oszlop, csíkos ponyva, pult. */
    private void stall(int x0, int z0, String wool, String stripe) {
        for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            put(x0 + c[0], 1, z0 - c[1], "spruce_fence");
            put(x0 + c[0], 2, z0 - c[1], "spruce_fence");
        }
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                put(x0 + dx, 3, z0 - dz, Math.floorMod(dx, 2) == 0 ? wool : stripe);
            }
        }
        put(x0 + 1, 1, z0, "barrel[facing=up]");
        put(x0, 1, z0 - 1, "spruce_slab[type=top]");
        put(x0 + 2, 1, z0 - 1, "crafting_table");
        put(x0 + 1, 1, z0 - 2, "composter");
    }

    private void keep() {
        int kx0 = -11;
        int kx1 = 11;
        int kz0 = -79;
        int kz1 = -67;
        int keepTop = 16;
        for (int x = kx0; x <= kx1; x++) {
            for (int z = kz0; z <= kz1; z++) {
                boolean alongX = z == kz0 || z == kz1;
                boolean alongZ = x == kx0 || x == kx1;
                boolean wall = alongX || alongZ;
                for (int y = 1; y <= keepTop; y++) {
                    if (!wall) {
                        put(x, y, z, y % 6 == 0 ? "spruce_planks" : "air");
                        continue;
                    }
                    int along = alongX ? x : z;
                    boolean window = (y % 6 == 3 || y % 6 == 4) && Math.floorMod(along, 4) == 1 && !(alongX && alongZ);
                    double n = noise(x, y * 17 + z, 52);
                    String block = window ? (alongX ? "light_blue_stained_glass_pane[east=true,west=true]" : "light_blue_stained_glass_pane[north=true,south=true]")
                            : (n < 0.15 ? "mossy_stone_bricks" : (n < 0.27 ? "cracked_stone_bricks" : "stone_bricks"));
                    put(x, y, z, block);
                }
            }
        }
        int ridge = (kz0 + kz1) / 2;
        int half = (kz1 - kz0) / 2 + 1;
        for (int i = 0; i <= half; i++) {
            int y = keepTop + 1 + i;
            for (int x = kx0 - 1; x <= kx1 + 1; x++) {
                if (kz0 - 1 + i < ridge) {
                    put(x, y, kz0 - 1 + i, stairs("deepslate_tile", "south", false));
                }
                if (kz1 + 1 - i > ridge) {
                    put(x, y, kz1 + 1 - i, stairs("deepslate_tile", "north", false));
                }
                if (kz0 - 1 + i == ridge || kz1 + 1 - i == ridge) {
                    put(x, y, ridge, "deepslate_tiles");
                }
            }
            for (int z = kz0 + i; z <= kz1 - i; z++) {
                put(kx0, y, z, "stone_bricks");
                put(kx1, y, z, "stone_bricks");
            }
        }
        for (int y = keepTop + 1; y <= keepTop + half + 3; y++) {
            put(kx1 - 4, y, ridge + 2, y >= keepTop + half + 2 ? "cobblestone_wall[up=true]" : "bricks");
        }
        put(kx1 - 4, keepTop + half + 4, ridge + 2, "campfire[lit=false]");
        // lépcső, szőnyeg, erkély
        for (int x = -2; x <= 2; x++) {
            put(x, 0, kz1 + 1, "stone_bricks");
            put(x, 1, kz1 + 1, Math.abs(x) == 2 ? "stone_bricks" : "light_blue_carpet");
            put(x, 1, kz1 + 2, Math.abs(x) == 2 ? stairs("stone_brick", "south", false) : "air");
        }
        for (int z = kz1 + 2; z <= -54; z++) {
            put(0, 1, z, "light_blue_carpet");
        }
        for (int x = -3; x <= 3; x++) {
            put(x, 6, kz1 + 1, "stone_brick_slab[type=top]");
            put(x, 6, kz1 + 2, "stone_brick_slab[type=top]");
            put(x, 7, kz1 + 2, "spruce_fence");
            put(x, 5, kz1 + 2, stairs("stone_brick", "north", true));
        }
        put(-3, 7, kz1 + 1, "spruce_fence");
        put(3, 7, kz1 + 1, "spruce_fence");
        for (int x = -1; x <= 1; x++) {
            put(x, 7, kz1, "air");
            put(x, 8, kz1, "air");
        }
        put(-3, 8, kz1 + 2, "lantern[hanging=false]");
        put(3, 8, kz1 + 2, "lantern[hanging=false]");
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                put(x, y, kz1, "air");
            }
            put(x, 4, kz1, x == 0 ? "chiseled_stone_bricks" : "stone_brick_stairs[facing=south,half=top]");
        }
        put(-1, 1, kz1, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        put(-1, 2, kz1, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        put(1, 1, kz1, "spruce_door[facing=south,half=lower,hinge=right,open=false]");
        put(1, 2, kz1, "spruce_door[facing=south,half=upper,hinge=right,open=false]");
        for (int side = -1; side <= 1; side += 2) {
            put(side * 4, 10, kz1 + 1, "light_blue_wall_banner[facing=south]");
            put(side * 8, 10, kz1 + 1, "white_wall_banner[facing=south]");
        }
        tower(kx0, kz1, 3.4, 21, 7);
        tower(kx1, kz1, 3.4, 21, 7);
        tower(0, kz0, 4.2, 20, 8);
    }

    private void tower(int tx, int tz, double radius, int height, int roofHeight) {
        int r = (int) Math.ceil(radius) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d > radius + 0.4) {
                    continue;
                }
                boolean shell = d > radius - 0.9;
                for (int y = 0; y <= height; y++) {
                    if (!shell) {
                        put(tx + dx, y, tz + dz, y == 0 || y % 7 == 0 ? "spruce_planks" : "air");
                        continue;
                    }
                    boolean window = y % 7 == 4 && (dx == 0 || dz == 0) && y < height - 2;
                    boolean slit = !window && y % 7 == 4 && Math.abs(dx) == Math.abs(dz) && y < height - 2;
                    double n = noise(tx + dx, y * 13 + tz + dz, 60);
                    String block = window ? "light_blue_stained_glass" : (slit ? "iron_bars" : (y == 0 ? "cobblestone"
                            : (y % 7 == 0 ? "polished_andesite" : (n < 0.15 ? "mossy_stone_bricks" : (n < 0.26 ? "cracked_stone_bricks" : "stone_bricks")))));
                    put(tx + dx, y, tz + dz, block);
                }
            }
        }
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = dist(dx, dz);
                if (d <= radius + 1.2) {
                    put(tx + dx, height + 1, tz + dz, d > radius + 0.4 ? stairs("stone_brick", outward(dx, dz), true) : "stone_bricks");
                }
            }
        }
        cone(tx, height + 2, tz, radius + 1.0, roofHeight, "deepslate_tile", "deepslate_tiles", "spruce_fence");
        int tip = height + 2 + roofHeight + 1;
        put(tx, tip + 1, tz, "spruce_fence");
        put(tx, tip + 2, tz, "spruce_fence");
        boolean blue = Math.floorMod(tx + tz, 2) == 0;
        for (int fx = 1; fx <= 3; fx++) {
            put(tx + fx, tip + 2, tz, blue ? "light_blue_wool" : "white_wool");
            if (fx <= 2) {
                put(tx + fx, tip + 1, tz, blue ? "white_wool" : "light_blue_wool");
            }
        }
    }

    private void well(int wx, int wz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, 0, wz + dz, dx == 0 && dz == 0 ? "water" : "cobblestone");
                put(wx + dx, 1, wz + dz, dx == 0 && dz == 0 ? "air" : "cobblestone_wall[up=true]");
            }
        }
        put(wx, -1, wz, "cobblestone");
        for (int[] p : new int[][]{{-1, -1}, {1, 1}}) {
            put(wx + p[0], 2, wz + p[1], "spruce_fence");
            put(wx + p[0], 3, wz + p[1], "spruce_fence");
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, 4, wz + dz, "spruce_slab[type=bottom]");
            }
        }
        put(wx, 3, wz, "chain[axis=y]");
    }

    private void letters() {
        String[][] word = {I, C, E, M, I, N, E};
        int scale = 2;
        int width = word.length * 5 * scale + (word.length - 1) * 2;
        int left = -width / 2;
        int base = 22;
        int front = -47;
        int rows = 7 * scale;
        boolean[][] fill = new boolean[width + 2][rows + 2];
        int x = 0;
        for (String[] letter : word) {
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (letter[row].charAt(col) != '#') {
                        continue;
                    }
                    for (int sx = 0; sx < scale; sx++) {
                        for (int sy = 0; sy < scale; sy++) {
                            fill[x + col * scale + sx + 1][row * scale + sy + 1] = true;
                        }
                    }
                }
            }
            x += 5 * scale + 2;
        }
        for (int gx = 0; gx < width + 2; gx++) {
            for (int gy = 0; gy < rows + 2; gy++) {
                boolean filled = fill[gx][gy];
                boolean outline = false;
                if (!filled) {
                    for (int ox = -1; ox <= 1 && !outline; ox++) {
                        for (int oy = -1; oy <= 1; oy++) {
                            int nx = gx + ox;
                            int ny = gy + oy;
                            if (nx >= 0 && ny >= 0 && nx < width + 2 && ny < rows + 2 && fill[nx][ny]) {
                                outline = true;
                                break;
                            }
                        }
                    }
                }
                if (!filled && !outline) {
                    continue;
                }
                int wx = left - 1 + gx;
                int wy = base + rows + 1 - gy;
                boolean ice = gx - 1 < 3 * (5 * scale + 2);
                String face;
                if (outline) {
                    face = "blue_terracotta";
                } else if (ice) {
                    face = "light_blue_concrete";
                } else {
                    face = "white_concrete";
                }
                put(wx, wy, front, face);
                put(wx, wy, front - 1, filled ? "light_blue_terracotta" : "blue_terracotta");
                put(wx, wy, front - 2, "blue_terracotta");
            }
        }
    }

    private void oak(int x, int y, int z, int height, boolean birch) {
        String log = birch ? "birch_log[axis=y]" : "oak_log[axis=y]";
        String leaves = birch ? "birch_leaves[persistent=true]" : "oak_leaves[persistent=true]";
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, log);
        }
        int top = y + height;
        for (int dy = -2; dy <= 1; dy++) {
            double r = dy <= -1 ? 2.4 : (dy == 0 ? 1.8 : 1.1);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dist(dx, dz) <= r && !(dx == 0 && dz == 0 && dy < 0) && noise(x + dx, z + dz + dy, 70) > 0.08) {
                        put(x + dx, top + dy, z + dz, leaves);
                    }
                }
            }
        }
    }

    private void spruce(int tx, int ty, int tz, int height, double width, boolean snowy) {
        for (int y = 1; y <= height; y++) {
            put(tx, ty + y, tz, "spruce_log[axis=y]");
        }
        int layers = height - 2;
        for (int i = 0; i < layers; i++) {
            int y = ty + 3 + i;
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
        put(tx, ty + height + 1, tz, "spruce_leaves[persistent=true]");
        if (snowy) {
            put(tx, ty + height + 2, tz, "snow[layers=2]");
        }
    }

    private void trees() {
        for (int gx = MIN_X; gx <= MAX_X; gx += 4) {
            for (int gz = MIN_Z; gz <= MAX_Z; gz += 4) {
                int x = gx + (int) (noise(gx, gz, 80) * 3);
                int z = gz + (int) (noise(gx, gz, 81) * 3);
                int top = h(x, z);
                if (top < 2 || top > 24 || (z > -24 && Math.abs(x) < 32) || !free(x, z, 2)) {
                    continue;
                }
                int steep = Math.max(Math.abs(h(x + 1, z) - top), Math.abs(h(x, z + 1) - top));
                if (steep > 2 || noise(x, z, 82) > 0.8) {
                    continue;
                }
                use(x - 1, z - 1, x + 1, z + 1);
                spruce(x, top, z, 6 + (int) (noise(x, z, 83) * 5), 2.2 + noise(x, z, 84) * 0.6, top >= 12);
            }
        }
        int[][] village = {{-18, -24}, {18, -24}, {-30, -23}, {30, -23}, {-43, -33}, {43, -33}, {-19, -51}, {19, -51},
                {-31, -51}, {31, -51}, {-44, -50}, {44, -50}, {-8, -22}, {8, -22}};
        for (int i = 0; i < village.length; i++) {
            int x = village[i][0];
            int z = village[i][1];
            if (h(x, z) != 0 || !free(x, z, 1)) {
                continue;
            }
            use(x - 1, z - 1, x + 1, z + 1);
            oak(x, 1, z, 4 + (int) (noise(x, z, 85) * 3), i % 3 == 1);
        }
    }

    /** A sziget széle körül láthatatlan fal, hogy ne lehessen leugrani vagy kimászni. A parkour kijáratánál rés van. */
    static final int BARRIER_TOP = 50;

    static boolean parkourGap(int x, int z) {
        return Math.abs(x) <= 6 && z > 0;
    }

    private void barrier() {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                int top = h(x, z);
                if (top < -50 || parkourGap(x, z)) {
                    continue;
                }
                boolean edge = false;
                for (int[] d : around) {
                    if (h(x + d[0], z + d[1]) < -50) {
                        edge = true;
                        break;
                    }
                }
                if (!edge) {
                    continue;
                }
                int from = onPlatform(x, z) ? PLATFORM + 1 : Math.max(top, 0) + 1;
                for (int y = from; y <= BARRIER_TOP; y++) {
                    put(x, y, z, "barrier");
                }
            }
        }
    }

    // ---- kitöltés: tanyák nyugaton, piac keleten, lebegő szigetek körben (v15) ----

    /** Csak a sík, még szabad mezőre építünk (a hegyekre, utakra, házakra nem). */
    private boolean flat(int x, int z) {
        return h(x, z) == 0 && x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z && !used[x - MIN_X][z - MIN_Z];
    }

    static boolean DEBUG;

    /**
     * A terület beépíthető-e: a szigeten van, senki nem foglalta, és legfeljebb 3 blokkal magasabb a mezőnél.
     * Ha igen, el is egyengeti (y = 0 lesz a talaj), hogy a házak ne lógjanak a levegőben vagy a dombba.
     */
    private boolean flatArea(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                int top = h(x, z);
                if (top < 0 || top > 3 || used[x - MIN_X][z - MIN_Z]) {
                    if (DEBUG) {
                        System.out.println("DBG area " + x0 + "," + z0 + " fail at " + x + "," + z + " h=" + top);
                    }
                    return false;
                }
            }
        }
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                int top = h(x, z);
                for (int y = 1; y <= top + 2; y++) {
                    put(x, y, z, "air");
                }
                put(x, 0, z, "grass_block");
                height[x - MIN_X][z - MIN_Z] = 0;
            }
        }
        return true;
    }

    /** Földút két pont között (előbb x, aztán z irányban), csak sík mezőn. */
    private void lane(int x0, int z0, int x1, int z1, int half) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int w = -half; w <= half; w++) {
                lanePath(x, z0 + w);
            }
        }
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int w = -half; w <= half; w++) {
                lanePath(x1 + w, z);
            }
        }
    }

    private void lanePath(int x, int z) {
        int top = h(x, z);
        if (top < 0 || top > 3 || used[x - MIN_X][z - MIN_Z]) {
            return;
        }
        for (int y = 1; y <= top + 2; y++) {
            put(x, y, z, "air");
        }
        height[x - MIN_X][z - MIN_Z] = 0;
        path(x, z);
    }

    private void lampPost(int x, int z) {
        if (h(x, z) != 0) {
            return;
        }
        put(x, 1, z, "cobblestone_wall[up=true]");
        put(x, 2, z, "spruce_fence");
        put(x, 3, z, "spruce_fence");
        put(x, 4, z, "lantern[hanging=false]");
        use(x, z, x, z);
    }

    static final House[] FARM_HOUSES = {
            new House(-62, -26, 9, 7, 2, "south", "spruce", "white_terracotta"),
            new House(-74, -25, 7, 7, 1, "south", "acacia", "smooth_sandstone"),
            new House(-46, -24, 7, 9, 1, "west", "dark_oak", "white_terracotta"),
            new House(-64, 1, 9, 7, 1, "north", "acacia", "smooth_sandstone"),
            new House(-77, 0, 7, 7, 1, "north", "spruce", "white_terracotta"),
            new House(44, -15, 7, 9, 1, "west", "spruce", "smooth_sandstone"),
            new House(45, 1, 9, 7, 2, "north", "acacia", "white_terracotta"),
            new House(60, 0, 7, 7, 1, "north", "dark_oak", "smooth_sandstone"),
            new House(73, 0, 9, 7, 1, "north", "spruce", "white_terracotta")
    };

    private void districts() {
        lane(-52, -35, -52, -6, 1);
        lane(-90, -6, -52, -6, 1);
        lane(52, -35, 52, -6, 1);
        lane(52, -6, 82, -6, 1);
        for (House house : FARM_HOUSES) {
            int x0 = house.x - house.sx / 2 - 1;
            int z0 = house.z - house.sz / 2 - 1;
            if (flatArea(x0, z0, x0 + house.sx + 1, z0 + house.sz + 1)) {
                house(house);
            }
        }
        for (int[] lamp : new int[][]{{-50, -30}, {-50, -18}, {-50, -10}, {-60, -4}, {-72, -4}, {-84, -4},
                {50, -24}, {50, -16}, {50, -10}, {62, -4}, {74, -4}}) {
            lampPost(lamp[0], lamp[1]);
        }
        fields(-90, -15, -56, -9);
        windmill(-40, -13);
        farmyard(-101, -13);
        market(66, -21);
        forge(43, -32);
        orchard(30, -6, 40, 6);
        orchard(-40, -2, -30, 6);
        int[][] islands = {{-106, 8, 28, 8}, {108, 14, 30, 7}, {-72, -12, 54, 9}, {84, 24, 64, 9}, {-58, 26, 90, 6}, {0, -22, 102, 6},
                {118, -8, -70, 7}, {-120, 20, -72, 7}};
        for (int i = 0; i < islands.length; i++) {
            floating(islands[i][0], islands[i][1], islands[i][2], islands[i][3], i);
        }
    }

    /** Termőföldek: búza, répa, krumpli, cékla sorokban, közöttük öntözőcsatorna, a végükön madárijesztő. */
    private void fields(int x0, int z0, int x1, int z1) {
        String[] crops = {"wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]", "wheat[age=6]"};
        int plot = 0;
        for (int px = x0; px + 6 <= x1; px += 8) {
            if (!flatArea(px, z0, px + 6, z1)) {
                plot++;
                continue;
            }
            use(px - 1, z0 - 1, px + 7, z1 + 1);
            String crop = crops[plot % crops.length];
            for (int x = px; x <= px + 6; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean water = x == px + 3;
                    boolean rim = z == z0 || z == z1;
                    if (water && !rim) {
                        put(x, 0, z, "water");
                        put(x, -1, z, "clay");
                        put(x, 1, z, "air");
                    } else if (rim) {
                        put(x, 0, z, "coarse_dirt");
                        put(x, 1, z, water ? "oak_slab[type=bottom]" : "air");
                    } else {
                        put(x, 0, z, "farmland[moisture=7]");
                        put(x, 1, z, crop);
                    }
                }
            }
            put(px - 1, 0, z0 - 1, "oak_log[axis=y]");
            put(px + 7, 0, z1 + 1, "oak_log[axis=y]");
            if (plot % 2 == 0) {
                int sx = px + 1;
                int sz = (z0 + z1) / 2;
                put(sx, 1, sz, "spruce_fence");
                put(sx, 2, sz, "hay_block[axis=y]");
                put(sx, 3, sz, "carved_pumpkin[facing=south]");
                put(sx - 1, 2, sz, "spruce_fence[east=true]");
                put(sx + 1, 2, sz, "spruce_fence[west=true]");
            }
            plot++;
        }
    }

    /** Nagy szélmalom kőalappal, fa felsővel és négy vitorlával. */
    private void windmill(int mx, int mz) {
        if (!flatArea(mx - 5, mz - 5, mx + 5, mz + 5)) {
            return;
        }
        use(mx - 5, mz - 5, mx + 5, mz + 5);
        int height = 14;
        for (int y = 0; y <= height; y++) {
            double r = 4.3 - y * 0.12;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = dist(dx, dz);
                    if (d > r) {
                        continue;
                    }
                    boolean wall = d > r - 1.1;
                    String block;
                    if (y == 0) {
                        block = wall ? "stone_bricks" : "spruce_planks";
                    } else if (!wall) {
                        block = y % 5 == 0 ? "spruce_planks" : "air";
                    } else if (y <= 5) {
                        double n = noise(mx + dx, mz + dz + y, 140);
                        block = n < 0.6 ? "cobblestone" : (n < 0.85 ? "stone_bricks" : "mossy_cobblestone");
                    } else {
                        boolean window = (y == 8 || y == 9 || y == 12) && (dx == 0 || dz == 0);
                        block = window ? "glass_pane" : (Math.floorMod(dx + dz, 4) == 0 ? "stripped_spruce_log[axis=y]" : "white_terracotta");
                    }
                    put(mx + dx, y, mz + dz, block);
                }
            }
        }
        cone(mx, height + 1, mz, 3.6, 5, "spruce", "spruce_planks", "spruce_fence");
        put(mx, 1, mz + 4, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        put(mx, 2, mz + 4, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        for (int z = mz + 5; z <= mz + 7; z++) {
            if (h(mx, z) == 0) {
                path(mx, z);
            }
        }
        int hubY = height - 2;
        int hubZ = mz + 4;
        put(mx, hubY, hubZ, "spruce_log[axis=z]");
        put(mx, hubY, hubZ + 1, "spruce_log[axis=z]");
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] arm : arms) {
            for (int i = 1; i <= 8; i++) {
                int ax = mx + arm[0] * i;
                int ay = hubY + arm[1] * i;
                put(ax, ay, hubZ + 1, "stripped_spruce_log[axis=" + (arm[0] != 0 ? "x" : "y") + "]");
                if (i >= 2) {
                    for (int w = 1; w <= 2; w++) {
                        put(ax - arm[1] * w, ay + arm[0] * w, hubZ + 1, i == 8 ? "stripped_spruce_log[axis=y]" : "white_wool");
                    }
                }
            }
        }
        for (int[] p : new int[][]{{-6, 2}, {-6, 3}, {5, -5}, {6, -4}}) {
            if (flat(mx + p[0], mz + p[1])) {
                put(mx + p[0], 1, mz + p[1], "hay_block[axis=y]");
                use(mx + p[0], mz + p[1], mx + p[0], mz + p[1]);
            }
        }
    }

    /** Tanya udvar: pajta, szénabálák, méhkaptárak, szekér. */
    private void farmyard(int bx, int bz) {
        if (!flatArea(bx - 1, bz - 1, bx + 9, bz + 7)) {
            return;
        }
        use(bx - 1, bz - 1, bx + 9, bz + 7);
        for (int x = bx; x <= bx + 8; x++) {
            for (int z = bz; z <= bz + 6; z++) {
                boolean wall = x == bx || x == bx + 8 || z == bz || z == bz + 6;
                boolean corner = (x == bx || x == bx + 8) && (z == bz || z == bz + 6);
                put(x, 0, z, "spruce_planks");
                for (int y = 1; y <= 4; y++) {
                    String block = !wall ? "air" : (corner ? "stripped_dark_oak_log[axis=y]" : (y == 4 ? "stripped_dark_oak_log[axis=" + (z == bz || z == bz + 6 ? "x" : "z") + "]" : "red_terracotta"));
                    put(x, y, z, block);
                }
            }
        }
        for (int i = 0; i <= 4; i++) {
            for (int x = bx - 1; x <= bx + 9; x++) {
                put(x, 5 + i, bz - 1 + i, stairs("dark_oak", "south", false));
                put(x, 5 + i, bz + 7 - i, stairs("dark_oak", "north", false));
            }
            for (int z = bz + i; z <= bz + 6 - i; z++) {
                put(bx, 5 + i, z, "red_terracotta");
                put(bx + 8, 5 + i, z, "red_terracotta");
            }
        }
        for (int x = bx + 3; x <= bx + 5; x++) {
            for (int y = 1; y <= 3; y++) {
                put(x, y, bz + 6, "air");
            }
        }
        put(bx + 4, 4, bz + 6, "white_terracotta");
        put(bx + 1, 1, bz + 1, "hay_block[axis=x]");
        put(bx + 2, 1, bz + 1, "hay_block[axis=x]");
        put(bx + 1, 2, bz + 1, "hay_block[axis=x]");
        put(bx + 7, 1, bz + 1, "barrel[facing=up]");
        put(bx + 7, 1, bz + 2, "composter[level=6]");
        for (int i = 0; i < 3; i++) {
            put(bx + 10, 1, bz + 1 + i * 2, "beehive[facing=east,honey_level=5]");
        }
        put(bx + 4, 1, bz + 8, "hay_block[axis=z]");
        put(bx + 2, 1, bz + 8, "oak_trapdoor[facing=north,half=bottom,open=true]");
        put(bx + 6, 1, bz + 8, "oak_trapdoor[facing=north,half=bottom,open=true]");
    }

    /** Piactér: kör alakú kövezett tér, középen kút, körben színes sátras standok, padok. */
    private void market(int mx, int mz) {
        int r = 9;
        if (!flatArea(mx - r, mz - r, mx + r, mz + r)) {
            return;
        }
        use(mx - r - 1, mz - r - 1, mx + r + 1, mz + r + 1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d > r + 0.4) {
                    continue;
                }
                double n = noise(mx + dx, mz + dz, 150);
                String block = d > r - 0.8 ? "polished_andesite" : (n < 0.45 ? "stone_bricks" : (n < 0.7 ? "andesite" : (n < 0.88 ? "cobblestone" : "mossy_stone_bricks")));
                put(mx + dx, 0, mz + dz, block);
                put(mx + dx, 1, mz + dz, "air");
            }
        }
        well(mx, mz);
        String[][] colors = {{"red_wool", "white_wool"}, {"yellow_wool", "orange_wool"}, {"lime_wool", "green_wool"},
                {"light_blue_wool", "blue_wool"}, {"magenta_wool", "purple_wool"}, {"orange_wool", "white_wool"}};
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.5;
            int sx = mx + (int) Math.round(Math.cos(a) * 6) - 1;
            int sz = mz + (int) Math.round(Math.sin(a) * 6) + 1;
            stall(sx, sz, colors[i][0], colors[i][1]);
        }
        for (int[] p : new int[][]{{-r, 0}, {r, 0}, {0, -r}, {0, r}}) {
            put(mx + p[0], 1, mz + p[1], "cobblestone_wall[up=true]");
            put(mx + p[0], 2, mz + p[1], "spruce_fence");
            put(mx + p[0], 3, mz + p[1], "lantern[hanging=false]");
        }
        for (int[] p : new int[][]{{-6, -6}, {6, 6}, {6, -6}, {-6, 6}}) {
            put(mx + p[0], 1, mz + p[1], stairs("spruce", p[0] < 0 ? "east" : "west", false));
            put(mx + p[0] + (p[0] < 0 ? 0 : 0), 1, mz + p[1] + (p[1] < 0 ? 1 : -1), stairs("spruce", p[0] < 0 ? "east" : "west", false));
            put(mx + p[0] + (p[0] < 0 ? -1 : 1), 1, mz + p[1], "potted_red_tulip");
        }
    }

    /** Kovácsműhely: nyitott tető, kohó lávával, üllő, köszörű. */
    private void forge(int fx, int fz) {
        if (!flatArea(fx - 1, fz - 1, fx + 7, fz + 6)) {
            return;
        }
        use(fx - 1, fz - 1, fx + 7, fz + 6);
        for (int x = fx; x <= fx + 6; x++) {
            for (int z = fz; z <= fz + 5; z++) {
                put(x, 0, z, noise(x, z, 160) < 0.5 ? "stone_bricks" : "cobblestone");
                for (int y = 1; y <= 4; y++) {
                    boolean post = (x == fx || x == fx + 6) && (z == fz || z == fz + 5);
                    boolean back = z == fz;
                    put(x, y, z, post ? "stripped_spruce_log[axis=y]" : (back ? (y <= 2 ? "stone_bricks" : "spruce_planks") : "air"));
                }
                put(x, 5, z, z == fz || z == fz + 5 ? stairs("deepslate_tile", z == fz ? "south" : "north", false) : "deepslate_tiles");
            }
        }
        for (int y = 1; y <= 8; y++) {
            put(fx + 1, y, fz, y <= 2 ? "lava_cauldron" : "bricks");
        }
        put(fx + 1, 1, fz + 1, "blast_furnace[facing=south,lit=true]");
        put(fx + 3, 1, fz + 2, "anvil[facing=east]");
        put(fx + 4, 1, fz + 3, "grindstone[face=floor,facing=north]");
        put(fx + 5, 1, fz + 1, "smithing_table");
        put(fx + 5, 2, fz + 1, "chain[axis=y]");
        put(fx + 2, 4, fz + 3, "lantern[hanging=true]");
        put(fx + 6, 1, fz + 3, "barrel[facing=up]");
        put(fx + 5, 1, fz + 4, "water_cauldron[level=3]");
    }

    /** Gyümölcsös: sorokban álló kis fák, közöttük virágok és méhkaptár. */
    private void orchard(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x += 4) {
            for (int z = z0; z <= z1; z += 4) {
                int tx = x + (int) (noise(x, z, 170) * 2);
                int tz = z + (int) (noise(x, z, 171) * 2);
                int top = h(tx, tz);
                if (top < 0 || top > 5 || !free(tx, tz, 1)) {
                    continue;
                }
                use(tx - 1, tz - 1, tx + 1, tz + 1);
                oak(tx, top + 1, tz, 3 + (int) (noise(tx, tz, 172) * 2), noise(tx, tz, 173) < 0.3);
            }
        }
    }

    /** Lebegő kis sziget a nagy körül: füves tető, kő és jég alja, rajta fák, néhányon kis ház vagy rom. */
    private void floating(int ix, int iy, int iz, int radius, int seed) {
        int max = radius + 2;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double a = Math.atan2(dz, dx);
                double edge = radius + 1.1 * Math.sin(a * 3 + seed) + 0.6 * Math.sin(a * 7 + seed * 2);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                int x = ix + dx;
                int z = iz + dz;
                put(x, iy, z, noise(x, z, 180 + seed) < 0.08 ? "moss_block" : "grass_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.5)) * radius * 1.1) + 1 + (int) (noise(x, z, 181) * 2);
                for (int l = 1; l <= deep; l++) {
                    double n = noise(x, z + l * 3, 182 + seed);
                    put(x, iy - l, z, l <= 1 ? "dirt" : (l >= deep - 1 ? (n < 0.5 ? "packed_ice" : "stone") : (n < 0.55 ? "stone" : (n < 0.8 ? "andesite" : "dirt"))));
                }
                if (noise(x, z, 183) < 0.05 && deep > 3) {
                    int length = 2 + (int) (noise(x, z, 184) * 5);
                    for (int l = 1; l <= length; l++) {
                        put(x, iy - deep - l, z, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
                double n = noise(x, z, 185);
                if (d < edge - 1) {
                    put(x, iy + 1, z, n < 0.12 ? "short_grass" : (n < 0.14 ? "poppy" : (n < 0.16 ? "cornflower" : "air")));
                }
            }
        }
        switch (seed % 4) {
            case 0:
                spruce(ix - 2, iy, iz + 1, 7, 2.4, true);
                spruce(ix + 3, iy, iz - 2, 5, 1.8, false);
                break;
            case 1:
                oak(ix + 2, iy + 1, iz, 4, false);
                ruin(ix - 3, iy, iz - 2);
                break;
            case 2:
                hut(ix - 2, iy, iz - 2);
                oak(ix + 4, iy + 1, iz + 3, 4, true);
                break;
            default:
                spruce(ix, iy, iz, 8, 2.6, true);
                put(ix + 3, iy + 1, iz + 2, "mossy_cobblestone");
                put(ix + 3, iy + 2, iz + 2, "mossy_cobblestone_wall[up=true]");
                break;
        }
    }

    private void ruin(int x0, int y, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                if (!wall) {
                    continue;
                }
                int top = 1 + (int) (noise(x, z, 190) * 4);
                for (int yy = 1; yy <= top; yy++) {
                    put(x, y + yy, z, noise(x, z + yy, 191) < 0.5 ? "mossy_stone_bricks" : "cracked_stone_bricks");
                }
            }
        }
    }

    private void hut(int x0, int y, int z0) {
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                boolean wall = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                boolean corner = (x == x0 || x == x0 + 4) && (z == z0 || z == z0 + 4);
                put(x, y, z, "spruce_planks");
                for (int yy = 1; yy <= 3; yy++) {
                    put(x, y + yy, z, !wall ? "air" : (corner ? "spruce_log[axis=y]" : (yy == 2 && (x == x0 + 2 || z == z0 + 2) ? "glass_pane" : "spruce_planks")));
                }
            }
        }
        for (int i = 0; i <= 2; i++) {
            for (int x = x0 - 1 + i; x <= x0 + 5 - i; x++) {
                put(x, y + 4 + i, z0 - 1 + i, stairs("spruce", "south", false));
                put(x, y + 4 + i, z0 + 5 - i, stairs("spruce", "north", false));
            }
            for (int z = z0 + i; z <= z0 + 4 - i; z++) {
                put(x0 - 1 + i, y + 4 + i, z, "spruce_planks");
                put(x0 + 5 - i, y + 4 + i, z, "spruce_planks");
            }
        }
        put(x0 + 2, y + 6, z0 + 2, "spruce_slab[type=bottom]");
        put(x0 + 2, y + 1, z0 + 4, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        put(x0 + 2, y + 2, z0 + 4, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        put(x0 + 1, y + 1, z0 + 1, "lantern[hanging=false]");
    }

    /** Ami a végén még üres maradt a mezőn: bokrok, kövek, virágfoltok, nyírfák. */
    private void meadow() {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                int top = h(x, z);
                if (top < 0 || top > 6 || used[x - MIN_X][z - MIN_Z] || (z > -24 && Math.abs(x) < 26)) {
                    continue;
                }
                int steep = Math.max(Math.abs(h(x + 1, z) - top), Math.abs(h(x, z + 1) - top));
                if (steep > 1) {
                    continue;
                }
                double patch = smooth(x, z, 9.0, 200);
                double n = noise(x, z, 201);
                if (patch > 0.68 && n < 0.35) {
                    String[] flowers = {"cornflower", "oxeye_daisy", "allium", "azure_bluet", "red_tulip", "white_tulip", "lily_of_the_valley"};
                    put(x, top + 1, z, flowers[(int) (smooth(x, z, 14.0, 202) * flowers.length) % flowers.length]);
                } else if (n < 0.012 && free(x, z, 1)) {
                    use(x - 1, z - 1, x + 1, z + 1);
                    put(x, top + 1, z, "oak_leaves[persistent=true]");
                    put(x + 1, top + 1, z, "oak_leaves[persistent=true]");
                    put(x, top + 1, z + 1, "azalea_leaves[persistent=true]");
                    put(x, top + 2, z, noise(x, z, 203) < 0.5 ? "flowering_azalea_leaves[persistent=true]" : "oak_leaves[persistent=true]");
                } else if (n < 0.02 && free(x, z, 1)) {
                    use(x, z, x, z);
                    put(x, top + 1, z, noise(x, z, 204) < 0.5 ? "mossy_cobblestone" : "andesite");
                    if (noise(x, z, 205) < 0.4) {
                        put(x + 1, top + 1, z, "cobblestone_slab[type=bottom]");
                    }
                } else if (n < 0.024 && free(x, z, 2)) {
                    use(x - 1, z - 1, x + 1, z + 1);
                    oak(x, top + 1, z, 5 + (int) (noise(x, z, 206) * 2), true);
                } else if (n < 0.2) {
                    put(x, top + 1, z, n < 0.17 ? "short_grass" : "fern");
                } else if (n < 0.215) {
                    put(x, top + 1, z, "tall_grass[half=lower]");
                    put(x, top + 2, z, "tall_grass[half=upper]");
                }
            }
        }
    }

    // ---- új szárnyak ----

    private void wings() {
        wingRoads();
        lake();
        lookout();
        arena();
    }

    private void wingRoads() {
        for (int side = -1; side <= 1; side += 2) {
            int end = side < 0 ? LAKE_X + 14 : TOWER_X - 4;
            for (int ax = 47; ax <= Math.abs(end); ax++) {
                int x = side * ax;
                int wobble = (int) Math.round(Math.sin(ax / 7.0) * 2);
                for (int w = -1; w <= 1; w++) {
                    int z = -38 + wobble + w;
                    if (h(x, z) > -50) {
                        path(x, z);
                    }
                }
                if (ax % 12 == 0) {
                    int z = -38 + wobble + 2;
                    put(x, 1, z, "spruce_fence");
                    put(x, 2, z, "lantern[hanging=false]");
                    use(x, z, x, z);
                }
            }
        }
        int gx = ARENA_X - (int) ARENA_R - 2;
        for (int z = -36; z <= ARENA_Z + 1; z++) {
            for (int x = gx - 2; x <= gx; x++) {
                path(x, z);
            }
        }
    }

    private void lake() {
        double rx = 12.5;
        double rz = 9.5;
        for (int x = LAKE_X - 16; x <= LAKE_X + 16; x++) {
            for (int z = LAKE_Z - 13; z <= LAKE_Z + 13; z++) {
                double e = Math.pow((x - LAKE_X) / rx, 2) + Math.pow((z - LAKE_Z) / rz, 2);
                if (e > 1.45) {
                    continue;
                }
                use(x, z, x, z);
                if (e <= 1.0) {
                    int depth = 1 + (int) Math.round((1 - e) * 4);
                    put(x, -depth - 1, z, noise(x, z, 101) < 0.5 ? "sand" : (noise(x, z, 102) < 0.5 ? "gravel" : "clay"));
                    for (int y = -depth; y <= 0; y++) {
                        put(x, y, z, "water");
                    }
                    put(x, 1, z, e > 0.35 && noise(x, z, 103) < 0.07 ? "lily_pad" : "air");
                } else {
                    put(x, 0, z, e < 1.2 ? "sand" : (noise(x, z, 104) < 0.5 ? "sand" : "grass_block"));
                    put(x, -1, z, "sand");
                    put(x, 1, z, e < 1.2 && noise(x, z, 105) < 0.12 ? "sugar_cane" : "air");
                    if (e < 1.2 && noise(x, z, 105) < 0.06) {
                        put(x, 2, z, "sugar_cane");
                    }
                }
            }
        }
        int py = 1;
        for (int x = LAKE_X + 13; x >= LAKE_X + 5; x--) {
            for (int z = LAKE_Z - 1; z <= LAKE_Z + 1; z++) {
                put(x, py - 1, z, "spruce_planks");
                put(x, py, z, Math.abs(z - LAKE_Z) == 1 && Math.floorMod(x, 3) == 0 ? "spruce_fence" : "air");
            }
            put(x, py - 2, LAKE_Z - 1, "spruce_log");
            put(x, py - 2, LAKE_Z + 1, "spruce_log");
        }
        put(LAKE_X + 5, py, LAKE_Z - 1, "spruce_fence");
        put(LAKE_X + 5, py + 1, LAKE_Z - 1, "lantern[hanging=false]");
        put(LAKE_X + 5, py, LAKE_Z + 1, "spruce_fence");
        put(LAKE_X + 5, py + 1, LAKE_Z + 1, "lantern[hanging=false]");
        put(LAKE_X + 6, py, LAKE_Z, "barrel[facing=up]");
        cabin(LAKE_X + 11, LAKE_Z - 15);
        layout.point("holo:lake", LAKE_X + 13.5, 3.2, LAKE_Z + 0.5, 0);
    }

    private void cabin(int x0, int z0) {
        use(x0 - 1, z0 - 1, x0 + 6, z0 + 6);
        for (int x = x0; x <= x0 + 5; x++) {
            for (int z = z0; z <= z0 + 5; z++) {
                boolean wall = x == x0 || x == x0 + 5 || z == z0 || z == z0 + 5;
                boolean corner = (x == x0 || x == x0 + 5) && (z == z0 || z == z0 + 5);
                put(x, 0, z, "spruce_planks");
                for (int y = 1; y <= 3; y++) {
                    put(x, y, z, !wall ? "air" : (corner ? "stripped_spruce_log[axis=y]" : (y == 2 && (x == x0 + 2 || z == z0 + 2) ? "glass_pane" : "spruce_planks")));
                }
            }
        }
        for (int i = 0; i <= 3; i++) {
            for (int x = x0 - 1 + i; x <= x0 + 6 - i; x++) {
                put(x, 4 + i, z0 - 1 + i, stairs("spruce", "south", false));
                put(x, 4 + i, z0 + 6 - i, stairs("spruce", "north", false));
            }
            for (int z = z0 + i; z <= z0 + 5 - i; z++) {
                put(x0 - 1 + i, 4 + i, z, "spruce_planks");
                put(x0 + 6 - i, 4 + i, z, "spruce_planks");
            }
        }
        put(x0 + 2, 1, z0 + 5, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        put(x0 + 2, 2, z0 + 5, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        put(x0 + 1, 1, z0 + 1, "barrel[facing=up]");
        put(x0 + 4, 1, z0 + 1, "crafting_table");
        put(x0 + 4, 2, z0 + 4, "lantern[hanging=false]");
        put(x0 + 4, 1, z0 + 4, "spruce_planks");
    }

    /** A régi (v13) kilátó blokkjai: ezeket bontja le a javítás, mielőtt az újat felépíti. */
    public void lookoutPatch() {
        int x0 = TOWER_X - 2;
        int z0 = TOWER_Z - 2;
        fill(x0, 1, z0, x0 + 4, 22, z0 + 4, "air");
        fill(x0 - 2, 23, z0 - 2, x0 + 6, 25, z0 + 6, "air");
        put(x0 + 2, 0, z0 - 1, "grass_block");
        lookout();
    }

    static final double[] LOOKOUT_HOLO = {TOWER_X - 6.5, 3.8, TOWER_Z + 0.5};
    /** A 8. rejtett ajándék helye: a kilátó tetején, a teraszon. */
    static final int[] LOOKOUT_GIFT = {TOWER_X + 3, 32, TOWER_Z};

    private void lookout() {
        int tx = TOWER_X;
        int tz = TOWER_Z;
        int top = 31;
        double radius = 3.6;
        double deck = 5.3;
        use(tx - 6, tz - 6, tx + 6, tz + 6);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dist(dx, dz);
                int x = tx + dx;
                int z = tz + dz;
                if (d <= 4.6) {
                    for (int y = -4; y <= 0; y++) {
                        put(x, y, z, noise(x, z + y * 7, 120) < 0.3 ? "andesite" : "cobblestone");
                    }
                }
                if (d > radius + 0.4 && d <= 4.9) {
                    put(x, 1, z, stairs("stone_brick", inward(dx, dz), false));
                }
                if (d <= radius + 0.4) {
                    boolean shell = d > radius - 0.9;
                    for (int y = 1; y < top; y++) {
                        if (!shell) {
                            boolean floor = (y == 8 || y == 16 || y == 24) && !(dx == 0 && dz == 2);
                            put(x, y, z, floor ? "spruce_planks" : "air");
                            continue;
                        }
                        double n = noise(x, y * 13 + z, 121);
                        String block = y <= 2 ? (n < 0.35 ? "mossy_cobblestone" : "cobblestone")
                                : (y % 8 == 1 && y > 2 ? "polished_andesite"
                                : (n < 0.12 ? "mossy_stone_bricks" : (n < 0.22 ? "cracked_stone_bricks" : "stone_bricks")));
                        put(x, y, z, block);
                    }
                }
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                    for (int y = 2; y < top - 2; y++) {
                        put(x, y, z, y % 8 == 1 ? "polished_andesite" : "stone_bricks");
                    }
                    put(x, 1, z, "stone_bricks");
                }
                boolean ledge = d > radius + 0.4 && d <= 4.5 && !(Math.abs(dx) == 3 && Math.abs(dz) == 3);
                if (ledge) {
                    put(x, 9, z, slab("stone_brick", true));
                    put(x, 17, z, slab("stone_brick", true));
                    put(x, 25, z, slab("stone_brick", true));
                }
                if (d > radius + 0.4 && d <= 4.5) {
                    put(x, top - 2, z, stairs("stone_brick", outward(dx, dz), true));
                }
                if (d > radius + 0.4 && d <= 4.95) {
                    put(x, top - 1, z, stairs("stone_brick", outward(dx, dz), true));
                }
                if (d <= deck) {
                    boolean edge = dist(dx + 1, dz) > deck || dist(dx - 1, dz) > deck || dist(dx, dz + 1) > deck || dist(dx, dz - 1) > deck;
                    boolean hole = dx == 0 && dz == 2;
                    put(x, top, z, edge || d > radius + 0.4 ? "stone_bricks" : (hole ? "ladder[facing=north]" : "spruce_planks"));
                    for (int y = top + 1; y <= top + 14; y++) {
                        put(x, y, z, "air");
                    }
                    if (edge) {
                        put(x, top + 1, z, "stone_bricks");
                        int seg = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 10));
                        if (seg % 2 == 0) {
                            put(x, top + 2, z, "stone_bricks");
                        } else {
                            put(x, top + 2, z, slab("stone_brick", false));
                        }
                    }
                }
            }
        }
        for (int y = 1; y < top; y++) {
            put(tx, y, tz + 2, "ladder[facing=north]");
        }
        for (int y = 1; y <= 2; y++) {
            put(tx - 3, y, tz, "air");
            put(tx - 4, y, tz, "air");
        }
        put(tx - 3, 1, tz, "spruce_door[facing=east,half=lower,hinge=left,open=false]");
        put(tx - 3, 2, tz, "spruce_door[facing=east,half=upper,hinge=left,open=false]");
        put(tx - 4, 3, tz, "polished_andesite");
        put(tx - 5, 3, tz, stairs("stone_brick", "east", true));
        put(tx - 4, 0, tz, "stone_bricks");
        put(tx - 5, 0, tz, "stone_bricks");
        put(tx - 5, 1, tz, "air");
        for (int s = -1; s <= 1; s += 2) {
            put(tx - 5, 1, tz + s * 2, "polished_andesite");
            put(tx - 5, 2, tz + s * 2, "lantern[hanging=false]");
        }
        int[][] windows = {{0, 1, 4}, {-1, 0, 6}, {0, 1, 12}, {-1, 0, 13}, {1, 0, 12}, {0, -1, 19}, {0, 1, 20}, {-1, 0, 21}, {1, 0, 21}, {0, 1, 27}, {-1, 0, 27}, {1, 0, 27}};
        for (int[] w : windows) {
            int bx = w[0];
            int bz = w[1];
            for (int y = w[2]; y <= w[2] + 1; y++) {
                put(tx + bx * 4, y, tz + bz * 4, "light_blue_stained_glass");
                put(tx + bx * 3, y, tz + bz * 3, "air");
            }
            put(tx + bx * 4, w[2] + 2, tz + bz * 4, "polished_andesite");
            put(tx + bx * 5, w[2] - 1, tz + bz * 5, slab("stone_brick", true));
        }
        put(tx + 2, 1, tz, "lantern[hanging=false]");
        put(tx - 1, 9, tz - 1, "lantern[hanging=false]");
        put(tx + 1, 17, tz - 1, "lantern[hanging=false]");
        put(tx - 1, 25, tz + 1, "lantern[hanging=false]");
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = top + 1; y <= top + 4; y++) {
                put(tx + c[0], y, tz + c[1], "spruce_log");
            }
        }
        cone(tx, top + 5, tz, 4.3, 8, "deepslate_tile", "deepslate_tiles", "spruce_fence");
        put(tx, top + 4, tz, "lantern[hanging=true]");
        int tip = top + 5 + 8 + 1;
        put(tx, tip + 1, tz, "spruce_fence");
        put(tx, tip + 2, tz, "spruce_fence");
        for (int fx = 1; fx <= 3; fx++) {
            put(tx + fx, tip + 2, tz, "light_blue_wool");
            if (fx <= 2) {
                put(tx + fx, tip + 1, tz, "white_wool");
            }
        }
        for (int[] c : new int[][]{{-4, -3}, {4, -3}, {-4, 3}, {4, 3}}) {
            put(tx + c[0], top + 3, tz + c[1], "lantern[hanging=false]");
        }
        layout.point("holo:lookout", LOOKOUT_HOLO[0], LOOKOUT_HOLO[1], LOOKOUT_HOLO[2], 0);
    }

    private void arena() {
        int r = (int) Math.ceil(ARENA_R) + 2;
        for (int x = ARENA_X - r; x <= ARENA_X + r; x++) {
            for (int z = ARENA_Z - r; z <= ARENA_Z + r; z++) {
                double d = dist(x - ARENA_X, z - ARENA_Z);
                if (d > ARENA_R + 1.6) {
                    continue;
                }
                use(x, z, x, z);
                for (int y = 1; y <= 5; y++) {
                    put(x, y, z, "air");
                }
                if (d <= ARENA_R) {
                    double n = noise(x, z, 121);
                    put(x, 0, z, d > ARENA_R - 1 ? "smooth_sandstone" : (n < 0.6 ? "sand" : (n < 0.85 ? "smooth_sandstone" : "red_sand")));
                    put(x, -1, z, "sandstone");
                } else {
                    boolean gate = x < ARENA_X && Math.abs(z - ARENA_Z) <= 1;
                    put(x, 0, z, "stone_bricks");
                    if (!gate) {
                        put(x, 1, z, "stone_bricks");
                        put(x, 2, z, "stone_brick_wall[up=true]");
                    }
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8 + Math.PI / 8;
            int x = ARENA_X + (int) Math.round(Math.cos(a) * (ARENA_R + 1));
            int z = ARENA_Z + (int) Math.round(Math.sin(a) * (ARENA_R + 1));
            put(x, 1, z, "chiseled_stone_bricks");
            put(x, 2, z, "chiseled_stone_bricks");
            put(x, 3, z, "lantern[hanging=false]");
        }
        int gx = ARENA_X - (int) ARENA_R - 2;
        for (int dz = -2; dz <= 2; dz += 4) {
            for (int y = 1; y <= 4; y++) {
                put(gx, y, ARENA_Z + dz, "stone_bricks");
            }
        }
        for (int dz = -2; dz <= 2; dz++) {
            put(gx, 5, ARENA_Z + dz, "stone_brick_slab[type=bottom]");
        }
        put(gx, 4, ARENA_Z, "lantern[hanging=true]");
        layout.point("pvp:center", ARENA_X + 0.5, 1, ARENA_Z + 0.5, 0);
        layout.point("pvp:exit", gx - 2.5, 1, ARENA_Z + 0.5, 90);
        layout.point("holo:pvp", gx + 0.5, 6.4, ARENA_Z + 0.5, 0);
    }

    private void launchPads() {
        pad("bal", -2, -33, -9.5, PLATFORM + 1.2, -2.5);
        pad("jobb", 2, -33, 10.5, PLATFORM + 1.2, -2.5);
        padAt("ter_bal", -2, PLATFORM, -(int) ARC + 3, -6.5, 1.2, -31.5);
        padAt("ter_jobb", 2, PLATFORM, -(int) ARC + 3, 7.5, 1.2, -31.5);
    }

    private void padAt(String id, int x, int y, int z, double tx, double ty, double tz) {
        put(x, y, z, "gold_block");
        put(x, y + 1, z, "light_weighted_pressure_plate");
        layout.point("launch:" + id, x, y + 1, z, 0);
        layout.point("launch:" + id + ":target", tx, ty, tz, 0);
    }

    private void pad(String id, int x, int z, double tx, double ty, double tz) {
        put(x, 0, z, "gold_block");
        put(x, 1, z, "light_weighted_pressure_plate");
        layout.point("launch:" + id, x, 1, z, 0);
        layout.point("launch:" + id + ":target", tx, ty, tz, 0);
    }

    private void parkour() {
        List<int[]> blocks = hu.icemine.core.parkour.CourseGen.generate(new int[]{0, PLATFORM, BACK + 2}, COURSE_LENGTH, 56L);
        for (int[] block : blocks) {
            layout.add("parkour:blocks", block[0], block[1], block[2], 0);
            course.add(block);
        }
        int[] last = blocks.get(blocks.size() - 1);
        layout.point("holo:parkour_finish", last[0] + 0.5, last[1] + 3.2, last[2] + 0.5, 0);
    }

    private void anchors() {
        layout.point("spawn", 0.5, PLATFORM + 1, 3.5, 180);
        layout.point("holo:welcome", 0.5, PLATFORM + 5.5, -13.5, 0);
        layout.point("holo:parkour_start", 0.5, PLATFORM + 2.7, BACK - 1.5, 0);
        layout.point("npc:contacts", 5.5, PLATFORM + 1, BACK - 2.5, 180);
        layout.point("holo:info_rules", -12 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
        layout.point("holo:info_server", 7 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
        layout.point("holo:info_contacts", 12 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
    }
}
