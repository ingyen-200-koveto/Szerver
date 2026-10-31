package hu.icemine.core.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

public final class ProfileService {

    public static final class Entry {
        public final String name;
        public final long value;

        public Entry(String name, long value) {
            this.name = name;
            this.value = value;
        }
    }

    private final Database database;
    private final Logger logger;
    private volatile String defaultRank;
    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> pending = new ConcurrentHashMap<>();
    private Consumer<PlayerProfile> freshHook = profile -> {
    };

    public ProfileService(Database database, Logger logger, String defaultRank) {
        this.database = database;
        this.logger = logger;
        this.defaultRank = defaultRank;
    }

    public void defaultRank(String rank) {
        this.defaultRank = rank;
    }

    public void onFresh(Consumer<PlayerProfile> hook) {
        this.freshHook = hook;
    }

    public PlayerProfile profile(Player player) {
        return online.get(player.getUniqueId());
    }

    public PlayerProfile profile(UUID uuid) {
        return online.get(uuid);
    }

    public Collection<PlayerProfile> online() {
        return online.values();
    }

    public boolean preload(UUID uuid, String name) {
        try {
            PlayerProfile profile = database.query(connection -> read(connection, uuid, name)).get(8, TimeUnit.SECONDS);
            pending.put(uuid, profile);
            return true;
        } catch (Exception exception) {
            logger.warning("Nem sikerült betölteni " + name + " adatait: " + exception.getMessage());
            return false;
        }
    }

    public boolean attach(Player player) {
        PlayerProfile profile = pending.remove(player.getUniqueId());
        if (profile == null) {
            if (!preload(player.getUniqueId(), player.getName())) {
                return false;
            }
            profile = pending.remove(player.getUniqueId());
            if (profile == null) {
                return false;
            }
        }
        profile.name(player.getName());
        if (profile.fresh()) {
            freshHook.accept(profile);
        }
        online.put(player.getUniqueId(), profile);
        return true;
    }

    public void discardPending(UUID uuid) {
        pending.remove(uuid);
    }

    public void detach(Player player) {
        PlayerProfile profile = online.remove(player.getUniqueId());
        if (profile == null) {
            return;
        }
        profile.closeSession();
        PlayerProfile.Snapshot snapshot = profile.snapshot(false);
        database.execute(connection -> write(connection, List.of(snapshot)));
    }

    public CompletableFuture<Void> saveDirty() {
        List<PlayerProfile.Snapshot> snapshots = new ArrayList<>();
        for (PlayerProfile profile : online.values()) {
            if (profile.dirty()) {
                snapshots.add(profile.snapshot(false));
            }
        }
        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return database.query(connection -> {
            write(connection, snapshots);
            return null;
        });
    }

    public void saveAllNow() {
        List<PlayerProfile.Snapshot> snapshots = new ArrayList<>();
        for (PlayerProfile profile : online.values()) {
            profile.closeSession();
            snapshots.add(profile.snapshot(false));
        }
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            database.query(connection -> {
                write(connection, snapshots);
                return null;
            }).get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            logger.severe("Leállításkor nem sikerült minden játékost elmenteni: " + exception.getMessage());
        }
    }

    public CompletableFuture<PlayerProfile> lookup(String name) {
        return database.query(connection -> {
            UUID uuid = null;
            String exact = name;
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, name FROM im_players WHERE name_lower = ?")) {
                statement.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        uuid = UUID.fromString(result.getString(1));
                        exact = result.getString(2);
                    }
                }
            }
            if (uuid == null) {
                return null;
            }
            return read(connection, uuid, exact);
        });
    }

    public CompletableFuture<List<Entry>> topStat(String stat, boolean ascending, int limit) {
        String order = ascending ? "ASC" : "DESC";
        return database.query(connection -> {
            List<Entry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT p.name, s.value FROM im_stats s JOIN im_players p ON p.uuid = s.uuid WHERE s.stat = ? AND s.value > 0 ORDER BY s.value " + order + " LIMIT ?")) {
                statement.setString(1, stat);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new Entry(result.getString(1), result.getLong(2)));
                    }
                }
            }
            return entries;
        });
    }

    public CompletableFuture<List<Entry>> topBalance(String currency, int limit) {
        return database.query(connection -> {
            List<Entry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT p.name, b.amount FROM im_balances b JOIN im_players p ON p.uuid = b.uuid WHERE b.currency = ? AND b.amount > 0 ORDER BY b.amount DESC LIMIT ?")) {
                statement.setString(1, currency);
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new Entry(result.getString(1), result.getLong(2)));
                    }
                }
            }
            return entries;
        });
    }

    public CompletableFuture<Boolean> setOfflineRank(String name, String rank) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE im_players SET rank_id = ? WHERE name_lower = ?")) {
                statement.setString(1, rank);
                statement.setString(2, name.toLowerCase(Locale.ROOT));
                return statement.executeUpdate() > 0;
            }
        });
    }

    private PlayerProfile read(Connection connection, UUID uuid, String name) throws SQLException {
        PlayerProfile profile = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name, rank_id, first_join, last_seen, playtime, settings FROM im_players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    profile = new PlayerProfile(uuid, result.getString(1), result.getString(2), result.getLong(3), result.getLong(4), result.getLong(5), false);
                    profile.decodeSettings(result.getString(6));
                }
            }
        }
        if (profile == null) {
            long now = System.currentTimeMillis();
            return new PlayerProfile(uuid, name, defaultRank, now, now, 0L, true);
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT currency, amount FROM im_balances WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    profile.loadBalance(result.getString(1), result.getLong(2));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT stat, value FROM im_stats WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    profile.loadStat(result.getString(1), result.getLong(2));
                }
            }
        }
        return profile;
    }

    private void write(Connection connection, List<PlayerProfile.Snapshot> snapshots) throws SQLException {
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement players = connection.prepareStatement(database.upsertPlayer());
             PreparedStatement balances = connection.prepareStatement(database.upsertBalance());
             PreparedStatement stats = connection.prepareStatement(database.upsertStat())) {
            for (PlayerProfile.Snapshot snapshot : snapshots) {
                String id = snapshot.uuid.toString();
                if (snapshot.main) {
                    players.setString(1, id);
                    players.setString(2, snapshot.name);
                    players.setString(3, snapshot.name.toLowerCase(Locale.ROOT));
                    players.setString(4, snapshot.rank);
                    players.setLong(5, snapshot.firstJoin);
                    players.setLong(6, snapshot.lastSeen);
                    players.setLong(7, snapshot.playtime);
                    players.setString(8, snapshot.settings);
                    players.addBatch();
                }
                for (Map.Entry<String, Long> entry : snapshot.balances.entrySet()) {
                    balances.setString(1, id);
                    balances.setString(2, entry.getKey());
                    balances.setLong(3, entry.getValue());
                    balances.addBatch();
                }
                for (Map.Entry<String, Long> entry : snapshot.stats.entrySet()) {
                    stats.setString(1, id);
                    stats.setString(2, entry.getKey());
                    stats.setLong(3, entry.getValue());
                    stats.addBatch();
                }
            }
            players.executeBatch();
            balances.executeBatch();
            stats.executeBatch();
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(auto);
        }
    }
}
