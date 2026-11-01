package hu.icemine.kingdoms;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Két királyság kapcsolata. A szövetség és a fegyverszünet kölcsönös (kérés és elfogadás kell hozzá), az ellenségeskedés
 * egyoldalú: ha valamelyik fél ellenségnek nyilvánítja a másikat, háború van. Egy nemzet királyságai egymásnak NATION.
 */
public enum Relation {
    SELF("&a", "Saját", true, true, true, true, true, false),
    NATION("&b", "Nemzettárs", false, true, true, true, true, false),
    ALLY("&d", "Szövetséges", false, true, true, true, true, false),
    TRUCE("&e", "Fegyverszünet", false, false, false, false, true, false),
    NEUTRAL("&f", "Semleges", false, false, false, false, false, false),
    ENEMY("&c", "Ellenség", false, false, false, false, false, true);

    public String color;
    public String title;
    /** Építhet-e a másik földjén. */
    public boolean build;
    /** Használhat-e ajtót, kaput, kapcsolót. */
    public boolean interact;
    /** Teleportálhat-e a másik otthonába. */
    public boolean home;
    /** A tornyok nem lőnek rá. */
    public boolean turretCeasefire;
    /** Nem sebezhetik egymást. */
    public boolean ceasefire;
    /** Indíthat-e inváziót a másik ellen. */
    public boolean invade;

    Relation(String color, String title, boolean build, boolean interact, boolean home, boolean turretCeasefire, boolean ceasefire, boolean invade) {
        this.color = color;
        this.title = title;
        this.build = build;
        this.interact = interact;
        this.home = home;
        this.turretCeasefire = turretCeasefire;
        this.ceasefire = ceasefire;
        this.invade = invade;
    }

    public String display() {
        return color + title;
    }

    /** A kérhető (kölcsönös) kapcsolat. */
    public boolean mutual() {
        return this == ALLY || this == TRUCE;
    }

    public static Relation of(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * A tényleges kapcsolat abból, amit az egyik és a másik fél beállított: ha bármelyik ellenséges, háború van;
     * a szövetség és a fegyverszünet csak akkor él, ha mindkét fél ugyanazt választotta.
     */
    public static Relation resolve(Relation mine, Relation theirs) {
        Relation a = mine == null ? NEUTRAL : mine;
        Relation b = theirs == null ? NEUTRAL : theirs;
        if (a == ENEMY || b == ENEMY) {
            return ENEMY;
        }
        if (a == b && a.mutual()) {
            return a;
        }
        return NEUTRAL;
    }

    /** A kingdoms.yml "relations" része felülírhatja a jogokat és a színeket. */
    public static void load(ConfigurationSection section) {
        for (Relation relation : values()) {
            String key = relation.name().toLowerCase(Locale.ROOT);
            ConfigurationSection entry = section == null || !section.contains(key, true) ? null : section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            relation.color = entry.getString("color", relation.color);
            relation.title = entry.getString("name", relation.title);
            if (relation == SELF) {
                continue;
            }
            relation.build = entry.getBoolean("build", relation.build);
            relation.interact = entry.getBoolean("interact", relation.interact);
            relation.home = entry.getBoolean("home", relation.home);
            relation.turretCeasefire = entry.getBoolean("turret-ceasefire", relation.turretCeasefire);
            relation.ceasefire = entry.getBoolean("ceasefire", relation.ceasefire);
            relation.invade = entry.getBoolean("invade", relation.invade);
        }
    }
}
