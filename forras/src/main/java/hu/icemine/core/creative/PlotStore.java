package hu.icemine.core.creative;

import hu.icemine.core.data.Database;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A telkek az IceMine adatbázisában (SQLite vagy MySQL), telkenként egy sor. */
final class PlotStore {

    private final Database database;

    PlotStore(Database database) {
        this.database = database;
    }

    void init() throws Exception {
        database.query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_creative_plots (id VARCHAR(32) PRIMARY KEY, owner VARCHAR(36) NOT NULL, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    List<Plot> loadAll() throws Exception {
        return database.query(connection -> {
            List<Plot> plots = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, data FROM im_creative_plots");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Plot plot = Plot.read(result.getString(1), result.getString(2));
                    if (plot != null) {
                        plots.add(plot);
                    }
                }
            }
            return plots;
        }).get(30, TimeUnit.SECONDS);
    }

    void save(Plot plot) {
        String id = plot.id();
        String owner = plot.owner.toString();
        String data = plot.write();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO im_creative_plots (id, owner, data, updated) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, owner);
                statement.setString(3, data);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        });
    }

    void delete(Plot plot) {
        String id = plot.id();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM im_creative_plots WHERE id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
        });
    }
}
