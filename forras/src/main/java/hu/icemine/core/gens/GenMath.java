package hu.icemine.core.gens;

public final class GenMath {

    private GenMath() {
    }

    public static long produce(long stored, long capacity, int amount, double multiplier) {
        if (stored >= capacity) {
            return stored;
        }
        long added = Math.max(1L, Math.round(amount * Math.max(0.0, multiplier)));
        return Math.min(capacity, stored + added);
    }

    public static int[] spiral(int index) {
        if (index <= 0) {
            return new int[]{0, 0};
        }
        int layer = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        int side = layer * 2;
        int max = (side + 1) * (side + 1) - 1;
        int offset = max - index;
        if (offset < side) {
            return new int[]{layer - offset, layer};
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-layer, layer - offset};
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-layer + offset, -layer};
        }
        offset -= side;
        return new int[]{layer, -layer + offset};
    }

    public static long sellValue(long count, long unit) {
        if (count <= 0 || unit <= 0) {
            return 0;
        }
        if (count > Long.MAX_VALUE / unit) {
            return Long.MAX_VALUE;
        }
        return count * unit;
    }
}
