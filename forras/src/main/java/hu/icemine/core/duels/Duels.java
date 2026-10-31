package hu.icemine.core.duels;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.combo.Knockback;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.Attributes;
import hu.icemine.core.util.ClickText;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class Duels implements Listener, ModeHandler {
    public static final String ID = "duels";
    public static final String FILE = "duels";
    private final IceMineCore core;
    private final Map<String, DuelKit> kits = new LinkedHashMap<>();
    private final List<ArenaSlot> slots = new ArrayList<>();
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<UUID, Match> watching = new HashMap<>();
    private final Map<String, List<Duels.Waiting>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> queued = new HashMap<>();
    private final Map<UUID, Map<UUID, Duels.Invite>> invites = new HashMap<>();
    private final Map<UUID, Duels.Last> lastMatch = new HashMap<>();
    private final Map<UUID, Boolean> rankedChoice = new HashMap<>();
    private final DuelsMenu menu;
    private World world;
    private int kFactor = 32;
    private int maxSeconds = 600;
    private long rewardWin = 10L;
    private long rewardRanked = 20L;

    public Duels(IceMineCore iceminecore) {
        this.core = iceminecore;
        this.menu = new DuelsMenu(iceminecore, this);
    }

    public IceMineCore core() {
        return this.core;
    }

    public DuelsMenu menu() {
        return this.menu;
    }

    public void load(YamlConfiguration yamlconfiguration, List<String> list) {
        this.kits.clear();
        ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("modes");
        if (configurationsection != null) {
            for (String s : configurationsection.getKeys(false)) {
                ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
                if (configurationsection1 != null && configurationsection1.getBoolean("enabled", true)) {
                    this.kits.put(s.toLowerCase(Locale.ROOT), DuelKit.read(s.toLowerCase(Locale.ROOT), configurationsection1, list));
                }
            }
        }

        this.kFactor = Math.max(4, Math.min(64, yamlconfiguration.getInt("elo.k-factor", 32)));
        this.maxSeconds = Math.max(60, Math.min(3600, yamlconfiguration.getInt("max-minutes", 10) * 60));
        this.rewardWin = Math.max(0L, yamlconfiguration.getLong("rewards.win", 10L));
        this.rewardRanked = Math.max(0L, yamlconfiguration.getLong("rewards.ranked-win", 20L));
        String s1 = yamlconfiguration.getString("world", "duels");
        if (this.world == null || !this.world.getName().equals(s1)) {
            this.world = Bukkit.getWorld(s1);
            if (this.world == null) {
                WorldCreator worldcreator = new WorldCreator(s1);
                worldcreator.type(WorldType.FLAT);
                worldcreator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
                worldcreator.generateStructures(false);
                this.world = worldcreator.createWorld();
            }
        }

        if (this.world == null) {
            list.add("duels.yml -> world: nem sikerült létrehozni a világot (" + s1 + ")");
        } else {
            this.world.setDifficulty(Difficulty.NORMAL);
            this.world.setSpawnFlags(false, false);
            this.world.setPVP(true);

            for (String[] astring : new String[][]{
                {"doDaylightCycle", "false"},
                {"doWeatherCycle", "false"},
                {"doMobSpawning", "false"},
                {"doFireTick", "false"},
                {"randomTickSpeed", "0"},
                {"announceAdvancements", "false"},
                {"showDeathMessages", "false"},
                {"doImmediateRespawn", "true"},
                {"keepInventory", "true"},
                {"naturalRegeneration", "true"},
                {"mobGriefing", "false"}
            }) {
                this.world.setGameRuleValue(astring[0], astring[1]);
            }

            this.world.setStorm(false);
            this.world.setThundering(false);
            this.world.setTime(6000L);
            if (this.matches.isEmpty()) {
                this.loadArenas(yamlconfiguration, list);
            }
        }
    }

    private void loadArenas(YamlConfiguration yamlconfiguration, List<String> list) {
        this.slots.clear();
        ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("arenas");
        if (configurationsection == null) {
            list.add("duels.yml -> arenas: nincs egy aréna sem");
        } else {
            int i = 0;
            boolean flag = false;

            for (String s : configurationsection.getKeys(false)) {
                ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
                if (configurationsection1 != null) {
                    List<String> list1 = configurationsection1.getStringList("themes");
                    if (list1.isEmpty()) {
                        list1 = List.of("forest");
                    }

                    int j = Math.max(1, Math.min(20, configurationsection1.getInt("count", 2)));

                    for (int k = 0; k < j; k++) {
                        String s1 = ((String)list1.get(k % list1.size())).toLowerCase(Locale.ROOT);
                        int l = k * 160;
                        byte b0 = 100;
                        int i1 = i * 160;
                        String s2 = s + "-" + (k + 1);
                        DuelArenaBuild duelarenabuild = new DuelArenaBuild((j1, k1, l1, s3) -> {}, l, b0, i1, s, s1);
                        duelarenabuild.build();
                        ArenaSlot arenaslot = new ArenaSlot(s2, s.toLowerCase(Locale.ROOT), s1, this.world, l, b0, i1, duelarenabuild.layout());
                        if (!yamlconfiguration.getBoolean("built." + s2, false)) {
                            this.rebuild(arenaslot);
                            yamlconfiguration.set("built." + s2, true);
                            flag = true;
                        }

                        this.slots.add(arenaslot);
                    }

                    i++;
                }
            }

            if (flag) {
                this.core.configs().save("duels");
            }
        }
    }

    void rebuild(ArenaSlot arenaslot) {
        Placer placer = new Placer(this.world, this.core.getLogger());
        int[] aint = arenaslot.bounds;
        placer.clear(aint[0] - 2, aint[1] - 2, aint[2] - 2, aint[3] + 2, aint[4] + 2, aint[5] + 2);
        new DuelArenaBuild(placer, arenaslot.x, arenaslot.y, arenaslot.z, arenaslot.type, arenaslot.theme).build();
        this.clearEntities(arenaslot);
    }

    public int rebuildFree() {
        int i = 0;

        for (ArenaSlot arenaslot : this.slots) {
            if (arenaslot.free()) {
                this.rebuild(arenaslot);
                i++;
            }
        }

        return i;
    }

    private void clearEntities(ArenaSlot arenaslot) {
        int[] aint = arenaslot.bounds;
        Location location = new Location(this.world, (aint[0] + aint[3]) / 2.0, (aint[1] + aint[4]) / 2.0, (aint[2] + aint[5]) / 2.0);

        for (Entity entity : this.world
            .getNearbyEntities(location, (aint[3] - aint[0]) / 2.0 + 4.0, (aint[4] - aint[1]) / 2.0 + 4.0, (aint[5] - aint[2]) / 2.0 + 4.0)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    public Map<String, DuelKit> kits() {
        return this.kits;
    }

    public DuelKit kit(String s) {
        return s == null ? null : this.kits.get(s.toLowerCase(Locale.ROOT));
    }

    public World duelWorld() {
        return this.world;
    }

    public Match match(Player player) {
        return this.matches.get(player.getUniqueId());
    }

    public List<Match> running() {
        List<Match> arraylist = new ArrayList<>();

        for (Match match : this.matches.values()) {
            if (!arraylist.contains(match)) {
                arraylist.add(match);
            }
        }

        return arraylist;
    }

    public boolean ranked(Player player) {
        return this.rankedChoice.getOrDefault(player.getUniqueId(), false);
    }

    public void toggleRanked(Player player) {
        this.rankedChoice.put(player.getUniqueId(), !this.ranked(player));
    }

    public int elo(Player player, String s) {
        PlayerProfile playerprofile = this.core.profiles().profile(player);
        if (playerprofile == null) {
            return 1000;
        }

        long i = playerprofile.stat("duels.elo." + s);
        return i <= 0L ? 1000 : (int)i;
    }

    public int queueSize(String s, boolean flag) {
        List<Duels.Waiting> list = this.queues.get(s + (flag ? ":r" : ":u"));
        return list == null ? 0 : list.size();
    }

    public int playing(String s) {
        byte b0 = 0;

        for (Match match : this.running()) {
            if (match.kit.id.equals(s)) {
                b0 += 2;
            }
        }

        return b0;
    }

    public String queuedFor(Player player) {
        return this.queued.get(player.getUniqueId());
    }

    private boolean inLobby(Player player) {
        String s = this.core.modes().current(player);
        return s == null || s.equals("lobby") || s.startsWith("lobby_");
    }

    private boolean available(Player player, boolean flag) {
        if (this.matches.containsKey(player.getUniqueId())) {
            if (flag) {
                this.core.messages().send(player, "duels.already-playing");
            }

            return false;
        } else if (this.core.combat().tagged(player)) {
            if (flag) {
                this.core.messages().send(player, "combat.blocked-generic");
            }

            return false;
        } else if (!this.inLobby(player)) {
            if (flag) {
                this.core.messages().send(player, "duels.only-lobby");
            }

            return false;
        } else {
            if (this.core.parkour().running(player)) {
                this.core.parkour().stop(player, true);
            }

            return true;
        }
    }

    public void join(Player player, String s, boolean flag) {
        DuelKit duelkit = this.kit(s);
        if (duelkit == null) {
            this.core.messages().send(player, "duels.unknown-mode");
        } else if (!duelkit.allowed(player)) {
            this.core.messages().send(player, "general.no-permission");
        } else {
            if (flag && !duelkit.ranked) {
                flag = false;
            }

            if (this.available(player, true)) {
                this.leaveQueue(player, false);
                String s1 = duelkit.id + (flag ? ":r" : ":u");
                this.queues.computeIfAbsent(s1, s2 -> new ArrayList<>()).add(new Duels.Waiting(player.getUniqueId(), this.elo(player, duelkit.id)));
                this.queued.put(player.getUniqueId(), s1);
                this.core
                    .messages()
                    .send(player, "duels.queued", "%mode%", duelkit.name, "%type%", this.core.messages().raw(flag ? "duels.ranked" : "duels.unranked"));
                this.matchmake();
            }
        }
    }

    public boolean leaveQueue(Player player, boolean flag) {
        String s = this.queued.remove(player.getUniqueId());
        if (s == null) {
            return false;
        }

        List<Duels.Waiting> list = this.queues.get(s);
        if (list != null) {
            list.removeIf(duels$waiting -> duels$waiting.id.equals(player.getUniqueId()));
        }

        if (flag) {
            this.core.messages().send(player, "duels.left-queue");
        }

        return true;
    }

    private void matchmake() {
        long i = System.currentTimeMillis();

        for (Entry<String, List<Duels.Waiting>> entry : this.queues.entrySet()) {
            List<Duels.Waiting> list = entry.getValue();
            list.removeIf(duels$waiting2 -> {
                Player player2 = Bukkit.getPlayer(duels$waiting2.id);
                boolean flag1 = player2 == null || this.matches.containsKey(duels$waiting2.id) || this.core.combat().tagged(player2) || !this.inLobby(player2);
                if (flag1) {
                    this.queued.remove(duels$waiting2.id);
                }

                return flag1;
            });
            if (list.size() >= 2) {
                boolean flag = ((String)entry.getKey()).endsWith(":r");
                DuelKit duelkit = this.kit(((String)entry.getKey()).substring(0, ((String)entry.getKey()).indexOf(58)));
                if (duelkit != null) {
                    for (int j = 0; j < list.size(); j++) {
                        Duels.Waiting duels$waiting = (Duels.Waiting)list.get(j);

                        for (int k = j + 1; k < list.size(); k++) {
                            Duels.Waiting duels$waiting1 = (Duels.Waiting)list.get(k);
                            if (!flag
                                || Elo.fits(
                                    duels$waiting.elo, (int)((i - duels$waiting.since) / 1000L), duels$waiting1.elo, (int)((i - duels$waiting1.since) / 1000L)
                                )) {
                                Player player = Bukkit.getPlayer(duels$waiting.id);
                                Player player1 = Bukkit.getPlayer(duels$waiting1.id);
                                if (player != null && player1 != null) {
                                    if (this.freeSlot(duelkit) == null) {
                                        return;
                                    }

                                    list.remove(k);
                                    list.remove(j);
                                    this.queued.remove(duels$waiting.id);
                                    this.queued.remove(duels$waiting1.id);
                                    this.start(duelkit, flag, 1, player, player1);
                                    this.matchmake();
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private ArenaSlot freeSlot(DuelKit duelkit) {
        List<ArenaSlot> arraylist = new ArrayList<>();

        for (ArenaSlot arenaslot : this.slots) {
            if (arenaslot.free() && arenaslot.type.equals(duelkit.arena)) {
                arraylist.add(arenaslot);
            }
        }

        return arraylist.isEmpty() ? null : (ArenaSlot)arraylist.get((int)(Math.random() * arraylist.size()));
    }

    public void invite(Player player, Player player1, String s, int i) {
        DuelKit duelkit = this.kit(s);
        if (duelkit == null) {
            this.core.messages().send(player, "duels.unknown-mode");
        } else if (player.equals(player1)) {
            this.core.messages().send(player, "duels.self");
        } else if (this.available(player, true)) {
            if (!this.available(player1, false)) {
                this.core.messages().send(player, "duels.target-busy", "%player%", player1.getName());
            } else {
                Map<UUID, Duels.Invite> map = this.invites.get(player.getUniqueId());
                if (map != null && map.containsKey(player1.getUniqueId())) {
                    this.accept(player, player1.getName());
                } else {
                    this.invites
                        .computeIfAbsent(player1.getUniqueId(), uuid -> new HashMap<>())
                        .put(player.getUniqueId(), new Duels.Invite(player.getUniqueId(), duelkit.id, i));
                    this.core.messages().send(player, "duels.invite-sent", "%player%", player1.getName(), "%mode%", duelkit.name, "%rounds%", this.rounds(i));
                    player1.sendMessage(
                        Text.color(
                            this.core
                                .messages()
                                .prefixed("duels.invite-received", "%player%", player.getName(), "%mode%", duelkit.name, "%rounds%", this.rounds(i))
                        )
                    );
                    String s1 = this.core.messages().raw("duels.invite-buttons").replace("%player%", player.getName());
                    player1.sendMessage(ClickText.line(s1, "&7Kattints: &f%value%", "%value%"));
                    player1.playSound(player1.getLocation(), "minecraft:block.note_block.pling", 0.8F, 1.4F);
                }
            }
        }
    }

    private String rounds(int i) {
        return i > 1 ? this.core.messages().raw("duels.best-of").replace("%rounds%", String.valueOf(i)) : "";
    }

    public void accept(Player player, String s) {
        Map<UUID, Duels.Invite> map = this.invites.get(player.getUniqueId());
        if (map != null && !map.isEmpty()) {
            Duels.Invite duels$invite = null;
            if (s == null) {
                for (Duels.Invite duels$invite1 : map.values()) {
                    if (duels$invite == null || duels$invite1.at > duels$invite.at) {
                        duels$invite = duels$invite1;
                    }
                }
            } else {
                for (Duels.Invite duels$invite2 : map.values()) {
                    Player player1 = Bukkit.getPlayer(duels$invite2.from);
                    if (player1 != null && player1.getName().equalsIgnoreCase(s)) {
                        duels$invite = duels$invite2;
                    }
                }
            }

            if (duels$invite != null && System.currentTimeMillis() - duels$invite.at <= 60000L) {
                Player player2 = Bukkit.getPlayer(duels$invite.from);
                if (player2 == null) {
                    map.remove(duels$invite.from);
                    this.core.messages().send(player, "general.no-player");
                } else {
                    DuelKit duelkit = this.kit(duels$invite.kit);
                    if (duelkit == null || !this.available(player, true) || !this.available(player2, false)) {
                        this.core.messages().send(player, "duels.target-busy", "%player%", player2.getName());
                    } else if (this.freeSlot(duelkit) == null) {
                        this.core.messages().send(player, "duels.no-arena");
                    } else {
                        map.remove(duels$invite.from);
                        this.leaveQueue(player, false);
                        this.leaveQueue(player2, false);
                        this.start(duelkit, false, duels$invite.bestOf, player2, player);
                    }
                }
            } else {
                this.core.messages().send(player, "duels.no-invite");
            }
        } else {
            this.core.messages().send(player, "duels.no-invite");
        }
    }

    public void deny(Player player, String s) {
        Map<UUID, Duels.Invite> map = this.invites.get(player.getUniqueId());
        if (map == null) {
            this.core.messages().send(player, "duels.no-invite");
        } else {
            Iterator<Entry<UUID, Duels.Invite>> iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<UUID, Duels.Invite> entry = iterator.next();
                Player player1 = Bukkit.getPlayer((UUID)entry.getKey());
                if (s == null || player1 != null && player1.getName().equalsIgnoreCase(s)) {
                    iterator.remove();
                    if (player1 != null) {
                        this.core.messages().send(player1, "duels.invite-denied", "%player%", player.getName());
                    }
                }
            }

            this.core.messages().send(player, "duels.denied");
        }
    }

    public void rematch(Player player) {
        Duels.Last duels$last = this.lastMatch.get(player.getUniqueId());
        if (duels$last != null && System.currentTimeMillis() - duels$last.at <= 60000L) {
            Player player1 = Bukkit.getPlayer(duels$last.opponent);
            if (player1 == null) {
                this.core.messages().send(player, "general.no-player");
            } else {
                this.invite(player, player1, duels$last.kit, duels$last.bestOf);
            }
        } else {
            this.core.messages().send(player, "duels.no-rematch");
        }
    }

    void start(DuelKit duelkit, boolean flag, int i, Player player, Player player1) {
        ArenaSlot arenaslot = this.freeSlot(duelkit);
        if (arenaslot == null) {
            this.core.messages().send(player, "duels.no-arena");
            this.core.messages().send(player1, "duels.no-arena");
        } else {
            Match match = new Match(this, duelkit, arenaslot, flag, i, player, player1);
            arenaslot.match = match;
            this.matches.put(player.getUniqueId(), match);
            this.matches.put(player1.getUniqueId(), match);
            this.invites.remove(player.getUniqueId());
            this.invites.remove(player1.getUniqueId());

            for (Player player2 : new Player[]{player, player1}) {
                Player player3 = player2 == player ? player1 : player;
                this.core
                    .messages()
                    .send(
                        player2,
                        "duels.found",
                        "%player%",
                        player3.getName(),
                        "%mode%",
                        duelkit.name,
                        "%type%",
                        this.core.messages().raw(flag ? "duels.ranked" : "duels.unranked"),
                        "%elo%",
                        flag ? " &8(&f" + this.elo(player3, duelkit.id) + " Elo&8)" : ""
                    );
            }

            this.prepareRound(match);
        }
    }

    private void prepareRound(Match match) {
        match.state = Match.State.COUNTDOWN;
        match.timer = 3;
        match.resetHits();

        for (DuelKit.Side duelkit$side : DuelKit.Side.values()) {
            Player player = match.player(duelkit$side);
            if (player != null) {
                this.core.guard().allowNext(player);
                player.teleport(match.spawn(duelkit$side));
                this.setup(player, match, duelkit$side);
            }
        }

        this.countdown(match);
    }

    private void setup(Player player, Match match, DuelKit.Side duelkit$side) {
        player.setGameMode(match.kit.usesBlocks() ? GameMode.SURVIVAL : GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);

        try {
            player.setHealth(player.getMaxHealth());
        } catch (IllegalArgumentException illegalargumentexception) {
        }

        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExp(0.0F);
        player.setLevel(0);
        match.kit.equip(player, duelkit$side);
        Attributes.oldCombat(player, match.kit.oldCombat);
        player.setMaximumNoDamageTicks(match.kit.hitDelay);
        player.setNoDamageTicks(0);
    }

    private void countdown(Match match) {
        for (Player player : this.players(match, true)) {
            if (match.timer > 0) {
                player.sendTitle(Text.color("&e&l" + match.timer), Text.color(match.kit.name), 0, 22, 0);
                player.playSound(player.getLocation(), "minecraft:block.note_block.hat", 0.8F, 1.0F);
            }
        }
    }

    List<Player> players(Match match, boolean flag) {
        List<Player> arraylist = new ArrayList<>();

        for (DuelKit.Side duelkit$side : DuelKit.Side.values()) {
            Player player = match.player(duelkit$side);
            if (player != null) {
                arraylist.add(player);
            }
        }

        if (flag) {
            for (UUID uuid : match.spectators) {
                Player player1 = Bukkit.getPlayer(uuid);
                if (player1 != null) {
                    arraylist.add(player1);
                }
            }
        }

        return arraylist;
    }

    public void tick() {
        for (Match match : this.running()) {
            switch (match.state) {
                case COUNTDOWN:
                    match.timer--;
                    if (match.timer <= 0) {
                        match.state = Match.State.FIGHT;

                        for (Player player : this.players(match, true)) {
                            player.sendTitle(Text.color(this.core.messages().raw("duels.fight")), "", 0, 20, 8);
                            player.playSound(player.getLocation(), "minecraft:entity.ender_dragon.growl", 0.6F, 1.6F);
                        }
                        break;
                    }

                    this.countdown(match);
                    break;
                case FIGHT:
                    match.elapsed++;
                    if (match.elapsed >= this.maxSeconds) {
                        this.end(match, null);
                    }
                    break;
                case ROUND_END:
                    match.timer--;
                    if (match.timer <= 0) {
                        match.round++;
                        if (match.kit.usesBlocks() && match.kit.rule != DuelKit.Rule.BRIDGE) {
                            this.rebuild(match.slot);
                        }

                        this.prepareRound(match);
                    }
                    break;
                case ENDED:
                    match.timer--;
                    if (match.timer <= 0) {
                        this.finish(match);
                    }
            }
        }

        long i = System.currentTimeMillis();

        for (Map<UUID, Duels.Invite> map : this.invites.values()) {
            map.values().removeIf(duels$invite -> i - duels$invite.at > 60000L);
        }

        this.lastMatch.values().removeIf(duels$last -> i - duels$last.at > 60000L);
        this.matchmake();

        for (Entry<UUID, String> entry : this.queued.entrySet()) {
            Player player1 = Bukkit.getPlayer((UUID)entry.getKey());
            if (player1 != null) {
                String s = (String)entry.getValue();
                DuelKit duelkit = this.kit(s.substring(0, s.indexOf(58)));
                player1.sendActionBar(Text.color(this.core.messages().raw("duels.searching").replace("%mode%", duelkit == null ? "?" : duelkit.name)));
            }
        }
    }

    void roundWon(Match match, DuelKit.Side duelkit$side, String s) {
        if (match.state == Match.State.FIGHT) {
            match.addWin(duelkit$side);
            Player player = match.player(duelkit$side);
            Player player1 = match.player(duelkit$side == DuelKit.Side.RED ? DuelKit.Side.BLUE : DuelKit.Side.RED);
            if (player != null && player1 != null) {
                this.core.cosmetics().playKill(player, player1.getLocation());
            }

            if (match.wins(duelkit$side) >= match.needed()) {
                this.end(match, duelkit$side == DuelKit.Side.RED ? match.red : match.blue);
            } else {
                match.state = Match.State.ROUND_END;
                match.timer = 3;

                for (Player player2 : this.players(match, true)) {
                    player2.sendTitle(
                        Text.color(this.core.messages().raw("duels.round-won").replace("%player%", player == null ? "?" : player.getName())),
                        Text.color("&7" + match.redWins + " &8- &7" + match.blueWins),
                        0,
                        40,
                        10
                    );
                }

                if (s != null) {
                    for (Player player3 : this.players(match, true)) {
                        this.core.messages().send(player3, s, "%player%", player == null ? "?" : player.getName());
                    }
                }
            }
        }
    }

    void end(Match match, UUID uuid) {
        if (match.state != Match.State.ENDED) {
            match.state = Match.State.ENDED;
            match.timer = 5;
            match.winner = uuid;
            String s = uuid == null ? null : match.name(uuid);
            String s1 = uuid == null ? null : match.name(match.opponent(uuid));

            for (Player player : this.players(match, true)) {
                if (uuid == null) {
                    player.sendTitle(Text.color(this.core.messages().raw("duels.draw-title")), "", 5, 60, 10);
                } else if (player.getUniqueId().equals(uuid)) {
                    player.sendTitle(Text.color(this.core.messages().raw("duels.win-title")), Text.color("&7" + match.kit.name), 5, 60, 10);
                    player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", 0.8F, 1.0F);
                } else if (match.involves(player.getUniqueId())) {
                    player.sendTitle(Text.color(this.core.messages().raw("duels.lose-title")), Text.color("&7" + s), 5, 60, 10);
                } else {
                    player.sendTitle(Text.color("&e" + s), Text.color(this.core.messages().raw("duels.won-sub")), 5, 60, 10);
                }
            }

            if (uuid != null) {
                Player player3 = Bukkit.getPlayer(uuid);
                UUID uuid1 = match.opponent(uuid);
                Player player1 = Bukkit.getPlayer(uuid1);
                String s2 = "";
                if (match.ranked) {
                    int i = player3 != null ? this.elo(player3, match.kit.id) : 1000;
                    int j = player1 != null ? this.elo(player1, match.kit.id) : 1000;
                    int[] aint = Elo.result(i, j, this.kFactor);
                    this.setElo(uuid, match.kit.id, aint[0]);
                    this.setElo(uuid1, match.kit.id, aint[1]);
                    s2 = " &8(&a+" + aint[2] + " &8/ &c-" + aint[2] + " Elo&8)";
                }

                this.record(uuid, match.kit.id, true);
                this.record(uuid1, match.kit.id, false);
                long k = match.ranked ? this.rewardRanked : this.rewardWin;
                if (player3 != null && k > 0L) {
                    this.core.economy().deposit(player3, this.core.economy().defaultCurrency(), k, "duels:win");
                }

                for (Player player2 : this.players(match, true)) {
                    this.core
                        .messages()
                        .send(
                            player2,
                            "duels.result",
                            "%winner%",
                            s,
                            "%loser%",
                            s1,
                            "%mode%",
                            match.kit.name,
                            "%score%",
                            match.redWins + " - " + match.blueWins,
                            "%elo%",
                            s2
                        );
                }

                if (player3 != null && player1 != null) {
                    double d0 = Math.round(player3.getHealth() * 5.0) / 10.0;
                    this.core.messages().send(player1, "duels.health-left", "%player%", s, "%health%", String.valueOf(d0));
                }
            }

            for (DuelKit.Side duelkit$side : DuelKit.Side.values()) {
                UUID uuid2 = duelkit$side == DuelKit.Side.RED ? match.red : match.blue;
                UUID uuid3 = match.opponent(uuid2);
                this.lastMatch.put(uuid2, new Duels.Last(uuid3, match.kit.id, match.bestOf));
                Player player4 = Bukkit.getPlayer(uuid2);
                if (player4 != null) {
                    player4.sendMessage(ClickText.line(this.core.messages().raw("duels.rematch-button"), "&7Kattints: &f%value%", "%value%"));
                }
            }
        }
    }

    private void setElo(UUID uuid, String s, int i) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerProfile playerprofile = player == null ? null : this.core.profiles().profile(player);
        if (playerprofile != null) {
            playerprofile.setStat("duels.elo." + s, i);
            playerprofile.maxStat("duels.elo_best", i);
        }
    }

    private void record(UUID uuid, String s, boolean flag) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerProfile playerprofile = player == null ? null : this.core.profiles().profile(player);
        if (playerprofile != null) {
            playerprofile.addStat("duels.played", 1L);
            playerprofile.addStat("duels." + s + (flag ? ".wins" : ".losses"), 1L);
            if (flag) {
                playerprofile.addStat("duels.wins", 1L);
                long i = playerprofile.addStat("duels.streak", 1L);
                playerprofile.maxStat("duels.best_streak", i);
            } else {
                playerprofile.addStat("duels.losses", 1L);
                playerprofile.setStat("duels.streak", 0L);
            }
        }
    }

    void finish(Match match) {
        for (UUID uuid : new UUID[]{match.red, match.blue}) {
            this.matches.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getWorld() == this.world) {
                this.reset(player);
                this.sendHome(player);
            }
        }

        for (UUID uuid1 : new ArrayList<>(match.spectators)) {
            this.watching.remove(uuid1);
            Player player1 = Bukkit.getPlayer(uuid1);
            if (player1 != null && player1.getWorld() == this.world) {
                this.sendHome(player1);
            }
        }

        match.spectators.clear();
        if (match.kit.usesBlocks()) {
            this.rebuild(match.slot);
        } else {
            this.clearEntities(match.slot);
        }

        match.slot.match = null;
        this.matchmake();
    }

    private void reset(Player player) {
        this.core.combat().clear(player);
        Attributes.oldCombat(player, false);
        player.setMaximumNoDamageTicks(20);

        for (PotionEffect potioneffect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(potioneffect.getType());
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setFireTicks(0);
    }

    void sendHome(Player player) {
        Location location = this.core.modeLobbies() != null && this.core.modeLobbies().has("duels") ? this.core.modeLobbies().entry("duels") : null;
        if (location == null) {
            this.core.modes().send(player, "lobby", true);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            this.core.guard().allowNext(player);
            player.teleport(location);
        }
    }

    public void forfeit(Player player) {
        Match match = this.matches.get(player.getUniqueId());
        if (match != null) {
            if (match.state != Match.State.ENDED) {
                for (Player player1 : this.players(match, true)) {
                    if (!player1.equals(player)) {
                        this.core.messages().send(player1, "duels.forfeit", "%player%", player.getName());
                    }
                }

                this.end(match, match.opponent(player.getUniqueId()));
            }

            this.matches.remove(player.getUniqueId());
            this.reset(player);
        }
    }

    public void spectate(Player player, Player player1) {
        Match match = this.matches.get(player1.getUniqueId());
        if (match == null) {
            this.core.messages().send(player, "duels.not-playing", "%player%", player1.getName());
        } else if (!this.matches.containsKey(player.getUniqueId()) && this.available(player, true)) {
            this.leaveQueue(player, false);
            match.spectators.add(player.getUniqueId());
            this.watching.put(player.getUniqueId(), match);
            this.core.guard().allowNext(player);
            player.teleport(match.slot.red.clone().add(0.0, 4.0, 0.0));
            player.setGameMode(GameMode.SPECTATOR);
            this.core.messages().send(player, "duels.spectating", "%red%", match.redName, "%blue%", match.blueName);

            for (Player player2 : this.players(match, false)) {
                this.core.messages().send(player2, "duels.spectator-joined", "%player%", player.getName());
            }
        }
    }

    public void stopSpectating(Player player) {
        Match match = this.watching.remove(player.getUniqueId());
        if (match != null) {
            match.spectators.remove(player.getUniqueId());
        }

        if (player.getWorld() == this.world) {
            this.sendHome(player);
        }
    }

    public boolean spectating(Player player) {
        return this.watching.containsKey(player.getUniqueId());
    }

    @Override
    public String id() {
        return "duels";
    }

    @Override
    public World world() {
        return this.world;
    }

    @Override
    public Location spawn(Player player) {
        Match match = this.matches.get(player.getUniqueId());
        if (match != null) {
            DuelKit.Side duelkit$side = match.side(player.getUniqueId());
            return match.spawn(duelkit$side);
        } else {
            Location location = this.core.modeLobbies() != null && this.core.modeLobbies().has("duels") ? this.core.modeLobbies().entry("duels") : null;
            return location != null ? location : this.core.lobby().spawn(player);
        }
    }

    @Override
    public void enter(Player player) {
        if (!this.matches.containsKey(player.getUniqueId()) && !this.watching.containsKey(player.getUniqueId()) && !player.hasPermission("icemine.admin.build")
            )
         {
            Bukkit.getScheduler()
                .runTask(
                    this.core,
                    () -> {
                        if (player.isOnline()
                            && player.getWorld() == this.world
                            && !this.matches.containsKey(player.getUniqueId())
                            && !this.watching.containsKey(player.getUniqueId())) {
                            this.sendHome(player);
                        }
                    }
                );
        }
    }

    @Override
    public void leave(Player player, boolean flag) {
        if (this.matches.containsKey(player.getUniqueId())) {
            this.forfeit(player);
        }

        if (this.watching.containsKey(player.getUniqueId())) {
            Match match = this.watching.remove(player.getUniqueId());
            match.spectators.remove(player.getUniqueId());
        }

        if (!flag && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> map) {
        Match match = this.matches.get(player.getUniqueId());
        if (match == null) {
            match = this.watching.get(player.getUniqueId());
        }

        map.put("%mode%", "&e&lDuels");
        if (match == null) {
            map.put("%duel_mode%", "-");
            map.put("%duel_opponent%", "-");
            map.put("%duel_score%", "-");
            map.put("%duel_hits%", "-");
            map.put("%duel_elo%", "-");
        } else {
            UUID uuid = player.getUniqueId();
            map.put("%duel_mode%", match.kit.name + (match.ranked ? " &8(&6Ranked&8)" : ""));
            map.put("%duel_opponent%", match.involves(uuid) ? match.name(match.opponent(uuid)) : match.redName + " &7vs &f" + match.blueName);
            map.put("%duel_score%", match.score(uuid));
            map.put("%duel_hits%", match.kit.rule == DuelKit.Rule.BOXING ? match.hitScore(uuid) : "-");
            map.put("%duel_elo%", match.ranked ? String.valueOf(this.elo(player, match.kit.id)) : "-");
            map.put("%duel_time%", Text.timer(match.elapsed * 1000L));
        }
    }

    public void shutdownAll() {
        for (Match match : this.running()) {
            if (match.state != Match.State.ENDED) {
                this.end(match, null);
            }

            this.finish(match);
        }
    }

    private Match fighting(Entity entity) {
        return entity instanceof Player && entity.getWorld() == this.world ? this.matches.get(entity.getUniqueId()) : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent entitydamagebyentityevent) {
        if (entitydamagebyentityevent.getEntity().getWorld() == this.world && entitydamagebyentityevent.getEntity() instanceof Player) {
            Player player = (Player)entitydamagebyentityevent.getEntity();
            Player player1 = entitydamagebyentityevent.getDamager() instanceof Player
                ? (Player)entitydamagebyentityevent.getDamager()
                : (
                    entitydamagebyentityevent.getDamager() instanceof Projectile
                            && ((Projectile)entitydamagebyentityevent.getDamager()).getShooter() instanceof Player
                        ? (Player)((Projectile)entitydamagebyentityevent.getDamager()).getShooter()
                        : null
                );
            Match match = this.matches.get(player.getUniqueId());
            if (match != null && player1 != null && match.state == Match.State.FIGHT && match.involves(player1.getUniqueId())) {
                if (!player1.equals(player)) {
                    if (entitydamagebyentityevent.getCause().name().equals("ENTITY_SWEEP_ATTACK") && match.kit.oldCombat) {
                        entitydamagebyentityevent.setCancelled(true);
                    } else {
                        DuelKit.Side duelkit$side = match.side(player1.getUniqueId());
                        switch (match.kit.rule) {
                            case BOXING:
                                entitydamagebyentityevent.setDamage(0.0);
                                if (entitydamagebyentityevent.getDamager() instanceof Player) {
                                    int j = match.addHit(duelkit$side);
                                    player1.sendActionBar(Text.color("&e" + match.hitScore(player1.getUniqueId()) + " &7ütés"));
                                    if (j >= match.kit.target) {
                                        this.roundWon(match, duelkit$side, "duels.boxing-win");
                                    }
                                }
                                break;
                            case SUMO:
                                entitydamagebyentityevent.setDamage(0.0);
                                break;
                            case COMBOFLY:
                                if (entitydamagebyentityevent.getDamager() instanceof Player) {
                                    int i = match.combo.merge(player1.getUniqueId(), 1, Integer::sum);
                                    match.combo.put(player.getUniqueId(), 0);
                                    entitydamagebyentityevent.setDamage(entitydamagebyentityevent.getDamage() * this.core.combo().damageFor("combofly"));
                                    Knockback.Settings knockback$settings = this.core.combo().settingsFor("combofly");
                                    UUID uuid = player.getUniqueId();
                                    Bukkit.getScheduler()
                                        .runTask(
                                            this.core,
                                            () -> {
                                                Player player3 = Bukkit.getPlayer(uuid);
                                                if (player3 != null && !player3.isDead() && player3.getWorld() == player1.getWorld()) {
                                                    Location location = player1.getLocation();
                                                    Location location1 = player3.getLocation();
                                                    double[] adouble = Knockback.compute(
                                                        location1.getX() - location.getX(),
                                                        location1.getZ() - location.getZ(),
                                                        Math.toRadians(location.getYaw()),
                                                        i,
                                                        knockback$settings,
                                                        heightAboveGround(player3, knockback$settings.maxHeight)
                                                    );
                                                    player3.setVelocity(new Vector(adouble[0], adouble[1], adouble[2]));
                                                }
                                            }
                                        );
                                    if (i >= 2) {
                                        player1.sendActionBar(Text.color("&b&lCOMBO &f» &b" + i + "x"));
                                    }
                                }
                        }
                    }
                }
            } else {
                if (player1 != null && !player1.equals(player)) {
                    entitydamagebyentityevent.setCancelled(true);
                }
            }
        }
    }

    private static double heightAboveGround(Player player, double d0) {
        Location location = player.getLocation();
        int i = (int)Math.ceil(d0) + 4;

        for (int j = 0; j <= i; j++) {
            if (location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() - j - 1, location.getBlockZ()).getType().isSolid()) {
                return j + (location.getY() - location.getBlockY());
            }
        }

        return i + 1;
    }

    private static boolean hasTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent entitydamageevent) {
        Match match = this.fighting(entitydamageevent.getEntity());
        if (match == null) {
            if (entitydamageevent.getEntity() instanceof Player && entitydamageevent.getEntity().getWorld() == this.world) {
                entitydamageevent.setCancelled(true);
            }
        } else {
            Player player = (Player)entitydamageevent.getEntity();
            if (match.state != Match.State.FIGHT) {
                entitydamageevent.setCancelled(true);
            } else if (match.kit.rule != DuelKit.Rule.BOXING && match.kit.rule != DuelKit.Rule.SUMO) {
                if (!(entitydamageevent.getFinalDamage() < player.getHealth()) && !hasTotem(player)) {
                    entitydamageevent.setCancelled(true);
                    this.died(match, player);
                }
            } else {
                if (!(entitydamageevent instanceof EntityDamageByEntityEvent)) {
                    entitydamageevent.setCancelled(true);
                }
            }
        }
    }

    private void died(Match match, Player player) {
        DuelKit.Side duelkit$side = match.side(player.getUniqueId());
        DuelKit.Side duelkit$side1 = duelkit$side == DuelKit.Side.RED ? DuelKit.Side.BLUE : DuelKit.Side.RED;
        if (match.kit.rule == DuelKit.Rule.BRIDGE) {
            Player player1 = match.player(duelkit$side1);

            for (Player player2 : this.players(match, true)) {
                this.core.messages().send(player2, "duels.bridge-death", "%player%", player.getName(), "%killer%", player1 == null ? "?" : player1.getName());
            }

            this.respawn(match, player, duelkit$side);
        } else {
            this.roundWon(match, duelkit$side1, null);
        }
    }

    private void respawn(Match match, Player player, DuelKit.Side duelkit$side) {
        this.core.guard().allowNext(player);
        player.teleport(match.spawn(duelkit$side));
        this.setup(player, match, duelkit$side);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent playermoveevent) {
        Location location = playermoveevent.getTo();
        if (location != null && location.getWorld() == this.world) {
            Player player = playermoveevent.getPlayer();
            Match match = this.matches.get(player.getUniqueId());
            if (match != null) {
                Location location1 = playermoveevent.getFrom();
                if (match.state != Match.State.COUNTDOWN && match.state != Match.State.ROUND_END) {
                    if (match.state == Match.State.FIGHT) {
                        DuelKit.Side duelkit$side = match.side(player.getUniqueId());
                        if (location.getY() < match.slot.fallY) {
                            if (match.kit.rule == DuelKit.Rule.SUMO) {
                                this.roundWon(match, duelkit$side == DuelKit.Side.RED ? DuelKit.Side.BLUE : DuelKit.Side.RED, "duels.sumo-win");
                            } else {
                                this.died(match, player);
                            }
                        } else if (match.kit.rule == DuelKit.Rule.BRIDGE) {
                            int[] aint = duelkit$side == DuelKit.Side.RED ? match.slot.goalBlue : match.slot.goalRed;
                            int[] aint1 = duelkit$side == DuelKit.Side.RED ? match.slot.goalRed : match.slot.goalBlue;
                            if (ArenaSlot.inside(aint, location)) {
                                this.scored(match, player, duelkit$side);
                            } else if (ArenaSlot.inside(aint1, location)) {
                                this.respawn(match, player, duelkit$side);
                            }
                        } else {
                            if (!match.slot.contains(location)) {
                                this.respawn(match, player, duelkit$side);
                            }
                        }
                    }
                } else {
                    if (location1.getX() != location.getX() || location1.getZ() != location.getZ()) {
                        Location location2 = location1.clone();
                        location2.setYaw(location.getYaw());
                        location2.setPitch(location.getPitch());
                        playermoveevent.setTo(location2);
                    }
                }
            }
        }
    }

    private void scored(Match match, Player player, DuelKit.Side duelkit$side) {
        match.addWin(duelkit$side);

        for (Player player1 : this.players(match, true)) {
            player1.sendTitle(
                Text.color(duelkit$side.chat + player.getName() + " &ególt lőtt!"), Text.color("&c" + match.redWins + " &8- &9" + match.blueWins), 0, 30, 8
            );
            player1.playSound(player1.getLocation(), "minecraft:entity.firework_rocket.blast", 0.8F, 1.0F);
        }

        if (match.wins(duelkit$side) >= match.kit.target) {
            this.end(match, player.getUniqueId());
        } else {
            this.prepareRound(match);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent blockplaceevent) {
        Block block = blockplaceevent.getBlock();
        if (block.getWorld() == this.world) {
            Player player = blockplaceevent.getPlayer();
            Match match = this.matches.get(player.getUniqueId());
            if (match == null) {
                if (player.getGameMode() != GameMode.CREATIVE || !player.hasPermission("icemine.admin.build")) {
                    blockplaceevent.setCancelled(true);
                }
            } else {
                Location location = block.getLocation().add(0.5, 0.5, 0.5);
                boolean flag = near(match.slot.goalRed, location) || near(match.slot.goalBlue, location);
                if (match.kit.usesBlocks()
                    && match.state == Match.State.FIGHT
                    && match.slot.contains(location)
                    && !flag
                    && block.getY() <= match.slot.y + 24 - 4) {
                    match.placed.add(key(block));
                } else {
                    blockplaceevent.setCancelled(true);
                }
            }
        }
    }

    private static boolean near(int[] aint, Location location) {
        return aint == null
            ? false
            : location.getX() >= aint[0] - 1
                && location.getX() <= aint[3] + 2
                && location.getZ() >= aint[2] - 1
                && location.getZ() <= aint[5] + 2
                && location.getY() <= aint[4] + 3;
    }

    private static long key(Block block) {
        return (block.getX() & 67108863L) << 38 | (block.getZ() & 67108863L) << 12 | block.getY() & 4095;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent blockbreakevent) {
        Block block = blockbreakevent.getBlock();
        if (block.getWorld() == this.world) {
            Player player = blockbreakevent.getPlayer();
            Match match = this.matches.get(player.getUniqueId());
            if (match == null) {
                if (player.getGameMode() != GameMode.CREATIVE || !player.hasPermission("icemine.admin.build")) {
                    blockbreakevent.setCancelled(true);
                }
            } else {
                boolean flag = match.kit.build
                    && match.kit.rule != DuelKit.Rule.BRIDGE
                    && match.slot.contains(block.getLocation())
                    && block.getY() > match.slot.y - 1
                    && block.getType() != Material.BARRIER
                    && block.getType() != Material.BEDROCK;
                if (match.state == Match.State.FIGHT && (flag || match.placed.remove(key(block)))) {
                    blockbreakevent.setDropItems(match.kit.build);
                } else {
                    blockbreakevent.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent playerbucketemptyevent) {
        if (playerbucketemptyevent.getBlock().getWorld() == this.world) {
            Match match = this.matches.get(playerbucketemptyevent.getPlayer().getUniqueId());
            if (match == null || !match.kit.build || match.state != Match.State.FIGHT || !match.slot.contains(playerbucketemptyevent.getBlock().getLocation())) {
                playerbucketemptyevent.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent entityexplodeevent) {
        if (entityexplodeevent.getLocation().getWorld() == this.world) {
            entityexplodeevent.blockList().removeIf(block -> block.getType() == Material.BARRIER || block.getType() == Material.BEDROCK);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent playerdropitemevent) {
        if (playerdropitemevent.getPlayer().getWorld() == this.world) {
            playerdropitemevent.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent foodlevelchangeevent) {
        Match match = this.fighting(foodlevelchangeevent.getEntity());
        if (foodlevelchangeevent.getEntity().getWorld() == this.world && (match == null || !match.kit.hunger)) {
            foodlevelchangeevent.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent entityregainhealthevent) {
        Match match = this.fighting(entityregainhealthevent.getEntity());
        if (match != null && !match.kit.regen) {
            String s = entityregainhealthevent.getRegainReason().name();
            if (s.equals("SATIATED") || s.equals("REGEN")) {
                entityregainhealthevent.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent playerteleportevent) {
        Location location = playerteleportevent.getTo();
        if (location != null && location.getWorld() == this.world && playerteleportevent.getCause().name().equals("ENDER_PEARL")) {
            Match match = this.matches.get(playerteleportevent.getPlayer().getUniqueId());
            if (match != null && !match.slot.contains(location)) {
                playerteleportevent.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent playerportalevent) {
        if (playerportalevent.getFrom().getWorld() == this.world) {
            playerportalevent.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent entityportalevent) {
        if (entityportalevent.getFrom().getWorld() == this.world) {
            entityportalevent.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent playerquitevent) {
        Player player = playerquitevent.getPlayer();
        this.leaveQueue(player, false);
        this.invites.remove(player.getUniqueId());

        for (Map<UUID, Duels.Invite> map : this.invites.values()) {
            map.remove(player.getUniqueId());
        }

        this.rankedChoice.remove(player.getUniqueId());
        if (this.matches.containsKey(player.getUniqueId())) {
            this.forfeit(player);
        }

        Match match = this.watching.remove(player.getUniqueId());
        if (match != null) {
            match.spectators.remove(player.getUniqueId());
        }
    }

    public String status(Player player) {
        Match match = this.matches.get(player.getUniqueId());
        return match != null ? match.kit.name : null;
    }

    public static Layout layoutFor(String s, String s1) {
        DuelArenaBuild duelarenabuild = new DuelArenaBuild((i, j, k, s2) -> {}, 0, 0, 0, s, s1);
        duelarenabuild.build();
        return duelarenabuild.layout();
    }

    static final class Invite {
        final UUID from;
        final String kit;
        final int bestOf;
        final long at;

        Invite(UUID uuid, String s, int i) {
            this.from = uuid;
            this.kit = s;
            this.bestOf = i;
            this.at = System.currentTimeMillis();
        }
    }

    static final class Last {
        final UUID opponent;
        final String kit;
        final int bestOf;
        final long at;

        Last(UUID uuid, String s, int i) {
            this.opponent = uuid;
            this.kit = s;
            this.bestOf = i;
            this.at = System.currentTimeMillis();
        }
    }

    static final class Waiting {
        final UUID id;
        final long since;
        final int elo;

        Waiting(UUID uuid, int i) {
            this.id = uuid;
            this.since = System.currentTimeMillis();
            this.elo = i;
        }
    }
}
