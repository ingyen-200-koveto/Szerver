package hu.icemine.kingdoms;

final class SpawnTown extends Build {

    interface Ground {
        int at(int x, int z);

        String top(int x, int z);
    }

    static final int RADIUS = 30;
    static final int FLAT = 34;
    static final int BLEND = 45;
    static final int SPAWN_Z = 15;

    private static final String[] TURN = {"east", "south", "west", "north"};
    private static final String[] FLAGS = {"blue", "red", "yellow", "white"};
    private static final String[][] AWNINGS = {{"red_wool", "white_wool"}, {"blue_wool", "white_wool"}, {"yellow_wool", "white_wool"}, {"green_wool", "white_wool"}};
    private static final String[][] HOUSES = {
            {"dark_oak", "calcite", "dark_oak_log"},
            {"brick", "calcite", "spruce_log"},
            {"spruce", "white_terracotta", "dark_oak_log"},
            {"deepslate_tile", "calcite", "dark_oak_log"}};
    private static final String[] FLOWERS = {"poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "red_tulip", "white_tulip"};
    private static final String[] POTS = {"potted_poppy", "potted_blue_orchid", "potted_dandelion", "potted_red_tulip"};

    private final Ground ground;

    SpawnTown(Sink sink, int cx, int cy, int cz, Ground ground) {
        super(sink, cx, cy, cz);
        this.ground = ground;
    }

    @Override
    void build() {
        terrain();
        paving();
        fountain();
        flowers();
        wall();
        for (int q = 0; q < 4; q++) {
            gate(q);
            corner(q);
            house(q);
            stall(q);
            details(q);
        }
    }

    private static int rx(int q, int a, int b) {
        switch (q) {
            case 1:
                return -b;
            case 2:
                return -a;
            case 3:
                return b;
            default:
                return a;
        }
    }

    private static int rz(int q, int a, int b) {
        switch (q) {
            case 1:
                return a;
            case 2:
                return -b;
            case 3:
                return -a;
            default:
                return b;
        }
    }

    private static String turn(String dir, int q) {
        for (int i = 0; i < 4; i++) {
            if (TURN[i].equals(dir)) {
                return TURN[(i + q) % 4];
            }
        }
        return dir;
    }

    static String rotate(String data, int q) {
        int open = data.indexOf('[');
        if (q == 0 || open < 0) {
            return data;
        }
        String[] props = data.substring(open + 1, data.length() - 1).split(",");
        StringBuilder out = new StringBuilder(data.substring(0, open + 1));
        for (int i = 0; i < props.length; i++) {
            String[] kv = props[i].split("=");
            String key = kv[0];
            String value = kv[1];
            if (key.equals("facing")) {
                value = turn(value, q);
            } else if (key.equals("axis")) {
                if (q % 2 == 1 && !value.equals("y")) {
                    value = value.equals("x") ? "z" : "x";
                }
            } else if (key.equals("rotation")) {
                value = String.valueOf((Integer.parseInt(value) + 4 * q) % 16);
            } else {
                key = turn(key, q);
            }
            if (i > 0) {
                out.append(',');
            }
            out.append(key).append('=').append(value);
        }
        return out.append(']').toString();
    }

    private void at(int q, int a, int y, int b, String data) {
        put(rx(q, a, b), y, rz(q, a, b), rotate(data, q));
    }

    private String stone(int x, int y, int z) {
        double n = noise(x * 5 + y, z * 3 - y * 7, 610);
        if (n < 0.1) {
            return "mossy_stone_bricks";
        }
        return n < 0.22 ? "cracked_stone_bricks" : "stone_bricks";
    }

    private static String filler(String top) {
        switch (top) {
            case "sand":
                return "sandstone";
            case "red_sand":
                return "red_sandstone";
            case "stone":
                return "stone";
            case "terracotta":
                return "terracotta";
            default:
                return "dirt";
        }
    }

    private void terrain() {
        for (int x = -BLEND; x <= BLEND; x++) {
            for (int z = -BLEND; z <= BLEND; z++) {
                double d = dist(x, z);
                if (d > BLEND) {
                    continue;
                }
                int g = ground.at(x, z) - cy;
                String top = ground.top(x, z);
                String under = filler(top);
                boolean road = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                if (d <= FLAT) {
                    for (int y = Math.min(g, -1); y <= -1; y++) {
                        put(x, y, z, y >= -3 ? under : "stone");
                    }
                    for (int y = 1; y <= Math.max(26, g + 12); y++) {
                        put(x, y, z, "air");
                    }
                    put(x, 0, z, road ? "dirt_path" : top);
                    continue;
                }
                double s = (d - FLAT) / (BLEND - FLAT);
                s = s * s * (3 - 2 * s);
                int target = (int) Math.round(g * s);
                for (int y = g + 1; y < target; y++) {
                    put(x, y, z, under);
                }
                put(x, target, z, road ? "dirt_path" : top);
                for (int y = target + 1; y <= Math.max(g, target) + 12; y++) {
                    put(x, y, z, "air");
                }
            }
        }
    }

    private void paving() {
        for (int x = -29; x <= 29; x++) {
            for (int z = -29; z <= 29; z++) {
                double d = dist(x, z);
                if (d >= 29.5) {
                    continue;
                }
                double n = noise(x, z, 400);
                String block;
                if (n < 0.45) {
                    block = "stone_bricks";
                } else if (n < 0.65) {
                    block = "polished_andesite";
                } else if (n < 0.8) {
                    block = "cracked_stone_bricks";
                } else if (n < 0.92) {
                    block = "andesite";
                } else {
                    block = "mossy_stone_bricks";
                }
                if (Math.abs(d - 10.5) < 0.55 || Math.abs(d - 18.5) < 0.55) {
                    block = "polished_andesite";
                }
                if ((Math.abs(x) <= 2 || Math.abs(z) <= 2) && d > 7.5) {
                    block = Math.floorMod(x + z, 2) == 0 ? "smooth_stone" : "polished_andesite";
                }
                put(x, 0, z, block);
            }
        }
    }

    private void fountain() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double d = dist(x, z);
                if (d > 7.6) {
                    continue;
                }
                if (d > 6.6) {
                    put(x, 0, z, "smooth_stone");
                    continue;
                }
                put(x, -2, z, "stone_bricks");
                if (d > 5.5) {
                    put(x, -1, z, "stone_bricks");
                    put(x, 0, z, "stone_bricks");
                    put(x, 1, z, "stone_brick_slab[type=bottom]");
                } else if (d > 2.6) {
                    put(x, -1, z, "water");
                    put(x, 0, z, "water");
                } else {
                    put(x, -1, z, "stone_bricks");
                    put(x, 0, z, "stone_bricks");
                    put(x, 1, z, "stone_bricks");
                    put(x, 2, z, d <= 1.42 ? "water" : "polished_andesite");
                }
            }
        }
        int[][] posts = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int[] p : posts) {
            put(p[0], 1, p[1], "stone_bricks");
            put(p[0], 2, p[1], "stone_brick_wall[up=true]");
            put(p[0], 3, p[1], "lantern[hanging=false]");
        }
        put(0, 2, 0, "stone_bricks");
        put(0, 3, 0, "stone_brick_wall[up=true]");
        put(0, 4, 0, "stone_brick_wall[up=true]");
        put(0, 5, 0, "chiseled_stone_bricks");
        put(0, 6, 0, "stone_bricks");
        put(1, 6, 0, stairs("stone_brick", "west", true));
        put(-1, 6, 0, stairs("stone_brick", "east", true));
        put(0, 6, 1, stairs("stone_brick", "north", true));
        put(0, 6, -1, stairs("stone_brick", "south", true));
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                put(x, 6, z, "stone_brick_slab[type=top]");
                put(x, 8, z, "gold_block");
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                put(x, 7, z, x == 0 && z == 0 ? "red_wool" : "gold_block");
            }
        }
    }

    private void flowers() {
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = dist(x, z);
                if (d < 7.6 || d > 9.0 || Math.abs(x) <= 2 || Math.abs(z) <= 2) {
                    continue;
                }
                put(x, 0, z, "grass_block");
                put(x, 1, z, FLOWERS[(int) (noise(x, z, 77) * FLOWERS.length)]);
            }
        }
    }

    private void wall() {
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                double d = dist(x, z);
                if (d < 27 || d > 29.9) {
                    continue;
                }
                if ((Math.abs(x) <= 2 && Math.abs(z) > 20) || (Math.abs(z) <= 2 && Math.abs(x) > 20)) {
                    continue;
                }
                if (d > 29) {
                    put(x, 1, z, stairs("stone_brick", inward(x, z), false));
                    continue;
                }
                for (int y = 1; y <= 5; y++) {
                    put(x, y, z, stone(x, y, z));
                }
                double angle = Math.atan2(z, x) + Math.PI;
                if (d >= 28.2 && Math.floorMod((int) Math.floor(angle * 28.6 / 2.0), 2) == 0) {
                    put(x, 6, z, stone(x, 6, z));
                }
            }
        }
        int[] degrees = {22, 30, 60, 68};
        for (int q = 0; q < 4; q++) {
            for (int deg : degrees) {
                double angle = Math.toRadians(deg + 90 * q);
                int x = (int) Math.round(Math.cos(angle) * 27.9);
                int z = (int) Math.round(Math.sin(angle) * 27.9);
                double d = dist(x, z);
                if (d >= 27 && d <= 29) {
                    put(x, 6, z, "lantern[hanging=false]");
                }
            }
        }
    }

    private void gate(int q) {
        String flag = FLAGS[q];
        for (int side = -1; side <= 1; side += 2) {
            for (int a = 3; a <= 7; a++) {
                for (int b = 25; b <= 31; b++) {
                    for (int y = 1; y <= 13; y++) {
                        at(q, side * a, y, b, stone(side * a + 40 * q, y, b));
                    }
                }
            }
            for (int y : new int[]{5, 6, 10, 11}) {
                at(q, side * 5, y, 31, "air");
                at(q, side * 7, y, 28, "air");
                if (y >= 10) {
                    at(q, side * 5, y, 25, "air");
                }
            }
            String toTower = side > 0 ? "west" : "east";
            for (int b = 25; b <= 31; b++) {
                at(q, side * 8, 1, b, stairs("stone_brick", toTower, false));
                at(q, side * 8, 13, b, stairs("stone_brick", toTower, true));
                at(q, side * 2, 13, b, stairs("stone_brick", opposite(toTower), true));
            }
            for (int a = 3; a <= 7; a++) {
                at(q, side * a, 1, 24, stairs("stone_brick", "south", false));
                at(q, side * a, 1, 32, stairs("stone_brick", "north", false));
            }
            for (int a = 2; a <= 8; a++) {
                at(q, side * a, 13, 24, stairs("stone_brick", "south", true));
                at(q, side * a, 13, 32, stairs("stone_brick", "north", true));
                for (int b = 24; b <= 32; b++) {
                    at(q, side * a, 14, b, "stone_bricks");
                    boolean edge = a == 2 || a == 8 || b == 24 || b == 32;
                    if (edge && Math.floorMod(a + b, 2) == 0) {
                        at(q, side * a, 15, b, stone(side * a, 15, b + 40 * q));
                    }
                }
            }
            for (int y = 15; y <= 18; y++) {
                at(q, side * 5, y, 28, "spruce_fence");
            }
            at(q, side * 5, 19, 28, flag + "_banner[rotation=0]");
            at(q, side * 5, 8, 32, flag + "_wall_banner[facing=south]");
            at(q, side * 5, 8, 24, flag + "_wall_banner[facing=north]");
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = 25; b <= 31; b++) {
                for (int y = 1; y <= 4; y++) {
                    at(q, a, y, b, "air");
                }
                if (Math.abs(a) == 2) {
                    at(q, a, 5, b, stairs("stone_brick", a > 0 ? "east" : "west", true));
                } else {
                    at(q, a, 5, b, b == 28 ? "iron_bars[east=true,west=true]" : "air");
                }
                for (int y = 6; y <= 9; y++) {
                    at(q, a, y, b, stone(a + 40 * q, y, b));
                }
            }
            at(q, a, 9, 24, stairs("stone_brick", "south", true));
            at(q, a, 9, 32, stairs("stone_brick", "north", true));
            for (int b = 24; b <= 32; b++) {
                at(q, a, 10, b, "stone_bricks");
            }
            if (Math.floorMod(a, 2) == 0) {
                at(q, a, 11, 24, stone(a, 11, 24 + 40 * q));
                at(q, a, 11, 32, stone(a, 11, 32 + 40 * q));
            }
        }
        for (int a = -2; a <= 2; a++) {
            for (int b = 25; b <= 33; b++) {
                at(q, a, 0, b, Math.floorMod(a + b, 2) == 0 ? "smooth_stone" : "polished_andesite");
            }
        }
        at(q, 0, 5, 25, "lantern[hanging=true]");
        at(q, 0, 5, 31, "lantern[hanging=true]");
        at(q, 0, 8, 24, flag + "_wall_banner[facing=north]");
        at(q, 0, 8, 32, flag + "_wall_banner[facing=south]");
    }

    private void corner(int q) {
        int tx = rx(q, 20, 20);
        int tz = rz(q, 20, 20);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = dist(dx, dz);
                int x = tx + dx;
                int z = tz + dz;
                if (d <= 3.5) {
                    for (int y = 1; y <= 11; y++) {
                        put(x, y, z, stone(x, y, z));
                    }
                    put(x, 12, z, "stone_bricks");
                } else if (d <= 4.6) {
                    put(x, 1, z, stairs("stone_brick", inward(dx, dz), false));
                    put(x, 12, z, stairs("stone_brick", inward(dx, dz), true));
                    double angle = Math.atan2(dz, dx) + Math.PI;
                    if (d > 3.9 && Math.floorMod((int) Math.floor(angle * 4.3 / 1.6), 2) == 0) {
                        put(x, 13, z, stone(x, 13, z));
                    }
                }
            }
        }
        int sx = tx > 0 ? -1 : 1;
        int sz = tz > 0 ? -1 : 1;
        for (int y : new int[]{6, 7}) {
            put(tx + sx * 2, y, tz + sz * 2, "air");
            put(tx - sx * 2, y, tz - sz * 2, "air");
        }
        cone(tx, 13, tz, 3.4, 7, "dark_oak", "dark_oak_planks", "lightning_rod[facing=up]");
    }

    private void house(int q) {
        String[] style = HOUSES[q];
        String roof = style[0];
        String plaster = style[1];
        String frame = style[2];
        String[] front = {"P", "W", "W", "P", "D", "P", "W", "W", "P"};
        String[] back = {"P", "S", "W", "S", "P", "S", "W", "S", "P"};
        String[] gable = {"P", "S", "W", "P", "W", "S", "P"};
        for (int i = 0; i <= 6; i++) {
            for (int j = 0; j <= 8; j++) {
                boolean edge = i == 0 || i == 6 || j == 0 || j == 8;
                h(q, i, 1, j, edge ? (noise(i + 9 * q, j, 620) < 0.3 ? "mossy_cobblestone" : "cobblestone") : "spruce_planks");
            }
        }
        for (int y = 2; y <= 5; y++) {
            for (int j = 0; j <= 8; j++) {
                h(q, 0, y, j, column(front[j], y, plaster, frame, "north=true,south=true"));
                h(q, 6, y, j, column(back[j], y, plaster, frame, "north=true,south=true"));
            }
            for (int i = 1; i <= 5; i++) {
                h(q, i, y, 0, column(gable[i], y, plaster, frame, "east=true,west=true"));
                h(q, i, y, 8, column(gable[i], y, plaster, frame, "east=true,west=true"));
            }
        }
        h(q, 0, 2, 4, "spruce_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
        h(q, 0, 3, 4, "spruce_door[facing=east,half=upper,hinge=left,open=false,powered=false]");
        for (int j = 0; j <= 8; j++) {
            String log = j == 0 || j == 8 ? frame + "[axis=y]" : frame + "[axis=z]";
            h(q, 0, 6, j, log);
            h(q, 6, 6, j, log);
        }
        for (int i = 1; i <= 5; i++) {
            h(q, i, 6, 0, frame + "[axis=x]");
            h(q, i, 6, 8, frame + "[axis=x]");
        }
        for (int k = 0; k <= 3; k++) {
            for (int j = -1; j <= 9; j++) {
                h(q, -1 + k, 6 + k, j, stairs(roof, "east", false));
                h(q, 7 - k, 6 + k, j, stairs(roof, "west", false));
            }
        }
        for (int j = -1; j <= 9; j++) {
            h(q, 3, 10, j, roof + "_slab[type=bottom]");
        }
        for (int k = 1; k <= 3; k++) {
            for (int i = k; i <= 6 - k; i++) {
                String block = k == 2 && i == 3 ? "glass_pane[east=true,west=true]" : plaster;
                h(q, i, 6 + k, 0, block);
                h(q, i, 6 + k, 8, block);
            }
        }
        for (int y = 2; y <= 11; y++) {
            h(q, 5, y, 7, "bricks");
        }
        h(q, 5, 12, 7, "campfire[lit=true,signal_fire=false,facing=north]");
        h(q, -1, 1, 4, stairs("cobblestone", "east", false));
        h(q, -1, 5, 3, "lantern[hanging=true]");
        h(q, -1, 5, 5, "lantern[hanging=true]");
        int[] windows = {1, 2, 6, 7};
        for (int n = 0; n < windows.length; n++) {
            h(q, -1, 2, windows[n], "spruce_slab[type=top]");
            h(q, -1, 3, windows[n], POTS[(n + q) % POTS.length]);
        }
        h(q, 3, 2, 4, "lantern[hanging=false]");
        h(q, 1, 2, 1, "crafting_table");
        h(q, 1, 2, 7, "barrel[facing=up]");
        h(q, 2, 2, 7, "barrel[facing=up]");
        h(q, 4, 2, 1, "spruce_stairs[facing=north,half=bottom]");
    }

    private void h(int q, int i, int y, int j, String data) {
        at(q, 5 + i, y, 10 + j, data);
    }

    private static String column(String type, int y, String plaster, String frame, String pane) {
        switch (type) {
            case "P":
                return frame + "[axis=y]";
            case "W":
                return y == 3 || y == 4 ? "glass_pane[" + pane + "]" : plaster;
            case "D":
                return y >= 4 ? plaster : "air";
            default:
                return plaster;
        }
    }

    private void stall(int q) {
        String[] colors = AWNINGS[q];
        for (int a = 13; a <= 17; a++) {
            for (int b = 4; b <= 8; b++) {
                at(q, a, 0, b, "spruce_planks");
            }
            for (int b = 3; b <= 8; b++) {
                at(q, a, 4, b, Math.floorMod(a, 2) == 0 ? colors[0] : colors[1]);
            }
        }
        int[][] posts = {{13, 4}, {17, 4}, {13, 8}, {17, 8}};
        for (int[] p : posts) {
            for (int y = 1; y <= 3; y++) {
                at(q, p[0], y, p[1], "spruce_fence");
            }
        }
        for (int a = 14; a <= 16; a++) {
            at(q, a, 1, 4, "spruce_slab[type=top]");
            at(q, a, 1, 8, "barrel[facing=up]");
        }
        at(q, 14, 2, 4, q % 2 == 0 ? "pumpkin" : "melon");
        at(q, 16, 2, 4, "barrel[facing=up]");
        at(q, 15, 2, 8, "hay_block[axis=y]");
        at(q, 15, 3, 6, "lantern[hanging=true]");
    }

    private void details(int q) {
        lamp(q, 3, 12, -1);
        lamp(q, -3, 12, 1);
        lamp(q, 3, 22, -1);
        lamp(q, -3, 22, 1);
        for (int a = 9; a <= 11; a++) {
            at(q, a, 1, 5, stairs("spruce", "south", false));
        }
        at(q, 8, 1, 5, "spruce_trapdoor[facing=west,half=bottom,open=true]");
        at(q, 12, 1, 5, "spruce_trapdoor[facing=east,half=bottom,open=true]");
        at(q, 7, 1, 7, "polished_andesite");
        for (int y = 2; y <= 6; y++) {
            at(q, 7, y, 7, "spruce_fence");
        }
        at(q, 7, 7, 7, FLAGS[q] + "_banner[rotation=14]");
        tree(rx(q, 19, 13), rz(q, 19, 13));
    }

    private void lamp(int q, int a, int b, int arm) {
        at(q, a, 1, b, "stone_brick_wall[up=true]");
        at(q, a, 2, b, "spruce_fence");
        at(q, a, 3, b, "spruce_fence");
        at(q, a, 4, b, arm < 0 ? "spruce_fence[west=true]" : "spruce_fence[east=true]");
        at(q, a + arm, 4, b, arm < 0 ? "spruce_fence[east=true]" : "spruce_fence[west=true]");
        at(q, a + arm, 3, b, "lantern[hanging=true]");
    }

    private void tree(int tx, int tz) {
        put(tx, 0, tz, "grass_block");
        for (int y = 1; y <= 5; y++) {
            put(tx, y, tz, "oak_log[axis=y]");
        }
        for (int y = 4; y <= 7; y++) {
            double r = y == 7 ? 1.2 : (y == 6 ? 2.0 : 2.6);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if ((dx != 0 || dz != 0 || y > 5) && dist(dx, dz) <= r && noise(tx + dx, tz + dz + y, 630) > 0.08) {
                        put(tx + dx, y, tz + dz, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }
}
