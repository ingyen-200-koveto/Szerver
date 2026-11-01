package hu.icemine.core.fullpvp;

import hu.icemine.core.build.Build;

/**
 * A FullPvP pálya: középen egy kiemelt, kör alakú vár udvar (ez a biztonságos zóna, itt van a bolt, a kitek, a széf,
 * a varázsasztal és az üllő), körülötte egy nagy, dombos rét fákkal, romokkal, őrtornyokkal és egy tóval, a szélén
 * kőfal. A várból négy lépcső vezet le a rétre.
 *
 * Koordináták a középponthoz képest; a talaj teteje 0 és 4 között van, a vár padlója {@link #FLOOR}.
 */
public final class FullPvpBuild extends Build {

    /** A vár udvarának sugara (ezen belül biztonságos zóna van). */
    public static final int SPAWN_RADIUS = 14;
    /** A vár padlójának magassága (a játékos eggyel fölötte áll). */
    public static final int FLOOR = 14;
    /** A pálya sugara a falig. */
    public static final int RADIUS = 72;
    /** Ennyivel a talaj alatt ér véget a pálya (lejjebb a semmi van). */
    public static final int DEPTH = 7;

    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] FACING_IN = {"south", "west", "north", "east"};

    public FullPvpBuild(Sink sink, int cx, int cy, int cz) {
        super(sink, cx, cy, cz);
    }

    @Override
    public void build() {
        terrain();
        paths();
        pond();
        wall();
        towers();
        ruins();
        trees();
        castle();
        ramps();
        stations();
    }

    /** A talaj teteje (0-4) egy oszlopban. A vár körül sík, hogy a lépcsők rendesen leérjenek. */
    static int surface(int x, int z) {
        double r = dist(x, z);
        double hill = smooth(x + 500, z + 500, 18.0, 11) * 3.0 + smooth(x + 500, z + 500, 7.0, 12) * 1.5;
        int natural = (int) Math.round(Math.max(0, Math.min(4, hill)));
        if (r < 30) {
            return 1;
        }
        if (r < 40) {
            double t = (r - 30) / 10.0;
            return (int) Math.round(1 + (natural - 1) * t);
        }
        return natural;
    }

    static boolean onPath(int x, int z) {
        double r = dist(x, z);
        if (r < 27 || r > 66) {
            return false;
        }
        return Math.abs(x) <= 1 || Math.abs(z) <= 1;
    }

    private void terrain() {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double r = dist(x, z);
                if (r > RADIUS + 0.5) {
                    continue;
                }
                int top = surface(x, z);
                for (int y = -DEPTH; y <= top; y++) {
                    String block;
                    if (y == top) {
                        double n = noise(x, z, 21);
                        block = n < 0.86 ? "grass_block" : (n < 0.95 ? "moss_block" : "coarse_dirt");
                    } else if (y >= top - 3) {
                        block = "dirt";
                    } else if (y == -DEPTH) {
                        block = "bedrock";
                    } else {
                        block = noise(x, y + 40, z) < 0.9 ? "stone" : "andesite";
                    }
                    put(x, y, z, block);
                }
                if (r > 28 && r < RADIUS - 2 && !onPath(x, z)) {
                    double n = noise(x, z, 22);
                    if (n < 0.08) {
                        put(x, top + 1, z, "short_grass");
                    } else if (n < 0.095) {
                        put(x, top + 1, z, noise(x, z, 23) < 0.5 ? "poppy" : "dandelion");
                    } else if (n < 0.1) {
                        put(x, top + 1, z, "cornflower");
                    }
                }
            }
        }
    }

    private void paths() {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (onPath(x, z)) {
                    int top = surface(x, z);
                    put(x, top, z, noise(x, z, 24) < 0.8 ? "dirt_path" : "gravel");
                    put(x, top + 1, z, "air");
                }
            }
        }
    }

    private void pond() {
        double angle = Math.toRadians(115);
        int px = (int) Math.round(Math.sin(angle) * 46);
        int pz = (int) Math.round(-Math.cos(angle) * 46);
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double e = (dx * dx) / 49.0 + (dz * dz) / 25.0;
                if (e > 1.15) {
                    continue;
                }
                int x = px + dx;
                int z = pz + dz;
                int top = surface(x, z);
                if (e <= 1.0) {
                    int depth = e < 0.4 ? 3 : 2;
                    for (int y = top - depth; y <= top + 1; y++) {
                        put(x, y, z, "air");
                    }
                    put(x, top - depth - 1, z, e < 0.5 ? "clay" : "sand");
                    for (int y = top - depth; y < top; y++) {
                        put(x, y, z, "water");
                    }
                    if (noise(x, z, 25) < 0.06 && depth == 2) {
                        put(x, top, z, "lily_pad");
                    }
                } else {
                    put(x, top, z, "sand");
                    put(x, top + 1, z, noise(x, z, 26) < 0.3 ? "sugar_cane" : "air");
                }
            }
        }
    }

    private void wall() {
        for (int x = -RADIUS - 2; x <= RADIUS + 2; x++) {
            for (int z = -RADIUS - 2; z <= RADIUS + 2; z++) {
                double r = dist(x, z);
                if (r < RADIUS - 0.5 || r > RADIUS + 0.7) {
                    continue;
                }
                int top = 6;
                for (int y = -DEPTH; y <= top; y++) {
                    double n = noise(x, y, z + 77);
                    put(x, y, z, n < 0.7 ? "stone_bricks" : (n < 0.85 ? "mossy_stone_bricks" : "cracked_stone_bricks"));
                }
                if (Math.floorMod(x + z, 2) == 0) {
                    put(x, top + 1, z, "stone_brick_wall[up=true]");
                }
                for (int y = top + 2; y <= top + 26; y++) {
                    put(x, y, z, "barrier");
                }
            }
        }
    }

    private void towers() {
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(45 + i * 90);
            int tx = (int) Math.round(Math.sin(angle) * 52);
            int tz = (int) Math.round(-Math.cos(angle) * 52);
            tower(tx, tz);
        }
    }

    private void tower(int tx, int tz) {
        int base = surface(tx, tz);
        int height = 9;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = tx + dx;
                int z = tz + dz;
                for (int y = surface(x, z) - 2; y <= base; y++) {
                    put(x, y, z, "cobblestone");
                }
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int y = base + 1; y <= base + height; y++) {
                    if (edge) {
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        String block = corner ? "stripped_spruce_log" : (noise(x, y, z + 5) < 0.75 ? "cobblestone" : "mossy_cobblestone");
                        if (!corner && y == base + 5 && (dx == 0 || dz == 0)) {
                            block = "air";
                        }
                        put(x, y, z, block);
                    } else {
                        put(x, y, z, "air");
                    }
                }
                put(x, base + height + 1, z, "spruce_planks");
            }
        }
        // ajtó a pálya közepe felé
        int sx = tx == 0 ? 0 : -Integer.signum(tx);
        int sz = tz == 0 ? 0 : -Integer.signum(tz);
        int doorX = Math.abs(tx) >= Math.abs(tz) ? tx + sx * 2 : tx;
        int doorZ = Math.abs(tx) >= Math.abs(tz) ? tz : tz + sz * 2;
        put(doorX, base + 1, doorZ, "air");
        put(doorX, base + 2, doorZ, "air");
        // létra a túloldali fal belső oldalán
        int lx = Math.abs(tx) >= Math.abs(tz) ? tx - sx : tx;
        int lz = Math.abs(tx) >= Math.abs(tz) ? tz : tz - sz;
        int wallX = Math.abs(tx) >= Math.abs(tz) ? tx - sx * 2 : tx;
        int wallZ = Math.abs(tx) >= Math.abs(tz) ? tz : tz - sz * 2;
        String facing = wallX > lx ? "west" : wallX < lx ? "east" : (wallZ > lz ? "north" : "south");
        for (int y = base + 1; y <= base + height; y++) {
            put(wallX, y, wallZ, "cobblestone");
            put(lx, y, lz, "ladder[facing=" + facing + "]");
        }
        put(lx, base + height + 1, lz, "spruce_trapdoor[facing=" + facing + ",half=top,open=true]");
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = tx + dx;
                int z = tz + dz;
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    put(x, base + height + 1, z, "spruce_slab[type=top]");
                    put(x, base + height + 2, z, "spruce_fence");
                } else if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    put(x, base + height + 2, z, "lantern[hanging=false]");
                }
            }
        }
    }

    private void ruins() {
        int[] angles = {20, 70, 160, 200, 250, 335};
        for (int i = 0; i < angles.length; i++) {
            double angle = Math.toRadians(angles[i]);
            int rx = (int) Math.round(Math.sin(angle) * (36 + i % 3 * 6));
            int rz = (int) Math.round(-Math.cos(angle) * (36 + i % 3 * 6));
            boolean alongX = i % 2 == 0;
            int length = 5 + i % 3;
            for (int k = -length / 2; k <= length / 2; k++) {
                int x = rx + (alongX ? k : 0);
                int z = rz + (alongX ? 0 : k);
                if (onPath(x, z)) {
                    continue;
                }
                int top = surface(x, z);
                int h = 1 + (int) Math.floor(noise(x, z, 31 + i) * 3);
                for (int y = top + 1; y <= top + h; y++) {
                    double n = noise(x, y, z + 31);
                    put(x, y, z, n < 0.5 ? "mossy_cobblestone" : (n < 0.8 ? "cobblestone" : "cracked_stone_bricks"));
                }
                if (noise(x, z, 33) < 0.3) {
                    put(x, top + h + 1, z, "cobblestone_slab[type=bottom]");
                }
            }
            int px = rx + (alongX ? 0 : 3);
            int pz = rz + (alongX ? 3 : 0);
            if (!onPath(px, pz)) {
                int top = surface(px, pz);
                for (int y = top + 1; y <= top + 4; y++) {
                    put(px, y, pz, y == top + 4 ? "chiseled_stone_bricks" : "stone_bricks");
                }
            }
        }
    }

    private void trees() {
        for (int gx = -66; gx <= 66; gx += 8) {
            for (int gz = -66; gz <= 66; gz += 8) {
                int x = gx + (int) Math.floor(noise(gx, gz, 41) * 5) - 2;
                int z = gz + (int) Math.floor(noise(gx, gz, 42) * 5) - 2;
                double r = dist(x, z);
                if (r < 33 || r > 65 || noise(gx, gz, 43) < 0.35) {
                    continue;
                }
                if (Math.abs(x) <= 4 || Math.abs(z) <= 4 || nearTower(x, z) || nearPond(x, z)) {
                    continue;
                }
                tree(x, z, noise(gx, gz, 44) < 0.65);
            }
        }
    }

    private static boolean nearTower(int x, int z) {
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(45 + i * 90);
            double tx = Math.sin(angle) * 52;
            double tz = -Math.cos(angle) * 52;
            if (dist(x - tx, z - tz) < 7) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearPond(int x, int z) {
        double angle = Math.toRadians(115);
        return dist(x - Math.sin(angle) * 46, z + Math.cos(angle) * 46) < 11;
    }

    private void tree(int x, int z, boolean oak) {
        int base = surface(x, z);
        int height = 4 + (int) Math.floor(noise(x, z, 45) * 3);
        String log = oak ? "oak_log" : "birch_log";
        String leaves = (oak ? "oak_leaves" : "birch_leaves") + "[persistent=true]";
        put(x, base, z, "dirt");
        for (int y = base + 1; y <= base + height; y++) {
            put(x, y, z, log);
        }
        int top = base + height;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy <= -1 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 1) {
                        continue;
                    }
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && noise(x + dx, z + dz, 46 + dy) < 0.6) {
                        continue;
                    }
                    put(x + dx, top + dy, z + dz, leaves);
                }
            }
        }
    }

    private void castle() {
        for (int x = -SPAWN_RADIUS - 1; x <= SPAWN_RADIUS + 1; x++) {
            for (int z = -SPAWN_RADIUS - 1; z <= SPAWN_RADIUS + 1; z++) {
                double r = dist(x, z);
                if (r > SPAWN_RADIUS + 0.5) {
                    continue;
                }
                boolean rim = r > SPAWN_RADIUS - 0.5;
                for (int y = 1; y < FLOOR; y++) {
                    if (rim) {
                        double n = noise(x, y, z + 9);
                        boolean window = y % 5 == 3 && Math.floorMod(x * 3 + z * 7, 5) == 0;
                        put(x, y, z, window ? "iron_bars" : (n < 0.75 ? "stone_bricks" : (n < 0.88 ? "mossy_stone_bricks" : "cracked_stone_bricks")));
                    } else if (y == 1) {
                        put(x, y, z, "stone_bricks");
                    } else {
                        put(x, y, z, "air");
                    }
                }
                String floor;
                if (r <= 3.5) {
                    floor = "polished_diorite";
                } else if (rim) {
                    floor = "chiseled_stone_bricks";
                } else if (Math.abs(r - 9) < 0.6) {
                    floor = "light_blue_glazed_terracotta";
                } else {
                    floor = Math.floorMod(x + z, 2) == 0 ? "polished_andesite" : "stone_bricks";
                }
                put(x, FLOOR, z, floor);
                for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
                    put(x, y, z, "air");
                }
                if (rim && !rampOpening(x, z)) {
                    put(x, FLOOR + 1, z, "stone_brick_wall[up=true]");
                    if (Math.floorMod(x * 5 + z * 3, 9) == 0) {
                        put(x, FLOOR + 2, z, "lantern[hanging=false]");
                    }
                }
            }
        }
        // közép: jég oszlop tengeri lámpással
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            put(0, y, 0, "packed_ice");
        }
        put(0, FLOOR + 4, 0, "sea_lantern");
        for (int[] side : SIDES) {
            put(side[0], FLOOR + 1, side[1], "smooth_quartz_stairs[facing=" + facing(-side[0], -side[1]) + ",half=bottom]");
        }
        layout.point("spawn", 0.5, FLOOR + 1, 9.5, 180);
        layout.point("safe", 0, FLOOR - 1, 0, SPAWN_RADIUS + 1.5);
        layout.point("holo:title", 0.5, FLOOR + 6.2, 0.5, 0);
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

    private static boolean rampOpening(int x, int z) {
        return (Math.abs(x) <= 1 && Math.abs(z) >= SPAWN_RADIUS - 1) || (Math.abs(z) <= 1 && Math.abs(x) >= SPAWN_RADIUS - 1);
    }

    private void ramps() {
        for (int s = 0; s < SIDES.length; s++) {
            int fx = SIDES[s][0];
            int fz = SIDES[s][1];
            int rx = -fz;
            int rz = fx;
            for (int i = 0; i <= FLOOR - 1; i++) {
                int along = SPAWN_RADIUS + 1 + i;
                int y = FLOOR - i;
                for (int w = -1; w <= 1; w++) {
                    int x = fx * along + rx * w;
                    int z = fz * along + rz * w;
                    int ground = surface(x, z);
                    for (int k = Math.min(ground, y - 1); k < y; k++) {
                        put(x, k, z, "stone_bricks");
                    }
                    if (y > ground) {
                        put(x, y, z, "stone_brick_stairs[facing=" + FACING_IN[s] + ",half=bottom]");
                    } else {
                        put(x, ground, z, "stone_bricks");
                    }
                    for (int k = y + 1; k <= y + 3; k++) {
                        put(x, k, z, "air");
                    }
                }
                for (int w = -2; w <= 2; w += 4) {
                    int x = fx * along + rx * w;
                    int z = fz * along + rz * w;
                    int ground = surface(x, z);
                    for (int k = ground; k <= y; k++) {
                        put(x, k, z, "stone_bricks");
                    }
                    if (y > ground) {
                        put(x, y + 1, z, i % 4 == 0 ? "stone_brick_wall[up=true]" : "stone_brick_wall");
                        if (i % 4 == 0) {
                            put(x, y + 2, z, "lantern[hanging=false]");
                        }
                    }
                }
            }
        }
    }

    private void stations() {
        // bolt: smaragdblokk, kitek: láda, széf: enderláda, varázsasztal könyvespolcokkal, üllő
        station("shop", -7, -5, "emerald_block");
        station("kits", 7, -5, "chest[facing=south]");
        station("vault", 0, -10, "ender_chest[facing=south]");
        put(-10, FLOOR + 1, 3, "enchanting_table");
        for (int dz = 1; dz <= 5; dz++) {
            put(-12, FLOOR + 1, dz, "bookshelf");
            put(-12, FLOOR + 2, dz, dz % 2 == 0 ? "bookshelf" : "air");
        }
        layout.point("holo:enchant", -9.5, FLOOR + 2.4, 3.5, 0);
        put(10, FLOOR + 1, 3, "anvil[facing=north]");
        layout.point("holo:anvil", 10.5, FLOOR + 2.4, 3.5, 0);
    }

    private void station(String id, int x, int z, String block) {
        put(x, FLOOR + 1, z, block);
        put(x - 1, FLOOR + 1, z, "polished_andesite_slab[type=bottom]");
        put(x + 1, FLOOR + 1, z, "polished_andesite_slab[type=bottom]");
        layout.point("station:" + id, x, FLOOR + 1, z, 0);
        layout.point("holo:" + id, x + 0.5, FLOOR + 2.6, z + 0.5, 0);
    }
}
