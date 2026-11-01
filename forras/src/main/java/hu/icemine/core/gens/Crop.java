package hu.icemine.core.gens;

import java.util.Locale;

/**
 * A Gens földek növényei. Csak szövegként írja le a blokkokat, így szerver nélkül is tesztelhető.
 * A dinnye és a tök fűre kerül (szár nélkül, egész blokként nő vissza), mert termőföld fölött a teli blokk földdé tapossa.
 */
public enum Crop {
    WHEAT("wheat", 7, "WHEAT", "farmland[moisture=7]"),
    CARROTS("carrots", 7, "CARROT", "farmland[moisture=7]"),
    POTATOES("potatoes", 7, "POTATO", "farmland[moisture=7]"),
    BEETROOTS("beetroots", 3, "BEETROOT", "farmland[moisture=7]"),
    MELON("melon", 0, "MELON_SLICE", "grass_block"),
    PUMPKIN("pumpkin", 0, "PUMPKIN", "grass_block"),
    SWEET_BERRY_BUSH("sweet_berry_bush", 3, "SWEET_BERRIES", "grass_block"),
    NETHER_WART("nether_wart", 3, "NETHER_WART", "soul_sand");

    public final String block;
    public final int maxAge;
    public final String drop;
    public final String soil;

    Crop(String block, int maxAge, String drop, String soil) {
        this.block = block;
        this.maxAge = maxAge;
        this.drop = drop;
        this.soil = soil;
    }

    /** Teljes blokk (dinnye, tök): aratás után eltűnik, és újra megjelenik. */
    public boolean wholeBlock() {
        return maxAge == 0;
    }

    public String mature() {
        return wholeBlock() ? block : block + "[age=" + maxAge + "]";
    }

    public String young() {
        return wholeBlock() ? "air" : block + "[age=0]";
    }

    /** Félig visszanőtt állapot. */
    public String half() {
        return wholeBlock() ? "air" : block + "[age=" + Math.max(1, maxAge / 2) + "]";
    }

    public static Crop of(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
