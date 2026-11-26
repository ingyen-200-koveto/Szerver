package hu.icemine.core.gens;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * A Gens "tycoon" része a generátorokon kívül:
 * - Elixír: aratáskor jár (a termés értékének egy része), ebből fejleszted a kapát.
 * - Gens szint: XP aratásért és dropok eladásáért, szintlépéskor jutalom; a jobb generátorhoz szint kell.
 * - Boosterek: pénz, elixír, generátor (gyorsaság), időre, jobb klikkel aktiválható tárgyak.
 * - Eventek: óránként egy véletlen event (pl. 2x generátor drop), bossbar mutatja, mikor jön a következő.
 */
public final class GensExtras implements Listener {

    public static final String BOOST_MONEY = "money";
    public static final String BOOST_ELIXIR = "elixir";
    public static final String BOOST_GEN = "gen";
    public static final String EVENT_GEN_SPEED = "gen-speed";
    public static final String EVENT_GEN_DROP = "gen-drop";
    public static final String EVENT_ELIXIR = "elixir";
    public static final String EVENT_MONEY = "money";
    public static final String EVENT_XP = "xp";
    public static final String BOOSTER_TAG = "gens:booster:";
    private static final String[] BOOST_TYPES = {BOOST_MONEY, BOOST_ELIXIR, BOOST_GEN};

    /** Egy event a gens.yml-ből. */
    record GameEvent(String id, String name, String type, double multiplier, int minutes) {
    }

    private final IceMineCore core;
    private final GensMode gens;
    private double elixirRate = 1.0;
    private double xpPerCrop = 2.0;
    private long levelBase = 150;
    private double levelGrowth = 1.17;
    private int levelMax = 100;
    private long rewardMoney = 500;
    private long rewardElixir = 200;
    private int boosterEvery = 5;
    private int slotsEvery = 10;
    private final List<GameEvent> events = new ArrayList<>();
    private int eventInterval = 45;
    private GameEvent active;
    private long activeUntil;
    private long nextEvent;
    private BossBar eventBar;
    private final Map<UUID, Map<String, BossBar>> boostBars = new HashMap<>();

    GensExtras(IceMineCore core, GensMode gens) {
        this.core = core;
        this.gens = gens;
    }

    void load(YamlConfiguration config, List<String> problems) {
        elixirRate = Math.max(0, config.getDouble("elixir.rate", 1.0));
        ConfigurationSection level = section(core, config, "level");
        if (level != null) {
            xpPerCrop = Math.max(0, level.getDouble("xp-per-crop", 2.0));
            levelBase = Math.max(10, level.getLong("base", 150));
            levelGrowth = Math.max(1.0, level.getDouble("growth", 1.17));
            levelMax = Math.max(1, level.getInt("max", 100));
            rewardMoney = Math.max(0, level.getLong("reward-money", 500));
            rewardElixir = Math.max(0, level.getLong("reward-elixir", 200));
            boosterEvery = Math.max(0, level.getInt("booster-every", 5));
            slotsEvery = Math.max(0, level.getInt("slots-every", 10));
        }
        events.clear();
        ConfigurationSection ev = section(core, config, "events");
        if (ev != null) {
            eventInterval = Math.max(5, ev.getInt("interval-minutes", 45));
            ConfigurationSection list = ev.getConfigurationSection("list");
            if (list != null) {
                for (String id : list.getKeys(false)) {
                    ConfigurationSection e = list.getConfigurationSection(id);
                    if (e == null) {
                        continue;
                    }
                    String type = e.getString("type", EVENT_GEN_DROP);
                    if (!List.of(EVENT_GEN_SPEED, EVENT_GEN_DROP, EVENT_ELIXIR, EVENT_MONEY, EVENT_XP).contains(type)) {
                        problems.add("gens.yml -> events.list." + id + ".type: ismeretlen (" + type + ")");
                        continue;
                    }
                    events.add(new GameEvent(id, e.getString("name", id), type, Math.max(1.0, e.getDouble("multiplier", 2.0)),
                            Math.max(1, e.getInt("minutes", 10))));
                }
            }
        }
        if (nextEvent == 0) {
            nextEvent = System.currentTimeMillis() + eventInterval * 60_000L;
        }
    }

