package hu.icemine.kingdoms;

import hu.icemine.core.data.Database;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A Kingdoms adatai az IceMine adatbázisában (SQLite vagy MySQL, amit a config.yml beállít). Minden királyság, játékos és
 * nemzet egy sor, az adat YAML szövegként. Betöltés induláskor egyben (szinkron), mentés mindig a háttérszálon.
 * A szöveget a főszálon állítjuk elő, így mentés közben nem változhat alatta semmi.
 */
final class KingdomStore {

    static final class Loaded {
        final List<Kingdom> kingdoms = new ArrayList<>();
        final List<KPlayer> players = new ArrayList<>();
        final List<Nation> nations = new ArrayList<>();
    }

    private final Database database;
    private final Logger logger;

    KingdomStore(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    void init() throws Exception {
        database.query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_kingdoms (id VARCHAR(36) PRIMARY KEY, name VARCHAR(64) NOT NULL, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_kingdom_players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_kingdom_nations (id VARCHAR(36) PRIMARY KEY, name VARCHAR(64) NOT NULL, data MEDIUMTEXT NOT NULL, updated BIGINT NOT NULL)");
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
    }

    Loaded loadAll(List<Rank> defaults) throws Exception {
        return database.query(connection -> {
            Loaded loaded = new Loaded();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, data FROM im_kingdoms");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    YamlConfiguration yaml = parse(rows.getString(2), "királyság " + rows.getString(1));
                    UUID id = Kingdom.uuid(rows.getString(1));
                    if (yaml != null && id != null) {
                        Kingdom kingdom = Kingdom.read(id, yaml, defaults);
                        if (kingdom.leader != null) {
                            loaded.kingdoms.add(kingdom);
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, data FROM im_kingdom_players");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    YamlConfiguration yaml = parse(rows.getString(2), "játékos " + rows.getString(1));
                    UUID id = Kingdom.uuid(rows.getString(1));
                    if (yaml != null && id != null) {
                        loaded.players.add(KPlayer.read(id, yaml));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, data FROM im_kingdom_nations");
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    YamlConfiguration yaml = parse(rows.getString(2), "nemzet " + rows.getString(1));
                    UUID id = Kingdom.uuid(rows.getString(1));
                    if (yaml != null && id != null) {
                        try {
                            loaded.nations.add(Nation.read(id, yaml));
                        } catch (IllegalArgumentException exception) {
                            logger.warning("Kingdoms: sérült nemzet a mentésben, kihagyom: " + id);
                        }
                    }
                }
            }
            return loaded;
        }).get(30, TimeUnit.SECONDS);
    }

    private YamlConfiguration parse(String text, String what) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text == null ? "" : text);
            return yaml;
        } catch (InvalidConfigurationException exception) {
            logger.warning("Kingdoms: nem olvasható adat (" + what + "): " + exception.getMessage());
            return null;
        }
    }

    static String serialize(Kingdom kingdom) {
        YamlConfiguration yaml = new YamlConfiguration();
        kingdom.write(yaml);
        return yaml.saveToString();
    }

    static String serialize(KPlayer player) {
        YamlConfiguration yaml = new YamlConfiguration();
        player.write(yaml);
        return yaml.saveToString();
    }

    static String serialize(Nation nation) {
        YamlConfiguration yaml = new YamlConfiguration();
        nation.write(yaml);
        return yaml.saveToString();
    }

    void save(Kingdom kingdom) {
        kingdom.dirty = false;
        write("im_kingdoms", kingdom.id.toString(), kingdom.name, serialize(kingdom));
    }

    void save(KPlayer player) {
        player.dirty = false;
        write("im_kingdom_players", player.uuid.toString(), player.name, serialize(player));
    }

    void save(Nation nation) {
        nation.dirty = false;
        write("im_kingdom_nations", nation.id.toString(), nation.name, serialize(nation));
    }

    private void write(String table, String id, String name, String data) {
        long now = System.currentTimeMillis();
        String key = table.equals("im_kingdom_players") ? "uuid" : "id";
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO " + table + " (" + key + ", name, data, updated) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, name == null ? "?" : name);
                statement.setString(3, data);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    void deleteKingdom(UUID id) {
        delete("im_kingdoms", "id", id);
    }

    void deleteNation(UUID id) {
        delete("im_kingdom_nations", "id", id);
    }

    private void delete(String table, String key, UUID id) {
        String text = id.toString();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + key + " = ?")) {
                statement.setString(1, text);
                statement.executeUpdate();
            }
        });
    }

    /**
     * A régi (3.9 előtti) egyszerű Kingdoms mentés átvétele: plugins/IceMine/kingdoms/kingdoms.yml. Király -> király,
     * lovag -> lovag, tag -> tag; a földek és az otthon megmaradnak. Utána a fájl .regi végződést kap.
     */
    static Loaded convertLegacy(File file, List<Rank> defaults, Logger logger) {
        Loaded loaded = new Loaded();
        if (!file.isFile()) {
            return loaded;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> names = yaml.getConfigurationSection("names") == null ? Map.of() : yaml.getConfigurationSection("names").getValues(false);
        java.util.Map<UUID, KPlayer> players = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : names.entrySet()) {
            UUID id = Kingdom.uuid(entry.getKey());
            if (id != null) {
                players.put(id, new KPlayer(id, String.valueOf(entry.getValue())));
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("kingdoms");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                UUID leader = entry == null ? null : Kingdom.uuid(entry.getString("leader"));
                if (leader == null) {
                    continue;
                }
                Kingdom kingdom = new Kingdom(UUID.randomUUID(), entry.getString("name", key), leader, entry.getLong("created", System.currentTimeMillis()));
                for (Rank rank : defaults) {
                    kingdom.ranks.put(rank.id, rank.copy());
                }
                ConfigurationSection members = entry.getConfigurationSection("members");
                if (members != null) {
                    for (String id : members.getKeys(false)) {
                        UUID member = Kingdom.uuid(id);
                        if (member == null) {
                            continue;
                        }
                        String role = members.getString(id, "MEMBER").toUpperCase(Locale.ROOT);
                        String rank = role.equals("KING") ? Rank.KING : role.equals("KNIGHT") && kingdom.ranks.containsKey("knight") ? "knight" : kingdom.lowest().id;
                        kingdom.members.put(member, rank);
                        KPlayer player = players.computeIfAbsent(member, uuid -> new KPlayer(uuid, "?"));
                        player.kingdom = kingdom.id;
                        player.joined = kingdom.created;
                    }
                }
                kingdom.members.put(leader, Rank.KING);
                KPlayer king = players.computeIfAbsent(leader, uuid -> new KPlayer(uuid, "?"));
                king.kingdom = kingdom.id;
                for (String chunk : entry.getStringList("claims")) {
                    Long parsed = Lands.parse(chunk);
                    if (parsed != null) {
                        kingdom.claims.add(parsed);
                    }
                }
                if (entry.contains("home.x")) {
                    kingdom.home = new double[]{entry.getDouble("home.x"), entry.getDouble("home.y"), entry.getDouble("home.z"), entry.getDouble("home.yaw")};
                }
                kingdom.log("Átkerült az új Kingdoms rendszerbe.");
                loaded.kingdoms.add(kingdom);
            }
        }
        loaded.players.addAll(players.values());
        File renamed = new File(file.getParentFile(), file.getName() + ".regi");
        if (!file.renameTo(renamed)) {
            logger.warning("Kingdoms: a régi mentést nem sikerült átnevezni: " + file.getName());
        }
        return loaded;
    }
}
