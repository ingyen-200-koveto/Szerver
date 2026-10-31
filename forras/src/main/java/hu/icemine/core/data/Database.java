package hu.icemine.core.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    public interface Task {
        void run(Connection connection) throws SQLException;
    }

    private final Logger logger;
    private final ExecutorService executor;
    private Connection connection;
    private boolean mysql;
    private String url;
    private String user;
    private String password;

    public Database(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "IceMine-Adatbazis");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean mysql() {
        return mysql;
    }

    public void open(String type, File folder, String file, String host, int port, String name, String user, String password, boolean ssl) throws Exception {
        this.mysql = "mysql".equalsIgnoreCase(type) || "mariadb".equalsIgnoreCase(type);
        if (mysql) {
            this.url = "jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=" + ssl + "&characterEncoding=utf8&useUnicode=true";
            this.user = user;
            this.password = password;
        } else {
            folder.mkdirs();
            this.url = "jdbc:sqlite:" + new File(folder, file).getAbsolutePath();
        }
        executor.submit(() -> {
            connect();
            createSchema();
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    private void connect() throws Exception {
        if (mysql) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            connection = DriverManager.getConnection(url, user, password);
        } else {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
            }
        }
    }

    private Connection connection() throws SQLException {
        try {
            if (connection == null || connection.isClosed() || (mysql && !connection.isValid(2))) {
                connect();
            }
        } catch (SQLException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SQLException(exception);
        }
        return connection;
    }

    private void createSchema() throws SQLException {
        String id = mysql ? "BIGINT PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS im_players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16) NOT NULL, name_lower VARCHAR(16) NOT NULL, rank_id VARCHAR(32) NOT NULL, first_join BIGINT NOT NULL, last_seen BIGINT NOT NULL, playtime BIGINT NOT NULL, settings TEXT)",
                "CREATE TABLE IF NOT EXISTS im_balances (uuid VARCHAR(36) NOT NULL, currency VARCHAR(32) NOT NULL, amount BIGINT NOT NULL, PRIMARY KEY (uuid, currency))",
                "CREATE TABLE IF NOT EXISTS im_stats (uuid VARCHAR(36) NOT NULL, stat VARCHAR(48) NOT NULL, value BIGINT NOT NULL, PRIMARY KEY (uuid, stat))",
                "CREATE TABLE IF NOT EXISTS im_transactions (id " + id + ", uuid VARCHAR(36) NOT NULL, currency VARCHAR(32) NOT NULL, delta BIGINT NOT NULL, balance BIGINT NOT NULL, reason VARCHAR(64) NOT NULL, time BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS im_admin_log (id " + id + ", actor VARCHAR(36) NOT NULL, action VARCHAR(48) NOT NULL, target VARCHAR(64), detail TEXT, time BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS im_gens_plots (uuid VARCHAR(36) PRIMARY KEY, plot INTEGER NOT NULL, level INTEGER NOT NULL, trusted TEXT, daily BIGINT NOT NULL, quests TEXT)",
                "CREATE TABLE IF NOT EXISTS im_gens_generators (world_x INTEGER NOT NULL, world_y INTEGER NOT NULL, world_z INTEGER NOT NULL, uuid VARCHAR(36) NOT NULL, tier INTEGER NOT NULL, stored BIGINT NOT NULL, PRIMARY KEY (world_x, world_y, world_z))"
        };
        try (Statement statement = connection().createStatement()) {
            for (String table : tables) {
                statement.execute(table);
            }
            index(statement, "im_players_name", "im_players", "name_lower");
            index(statement, "im_stats_stat", "im_stats", "stat, value");
            index(statement, "im_balances_currency", "im_balances", "currency, amount");
            index(statement, "im_transactions_uuid", "im_transactions", "uuid");
            index(statement, "im_gens_owner", "im_gens_generators", "uuid");
        }
    }

    private void index(Statement statement, String name, String table, String columns) throws SQLException {
        if (mysql) {
            try {
                statement.execute("CREATE INDEX " + name + " ON " + table + " (" + columns + ")");
            } catch (SQLException ignored) {
            }
        } else {
            statement.execute("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")");
        }
    }

    public String upsertBalance() {
        return mysql
                ? "INSERT INTO im_balances (uuid, currency, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
                : "INSERT INTO im_balances (uuid, currency, amount) VALUES (?, ?, ?) ON CONFLICT(uuid, currency) DO UPDATE SET amount = excluded.amount";
    }

    public String adjustBalance() {
        return mysql
                ? "INSERT INTO im_balances (uuid, currency, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = GREATEST(0, LEAST(?, amount + ?))"
                : "INSERT INTO im_balances (uuid, currency, amount) VALUES (?, ?, ?) ON CONFLICT(uuid, currency) DO UPDATE SET amount = MAX(0, MIN(?, amount + ?))";
    }

    public String upsertStat() {
        return mysql
                ? "INSERT INTO im_stats (uuid, stat, value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)"
                : "INSERT INTO im_stats (uuid, stat, value) VALUES (?, ?, ?) ON CONFLICT(uuid, stat) DO UPDATE SET value = excluded.value";
    }

    public String upsertPlayer() {
        return mysql
                ? "INSERT INTO im_players (uuid, name, name_lower, rank_id, first_join, last_seen, playtime, settings) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), name_lower = VALUES(name_lower), rank_id = VALUES(rank_id), last_seen = VALUES(last_seen), playtime = VALUES(playtime), settings = VALUES(settings)"
                : "INSERT INTO im_players (uuid, name, name_lower, rank_id, first_join, last_seen, playtime, settings) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, name_lower = excluded.name_lower, rank_id = excluded.rank_id, last_seen = excluded.last_seen, playtime = excluded.playtime, settings = excluded.settings";
    }

    public String upsertPlot() {
        return mysql
                ? "INSERT INTO im_gens_plots (uuid, plot, level, trusted, daily, quests) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE level = VALUES(level), trusted = VALUES(trusted), daily = VALUES(daily), quests = VALUES(quests)"
                : "INSERT INTO im_gens_plots (uuid, plot, level, trusted, daily, quests) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET level = excluded.level, trusted = excluded.trusted, daily = excluded.daily, quests = excluded.quests";
    }

    public String upsertGenerator() {
        return mysql
                ? "INSERT INTO im_gens_generators (world_x, world_y, world_z, uuid, tier, stored) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE uuid = VALUES(uuid), tier = VALUES(tier), stored = VALUES(stored)"
                : "INSERT INTO im_gens_generators (world_x, world_y, world_z, uuid, tier, stored) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(world_x, world_y, world_z) DO UPDATE SET uuid = excluded.uuid, tier = excluded.tier, stored = excluded.stored";
    }

    public <T> CompletableFuture<T> query(Work<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(work.run(connection()));
                } catch (Throwable throwable) {
                    logger.log(Level.WARNING, "Adatbázis hiba: " + throwable.getMessage(), throwable);
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    public void execute(Task task) {
        query(connection -> {
            task.run(connection);
            return null;
        });
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warning("Az adatbázis mentés nem fejeződött be 20 másodperc alatt.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
