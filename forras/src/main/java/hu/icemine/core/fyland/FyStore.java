package hu.icemine.core.fyland;

import hu.icemine.core.data.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A Fyland szigetek az IceMine adatbázisában (SQLite vagy MySQL): szigetenként egy sor, az adat YAML szövegként.
 * Induláskor egyben töltjük be, mentés a háttérszálon. A következő szabad sziget helyét is itt tartjuk, hogy egy
 * törölt sziget helyére ne kerüljön új (ott még ott lehetnek a régi blokkok).
 */
final class FyStore {

    private final Database database;
    private final Logger logger;

    FyStore(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    void init() throws Exception {
        database.query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_fyland_islands (id VARCHAR(36) PRIMARY KEY, owner VARCHAR(36) NOT NULL, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_fyland_meta (k VARCHAR(32) PRIMARY KEY, v VARCHAR(64) NOT NULL)");
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    List<FyIsland> loadAll() throws Exception {
        return database.query(connection -> {
            List<FyIsland> islands = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, data FROM im_fyland_islands");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID id = FyIsland.uuid(rows.getString(1));
                    YamlConfiguration yaml = new YamlConfiguration();
                    try {
                        yaml.loadFromString(rows.getString(2) == null ? "" : rows.getString(2));
                    } catch (InvalidConfigurationException exception) {
                        logger.warning("Fyland: nem olvasható sziget (" + rows.getString(1) + "): " + exception.getMessage());
                        continue;
                    }
                    FyIsland island = id == null ? null : FyIsland.read(id, yaml);
                    if (island != null) {
                        islands.add(island);
                    }
                }
            }
            return islands;
        }).get(30, TimeUnit.SECONDS);
    }

    int nextIndex() throws Exception {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT v FROM im_fyland_meta WHERE k = ?")) {
                statement.setString(1, "next-index");
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        try {
                            return Integer.parseInt(rows.getString(1));
                        } catch (NumberFormatException exception) {
                            return 1;
                        }
                    }
                }
            }
            return 1;
        }).get(15, TimeUnit.SECONDS);
    }

    void saveNextIndex(int index) {
        String value = String.valueOf(index);
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO im_fyland_meta (k, v) VALUES (?, ?)")) {
                statement.setString(1, "next-index");
                statement.setString(2, value);
                statement.executeUpdate();
            }
        });
    }

    static String serialize(FyIsland island) {
        YamlConfiguration yaml = new YamlConfiguration();
        island.write(yaml);
        return yaml.saveToString();
    }

    void save(FyIsland island) {
        island.dirty = false;
        String id = island.id.toString();
        String owner = island.owner.toString();
        String data = serialize(island);
        long now = System.currentTimeMillis();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO im_fyland_islands (id, owner, data, updated) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, owner);
                statement.setString(3, data);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    void delete(UUID id) {
        String text = id.toString();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_fyland_islands WHERE id = ?")) {
                statement.setString(1, text);
                statement.executeUpdate();
            }
        });
    }
}
