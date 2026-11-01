package hu.icemine.kingdoms;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Egy lerakott építmény vagy torony a királyság földjén. */
public final class Placed {

    public final UUID id;
    /** "structure" vagy "turret". */
    public final String kind;
    public final String type;
    public final int x;
    public final int y;
    public final int z;
    public int level;
    /** Tornyoknál a lőszer, építményeknél az energia. */
    public long ammo;
    /** Amit az építmény eddig összegyűjtött (pl. a Kitermelő Resource Pointjai). */
    public long stored;
    /** Szabad szöveg (pl. a teleportkapu neve). */
    public String label = "";
    public boolean enabled = true;
    /** Futás közbeni adat: mikor lőtt / termelt utoljára. */
    public transient long last;

    public Placed(UUID id, String kind, String type, int x, int y, int z, int level) {
        this.id = id;
        this.kind = kind;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
    }

    public boolean turret() {
        return kind.equals("turret");
    }

    public long chunk() {
        return Lands.key(x >> 4, z >> 4);
    }

    public boolean at(int bx, int by, int bz) {
        return x == bx && y == by && z == bz;
    }

    void write(ConfigurationSection section) {
        section.set("kind", kind);
        section.set("type", type);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("level", level);
        section.set("ammo", ammo);
        section.set("stored", stored);
        section.set("label", label);
        section.set("enabled", enabled);
    }

    static Placed read(String key, ConfigurationSection section) {
        UUID id;
        try {
            id = UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            id = UUID.randomUUID();
        }
        Placed placed = new Placed(id, section.getString("kind", "structure"), section.getString("type", "nexus"),
                section.getInt("x"), section.getInt("y"), section.getInt("z"), Math.max(1, section.getInt("level", 1)));
        placed.ammo = section.getLong("ammo", 0);
        placed.stored = section.getLong("stored", 0);
        placed.label = section.getString("label", "");
        placed.enabled = section.getBoolean("enabled", true);
        return placed;
    }
}
