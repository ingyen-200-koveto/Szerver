package hu.icemine.core.bedwars;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

/** Egy csapat egy BedWars meccsben: tagok, ágy, fejlesztések, csapdák. */
public final class BwTeam {

    public final TeamColor color;
    public final List<UUID> members = new ArrayList<>();
    public Location spawn;
    public Location bedHead;
    public Location bedFoot;
    public String bedFacing = "north";
    public Location base;
    public boolean bedAlive = true;
    public boolean out;
    public int sharpness;
    public int protection;
    public int haste;
    public int forge;
    public boolean healPool;
    public final Deque<Trap> traps = new ArrayDeque<>();
    /** Eddig (tick számláló szerint) nem sülhet el újabb csapda. */
    public long trapReadyAt;

    public BwTeam(TeamColor color) {
        this.color = color;
    }

    public void reset() {
        members.clear();
        bedAlive = true;
        out = false;
        sharpness = 0;
        protection = 0;
        haste = 0;
        forge = 0;
        healPool = false;
        traps.clear();
        trapReadyAt = 0;
    }

    /** A kovácsműhely szorzója a vas és arany termelésre. */
    public double forgeSpeed() {
        switch (forge) {
            case 1:
                return 1.5;
            case 2:
            case 3:
                return 2.0;
            case 4:
                return 3.0;
            default:
                return 1.0;
        }
    }

    public boolean isBed(int x, int y, int z) {
        return (bedHead != null && bedHead.getBlockX() == x && bedHead.getBlockY() == y && bedHead.getBlockZ() == z)
                || (bedFoot != null && bedFoot.getBlockX() == x && bedFoot.getBlockY() == y && bedFoot.getBlockZ() == z);
    }
}
