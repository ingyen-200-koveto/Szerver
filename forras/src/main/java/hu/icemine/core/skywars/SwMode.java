package hu.icemine.core.skywars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/** Egy SkyWars mód (solo, duó): hány sziget, hány fő egy szigeten, mennyi kell az induláshoz. */
public final class SwMode {

    public final String id;
    public final String name;
    public final int teamSize;
    public final int islands;
    public final int minPlayers;
    public final int arenas;
    public final List<SwMap.Theme> maps;

    SwMode(String id, String name, int teamSize, int islands, int minPlayers, int arenas, List<SwMap.Theme> maps) {
        this.id = id;
        this.name = name;
        this.teamSize = teamSize;
        this.islands = islands;
        this.minPlayers = minPlayers;
        this.arenas = arenas;
        this.maps = maps;
    }

    static SwMode read(String id, ConfigurationSection s, List<String> problems) {
        List<SwMap.Theme> maps = new ArrayList<>();
        for (String m : s.getStringList("maps")) {
            SwMap.Theme t = SwMap.Theme.of(m);
            if (t == null) {
                problems.add("skywars.yml -> modes." + id + ".maps: ismeretlen pálya: " + m);
            } else {
                maps.add(t);
            }
        }
        if (maps.isEmpty()) {
            maps.addAll(List.of(SwMap.Theme.values()));
        }
        int islands = Math.max(2, Math.min(8, s.getInt("islands", 8)));
        int team = Math.max(1, Math.min(4, s.getInt("team-size", 1)));
        return new SwMode(id.toLowerCase(Locale.ROOT), s.getString("name", id), team, islands,
                Math.max(2, s.getInt("min-players", 2)), Math.max(1, Math.min(6, s.getInt("arenas", 2))), maps);
    }

    public int capacity() {
        return teamSize * islands;
    }

    public boolean matches(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.equals(id) || hu.icemine.core.util.Text.strip(hu.icemine.core.util.Text.color(name)).equalsIgnoreCase(text)
                || (id.equals("duo") && (t.equals("duó") || t.equals("doubles"))) || (id.equals("solo") && t.equals("szolo"));
    }
}
