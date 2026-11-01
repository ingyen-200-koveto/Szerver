package hu.icemine.core.modelobby;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Title;
import java.util.Locale;

/**
 * Egy játékmód saját lobbyja. Minden módnak saját témája van (FullPvP: gladiátor aréna, Fyland: felhők fölötti szigetek,
 * ComboFly: ametiszt kristályok, Gens: farm és bánya, Kingdoms: vár udvar, Duels: párbaj csarnok), de a fontos pontok
 * (spawn, portál, hologramok, toplisták) mindenhol ugyanott vannak, így a config és a hologramok témától függetlenek.
 */
public final class ModeLobbyBuild extends Build {

    public static final int RADIUS = 26;
    public static final int CLEAR = 46;
    static final int PORTAL_Z = -12;
    static final int SPAWN_Z = 12;

    enum Theme {
        KITPVP, SKYPVP, COMBOFLY, GENS, KINGDOMS, DUELS, BEDWARS, SNOW;

        static Theme of(String text) {
            if (text == null) {
                return SNOW;
            }
            String key = text.trim().toLowerCase(Locale.ROOT);
            // a FullPvP a gladiátor arénát, a Fyland a felhők fölötti szigeteket kapja
            if (key.equals("fullpvp") || key.equals("arena")) {
                return KITPVP;
            }
            if (key.equals("fyland") || key.equals("oneblock") || key.equals("sky")) {
                return SKYPVP;
            }
            try {
                return valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return SNOW;
            }
        }
    }

    private final String color;
    private final String title;
    private final int split;
    private final Theme theme;

    public ModeLobbyBuild(Sink sink, int cx, int cy, int cz, String color, String title, int split) {
        this(sink, cx, cy, cz, color, title, split, "snow");
    }

    public ModeLobbyBuild(Sink sink, int cx, int cy, int cz, String color, String title, int split, String theme) {
        super(sink, cx, cy, cz);
        this.color = color == null || color.isBlank() ? "light_blue" : color;
        this.title = title == null ? "" : title;
        this.split = split;
        this.theme = Theme.of(theme);
    }

    private String outline() {
        switch (color) {
            case "light_blue":
            case "blue":
            case "cyan":
                return "blue_terracotta";
            case "lime":
            case "green":
                return "green_terracotta";
            case "yellow":
                return "orange_terracotta";
            case "orange":
                return "brown_terracotta";
            case "purple":
            case "magenta":
                return "purple_terracotta";
            default:
                return color + "_terracotta";
        }
    }

    private String c(String suffix) {
        return color + "_" + suffix;
    }

    @Override
    public void build() {
        island();
        rim();
        paths();
        spawnPlaza();
        portal();
        pedestals();
        switch (theme) {
            case KITPVP:
                gladiators();
                break;
            case SKYPVP:
                clouds();
                break;
            case COMBOFLY:
                crystals();
                break;
            case GENS:
                farm();
                break;
            case KINGDOMS:
                castle();
                break;
            case DUELS:
                hall();
                break;
            case BEDWARS:
                camp();
                break;
            default:
                trees();
        }
        lanterns();
        if (!title.isEmpty()) {
            int scale = title.length() <= 5 ? 2 : 1;
            new Title((x, y, z, data) -> put(x, y, z, data), 0, scale == 2 ? 12 : 11, PORTAL_Z - 8, scale, "south", title, c("concrete"), "white_concrete",
                    split <= 0 ? title.length() : split, outline(), outline()).build();
        }
        layout.point("spawn", 0.5, 1, SPAWN_Z + 0.5, 180);
        layout.areas.put("portal", new hu.icemine.core.build.Layout.Area(-2, 1, PORTAL_Z - 1, 2, 5, PORTAL_Z + 1));
        layout.point("holo:play", 6.5, 2.35, SPAWN_Z - 2.5, 0);
        layout.point("holo:info", -5.5, 2.35, SPAWN_Z - 2.5, 0);
        layout.point("holo:top1", -12.5, 3.3, 0.5, 0);
        layout.point("holo:top2", 13.5, 3.3, 0.5, 0);
        layout.point("holo:top3", -9.5, 3.3, -6.5, 0);
        layout.point("holo:top4", 10.5, 3.3, -6.5, 0);
    }

    private double edge(int x, int z) {
        double angle = Math.atan2(z, x);
        return RADIUS + 1.2 * Math.sin(angle * 4 + 0.5) + 0.7 * Math.sin(angle * 9 + 2);
    }

    // ---- a sziget anyaga témánként ----

    private String surface(int x, int z) {
        double n = noise(x, z, 62);
        double s = smooth(x, z, 6.0, 69);
        switch (theme) {
            case KITPVP:
                return s > 0.62 ? "smooth_sandstone" : (n < 0.12 ? "red_sand" : (n < 0.2 ? "coarse_dirt" : "sand"));
            case SKYPVP:
            case GENS:
                return n < 0.06 ? "moss_block" : "grass_block";
            case COMBOFLY:
                return s > 0.6 ? "purpur_block" : (n < 0.1 ? "end_stone" : "end_stone_bricks");
            case KINGDOMS:
                return s > 0.58 ? "coarse_dirt" : (n < 0.1 ? "moss_block" : "grass_block");
            case DUELS:
                return n < 0.15 ? "cracked_polished_blackstone_bricks" : (s > 0.6 ? "blackstone" : "polished_blackstone_bricks");
            case BEDWARS:
                return s > 0.64 ? "moss_block" : (n < 0.1 ? "coarse_dirt" : "grass_block");
            default:
                return n < 0.08 ? "white_concrete_powder" : "snow_block";
        }
    }

