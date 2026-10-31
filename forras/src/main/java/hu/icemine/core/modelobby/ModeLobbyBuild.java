package hu.icemine.core.modelobby;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Title;

public final class ModeLobbyBuild extends Build {

    public static final int RADIUS = 26;
    public static final int CLEAR = 46;
    static final int PORTAL_Z = -12;
    static final int SPAWN_Z = 12;

    private final String color;
    private final String title;
    private final int split;

    public ModeLobbyBuild(Sink sink, int cx, int cy, int cz, String color, String title, int split) {
        super(sink, cx, cy, cz);
        this.color = color == null || color.isBlank() ? "light_blue" : color;
        this.title = title == null ? "" : title;
        this.split = split;
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
        trees();
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
                    String block;
                    if (y == 0) {
                        double n = noise(x, z, 62);
                        block = n < 0.08 ? "white_concrete_powder" : "snow_block";
                    } else if (y > -3) {
                        block = noise(x, z + y, 63) < 0.7 ? "snow_block" : "packed_ice";
                    } else if (y < -depth + 2) {
                        block = noise(x + y, z, 64) < 0.5 ? "blue_ice" : "packed_ice";
                    } else {
                        double n = noise(x + y, z - y, 65);
                        block = n < 0.45 ? "stone" : (n < 0.75 ? "packed_ice" : "andesite");
                    }
                    put(x, y, z, block);
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
                put(x, 1, z, "stone_brick_wall[up=true]");
                if (noise(x, z, 66) < 0.1) {
                    put(x, 2, z, "lantern[hanging=false]");
                }
                for (int y = 3; y <= 5; y++) {
                    put(x, y, z, "barrier");
                }
            }
        }
    }

    private void paths() {
        for (int z = PORTAL_Z + 2; z <= SPAWN_Z - 4; z++) {
            for (int x = -2; x <= 2; x++) {
                String block = Math.abs(x) == 2 ? "polished_andesite" : (x == 0 && Math.floorMod(z, 4) == 0 ? c("concrete") : "smooth_stone");
                put(x, 0, z, block);
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) <= 2) {
                    continue;
                }
                put(x, 0, z, Math.abs(z) == 1 ? "polished_andesite" : "smooth_stone");
            }
        }
    }

    private void spawnPlaza() {
        for (int x = -6; x <= 6; x++) {
            for (int z = SPAWN_Z - 6; z <= SPAWN_Z + 6; z++) {
                double d = dist(x, z - SPAWN_Z);
                if (d > 5.6) {
                    continue;
                }
                String block;
                if (d < 1.2) {
                    block = "sea_lantern";
                } else if (d < 2.4) {
                    block = c("concrete");
                } else if (d > 4.6) {
                    block = "polished_andesite";
                } else {
                    block = Math.floorMod(x + z, 2) == 0 ? "smooth_quartz" : "calcite";
                }
                put(x, 0, z, block);
            }
        }
    }

    private void portal() {
        for (int x = -6; x <= 6; x++) {
            for (int z = PORTAL_Z - 3; z <= PORTAL_Z + 3; z++) {
                put(x, 0, z, Math.abs(x) >= 5 || Math.abs(z - PORTAL_Z) >= 3 ? "polished_andesite" : "smooth_quartz");
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                boolean frame = x == -3 || x == 3 || y == 0 || y == 6;
                put(x, y, PORTAL_Z, frame ? "obsidian" : "nether_portal[axis=x]");
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 0; y <= 7; y++) {
                put(side * 4, y, PORTAL_Z, y == 7 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
                put(side * 4, y, PORTAL_Z - 1, y % 2 == 0 ? c("concrete") : "quartz_bricks");
            }
            put(side * 4, 8, PORTAL_Z, "lantern[hanging=false]");
            put(side * 5, 1, PORTAL_Z + 1, "stone_brick_wall[up=true]");
            put(side * 5, 2, PORTAL_Z + 1, "lantern[hanging=false]");
            put(side * 4, 5, PORTAL_Z + 1, c("wall_banner") + "[facing=south]");
        }
        for (int x = -3; x <= 3; x++) {
            put(x, 7, PORTAL_Z, Math.abs(x) == 3 ? "quartz_bricks" : c("concrete"));
            put(x, 8, PORTAL_Z, x == 0 ? "sea_lantern" : "quartz_slab[type=bottom]");
        }
    }

    private void pedestals() {
        int[][] spots = {{6, SPAWN_Z - 3}, {-6, SPAWN_Z - 3}};
        for (int[] spot : spots) {
            put(spot[0], 1, spot[1], "chiseled_stone_bricks");
            put(spot[0], 2, spot[1], c("carpet"));
        }
        int[][] boards = {{-13, 0}, {13, 0}, {-10, -7}, {10, -7}};
        for (int[] board : boards) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(board[0] + dx, 0, board[1] + dz, "polished_andesite");
                }
            }
            put(board[0], 1, board[1], "chiseled_stone_bricks");
            put(board[0], 2, board[1], "stone_brick_slab[type=bottom]");
        }
    }

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
        for (int z = PORTAL_Z + 4; z <= SPAWN_Z - 6; z += 5) {
            for (int side = -1; side <= 1; side += 2) {
                put(side * 3, 1, z, "spruce_fence");
                put(side * 3, 2, z, "spruce_fence");
                put(side * 3, 3, z, "lantern[hanging=false]");
            }
        }
    }
}
