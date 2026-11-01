package hu.icemine.core.bedwars;

/** Blokk koordináták egy long-ba csomagolva (a lerakott blokkok listájához). */
public final class Keys {

    private Keys() {
    }

    public static long of(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int y(long key) {
        return (int) (key & 0xFFF) - 2048;
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }
}
