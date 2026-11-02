package hu.icemine.core;

import hu.icemine.core.board.BoardService;
import hu.icemine.core.board.Leaderboards;
import hu.icemine.core.combat.CombatTag;
import hu.icemine.core.combo.ComboArenaMode;
import hu.icemine.core.combo.ComboFly;
import hu.icemine.core.command.AdminCommand;
import hu.icemine.core.command.PlayerCommands;
import hu.icemine.core.config.ConfigFiles;
import hu.icemine.core.config.DataMove;
import hu.icemine.core.config.Messages;
import hu.icemine.core.config.Migrations;
import hu.icemine.core.config.Upgrades;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.Database;
import hu.icemine.core.data.ProfileService;
import hu.icemine.core.duels.Duels;
import hu.icemine.core.duels.DuelsCommand;
import hu.icemine.core.economy.Economy;
import hu.icemine.core.gens.GensMenu;
import hu.icemine.core.gens.GensMode;
import hu.icemine.core.lobby.ContactBook;
import hu.icemine.core.lobby.Cosmetics;
import hu.icemine.core.lobby.Holograms;
import hu.icemine.core.lobby.LobbyMenus;
import hu.icemine.core.lobby.LobbyModule;
import hu.icemine.core.lobby.LobbyProtection;
import hu.icemine.core.lobby.Npcs;
import hu.icemine.core.lobby.Portals;
import hu.icemine.core.menu.MenuListener;
import hu.icemine.core.mode.InventoryStore;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeService;
import hu.icemine.core.mode.TeleportGuard;
import hu.icemine.core.parkour.ParkourService;
import hu.icemine.core.rank.Prefixes;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.kingdoms.IceMineKingdoms;
import java.io.File;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class IceMineCore extends JavaPlugin {

    private static IceMineCore instance;

    private ConfigFiles configs;
    private Messages messages;
    private Database database;
    private ProfileService profiles;
    private Economy economy;
    private Prefixes prefixes;
    private hu.icemine.core.rank.Ranks ranks;
    private hu.icemine.core.command.PermissionSetup permissions;
    private ModeService modes;
    private TeleportGuard guard;
    private CombatTag combat;
    private ComboFly combo;
    private LobbyModule lobby;
    private LobbyProtection protection;
    private LobbyMenus menus;
    private Portals portals;
    private Holograms holograms;
    private hu.icemine.core.modelobby.ModeLobbies modeLobbies;
    private IceMineKingdoms kingdoms;
    private hu.icemine.core.bedwars.Bedwars bedwars;
    private hu.icemine.core.economy.IceCoins iceCoins;
    private hu.icemine.core.rank.NameTags nameTags;
    private final hu.icemine.core.board.TabList tabList = new hu.icemine.core.board.TabList(this);
    private final Updater updater = new Updater(this);
    private Duels duels;
    private Npcs npcs;
    private ContactBook contacts;
    private Cosmetics cosmetics;
    private ParkourService parkour;
    private BoardService boards;
    private Leaderboards leaderboards;
    private InventoryStore inventories;
    private hu.icemine.core.fyland.FylandMode fyland;
    private hu.icemine.core.creative.CreativeMode creative;
    private hu.icemine.core.market.Market market;
    private ComboArenaMode comboArena;
    private GensMode gens;
    private hu.icemine.core.lobby.LobbyClicks lobbyClicks;
    private GensMenu gensMenu;
    private AuthHook auth;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean ready;

    public static IceMineCore get() {
        return instance;
    }

    @Override
    public void onLoad() {
        DataMove.run(getDataFolder().getParentFile(), getDataFolder().getName(), getLogger());
        if (hu.icemine.core.config.FirstSetup.run(Bukkit.getWorldContainer(), getLogger())) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(0);
        }
        try {
            hu.icemine.core.config.ForeignMessages.run(getDataFolder().getParentFile(), getDataFolder().getName(), this::getResource, getLogger());
        } catch (RuntimeException exception) {
            getLogger().warning("Nem sikerült beállítani az AuthMe/Essentials üzeneteket: " + exception);
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        Items.init(this);
        safely("régi módok takarítása", () -> hu.icemine.core.config.OldModes.run(getDataFolder(), Bukkit.getWorldContainer(), getLogger()));
        configs = new ConfigFiles(this);
        List<String> problems = configs.loadAll();
        safely("config frissítés", () -> {
            Upgrades upgrades = new Upgrades(getDataFolder(), this::getResource);
            for (String note : upgrades.apply(new java.util.LinkedHashSet<>(java.util.Arrays.asList(ConfigFiles.NAMES)), new Upgrades.Target() {
                @Override
                public YamlConfiguration config(String file) {
                    return configs.get(file);
                }

                @Override
                public void save(String file) {
                    configs.save(file);
                }
            })) {
                getLogger().info("Config frissítve: " + note);
            }
            for (String note : Migrations.run(configs)) {
                getLogger().info("Config frissítve: " + note);
            }
        });
        messages = new Messages(configs, getLogger());
        database = new Database(getLogger());
        YamlConfiguration main = configs.get("config");
        Values values = new Values("config.yml", main::get);
        try {
            database.open(values.choice("database.type", "sqlite", "sqlite", "mysql", "mariadb"), getDataFolder(),
                    values.text("database.file", "icemine.db"), values.text("database.host", "localhost"), values.integer("database.port", 3306, 1, 65535),
                    values.text("database.name", "icemine"), values.text("database.user", "root"), values.text("database.password", ""),
                    values.bool("database.ssl", false));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Nem sikerült megnyitni az adatbázist, a plugin leáll. Nézd meg a config.yml database részét!", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        profiles = new ProfileService(database, getLogger(), "jatekos");
        economy = new Economy(database, profiles);
        iceCoins = new hu.icemine.core.economy.IceCoins(this);
        nameTags = new hu.icemine.core.rank.NameTags(this);
        prefixes = new Prefixes(this, getLogger());
        ranks = new hu.icemine.core.rank.Ranks(this);
        permissions = new hu.icemine.core.command.PermissionSetup(this);
        modes = new ModeService(this);
        guard = new TeleportGuard(this);
        combat = new CombatTag(this);
        combo = new ComboFly(this);
        lobby = new LobbyModule(this);
        protection = new LobbyProtection(this);
        menus = new LobbyMenus(this);
        portals = new Portals(this);
        holograms = new Holograms(this);
        npcs = new Npcs(this);
        contacts = new ContactBook(this);
        cosmetics = new Cosmetics(this);
        parkour = new ParkourService(this);
        boards = new BoardService(this);
        leaderboards = new Leaderboards(this);
        inventories = new InventoryStore(new File(getDataFolder(), "inventories"), getLogger());
        fyland = new hu.icemine.core.fyland.FylandMode(this);
        creative = new hu.icemine.core.creative.CreativeMode(this);
        market = new hu.icemine.core.market.Market(this);
        comboArena = new ComboArenaMode(this);
        gens = new GensMode(this);
        gensMenu = new GensMenu(this, gens);
        modeLobbies = new hu.icemine.core.modelobby.ModeLobbies(this);
        duels = new Duels(this);
        bedwars = new hu.icemine.core.bedwars.Bedwars(this);
        auth = new AuthHook(getLogger());
        problems.addAll(values.problems());
        safely("pénzrendszer", () -> economy.load(configs.get("economy"), problems));
        profiles.onFresh(economy::applyStart);
        safely("játékmódok", () -> modes.load(configs.get("modes"), problems));
        safely("rangok", () -> ranks.load(configs.get("ranks"), problems));
        safely("combat tag", () -> combat.load(configs.get("combat-tag"), problems));
        safely("combofly", () -> combo.load(configs.get("combofly"), problems));
        safely("lobby", () -> lobby.load(configs.get("lobby"), problems));
        modes.register(lobby);
        loadLobbyParts(problems);
        safely("parkour", () -> parkour.load(configs.get("parkour"), problems));
        safely("toplisták", () -> leaderboards.load(main, problems));
        safely("IceCoin", () -> iceCoins.load(main, problems));
        safely("névtáblák", () -> nameTags.load(main));
        safely("frissítés", () -> updater.load(main));
        safely("Fyland", () -> {
            fyland.load(configs.get(hu.icemine.core.fyland.FylandMode.FILE), problems);
            modes.register(fyland);
        });
        safely("Creative", () -> {
            creative.load(configs.get(hu.icemine.core.creative.CreativeMode.FILE), problems);
            modes.register(creative);
        });
        safely("ComboFly aréna", () -> {
            comboArena.load(configs.get(ComboArenaMode.FILE), problems);
            modes.register(comboArena);
        });
        safely("Gens", () -> {
            gens.load(configs.get("gens"), problems);
            modes.register(gens);
        });
        safely("piac", () -> market.load(configs.get(hu.icemine.core.market.Market.FILE), problems));
        safely("hologramok", () -> {
            holograms.load(configs.get("lobby"), problems);
            holograms.spawnAll();
        });
        safely("NPC-k", () -> {
            npcs.load(configs.get("lobby"), problems);
            npcs.spawnAll();
        });
        safely("Duels", () -> {
            duels.load(configs.get("duels"), problems);
            modes.register(duels);
        });
        safely("BedWars", () -> {
            bedwars.load(configs.get(hu.icemine.core.bedwars.Bedwars.FILE), problems);
            modes.register(bedwars);
        });
        safely("mód lobbyk", () -> {
            modeLobbies.load(configs.get(hu.icemine.core.modelobby.ModeLobbies.FILE), problems);
            modeLobbies.spawnAll();
        });
        safely("scoreboard", () -> boards.load(configs.get("scoreboard"), problems));
        configs.report(problems);
        lobbyClicks = new hu.icemine.core.lobby.LobbyClicks(this);
        registerListeners(new CoreListener(this), modes, guard, combat, combo, protection, portals, npcs, holograms, lobbyClicks, cosmetics, modeLobbies, duels, parkour, boards, new MenuListener(this),
                fyland, comboArena, gens, market.trades(), market.specials(), market.lottery(), market.keys(), new hu.icemine.core.command.CommandGuard(this), new hu.icemine.core.bedwars.BwListener(bedwars), nameTags);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModeService.CHANNEL);
        AdminCommand admin = new AdminCommand(this);
        hu.icemine.core.command.IcemCommand icem = new hu.icemine.core.command.IcemCommand(this, admin);
        register("icem", icem, icem);
        PlayerCommands players = new PlayerCommands(this);
        for (String name : new String[]{"lobby", "spawn", "profil", "top", "kozmetika", "penz", "fizet", "napi", "kapcsolat", "parkour", "combofly", "kit", "arena", "gens", "elad"}) {
            register(name, players, players);
        }
        DuelsCommand duelsCommand = new DuelsCommand(this, duels);
        register("duel", duelsCommand, duelsCommand);
        register("duels", duelsCommand, duelsCommand);
        hu.icemine.core.bedwars.BwCommand bwCommand = new hu.icemine.core.bedwars.BwCommand(bedwars);
        register("bw", bwCommand, bwCommand);
        hu.icemine.core.fyland.FyCommand fyCommand = new hu.icemine.core.fyland.FyCommand(fyland);
        register("fyland", fyCommand, fyCommand);
        register("pw", fyCommand, fyCommand);
        register("enchanter", fyCommand, fyCommand);
        register("fywarp", fyCommand, fyCommand);
        registerListeners(fyland.listeners().toArray(new Listener[0]));
        registerListeners(creative);
        hu.icemine.core.creative.PlotCommand plotCommand = new hu.icemine.core.creative.PlotCommand(creative);
        register("plot", plotCommand, plotCommand);
        hu.icemine.core.market.MarketCommand marketCommand = new hu.icemine.core.market.MarketCommand(market);
        for (String name : new String[]{"ah", "trade", "shop", "sell", "baltop", "lottery", "kulcs", "market"}) {
            register(name, marketCommand, marketCommand);
        }
        safely("jogosultságok", () -> permissions.load(configs.get("permissions"), problems));
        startTasks();
        kingdoms = new IceMineKingdoms(this);
        safely("Kingdoms", kingdoms::onEnable);
        ready = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (profiles.attach(player)) {
                gens.preload(player.getUniqueId());
                gens.attach(player);
                prefixes.apply(player);
                ranks.apply(player);
                ModeHandler handler = modes.currentHandler(player);
                if (handler != null) {
                    handler.enter(player);
                }
                boards.rebuild(player);
            }
        }
        leaderboards.refresh();
        // a LuckPerms csoportok (ranks.yml -> luckperms) a szerver teljes elindulása után jönnek létre
        Bukkit.getScheduler().runTaskLater(this, () -> safely("LuckPerms rangok", () -> {
            hu.icemine.core.rank.LuckPermsSetup.run(getDataFolder(), configs.get("ranks"), getLogger());
            Bukkit.getScheduler().runTaskLater(this, ranks::refreshAll, 60L);
        }), 40L);
        getLogger().info("IceMine " + getDescription().getVersion() + " elindult" + (problems.isEmpty() ? "." : ", " + problems.size() + " config figyelmeztetéssel."));
    }

    private void safely(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "Hiba történt a betöltéskor: " + name + ". Ez a rész nem fog működni:", throwable);
        }
    }

    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Hiányzó parancs a plugin.yml-ből: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void loadLobbyParts(List<String> problems) {
        safely("lobbyvédelem", () -> protection.load(configs.get("lobby").getStringList("protection.allow-interact")));
        safely("portálok", () -> portals.load(configs.get("lobby"), problems));
        safely("kozmetikák", () -> cosmetics.load(configs.get("lobby"), problems));
        safely("kapcsolatok", () -> contacts.load(configs.get("contacts"), problems));
    }

    public void reloadLobbyParts() {
        List<String> problems = new ArrayList<>();
        loadLobbyParts(problems);
        safely("parkour", () -> parkour.load(configs.get("parkour"), problems));
        safely("hologramok", () -> {
            holograms.load(configs.get("lobby"), problems);
            holograms.spawnAll();
        });
        safely("NPC-k", () -> {
            npcs.load(configs.get("lobby"), problems);
            npcs.spawnAll();
        });
        configs.report(problems);
    }

    private void startTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        long autosave = Math.max(30, configs.get("config").getLong("autosave-seconds", 60)) * 20L;
        long boardTicks = Math.max(10, configs.get("scoreboard").getLong("update-ticks", 20));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            combo.tick();
            parkour.tick();
        }, 5L, 5L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, cosmetics::tick, 4L, 4L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, creative::tick, 1L, 1L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, combat::tick, 10L, 10L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, nameTags::tick, 20L, 5L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, boards::tick, boardTicks, boardTicks));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, boards::fastTick, 3L, 2L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            gens.tick();
            fyland.tick();
            market.tick();
            comboArena.tickAbilities();
        }, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            holograms.tick();
            npcs.tick();
            modeLobbies.tick();
        }, 40L, 40L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            profiles.saveDirty();
            gens.saveAll();
            fyland.saveAll();
        }, autosave, autosave));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, leaderboards::refresh, 200L, 1200L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, market::minute, 1200L, 1200L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, duels::tick, 20L, 20L));
        // a LuckPermsben átírt rangok fél percen belül életbe lépnek, újrabelépés nélkül is
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, ranks::refreshAll, 600L, 600L));
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (!ready) {
            if (database != null) {
                database.close();
            }
            return;
        }
        if (kingdoms != null) {
            safely("Kingdoms leállítás", kingdoms::onDisable);
        }
        if (duels != null) {
            safely("Duels leállítás", duels::shutdownAll);
        }
        if (market != null) {
            safely("piac leállítás", market::shutdown);
        }
        modes.shutdown();
        combat.shutdown();
        holograms.despawn();
        if (modeLobbies != null) {
            modeLobbies.despawn();
        }
        npcs.despawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            prefixes.clear(player);
        }
        ranks.clearAll();
        profiles.saveAllNow();
        if (iceCoins != null) {
            iceCoins.stop();
        }
        if (nameTags != null) {
            nameTags.cleanup();
        }
        inventories.close();
        database.close();
        ready = false;
    }

    public List<String> reloadConfigs(String file) {
        List<String> problems = file.equals("all") ? configs.loadAll() : configs.reload(file);
        YamlConfiguration main = configs.get("config");
        boolean all = file.equals("all");
        if (all || file.equals("economy")) {
            safely("pénzrendszer", () -> economy.load(configs.get("economy"), problems));
        }
        if (all || file.equals("modes")) {
            safely("játékmódok", () -> modes.load(configs.get("modes"), problems));
        }
        if (all || file.equals("ranks")) {
            safely("rangok", () -> {
                ranks.load(configs.get("ranks"), problems);
                ranks.refreshAll();
            });
        }
        if (all || file.equals("permissions")) {
            safely("jogosultságok", () -> permissions.load(configs.get("permissions"), problems));
        }
        if (all || file.equals("combat-tag")) {
            safely("combat tag", () -> combat.load(configs.get("combat-tag"), problems));
        }
        if (all || file.equals("combofly")) {
            safely("combofly", () -> combo.load(configs.get("combofly"), problems));
        }
        if (all || file.equals("lobby") || file.equals("contacts") || file.equals("parkour")) {
            if (all || file.equals("lobby")) {
                safely("lobby", () -> lobby.load(configs.get("lobby"), problems));
            }
            loadLobbyParts(problems);
            safely("parkour", () -> parkour.load(configs.get("parkour"), problems));
            if (all || file.equals("lobby")) {
                safely("hologramok", () -> {
                    holograms.load(configs.get("lobby"), problems);
                    holograms.spawnAll();
                });
                safely("NPC-k", () -> {
                    npcs.load(configs.get("lobby"), problems);
                    npcs.spawnAll();
                });
            }
        }
        if (all || file.equals("config")) {
            safely("toplisták", () -> leaderboards.load(main, problems));
            leaderboards.refresh();
            safely("IceCoin", () -> iceCoins.load(main, problems));
            safely("névtáblák", () -> nameTags.load(main));
            safely("frissítés", () -> updater.load(main));
        }
        if (all || file.equals(hu.icemine.core.market.Market.FILE) || file.equals("economy")) {
            safely("piac", () -> market.load(configs.get(hu.icemine.core.market.Market.FILE), problems));
        }
        if (all || file.equals(hu.icemine.core.creative.CreativeMode.FILE)) {
            safely("Creative", () -> creative.load(configs.get(hu.icemine.core.creative.CreativeMode.FILE), problems));
        }
        if (all || file.equals(hu.icemine.core.fyland.FylandMode.FILE)) {
            safely("Fyland", () -> fyland.load(configs.get(hu.icemine.core.fyland.FylandMode.FILE), problems));
        }
        if (all || file.equals(ComboArenaMode.FILE)) {
            safely("ComboFly aréna", () -> comboArena.load(configs.get(ComboArenaMode.FILE), problems));
        }
        if (all || file.equals("gens")) {
            safely("Gens", () -> gens.load(configs.get("gens"), problems));
        }
        if (all || file.equals("duels")) {
            safely("Duels", () -> duels.load(configs.get("duels"), problems));
        }
        if (all || file.equals(hu.icemine.core.bedwars.Bedwars.FILE)) {
            safely("BedWars", () -> bedwars.load(configs.get(hu.icemine.core.bedwars.Bedwars.FILE), problems));
        }
        if (all || file.equals("modes") || file.equals(hu.icemine.core.modelobby.ModeLobbies.FILE)) {
            safely("mód lobbyk", () -> {
                modeLobbies.load(configs.get(hu.icemine.core.modelobby.ModeLobbies.FILE), problems);
                modeLobbies.spawnAll();
            });
        }
        if (all || file.equals("scoreboard")) {
            safely("scoreboard", () -> boards.load(configs.get("scoreboard"), problems));
        }
        startTasks();
        configs.report(problems);
        return problems;
    }

    public void rebuild(String target) {
        switch (target) {
            case "lobby":
                for (Player player : lobby.world().getPlayers()) {
                    parkour.stop(player, true);
                }
                holograms.despawn();
                npcs.despawn();
                lobby.rebuild();
                break;
            case "combofly":
                comboArena.rebuild();
                break;
            case "gens":
                gens.rebuildHub();
                break;
            default:
                if (target.startsWith(hu.icemine.core.modelobby.ModeLobbies.PREFIX)) {
                    modeLobbies.rebuild(target.substring(hu.icemine.core.modelobby.ModeLobbies.PREFIX.length()));
                }
                break;
        }
    }

    public boolean setModeSpawn(Player player, String mode, String extra) {
        if (mode.startsWith(hu.icemine.core.modelobby.ModeLobbies.PREFIX)) {
            return modeLobbies.setSpawn(player, mode.substring(hu.icemine.core.modelobby.ModeLobbies.PREFIX.length()));
        }
        Location location = player.getLocation();
        YamlConfiguration config;
        switch (mode) {
            case "combofly":
                if (player.getWorld() != comboArena.world()) {
                    return false;
                }
                comboArena.setSpawn(location);
                return true;
            case "gens":
                if (player.getWorld() != gens.world()) {
                    return false;
                }
                config = configs.get("gens");
                Locs.write(config, "hub", location);
                configs.save("gens");
                reloadConfigs("gens");
                return true;
            default:
                return false;
        }
    }

    public void adminLog(CommandSender sender, String action, String target, String detail) {
        String actor = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "CONSOLE";
        String name = sender.getName();
        getLogger().info("[Admin] " + name + ": " + action + (target == null ? "" : " " + target) + (detail == null ? "" : " (" + detail + ")"));
        if (!configs.get("config").getBoolean("admin-log", true)) {
            return;
        }
        long time = System.currentTimeMillis();
        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO im_admin_log (actor, action, target, detail, time) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, actor);
                statement.setString(2, action);
                statement.setString(3, target);
                statement.setString(4, name + (detail == null ? "" : ": " + detail));
                statement.setLong(5, time);
                statement.executeUpdate();
            }
        });
    }

    public void cleanItems(Player player) {
        if (!lobby.cleanupForeignItems()) {
            return;
        }
        boolean inLobby = lobby.inLobby(player);
        boolean inModeLobby = modeLobbies != null && modeLobbies.isLobbyWorld(player.getWorld());
        boolean inPvp = comboArena.inWorld(player);
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            String tag = Items.tagOf(item);
            boolean lobbyItem = tag != null && (tag.startsWith("lobby:") || tag.startsWith("parkour:"));
            boolean pvpItem = Items.kitOf(item) != null || (tag != null && tag.startsWith("pvp:"));
            boolean modeLobbyItem = tag != null && tag.startsWith(hu.icemine.core.modelobby.ModeLobbies.ITEM);
            if ((lobbyItem && !inLobby) || (pvpItem && !inPvp) || (modeLobbyItem && !inModeLobby)) {
                inventory.setItem(i, null);
            }
        }
    }

    public ConfigFiles configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public Economy economy() {
        return economy;
    }

    public Prefixes prefixes() {
        return prefixes;
    }

    public hu.icemine.core.rank.Ranks ranks() {
        return ranks;
    }

    public hu.icemine.core.command.PermissionSetup permissions() {
        return permissions;
    }

    /** A PvP mód (ComboFly aréna), ahol a játékos most van, vagy null. */
    public hu.icemine.core.kitpvp.PvpMode pvpModeOf(Player player) {
        if (comboArena != null && comboArena.inWorld(player)) {
            return comboArena;
        }
        return null;
    }

    public ModeService modes() {
        return modes;
    }

    public TeleportGuard guard() {
        return guard;
    }

    public CombatTag combat() {
        return combat;
    }

    public ComboFly combo() {
        return combo;
    }

    public LobbyModule lobby() {
        return lobby;
    }

    public LobbyMenus menus() {
        return menus;
    }

    public Portals portals() {
        return portals;
    }

    public hu.icemine.core.modelobby.ModeLobbies modeLobbies() {
        return modeLobbies;
    }

    public hu.icemine.core.lobby.LobbyClicks lobbyClicks() {
        return lobbyClicks;
    }

    public Holograms holograms() {
        return holograms;
    }

    public Npcs npcs() {
        return npcs;
    }

    public ContactBook contacts() {
        return contacts;
    }

    public Cosmetics cosmetics() {
        return cosmetics;
    }

    public ParkourService parkour() {
        return parkour;
    }

    public BoardService boards() {
        return boards;
    }

    public Leaderboards leaderboards() {
        return leaderboards;
    }

    public InventoryStore inventories() {
        return inventories;
    }

    public hu.icemine.core.market.Market market() {
        return market;
    }

    public Updater updater() {
        return updater;
    }

    public hu.icemine.core.board.TabList tabList() {
        return tabList;
    }

    public hu.icemine.core.rank.NameTags nameTags() {
        return nameTags;
    }

    public hu.icemine.core.creative.CreativeMode creative() {
        return creative;
    }

    public hu.icemine.core.fyland.FylandMode fyland() {
        return fyland;
    }

    public ComboArenaMode comboArena() {
        return comboArena;
    }

    public Duels duels() {
        return duels;
    }

    public IceMineKingdoms kingdoms() {
        return kingdoms;
    }

    public hu.icemine.core.economy.IceCoins iceCoins() {
        return iceCoins;
    }

    public hu.icemine.core.bedwars.Bedwars bedwars() {
        return bedwars;
    }

    public GensMode gens() {
        return gens;
    }

    public GensMenu gensMenu() {
        return gensMenu;
    }

    public AuthHook auth() {
        return auth;
    }
}
