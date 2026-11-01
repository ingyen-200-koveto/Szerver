package hu.icemine.core.bedwars;

import hu.icemine.core.build.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A BedWars pályák. Négy saját tervezésű pálya van, kettő 8 csapatos (Solo és Duó), kettő 4 csapatos (3v3v3v3 és 4v4v4v4).
 *
 * A csapat szigetek mind ugyanúgy épülnek fel, csak a téma (anyagok, díszek) más:
 * a sziget közepétől a pálya közepe felé van az ágy, kifelé a spawn, mellette a bolt és a fejlesztések, leghátul a generátor.
 * Minden fontos pont {@link Spot}-ként kerül ki, ebből tudja az aréna, hova kell teleportálni, hol termel a generátor stb.
 */
public final class BwMap extends Build {

    public static final int LOBBY_Y = 55;
    public static final int SPECTATOR_Y = 26;
    /** A csapat sziget sugara. */
    public static final double ISLAND = 10.5;

    public enum Theme {
        JEGCSUCS("jegcsucs", "&b&lJégcsúcs", 8),
        TOLGYES("tolgyes", "&a&lTölgyes", 8),
        SZIKLAVAR("sziklavar", "&7&lSziklavár", 4),
        HOMOKKO("homokko", "&6&lHomokkő", 4);

        public final String id;
        public final String display;
        public final int teams;

        Theme(String id, String display, int teams) {
            this.id = id;
            this.display = display;
            this.teams = teams;
        }

        public static Theme of(String text) {
            if (text == null) {
                return null;
            }
            String wanted = TeamColor.simple(text).replace(" ", "");
            for (Theme theme : values()) {
                if (theme.id.equals(wanted) || theme.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                    return theme;
                }
            }
            return null;
        }
    }

    public static final class Spot {
        public final String kind;
        /** A csapat sorszáma (0-tól), vagy -1, ha nem csapathoz tartozik. */
        public final int team;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        /** Csak az ágynál: merre néz (north, south, east, west). */
        public final String facing;

        Spot(String kind, int team, double x, double y, double z, float yaw, String facing) {
            this.kind = kind;
            this.team = team;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.facing = facing;
        }
    }

    /** Egy csapat sziget helye és iránya (f = a pálya közepétől kifelé mutató egységvektor). */
    public static final class Slot {
        public final int x;
        public final int z;
        public final int fx;
        public final int fz;

        Slot(int x, int z, int fx, int fz) {
            this.x = x;
            this.z = z;
            this.fx = fx;
            this.fz = fz;
        }

        int rx() {
            return -fz;
        }

        int rz() {
            return fx;
        }

        /** Helyi koordináta (a = kifelé, b = jobbra) a pálya koordinátájára. */
        int[] at(int a, int b) {
            return new int[]{x + fx * a + rx() * b, z + fz * a + rz() * b};
        }
    }

    /**
     * A pálya elrendezésének verziója. 1: az első (közelebbi szigetek), 2: nagyobb távolság a szigetek között,
     * 3: még messzebb egymástól a csapat szigetek és a gyémánt szigetek.
     * Ha változik, a már felépített arénák egyszer újraépülnek (a régi elrendezést blokkra pontosan eltakarítjuk).
     */
    public static final int LAYOUT = 3;

    private final Theme theme;
    private final int layout;
    private final List<Spot> spots = new ArrayList<>();

    public BwMap(Sink sink, int cx, int cy, int cz, Theme theme) {
        this(sink, cx, cy, cz, theme, LAYOUT);
    }

    public BwMap(Sink sink, int cx, int cy, int cz, Theme theme, int layout) {
        super(sink, cx, cy, cz);
        this.theme = theme == null ? Theme.TOLGYES : theme;
        this.layout = layout;
    }

    public Theme theme() {
        return theme;
    }

    public List<Spot> spots() {
        return spots;
    }

    /** A pálya fél szélessége (ekkora négyzeten belül van minden), ezt használja az aréna határnak és a takarításnak. */
    public static int extent(int teams) {
        return extent(teams, LAYOUT);
    }

    public static int extent(int teams, int layout) {
        if (layout <= 1) {
            return teams > 4 ? 80 : 64;
        }
        if (layout == 2) {
            return teams > 4 ? 92 : 76;
        }
        return teams > 4 ? 114 : 96;
    }

