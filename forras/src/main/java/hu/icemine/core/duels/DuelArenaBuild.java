package hu.icemine.core.duels;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Layout;

/**
 * A Duels arénák. Típus (classic, sumo, bridge, builduhc, crystal) és téma (forest, desert, ice, nether) szerint épül.
 * A játékhoz fontos méretek (a pálya határa, a spawnok, a Bridge kapuk, a leesési magasság) fixek, a dísz körülöttük van.
 * Classic/UHC/Crystal/Sumo: a piros a pálya északi (z-), a kék a déli (z+) oldalán kezd. Bridge: a kék sziget van északon.
 */
public final class DuelArenaBuild extends Build {

    public static final int HALF = 24;
    public static final int WALL = 6;
    public static final int CEILING = 24;
    public static final int DEPTH = 12;
    /** Ennyivel nagyobb területet kell újraépítéskor letakarítani a határnál (a lelátók miatt). */
    public static final int MARGIN = 7;

    private final String type;
    private final String theme;

    public DuelArenaBuild(Build.Sink sink, int x, int y, int z, String type, String theme) {
        super(sink, x, y, z);
        this.type = type == null ? "classic" : type;
        this.theme = theme == null ? "forest" : theme;
    }

    @Override
    public void build() {
        switch (type) {
            case "sumo":
                sumo();
                break;
            case "bridge":
                bridge();
                break;
            case "builduhc":
                uhc();
                break;
            case "crystal":
                crystal();
                break;
            default:
                classic();
        }
    }

    // ---- anyagok témánként ----

    private String floor(int x, int z) {
        double n = noise(x, z, 301);
        double s = smooth(x, z, 6.0, 302);
        switch (theme) {
            case "desert":
                return s > 0.7 ? "cut_sandstone" : (n < 0.6 ? "smooth_sandstone" : (n < 0.85 ? "sandstone" : "chiseled_sandstone"));
            case "ice":
                return s > 0.72 ? "light_blue_concrete_powder" : (n < 0.8 ? "snow_block" : "white_concrete_powder");
            case "nether":
                return s > 0.7 ? "crimson_nylium" : (n < 0.6 ? "nether_bricks" : (n < 0.85 ? "polished_blackstone_bricks" : "cracked_nether_bricks"));
            default:
                return s > 0.72 ? "moss_block" : (n < 0.93 ? "grass_block" : "coarse_dirt");
        }
    }

    private String wall(int x, int y, int z) {
        double n = noise(x + y, z - y, 303);
        switch (theme) {
            case "desert":
                return y >= 5 ? "chiseled_sandstone" : (n < 0.6 ? "sandstone" : "cut_sandstone");
            case "ice":
                return y >= 5 ? "snow_block" : (y <= 1 ? "stone_bricks" : (n < 0.85 ? "packed_ice" : "blue_ice"));
            case "nether":
                return y >= 5 ? "chiseled_polished_blackstone" : (n < 0.6 ? "nether_bricks" : "polished_blackstone_bricks");
            default:
                return y >= 5 ? "mossy_stone_bricks" : (n < 0.6 ? "stone_bricks" : (n < 0.85 ? "mossy_stone_bricks" : "cracked_stone_bricks"));
        }
    }

    private String wallTop() {
        switch (theme) {
            case "desert":
                return "sandstone_wall";
            case "ice":
                return "stone_brick_wall";
            case "nether":
                return "polished_blackstone_wall";
            default:
                return "mossy_stone_brick_wall";
        }
    }

    private String accent() {
        switch (theme) {
            case "desert":
                return "smooth_sandstone";
            case "ice":
                return "packed_ice";
            case "nether":
                return "polished_blackstone";
            default:
                return "polished_andesite";
        }
    }

    private String light() {
        return theme.equals("nether") ? "soul_lantern[hanging=false]" : "lantern[hanging=false]";
    }

    private String seat() {
        switch (theme) {
            case "desert":
                return "sandstone_stairs";
            case "ice":
                return "stone_brick_stairs";
            case "nether":
                return "nether_brick_stairs";
            default:
                return "spruce_stairs";
        }
    }

    private String under(int x, int y, int z) {
        double n = noise(x + y, z + y, 304);
        if (theme.equals("desert")) {
            return n < 0.7 ? "sandstone" : "terracotta";
        }
        if (theme.equals("nether")) {
            return n < 0.7 ? "netherrack" : "blackstone";
        }
        return n < 0.6 ? "stone" : (n < 0.85 ? "andesite" : "dirt");
    }

    // ---- közös elemek: fal, sarok tornyok, csapat zászlók, lelátó ----

