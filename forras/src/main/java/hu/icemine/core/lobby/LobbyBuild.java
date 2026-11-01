package hu.icemine.core.lobby;

import hu.icemine.core.build.Build;
import java.util.ArrayList;
import java.util.List;

public final class LobbyBuild extends Build {

    public static final int VERSION = 6;
    public static final int CLEAR = 90;
    public static final int CLEAR_NORTH = 100;
    public static final int CLEAR_SOUTH = 112;

    static final int PLATFORM = 5;
    static final double ARC = 15.5;
    static final int TERRACE = 24;
    static final int BACK = 8;

    private static final int MIN_X = -84;
    private static final int MAX_X = 84;
    private static final int MIN_Z = -100;
    private static final int MAX_Z = 16;

    private static final String[] I = {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"};
    private static final String[] C = {".####", "#....", "#....", "#....", "#....", "#....", ".####"};
    private static final String[] E = {"#####", "#....", "#....", "####.", "#....", "#....", "#####"};
    private static final String[] M = {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"};
    private static final String[] N = {"#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"};

    static final String[][] SLOTS = {
            {"kitpvp", "-20", "red"},
            {"skypvp", "20", "light_blue"},
            {"combofly", "-41", "purple"},
            {"kingdoms", "41", "yellow"},
            {"gens", "-62", "lime"},
            {"duels", "62", "yellow"},
            {"bedwars", "-83", "orange"},
            {"soon_3", "83", "gray"}
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
        stairs();
        roads();
        fountain();
        houses();
        castle();
        letters();
        trees();
        launchPads();
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
        return Math.pow(Math.pow(Math.abs(x) / 78.0, p) + Math.pow(Math.abs(dz) / rz, p), 1.0 / p);
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

    private static int ground(int x, int z) {
        double bluff = (PLATFORM - 1) * smoothstep(-21, -15, z) * (1 - smoothstep(27, 41, Math.abs(x)));
        double r = shape(x, z);
        double ring = smoothstep(0.6, 0.93, r);
        double north = 1 - smoothstep(-26, -8, z);
        double rough = 0.55 + 0.45 * smooth(x, z, 13.0, 5) + 0.25 * smooth(x, z, 5.0, 6);
        double mountain = ring * north * 30 * rough * (1 - castleMask(x, z));
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

    private static boolean bed(int x, int z) {
        double d = dist(x, z);
        return z <= -2 && Math.abs(x) >= 4 && d <= 10.2 && !(Math.abs(x) <= 5 && z > -5);
    }

    private void platform() {
        int r = TERRACE + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -17; z <= BACK + 1; z++) {
                if (!onPlatform(x, z)) {
                    continue;
                }
                use(x, z, x, z);
                int base = Math.max(-2, h(x, z));
                for (int y = base; y < PLATFORM; y++) {
                    double n = noise(x, z + y, 20);
                    put(x, y, z, y >= PLATFORM - 1 ? "stone_bricks" : (n < 0.2 ? "mossy_stone_bricks" : (n < 0.35 ? "cracked_stone_bricks" : "stone_bricks")));
                }
                double d = dist(x, z);
                String floor;
                if (d <= 1.5) {
                    floor = "sea_lantern";
                } else if (d <= 2.6) {
                    floor = "light_blue_concrete";
                } else if (d <= 3.4) {
                    floor = "smooth_stone";
                } else if (Math.abs(x) <= 2 && z < -3) {
                    floor = Math.floorMod(z, 3) == 0 ? "chiseled_stone_bricks" : "stone_bricks";
                } else if (Math.abs(x) <= 1 && z > 3) {
                    floor = Math.floorMod(z, 3) == 0 ? "chiseled_stone_bricks" : "stone_bricks";
                } else if (bed(x, z)) {
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
                } else if (Math.abs(d - 6.9) < 0.5) {
                    floor = "andesite";
                } else {
                    floor = noise(x, z, 23) < 0.15 ? "andesite" : "polished_andesite";
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
        for (int side = -1; side <= 1; side += 2) {
            bush(side * 7, PLATFORM + 1, -7);
        }
        for (int x = -r; x <= r; x++) {
            for (int z = -17; z <= BACK + 1; z++) {
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
            int px = (int) Math.round(Math.sin(angle) * 12);
            int pz = (int) Math.round(-Math.cos(angle) * 12);
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
            int z = -16 - k;
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

    private static final class House {
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

    private void houses() {
        House[] list = {
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
            if ((Math.abs(px) <= 3 && pz >= -51 && pz <= -28) || (pz >= -40 && pz <= -36)) {
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
                    put(x, y, z, y == 1 ? "cobblestone" : (n < 0.18 ? "mossy_stone_bricks" : (n < 0.3 ? "cracked_stone_bricks" : "stone_bricks")));
                }
                boolean outer = Math.abs(x) == 21 || z == -81 || z == -55;
                if (outer && Math.floorMod(x + z, 2) == 0) {
                    put(x, wallTop + 1, z, "stone_bricks");
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
        int[][] trees = {{14, -62}, {-16, -73}, {16, -74}};
        for (int[] tree : trees) {
            oak(tree[0], 1, tree[1], 5 + (int) (noise(tree[0], tree[1], 53) * 2), false);
        }
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
                    String block = window ? (alongX ? "glass_pane[east=true,west=true]" : "glass_pane[north=true,south=true]")
                            : (n < 0.15 ? "mossy_stone_bricks" : (n < 0.27 ? "cracked_stone_bricks" : "stone_bricks"));
                    put(x, y, z, block);
                }
                if (wall && Math.floorMod(x + z, 2) == 0) {
                    put(x, keepTop + 1, z, "stone_bricks");
                }
            }
        }
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
                    double n = noise(tx + dx, y * 13 + tz + dz, 60);
                    put(tx + dx, y, tz + dz, window ? "glass" : (y == 0 ? "cobblestone" : (n < 0.15 ? "mossy_stone_bricks" : (n < 0.26 ? "cracked_stone_bricks" : "stone_bricks"))));
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
        cone(tx, height + 2, tz, radius + 1.0, roofHeight, "deepslate_tile", "deepslate_tiles", "lightning_rod[facing=up]");
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
        int base = 30;
        int front = -72;
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
        for (int gx = MIN_X; gx <= MAX_X; gx += 5) {
            for (int gz = MIN_Z; gz <= MAX_Z; gz += 5) {
                int x = gx + (int) (noise(gx, gz, 80) * 4);
                int z = gz + (int) (noise(gx, gz, 81) * 4);
                int top = h(x, z);
                if (top < 2 || top > 24 || (z > -24 && Math.abs(x) < 32) || !free(x, z, 2)) {
                    continue;
                }
                int steep = Math.max(Math.abs(h(x + 1, z) - top), Math.abs(h(x, z + 1) - top));
                if (steep > 2 || noise(x, z, 82) > 0.62) {
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

    private void launchPads() {
        pad("bal", -2, -33, -4.5, PLATFORM + 1.2, -8.5);
        pad("jobb", 2, -33, 5.5, PLATFORM + 1.2, -8.5);
    }

    private void pad(String id, int x, int z, double tx, double ty, double tz) {
        put(x, 0, z, "gold_block");
        put(x, 1, z, "light_weighted_pressure_plate");
        layout.point("launch:" + id, x, 1, z, 0);
        layout.point("launch:" + id + ":target", tx, ty, tz, 0);
    }

    private void parkour() {
        int[][] jumps = {
                {0, 5, 11, 0, 0}, {3, 5, 11, 0, 0}, {6, 5, 12, 0, 0}, {9, 6, 12, 0, 0}, {11, 6, 14, 0, 0}, {14, 6, 14, 0, 0},
                {17, 7, 13, 0, 0}, {20, 7, 13, 0, 0}, {22, 7, 15, 0, 0}, {25, 8, 15, 0, 0}, {28, 8, 15, 0, 0}, {30, 9, 16, 0, 0},
                {33, 9, 16, 0, 0}, {36, 10, 20, 1, 0}, {35, 11, 25, 0, 1}, {31, 11, 25, 0, 1}, {28, 12, 25, 0, 1}, {24, 12, 26, 0, 1},
                {21, 13, 26, 0, 1}, {17, 12, 27, 0, 1}, {14, 13, 27, 0, 1}, {11, 13, 29, 0, 1}, {7, 13, 29, 0, 1}, {4, 14, 29, 0, 1},
                {0, 14, 28, 0, 1}, {-3, 15, 29, 0, 1}, {-7, 15, 29, 0, 1}, {-10, 15, 31, 0, 1}, {-14, 15, 31, 0, 1}, {-17, 16, 35, 1, 1},
                {-16, 17, 40, 0, 2}, {-12, 17, 40, 0, 2}, {-8, 18, 40, 0, 2}, {-5, 18, 43, 0, 2}, {0, 17, 43, 0, 2}, {3, 18, 44, 0, 2},
                {6, 19, 46, 0, 2}, {10, 19, 46, 0, 2}, {13, 20, 45, 0, 2}, {18, 19, 45, 0, 2}, {20, 20, 48, 0, 2}, {24, 21, 48, 0, 2},
                {28, 21, 48, 0, 2}, {32, 21, 49, 0, 2}, {35, 22, 53, 1, 2}, {34, 23, 58, 0, 3}, {30, 23, 58, 0, 3}, {27, 24, 58, 0, 3},
                {23, 24, 59, 0, 3}, {19, 24, 59, 0, 3}, {16, 25, 61, 0, 3}, {12, 25, 61, 0, 3}, {7, 25, 61, 0, 3}, {4, 26, 61, 0, 3},
                {0, 26, 63, 0, 3}, {-4, 27, 63, 0, 3}, {-8, 27, 63, 0, 3}, {-12, 27, 62, 0, 3}, {-15, 28, 66, 1, 3}, {-14, 29, 71, 0, 4},
                {-10, 30, 71, 0, 4}, {-5, 30, 71, 0, 4}, {-2, 31, 72, 0, 4}, {2, 31, 74, 0, 4}, {5, 32, 76, 0, 4}, {10, 32, 76, 0, 4},
                {14, 33, 76, 0, 4}, {18, 33, 76, 0, 4}, {22, 33, 77, 0, 4}, {26, 34, 77, 0, 4}, {31, 34, 77, 0, 4}, {34, 35, 81, 2, 4}
        };
        String[][] styles = {
                {"mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks"},
                {"spruce_log[axis=y]", "oak_log[axis=y]", "spruce_planks"},
                {"moss_block", "flowering_azalea_leaves[persistent=true]", "azalea_leaves[persistent=true]"},
                {"packed_ice", "blue_ice", "packed_ice"},
                {"smooth_quartz", "light_blue_concrete", "calcite"}
        };
        int index = 0;
        for (int[] jump : jumps) {
            int x = jump[0];
            int y = jump[1];
            int z = jump[2];
            int section = Math.min(styles.length - 1, jump[4]);
            if (jump[3] == 1) {
                islet(x, y, z, false);
                put(x, y + 1, z, "heavy_weighted_pressure_plate");
                layout.add("parkour:checkpoints", x, y + 1, z, 0);
            } else if (jump[3] == 2) {
                islet(x, y, z, true);
                put(x, y + 1, z, "light_weighted_pressure_plate");
                layout.point("parkour:finish", x, y + 1, z, 0);
                layout.point("holo:parkour_finish", x + 0.5, y + 3.6, z + 0.5, 0);
            } else {
                String[] style = styles[section];
                String block = style[(int) (noise(x, z, 90 + index) * style.length)];
                put(x, y, z, block);
                if (section == 1 && block.contains("log")) {
                    put(x, y - 1, z, block);
                    put(x, y - 2, z, block);
                } else if (section == 0) {
                    put(x, y - 1, z, noise(x, z, 91) < 0.5 ? "cobblestone" : "mossy_cobblestone");
                } else if (section == 2) {
                    put(x, y - 1, z, "dirt");
                    put(x, y - 2, z, "hanging_roots");
                } else if (section == 3) {
                    put(x, y - 1, z, "snow_block");
                    put(x, y - 2, z, "ice");
                } else {
                    put(x, y - 1, z, "packed_ice");
                }
            }
            course.add(new int[]{x, y, z});
            index++;
        }
    }

    private void islet(int x, int y, int z, boolean big) {
        double radius = big ? 3.3 : 2.3;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = dist(dx, dz);
                if (d > radius) {
                    continue;
                }
                put(x + dx, y, z + dz, big ? (d < 1.5 ? "smooth_quartz" : (Math.floorMod(dx + dz, 2) == 0 ? "light_blue_concrete" : "calcite")) : "grass_block");
                int depth = (int) ((1 - d / (radius + 0.5)) * (big ? 5 : 4)) + 1;
                for (int l = 1; l <= depth; l++) {
                    put(x + dx, y - l, z + dz, l == 1 ? "dirt" : (noise(x + dx, z + dz + l, 92) < 0.6 ? "stone" : "andesite"));
                }
                if (!big && d > 1.6 && noise(x + dx, z + dz, 93) < 0.3) {
                    put(x + dx, y + 1, z + dz, "short_grass");
                }
            }
        }
        if (big) {
            for (int[] p : new int[][]{{-2, -2}, {2, 2}, {-2, 2}}) {
                put(x + p[0], y + 1, z + p[1], "stone_brick_wall[up=true]");
                put(x + p[0], y + 2, z + p[1], "lantern[hanging=false]");
            }
        } else {
            int side = x > 0 ? 2 : -2;
            put(x + side, y + 1, z, "spruce_fence");
            put(x + side, y + 2, z, "lantern[hanging=false]");
        }
    }

    private void anchors() {
        layout.point("spawn", 0.5, PLATFORM + 1, 0.5, 180);
        layout.point("holo:welcome", 0.5, PLATFORM + 5.5, -13.5, 0);
        layout.point("holo:parkour_start", 0.5, PLATFORM + 2.7, BACK - 1.5, 0);
        layout.point("npc:daily", -4.5, PLATFORM + 1, BACK - 2.5, 180);
        layout.point("npc:contacts", 5.5, PLATFORM + 1, BACK - 2.5, 180);
        layout.point("holo:info_commands", -7 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
        layout.point("holo:info_rules", -12 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
        layout.point("holo:info_server", 7 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
        layout.point("holo:info_contacts", 12 + 0.5, PLATFORM + 3.0, BACK - 1.5, 0);
    }
}