    private String under(int x, int y, int z, int depth) {
        double n = noise(x + y, z - y, 65);
        switch (theme) {
            case KITPVP:
                if (y > -3) {
                    return n < 0.6 ? "sandstone" : "smooth_sandstone";
                }
                return n < 0.4 ? "terracotta" : (n < 0.7 ? "orange_terracotta" : "red_sandstone");
            case SKYPVP:
            case GENS:
            case KINGDOMS:
            case BEDWARS:
                if (y > -3) {
                    return "dirt";
                }
                if (y < -depth + 2 && theme == Theme.SKYPVP) {
                    return n < 0.5 ? "white_wool" : "white_concrete";
                }
                return n < 0.5 ? "stone" : (n < 0.75 ? "andesite" : (n < 0.85 ? "coal_ore" : "dirt"));
            case COMBOFLY:
                if (y < -depth + 3) {
                    return n < 0.35 ? "amethyst_block" : (n < 0.45 ? "budding_amethyst" : "calcite");
                }
                return n < 0.5 ? "end_stone" : (n < 0.8 ? "obsidian" : "crying_obsidian");
            case DUELS:
                return n < 0.4 ? "blackstone" : (n < 0.7 ? "basalt[axis=y]" : (n < 0.8 ? "gilded_blackstone" : "deepslate"));
            default:
                if (y > -3) {
                    return noise(x, z + y, 63) < 0.7 ? "snow_block" : "packed_ice";
                }
                if (y < -depth + 2) {
                    return noise(x + y, z, 64) < 0.5 ? "blue_ice" : "packed_ice";
                }
                return n < 0.45 ? "stone" : (n < 0.75 ? "packed_ice" : "andesite");
        }
    }