    private void box(int half, int ceiling) {
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                boolean edge = Math.abs(x) == half + 1 || Math.abs(z) == half + 1;
                for (int y = -3; y < 0; y++) {
                    put(x, y, z, under(x, y, z));
                }
                if (!edge) {
                    put(x, ceiling, z, "barrier");
                    continue;
                }
                for (int y = 0; y <= WALL - 1; y++) {
                    put(x, y, z, wall(x, y, z));
                }
                put(x, WALL, z, Math.floorMod(x + z, 2) == 0 ? wall(x, 5, z) : wallTop());
                for (int y = WALL + 1; y <= ceiling; y++) {
                    put(x, y, z, "barrier");
                }
            }
        }
        // a fal belső szegélye és csapatszínű csík a két kezdő oldalon
        for (int i = -half; i <= half; i++) {
            put(i, 2, -half - 1, Math.floorMod(i, 4) == 0 ? "red_glazed_terracotta" : wall(i, 2, -half - 1));
            put(i, 2, half + 1, Math.floorMod(i, 4) == 0 ? "blue_glazed_terracotta" : wall(i, 2, half + 1));
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                tower(sx * (half + 1), sz * (half + 1));
            }
        }
        for (int i = -2; i <= 2; i++) {
            int x = i * (half / 3);
            put(x, 4, -half, "red_wall_banner[facing=south]");
            put(x, 4, half, "blue_wall_banner[facing=north]");
        }
        layout.areas.put("bounds", new Layout.Area(-half, -12, -half, half, ceiling - 1, half));
    }

    private void tower(int x, int z) {
        for (int y = 0; y <= WALL + 3; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // a csipkézett tetőn a rések helyén marad a fal barrierje, így ott sem lehet kimászni
                    if (y == WALL + 3 && Math.floorMod(dx + dz, 2) != 0) {
                        continue;
                    }
                    put(x + dx, y, z + dz, wall(x + dx, Math.min(5, y), z + dz));
                }
            }
        }
        put(x, WALL + 4, z, light());
    }

    /** Lelátó a pálya két hosszanti oldalán, a falon kívül (a nézők innen is láthatják a meccset). */
    private void stands(int half) {
        for (int side = -1; side <= 1; side += 2) {
            for (int tier = 0; tier < 4; tier++) {
                int x = side * (half + 2 + tier);
                int seatY = WALL - 2 + tier;
                for (int z = -half + 3; z <= half - 3; z++) {
                    for (int y = 0; y < seatY; y++) {
                        put(x, y, z, wall(x, Math.min(4, y), z));
                    }
                    // a lépcső magas fele kifelé néz, így a néző a pálya felé fordul
                    String facing = side < 0 ? "west" : "east";
                    put(x, seatY, z, Math.floorMod(z, 6) == 0 ? light() : seat() + "[facing=" + facing + ",half=bottom]");
                }
            }
            // korlát a legfelső sor mögött
            int back = side * (half + 6);
            for (int z = -half + 3; z <= half - 3; z++) {
                for (int y = 0; y <= WALL + 2; y++) {
                    put(back, y, z, wall(back, Math.min(5, y), z));
                }
                put(back, WALL + 3, z, Math.floorMod(z, 2) == 0 ? wallTop() : "air");
            }
        }
    }

    private void spawns(double distance) {
        layout.point("spawn:red", 0.5, 1, -distance + 0.5, 0);
        layout.point("spawn:blue", 0.5, 1, distance + 0.5, 180);
    }

    /** A pálya közepén a jel: kör, félig piros, félig kék. */
    private void emblem(int y) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double d = dist(x, z);
                if (d > 5.2) {
                    continue;
                }
                String block;
                if (d < 1.3) {
                    block = theme.equals("nether") ? "gilded_blackstone" : "gold_block";
                } else if (d > 4.2) {
                    block = accent();
                } else if (d > 3.2) {
                    block = z < 0 ? "red_terracotta" : (z > 0 ? "blue_terracotta" : "white_terracotta");
                } else {
                    block = floor(x, z);
                }
                put(x, y, z, block);
            }
        }
    }

    /** Fedezékek: romos oszlopok és alacsony falak témához illő anyagból. */
    private void cover(int half) {
        int[][] ruins = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
        for (int[] r : ruins) {
            int height = 3 + (int) (noise(r[0], r[1], 310) * 2);
            for (int y = 1; y <= height; y++) {
                put(r[0], y, r[1], wall(r[0], Math.min(4, y), r[1]));
            }
            put(r[0], height + 1, r[1], light());
            int[][] rubble = {{1, 0}, {0, 1}, {-1, 1}, {1, -1}};
            for (int[] d : rubble) {
                if (noise(r[0] + d[0], r[1] + d[1], 311) < 0.7) {
                    put(r[0] + d[0], 1, r[1] + d[1], wall(r[0] + d[0], 1, r[1] + d[1]));
                }
            }
            decoration(r[0] + 2, r[1] - 2);
        }
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 16;
            for (int z = -2; z <= 2; z++) {
                put(x, 1, z, wall(x, 1, z));
                if (Math.abs(z) <= 1) {
                    put(x, 2, z, wallTop());
                }
            }
        }
    }

    private void decoration(int x, int z) {
        switch (theme) {
            case "desert":
                put(x, 1, z, "dead_bush");
                break;
            case "ice":
                put(x, 1, z, "packed_ice");
                put(x, 2, z, "ice");
                break;
            case "nether":
                put(x, 1, z, "crimson_roots");
                break;
            default:
                put(x, 1, z, "azalea");
        }
    }

    // ---- típusok ----

    private void classic() {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean border = Math.abs(x) >= HALF - 1 || Math.abs(z) >= HALF - 1;
                put(x, 0, z, border ? accent() : floor(x, z));
            }
        }
        box(HALF, CEILING);
        emblem(0);
        cover(HALF);
        stands(HALF);
        spawns(16);
        spawnPads(16);
    }

    private void spawnPads(int distance) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                put(x, 0, -distance + z, x == 0 && z == 0 ? "red_concrete" : "red_terracotta");
                put(x, 0, distance + z, x == 0 && z == 0 ? "blue_concrete" : "blue_terracotta");
            }
        }
    }

    private void sumo() {
        double radius = 7.5;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double d = dist(x, z);
                if (d > radius) {
                    continue;
                }
                String top;
                if (d > radius - 1.0) {
                    top = "white_concrete";
                } else if (d < 1.5) {
                    top = "red_concrete";
                } else if (d > radius - 2.2) {
                    top = "stripped_bamboo_block[axis=y]";
                } else {
                    top = Math.floorMod(x + z, 2) == 0 ? "bamboo_mosaic" : "bamboo_planks";
                }
                put(x, 0, z, top);
                int depth = 1 + (int) Math.round((1.0 - d / radius) * 7.0);
                for (int y = -depth; y < 0; y++) {
                    put(x, y, z, under(x, y, z));
                }
            }
        }
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double d = dist(x, z);
                if (d > radius + 3.0 && d <= 16.0) {
                    put(x, -6, z, "water");
                    put(x, -7, z, "sand");
                    if (noise(x, z, 312) < 0.03) {
                        put(x, -5, z, "lily_pad");
                    }
                }
                if (d > 15.0 && d <= 16.2) {
                    for (int y = -6; y <= 1; y++) {
                        put(x, y, z, y == 1 ? "dark_oak_slab[type=bottom]" : (y == 0 ? "stripped_dark_oak_log[axis=y]" : "stone_bricks"));
                    }
                    for (int y = 2; y <= 8; y++) {
                        put(x, y, z, "barrier");
                    }
                }
            }
        }
        // négy torii kapu a peremen
        int[][] gates = {{0, -15, 0}, {0, 15, 0}, {-15, 0, 1}, {15, 0, 1}};
        for (int[] g : gates) {
            torii(g[0], g[1], g[2] == 1);
        }
        // cseresznyefák a kapuk között
        int[][] trees = {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}};
        for (int[] t : trees) {
            cherry(t[0], t[1]);
        }
        layout.areas.put("bounds", new Layout.Area(-16, -12, -16, 16, 20, 16));
        layout.point("spawn:red", 0.5, 1, -3.5, 0);
        layout.point("spawn:blue", 0.5, 1, 4.5, 180);
        layout.point("fall", 0, -2, 0, 0);
    }

    private void torii(int x, int z, boolean alongZ) {
        for (int side = -1; side <= 1; side += 2) {
            int px = alongZ ? x : x + side * 2;
            int pz = alongZ ? z + side * 2 : z;
            for (int y = 1; y <= 5; y++) {
                put(px, y, pz, "crimson_stem[axis=y]");
            }
        }
        for (int i = -3; i <= 3; i++) {
            int bx = alongZ ? x : x + i;
            int bz = alongZ ? z + i : z;
            put(bx, 6, bz, "red_concrete");
            if (Math.abs(i) <= 2) {
                put(bx, 4, bz, "crimson_planks");
            }
        }
        put(x, 3, z, "lantern[hanging=true]");
    }

    private void cherry(int x, int z) {
        for (int y = 1; y <= 4; y++) {
            put(x, y, z, "cherry_log[axis=y]");
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 4; y <= 6; y++) {
                    if (dist(dx, dz) + Math.abs(y - 5) * 0.8 <= 2.6) {
                        put(x + dx, y, z + dz, "cherry_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private void island(int center, String team) {
        for (int x = -6; x <= 6; x++) {
            for (int dz = -6; dz <= 6; dz++) {
                int z = center + dz;
                boolean edge = Math.abs(x) == 6 || Math.abs(dz) == 6;
                String top = edge ? team + "_concrete" : (Math.floorMod(x + dz, 2) == 0 ? team + "_terracotta" : "white_terracotta");
                put(x, 0, z, top);
                int depth = edge ? 2 : 4 + (int) (noise(x, z, 306) * 3);
                for (int y = -depth; y < 0; y++) {
                    put(x, y, z, under(x, y, z));
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int px = sx * 6;
                int pz = center + sz * 6;
                put(px, 1, pz, team + "_concrete");
                put(px, 2, pz, "stone_brick_wall");
                put(px, 3, pz, team + "_banner[rotation=" + (center < 0 ? 0 : 8) + "]");
            }
        }
    }

    private void goal(int center, String team) {
        for (int x = -3; x <= 3; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                int z = center + dz;
                boolean hole = Math.abs(x) <= 2 && Math.abs(dz) <= 1;
                if (hole) {
                    put(x, 0, z, "end_portal");
                    put(x, -1, z, "bedrock");
                } else {
                    put(x, 0, z, "end_stone_bricks");
                    put(x, 1, z, team + "_stained_glass");
                }
            }
        }
    }

    private void bridge() {
        island(-27, "blue");
        island(27, "red");
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
                put(x, 0, z, edge ? "white_terracotta" : (dist(x, z) < 1.5 ? "gold_block" : "smooth_quartz"));
                for (int y = -3; y < 0; y++) {
                    put(x, y, z, under(x, y, z));
                }
            }
        }
        for (int z = -21; z <= 21; z++) {
            if (Math.abs(z) > 4) {
                put(0, 0, z, "white_terracotta");
            }
        }
        goal(-31, "blue");
        goal(31, "red");
        for (int side = -1; side <= 1; side += 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                put(sx * 3, 1, side * 27, "stone_brick_wall");
                put(sx * 3, 2, side * 27, "lantern[hanging=false]");
            }
        }
        layout.areas.put("bounds", new Layout.Area(-20, -12, -36, 20, 30, 36));
        layout.areas.put("goal:blue", new Layout.Area(-2, -1, -32, 2, 1, -30));
        layout.areas.put("goal:red", new Layout.Area(-2, -1, 30, 2, 1, 32));
        layout.point("spawn:blue", 0.5, 1, -25.5, 0);
        layout.point("spawn:red", 0.5, 1, 26.5, 180);
        layout.point("fall", 0, -8, 0, 0);
    }

    private void uhc() {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                int top = uhcTop(x, z);
                boolean pond = dist(x - 8, z + 6) < 4.2;
                if (pond) {
                    top = 0;
                }
                for (int y = 0; y <= top; y++) {
                    put(x, y, z, y == top ? (pond ? "water" : "grass_block") : "dirt");
                }
                if (pond) {
                    put(x, -1, z, "sand");
                }
            }
        }
        box(HALF, 28);
        stands(HALF);
        int[][] trees = {{-12, 10}, {13, -12}, {-15, -14}, {16, 12}};
        for (int[] tree : trees) {
            int base = uhcTop(tree[0], tree[1]) + 1;
            for (int y = 0; y < 5; y++) {
                put(tree[0], base + y, tree[1], "oak_log[axis=y]");
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = 3; y <= 5; y++) {
                        if ((dx != 0 || dz != 0 || y == 5) && (Math.abs(dx) != 2 || Math.abs(dz) != 2)) {
                            put(tree[0] + dx, base + y, tree[1] + dz, "oak_leaves[persistent=true]");
                        }
                    }
                }
            }
        }
        layout.point("spawn:red", 0.5, uhcTop(0, -16) + 1, -15.5, 0);
        layout.point("spawn:blue", 0.5, uhcTop(0, 16) + 1, 16.5, 180);
    }

    private int uhcTop(int x, int z) {
        if (dist(x - 8, z + 6) < 4.2) {
            return 0;
        }
        double height = smooth(x, z, 9.0, 307) * 4.0 + smooth(x, z, 4.0, 308) * 1.5;
        return (int) Math.max(0L, Math.round(height - 1.5));
    }

    private void crystal() {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                double n = noise(x, z, 309);
                boolean border = Math.abs(x) >= HALF - 1 || Math.abs(z) >= HALF - 1;
                put(x, 0, z, border ? "crying_obsidian" : (n < 0.12 ? "obsidian" : (n < 0.6 ? "polished_blackstone" : "blackstone")));
                put(x, -1, z, "bedrock");
            }
        }
        box(HALF, CEILING);
        stands(HALF);
        int[][] pillars = {{-12, 0}, {12, 0}, {0, -6}, {0, 6}};
        for (int[] p : pillars) {
            for (int y = 1; y <= 2; y++) {
                put(p[0], y, p[1], "obsidian");
            }
        }
        spawns(14);
        spawnPads(14);
    }
}
