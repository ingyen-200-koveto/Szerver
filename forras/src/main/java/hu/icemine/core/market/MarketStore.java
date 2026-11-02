package hu.icemine.core.market;

import hu.icemine.core.data.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** A piac adatai az IceMine adatbázisában (SQLite vagy MySQL). Betöltés induláskor, írás mindig a háttérszálon. */
final class MarketStore {

    /** Egy sor a speciális blokkok táblájából. */
    record SpecialRow(String world, int x, int y, int z, String type, String owner, String scope, long stored) {
    }

    /** Egy sor az aukciók táblájából. */
    record AuctionRow(String id, String scope, String seller, String sellerName, long price, String item, long created, long expires, String status) {
    }

    private final Database database;
    private final Logger logger;

    MarketStore(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    void init() throws Exception {
        database.query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_market_auctions (id VARCHAR(36) PRIMARY KEY, scope VARCHAR(32) NOT NULL, seller VARCHAR(36) NOT NULL, "
                        + "seller_name VARCHAR(16) NOT NULL, price BIGINT NOT NULL, item MEDIUMTEXT NOT NULL, created BIGINT NOT NULL, expires BIGINT NOT NULL, status VARCHAR(16) NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_market_specials (world VARCHAR(64) NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                        + "type VARCHAR(16) NOT NULL, owner VARCHAR(36) NOT NULL, scope VARCHAR(32) NOT NULL, stored BIGINT NOT NULL, PRIMARY KEY (world, x, y, z))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_market_shop (scope VARCHAR(32) NOT NULL, item VARCHAR(64) NOT NULL, net DOUBLE NOT NULL, PRIMARY KEY (scope, item))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_market_meta (k VARCHAR(96) PRIMARY KEY, v TEXT NOT NULL)");
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    // ---- tárgyak szöveggé és vissza ----

    static String serialize(ItemStack item) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item);
        return yaml.saveToString();
    }

    ItemStack deserialize(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
            return yaml.getItemStack("item");
        } catch (InvalidConfigurationException | RuntimeException exception) {
            logger.warning("Piac: sérült tárgy a mentésben: " + exception.getMessage());
            return null;
        }
    }

    // ---- aukciók ----

    List<AuctionRow> loadAuctions() throws Exception {
        return database.query(connection -> {
            List<AuctionRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, scope, seller, seller_name, price, item, created, expires, status FROM im_market_auctions WHERE status IN ('active', 'expired')");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new AuctionRow(result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getLong(5),
                            result.getString(6), result.getLong(7), result.getLong(8), result.getString(9)));
                }
            }
            return rows;
        }).get(30, TimeUnit.SECONDS);
    }

    void saveAuction(AuctionRow row) {
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO im_market_auctions (id, scope, seller, seller_name, price, item, created, expires, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, row.id());
                statement.setString(2, row.scope());
                statement.setString(3, row.seller());
                statement.setString(4, row.sellerName());
                statement.setLong(5, row.price());
                statement.setString(6, row.item());
                statement.setLong(7, row.created());
                statement.setLong(8, row.expires());
                statement.setString(9, row.status());
                statement.executeUpdate();
            }
        });
    }

    void setAuctionStatus(String id, String status) {
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE im_market_auctions SET status = ? WHERE id = ?")) {
                statement.setString(1, status);
                statement.setString(2, id);
                statement.executeUpdate();
            }
        });
    }

    // ---- speciális blokkok ----

    List<SpecialRow> loadSpecials() throws Exception {
        return database.query(connection -> {
            List<SpecialRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT world, x, y, z, type, owner, scope, stored FROM im_market_specials");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new SpecialRow(result.getString(1), result.getInt(2), result.getInt(3), result.getInt(4), result.getString(5),
                            result.getString(6), result.getString(7), result.getLong(8)));
                }
            }
            return rows;
        }).get(30, TimeUnit.SECONDS);
    }

    void saveSpecial(SpecialRow row) {
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO im_market_specials (world, x, y, z, type, owner, scope, stored) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, row.world());
                statement.setInt(2, row.x());
                statement.setInt(3, row.y());
                statement.setInt(4, row.z());
                statement.setString(5, row.type());
                statement.setString(6, row.owner());
                statement.setString(7, row.scope());
                statement.setLong(8, row.stored());
                statement.executeUpdate();
            }
        });
    }

    void deleteSpecial(String world, int x, int y, int z) {
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_market_specials WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, world);
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                statement.executeUpdate();
            }
        });
    }

    // ---- bolt számlálók ----

    Map<String, Double> loadShop() throws Exception {
        return database.query(connection -> {
            Map<String, Double> rows = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT scope, item, net FROM im_market_shop");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.put(result.getString(1) + ":" + result.getString(2), result.getDouble(3));
                }
            }
            return rows;
        }).get(15, TimeUnit.SECONDS);
    }

    void saveShop(Map<String, Double> counters) {
        Map<String, Double> copy = new HashMap<>(counters);
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO im_market_shop (scope, item, net) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Double> entry : copy.entrySet()) {
                    String[] key = entry.getKey().split(":", 2);
                    if (key.length < 2) {
                        continue;
                    }
                    statement.setString(1, key[0]);
                    statement.setString(2, key[1]);
                    statement.setDouble(3, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
    }

    // ---- egyéb (lottó, függő jutalmak) ----

    Map<String, String> loadMeta() throws Exception {
        return database.query(connection -> {
            Map<String, String> rows = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT k, v FROM im_market_meta");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.put(result.getString(1), result.getString(2));
                }
            }
            return rows;
        }).get(15, TimeUnit.SECONDS);
    }

    void saveMeta(String key, String value) {
        database.execute(connection -> {
            if (value == null) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_market_meta WHERE k = ?")) {
                    statement.setString(1, key);
                    statement.executeUpdate();
                }
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO im_market_meta (k, v) VALUES (?, ?)")) {
                statement.setString(1, key);
                statement.setString(2, value);
                statement.executeUpdate();
            }
        });
    }

    /** A legtöbb pénzű játékosok egy pénznemben (az adatbázis szerint), a háttérszálon. */
    java.util.concurrent.CompletableFuture<List<String[]>> topAsync(String currency, int limit) {
        return database.query(connection -> {
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT b.uuid, p.name, b.amount FROM im_balances b LEFT JOIN im_players p ON p.uuid = b.uuid WHERE b.currency = ? ORDER BY b.amount DESC LIMIT ?")) {
                statement.setString(1, currency);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(new String[]{result.getString(1), result.getString(2), String.valueOf(result.getLong(3))});
                    }
                }
            }
            return rows;
        });
    }
}
