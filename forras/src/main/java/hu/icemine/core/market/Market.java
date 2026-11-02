package hu.icemine.core.market;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.util.ClickText;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A közös piac: aukciósház, csere, dinamikus bolt, eladó láda, craftoló, farm blokk, lottó és kulcsok. Mindez
 * "piaconként" (scope) külön működik: a Kingdoms és a Fyland saját pénzzel, saját aukciókkal és saját árakkal,
 * hogy a két mód tárgyai és pénze ne keveredjen.
 */
public final class Market {

    public static final String FILE = "market";

    /** Egy piac: melyik világban él, és milyen pénzzel. */
    public record Scope(String id, String world, String currency, String name) {
    }

    private final IceMineCore core;
    private final Map<String, Scope> scopes = new LinkedHashMap<>();
    private final MarketStore store;
    private final AuctionHouse auctions;
    private final TradeManager trades;
    private final DynamicShop shop;
    private final Specials specials;
    private final Lottery lottery;
    private final Keys keys;
    private YamlConfiguration config;
    private boolean ready;

    public Market(IceMineCore core) {
        this.core = core;
        this.store = new MarketStore(core.database(), core.getLogger());
        this.auctions = new AuctionHouse(this);
        this.trades = new TradeManager(this);
        this.shop = new DynamicShop(this);
        this.specials = new Specials(this);
        this.lottery = new Lottery(this);
        this.keys = new Keys(this);
    }

    public void load(YamlConfiguration config, List<String> problems) {
        this.config = config;
        scopes.clear();
        ConfigurationSection section = config.getConfigurationSection("scopes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String currency = section.getString(id + ".currency", core.economy().defaultCurrency());
                if (core.economy().currency(currency) == null) {
                    problems.add("market.yml -> scopes." + id + ".currency: ismeretlen pénznem (" + currency + ")");
                    currency = core.economy().defaultCurrency();
                }
                scopes.put(id.toLowerCase(Locale.ROOT), new Scope(id.toLowerCase(Locale.ROOT), section.getString(id + ".world", id), currency,
                        section.getString(id + ".name", id)));
            }
        }
        if (!ready) {
            try {
                store.init();
                auctions.loadAll();
                shop.loadCounters();
                specials.loadAll();
                lottery.loadState();
                ready = true;
            } catch (Exception exception) {
                problems.add("Piac: nem sikerült betölteni az adatbázisból (" + exception.getMessage() + "), a piac most nem működik");
                return;
            }
        }
        auctions.load(config, problems);
        shop.load(config, problems);
        specials.load(config, problems);
        lottery.load(config, problems);
        keys.load(config, problems);
    }

    public boolean ready() {
        return ready;
    }

    IceMineCore core() {
        return core;
    }

    MarketStore store() {
        return store;
    }

    public AuctionHouse auctions() {
        return auctions;
    }

    public TradeManager trades() {
        return trades;
    }

    public DynamicShop shop() {
        return shop;
    }

    public Specials specials() {
        return specials;
    }

    public Lottery lottery() {
        return lottery;
    }

    public Keys keys() {
        return keys;
    }

    YamlConfiguration config() {
        return config;
    }

    // ---- piacok ----

    public java.util.Collection<Scope> scopes() {
        return scopes.values();
    }

    public Scope scope(String id) {
        return id == null ? null : scopes.get(id.toLowerCase(Locale.ROOT));
    }

    /** A piac, amelyiknek a világában a játékos áll, vagy null. */
    public Scope scopeOf(Player player) {
        return scopeOf(player.getWorld());
    }

    public Scope scopeOf(World world) {
        if (world == null) {
            return null;
        }
        for (Scope scope : scopes.values()) {
            if (scope.world().equalsIgnoreCase(world.getName())) {
                return scope;
            }
        }
        return null;
    }

    /** A játékos piaca; ha nincs piacos világban, üzen neki, és null-t ad. */
    Scope require(Player player) {
        Scope scope = ready ? scopeOf(player) : null;
        if (scope == null) {
            send(player, "only-in-scope");
        }
        return scope;
    }

    World world(Scope scope) {
        return Bukkit.getWorld(scope.world());
    }

    // ---- pénz ----

    String money(Scope scope, long amount) {
        return core.economy().format(scope.currency(), amount);
    }

    long balance(Player player, Scope scope) {
        return core.economy().balance(player, scope.currency());
    }

    boolean withdraw(Player player, Scope scope, long amount, String reason) {
        return amount <= 0 || core.economy().withdraw(player, scope.currency(), amount, reason) == Ledger.Result.OK;
    }

    /** Pénz egy játékosnak: ha fent van, azonnal, ha nincs, az adatbázisban. */
    void pay(UUID player, Scope scope, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null && core.profiles().profile(online) != null) {
            core.economy().deposit(online, scope.currency(), amount, reason);
        } else {
            core.economy().adjustOffline(player, scope.currency(), amount, reason);
        }
    }

    // ---- üzenetek ----

    String text(String key, String... pairs) {
        String value = config == null ? null : config.getString("messages." + key);
        return Text.replace(value == null ? "&c[piac: " + key + "]" : value, pairs);
    }

    List<String> texts(String key, String... pairs) {
        List<String> result = new ArrayList<>();
        if (config != null) {
            for (String line : config.getStringList("messages." + key)) {
                result.add(Text.replace(line, pairs));
            }
        }
        return result;
    }

    void send(Player player, String key, String... pairs) {
        core.messages().sendTagged(player, text(key, pairs));
    }

    void click(Player player, String key, String... pairs) {
        player.sendMessage(ClickText.line(core.messages().prefix() + text(key, pairs), "&7Kattints: &f%value%", "&7Kattints"));
    }

    void broadcast(Scope scope, String key, String... pairs) {
        World world = world(scope);
        if (world == null) {
            return;
        }
        String line = core.messages().tagged(text(key, pairs));
        for (Player player : world.getPlayers()) {
            player.sendMessage(line);
        }
    }

    /** Tárgy a játékosnak; ha nincs hely, a lába elé esik. */
    static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        for (ItemStack left : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** A tárgy neve az üzenetekhez: a saját neve, vagy a market.yml item-names részéből, vagy az anyag neve. */
    String itemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return materialName(item.getType().name());
    }

    String materialName(String material) {
        String key = material.toLowerCase(Locale.ROOT);
        String custom = config == null ? null : config.getString("item-names." + key);
        if (custom != null) {
            return custom;
        }
        StringBuilder name = new StringBuilder();
        for (String part : key.split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    // ---- életciklus ----

    /** Másodpercenként. */
    public void tick() {
        if (!ready) {
            return;
        }
        specials.tick();
        trades.tick();
    }

    /** Percenként. */
    public void minute() {
        if (!ready) {
            return;
        }
        auctions.expire();
        lottery.check();
        shop.drift();
    }

    public void shutdown() {
        if (!ready) {
            return;
        }
        trades.cancelAll();
        shop.saveCounters();
        specials.shutdown();
    }
}
