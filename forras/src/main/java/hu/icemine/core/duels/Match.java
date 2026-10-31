package hu.icemine.core.duels;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class Match {
    final Duels duels;
    final DuelKit kit;
    final ArenaSlot slot;
    final boolean ranked;
    final int bestOf;
    final UUID red;
    final UUID blue;
    final String redName;
    final String blueName;
    final Set<UUID> spectators = new HashSet<>();
    final Set<Long> placed = new HashSet<>();
    final Map<UUID, Integer> combo = new HashMap<>();
    Match.State state = Match.State.COUNTDOWN;
    int timer = 3;
    int elapsed;
    int redWins;
    int blueWins;
    int redHits;
    int blueHits;
    int round = 1;
    UUID winner;

    Match(Duels duels, DuelKit duelkit, ArenaSlot arenaslot, boolean flag, int i, Player player, Player player1) {
        this.duels = duels;
        this.kit = duelkit;
        this.slot = arenaslot;
        this.ranked = flag;
        this.bestOf = Math.max(1, i);
        this.red = player.getUniqueId();
        this.blue = player1.getUniqueId();
        this.redName = player.getName();
        this.blueName = player1.getName();
    }

    public int needed() {
        return this.kit.rule == DuelKit.Rule.BRIDGE ? this.kit.target : this.bestOf / 2 + 1;
    }

    public boolean involves(UUID uuid) {
        return this.red.equals(uuid) || this.blue.equals(uuid);
    }

    public DuelKit.Side side(UUID uuid) {
        return this.red.equals(uuid) ? DuelKit.Side.RED : (this.blue.equals(uuid) ? DuelKit.Side.BLUE : null);
    }

    public UUID opponent(UUID uuid) {
        return this.red.equals(uuid) ? this.blue : this.red;
    }

    public String name(UUID uuid) {
        return this.red.equals(uuid) ? this.redName : this.blueName;
    }

    public Player player(DuelKit.Side duelkit$side) {
        return Bukkit.getPlayer(duelkit$side == DuelKit.Side.RED ? this.red : this.blue);
    }

    public Location spawn(DuelKit.Side duelkit$side) {
        return (duelkit$side == DuelKit.Side.RED ? this.slot.red : this.slot.blue).clone();
    }

    public int wins(DuelKit.Side duelkit$side) {
        return duelkit$side == DuelKit.Side.RED ? this.redWins : this.blueWins;
    }

    public int hits(DuelKit.Side duelkit$side) {
        return duelkit$side == DuelKit.Side.RED ? this.redHits : this.blueHits;
    }

    void addWin(DuelKit.Side duelkit$side) {
        if (duelkit$side == DuelKit.Side.RED) {
            this.redWins++;
        } else {
            this.blueWins++;
        }
    }

    int addHit(DuelKit.Side duelkit$side) {
        return duelkit$side == DuelKit.Side.RED ? ++this.redHits : ++this.blueHits;
    }

    void resetHits() {
        this.redHits = 0;
        this.blueHits = 0;
        this.combo.clear();
    }

    public String score(UUID uuid) {
        DuelKit.Side duelkit$side = this.side(uuid);
        if (duelkit$side == null) {
            return this.redWins + " - " + this.blueWins;
        }

        DuelKit.Side duelkit$side1 = duelkit$side == DuelKit.Side.RED ? DuelKit.Side.BLUE : DuelKit.Side.RED;
        return this.wins(duelkit$side) + " - " + this.wins(duelkit$side1);
    }

    public String hitScore(UUID uuid) {
        DuelKit.Side duelkit$side = this.side(uuid);
        if (duelkit$side == null) {
            return this.redHits + " - " + this.blueHits;
        }

        DuelKit.Side duelkit$side1 = duelkit$side == DuelKit.Side.RED ? DuelKit.Side.BLUE : DuelKit.Side.RED;
        return this.hits(duelkit$side) + " - " + this.hits(duelkit$side1);
    }

    public Match.State state() {
        return this.state;
    }

    public DuelKit kit() {
        return this.kit;
    }

    public enum State {
        COUNTDOWN,
        FIGHT,
        ROUND_END,
        ENDED;
    }
}
