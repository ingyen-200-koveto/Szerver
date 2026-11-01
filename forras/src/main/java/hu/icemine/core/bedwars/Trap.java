package hu.icemine.core.bedwars;

/** A csapdák. A csapat legfeljebb max-traps darabot vehet, sorban sülnek el, amikor ellenség lép a bázisra. */
public enum Trap {
    /** Vakság és lassítás a betolakodónak. */
    VAKITO("TRIPWIRE_HOOK"),
    /** Gyorsaság és ugrás a csapatnak, hogy gyorsan visszaérjenek. */
    ELLENTAMADAS("FEATHER"),
    /** Leveszi a láthatatlanságot, és ragyogni kezd a betolakodó. */
    RIASZTO("REDSTONE_TORCH"),
    /** Bányász fáradtság a betolakodónak, nehezebben bontja az ágy védelmét. */
    BANYASZ("IRON_PICKAXE");

    public final String icon;

    Trap(String icon) {
        this.icon = icon;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A következő csapda ára gyémántban: az első 1, a második 2, a harmadik 4... */
    public static int price(int owned) {
        return 1 << Math.max(0, Math.min(5, owned));
    }
}
