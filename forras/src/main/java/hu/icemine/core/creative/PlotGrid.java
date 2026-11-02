package hu.icemine.core.creative;

/**
 * A telkek elrendezése: négyzetrács, minden cella egy út (road széles) és utána egy telek (size × size).
 * Az út a cella elején van, így a (0, 0) körül út fut. A telek körül egy blokkal kijjebb, az úton van a szegély.
 */
final class PlotGrid {

    static final long NONE = Long.MIN_VALUE;

    final int size;
    final int road;
    final int cell;
    final int height;

    PlotGrid(int size, int road, int height) {
        this.size = size;
        this.road = road;
        this.cell = size + road;
        this.height = height;
    }

    static long key(int px, int pz) {
        return ((long) px << 32) | (pz & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    /** A telek kulcsa, amin ez a blokk van, vagy NONE, ha úton. */
    long plotAt(int x, int z) {
        if (Math.floorMod(x, cell) < road || Math.floorMod(z, cell) < road) {
            return NONE;
        }
        return key(Math.floorDiv(x, cell), Math.floorDiv(z, cell));
    }

    int minX(int px) {
        return px * cell + road;
    }

    int minZ(int pz) {
        return pz * cell + road;
    }

    /** A telek szegélye: a telek körüli egy blokkos keret (az úton). */
    boolean ring(int x, int z) {
        int lx = Math.floorMod(x, cell);
        int lz = Math.floorMod(z, cell);
        boolean edgeX = lx == road - 1 || lx == 0;
        boolean edgeZ = lz == road - 1 || lz == 0;
        boolean inX = lx >= road;
        boolean inZ = lz >= road;
        return edgeX && (inZ || edgeZ) || edgeZ && (inX || edgeX);
    }

    /** A szegély blokkjai egy telek körül (x, z párok). */
    int[][] ringOf(int px, int pz) {
        int x0 = minX(px) - 1;
        int z0 = minZ(pz) - 1;
        int side = size + 2;
        int[][] result = new int[(side - 1) * 4][];
        int i = 0;
        for (int d = 0; d < side - 1; d++) {
            result[i++] = new int[]{x0 + d, z0};
            result[i++] = new int[]{x0 + side - 1, z0 + d};
            result[i++] = new int[]{x0 + side - 1 - d, z0 + side - 1};
            result[i++] = new int[]{x0, z0 + side - 1 - d};
        }
        return result;
    }

    /** Négyzetes spirál a (0, 0) körül: 0 → (0, 0), 1 → (1, 0), 2 → (1, 1)... */
    static int[] spiral(int index) {
        if (index <= 0) {
            return new int[]{0, 0};
        }
        int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        int side = ring * 2;
        int max = (side + 1) * (side + 1) - 1;
        int offset = max - index;
        if (offset < side) {
            return new int[]{ring - offset, -ring};
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-ring, -ring + offset};
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-ring + offset, ring};
        }
        offset -= side;
        return new int[]{ring, ring - offset};
    }
}