    public static List<Slot> slots(int teams) {
        return slots(teams, LAYOUT);
    }

    public static List<Slot> slots(int teams, int layout) {
        List<Slot> result = new ArrayList<>();
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        if (teams > 4) {
            int distance = layout <= 1 ? 60 : (layout == 2 ? 74 : 94);
            int offset = layout <= 1 ? 20 : (layout == 2 ? 24 : 36);
            for (int[] side : sides) {
                int rx = -side[1];
                int rz = side[0];
                result.add(new Slot(side[0] * distance - rx * offset, side[1] * distance - rz * offset, side[0], side[1]));
                result.add(new Slot(side[0] * distance + rx * offset, side[1] * distance + rz * offset, side[0], side[1]));
            }
        } else {
            int distance = layout <= 1 ? 46 : (layout == 2 ? 60 : 80);
            for (int[] side : sides) {
                result.add(new Slot(side[0] * distance, side[1] * distance, side[0], side[1]));
            }
        }
        return result;
    }

    public static int[][] diamonds(int teams) {
        return diamonds(teams, LAYOUT);
    }

    public static int[][] diamonds(int teams, int layout) {
        int d = layout <= 1 ? (teams > 4 ? 40 : 26) : (layout == 2 ? (teams > 4 ? 50 : 34) : (teams > 4 ? 64 : 46));
        return new int[][]{{-d, -d}, {d, -d}, {d, d}, {-d, d}};
    }

    @Override
    public void build() {
        List<Slot> slots = slots(theme.teams, layout);
        for (int i = 0; i < slots.size(); i++) {
            teamIsland(i, slots.get(i));
        }
        for (int[] diamond : diamonds(theme.teams, layout)) {
            diamondIsland(diamond[0], diamond[1]);
        }
        middle();
        waitingRoom();
    }

    // ---- segédek ----