    private void island() {
        for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) {
            for (int z = -RADIUS - 3; z <= RADIUS + 3; z++) {
                double d = dist(x, z);
                double e = edge(x, z);
                if (d > e) {
                    continue;
                }
                double t = 1 - d / e;
                int depth = 3 + (int) Math.round(t * t * 20 + smooth(x, z, 5.0, 61) * 6 * t);
                for (int y = -depth; y <= 0; y++) {
                    put(x, y, z, y == 0 ? surface(x, z) : under(x, y, z, depth));
                }
            }
        }
    }

    private void rim() {
        for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) {
            for (int z = -RADIUS - 3; z <= RADIUS + 3; z++) {
                double d = dist(x, z);
                double e = edge(x, z);
                if (d > e || d <= e - 1.2) {
                    continue;
                }
                rimBlock(x, z);
                for (int y = 3; y <= 6; y++) {
                    put(x, y, z, "barrier");
                }
            }
        }
    }

    private void rimBlock(int x, int z) {
        boolean post = noise(x, z, 66) < 0.1;
        switch (theme) {
            case KITPVP:
                put(x, 1, z, "sandstone_wall");
                put(x, 2, z, post ? "torch" : "air");
                break;
            case SKYPVP:
                put(x, 1, z, "white_stained_glass");
                put(x, 2, z, post ? "white_stained_glass" : "air");
                break;
            case COMBOFLY:
                put(x, 1, z, "purpur_slab[type=bottom]");
                put(x, 2, z, post ? "end_rod[facing=up]" : "air");
                break;
            case GENS:
                put(x, 1, z, "oak_fence");
                put(x, 2, z, post ? "lantern[hanging=false]" : "air");
                break;
            case KINGDOMS:
                put(x, 1, z, "stone_bricks");
                put(x, 2, z, Math.floorMod(x + z, 2) == 0 ? "stone_brick_wall" : (post ? "torch" : "air"));
                break;
            case DUELS:
                put(x, 1, z, "blackstone_wall");
                put(x, 2, z, post ? "soul_lantern[hanging=false]" : "air");
                break;
            case BEDWARS:
                // a perem négy csapatszínben körbe
                String[] wool = {"red", "blue", "lime", "yellow"};
                int part = (int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 2)) % 4;
                put(x, 1, z, wool[part] + "_wool");
                put(x, 2, z, post ? "lantern[hanging=false]" : "air");
                break;
            default:
                put(x, 1, z, "stone_brick_wall[up=true]");
                put(x, 2, z, post ? "lantern[hanging=false]" : "air");
        }
    }

    // ---- utak, spawn, portál: mindenhol ugyanott, de témához illő anyagból ----

    private String[] pathBlocks() {
        switch (theme) {
            case KITPVP:
                return new String[]{"cut_sandstone", "smooth_sandstone", c("terracotta")};
            case SKYPVP:
                return new String[]{"birch_planks", "smooth_quartz", c("concrete")};
            case COMBOFLY:
                return new String[]{"purpur_block", "end_stone_bricks", "amethyst_block"};
            case GENS:
                return new String[]{"dirt_path", "gravel", "oak_planks"};
            case KINGDOMS:
                return new String[]{"stone_bricks", "cobblestone", "chiseled_stone_bricks"};
            case DUELS:
                return new String[]{"polished_blackstone", "smooth_quartz", "chiseled_polished_blackstone"};
            case BEDWARS:
                return new String[]{"oak_planks", "smooth_stone", c("terracotta")};
            default:
                return new String[]{"polished_andesite", "smooth_stone", c("concrete")};
        }
    }

    private void paths() {
        String[] b = pathBlocks();
        for (int z = PORTAL_Z + 2; z <= SPAWN_Z - 4; z++) {
            for (int x = -2; x <= 2; x++) {
                String block = Math.abs(x) == 2 ? b[0] : (x == 0 && Math.floorMod(z, 4) == 0 ? b[2] : b[1]);
                put(x, 0, z, block);
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) <= 2) {
                    continue;
                }
                put(x, 0, z, Math.abs(z) == 1 ? b[0] : b[1]);
            }
        }
    }

    private void spawnPlaza() {
        String[] b = pathBlocks();
        for (int x = -6; x <= 6; x++) {
            for (int z = SPAWN_Z - 6; z <= SPAWN_Z + 6; z++) {
                double d = dist(x, z - SPAWN_Z);
                if (d > 5.6) {
                    continue;
                }
                String block;
                if (d < 1.2) {
                    block = theme == Theme.COMBOFLY ? "amethyst_block" : (theme == Theme.DUELS ? "gilded_blackstone" : "sea_lantern");
                } else if (d < 2.4) {
                    block = theme == Theme.DUELS ? "red_concrete" : c("concrete");
                } else if (d > 4.6) {
                    block = b[0];
                } else if (theme == Theme.DUELS) {
                    block = Math.floorMod(x + z, 2) == 0 ? "smooth_quartz" : "polished_blackstone";
                } else {
                    block = Math.floorMod(x + z, 2) == 0 ? b[1] : (theme == Theme.SNOW ? "calcite" : b[0]);
                }
                put(x, 0, z, block);
            }
        }
        if (theme == Theme.DUELS) {
            for (int x = -2; x <= 2; x++) {
                for (int z = SPAWN_Z - 2; z <= SPAWN_Z + 2; z++) {
                    double d = dist(x, z - SPAWN_Z);
                    if (d >= 1.2 && d < 2.4 && x > 0) {
                        put(x, 0, z, "blue_concrete");
                    }
                }
            }
        }
    }

    private String[] portalBlocks() {
        switch (theme) {
            case KITPVP:
                return new String[]{"red_nether_bricks", "chiseled_sandstone", "cut_sandstone", "gold_block"};
            case SKYPVP:
                return new String[]{"quartz_pillar[axis=y]", "light_blue_stained_glass", "smooth_quartz", "sea_lantern"};
            case COMBOFLY:
                return new String[]{"purpur_pillar[axis=y]", "amethyst_block", "purpur_block", "end_rod[facing=up]"};
            case GENS:
                return new String[]{"oak_log[axis=y]", "hay_block[axis=y]", "oak_planks", "glowstone"};
            case KINGDOMS:
                return new String[]{"stone_bricks", "chiseled_stone_bricks", "mossy_stone_bricks", "gold_block"};
            case DUELS:
                return new String[]{"polished_blackstone_bricks", "gilded_blackstone", "chiseled_polished_blackstone", "soul_lantern[hanging=false]"};
            case BEDWARS:
                return new String[]{"stripped_dark_oak_log[axis=y]", "red_wool", "spruce_planks", "glowstone"};
            default:
                return new String[]{"quartz_pillar[axis=y]", "chiseled_quartz_block", "quartz_bricks", "sea_lantern"};
        }
    }

    private void portal() {
        String[] b = pathBlocks();
        String[] p = portalBlocks();
        for (int x = -6; x <= 6; x++) {
            for (int z = PORTAL_Z - 3; z <= PORTAL_Z + 3; z++) {
                put(x, 0, z, Math.abs(x) >= 5 || Math.abs(z - PORTAL_Z) >= 3 ? b[0] : b[1]);
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                boolean frame = x == -3 || x == 3 || y == 0 || y == 6;
                put(x, y, PORTAL_Z, frame ? (theme == Theme.SNOW ? "obsidian" : p[2]) : "nether_portal[axis=x]");
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 0; y <= 7; y++) {
                put(side * 4, y, PORTAL_Z, y == 7 ? p[1] : p[0]);
                put(side * 4, y, PORTAL_Z - 1, y % 2 == 0 ? c("concrete") : p[2]);
            }
            put(side * 4, 8, PORTAL_Z, theme == Theme.GENS ? "lantern[hanging=false]" : (theme == Theme.COMBOFLY ? "end_rod[facing=up]" : "lantern[hanging=false]"));
            put(side * 5, 1, PORTAL_Z + 1, theme == Theme.KITPVP ? "campfire[lit=true]" : "stone_brick_wall[up=true]");
            if (theme != Theme.KITPVP) {
                put(side * 5, 2, PORTAL_Z + 1, theme == Theme.DUELS ? "soul_lantern[hanging=false]" : "lantern[hanging=false]");
            }
            String banner = theme == Theme.DUELS ? (side < 0 ? "red" : "blue") : color;
            put(side * 4, 5, PORTAL_Z + 1, banner + "_wall_banner[facing=south]");
        }
        for (int x = -3; x <= 3; x++) {
            put(x, 7, PORTAL_Z, Math.abs(x) == 3 ? p[2] : c("concrete"));
            put(x, 8, PORTAL_Z, x == 0 ? p[3] : (theme == Theme.SNOW || theme == Theme.SKYPVP ? "quartz_slab[type=bottom]" : "air"));
        }
    }

    private void pedestals() {
        String top = theme == Theme.COMBOFLY ? "amethyst_block" : (theme == Theme.DUELS ? "chiseled_polished_blackstone" : "chiseled_stone_bricks");
        int[][] spots = {{6, SPAWN_Z - 3}, {-6, SPAWN_Z - 3}};
        for (int[] spot : spots) {
            put(spot[0], 1, spot[1], top);
            put(spot[0], 2, spot[1], c("carpet"));
        }
        String[] b = pathBlocks();
        int[][] boards = {{-13, 0}, {13, 0}, {-10, -7}, {10, -7}};
        for (int[] board : boards) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(board[0] + dx, 0, board[1] + dz, b[0]);
                }
            }
            put(board[0], 1, board[1], top);
            put(board[0], 2, board[1], theme == Theme.DUELS ? "polished_blackstone_slab[type=bottom]" : "stone_brick_slab[type=bottom]");
        }
    }

    /** Szabad hely-e dekorációnak (nem út, nem spawn, nem portál, nem toplista). */
    private boolean free(int x, int z) {
        if (dist(x, z) > RADIUS - 3) {
            return false;
        }
        if (Math.abs(x) <= 3 && z >= PORTAL_Z - 4 && z <= SPAWN_Z + 7) {
            return false;
        }
        if (Math.abs(z) <= 2 && Math.abs(x) <= 15) {
            return false;
        }
        if (Math.abs(x) <= 7 && z >= PORTAL_Z - 4 && z <= PORTAL_Z + 4) {
            return false;
        }
        if (dist(x, z - SPAWN_Z) < 8) {
            return false;
        }
        int[][] boards = {{-13, 0}, {13, 0}, {-10, -7}, {10, -7}};
        for (int[] board : boards) {
            if (Math.abs(x - board[0]) <= 2 && Math.abs(z - board[1]) <= 2) {
                return false;
            }
        }
        return true;
    }

    // ---- KitPvP: gladiátor aréna ----

    private void gladiators() {
        // oszlopsor körben, vörös zászlókkal
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            int x = (int) Math.round(Math.cos(angle) * (RADIUS - 4));
            int z = (int) Math.round(Math.sin(angle) * (RADIUS - 4));
            if (!free(x, z)) {
                continue;
            }
            for (int y = 1; y <= 5; y++) {
                put(x, y, z, y == 1 ? "chiseled_sandstone" : (y == 5 ? "chiseled_sandstone" : "cut_sandstone"));
            }
            put(x, 6, z, "campfire[lit=true]");
            put(x, 4, z + (z > 0 ? -1 : 1), "red_wall_banner[facing=" + (z > 0 ? "north" : "south") + "]");
        }
        // edző bábuk (széna + tök)
        int[][] dummies = {{-16, 8}, {-18, 4}, {16, 9}, {18, 5}, {-8, 18}, {9, 18}};
        for (int[] d : dummies) {
            if (!free(d[0], d[1])) {
                continue;
            }
            put(d[0], 1, d[1], "oak_fence");
            put(d[0], 2, d[1], "hay_block[axis=y]");
            put(d[0], 3, d[1], "carved_pumpkin[facing=north]");
        }
        // fegyverkovács sarok
        int[][] forge = {{-17, -10}, {17, -11}};
        for (int[] f : forge) {
            if (!free(f[0], f[1])) {
                continue;
            }
            put(f[0], 1, f[1], "anvil[facing=north]");
            put(f[0] + 1, 1, f[1], "smithing_table");
            put(f[0] - 1, 1, f[1], "grindstone[face=floor,facing=north]");
            put(f[0], 1, f[1] + 1, "blast_furnace[facing=south,lit=true]");
        }
        // pár akácfa és száraz bokor
        int[][] trees = {{-20, -2}, {21, 3}, {-3, 21}, {4, -21}};
        for (int[] t : trees) {
            if (!free(t[0], t[1])) {
                continue;
            }
            acacia(t[0], t[1]);
        }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (free(x, z) && noise(x, z, 70) < 0.025) {
                    put(x, 1, z, "dead_bush");
                }
            }
        }
    }

    private void acacia(int x, int z) {
        for (int y = 1; y <= 4; y++) {
            put(x + (y > 2 ? 1 : 0), y, z, "acacia_log[axis=y]");
        }
        for (int dx = -2; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx - 0.5) + Math.abs(dz) <= 3.5) {
                    put(x + dx, 5, z + dz, "acacia_leaves[persistent=true]");
                }
            }
        }
    }

    // ---- SkyPvP: felhők és lebegő szigetek ----

    private void clouds() {
        int[][] puffs = {{-30, -6, -18}, {30, -4, -14}, {-32, -8, 10}, {28, -10, 18}, {0, -12, 32}, {-14, 8, -34}, {16, 10, -32}, {-34, 4, -2}, {34, 6, 2}};
        for (int[] p : puffs) {
            cloud(p[0], p[1], p[2], 4 + (int) (noise(p[0], p[2], 71) * 3));
        }
        int[][] islets = {{-20, -16}, {21, -15}, {-19, 17}, {20, 16}};
        for (int[] i : islets) {
            if (!free(i[0], i[1])) {
                continue;
            }
            birch(i[0], i[1]);
        }
        // kis lebegő szigetek a peremen túl, hidakkal
        floating(-36, 2, -24);
        floating(36, 3, -22);
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (free(x, z) && noise(x, z, 72) < 0.04) {
                    put(x, 1, z, noise(x, z, 73) < 0.5 ? "short_grass" : (noise(x, z, 74) < 0.5 ? "cornflower" : "azure_bluet"));
                }
            }
        }
    }

    private void cloud(int cx, int cy, int cz, int size) {
        for (int x = -size * 2; x <= size * 2; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = -1; y <= 2; y++) {
                    double d = Math.sqrt((x / 2.0) * (x / 2.0) + z * z + (y * 1.6) * (y * 1.6));
                    if (d <= size - smooth(cx + x, cz + z, 3.0, 75) * 2) {
                        put(cx + x, cy + y, cz + z, noise(cx + x, cz + z + y, 76) < 0.7 ? "white_wool" : "white_concrete");
                    }
                }
            }
        }
    }

    private void floating(int cx, int cy, int cz) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double d = dist(x, z);
                if (d > 4.2) {
                    continue;
                }
                int depth = 1 + (int) Math.round((4.2 - d) * 1.4);
                for (int y = -depth; y <= 0; y++) {
                    put(cx + x, cy + y, cz + z, y == 0 ? "grass_block" : (y > -2 ? "dirt" : "stone"));
                }
            }
        }
        birch(cx, cz + 0, cy);
    }

    private void birch(int x, int z) {
        birch(x, z, 0);
    }

    private void birch(int x, int z, int base) {
        int height = 5 + (int) (noise(x, z, 77) * 2);
        for (int y = 1; y <= height; y++) {
            put(x, base + y, z, "birch_log[axis=y]");
        }
        for (int y = height - 2; y <= height + 1; y++) {
            int r = y >= height ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0 || y > height) && Math.abs(dx) + Math.abs(dz) <= r + 1) {
                        put(x + dx, base + y, z + dz, "birch_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    // ---- ComboFly: ametiszt kristályok ----

    private void crystals() {
        int[][] spikes = {{-17, 10, 9}, {17, 12, 10}, {-19, -8, 12}, {19, -10, 11}, {-9, 19, 8}, {10, 20, 9}, {-13, -17, 10}, {14, -18, 9}, {0, 22, 7}};
        for (int[] s : spikes) {
            if (!free(s[0], s[1])) {
                continue;
            }
            spike(s[0], s[1], s[2]);
        }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!free(x, z)) {
                    continue;
                }
                double n = noise(x, z, 78);
                if (n < 0.03) {
                    put(x, 1, z, "amethyst_cluster[facing=up]");
                } else if (n < 0.05) {
                    put(x, 1, z, "large_amethyst_bud[facing=up]");
                }
            }
        }
        // lebegő kristályszilánkok
        int[][] shards = {{-8, 14, 4}, {9, 15, 3}, {-15, 12, -14}, {15, 13, -13}, {0, 17, 20}};
        for (int[] s : shards) {
            for (int y = -1; y <= 1; y++) {
                put(s[0], s[1] + y, s[2], y == 0 ? "amethyst_block" : "purple_stained_glass");
            }
            put(s[0] + 1, s[1], s[2], "purple_stained_glass");
            put(s[0] - 1, s[1], s[2], "purple_stained_glass");
        }
    }

    private void spike(int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            double r = 1.8 * (1 - (double) y / (height + 1)) + 0.3;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dist(dx, dz) <= r) {
                        put(x + dx, y, z + dz, noise(x + dx, z + dz + y, 79) < 0.2 ? "budding_amethyst" : "amethyst_block");
                    }
                }
            }
        }
        put(x, height + 1, z, "end_rod[facing=up]");
    }

    // ---- Gens: farm és bánya ----

    private void farm() {
        // vetemények két oldalon
        farmPatch(-19, 8, "wheat[age=7]");
        farmPatch(15, 8, "carrots[age=7]");
        farmPatch(-19, -12, "potatoes[age=7]");
        // termény bemutató talapzatok (a Gens földjei sorban)
        String[] ores = {"hay_block[axis=y]", "melon", "pumpkin", "moss_block", "nether_wart_block", "emerald_block"};
        for (int i = 0; i < ores.length; i++) {
            int x = 14 + (i % 3) * 3;
            int z = -15 + (i / 3) * 3;
            if (!free(x, z)) {
                continue;
            }
            put(x, 1, z, "polished_andesite");
            put(x, 2, z, ores[i]);
        }
        // szélmalom
        windmill(-6, 20);
        // széna, hordók, komposztáló
        int[][] props = {{9, 19}, {11, 18}, {-21, 0}, {21, -1}};
        for (int[] p : props) {
            if (!free(p[0], p[1])) {
                continue;
            }
            put(p[0], 1, p[1], "hay_block[axis=y]");
            put(p[0] + 1, 1, p[1], "barrel[facing=up]");
            put(p[0], 2, p[1], "hay_block[axis=y]");
            put(p[0], 1, p[1] + 1, "composter[level=0]");
        }
        int[][] trees = {{-12, 19}, {19, 13}, {-21, -3}, {6, -21}};
        for (int[] t : trees) {
            if (free(t[0], t[1])) {
                oak(t[0], t[1]);
            }
        }
    }

    private void farmPatch(int x0, int z0, String crop) {
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (!free(x, z)) {
                    continue;
                }
                if (dx == 2 && dz == 2) {
                    put(x, 0, z, "water");
                    continue;
                }
                put(x, 0, z, "farmland[moisture=7]");
                put(x, 1, z, crop);
            }
        }
    }

    private void windmill(int x, int z) {
        if (!free(x, z)) {
            return;
        }
        for (int y = 1; y <= 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (edge) {
                        put(x + dx, y, z + dz, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? "stripped_oak_log[axis=y]" : "oak_planks");
                    }
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    put(x + dx, 8, z + dz, "spruce_slab[type=bottom]");
                }
            }
        }
        put(x, 6, z - 2, "oak_planks");
        for (int i = 1; i <= 4; i++) {
            put(x + i, 6 + i, z - 2, "white_wool");
            put(x - i, 6 + i, z - 2, "white_wool");
            if (6 - i >= 2) {
                put(x + i, 6 - i, z - 2, "white_wool");
                put(x - i, 6 - i, z - 2, "white_wool");
            }
        }
    }

    private void oak(int x, int z) {
        for (int y = 1; y <= 4; y++) {
            put(x, y, z, "oak_log[axis=y]");
        }
        for (int y = 3; y <= 6; y++) {
            int r = y >= 5 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0 || y > 4) && !(Math.abs(dx) == r && Math.abs(dz) == r && noise(x + dx, z + dz + y, 80) < 0.5)) {
                        put(x + dx, y, z + dz, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    // ---- Kingdoms: vár udvar ----

    private void castle() {
        int[][] towers = {{-18, -14}, {18, -14}, {-18, 15}, {18, 15}};
        for (int[] t : towers) {
            tower(t[0], t[1]);
        }
        // trón a portál mögött kicsit oldalt
        int[][] thrones = {{-9, PORTAL_Z - 2}, {9, PORTAL_Z - 2}};
        for (int[] t : thrones) {
            put(t[0], 1, t[1], "gold_block");
            put(t[0], 2, t[1], "spruce_stairs[facing=south,half=bottom]");
            put(t[0], 3, t[1] - 1, "gold_block");
            put(t[0] - 1, 1, t[1] + 1, "red_carpet");
            put(t[0], 1, t[1] + 1, "red_carpet");
            put(t[0] + 1, 1, t[1] + 1, "red_carpet");
        }
        // virágágyások és sötét tölgyek
        int[][] beds = {{-16, 6}, {16, 6}, {-8, 19}, {8, 19}};
        String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy"};
        for (int[] b : beds) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (free(b[0] + dx, b[1] + dz)) {
                        put(b[0] + dx, 0, b[1] + dz, "grass_block");
                        put(b[0] + dx, 1, b[1] + dz, flowers[Math.floorMod(b[0] + dx * 3 + dz, flowers.length)]);
                    }
                }
            }
        }
        int[][] trees = {{-21, 0}, {21, 2}, {0, 22}, {-13, -19}, {13, -19}};
        for (int[] t : trees) {
            if (free(t[0], t[1])) {
                darkOak(t[0], t[1]);
            }
        }
    }

    private void tower(int x, int z) {
        for (int y = 1; y <= 9; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double d = dist(dx, dz);
                    if (d > 2.4) {
                        continue;
                    }
                    boolean wall = d > 1.4;
                    if (wall) {
                        String block = noise(x + dx, z + dz + y, 81) < 0.2 ? "mossy_stone_bricks" : (noise(x + dx, z + dz + y, 82) < 0.15 ? "cracked_stone_bricks" : "stone_bricks");
                        put(x + dx, y, z + dz, y == 5 && (dx == 0 || dz == 0) ? "air" : block);
                    } else {
                        put(x + dx, y, z + dz, y == 9 ? "spruce_planks" : "air");
                    }
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dist(dx, dz) <= 2.4 && dist(dx, dz) > 1.4 && Math.floorMod(dx + dz, 2) == 0) {
                    put(x + dx, 10, z + dz, "stone_bricks");
                }
            }
        }
        for (int y = 10; y <= 12; y++) {
            put(x, y, z, "spruce_fence");
        }
        put(x, 13, z, c("banner") + "[rotation=0]");
        put(x, 2, z, "lantern[hanging=false]");
    }

    private void darkOak(int x, int z) {
        for (int y = 1; y <= 5; y++) {
            put(x, y, z, "dark_oak_log[axis=y]");
            put(x + 1, y, z, "dark_oak_log[axis=y]");
        }
        for (int y = 4; y <= 7; y++) {
            int r = y >= 6 ? 2 : 3;
            for (int dx = -r; dx <= r + 1; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dist(dx - 0.5, dz) <= r + 0.3 && (y > 5 || Math.abs(dx - 0.5) > 0.6)) {
                        put(x + dx, y, z + dz, "dark_oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    // ---- Duels: párbaj csarnok ----

    private void hall() {
        // két oldal: piros (nyugat) és kék (kelet)
        for (int side = -1; side <= 1; side += 2) {
            String team = side < 0 ? "red" : "blue";
            for (int i = 0; i < 4; i++) {
                int x = side * 17;
                int z = -12 + i * 8;
                if (!free(x, z)) {
                    continue;
                }
                for (int y = 1; y <= 6; y++) {
                    put(x, y, z, y == 1 || y == 6 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
                }
                put(x, 7, z, "soul_lantern[hanging=false]");
                put(x - side, 4, z, team + "_wall_banner[facing=" + (side < 0 ? "east" : "west") + "]");
                put(x, 0, z + 1, team + "_terracotta");
                put(x, 0, z - 1, team + "_terracotta");
            }
            // keresztbe tett kardok szobra
            int sx = side * 20;
            int sz = 10;
            if (free(sx, sz)) {
                put(sx, 1, sz, "polished_blackstone");
                put(sx, 2, sz, team + "_concrete");
                put(sx, 3, sz, "iron_bars");
                put(sx, 4, sz, "iron_bars");
                put(sx, 5, sz, "gold_block");
            }
        }
        // a csarnok közepén lógó láncos lámpások
        int[][] hangs = {{-8, 6}, {8, 6}, {-8, -6}, {8, -6}, {0, 19}};
        for (int[] h : hangs) {
            if (!free(h[0], h[1])) {
                continue;
            }
            put(h[0], 1, h[1], "polished_blackstone_wall");
            put(h[0], 2, h[1], "polished_blackstone_wall");
            put(h[0], 3, h[1], "chain[axis=y]");
            put(h[0], 4, h[1], "soul_lantern[hanging=false]");
        }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (free(x, z) && noise(x, z, 83) < 0.02) {
                    put(x, 1, z, noise(x, z, 84) < 0.5 ? "red_carpet" : "blue_carpet");
                }
            }
        }
    }

    // ---- BedWars: tábor ágyakkal, generátorokkal és fákkal ----

    /** A BedWars lobby négy szobra (Solo, Duó, 3v3v3v3, 4v4v4v4) ívben a spawn előtt, mind a spawn felé néz. */
    static final int[][] STATUES = {{-10, 6}, {-5, 5}, {5, 5}, {10, 6}};
    private static final String[] STATUE_COLORS = {"red", "blue", "yellow", "lime"};

    private void statues() {
        for (int i = 0; i < STATUES.length; i++) {
            int x = STATUES[i][0];
            int z = STATUES[i][1];
            String color = STATUE_COLORS[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(x + dx, 0, z + dz, dx == 0 && dz == 0 ? color + "_concrete" : color + "_terracotta");
                    put(x + dx, 1, z + dz, "air");
                }
            }
            // két lámpa a szobor mögött (északra, a spawntól távolabb)
            for (int side = -1; side <= 1; side += 2) {
                put(x + side * 2, 0, z - 1, "oak_planks");
                put(x + side * 2, 1, z - 1, "oak_fence");
                put(x + side * 2, 2, z - 1, "lantern[hanging=false]");
            }
            // a mód színű ágy a szobor mögött
            put(x, 0, z - 2, "oak_planks");
            put(x, 0, z - 3, "oak_planks");
            put(x, 1, z - 2, color + "_bed[part=foot,facing=north]");
            put(x, 1, z - 3, color + "_bed[part=head,facing=north]");
            double dx = 0.5 - (x + 0.5);
            double dz = SPAWN_Z + 0.5 - (z + 0.5);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            layout.point("npc:statue" + (i + 1), x + 0.5, 1, z + 0.5, yaw);
        }
    }

    private void camp() {
        statues();
        String[] colors = {"red", "blue", "lime", "yellow"};
        int[][] pads = {{-17, -12}, {17, -12}, {-17, 10}, {17, 10}};
        for (int i = 0; i < pads.length; i++) {
            int x = pads[i][0];
            int z = pads[i][1];
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 3;
                    put(x + dx, 0, z + dz, border ? "oak_planks" : colors[i] + "_wool");
                }
            }
            put(x, 1, z, colors[i] + "_bed[part=foot,facing=north]");
            put(x, 1, z - 1, colors[i] + "_bed[part=head,facing=north]");
            for (int side = -1; side <= 1; side += 2) {
                put(x + side * 2, 1, z - 3, "oak_log[axis=y]");
                put(x + side * 2, 2, z - 3, "oak_fence");
                put(x + side * 2, 3, z - 3, "lantern[hanging=false]");
            }
            // a "védelem" az ágy körül, ahogy a játékban szokás
            int[][] ring = {{-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {0, 1}, {0, -2}};
            for (int[] r : ring) {
                put(x + r[0], 1, z + r[1], colors[i] + "_wool");
            }
            put(x, 2, z, "end_stone");
            put(x, 2, z - 1, "end_stone");
        }
        int[][] gens = {{-9, -17, 0}, {9, -17, 1}};
        for (int[] g : gens) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean center = dx == 0 && dz == 0;
                    put(g[0] + dx, 0, g[1] + dz, center ? (g[2] == 0 ? "iron_block" : "diamond_block") : (g[2] == 0 ? "gold_block" : "polished_andesite"));
                }
            }
            put(g[0], 1, g[1], g[2] == 0 ? "light_weighted_pressure_plate" : "heavy_weighted_pressure_plate");
        }
        int[][] trees = {{-19, 0}, {20, -2}, {-11, 19}, {12, 20}, {0, 23}, {-21, -5}};
        for (int[] t : trees) {
            if (dist(t[0], t[1]) > RADIUS - 4 || !free(t[0], t[1]) || nearAny(t[0], t[1], pads, 5, 6) || nearAny(t[0], t[1], STATUES, 4, 5)) {
                continue;
            }
            int height = 4 + (int) (noise(t[0], t[1], 85) * 2);
            for (int y = 1; y <= height; y++) {
                put(t[0], y, t[1], "oak_log[axis=y]");
            }
            for (int dy = -1; dy <= 1; dy++) {
                int r = dy == 1 ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if ((Math.abs(dx) == r && Math.abs(dz) == r && dy != -1) || (dx == 0 && dz == 0 && dy < 1)) {
                            continue;
                        }
                        put(t[0] + dx, height + dy, t[1] + dz, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (!free(x, z) || nearAny(x, z, pads, 3, 4) || nearAny(x, z, gens, 2, 2) || nearAny(x, z, STATUES, 2, 4)) {
                    continue;
                }
                double n = noise(x, z, 86);
                if (n < 0.08) {
                    put(x, 1, z, "short_grass");
                } else if (n < 0.1) {
                    put(x, 1, z, n < 0.09 ? "poppy" : "dandelion");
                }
            }
        }
    }

    private static boolean nearAny(int x, int z, int[][] spots, int rx, int rz) {
        for (int[] spot : spots) {
            if (Math.abs(x - spot[0]) <= rx && Math.abs(z - spot[1]) <= rz) {
                return true;
            }
        }
        return false;
    }

    // ---- az eredeti havas fenyők ----

    private void trees() {
        int[][] spots = {{-17, 12}, {16, 14}, {-19, -6}, {19, -8}, {-10, 20}, {11, 21}, {-14, -16}, {15, -17}, {0, 22}};
        for (int[] spot : spots) {
            int x = spot[0];
            int z = spot[1];
            if (dist(x, z) > RADIUS - 4) {
                continue;
            }
            int height = 6 + (int) (noise(x, z, 67) * 3);
            for (int y = 1; y <= 2; y++) {
                put(x, y, z, "spruce_log[axis=y]");
            }
            for (int h = 0; h < height; h++) {
                double r = 2.6 * (1 - (double) h / height) + 0.4;
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        double d = dist(dx, dz);
                        if (d > r) {
                            continue;
                        }
                        boolean snowy = h % 2 == 1 && d > r - 1 && noise(x + dx, z + dz + h, 68) < 0.5;
                        put(x + dx, 3 + h, z + dz, dx == 0 && dz == 0 ? "spruce_log[axis=y]" : (snowy ? "snow_block" : "spruce_leaves[persistent=true]"));
                    }
                }
            }
            put(x, 3 + height, z, "snow_block");
        }
    }

    private void lanterns() {
        String post;
        String light;
        switch (theme) {
            case KITPVP:
                post = "sandstone_wall";
                light = "torch";
                break;
            case COMBOFLY:
                post = "purpur_pillar[axis=y]";
                light = "end_rod[facing=up]";
                break;
            case GENS:
                post = "oak_fence";
                light = "lantern[hanging=false]";
                break;
            case KINGDOMS:
                post = "stone_brick_wall";
                light = "lantern[hanging=false]";
                break;
            case DUELS:
                post = "polished_blackstone_wall";
                light = "soul_lantern[hanging=false]";
                break;
            case SKYPVP:
                post = "birch_fence";
                light = "lantern[hanging=false]";
                break;
            default:
                post = "spruce_fence";
                light = "lantern[hanging=false]";
        }
        for (int z = PORTAL_Z + 4; z <= SPAWN_Z - 6; z += 5) {
            for (int side = -1; side <= 1; side += 2) {
                put(side * 3, 1, z, post);
                put(side * 3, 2, z, post);
                put(side * 3, 3, z, light);
            }
        }
    }
}
