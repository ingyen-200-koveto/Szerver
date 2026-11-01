package hu.icemine.kingdoms;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A királysági rangok jogai. A király mindent megtehet, a többi rangnál a rangok menüben kapcsolhatók. */
public enum KPerm {
    BUILD("Építés és bontás", "BRICKS"),
    INTERACT("Ajtók, kapuk, kapcsolók", "OAK_DOOR"),
    USE("Ládák, kemencék, munkapadok", "CHEST"),
    NEXUS("A Nexus megnyitása", "BEACON"),
    NEXUS_BUILD("Építés a Nexus földjén", "END_STONE_BRICKS"),
    CLAIM("Földfoglalás", "GRASS_BLOCK"),
    UNCLAIM("Föld elengedése", "DEAD_BUSH"),
    INVITE("Játékosok meghívása", "WRITABLE_BOOK"),
    KICK("Tagok kirúgása", "IRON_DOOR"),
    SET_RANKS("Tagok rangjának állítása", "GOLDEN_HELMET"),
    MANAGE_RANKS("Rangok szerkesztése", "NAME_TAG"),
    HOME("Teleport az otthonba", "RED_BED"),
    SET_HOME("Otthon beállítása", "COMPASS"),
    WITHDRAW("Pénz kivétele a bankból", "GOLD_INGOT"),
    UPGRADE("Fejlesztések vásárlása", "ANVIL"),
    STRUCTURES("Építmények kezelése", "LODESTONE"),
    TURRETS("Tornyok kezelése", "DISPENSER"),
    RELATIONS("Kapcsolatok más királyságokkal", "WHITE_BANNER"),
    INVADE("Invázió indítása", "IRON_SWORD"),
    NATION("Nemzet ügyei", "SHIELD"),
    SETTINGS("Királyság beállításai", "COMPARATOR"),
    BROADCAST("Körüzenet a tagoknak", "BELL");

    public final String title;
    public final String icon;

    KPerm(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    public static KPerm of(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** A sima tagoknak alapból járó jogok. */
    public static Set<KPerm> basic() {
        return EnumSet.of(BUILD, INTERACT, USE, NEXUS, HOME, INVADE);
    }
}
