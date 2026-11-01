package hu.icemine.core.bedwars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/** Egy BedWars mód a bedwars.yml modes részéből (Solo, Duó, 3v3v3v3, 4v4v4v4). */
public final class BwMode {

    public final String id;
    public final String name;
    public final String icon;
    public final int slot;
    public final int teams;
    public final int teamSize;
    public final int minPlayers;
    public final int arenas;
    public final double generatorSpeed;
    public final List<BwMap.Theme> maps;
    public final List<String> aliases;
    public final List<String> description;

    BwMode(String id, String name, String icon, int slot, int teams, int teamSize, int minPlayers, int arenas, double generatorSpeed,
           List<BwMap.Theme> maps, List<String> aliases, List<String> description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.slot = slot;
        this.teams = teams;
        this.teamSize = teamSize;
        this.minPlayers = minPlayers;
        this.arenas = arenas;
        this.generatorSpeed = generatorSpeed;
        this.maps = maps;
        this.aliases = aliases;
        this.description = description;
    }

    public int capacity() {
        return teams * teamSize;
    }

    /** Pl. "8x1" vagy "4x4". */
    public String format() {
        return teams + "x" + teamSize;
    }

    public boolean matches(String text) {
        if (text == null) {
            return false;
        }
        String wanted = TeamColor.simple(text);
        if (id.equalsIgnoreCase(wanted)) {
            return true;
        }
        for (String alias : aliases) {
            if (TeamColor.simple(alias).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    static BwMode read(String id, ConfigurationSection section, List<String> problems) {
        int teams = section.getInt("teams", 4) > 4 ? 8 : 4;
        int teamSize = BwSettings.clamp(section.getInt("team-size", 1), 1, 8);
        List<BwMap.Theme> maps = new ArrayList<>();
        for (String text : section.getStringList("maps")) {
            BwMap.Theme theme = BwMap.Theme.of(text);
            if (theme == null) {
                problems.add("bedwars.yml -> modes." + id + ".maps: ismeretlen pálya: " + text);
            } else if (theme.teams != teams) {
                problems.add("bedwars.yml -> modes." + id + ".maps: a(z) " + text + " pálya " + theme.teams + " csapatos, ez a mód " + teams + " csapatos");
            } else {
                maps.add(theme);
            }
        }
        if (maps.isEmpty()) {
            for (BwMap.Theme theme : BwMap.Theme.values()) {
                if (theme.teams == teams) {
                    maps.add(theme);
                }
            }
        }
        return new BwMode(id.toLowerCase(Locale.ROOT),
                section.getString("name", id),
                section.getString("icon", "RED_BED"),
                section.getInt("slot", -1),
                teams,
                teamSize,
                BwSettings.clamp(section.getInt("min-players", 2), 2, teams * teamSize),
                BwSettings.clamp(section.getInt("arenas", 1), 0, 10),
                Math.max(0.1, section.getDouble("generator-speed", 1.0)),
                maps,
                section.getStringList("aliases"),
                section.getStringList("description"));
    }
}
