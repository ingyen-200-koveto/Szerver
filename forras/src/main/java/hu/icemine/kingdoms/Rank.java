package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Egy királysági rang. A priority a sorrend: 0 a király (a legmagasabb), minél nagyobb a szám, annál lejjebb van.
 * A király rangot nem lehet törölni, és mindenhez joga van.
 */
public final class Rank {

    public static final String KING = "king";

    public final String id;
    public String name;
    public String color;
    public String symbol;
    public String material;
    public int priority;
    /** Ennyi chunkot foglalhat egy ilyen rangú tag (-1 = korlátlan, 0 = egyet sem). */
    public int maxClaims;
    public final Set<KPerm> perms = EnumSet.noneOf(KPerm.class);

    public Rank(String id, String name, String color, String symbol, String material, int priority, int maxClaims, Set<KPerm> perms) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.symbol = symbol;
        this.material = material;
        this.priority = priority;
        this.maxClaims = maxClaims;
        this.perms.addAll(perms);
    }

    public boolean king() {
        return id.equals(KING);
    }

    public boolean has(KPerm perm) {
        return king() || perms.contains(perm);
    }

    /** "&c♚ Király" formában. */
    public String display() {
        return color + symbol + " " + name;
    }

    public Rank copy() {
        return new Rank(id, name, color, symbol, material, priority, maxClaims, perms);
    }

    void write(ConfigurationSection section) {
        section.set("name", name);
        section.set("color", color);
        section.set("symbol", symbol);
        section.set("material", material);
        section.set("priority", priority);
        section.set("max-claims", maxClaims);
        List<String> list = new ArrayList<>();
        for (KPerm perm : perms) {
            list.add(perm.name());
        }
        section.set("permissions", list);
    }

    static Rank read(String id, ConfigurationSection section, Rank fallback) {
        Set<KPerm> perms = EnumSet.noneOf(KPerm.class);
        if (section.isList("permissions")) {
            for (String text : section.getStringList("permissions")) {
                KPerm perm = KPerm.of(text);
                if (perm != null) {
                    perms.add(perm);
                }
            }
        } else if (fallback != null) {
            perms.addAll(fallback.perms);
        }
        return new Rank(id,
                section.getString("name", fallback == null ? id : fallback.name),
                section.getString("color", fallback == null ? "&f" : fallback.color),
                section.getString("symbol", fallback == null ? "♟" : fallback.symbol),
                section.getString("material", fallback == null ? "IRON_BLOCK" : fallback.material),
                id.equals(KING) ? 0 : Math.max(1, section.getInt("priority", fallback == null ? 9 : fallback.priority)),
                section.getInt("max-claims", fallback == null ? 0 : fallback.maxClaims),
                perms);
    }

    /** Az alap rangok (ha a kingdoms.yml-ben nincs megadva más): Király, Trónörökös, Herceg, Lovag, Tag. */
    public static List<Rank> defaults() {
        List<Rank> list = new ArrayList<>();
        list.add(new Rank(KING, "Király", "&c", "♚", "EMERALD_BLOCK", 0, -1, EnumSet.allOf(KPerm.class)));
        Set<KPerm> prince = EnumSet.allOf(KPerm.class);
        prince.remove(KPerm.MANAGE_RANKS);
        list.add(new Rank("prince", "Trónörökös", "&6", "♛", "DIAMOND_BLOCK", 1, -1, prince));
        Set<KPerm> duke = EnumSet.of(KPerm.BUILD, KPerm.INTERACT, KPerm.USE, KPerm.NEXUS, KPerm.NEXUS_BUILD, KPerm.CLAIM, KPerm.UNCLAIM,
                KPerm.INVITE, KPerm.KICK, KPerm.SET_RANKS, KPerm.HOME, KPerm.SET_HOME, KPerm.UPGRADE, KPerm.STRUCTURES, KPerm.TURRETS,
                KPerm.INVADE, KPerm.BROADCAST, KPerm.WITHDRAW);
        list.add(new Rank("duke", "Herceg", "&d", "❖", "GOLD_BLOCK", 2, 30, duke));
        Set<KPerm> knight = EnumSet.of(KPerm.BUILD, KPerm.INTERACT, KPerm.USE, KPerm.NEXUS, KPerm.NEXUS_BUILD, KPerm.CLAIM, KPerm.INVITE,
                KPerm.HOME, KPerm.STRUCTURES, KPerm.TURRETS, KPerm.INVADE);
        list.add(new Rank("knight", "Lovag", "&e", "♜", "IRON_BLOCK", 3, 10, knight));
        list.add(new Rank("member", "Tag", "&7", "♟", "COPPER_BLOCK", 4, 0, KPerm.basic()));
        return list;
    }
}