    /**
     * Egy rész a gens.yml-ből. Ha a szerveren lévő (régebbi) gens.yml-ből hiányzik vagy üres, a beépített
     * alapértéket használjuk (a getConfigurationSection ilyenkor üres részt adna vissza).
     */
    static ConfigurationSection section(IceMineCore core, YamlConfiguration config, String path) {
        Object own = config.get(path, null);
        if (own instanceof ConfigurationSection section && !section.getKeys(false).isEmpty()) {
            return section;
        }
        if (config.getDefaults() != null && config.getDefaults().get(path) instanceof ConfigurationSection def) {
            return def;
        }
        java.io.InputStream stream = core.getResource("gens.yml");
        if (stream == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getConfigurationSection(path);
    }

    // ---- elixír ----

    public long elixir(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        return profile == null ? 0 : profile.stat("gens.elixir");
    }

    public String formatElixir(long amount) {
        return "&d" + Text.number(amount) + " ⚗";
    }

    void giveElixir(Player player, long amount) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null && amount > 0) {
            profile.addStat("gens.elixir", amount);
            profile.addStat("gens.elixir_total", amount);
        }
    }

    /** Elixírt von le: false, ha nincs elég. */
    public boolean takeElixir(Player player, long amount) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        long have = profile.stat("gens.elixir");
        if (have < amount) {
            return false;
        }
        profile.setStat("gens.elixir", have - amount);
        return true;
    }

    /** Aratás után: elixír és XP. */
    void harvested(Player player, long saleValue, int crops, int fieldIndex) {
        double elixirMult = boost(player, BOOST_ELIXIR) * eventMultiplier(EVENT_ELIXIR);
        giveElixir(player, Math.max(crops > 0 ? 1 : 0, Math.round(saleValue * elixirRate * elixirMult)));
        addXp(player, Math.max(1, Math.round(crops * xpPerCrop * (1 + fieldIndex * 0.6))));
    }

    // ---- szint ----

    public int level(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        return profile == null ? 1 : (int) Math.max(1, profile.stat("gens.szint"));
    }

    public long need(int level) {
        return Math.round(levelBase * Math.pow(levelGrowth, level - 1));
    }

    public double levelPercent(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return 0;
        }
        int level = level(player);
        if (level >= levelMax) {
            return 100;
        }
        return Math.min(99.9, profile.stat("gens.szintxp") * 100.0 / need(level));
    }

    public void addXp(Player player, long xp) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null || xp <= 0) {
            return;
        }
        xp = Math.round(xp * eventMultiplier(EVENT_XP));
        int level = level(player);
        if (level >= levelMax) {
            return;
        }
        long current = profile.stat("gens.szintxp") + xp;
        int start = level;
        while (level < levelMax && current >= need(level)) {
            current -= need(level);
            level++;
        }
        profile.setStat("gens.szintxp", level >= levelMax ? 0 : current);
        if (level > start) {
            profile.setStat("gens.szint", level);
            for (int l = start + 1; l <= level; l++) {
                levelUp(player, l);
            }
        }
    }

    private void levelUp(Player player, int level) {
        String cur = gens.currency();
        long money = rewardMoney * level;
        long elixir = rewardElixir * level;
        if (money > 0) {
            core.economy().deposit(player, cur, money, "gens:level");
        }
        giveElixir(player, elixir);
        StringBuilder extra = new StringBuilder();
        if (boosterEvery > 0 && level % boosterEvery == 0) {
            String type = BOOST_TYPES[(level / boosterEvery) % BOOST_TYPES.length];
            give(player, booster(type, 2.0, 30));
            extra.append(" &7+ &a").append(boostName(type)).append(" booster");
        }
        if (slotsEvery > 0 && level % slotsEvery == 0) {
            give(player, gens.generators().slotItem(1));
            extra.append(" &7+ &bGenerátor férőhely");
        }
        player.sendTitle(Text.color("&a&lSZINTLÉPÉS!"), Text.color("&fGens szint: &a" + level), 5, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.1F);
        core.messages().sendTagged(player, "&aElérted a(z) &f" + level + ". &aGens szintet! Jutalom: &e" + core.economy().format(cur, money)
                + " &7+ " + formatElixir(elixir) + extra);
    }

    // ---- boosterek ----

    public static String boostName(String type) {
        return switch (type) {
            case BOOST_MONEY -> "Pénz";
            case BOOST_ELIXIR -> "Elixír";
            default -> "Generátor";
        };
    }

    public ItemStack booster(String type, double multiplier, int minutes) {
        Material icon = switch (type) {
            case BOOST_MONEY -> Material.GOLD_NUGGET;
            case BOOST_ELIXIR -> Material.DRAGON_BREATH;
            default -> Material.BLAZE_POWDER;
        };
        String mult = Text.decimal(multiplier) + "x";
        return Items.of(icon).name("&a&l" + boostName(type) + " booster &f" + mult)
                .lore("&7" + switch (type) {
                    case BOOST_MONEY -> "Több pénzt kapsz eladáskor.";
                    case BOOST_ELIXIR -> "Több elixírt kapsz aratáskor.";
                    default -> "Gyorsabban termelnek a generátoraid.";
                }, "", "&7Szorzó: &f" + mult, "&7Időtartam: &f" + minutes + " perc", "", "&eJobb klikk &7» Felhasználás")
                .glow().tag(BOOSTER_TAG + type + ":" + multiplier + ":" + minutes).build();
    }

    /** A játékos aktív boostere (szorzó), vagy 1.0. */
    public double boost(Player player, String type) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return 1.0;
        }
        String[] p = profile.setting("gens.boost." + type, "").split("\\|");
        if (p.length < 2) {
            return 1.0;
        }
        try {
            long until = Long.parseLong(p[1]);
            return until > System.currentTimeMillis() ? Math.max(1.0, Double.parseDouble(p[0])) : 1.0;
        } catch (NumberFormatException exception) {
            return 1.0;
        }
    }

    private long boostUntil(Player player, String type) {
        PlayerProfile profile = core.profiles().profile(player);
        String[] p = profile == null ? new String[0] : profile.setting("gens.boost." + type, "").split("\\|");
        try {
            return p.length < 2 ? 0 : Long.parseLong(p[1]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean activate(Player player, String type, double multiplier, int minutes) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        double current = boost(player, type);
        long until = boostUntil(player, type);
        if (current > 1.0 && Math.abs(current - multiplier) > 0.001) {
            core.messages().sendTagged(player, "&cMár fut egy másik " + boostName(type) + " boostered (" + Text.decimal(current)
                    + "x). Várd meg, amíg lejár.");
            return false;
        }
        long start = current > 1.0 ? until : now;
        profile.put("gens.boost." + type, multiplier + "|" + (start + minutes * 60_000L));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.5F);
        core.messages().sendTagged(player, "&aAktiváltál egy &f" + Text.decimal(multiplier) + "x " + boostName(type) + " &aboostert! &7("
                + ((start + minutes * 60_000L - now) / 60_000L) + " perc)");
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        String tag = Items.tagOf(event.getItem());
        if (tag == null || !tag.startsWith(BOOSTER_TAG)) {
            return;
        }
        event.setCancelled(true);
        String[] p = tag.substring(BOOSTER_TAG.length()).split(":");
        if (p.length < 3) {
            return;
        }
        try {
            if (activate(event.getPlayer(), p[0], Double.parseDouble(p[1]), Integer.parseInt(p[2]))) {
                ItemStack hand = event.getItem();
                hand.setAmount(hand.getAmount() - 1);
            }
        } catch (NumberFormatException ignored) {
            // hibás booster tárgy
        }
    }

    // ---- eventek ----

    public double eventMultiplier(String type) {
        return active != null && active.type().equals(type) && System.currentTimeMillis() < activeUntil ? active.multiplier() : 1.0;
    }

    public boolean startEvent(String id) {
        GameEvent found = null;
        for (GameEvent e : events) {
            if (e.id().equalsIgnoreCase(id)) {
                found = e;
            }
        }
        if (found == null && "random".equalsIgnoreCase(id) && !events.isEmpty()) {
            found = events.get(ThreadLocalRandom.current().nextInt(events.size()));
        }
        if (found == null) {
            return false;
        }
        active = found;
        activeUntil = System.currentTimeMillis() + found.minutes() * 60_000L;
        nextEvent = activeUntil + eventInterval * 60_000L;
        for (Player player : gens.world() == null ? List.<Player>of() : gens.world().getPlayers()) {
            player.sendTitle(Text.color("&d&l" + Text.strip(Text.color(found.name()))), Text.color("&f" + found.minutes() + " percig!"), 5, 50, 10);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            core.messages().sendTagged(player, "&d&lGens event: &f" + found.name() + " &7(" + found.minutes() + " perc)");
        }
        return true;
    }

    public List<String> eventIds() {
        List<String> ids = new ArrayList<>();
        for (GameEvent e : events) {
            ids.add(e.id());
        }
        return ids;
    }

    void tick() {
        long now = System.currentTimeMillis();
        if (active != null && now >= activeUntil) {
            active = null;
        }
        if (active == null && !events.isEmpty() && now >= nextEvent) {
            startEvent("random");
        }
        List<Player> inGens = gens.world() == null ? List.of() : gens.world().getPlayers();
        if (eventBar == null) {
            eventBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        }
        if (active != null) {
            long left = Math.max(0, activeUntil - now);
            eventBar.setTitle(Text.color(active.name() + " &7- &f" + clock(left)));
            eventBar.setColor(BarColor.PINK);
            eventBar.setProgress(Math.max(0, Math.min(1, left / (active.minutes() * 60_000.0))));
        } else if (!events.isEmpty()) {
            long left = Math.max(0, nextEvent - now);
            eventBar.setTitle(Text.color("&aKövetkező event indul: &f" + clock(left)));
            eventBar.setColor(BarColor.GREEN);
            eventBar.setProgress(Math.max(0, Math.min(1, 1 - left / (eventInterval * 60_000.0))));
        }
        sync(eventBar, inGens, !events.isEmpty());
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, BossBar> bars = boostBars.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            boolean here = player.getWorld() == gens.world();
            for (String type : BOOST_TYPES) {
                double mult = boost(player, type);
                BossBar bar = bars.get(type);
                if (mult <= 1.0 || !here) {
                    if (bar != null) {
                        bar.removeAll();
                        bars.remove(type);
                    }
                    continue;
                }
                if (bar == null) {
                    bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
                    bar.addPlayer(player);
                    bars.put(type, bar);
                }
                long left = Math.max(0, boostUntil(player, type) - now);
                bar.setTitle(Text.color("&e+" + Math.round((mult - 1) * 100) + "% &f" + boostName(type) + " booster &7| &f" + clock(left)));
                bar.setProgress(Math.max(0, Math.min(1, left / (60 * 60_000.0))));
            }
        }
    }

    private static void sync(BossBar bar, List<Player> players, boolean show) {
        for (Player p : new ArrayList<>(bar.getPlayers())) {
            if (!show || !players.contains(p)) {
                bar.removePlayer(p);
            }
        }
        if (show) {
            for (Player p : players) {
                if (!bar.getPlayers().contains(p)) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    void quit(UUID uuid) {
        Map<String, BossBar> bars = boostBars.remove(uuid);
        if (bars != null) {
            for (BossBar bar : bars.values()) {
                bar.removeAll();
            }
        }
    }

    void shutdown() {
        if (eventBar != null) {
            eventBar.removeAll();
        }
        for (Map<String, BossBar> bars : boostBars.values()) {
            for (BossBar bar : bars.values()) {
                bar.removeAll();
            }
        }
        boostBars.clear();
    }

    private static String clock(long millis) {
        long s = millis / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** A scoreboard és a menük értékei. */
    void placeholders(Player player, Map<String, String> values) {
        values.put("%gens_elixir%", Text.number(elixir(player)));
        values.put("%gens_szint%", String.valueOf(level(player)));
        values.put("%gens_szint_pct%", String.format(Locale.ROOT, "%.1f", levelPercent(player)));
        values.put("%gens_gens%", String.valueOf(gens.generators().placed(player.getUniqueId())));
        values.put("%gens_gens_max%", String.valueOf(gens.generators().slots(player)));
    }
}
