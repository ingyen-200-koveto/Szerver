package hu.icemine.core.skywars;

import hu.icemine.core.build.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Egy SkyWars pálya: körben a játékosok szigetei (mindegyiken fa, ládák és üveg ketrec), középen nagyobb sziget
 * jobb ládákkal. Ugyanazzal a seed-del mindig ugyanúgy épül, így minden meccs után pontosan visszaáll.
 */
public final class SwMap {

    public enum Theme {
        JEG("jeg", "Jégszigetek", "snow_block", "packed_ice", "stone", "spruce_log", "spruce_leaves[persistent=true]", "light_blue_stained_glass"),
        ERDO("erdo", "Erdei szigetek", "grass_block", "dirt", "stone", "oak_log", "oak_leaves[persistent=true]", "lime_stained_glass"),
        SIVATAG("sivatag", "Sivatagi szigetek", "sand", "sandstone", "smooth_sandstone", "jungle_log", "jungle_leaves[persistent=true]", "orange_stained_glass");

        public final String id;
        public final String display;
        final String top;
        final String sub;
        final String rock;
        final String log;
        final String leaves;
        final String glass;

        Theme(String id, String display, String top, String sub, String rock, String log, String leaves, String glass) {
            this.id = id;
            this.display = display;
            this.top = top;
            this.sub = sub;
            this.rock = rock;
            this.log = log;
            this.leaves = leaves;
            this.glass = glass;
        }

        public static Theme of(String text) {
            if (text == null) {
                return null;
            }
            String t = text.trim().toLowerCase(Locale.ROOT);
            for (Theme theme : values()) {
                if (theme.id.equals(t) || theme.name().equalsIgnoreCase(t)) {
                    return theme;
                }
            }
            return null;
        }
    }

    public static final int RADIUS = 42;
    public static final int ISLAND = 7;
    public static final int CENTER = 11;
    /** Ennyi blokkra van a pálya széle a középponttól (a takarításhoz). */
    public static final int REACH = RADIUS + ISLAND + 8;
    public static final int BELOW = 16;
    public static final int ABOVE = 30;

    private final Build.Sink sink;
    private final int cx;
    private final int cy;
    private final int cz;
    private final Theme theme;
    private final int islands;

    public SwMap(Build.Sink sink, int cx, int cy, int cz, Theme theme, int islands) {
        this.sink = sink;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.theme = theme;
        this.islands = islands;
    }

    /** A sziget középpontja (relatív x, z). */
    public static int[] island(int index, int count) {
        double a = 2 * Math.PI * index / count;
        return new int[]{(int) Math.round(Math.cos(a) * RADIUS), (int) Math.round(Math.sin(a) * RADIUS)};
    }

    /** A sziget ládái (relatív x, y, z a sziget közepéhez). */
    public static List<int[]> islandChests() {
        return List.of(new int[]{-3, 1, 2}, new int[]{2, 1, -4}, new int[]{4, 1, 3});
    }

    public static List<int[]> centerChests() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = 2 * Math.PI * i / 6;
            out.add(new int[]{(int) Math.round(Math.cos(a) * 5), 1, (int) Math.round(Math.sin(a) * 5)});
        }
        return out;
    }

    /** A ketrec padlója ennyivel van a sziget teteje fölött; a játékos eggyel fölötte áll. */
    public static final int CAGE = 3;

    public void build() {
        Random random = new Random(theme.ordinal() * 7919L + islands);
        for (int i = 0; i < islands; i++) {
            int[] c = island(i, islands);
            blob(cx + c[0], cz + c[1], ISLAND, random);
            tree(cx + c[0] - 4, cy + 1, cz + c[1] - 2);
            rocks(cx + c[0] + 4, cz + c[1] - 1, random);
            for (int[] ch : islandChests()) {
                sink.set(cx + c[0] + ch[0], cy + ch[1], cz + c[1] + ch[2], "chest");
            }
            cage(cx + c[0], cz + c[1], true);
        }
        blob(cx, cz, CENTER, random);
        for (int[] ch : centerChests()) {
            sink.set(cx + ch[0], cy + ch[1], cz + ch[2], "chest");
        }
        for (int y = 1; y <= 3; y++) {
            sink.set(cx, cy + y, cz, theme.rock);
        }
        sink.set(cx, cy + 4, cz, "sea_lantern");
    }

    private void blob(int x0, int z0, int r, Random random) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = r + (random.nextDouble() - 0.5) * 0.9;
                if (d > edge) {
                    continue;
                }
                sink.set(x0 + dx, cy, z0 + dz, theme.top);
                int depth = 2 + (int) Math.round((edge - d) * 1.1) + random.nextInt(2);
                for (int y = 1; y <= depth; y++) {
                    sink.set(x0 + dx, cy - y, z0 + dz, y <= 2 ? theme.sub : theme.rock);
                }
            }
        }
    }

    /** Kis kőkupac a sziget szélén, benne egy-két érccel (csákánnyal kibányászható). */
    private void rocks(int x, int z, Random random) {
        String[] ores = {"coal_ore", "iron_ore", theme.rock, theme.rock};
        for (int[] p : new int[][]{{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            sink.set(x + p[0], cy + 1, z + p[1], ores[random.nextInt(ores.length)]);
        }
        sink.set(x, cy + 2, z, theme.rock);
    }

    private void tree(int x, int y, int z) {
        for (int dy = 0; dy < 4; dy++) {
            sink.set(x, y + dy, z, theme.log);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 2; dy <= 3; dy++) {
                    if (dx != 0 || dz != 0) {
                        sink.set(x + dx, y + dy, z + dz, theme.leaves);
                    }
                }
            }
        }
        sink.set(x, y + 4, z, theme.leaves);
    }

    /** Ketrec a sziget fölött (true) vagy annak eltüntetése (false). */
    public void cage(int x0, int z0, boolean place) {
        String g = place ? theme.glass : "air";
        int floor = cy + CAGE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sink.set(x0 + dx, floor, z0 + dz, g);
                sink.set(x0 + dx, floor + 4, z0 + dz, g);
                if (dx != 0 || dz != 0) {
                    for (int dy = 1; dy <= 3; dy++) {
                        sink.set(x0 + dx, floor + dy, z0 + dz, g);
                    }
                }
            }
        }
    }

    public void cages(boolean place) {
        for (int i = 0; i < islands; i++) {
            int[] c = island(i, islands);
            cage(cx + c[0], cz + c[1], place);
        }
    }
}
