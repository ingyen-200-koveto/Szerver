package hu.icemine.core.bedwars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.TextDisplay;

/** Egy nyersanyag generátor (csapat vas/arany, vagy közös gyémánt/smaragd). */
public final class Generator {

    public enum Type {
        IRON("IRON_INGOT", 48),
        GOLD("GOLD_INGOT", 12),
        DIAMOND("DIAMOND", 4),
        EMERALD("EMERALD", 2);

        private final String material;
        /** Ennél több nem gyűlhet össze a generátoron. */
        public final int max;

        Type(String material, int max) {
            this.material = material;
            this.max = max;
        }

        public Material item() {
            return Material.valueOf(material);
        }
    }

    public final Type type;
    public final Location location;
    /** A csapat, akié a generátor, vagy null, ha közös. */
    public final BwTeam team;
    public int tier = 1;
    public int timer;
    public int emeraldTimer;
    public TextDisplay label;
    public ArmorStand spinner;
    public float spin;

    public Generator(Type type, Location location, BwTeam team) {
        this.type = type;
        this.location = location;
        this.team = team;
    }

    /**
     * Hány tick két termelés között.
     * @param speed a csapat kovácsműhely szorzója és a mód generátor sebessége összeszorozva
     */
    public static int interval(Type type, int tier, double speed, int[] diamond, int[] emerald, int iron, int gold) {
        double base;
        switch (type) {
            case IRON:
                base = iron;
                break;
            case GOLD:
                base = gold;
                break;
            case DIAMOND:
                base = diamond[Math.max(0, Math.min(diamond.length - 1, tier - 1))];
                break;
            default:
                base = emerald[Math.max(0, Math.min(emerald.length - 1, tier - 1))];
        }
        return Math.max(2, (int) Math.round(base / Math.max(0.1, speed)));
    }
}
