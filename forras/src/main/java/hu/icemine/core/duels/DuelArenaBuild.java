package hu.icemine.core.duels;

import hu.icemine.core.build.Build;
import hu.icemine.core.build.Layout;

public final class DuelArenaBuild extends Build {
    public static final int HALF = 24;
    public static final int WALL = 6;
    public static final int CEILING = 24;
    public static final int DEPTH = 12;
    private final String type;
    private final String theme;

    public DuelArenaBuild(Build.Sink build$sink, int i, int j, int k, String s, String s1) {
        super(build$sink, i, j, k);
        this.type = s == null ? "classic" : s;
        this.theme = s1 == null ? "forest" : s1;
    }

    @Override
    public void build() {
        switch (this.type) {
            case "sumo":
                this.sumo();
                break;
            case "bridge":
                this.bridge();
                break;
            case "builduhc":
                this.uhc();
                break;
            case "crystal":
                this.crystal();
                break;
            default:
                this.classic();
        }
    }

    private String floor(int i, int j) {
        double d0 = noise(i, j, 301);
        double d1 = smooth(i, j, 6.0, 302);
        switch (this.theme) {
            case "desert":
                return d1 > 0.7 ? "cut_sandstone" : (d0 < 0.6 ? "smooth_sandstone" : (d0 < 0.85 ? "sandstone" : "chiseled_sandstone"));
            case "ice":
                return d1 > 0.72 ? "light_blue_concrete_powder" : (d0 < 0.8 ? "snow_block" : "white_concrete_powder");
            case "nether":
                return d1 > 0.7 ? "crimson_nylium" : (d0 < 0.6 ? "nether_bricks" : (d0 < 0.85 ? "polished_blackstone_bricks" : "cracked_nether_bricks"));
            default:
                return d1 > 0.72 ? "moss_block" : (d0 < 0.85 ? "grass_block" : "coarse_dirt");
        }
    }

    private String wall(int i, int j, int k) {
        double d0 = noise(i + j, k - j, 303);
        switch (this.theme) {
            case "desert":
                return j == 5 ? "chiseled_sandstone" : (d0 < 0.6 ? "sandstone" : "cut_sandstone");
            case "ice":
                return j == 5 ? "snow_block" : (d0 < 0.55 ? "packed_ice" : "stone_bricks");
            case "nether":
                return j == 5 ? "chiseled_polished_blackstone" : (d0 < 0.6 ? "nether_bricks" : "polished_blackstone_bricks");
            default:
                return j == 5 ? "mossy_stone_bricks" : (d0 < 0.6 ? "stone_bricks" : (d0 < 0.85 ? "mossy_stone_bricks" : "cracked_stone_bricks"));
        }
    }

    private String under(int i, int j, int k) {
        double d0 = noise(i + j, k + j, 304);
        if (this.theme.equals("desert")) {
            return d0 < 0.7 ? "sandstone" : "terracotta";
        } else if (this.theme.equals("nether")) {
            return d0 < 0.7 ? "netherrack" : "blackstone";
        } else {
            return d0 < 0.6 ? "stone" : (d0 < 0.85 ? "andesite" : "dirt");
        }
    }

    private void box(int i, int j) {
        for (int k = -i - 1; k <= i + 1; k++) {
            for (int l = -i - 1; l <= i + 1; l++) {
                boolean flag = Math.abs(k) == i + 1 || Math.abs(l) == i + 1;

                for (int i1 = -3; i1 < 0; i1++) {
                    this.put(k, i1, l, this.under(k, i1, l));
                }

                if (!flag) {
                    this.put(k, j, l, "barrier");
                } else {
                    this.put(k, 0, l, this.wall(k, 0, l));

                    for (int l1 = 1; l1 < 6; l1++) {
                        this.put(k, l1, l, this.wall(k, l1, l));
                    }

                    for (int i2 = 6; i2 <= j; i2++) {
                        this.put(k, i2, l, "barrier");
                    }
                }
            }
        }

        for (byte b0 = -1; b0 <= 1; b0 += 2) {
            for (byte b1 = -1; b1 <= 1; b1 += 2) {
                int k1 = b0 * i;
                int j2 = b1 * i;

                for (int j1 = 1; j1 <= 6; j1++) {
                    this.put(k1, j1, j2, this.wall(k1, 5, j2));
                }

                this.put(k1, 7, j2, "lantern[hanging=false]");
            }
        }

        this.layout.areas.put("bounds", new Layout.Area(-i, -12, -i, i, j - 1, i));
    }

