package hu.icemine.core.creative;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Egy lefoglalt telek: kié, mi a neve, kik építhetnek rajta, és kik nem jöhetnek rá. */
final class Plot {

    final int px;
    final int pz;
    UUID owner;
    String ownerName;
    String name = "";
    final Map<UUID, String> trusted = new LinkedHashMap<>();
    final Map<UUID, String> denied = new LinkedHashMap<>();
    long created;
    /** Épp törlődik (újragenerálás közben nem lehet rajta építeni). */
    transient boolean clearing;

    Plot(int px, int pz, UUID owner, String ownerName) {
        this.px = px;
        this.pz = pz;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    long key() {
        return PlotGrid.key(px, pz);
    }

    String id() {
        return px + ";" + pz;
    }

    boolean canBuild(UUID player) {
        return owner.equals(player) || trusted.containsKey(player);
    }

    String display() {
        return name == null || name.isBlank() ? ownerName + " telke" : name;
    }

    String write() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("owner", owner.toString());
        yaml.set("owner-name", ownerName);
        yaml.set("name", name);
        yaml.set("created", created);
        for (Map.Entry<UUID, String> entry : trusted.entrySet()) {
            yaml.set("trusted." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, String> entry : denied.entrySet()) {
            yaml.set("denied." + entry.getKey(), entry.getValue());
        }
        return yaml.saveToString();
    }

    static Plot read(String id, String data) {
        String[] parts = id.split(";");
        if (parts.length != 2) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(data);
            int px = Integer.parseInt(parts[0]);
            int pz = Integer.parseInt(parts[1]);
            UUID owner = UUID.fromString(yaml.getString("owner", ""));
            Plot plot = new Plot(px, pz, owner, yaml.getString("owner-name", "?"));
            plot.name = yaml.getString("name", "");
            plot.created = yaml.getLong("created");
            readMap(yaml.getConfigurationSection("trusted"), plot.trusted);
            readMap(yaml.getConfigurationSection("denied"), plot.denied);
            return plot;
        } catch (Exception exception) {
            return null;
        }
    }

    private static void readMap(ConfigurationSection section, Map<UUID, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                target.put(UUID.fromString(key), section.getString(key, "?"));
            } catch (IllegalArgumentException ignored) {
                // hibás sor, kihagyjuk
            }
        }
    }
}
