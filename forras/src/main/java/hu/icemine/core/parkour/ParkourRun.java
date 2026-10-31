package hu.icemine.core.parkour;

public final class ParkourRun {

    public enum Reach {
        OK,
        ALREADY,
        SKIPPED
    }

    private final long startNanos;
    private final int total;
    private int next;
    private int last = -1;
    private int falls;

    public ParkourRun(long startNanos, int total) {
        this.startNanos = startNanos;
        this.total = Math.max(0, total);
    }

    public Reach reach(int index) {
        if (index < next) {
            return Reach.ALREADY;
        }
        if (index > next) {
            return Reach.SKIPPED;
        }
        next++;
        last = index;
        return Reach.OK;
    }

    public boolean complete() {
        return next >= total;
    }

    public int last() {
        return last;
    }

    public int reached() {
        return next;
    }

    public int total() {
        return total;
    }

    public long elapsed(long nowNanos) {
        return Math.max(0L, (nowNanos - startNanos) / 1_000_000L);
    }

    public void fell() {
        falls++;
    }

    public int falls() {
        return falls;
    }

    public static boolean plausible(long millis, long minimum) {
        return millis >= minimum;
    }
}
