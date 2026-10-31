package hu.icemine.core.economy;

import hu.icemine.core.config.Values;
import hu.icemine.core.data.Database;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.data.ProfileService;
import hu.icemine.core.util.Text;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class Economy {

    public static final class Currency {
        public final String id;
        public final String name;
        public final String symbol;
        public final String color;
        public final long start;
        public final long max;

        Currency(String id, String name, String symbol, String color, long start, long max) {
            this.id = id;
            this.name = name;
            this.symbol = symbol;
            this.color = color;
            this.start = start;
            this.max = max;
        }
    }

    private final Database database;
    private final ProfileService profiles;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();
    private String defaultCurrency = "kristaly";
    private long maxTransaction = 1_000_000_000L;
    private boolean log = true;

    public Economy(Database database, ProfileService profiles) {
        this.database = database;
        this.profiles = profiles;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Values values = new Values("economy.yml", config::get);
        currencies.clear();
        ConfigurationSection section = config.getConfigurationSection("currencies");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String base = "currencies." + id + ".";
                String key = id.toLowerCase(Locale.ROOT);
                currencies.put(key, new Currency(key,
                        values.text(base + "name", id),
                        values.text(base + "symbol", "❄"),
                        values.text(base + "color", "&b"),
                        values.number(base + "start", 0, 0, Long.MAX_VALUE / 4),
                        values.number(base + "max", 1_000_000_000_000L, 1, Long.MAX_VALUE / 4)));
            }
        }
        if (currencies.isEmpty()) {
            problems.add("economy.yml -> currencies: nincs egy pénznem sem megadva, létrehozom a kristályt");
            currencies.put("kristaly", new Currency("kristaly", "Kristály", "❄", "&b", 100, 1_000_000_000_000L));
        }
        defaultCurrency = values.text("default-currency", "kristaly").toLowerCase(Locale.ROOT);
        if (!currencies.containsKey(defaultCurrency)) {
            problems.add("economy.yml -> default-currency: ismeretlen pénznem (" + defaultCurrency + ")");
            defaultCurrency = currencies.keySet().iterator().next();
        }
        maxTransaction = values.number("max-transaction", 1_000_000_000L, 1, Long.MAX_VALUE / 4);
        log = values.bool("log-transactions", true);
        problems.addAll(values.problems());
    }

    public Collection<Currency> currencies() {
        return currencies.values();
    }

    public Currency currency(String id) {
        return id == null ? null : currencies.get(id.toLowerCase(Locale.ROOT));
    }

    public String defaultCurrency() {
        return defaultCurrency;
    }

    public String format(String currencyId, long amount) {
        Currency currency = currency(currencyId);
        if (currency == null) {
            return Text.number(amount);
        }
        return Text.color(currency.color + Text.number(amount) + " " + currency.symbol);
    }

    public void applyStart(PlayerProfile profile) {
        for (Currency currency : currencies.values()) {
            if (currency.start > 0) {
                profile.setBalance(currency.id, currency.start);
            }
        }
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Pénzművelet csak a fő szálon futhat");
        }
    }

    public long balance(Player player, String currencyId) {
        PlayerProfile profile = profiles.profile(player);
        return profile == null ? 0L : profile.balance(currencyId);
    }

    public Ledger.Result deposit(Player player, String currencyId, long amount, String reason) {
        requireMain();
        Currency currency = currency(currencyId);
        if (currency == null) {
            return Ledger.Result.UNKNOWN_CURRENCY;
        }
        PlayerProfile profile = profiles.profile(player);
        if (profile == null) {
            return Ledger.Result.NOT_LOADED;
        }
        long balance = profile.balance(currency.id);
        Ledger.Result result = Ledger.checkDeposit(balance, amount, currency.max, maxTransaction);
        if (result == Ledger.Result.OK) {
            profile.setBalance(currency.id, balance + amount);
            record(profile.uuid(), currency.id, amount, balance + amount, reason);
        }
        return result;
    }

    public Ledger.Result withdraw(Player player, String currencyId, long amount, String reason) {
        requireMain();
        Currency currency = currency(currencyId);
        if (currency == null) {
            return Ledger.Result.UNKNOWN_CURRENCY;
        }
        PlayerProfile profile = profiles.profile(player);
        if (profile == null) {
            return Ledger.Result.NOT_LOADED;
        }
        long balance = profile.balance(currency.id);
        Ledger.Result result = Ledger.checkWithdraw(balance, amount, maxTransaction);
        if (result == Ledger.Result.OK) {
            profile.setBalance(currency.id, balance - amount);
            record(profile.uuid(), currency.id, -amount, balance - amount, reason);
        }
        return result;
    }

    public Ledger.Result transfer(Player from, Player to, String currencyId, long amount, String reason) {
        requireMain();
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return Ledger.Result.SAME_PLAYER;
        }
        Currency currency = currency(currencyId);
        if (currency == null) {
            return Ledger.Result.UNKNOWN_CURRENCY;
        }
        PlayerProfile source = profiles.profile(from);
        PlayerProfile target = profiles.profile(to);
        if (source == null || target == null) {
            return Ledger.Result.NOT_LOADED;
        }
        long sourceBalance = source.balance(currency.id);
        long targetBalance = target.balance(currency.id);
        Ledger.Result result = Ledger.checkTransfer(sourceBalance, targetBalance, amount, currency.max, maxTransaction);
        if (result == Ledger.Result.OK) {
            source.setBalance(currency.id, sourceBalance - amount);
            target.setBalance(currency.id, targetBalance + amount);
            record(source.uuid(), currency.id, -amount, sourceBalance - amount, reason + ":" + target.name());
            record(target.uuid(), currency.id, amount, targetBalance + amount, reason + ":" + source.name());
        }
        return result;
    }

    public Ledger.Result set(Player player, String currencyId, long amount, String reason) {
        requireMain();
        Currency currency = currency(currencyId);
        if (currency == null) {
            return Ledger.Result.UNKNOWN_CURRENCY;
        }
        PlayerProfile profile = profiles.profile(player);
        if (profile == null) {
            return Ledger.Result.NOT_LOADED;
        }
        if (amount < 0) {
            return Ledger.Result.INVALID_AMOUNT;
        }
        long before = profile.balance(currency.id);
        long after = Ledger.clampSet(amount, currency.max);
        profile.setBalance(currency.id, after);
        record(profile.uuid(), currency.id, after - before, after, reason);
        return Ledger.Result.OK;
    }

    public CompletableFuture<Boolean> adjustOffline(UUID uuid, String currencyId, long delta, String reason) {
        Currency currency = currency(currencyId);
        if (currency == null || delta == 0 || Math.abs(delta) > maxTransaction) {
            return CompletableFuture.completedFuture(false);
        }
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(database.adjustBalance())) {
                statement.setString(1, uuid.toString());
                statement.setString(2, currency.id);
                statement.setLong(3, Math.max(0, Math.min(currency.max, delta)));
                statement.setLong(4, currency.max);
                statement.setLong(5, delta);
                statement.executeUpdate();
            }
            if (log) {
                insertLog(connection, uuid, currency.id, delta, -1, reason);
            }
            return true;
        });
    }

    private void record(UUID uuid, String currency, long delta, long balance, String reason) {
        if (!log) {
            return;
        }
        String cut = reason == null ? "?" : (reason.length() > 64 ? reason.substring(0, 64) : reason);
        database.execute(connection -> insertLog(connection, uuid, currency, delta, balance, cut));
    }

    private static void insertLog(java.sql.Connection connection, UUID uuid, String currency, long delta, long balance, String reason) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO im_transactions (uuid, currency, delta, balance, reason, time) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency);
            statement.setLong(3, delta);
            statement.setLong(4, balance);
            statement.setString(5, reason.length() > 64 ? reason.substring(0, 64) : reason);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}
