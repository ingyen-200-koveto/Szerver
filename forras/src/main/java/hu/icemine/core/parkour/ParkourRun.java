package hu.icemine.core.parkour;

public final class ParkourRun {

    public enum Step {
        NONE,
        NEXT,
        CHECKPOINT,
        FINISH
    }

    private final long startNanos;
    private final int length;
    private final int every;
    private int current = -1;
    private int checkpoint = -1;
    private int falls;

    public ParkourRun(long startNanos, int length, int every) {
        this.startNanos = startNanos;
        this.length = Math.max(1, length);
        this.every = Math.max(0, every);
    }

    public static boolean isCheckpoint(int index, int length, int every) {
        return every > 0 && index < length - 1 && (index + 1) % every == 0;
    }

    public static int checkpointCount(int length, int every) {
        return every > 0 ? (Math.max(1, length) - 1) / every : 0;
    }

    public Step land(int index) {
        if (index != current + 1 || index >= length) {
            return Step.NONE;
        }
        current = index;
        if (index == length - 1) {
            return Step.FINISH;
        }
        if (isCheckpoint(index, length, every)) {
            checkpoint = index;
            return Step.CHECKPOINT;
        }
        return Step.NEXT;
    }

    public void fell() {
        falls++;
        back();
    }

    public void back() {
        current = checkpoint;
    }

    public int current() {
        return current;
    }

    public int checkpoint() {
        return checkpoint;
    }

    public int checkpointsReached() {
        return every > 0 ? (checkpoint + 1) / every : 0;
    }

    public int checkpointTotal() {
        return checkpointCount(length, every);
    }

    public int length() {
        return length;
    }

    public long elapsed(long nowNanos) {
        return Math.max(0L, (nowNanos - startNanos) / 1_000_000L);
    }

    public int falls() {
        return falls;
    }

    public static boolean plausible(long millis, long minimum) {
        return millis >= minimum;
    }
}
