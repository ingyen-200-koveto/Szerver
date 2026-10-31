package hu.icemine.core.combo;

/**
 * ComboFly lökés. A cél nem a nagy hátralökés, hanem hogy az ellenfél felfelé repüljön és ütőtávban maradjon, így hosszú,
 * levegős kombókat lehet csinálni. Öt külön állítható rész van (combofly.yml):
 * <ul>
 *     <li>horizontal / vertical: az alap oldal- és függőleges lökés</li>
 *     <li>air: szorzó, ha az ellenfél már a levegőben van (kisebb oldallökés = közel marad)</li>
 *     <li>ground: szorzó, ha a földön áll (az első ütés emelje fel rendesen)</li>
 *     <li>combo: kombónként ennyivel változik az oldal- és a függőleges lökés</li>
 * </ul>
 * A max-height fölött már nem lök felfelé, hogy senki ne repüljön el a pályáról.
 */
public final class Knockback {

    /** Ennél közelebb a talajhoz (blokk) földön állónak számít. */
    public static final double GROUND_EPSILON = 0.15;

    public static final class Settings {
        public final double horizontal;
        public final double vertical;
        /** Függőleges változás kombónként (régi neve: combo-scaling). */
        public final double scaling;
        public final double maxHorizontal;
        public final double maxVertical;
        public final double maxHeight;
        public final double airHorizontal;
        public final double airVertical;
        public final double groundHorizontal;
        public final double groundVertical;
        /** Oldalirányú változás kombónként; negatív érték = minél hosszabb a kombó, annál közelebb marad az ellenfél. */
        public final double comboHorizontal;

        public Settings(double horizontal, double vertical, double scaling, double maxHorizontal, double maxVertical, double maxHeight) {
            this(horizontal, vertical, scaling, maxHorizontal, maxVertical, maxHeight, 1.0, 1.0, 1.0, 1.0, 0.0);
        }

        public Settings(double horizontal, double vertical, double scaling, double maxHorizontal, double maxVertical, double maxHeight,
                double airHorizontal, double airVertical, double groundHorizontal, double groundVertical, double comboHorizontal) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.scaling = scaling;
            this.maxHorizontal = maxHorizontal;
            this.maxVertical = maxVertical;
            this.maxHeight = maxHeight;
            this.airHorizontal = airHorizontal;
            this.airVertical = airVertical;
            this.groundHorizontal = groundHorizontal;
            this.groundVertical = groundVertical;
            this.comboHorizontal = comboHorizontal;
        }
    }

    private Knockback() {
    }

    /**
     * @param dx, dz            az ütött és az ütő vízszintes távolsága (az ütőtől elfelé lökünk)
     * @param yawRadians        az ütő nézési iránya, ha pont egymásban állnak
     * @param combo             hányadik találat a kombóban (1 = első)
     * @param heightAboveGround az ütött milyen magasan van a talaj fölött
     * @return {x, y, z} sebesség
     */
    public static double[] compute(double dx, double dz, double yawRadians, int combo, Settings settings, double heightAboveGround) {
        double length = Math.sqrt(dx * dx + dz * dz);
        double nx;
        double nz;
        if (length < 1.0E-4) {
            nx = -Math.sin(yawRadians);
            nz = Math.cos(yawRadians);
        } else {
            nx = dx / length;
            nz = dz / length;
        }
        boolean onGround = heightAboveGround < GROUND_EPSILON;
        int extra = Math.max(0, combo - 1);
        double horizontal = settings.horizontal * (onGround ? settings.groundHorizontal : settings.airHorizontal) + extra * settings.comboHorizontal;
        horizontal = Math.max(0.0, Math.min(settings.maxHorizontal, horizontal));
        double vertical = settings.vertical * (onGround ? settings.groundVertical : settings.airVertical) + extra * settings.scaling;
        vertical = Math.max(-settings.maxVertical, Math.min(settings.maxVertical, vertical));
        if (heightAboveGround > settings.maxHeight) {
            vertical = Math.min(vertical, 0.0);
        } else if (heightAboveGround > settings.maxHeight * 0.6) {
            vertical *= 0.5;
        }
        return new double[]{nx * horizontal, vertical, nz * horizontal};
    }
}
