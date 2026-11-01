package hu.icemine.core.bedwars;

import java.util.UUID;

/** Egy játékos adatai egy BedWars meccsben. */
public final class BwPlayer {

    public final UUID id;
    public final String name;
    public TeamColor team;
    public TeamColor wanted;
    public boolean eliminated;
    public int respawnSeconds = -1;
    public int kills;
    public int finals;
    public int beds;
    public int deaths;
    /** 0 = bőr, 1 = lánc, 2 = vas, 3 = gyémánt (halál után is megmarad). */
    public int armor;
    public int pickaxe;
    public int axe;
    public boolean shears;
    public UUID lastDamager;
    public long lastDamageAt;
    public long lastFireball;

    public BwPlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean respawning() {
        return respawnSeconds >= 0;
    }

    public boolean playing() {
        return team != null && !eliminated && !respawning();
    }

    /** Halálkor a csákány és a balta egy szintet visszaesik (de nem tűnik el). */
    public void died() {
        deaths++;
        pickaxe = Math.max(pickaxe > 0 ? 1 : 0, pickaxe - 1);
        axe = Math.max(axe > 0 ? 1 : 0, axe - 1);
    }
}
