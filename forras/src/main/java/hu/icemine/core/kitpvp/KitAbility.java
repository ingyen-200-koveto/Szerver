package hu.icemine.core.kitpvp;

import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Egy kit képessége (kitpvp.yml -> kits.&lt;kit&gt;.ability). A képességet a hotbaron lévő képesség tárggyal, jobb klikkel lehet
 * használni, utána a cooldown leteltéig nem lehet újra.
 *
 * <pre>
 * ability:
 *   type: freeze        # lásd Type
 *   name: "&bJégvihar"
 *   item: PACKED_ICE
 *   cooldown: 25        # másodperc
 *   power: 2            # típustól függ (erősség / lökés / szív / effekt szint)
 *   radius: 5           # hatótáv blokkban (területi és célzott képességeknél)
 *   duration: 3         # effekt hossza másodpercben
 *   damage: 0           # sebzés fél szívben
 *   description: [...]
 * </pre>
 */
public final class KitAbility {

    public enum Type {
        /** Előre lendül (power = erő). */
        DASH("Előrelendülés"),
        /** Felfelé és előre ugrik (power = erő). */
        LEAP("Ugrás"),
        /** Gyógyul (power = szív) és regenerációt kap (duration). */
        HEAL("Gyógyulás"),
        /** Erő és gyorsaság (power = erő szintje, duration). */
        RAGE("Düh"),
        /** Ellenállás (power = szint, duration). */
        SHIELD("Pajzs"),
        /** Láthatatlanság és gyorsaság (duration). */
        INVISIBLE("Láthatatlanság"),
        /** Nyílzápor (power = nyilak száma). */
        ARROWS("Nyílzápor"),
        /** Tűzgolyó (power = sebesség). */
        FIREBALL("Tűzgolyó"),
        /** A közeli ellenfelek lelassulnak (radius, power = lassítás szintje, duration, damage). */
        FREEZE("Fagyasztás"),
        /** Villám a megcélzott ellenfélre (radius = hatótáv, damage). */
        LIGHTNING("Villám"),
        /** Lökéshullám: a közeli ellenfeleket ellöki (radius, power = lökés, damage). */
        SHOCKWAVE("Lökéshullám"),
        /** A megcélzott ellenfelet magához húzza (radius = hatótáv, power = húzás). */
        PULL("Behúzás"),
        /** A közeli ellenfeleket megvakítja (radius, duration). */
        SMOKE("Füstbomba");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        public static Type parse(String text) {
            if (text == null) {
                return null;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        /** Kell-e hozzá ellenfél a közelben vagy a célkeresztben. */
        public boolean targeted() {
            return this == LIGHTNING || this == PULL;
        }

        public boolean area() {
            return this == FREEZE || this == SHOCKWAVE || this == SMOKE;
        }
    }

    public static final String TAG = "ability:";

    public final Type type;
    public final String name;
    public final String itemName;
    public final int cooldown;
    public final double power;
    public final double radius;
    public final int duration;
    public final double damage;
    public final List<String> description;

    KitAbility(Type type, String name, String itemName, int cooldown, double power, double radius, int duration, double damage, List<String> description) {
        this.type = type;
        this.name = name;
        this.itemName = itemName;
        this.cooldown = cooldown;
        this.power = power;
        this.radius = radius;
        this.duration = duration;
        this.damage = damage;
        this.description = description;
    }

    /** null, ha nincs képesség vagy hibás (a hibát a problems listába írja). */
    public static KitAbility read(String where, ConfigurationSection section, List<String> problems) {
        if (section == null || !section.isSet("type")) {
            return null;
        }
        Type type = Type.parse(section.getString("type"));
        if (type == null) {
            List<String> names = new ArrayList<>();
            for (Type value : Type.values()) {
                names.add(value.name().toLowerCase(Locale.ROOT));
            }
            problems.add(where + ".type: ismeretlen képesség (" + section.getString("type") + "). Lehet: " + String.join(", ", names));
            return null;
        }
        return new KitAbility(type,
                section.getString("name", "&b" + type.label),
                section.getString("item", "NETHER_STAR"),
                clamp(section.getInt("cooldown", 20), 1, 600),
                clamp(section.getDouble("power", 1.0), 0.0, 10.0),
                clamp(section.getDouble("radius", 5.0), 1.0, 30.0),
                clamp(section.getInt("duration", 5), 1, 60),
                clamp(section.getDouble("damage", 0.0), 0.0, 20.0),
                new ArrayList<>(section.getStringList("description")));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Material material() {
        return Items.material(itemName, Material.NETHER_STAR);
    }

    /** A hotbaron lévő tárgy. Kit tárgyként van megjelölve, így nem lehet eldobni és nem esik le halálkor. */
    public ItemStack item(String kitId, List<String> lore) {
        return Items.of(material()).name(name).lore(lore).hideAll().glow().kit(kitId).tag(TAG + kitId).build();
    }

    /** Cooldown milliszekundumban, a fejlesztés (pl. -10%/szint) után. Legalább 1 mp marad. */
    public long cooldownMillis(double multiplier) {
        double factor = Math.max(0.1, Math.min(1.0, multiplier));
        return Math.max(1000L, Math.round(cooldown * 1000.0 * factor));
    }
}
