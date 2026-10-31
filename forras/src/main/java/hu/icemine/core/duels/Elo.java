package hu.icemine.core.duels;

public final class Elo {
    public static final int START = 1000;

    private Elo() {
    }

    public static double expected(int i, int j) {
        return 1.0 / (1.0 + Math.pow(10.0, (j - i) / 400.0));
    }

    public static int[] result(int i, int j, int k) {
        double d0 = expected(i, j);
        int l = (int)Math.round(k * (1.0 - d0));
        l = Math.max(1, l);
        int i1 = Math.max(100, j - l);
        return new int[]{i + l, i1, l};
    }

    public static int window(int i) {
        return Math.min(1000, 75 + i * 25);
    }

    public static boolean fits(int i, int j, int k, int l) {
        int i1 = Math.max(window(j), window(l));
        return Math.abs(i - k) <= i1;
    }

    public static String division(int i) {
        if (i >= 1800) {
            return "&5&lMester";
        } else if (i >= 1500) {
            return "&b&lGyémánt";
        } else if (i >= 1300) {
            return "&6&lArany";
        } else {
            return i >= 1100 ? "&f&lEzüst" : "&c&lBronz";
        }
    }
}
