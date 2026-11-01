package hu.icemine.kingdoms;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Egy játékos Kingdoms adatai: melyik királyság tagja, ereje (power) és statisztikái. */
public final class KPlayer {

    public final UUID uuid;
    public String name;
    public UUID kingdom;
    public long joined;
    public double power;
    public long kills;
    public long deaths;
    /** Ennyi Resource Pointot adott be a királyságnak. */
    public long donated;
    public long invasions;
    public long lastSeen;
    /** Mikor kapott utoljára power-t online töltésért. */
    public long powerTick;

    public transient boolean kingdomChat;
    public transient boolean autoClaim;
    public transient boolean autoMap;
    public transient boolean dirty;

    public KPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    void write(ConfigurationSection section) {
        section.set("name", name);
        section.set("kingdom", kingdom == null ? null : kingdom.toString());
        section.set("joined", joined);
        section.set("power", power);
        section.set("kills", kills);
        section.set("deaths", deaths);
        section.set("donated", donated);
        section.set("invasions", invasions);
        section.set("last-seen", lastSeen);
    }

    static KPlayer read(UUID uuid, ConfigurationSection section) {
        KPlayer player = new KPlayer(uuid, section.getString("name", "?"));
        String kingdom = section.getString("kingdom");
        if (kingdom != null && !kingdom.isEmpty()) {
            try {
                player.kingdom = UUID.fromString(kingdom);
            } catch (IllegalArgumentException ignored) {
                player.kingdom = null;
            }
        }
        player.joined = section.getLong("joined");
        player.power = section.getDouble("power");
        player.kills = section.getLong("kills");
        player.deaths = section.getLong("deaths");
        player.donated = section.getLong("donated");
        player.invasions = section.getLong("invasions");
        player.lastSeen = section.getLong("last-seen");
        return player;
    }
}