    static float yawToward(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    static String facing(int dx, int dz) {
        if (dx > 0) {
            return "east";
        }
        if (dx < 0) {
            return "west";
        }
        return dz > 0 ? "south" : "north";
    }

    private void spot(String kind, int team, double x, double y, double z, float yaw) {
        spots.add(new Spot(kind, team, x, y, z, yaw, null));
    }

    private String ground(int x, int z) {
        double n = noise(x, z, 501);
        double s = smooth(x, z, 5.0, 502);
        switch (theme) {
            case JEGCSUCS:
                return s > 0.75 ? "packed_ice" : (n < 0.78 ? "snow_block" : (n < 0.92 ? "white_concrete_powder" : "calcite"));
            case SZIKLAVAR:
                return n < 0.5 ? "stone_bricks" : (n < 0.7 ? "cracked_stone_bricks" : (n < 0.85 ? "mossy_stone_bricks" : (n < 0.95 ? "cobblestone" : "andesite")));
            case HOMOKKO:
                return s > 0.72 ? "sand" : (n < 0.55 ? "smooth_sandstone" : (n < 0.82 ? "sandstone" : "cut_sandstone"));
            default:
                return s > 0.74 ? "moss_block" : (n < 0.84 ? "grass_block" : (n < 0.93 ? "coarse_dirt" : "podzol"));
        }
    }

    private String rim() {
        switch (theme) {
            case JEGCSUCS:
                return "packed_ice";
            case SZIKLAVAR:
                return "polished_andesite";
            case HOMOKKO:
                return "cut_sandstone";
            default:
                return "coarse_dirt";
        }
    }

    private String path() {
        switch (theme) {
            case JEGCSUCS:
                return "spruce_planks";
            case SZIKLAVAR:
                return "polished_andesite";
            case HOMOKKO:
                return "smooth_sandstone";
            default:
                return "dirt_path";
        }
    }

    private String wood() {
        switch (theme) {
            case JEGCSUCS:
                return "spruce";
            case SZIKLAVAR:
                return "dark_oak";
            case HOMOKKO:
                return "acacia";
            default:
                return "oak";
        }
    }

    private String light() {
        return "lantern[hanging=false]";
    }

    private String underBlock(int x, int y, int z, int depth) {
        double n = noise(x + y, z - y, 503);
        int fromTop = -y;
        int fromBottom = depth + y;
        switch (theme) {
            case JEGCSUCS:
                if (fromTop <= 2) {
                    return n < 0.6 ? "snow_block" : "packed_ice";
                }
                if (fromBottom <= 1) {
                    return n < 0.5 ? "blue_ice" : "packed_ice";
                }
                return n < 0.45 ? "stone" : (n < 0.75 ? "packed_ice" : (n < 0.93 ? "andesite" : "blue_ice"));
            case SZIKLAVAR:
                if (fromTop <= 2) {
                    return n < 0.6 ? "cobblestone" : "stone";
                }
                if (fromBottom <= 1) {
                    return n < 0.5 ? "deepslate" : "cobbled_deepslate";
                }
                return n < 0.05 ? "iron_ore" : (n < 0.55 ? "stone" : (n < 0.8 ? "andesite" : "tuff"));
            case HOMOKKO:
                if (fromTop <= 2) {
                    return n < 0.7 ? "sandstone" : "sand";
                }
                if (fromBottom <= 1) {
                    return n < 0.5 ? "terracotta" : "orange_terracotta";
                }
                return n < 0.04 ? "gold_ore" : (n < 0.5 ? "sandstone" : (n < 0.75 ? "terracotta" : (n < 0.9 ? "orange_terracotta" : "smooth_sandstone")));
            default:
                if (fromTop <= 2) {
                    return "dirt";
                }
                if (fromBottom <= 1) {
                    return n < 0.6 ? "stone" : "andesite";
                }
                return n < 0.06 ? "coal_ore" : (n < 0.5 ? "stone" : (n < 0.8 ? "dirt" : "tuff"));
        }
    }

    /** A sziget alja: középen mély, a szélén vékony, kicsit szabálytalan. */
    private void underside(int x, int z, double closeness, int maxDepth, int salt, int topY) {
        int depth = 2 + (int) Math.round(closeness * closeness * maxDepth + smooth(cx + x, cz + z, 4.0, salt) * 3.0 * closeness);
        for (int y = -depth; y <= -1; y++) {
            put(x, topY + y, z, underBlock(x, y, z, depth));
        }
    }

    private void flora(int x, int y, int z, int salt) {
        double n = noise(x, z, salt);
        switch (theme) {
            case TOLGYES:
                if (n < 0.12) {
                    put(x, y, z, "short_grass");
                } else if (n < 0.16) {
                    String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium"};
                    put(x, y, z, flowers[(int) (noise(z, x, salt + 1) * flowers.length)]);
                }
                break;
            case JEGCSUCS:
                if (n < 0.12) {
                    put(x, y, z, "snow[layers=1]");
                }
                break;
            case HOMOKKO:
                if (n < 0.03) {
                    put(x, y - 1, z, "sand");
                    put(x, y, z, "dead_bush");
                }
                break;
            default:
                break;
        }
    }

    // ---- díszek ----

    private void oak(int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, "oak_log[axis=y]");
        }
        int top = y + height;
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy >= 0 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if ((!corner || (dy != 1 && noise(x + dx, z + dz + dy, 504) < 0.5)) && (dx != 0 || dz != 0 || dy >= 1)) {
                        put(x + dx, top + dy, z + dz, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private void spruce(int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, "spruce_log[axis=y]");
        }
        for (int i = 2; i <= height + 1; i++) {
            int r = (height + 1 - i) % 2 == 0 ? 1 : 2;
            if (i == height + 1) {
                r = 0;
            }
            if (i >= height - 1) {
                r = Math.min(r, 1);
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + (r > 1 ? 1 : 0) || (dx == 0 && dz == 0 && i < height)) {
                        continue;
                    }
                    put(x + dx, y + i, z + dz, "spruce_leaves[persistent=true]");
                    if (noise(x + dx, z + dz, 505 + i) < 0.5) {
                        put(x + dx, y + i + 1, z + dz, "snow[layers=1]");
                    }
                }
            }
        }
        put(x, y + height + 2, z, "snow[layers=2]");
    }

    private void iceSpike(int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, i == height - 1 ? "blue_ice" : "packed_ice");
            if (i < height / 2) {
                put(x + 1, y + i, z, "packed_ice");
                put(x, y + i, z + 1, "packed_ice");
            }
        }
    }

    private void palm(int x, int y, int z, int height, int lean) {
        int px = x;
        for (int i = 0; i < height; i++) {
            if (i == height / 2) {
                px += lean;
            }
            put(px, y + i, z, "jungle_log[axis=y]");
        }
        int top = y + height;
        put(px, top, z, "jungle_leaves[persistent=true]");
        int[][] fronds = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] f : fronds) {
            for (int i = 1; i <= 3; i++) {
                put(px + f[0] * i, top - (i == 3 ? 1 : 0), z + f[1] * i, "jungle_leaves[persistent=true]");
            }
        }
        put(px + 1, top - 1, z, "cocoa[age=2,facing=west]");
    }

    /** Homokkő oszlop egy kis száraz bokorral (kaktuszt nem rakunk, mert ha valaki mellé épít, kiesik a helyéről). */
    private void pillar(int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, i == height - 1 ? "chiseled_sandstone" : "cut_sandstone");
        }
        put(x, y + height, z, "sandstone_slab[type=bottom]");
        put(x + 1, y - 1, z, "sand");
        put(x + 1, y, z, "dead_bush");
    }

    private void tower(int x, int y, int z, String top) {
        for (int i = 0; i < 4; i++) {
            put(x, y + i, z, i == 3 ? top : "stone_brick_wall");
        }
        put(x, y + 4, z, light());
    }

    private void cornerDecoration(int x, int z, String team) {
        int h = 4 + (int) (noise(x, z, 506) * 3);
        switch (theme) {
            case JEGCSUCS:
                if (noise(x, z, 507) < 0.5) {
                    spruce(x, 1, z, h + 1);
                } else {
                    iceSpike(x, 1, z, h);
                }
                break;
            case SZIKLAVAR:
                tower(x, 1, z, team + "_concrete");
                break;
            case HOMOKKO:
                if (noise(x, z, 507) < 0.7) {
                    palm(x, 1, z, h + 1, noise(x, z, 508) < 0.5 ? 1 : -1);
                } else {
                    pillar(x, 1, z, 3);
                }
                break;
            default:
                oak(x, 1, z, h);
        }
    }

    // ---- csapat sziget ----

    private void teamIsland(int team, Slot slot) {
        TeamColor color = TeamColor.values()[team];
        String c = color.block;
        double rimFrom = ISLAND - 1.1;
        List<int[]> free = new ArrayList<>();
        for (int a = -11; a <= 11; a++) {
            for (int b = -11; b <= 11; b++) {
                double d = Math.cbrt(Math.pow(Math.abs(a), 3) + Math.pow(Math.abs(b), 3));
                if (d > ISLAND) {
                    continue;
                }
                int[] p = slot.at(a, b);
                String top;
                boolean nest = a >= -8 && a <= -3 && Math.abs(b) <= 2;
                if (d > rimFrom) {
                    top = c + "_terracotta";
                } else if (nest) {
                    boolean border = a == -8 || a == -3 || Math.abs(b) == 2;
                    top = border ? wood() + "_planks" : c + (theme == Theme.SZIKLAVAR ? "_concrete" : "_wool");
                } else if (Math.abs(b) <= 1 && a > -3 && a < 7) {
                    top = path();
                } else {
                    top = ground(p[0], p[1]);
                    if (d < rimFrom - 1.0) {
                        free.add(p);
                    }
                }
                put(p[0], 0, p[1], top);
                underside(p[0], p[1], 1.0 - d / (ISLAND + 0.6), 9, 509, 0);
            }
        }
        // nyak a pálya közepe felé, itt kezdődnek a hidak
        for (int a = -15; a <= -11; a++) {
            for (int b = -1; b <= 1; b++) {
                int[] p = slot.at(a, b);
                put(p[0], 0, p[1], Math.abs(b) == 1 ? c + "_terracotta" : path());
                for (int y = -2; y <= -1; y++) {
                    put(p[0], y, p[1], underBlock(p[0], y, p[1], 2));
                }
            }
        }

        // ágy: a láb a sziget felé, a fej a pálya közepe felé
        int[] foot = slot.at(-5, 0);
        int[] head = slot.at(-6, 0);
        String bedFacing = facing(-slot.fx, -slot.fz);
        put(foot[0], 1, foot[1], c + "_bed[part=foot,facing=" + bedFacing + "]");
        put(head[0], 1, head[1], c + "_bed[part=head,facing=" + bedFacing + "]");
        spots.add(new Spot("bed", team, head[0], 1, head[1], 0, bedFacing));
        spots.add(new Spot("bedfoot", team, foot[0], 1, foot[1], 0, bedFacing));
        for (int side = -1; side <= 1; side += 2) {
            int[] post = slot.at(-6, side * 3);
            put(post[0], 1, post[1], theme == Theme.SZIKLAVAR ? "chiseled_stone_bricks" : wood() + "_log[axis=y]");
            put(post[0], 2, post[1], wood() + "_fence");
            put(post[0], 3, post[1], light());
        }

        int[] spawn = slot.at(2, 0);
        spot("spawn", team, spawn[0] + 0.5, 1, spawn[1] + 0.5, yawToward(-slot.fx, -slot.fz));
        int[] base = slot.at(0, 0);
        spot("base", team, base[0] + 0.5, 1, base[1] + 0.5, 0);

        // generátor hátul
        int[] gen = slot.at(8, 0);
        for (int a = 7; a <= 9; a++) {
            for (int b = -1; b <= 1; b++) {
                int[] p = slot.at(a, b);
                boolean center = a == 8 && b == 0;
                put(p[0], 0, p[1], center ? "iron_block" : (Math.abs(b) == 1 && a == 8 ? "gold_block" : (theme == Theme.HOMOKKO ? "smooth_sandstone" : "smooth_stone")));
            }
        }
        spot("generator", team, gen[0] + 0.5, 1, gen[1] + 0.5, 0);

        // bolt (bal) és fejlesztések (jobb)
        booth(slot, 3, -6, team, wood());
        booth(slot, 3, 6, team, "dark_oak".equals(wood()) ? "spruce" : "dark_oak");
        int[] shop = slot.at(3, -6);
        int[] upgrade = slot.at(3, 6);
        spot("shop", team, shop[0] + 0.5, 1, shop[1] + 0.5, yawToward(slot.rx(), slot.rz()));
        spot("upgrade", team, upgrade[0] + 0.5, 1, upgrade[1] + 0.5, yawToward(-slot.rx(), -slot.rz()));

        // díszek a hátsó sarkokban és zászló
        for (int side = -1; side <= 1; side += 2) {
            int[] corner = slot.at(7, side * 7);
            cornerDecoration(corner[0], corner[1], c);
        }
        int[] flag = slot.at(-2, 4);
        for (int y = 1; y <= 4; y++) {
            put(flag[0], y, flag[1], wood() + "_fence");
        }
        put(flag[0], 5, flag[1], c + "_wool");
        int[] flag2 = slot.at(-2, 5);
        put(flag2[0], 5, flag2[1], c + "_wool");
        put(flag2[0], 4, flag2[1], c + "_wool");

        int[][] busy = {slot.at(2, 0), slot.at(8, 0), shop, upgrade, slot.at(7, 7), slot.at(7, -7), flag, slot.at(-6, 3), slot.at(-6, -3)};
        for (int[] p : free) {
            boolean ok = true;
            for (int[] q : busy) {
                if (Math.abs(p[0] - q[0]) <= 2 && Math.abs(p[1] - q[1]) <= 2) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                flora(p[0], 1, p[1], 510);
            }
        }
    }

    /** Kis bódé az NPC-nek: padló, két oszlop hátul, tető. A felirat a tető alatt látszik. */
    private void booth(Slot slot, int a, int b, int team, String wood) {
        int out = b > 0 ? 1 : -1;
        for (int da = -1; da <= 1; da++) {
            for (int db = -1; db <= 1; db++) {
                int[] p = slot.at(a + da, b + db);
                put(p[0], 0, p[1], wood + "_planks");
                put(p[0], 4, p[1], wood + "_slab[type=bottom]");
            }
        }
        for (int da = -1; da <= 1; da += 2) {
            int[] p = slot.at(a + da, b + out);
            for (int y = 1; y <= 3; y++) {
                put(p[0], y, p[1], wood + "_fence");
            }
        }
        int[] back = slot.at(a, b + out);
        put(back[0], 3, back[1], TeamColor.values()[team].block + "_wool");
    }

    // ---- gyémánt szigetek ----

    private void diamondIsland(int x, int z) {
        double radius = 5.2;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = dist(dx, dz);
                if (d > radius) {
                    continue;
                }
                put(x + dx, 0, z + dz, d > radius - 1.0 ? rim() : ground(x + dx, z + dz));
                underside(x + dx, z + dz, 1.0 - d / (radius + 0.6), 6, 511, 0);
                if (d > 2.2 && d < radius - 1.0) {
                    flora(x + dx, 1, z + dz, 512);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(x + dx, 0, z + dz, dx == 0 && dz == 0 ? "diamond_block" : (theme == Theme.HOMOKKO ? "cut_sandstone" : "polished_andesite"));
            }
        }
        spot("diamond", -1, x + 0.5, 1, z + 0.5, 0);
        int ox = x + (x > 0 ? 3 : -3);
        int oz = z + (z > 0 ? 3 : -3);
        switch (theme) {
            case JEGCSUCS:
                iceSpike(ox, 1, oz, 4);
                break;
            case SZIKLAVAR:
                for (int y = 1; y <= 2; y++) {
                    put(ox, y, oz, "mossy_stone_bricks");
                }
                put(ox, 3, oz, "stone_brick_slab[type=bottom]");
                break;
            case HOMOKKO:
                pillar(ox, 1, oz, 2);
                break;
            default:
                put(ox, 1, oz, "oak_leaves[persistent=true]");
                put(ox + 1, 1, oz, "oak_leaves[persistent=true]");
                put(ox, 2, oz, "oak_leaves[persistent=true]");
        }
    }

    // ---- közép ----

    private void middle() {
        boolean big = theme.teams > 4;
        double radius = big ? 15.5 : 13.0;
        int top = 2;
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = dist(x, z);
                if (d > radius) {
                    continue;
                }
                String block;
                if (d > radius - 1.1) {
                    block = rim();
                } else if (d < 3.5) {
                    block = centerFloor(x, z);
                } else {
                    block = ground(x, z);
                }
                put(x, top, z, block);
                put(x, top - 1, z, underBlock(x, -1, z, 4));
                put(x, top - 2, z, underBlock(x, -2, z, 4));
                underside(x, z, 1.0 - d / (radius + 0.6), 14, 513, top - 2);
            }
        }
        List<int[]> emeralds = new ArrayList<>();
        emeralds.add(new int[]{-7, 0});
        emeralds.add(new int[]{7, 0});
        if (big) {
            emeralds.add(new int[]{0, -7});
            emeralds.add(new int[]{0, 7});
        }
        for (int[] e : emeralds) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(e[0] + dx, top, e[1] + dz, dx == 0 && dz == 0 ? "emerald_block" : (theme == Theme.HOMOKKO ? "cut_sandstone" : "polished_andesite"));
                }
            }
            spot("emerald", -1, e[0] + 0.5, top + 1, e[1] + 0.5, 0);
        }
        // lámpák a peremen
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i + Math.PI / 8;
            int x = (int) Math.round(Math.cos(angle) * (radius - 3));
            int z = (int) Math.round(Math.sin(angle) * (radius - 3));
            put(x, top + 1, z, theme == Theme.SZIKLAVAR ? "stone_brick_wall" : wood() + "_fence");
            put(x, top + 2, z, light());
        }
        centerpiece(top + 1);
    }

    private String centerFloor(int x, int z) {
        switch (theme) {
            case JEGCSUCS:
                return "blue_ice";
            case SZIKLAVAR:
                return "chiseled_stone_bricks";
            case HOMOKKO:
                return "chiseled_sandstone";
            default:
                return "moss_block";
        }
    }

    private void centerpiece(int y) {
        switch (theme) {
            case JEGCSUCS:
                // jégtorony: egymásra rakott, felfelé szűkülő korongok
                for (int h = 0; h < 13; h++) {
                    double radius = 2.6 * (1.0 - h / 14.0) + 0.4;
                    int r = (int) Math.ceil(radius);
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            double d = dist(dx, dz);
                            if (d <= radius) {
                                put(dx, y + h, dz, d < radius - 1.0 ? "blue_ice" : "packed_ice");
                            }
                        }
                    }
                }
                put(0, y + 13, 0, "sea_lantern");
                int[][] spikes = {{-4, -4}, {4, -4}, {4, 4}, {-4, 4}};
                for (int i = 0; i < spikes.length; i++) {
                    iceSpike(spikes[i][0], y, spikes[i][1], 3 + i % 2);
                }
                break;
            case SZIKLAVAR:
                for (int h = 0; h < 8; h++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            put(dx, y + h, dz, noise(dx, dz + h, 514) < 0.7 ? "stone_bricks" : "mossy_stone_bricks");
                        }
                    }
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        put(dx, y + 8, dz, "stone_brick_slab[type=bottom]");
                        if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && Math.floorMod(dx + dz, 2) == 0) {
                            put(dx, y + 9, dz, "stone_bricks");
                        }
                    }
                }
                put(0, y + 9, 0, light());
                String[] wall = {"red", "blue", "lime", "yellow"};
                int[][] sides = {{0, -2, 2}, {2, 0, 3}, {0, 2, 0}, {-2, 0, 1}};
                for (int i = 0; i < 4; i++) {
                    String face = new String[]{"north", "east", "south", "west"}[i];
                    put(sides[i][0], y + 5, sides[i][1], wall[i] + "_wall_banner[facing=" + face + "]");
                }
                break;
            case HOMOKKO:
                for (int h = 0; h < 4; h++) {
                    int r = 3 - h;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                            put(dx, y + h, dz, edge ? "smooth_sandstone" : "sandstone");
                        }
                    }
                }
                put(0, y + 4, 0, "gold_block");
                put(0, y + 5, 0, "lantern[hanging=false]");
                for (int side = -1; side <= 1; side += 2) {
                    palm(side * 5, y, side * -5, 6, side);
                }
                break;
            default:
                // nagy tölgy a közepén
                for (int h = 0; h < 9; h++) {
                    for (int dx = 0; dx <= 1; dx++) {
                        for (int dz = 0; dz <= 1; dz++) {
                            put(dx, y + h, dz, "oak_log[axis=y]");
                        }
                    }
                }
                for (int dy = 6; dy <= 12; dy++) {
                    double radius = dy < 9 ? 4.6 : (dy < 11 ? 3.8 : 2.4);
                    int r = (int) Math.ceil(radius);
                    for (int dx = -r; dx <= r + 1; dx++) {
                        for (int dz = -r; dz <= r + 1; dz++) {
                            double d = dist(dx - 0.5, dz - 0.5);
                            if (d <= radius && noise(dx, dz + dy, 515) < 0.92 && !(dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1 && dy < 9)) {
                                put(dx, y + dy, dz, "oak_leaves[persistent=true]");
                            }
                        }
                    }
                }
        }
    }

    // ---- váró ----

    private void waitingRoom() {
        double radius = 8.5;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double d = dist(x, z);
                if (d > radius + 1.0) {
                    continue;
                }
                if (d <= radius) {
                    put(x, LOBBY_Y, z, d < 2.0 ? centerFloor(x, z) : (d < 3.2 ? rim() : ground(x, z)));
                    put(x, LOBBY_Y - 1, z, underBlock(x, -3, z, 4));
                } else {
                    put(x, LOBBY_Y, z, rim());
                    for (int y = 1; y <= 3; y++) {
                        put(x, LOBBY_Y + y, z, "glass");
                    }
                    for (int y = 4; y <= 6; y++) {
                        put(x, LOBBY_Y + y, z, "barrier");
                    }
                }
            }
        }
        int teams = theme.teams;
        for (int i = 0; i < teams; i++) {
            double angle = Math.PI * 2 * i / teams - Math.PI / 2;
            int x = (int) Math.round(Math.cos(angle) * 6);
            int z = (int) Math.round(Math.sin(angle) * 6);
            put(x, LOBBY_Y + 1, z, TeamColor.values()[i].block + "_wool");
            put(x, LOBBY_Y + 2, z, light());
        }
        spot("lobby", -1, 0.5, LOBBY_Y + 1, 0.5, 180);
        spot("spectator", -1, 0.5, SPECTATOR_Y, 0.5, 180);
    }
}