    private void spawns(double d0) {
        this.layout.point("spawn:red", 0.5, 1.0, -d0 + 0.5, 0.0);
        this.layout.point("spawn:blue", 0.5, 1.0, d0 + 0.5, 180.0);
    }

    private void classic() {
        for (int i = -24; i <= 24; i++) {
            for (int j = -24; j <= 24; j++) {
                this.put(i, 0, j, this.floor(i, j));
            }
        }

        this.box(24, 24);
        int[][] aint1 = new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};

        for (int[] aint : aint1) {
            for (int k = 1; k <= 3; k++) {
                this.put(aint[0], k, aint[1], this.wall(aint[0], k, aint[1]));
            }

            this.put(aint[0], 4, aint[1], "lantern[hanging=false]");
        }

        this.spawns(16.0);
    }

    private void sumo() {
        double d0 = 7.5;

        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                double d1 = dist(i, j);
                if (d1 <= d0) {
                    this.put(
                        i,
                        0,
                        j,
                        d1 > d0 - 1.0 ? "white_concrete" : (d1 < 1.5 ? "red_concrete" : (Math.floorMod(i + j, 2) == 0 ? "smooth_stone" : "polished_andesite"))
                    );
                    int k = 1 + (int)Math.round((1.0 - d1 / d0) * 7.0);

                    for (int l = -k; l < 0; l++) {
                        this.put(i, l, j, this.under(i, l, j));
                    }
                }
            }
        }

        for (int i1 = -16; i1 <= 16; i1++) {
            for (int j1 = -16; j1 <= 16; j1++) {
                double d2 = dist(i1, j1);
                if (d2 > d0 + 3.0 && d2 <= 16.0) {
                    this.put(i1, -6, j1, "water");
                    this.put(i1, -7, j1, "sand");
                }

                if (d2 > 15.0 && d2 <= 16.2) {
                    for (int k1 = -6; k1 <= 2; k1++) {
                        this.put(i1, k1, j1, k1 == 2 ? "stone_brick_wall[up=true]" : "stone_bricks");
                    }
                }
            }
        }

        this.layout.areas.put("bounds", new Layout.Area(-16, -12, -16, 16, 20, 16));
        this.layout.point("spawn:red", 0.5, 1.0, -3.5, 0.0);
        this.layout.point("spawn:blue", 0.5, 1.0, 4.5, 180.0);
        this.layout.point("fall", 0.0, -2.0, 0.0, 0.0);
    }

    private void island(int i, String s) {
        for (int j = -6; j <= 6; j++) {
            for (int k = -6; k <= 6; k++) {
                int l = i + k;
                boolean flag = Math.abs(j) == 6 || Math.abs(k) == 6;
                this.put(j, 0, l, flag ? s + "_terracotta" : (noise(j, l, 305) < 0.8 ? "stone_bricks" : "cracked_stone_bricks"));
                int i1 = flag ? 2 : 4 + (int)(noise(j, l, 306) * 3.0);

                for (int j1 = -i1; j1 < 0; j1++) {
                    this.put(j, j1, l, this.under(j, j1, l));
                }
            }
        }
    }

    private void bridge() {
        this.island(-27, "blue");
        this.island(27, "red");

        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                this.put(i, 0, j, Math.abs(i) != 4 && Math.abs(j) != 4 ? "smooth_stone" : "white_terracotta");

                for (int k = -3; k < 0; k++) {
                    this.put(i, k, j, this.under(i, k, j));
                }
            }
        }

        for (int i1 = -21; i1 <= 21; i1++) {
            if (Math.abs(i1) > 4) {
                this.put(0, 0, i1, "white_terracotta");
            }
        }

        for (byte b0 = -1; b0 <= 1; b0 += 2) {
            int j1 = b0 * 31;

            for (int k1 = -2; k1 <= 2; k1++) {
                for (int l = -1; l <= 1; l++) {
                    this.put(k1, 0, j1 + l, "end_portal");
                    this.put(k1, -1, j1 + l, "bedrock");
                }
            }
        }

        for (byte b1 = -1; b1 <= 1; b1 += 2) {
            for (byte b2 = -1; b2 <= 1; b2 += 2) {
                this.put(b2 * 3, 1, b1 * 27, "stone_brick_wall[up=true]");
                this.put(b2 * 3, 2, b1 * 27, "lantern[hanging=false]");
            }
        }

        this.layout.areas.put("bounds", new Layout.Area(-20, -12, -36, 20, 30, 36));
        this.layout.areas.put("goal:blue", new Layout.Area(-2, -1, -32, 2, 1, -30));
        this.layout.areas.put("goal:red", new Layout.Area(-2, -1, 30, 2, 1, 32));
        this.layout.point("spawn:blue", 0.5, 1.0, -25.5, 0.0);
        this.layout.point("spawn:red", 0.5, 1.0, 26.5, 180.0);
        this.layout.point("fall", 0.0, -8.0, 0.0, 0.0);
    }

    private void uhc() {
        for (int i = -24; i <= 24; i++) {
            for (int j = -24; j <= 24; j++) {
                int k = this.uhcTop(i, j);
                boolean flag = dist(i - 8, j + 6) < 4.2;
                if (flag) {
                    k = 0;
                }

                for (int l = 0; l <= k; l++) {
                    this.put(i, l, j, l == k ? (flag ? "water" : "grass_block") : "dirt");
                }

                if (flag) {
                    this.put(i, -1, j, "sand");
                }
            }
        }

        this.box(24, 28);
        int[][] aint = new int[][]{{-12, 10}, {13, -12}, {-15, -14}, {16, 12}};

        for (int[] aint1 : aint) {
            int i1 = this.uhcTop(aint1[0], aint1[1]) + 1;

            for (int j1 = 0; j1 < 5; j1++) {
                this.put(aint1[0], i1 + j1, aint1[1], "oak_log[axis=y]");
            }

            for (int i2 = -2; i2 <= 2; i2++) {
                for (int k1 = -2; k1 <= 2; k1++) {
                    for (int l1 = 3; l1 <= 5; l1++) {
                        if ((i2 != 0 || k1 != 0 || l1 == 5) && (Math.abs(i2) != 2 || Math.abs(k1) != 2)) {
                            this.put(aint1[0] + i2, i1 + l1, aint1[1] + k1, "oak_leaves[persistent=true]");
                        }
                    }
                }
            }
        }

        this.layout.point("spawn:red", 0.5, this.uhcTop(0, -16) + 1, -15.5, 0.0);
        this.layout.point("spawn:blue", 0.5, this.uhcTop(0, 16) + 1, 16.5, 180.0);
    }

    private int uhcTop(int i, int j) {
        if (dist(i - 8, j + 6) < 4.2) {
            return 0;
        }

        double d0 = smooth(i, j, 9.0, 307) * 4.0 + smooth(i, j, 4.0, 308) * 1.5;
        return (int)Math.max(0L, Math.round(d0 - 1.5));
    }

    private void crystal() {
        for (int i = -24; i <= 24; i++) {
            for (int j = -24; j <= 24; j++) {
                double d0 = noise(i, j, 309);
                this.put(i, 0, j, d0 < 0.12 ? "obsidian" : (d0 < 0.6 ? "polished_blackstone" : "blackstone"));
                this.put(i, -1, j, "bedrock");
            }
        }

        this.box(24, 24);
        this.spawns(14.0);
    }
}
