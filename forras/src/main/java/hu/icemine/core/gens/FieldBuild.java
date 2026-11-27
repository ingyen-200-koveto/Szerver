package hu.icemine.core.gens;

import hu.icemine.core.build.Build;

/**
 * Egy föld kirakása a szigeten: feloldva talaj és beérett növények, lezárva durva föld és pár kerítés.
 * A koordináták a sziget közepéhez képest, a talaj y = 0.
 */
public final class FieldBuild extends Build {

    private final int field;
    private final Crop crop;
    private final boolean unlocked;
    private final boolean shared;

    public FieldBuild(Sink sink, int cx, int cy, int cz, int field, Crop crop, boolean unlocked) {
        this(sink, cx, cy, cz, field, crop, unlocked, false);
    }

    /** shared: a közös farm egyik nagy földje a központban. */
    public FieldBuild(Sink sink, int cx, int cy, int cz, int field, Crop crop, boolean unlocked, boolean shared) {
        super(sink, cx, cy, cz);
        this.field = field;
        this.crop = crop;
        this.unlocked = unlocked;
        this.shared = shared;
    }

    @Override
    public void build() {
        int[] c = shared ? SharedFarm.center(field) : FarmLayout.center(field);
        int half = FarmLayout.HALF;
        for (int[] cell : shared ? SharedFarm.cells(field) : FarmLayout.cells(field)) {
            boolean corner = !shared && Math.abs(cell[0] - c[0]) == half && Math.abs(cell[1] - c[1]) == half;
            if (unlocked) {
                put(cell[0], -1, cell[1], crop == Crop.NETHER_WART ? "soul_soil" : "dirt");
                put(cell[0], 0, cell[1], crop.soil);
                put(cell[0], 1, cell[1], crop.mature());
            } else {
                put(cell[0], -1, cell[1], "dirt");
                put(cell[0], 0, cell[1], noise(cell[0], cell[1], 700 + field) < 0.3 ? "rooted_dirt" : "coarse_dirt");
                put(cell[0], 1, cell[1], corner ? "spruce_fence" : "air");
            }
            put(cell[0], 2, cell[1], "air");
        }
    }
}
