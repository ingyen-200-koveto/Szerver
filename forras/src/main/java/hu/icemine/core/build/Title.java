package hu.icemine.core.build;

import java.util.Locale;

public final class Title extends Build {

    private static final int DEPTH = 3;

    private final String[][] word;
    private final String frontA;
    private final String frontB;
    private final int split;
    private final String outlineBlock;
    private final String middleBlock;

    private final int scale;
    private final int rightX;
    private final int rightZ;
    private final int backX;
    private final int backZ;
    private final int width;
    private final int rows;

    public Title(Sink sink, int x, int y, int z, int scale, String facing) {
        this(sink, x, y, z, scale, facing, "ICEMINE", "light_blue_concrete", "white_concrete", 3, "blue_terracotta", "light_blue_terracotta");
    }

    public Title(Sink sink, int x, int y, int z, int scale, String facing, String text, String frontA, String frontB, int split,
                 String outline, String middle) {
        super(sink, x, y, z);
        this.word = BlockFont.word(text);
        this.frontA = frontA;
        this.frontB = frontB;
        this.split = split;
        this.outlineBlock = outline;
        this.middleBlock = middle;
        this.scale = Math.max(1, Math.min(4, scale));
        int[] face = facing(facing);
        this.backX = -face[0];
        this.backZ = -face[1];
        this.rightX = -backZ;
        this.rightZ = backX;
        this.width = Math.max(1, word.length * 5 * this.scale + (word.length - 1) * 2);
        this.rows = 7 * this.scale;
    }

    public static boolean validFacing(String facing) {
        String value = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return value.equals("south") || value.equals("north") || value.equals("east") || value.equals("west");
    }

    private static int[] facing(String facing) {
        switch (facing == null ? "" : facing.toLowerCase(Locale.ROOT)) {
            case "north":
                return new int[]{0, -1};
            case "east":
                return new int[]{1, 0};
            case "west":
                return new int[]{-1, 0};
            default:
                return new int[]{0, 1};
        }
    }

    public int[] box() {
        int[] a = world(0, 0, 0);
        int[] b = world(width + 1, rows + 1, DEPTH - 1);
        return new int[]{Math.min(a[0], b[0]), a[1], Math.min(a[2], b[2]), Math.max(a[0], b[0]), b[1], Math.max(a[2], b[2])};
    }

    private int[] world(int gx, int up, int depth) {
        int across = gx - (width + 2) / 2;
        return new int[]{cx + across * rightX + depth * backX, cy + up, cz + across * rightZ + depth * backZ};
    }

    private void place(int gx, int up, int depth, String data) {
        int across = gx - (width + 2) / 2;
        put(across * rightX + depth * backX, up, across * rightZ + depth * backZ, data);
    }

    @Override
    public void build() {
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
                int up = rows + 1 - gy;
                boolean first = gx - 1 < split * (5 * scale + 2);
                String face;
                if (outline) {
                    face = outlineBlock;
                } else if (first) {
                    face = frontA;
                } else {
                    face = frontB;
                }
                place(gx, up, 0, face);
                place(gx, up, 1, filled ? middleBlock : outlineBlock);
                place(gx, up, 2, outlineBlock);
            }
        }
    }
}
