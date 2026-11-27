package hu.icemine.core.gens;

import java.util.ArrayList;
import java.util.List;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Layout;

public final class GensBuild extends Build {

    public static final int HUB_CLEAR = 238;
    public static final int PLOT_CLEAR = 30;
    static final int HUB_RADIUS = 34;
    static final int PLOT_RADIUS = 25;
    /** A földek és az utak négyzete a sziget közepétől (a 8 föld 12-ig tart, körülötte 2 széles út). */
    static final int FARM = 14;

    private final boolean hub;
    private final int salt;

    public GensBuild(Sink sink, int cx, int cy, int cz, boolean hub, int salt) {
        super(sink, cx, cy, cz);
        this.hub = hub;
        this.salt = salt;
    }

    /** A sziget közepén, a téren; északra (az első föld felé) néz. */
    /** A sziget portálja vissza a közös farmra: {dx, y, dz, 1 = keret / 0 = portál}, a tér északi szélén. */
    public static final int ISLAND_PORTAL_Z = -5;

    public static List<int[]> islandPortal() {
        List<int[]> blocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int y = 0; y <= 5; y++) {
                boolean frame = Math.abs(dx) == 2 || y == 0 || y == 5;
                blocks.add(new int[]{dx, y, ISLAND_PORTAL_Z, frame ? 1 : 0});
            }
        }
        return blocks;
    }

    public static Layout.Point plotSpawn() {
        return new Layout.Point(0.5, 1, 0.5, 180);
    }

    @Override
    public void build() {
        if (hub) {
            hub();
        } else {
            plot();
        }
    }

    private void island(double radius, int depth, int seed) {
        int max = (int) Math.ceil(radius) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + 1.2 * Math.sin(angle * 5 + seed) + 0.7 * Math.sin(angle * 11 + 1.3 + seed);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(cx + dx, cz + dz, seed) < 0.1 ? "packed_ice" : "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.6)) * depth) + 1 + (int) (noise(cx + dx, cz + dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(cx + dx, cz + dz * 3 + l, seed + 10 + l);
                    String block = l == 1 ? "dirt" : (l <= 3 ? (m < 0.6 ? "dirt" : "stone") : (m < 0.4 ? "stone" : (m < 0.7 ? "packed_ice" : (m < 0.88 ? "andesite" : "blue_ice"))));
                    put(dx, -l, dz, block);
                }
                if (noise(cx + dx, cz + dz, seed + 3) < 0.06 && deep > 3) {
                    int length = 2 + (int) (noise(cx + dx, cz + dz, seed + 4) * 5);
                    for (int l = 1; l <= length; l++) {
                        put(dx, -deep - l, dz, l >= length - 1 ? "blue_ice" : "packed_ice");
                    }
                }
            }
        }
    }

    /**
     * A játékos szigete: trópusi sziget homokos parttal. Középen a tér (eladó hordó, kapa üllő, portál a farmra),
     * keletre az automata generátor farm vízcsatornákkal és gyűjtőládákkal, délnyugatra egy nagy pálmafa,
     * északnyugatra egy nagy szikla. A többi füves rész szabad, oda is lehet generátort rakni.
     */
    private void plot() {
        tropicIsland(PLOT_RADIUS, 13, 200 + Math.floorMod(salt, 50));
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
                    put(dx, 0, dz, edge ? "stripped_spruce_wood[axis=y]" : (Math.floorMod(dx + dz, 2) == 0 ? "spruce_planks" : "birch_planks"));
                    put(dx, -1, dz, "dirt");
                }
            }
        }
        for (int dx = 4; dx < FARM_X0; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double n = noise(cx + dx, cz + dz, 410 + salt);
                put(dx, 0, dz, n < 0.15 ? "coarse_dirt" : "dirt_path");
            }
        }
        path(-9, -4, -1, 1);
        path(-1, 1, 4, 11);
        put(FarmLayout.SELL[0], 1, FarmLayout.SELL[1], "barrel[facing=up]");
        put(FarmLayout.SELL[0], 2, FarmLayout.SELL[1], "lantern[hanging=false]");
        put(FarmLayout.HOE[0], 1, FarmLayout.HOE[1], "anvil[facing=east]");
        put(2, 1, 2, "hay_block[axis=y]");
        put(-2, 1, 2, "composter[level=8]");
        for (int[] b : islandPortal()) {
            put(b[0], b[1], b[2], b[3] == 1 ? "crying_obsidian" : "nether_portal[axis=x]");
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            put(3 * sx, 1, 3, "spruce_fence");
            put(3 * sx, 2, 3, "lantern[hanging=false]");
        }
        genFarm();
        palm(-11, 10, 18, 0.9, Math.PI);
        palm(-2, 17, 9, 0.7, Math.PI / 2);
        palm(16, 14, 9, 0.7, 0);
        palm(15, -15, 8, 0.6, -Math.PI / 2);
        rock(-11, -11, 7.6, 15);
        rock(-17, -3, 2.8, 4);
        rock(-4, -16, 2.2, 3);
        decorate();
    }

    /** Az automata farm: {x0, x1} a csatornák hossza, a sorok z-je. A generátorok a csatornák két oldalán, a párnákon állnak. */
    public static final int FARM_X0 = 10;
    public static final int FARM_X1 = 21;
    public static final int CHANNEL_X0 = 12;
    public static final int CHANNEL_X1 = 19;
    public static final int[] CHANNELS = {-3, 3};
    public static final int CHEST_X = 11;

    /** Generátor párna-e (a sziget közepéhez képest): ide rakott generátor az automata farmhoz tartozik. */
    public static boolean farmPad(int dx, int dz) {
        if (dx < CHANNEL_X0 || dx > CHANNEL_X1) {
            return false;
        }
        for (int c : CHANNELS) {
            if (Math.abs(dz - c) == 1) {
                return true;
            }
        }
        return false;
    }

    /** Melyik csatorna felé esik a párna dropja (z irány: -1 / +1), 0 ha nem párna. */
    public static int padFlow(int dx, int dz) {
        if (!farmPad(dx, dz)) {
            return 0;
        }
        for (int c : CHANNELS) {
            if (Math.abs(dz - c) == 1) {
                return c > dz ? 1 : -1;
            }
        }
        return 0;
    }

    /** Az automata farm területe (itt csak generátort lehet lerakni, a párnákra). */
    public static boolean inFarm(int dx, int dz) {
        return dx >= FARM_X0 && dx <= FARM_X1 && Math.abs(dz) <= 6;
    }

    public static boolean farmChest(int dx, int dz) {
        if (dx != CHEST_X) {
            return false;
        }
        for (int c : CHANNELS) {
            if (dz == c) {
                return true;
            }
        }
        return false;
    }

    private void genFarm() {
        for (int dx = FARM_X0; dx <= FARM_X1; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int y = 1; y <= 6; y++) {
                    put(dx, y, dz, "air");
                }
                boolean border = dx == FARM_X0 || dx == FARM_X1 || Math.abs(dz) == 6;
                put(dx, -1, dz, "stone_bricks");
                put(dx, -2, dz, "stone");
                double n = noise(cx + dx, cz + dz, 77);
                String floor = border ? "polished_andesite" : (n < 0.12 ? "mossy_stone_bricks" : (n < 0.2 ? "cracked_stone_bricks" : "stone_bricks"));
                if (farmPad(dx, dz)) {
                    floor = "chiseled_stone_bricks";
                }
                put(dx, 0, dz, floor);
            }
        }
        for (int c : CHANNELS) {
            for (int dx = CHANNEL_X0; dx <= CHANNEL_X1; dx++) {
                int level = CHANNEL_X1 - dx;
                put(dx, 0, c, level == 0 ? "water" : "water[level=" + level + "]");
                put(dx, -1, c, dx == CHANNEL_X0 ? "hopper[facing=down]" : "smooth_stone");
            }
            put(CHEST_X, 0, c, "chest[facing=west]");
            put(CHANNEL_X1 + 1, 0, c, "stone_bricks");
            put(CHANNEL_X1 + 1, 1, c, "stone_brick_stairs[facing=east,half=bottom]");
            put(CHANNEL_X1 + 2, 1, c, "stone_bricks");
            put(CHANNEL_X1 + 2, 2, c, "stone_brick_slab[type=bottom]");
            put(CHANNEL_X1 + 1, 2, c, "lantern[hanging=false]");
        }
        for (int sx : new int[]{FARM_X0, FARM_X1}) {
            for (int sz : new int[]{-6, 6}) {
                put(sx, 1, sz, "stone_brick_wall[up=true]");
                put(sx, 2, sz, "stone_brick_wall[up=true]");
                put(sx, 3, sz, "lantern[hanging=false]");
            }
        }
        put(FARM_X0, 1, -1, "stone_brick_wall[up=true]");
        put(FARM_X0, 2, -1, "lantern[hanging=false]");
        put(FARM_X0, 1, 1, "stone_brick_wall[up=true]");
        put(FARM_X0, 2, 1, "lantern[hanging=false]");
    }

    private void path(int x0, int x1, int z0, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (Math.abs(x) <= 3 && Math.abs(z) <= 3) {
                    continue;
                }
                double n = noise(cx + x, cz + z, 420 + salt);
                put(x, 0, z, n < 0.18 ? "coarse_dirt" : "dirt_path");
            }
        }
    }

    /** Pálmafa: enyhén íves törzs, a tetején lehajló levelek és kakaó. */
    private void palm(int px, int pz, int height, double lean, double angle) {
        int seed = (int) Math.round(angle * 10);
        double ax = Math.cos(angle) * lean;
        double az = Math.sin(angle) * lean;
        int tx = px;
        int tz = pz;
        for (int y = 1; y <= height; y++) {
            double t = (double) y / height;
            int nx = px + (int) Math.round(ax * t * t * 5);
            int nz = pz + (int) Math.round(az * t * t * 5);
            if (nx != tx || nz != tz) {
                put(nx, y - 1, nz, "jungle_log[axis=y]");
            }
            tx = nx;
            tz = nz;
            put(tx, y, tz, y % 3 == 0 ? "jungle_wood[axis=y]" : "jungle_log[axis=y]");
        }
        put(px + 1, 1, pz, "jungle_wood[axis=y]");
        put(px, 1, pz + 1, "jungle_wood[axis=y]");
        int top = height + 1;
        put(tx, top, tz, "jungle_leaves[persistent=true]");
        put(tx, top + 1, tz, "jungle_leaves[persistent=true]");
        int len = height >= 15 ? 9 : (height >= 12 ? 7 : 5);
        for (int f = 0; f < 8; f++) {
            double a = f * Math.PI / 4 + seed * 0.4;
            double ux = Math.cos(a);
            double uz = Math.sin(a);
            for (int i = 1; i <= len; i++) {
                int x = tx + (int) Math.round(ux * i);
                int z = tz + (int) Math.round(uz * i);
                int y = top + (i <= 2 ? 1 : 0) - Math.max(0, i - (len - 2));
                put(x, y, z, "jungle_leaves[persistent=true]");
                if (i >= 2 && i <= len - 2) {
                    put(x + (int) Math.round(-uz), y, z + (int) Math.round(ux), "jungle_leaves[persistent=true]");
                }
                if (i == len) {
                    put(x, y - 1, z, "jungle_leaves[persistent=true]");
                }
            }
        }
        if (height >= 15) {
            put(tx, height + 3, tz, "jungle_leaves[persistent=true]");
            put(tx + 1, top + 1, tz, "jungle_leaves[persistent=true]");
            put(tx - 1, top + 1, tz, "jungle_leaves[persistent=true]");
            put(tx, top + 1, tz + 1, "jungle_leaves[persistent=true]");
            put(tx, top + 1, tz - 1, "jungle_leaves[persistent=true]");
        }
        if (height >= 10) {
            put(tx + 1, height - 1, tz, "cocoa[age=2,facing=west]");
            put(tx - 1, height - 1, tz, "cocoa[age=2,facing=east]");
            put(tx, height - 2, tz + 1, "cocoa[age=2,facing=north]");
        }
    }

    /** Nagy szikla: kő, andezit, tufa, a tetején moha. */
    private void rock(int rx, int rz, double radius, int maxHeight) {
        int r = (int) Math.ceil(radius) + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double a = Math.atan2(dz, dx);
                double edge = radius * (1 + 0.18 * Math.sin(a * 3 + rx) + 0.1 * Math.sin(a * 7 + rz));
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                double t = 1 - d / edge;
                int h = (int) Math.round(maxHeight * Math.pow(t, 0.75) + noise(cx + rx + dx, cz + rz + dz, 91) * 2.2 - 0.4);
                h = Math.max(1, h);
                for (int y = -1; y <= h; y++) {
                    double m = noise(cx + rx + dx * 2 + y, cz + rz + dz + y * 3, 93);
                    String block = m < 0.42 ? "stone" : (m < 0.62 ? "andesite" : (m < 0.78 ? "tuff" : (m < 0.9 ? "cobblestone" : "gravel")));
                    if (y == h) {
                        double top = noise(cx + rx + dx, cz + rz + dz, 95);
                        block = top < 0.45 ? "moss_block" : (top < 0.65 ? "mossy_cobblestone" : block);
                    }
                    put(rx + dx, y, rz + dz, block);
                }
                if (h >= 3 && noise(cx + rx + dx, cz + rz + dz, 97) < 0.25 && d > edge - 1.3) {
                    put(rx + dx, h + 1, rz + dz, "moss_carpet");
                }
            }
        }
    }

    /** Trópusi sziget: fű, a szélén homok, alatta föld és kő. */
    private void tropicIsland(double radius, int depth, int seed) {
        int max = (int) Math.ceil(radius) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + 1.2 * Math.sin(angle * 5 + seed) + 0.7 * Math.sin(angle * 11 + 1.3 + seed);
                double d = dist(dx, dz);
                if (d > edge) {
                    continue;
                }
                boolean beach = d > edge - 2.6 - noise(cx + dx, cz + dz, seed + 5) * 1.2;
                double g = noise(cx + dx, cz + dz, seed);
                put(dx, 0, dz, beach ? "sand" : (g < 0.06 ? "moss_block" : "grass_block"));
                for (int y = 1; y <= 2; y++) {
                    put(dx, y, dz, "air");
                }
                int deep = (int) ((1 - Math.pow(d / edge, 1.6)) * depth) + 1 + (int) (noise(cx + dx, cz + dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(cx + dx, cz + dz * 3 + l, seed + 10 + l);
                    String block = beach && l <= 2 ? "sandstone" : (l <= 2 ? "dirt" : (m < 0.5 ? "stone" : (m < 0.72 ? "dirt" : (m < 0.88 ? "andesite" : "granite"))));
                    put(dx, -l, dz, block);
                }
                if (noise(cx + dx, cz + dz, seed + 3) < 0.05 && deep > 3) {
                    int length = 2 + (int) (noise(cx + dx, cz + dz, seed + 4) * 4);
                    for (int l = 1; l <= length; l++) {
                        put(dx, -deep - l, dz, l == length ? "hanging_roots" : "rooted_dirt");
                    }
                }
            }
        }
    }

    /** Fű és virágok a szabad füves részeken (a tértől, a farmtól, a fáktól és a sziklától távol). */
    private void decorate() {
        int r = PLOT_RADIUS - 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) <= 4 && Math.abs(dz) <= 6 || dx >= 3 && dx <= FARM_X1 + 1 && Math.abs(dz) <= 7
                        || dist(dx + 11, dz + 11) < 9.5 || dist(dx + 17, dz + 3) < 4.5 || dist(dx + 4, dz + 16) < 3.5 || dx >= -9 && dx <= -4 && Math.abs(dz) <= 1 || Math.abs(dx) <= 1 && dz >= 4 && dz <= 11) {
                    continue;
                }
                double n = noise(cx + dx, cz + dz, 830 + salt);
                double edge = PLOT_RADIUS - 3 + 1.2 * Math.sin(Math.atan2(dz, dx) * 5 + 200 + Math.floorMod(salt, 50));
                if (dist(dx, dz) > edge - 2) {
                    continue;
                }
                String plant = n < 0.07 ? "short_grass" : (n < 0.085 ? "poppy" : (n < 0.1 ? "dandelion" : (n < 0.11 ? "azure_bluet" : null)));
                if (plant != null) {
                    put(dx, 1, dz, plant);
                }
            }
        }
    }

    private void well(int wx, int wz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(wx + dx, -1, wz + dz, "stone");
                if (dx == 0 && dz == 0) {
                    put(wx, 0, wz, "water");
                    continue;
                }
                put(wx + dx, 0, wz + dz, "mossy_cobblestone");
                put(wx + dx, 1, wz + dz, Math.abs(dx) + Math.abs(dz) == 2 ? "mossy_cobblestone" : "cobblestone_slab[type=bottom]");
                if (Math.abs(dx) + Math.abs(dz) == 2) {
                    put(wx + dx, 2, wz + dz, "spruce_fence");
                    put(wx + dx, 3, wz + dz, "spruce_fence");
                }
                put(wx + dx, 4, wz + dz, "spruce_slab[type=bottom]");
            }
        }
        put(wx, 4, wz, "spruce_planks");
        put(wx, 3, wz, "chain[axis=y]");
    }

    private void lamp(int x, int z) {
        put(x, 1, z, "stone_brick_wall[up=true]");
        put(x, 2, z, "spruce_fence");
        put(x, 3, z, "spruce_fence");
        put(x, 4, z, "lantern[hanging=false]");
    }

    /** A sziget régi földjeinek helye: itt most fű van, ide lehet generátort rakni. */
    static String islandGround(int x, int z) {
        double n = noise(x, z, 720);
        return n < 0.08 ? "moss_block" : (n < 0.14 ? "coarse_dirt" : "grass_block");
    }

    /**
     * A központ: a közös farm. Középen a tér szélmalommal, eladó hordóval és kapa üllővel, körülötte a 8 nagy föld
     * (ezeket a FieldBuild rakja ki), köztük széles utak lámpákkal, a szélén standok és fenyők.
     */
    /** A központ szigete: lekerekített négyzet, hogy a nagy földek elférjenek (a telkek 48 blokkon túl kezdődnek). */
    private void squareIsland(double half, int depth, int seed) {
        int max = (int) Math.ceil(half) + 2;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double p = 6;
                double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
                double edge = half + 1.0 * Math.sin(Math.atan2(dz, dx) * 7 + seed) + noise(dx, dz, seed + 1) * 0.8;
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(cx + dx, cz + dz, seed) < 0.1 ? "packed_ice" : "snow_block");
                int deep = (int) ((1 - Math.pow(d / edge, 1.6)) * depth) + 1 + (int) (noise(cx + dx, cz + dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(cx + dx, cz + dz * 3 + l, seed + 10 + l);
                    String block = l == 1 ? "dirt" : (l <= 3 ? (m < 0.6 ? "dirt" : "stone") : (m < 0.4 ? "stone" : (m < 0.7 ? "packed_ice" : (m < 0.88 ? "andesite" : "blue_ice"))));
                    put(dx, -l, dz, block);
                }
            }
        }
    }

    /** A központ nagy szigete (a farm és körülötte a város), füves tetővel. */
    static final double TOWN = 224;

    static boolean onIsland(int dx, int dz) {
        double p = 3;
        double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
        return d <= TOWN - 3;
    }

    private void bigIsland(double half, int depth, int seed) {
        int max = (int) Math.ceil(half) + 3;
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double p = 3;
                double d = Math.pow(Math.pow(Math.abs(dx), p) + Math.pow(Math.abs(dz), p), 1 / p);
                double edge = half + 1.4 * Math.sin(Math.atan2(dz, dx) * 9 + seed) + noise(dx, dz, seed + 1) * 0.8;
                if (d > edge) {
                    continue;
                }
                put(dx, 0, dz, noise(dx, dz, seed) < 0.06 ? "moss_block" : "grass_block");
                double t = 1 - Math.pow(d / edge, 1.6);
                int deep = (int) (t * depth) + 1 + (int) (noise(dx, dz, seed + 2) * 3);
                for (int l = 1; l <= deep; l++) {
                    double m = noise(dx, dz * 3 + l, seed + 10 + l);
                    String block = l <= 2 ? "dirt" : (m < 0.45 ? "stone" : (m < 0.7 ? "terracotta" : (m < 0.88 ? "andesite" : "packed_mud")));
                    put(dx, -l, dz, block);
                }
                if (d > edge - 1.2) {
                    put(dx, 1, dz, "oak_fence");
                    for (int y = 2; y <= 5; y++) {
                        put(dx, y, dz, "barrier");
                    }
                }
            }
        }
    }

    /** A tér sugara a központ közepén. */
    static final int PLAZA = 18;

    private void hub() {
        bigIsland(TOWN, 20, 300);
        int e = SharedFarm.EXTENT + 2;
        boolean[][] road = new boolean[e * 2 + 1][e * 2 + 1];
        for (int i = 0; i < SharedFarm.FIELDS; i++) {
            double[] gate = SharedFarm.gate(i, 0.5);
            double len = dist(gate[0], gate[1]);
            for (double t = PLAZA - 1; t <= len + 1; t += 0.5) {
                for (double w = -1.5; w <= 1.5; w += 0.5) {
                    int x = (int) Math.round(gate[0] / len * t - gate[1] / len * w);
                    int z = (int) Math.round(gate[1] / len * t + gate[0] / len * w);
                    if (Math.abs(x) <= e && Math.abs(z) <= e && SharedFarm.fieldAt(x, z) < 0) {
                        road[x + e][z + e] = true;
                    }
                }
            }
        }
        for (int dx = -e; dx <= e; dx++) {
            for (int dz = -e; dz <= e; dz++) {
                double d = dist(dx, dz);
                if (Math.abs(d - 56) < 1.6 && SharedFarm.fieldAt(dx, dz) < 0) {
                    road[dx + e][dz + e] = true;
                }
            }
        }
        for (int dx = -e; dx <= e; dx++) {
            for (int dz = -e; dz <= e; dz++) {
                double d = dist(dx, dz);
                if (d > e) {
                    continue;
                }
                for (int y = 1; y <= 14; y++) {
                    put(dx, y, dz, "air");
                }
                int field = SharedFarm.fieldAt(dx, dz);
                if (field >= 0) {
                    put(dx, -1, dz, "dirt");
                    continue;
                }
                int rim = rimOf(dx, dz);
                if (d <= PLAZA) {
                    boolean edge = d > PLAZA - 1;
                    put(dx, 0, dz, edge ? "prismarine_bricks" : (Math.abs(d - 7) < 0.55 ? "cyan_concrete"
                            : (Math.floorMod(dx + dz, 2) == 0 ? "calcite" : "smooth_quartz")));
                } else if (road[dx + e][dz + e]) {
                    double n = noise(dx, dz, 721);
                    put(dx, 0, dz, n < 0.12 ? "coarse_dirt" : (n < 0.2 ? "gravel" : "dirt_path"));
                } else if (rim >= 0) {
                    put(dx, 0, dz, noise(dx, dz, 725) < 0.5 ? "coarse_dirt" : "rooted_dirt");
                } else {
                    double n = noise(dx, dz, 722);
                    double patch = smooth(dx, dz, 6.0, 723);
                    put(dx, 0, dz, n < 0.05 ? "moss_block" : "grass_block");
                    if (n < 0.02 + 0.05 * patch) {
                        String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet"};
                        put(dx, 1, dz, flowers[(int) (noise(dx, dz, 724) * flowers.length)]);
                    } else if (n < 0.06 + 0.08 * patch) {
                        put(dx, 1, dz, "short_grass");
                    }
                }
            }
        }
        for (int i = 0; i < SharedFarm.FIELDS; i++) {
            double[] gate = SharedFarm.gate(i, 0.5);
            double len = dist(gate[0], gate[1]);
            for (double t = PLAZA + 6; t < len - 3; t += 13) {
                int side = ((int) (t / 13)) % 2 == 0 ? 1 : -1;
                int x = (int) Math.round(gate[0] / len * t - gate[1] / len * 2.6 * side);
                int z = (int) Math.round(gate[1] / len * t + gate[0] / len * 2.6 * side);
                lamp(x, z);
            }
            double between = Math.atan2(SharedFarm.center(i)[1], SharedFarm.center(i)[0]) + Math.PI / SharedFarm.FIELDS;
            int mx = (int) Math.round(Math.cos(between) * 104);
            int mz = (int) Math.round(Math.sin(between) * 104);
            switch (i % 4) {
                case 0 -> grove(mx, mz);
                case 1 -> hayYard(mx, mz);
                case 2 -> pond(mx, mz);
                default -> scarecrowYard(mx, mz);
            }
        }
        windmill();
        put(SharedFarm.SELL[0], 1, SharedFarm.SELL[1], "barrel[facing=up]");
        put(SharedFarm.SELL[0], 2, SharedFarm.SELL[1], "lantern[hanging=false]");
        put(SharedFarm.HOE[0], 1, SharedFarm.HOE[1], "anvil[facing=east]");
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(a) * (PLAZA - 1));
            int z = (int) Math.round(Math.sin(a) * (PLAZA - 1));
            put(x, 1, z, "prismarine_wall[up=true]");
            put(x, 2, z, "sea_lantern");
        }
        stall(12, 2, "lime_wool", "white_wool");
        stall(-12, 2, "cyan_wool", "white_wool");
        portal(PORTAL[0], PORTAL[1]);
        hu.icemine.core.modelobby.GensFarmTown.hubTown((x, y, z, data) -> put(x, y, z, data), 0, 0, 0, GensBuild::onIsland,
                SharedFarm.EXTENT + 4).build();
        new hu.icemine.core.build.Title((x, y, z, data) -> put(x, y, z, data), 0, 36, -56, 2, "south", "GENS", "lime_concrete", "white_concrete",
                4, "green_terracotta", "green_terracotta").build();
        layout.point("spawn", 0.5, 1, 14.5, 180);
    }

    /** Melyik föld mellett van közvetlenül (a föld szegélye, ide kerül a kerítés), vagy -1. */
    private static int rimOf(int x, int z) {
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            int field = SharedFarm.fieldAt(x + n[0], z + n[1]);
            if (field >= 0) {
                return field;
            }
        }
        return -1;
    }

    private void oak(int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            put(x, y, z, "oak_log[axis=y]");
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy * 1.8 + dz * dz) <= 3.1 - noise(x + dx, z + dz + dy, 730) * 0.8) {
                        put(x + dx, height + dy, z + dz, noise(x + dx, dy + z + dz, 731) < 0.25 ? "birch_leaves[persistent=true]" : "oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private void grove(int x, int z) {
        oak(x, z, 6);
        oak(x + 5, z + 3, 5);
        oak(x - 4, z + 4, 7);
        put(x + 2, 1, z - 3, "oak_log[axis=x]");
        put(x + 3, 1, z - 3, "oak_log[axis=x]");
    }

    private void hayYard(int x, int z) {
        int[][] bales = {{0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 2}, {-1, 0, 1}, {0, -1, 1}, {2, 1, 1}};
        for (int[] b : bales) {
            for (int y = 1; y <= b[2]; y++) {
                put(x + b[0], y, z + b[1], "hay_block[axis=y]");
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            put(x + dx + 4, 2, z - 3, "spruce_slab[type=top]");
            put(x + dx + 4, 3, z - 3, dx == 0 ? "hay_block[axis=x]" : "air");
        }
        put(x + 2, 1, z - 4, "dark_oak_trapdoor[facing=north,half=bottom,open=true]");
        put(x + 6, 1, z - 4, "dark_oak_trapdoor[facing=north,half=bottom,open=true]");
        put(x - 2, 1, z + 2, "composter[level=8]");
        put(x - 3, 1, z + 2, "barrel[facing=up]");
    }

    private void pond(int x, int z) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = dist(dx, dz * 1.2);
                if (d <= 4.2) {
                    put(x + dx, 0, z + dz, "water");
                    put(x + dx, -1, z + dz, d < 2.5 ? "water" : "clay");
                    put(x + dx, d < 2.5 ? -2 : -2, z + dz, "clay");
                    if (noise(x + dx, z + dz, 732) < 0.08) {
                        put(x + dx, 1, z + dz, "lily_pad");
                    }
                } else if (d <= 5.2) {
                    put(x + dx, 0, z + dz, noise(x + dx, z + dz, 733) < 0.5 ? "mud" : "grass_block");
                    if (noise(x + dx, z + dz, 734) < 0.2) {
                        put(x + dx, 1, z + dz, "sugar_cane");
                    }
                }
            }
        }
    }

    private void scarecrowYard(int x, int z) {
        put(x, 1, z, "spruce_fence");
        put(x, 2, z, "hay_block[axis=y]");
        put(x, 3, z, "carved_pumpkin[facing=south]");
        put(x - 1, 2, z, "spruce_fence");
        put(x + 1, 2, z, "spruce_fence");
        int[][] pumpkins = {{3, 2}, {4, 1}, {-3, 2}, {2, -3}, {-2, -3}};
        for (int[] p : pumpkins) {
            put(x + p[0], 1, z + p[1], noise(p[0], p[1], 735) < 0.3 ? "melon" : "pumpkin");
        }
        put(x - 4, 1, z - 1, "barrel[facing=up]");
        put(x - 4, 2, z - 1, "lantern[hanging=false]");
    }

    /** Portál a téren: aki belelép, a saját szigetére kerül. */
    static final int[] PORTAL = {0, 0};

    private void portal(int px, int pz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int y = 0; y <= 5; y++) {
                boolean frame = Math.abs(dx) == 2 || y == 0 || y == 5;
                put(px + dx, y, pz, frame ? "crying_obsidian" : "nether_portal[axis=x]");
            }
        }
    }

    /** A központ közepén egy nagy szélmalom: belül üres, a közepén a portál, négy oldalán kapu, a vitorlák délre néznek. */
    private void windmill() {
        double r = 7.4;
        int wall = 21;
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = dist(dx, dz);
                if (d > r + 0.5) {
                    continue;
                }
                put(dx, 0, dz, d > r - 0.6 ? "stone_bricks" : (Math.floorMod(dx + dz, 2) == 0 ? "spruce_planks" : "stripped_spruce_wood[axis=y]"));
                boolean shell = d > r - 1.0;
                for (int y = 1; y <= wall; y++) {
                    double taper = r - y * 0.12;
                    boolean in = d <= taper + 0.5;
                    if (!in) {
                        continue;
                    }
                    if (!(d > taper - 0.9)) {
                        put(dx, y, dz, y == wall ? "spruce_planks" : "air");
                        continue;
                    }
                    String block;
                    if (y <= 4) {
                        block = noise(dx, y + dz, 760) < 0.3 ? "mossy_cobblestone" : "cobblestone";
                    } else if (y % 6 == 5) {
                        block = "stripped_spruce_log[axis=y]";
                    } else {
                        block = noise(dx, y * 3 + dz, 761) < 0.12 ? "calcite" : "smooth_quartz";
                    }
                    boolean window = y % 6 == 2 && y > 6 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);
                    put(dx, y, dz, window ? "light_blue_stained_glass" : block);
                }
            }
        }
        // kapuk a négy oldalon
        for (int[] door : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            for (int s2 = -1; s2 <= 1; s2++) {
                for (int y = 1; y <= 4; y++) {
                    for (int k = 5; k <= 8; k++) {
                        int x = door[0] * k + door[1] * s2;
                        int z = door[1] * k + door[0] * s2;
                        put(x, y, z, "air");
                    }
                }
                int x = door[0] * 7 + door[1] * s2;
                int z = door[1] * 7 + door[0] * s2;
                put(x, 5, z, "stripped_spruce_log[axis=y]");
            }
        }
        for (int[] c : new int[][]{{2, 3}, {-2, 3}, {3, -2}, {-3, -2}}) {
            put(c[0], 3, c[1], "lantern[hanging=false]");
            put(c[0], 1, c[1], "hay_block[axis=y]");
            put(c[0], 2, c[1], "hay_block[axis=y]");
        }
        cone(0, wall + 1, 0, r - wall * 0.12 + 1.5, 7, "dark_oak", "dark_oak_planks", "lightning_rod[facing=up]");
        // vitorlák délre, a tengely a torony oldalán
        int hubY = wall - 2;
        int hubZ = (int) Math.ceil(r - hubY * 0.12) + 1;
        put(0, hubY, hubZ - 1, "stripped_spruce_log[axis=z]");
        put(0, hubY, hubZ, "stripped_spruce_log[axis=z]");
        put(0, hubY, hubZ + 1, "spruce_planks");
        for (int i = 1; i <= 11; i++) {
            put(0, hubY + i, hubZ + 1, "stripped_spruce_log[axis=y]");
            put(i, hubY, hubZ + 1, "stripped_spruce_log[axis=x]");
            if (hubY - i >= 2) {
                put(0, hubY - i, hubZ + 1, "stripped_spruce_log[axis=y]");
            }
            put(-i, hubY, hubZ + 1, "stripped_spruce_log[axis=x]");
            if (i >= 3) {
                for (int w = 1; w <= 2; w++) {
                    put(w, hubY + i, hubZ + 1, "white_wool");
                    put(i, hubY - w, hubZ + 1, "white_wool");
                    if (hubY - i >= 2) {
                        put(-w, hubY - i, hubZ + 1, "white_wool");
                    }
                    put(-i, hubY + w, hubZ + 1, "white_wool");
                }
            }
        }
    }

    private void stall(int sx, int sz, String a, String b) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                put(sx + dx, 0, sz + dz, "spruce_planks");
                put(sx + dx, 4, sz + dz, Math.floorMod(dz, 2) == 0 ? a : b);
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    for (int y = 1; y <= 3; y++) {
                        put(sx + dx, y, sz + dz, "spruce_fence");
                    }
                }
            }
        }
        int dir = sx > 0 ? -1 : 1;
        for (int dz = -1; dz <= 1; dz++) {
            put(sx + 2 * dir, 1, sz + dz, "spruce_slab[type=top]");
            put(sx - 2 * dir, 1, sz + dz, "barrel[facing=up]");
        }
        put(sx, 3, sz, "lantern[hanging=true]");
        put(sx + 2 * dir, 2, sz, "emerald_block");
    }

    private void spruce(int tx, int tz, int height, double width) {
        for (int y = 1; y <= height; y++) {
            put(tx, y, tz, "spruce_log[axis=y]");
        }
        int layers = height - 2;
        for (int i = 0; i < layers; i++) {
            int y = 3 + i;
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
        put(tx, height + 1, tz, "spruce_leaves[persistent=true]");
        put(tx, height + 2, tz, "snow[layers=2]");
    }
}
