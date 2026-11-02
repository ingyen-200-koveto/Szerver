package hu.icemine.kingdoms;

import hu.icemine.core.economy.Ledger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * A királyságok logikája: alapítás, tagok, rangok, földek, bank, Nexuspont, szintek, power, kapcsolatok, nemzetek,
 * fejlesztések. Az üzeneteket is ez küldi, a parancsok és a menük csak meghívják.
 */
public final class KingdomManager {

    private final IceMineKingdoms plugin;
    private final KingdomStore store;
    private final Map<UUID, Kingdom> kingdoms = new ConcurrentHashMap<>();
    private final Map<String, Kingdom> byName = new ConcurrentHashMap<>();
    private final Map<UUID, KPlayer> players = new ConcurrentHashMap<>();
    private final Map<UUID, Nation> nations = new ConcurrentHashMap<>();
    private final Map<Long, UUID> claimIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private final Map<UUID, UUID> nationInvites = new HashMap<>();
    private final Map<UUID, Long> confirm = new HashMap<>();
    private final Map<UUID, String> confirmWhat = new HashMap<>();

    KingdomManager(IceMineKingdoms plugin, KingdomStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    KConfig cfg() {
        return plugin.settings();
    }

    // ---- betöltés, mentés ----

    void load(KingdomStore.Loaded loaded) {
        kingdoms.clear();
        byName.clear();
        players.clear();
        nations.clear();
        claimIndex.clear();
        for (KPlayer player : loaded.players) {
            players.put(player.uuid, player);
        }
        for (Kingdom kingdom : loaded.kingdoms) {
            register(kingdom);
        }
        for (Nation nation : loaded.nations) {
            nation.kingdoms.removeIf(id -> !kingdoms.containsKey(id));
            if (!kingdoms.containsKey(nation.capital) || nation.kingdoms.isEmpty()) {
                continue;
            }
            nations.put(nation.id, nation);
        }
        for (Kingdom kingdom : kingdoms.values()) {
            if (kingdom.nation != null && !nations.containsKey(kingdom.nation)) {
                kingdom.nation = null;
                kingdom.dirty = true;
            }
            for (UUID member : kingdom.members.keySet()) {
                KPlayer player = players.computeIfAbsent(member, id -> new KPlayer(id, "?"));
                if (!kingdom.id.equals(player.kingdom)) {
                    player.kingdom = kingdom.id;
                    player.dirty = true;
                }
            }
        }
        for (KPlayer player : players.values()) {
            if (player.kingdom != null && (!kingdoms.containsKey(player.kingdom) || !kingdoms.get(player.kingdom).members.containsKey(player.uuid))) {
                player.kingdom = null;
                player.dirty = true;
            }
        }
    }

    private void register(Kingdom kingdom) {
        kingdoms.put(kingdom.id, kingdom);
        byName.put(kingdom.name.toLowerCase(Locale.ROOT), kingdom);
        for (long key : kingdom.claims) {
            UUID previous = claimIndex.putIfAbsent(key, kingdom.id);
            if (previous != null && !previous.equals(kingdom.id)) {
                plugin.getLogger().warning("Kingdoms: a " + Lands.text(key) + " chunk két királyságnál is szerepel, a második elengedi: " + kingdom.name);
                kingdom.claims.remove(key);
                kingdom.dirty = true;
            }
        }
    }

    /** A változott adatok mentése (percenként és leállításkor). */
    void saveDirty() {
        for (Kingdom kingdom : kingdoms.values()) {
            if (kingdom.dirty) {
                store.save(kingdom);
            }
        }
        for (KPlayer player : players.values()) {
            if (player.dirty) {
                store.save(player);
            }
        }
        for (Nation nation : nations.values()) {
            if (nation.dirty) {
                store.save(nation);
            }
        }
    }

    void saveAll() {
        for (Kingdom kingdom : kingdoms.values()) {
            store.save(kingdom);
        }
        for (KPlayer player : players.values()) {
            if (player.dirty || player.kingdom != null) {
                store.save(player);
            }
        }
        for (Nation nation : nations.values()) {
            store.save(nation);
        }
    }

    // ---- lekérdezések ----

    public KPlayer player(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> {
            KPlayer created = new KPlayer(id, nameOf(id));
            created.power = cfg().powerInitial;
            created.dirty = true;
            return created;
        });
    }

    public KPlayer player(Player player) {
        KPlayer data = player(player.getUniqueId());
        if (!player.getName().equals(data.name)) {
            data.name = player.getName();
            data.dirty = true;
        }
        return data;
    }

    public String nameOf(UUID uuid) {
        KPlayer known = players.get(uuid);
        if (known != null && known.name != null && !known.name.equals("?")) {
            return known.name;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline == null ? uuid.toString().substring(0, 8) : offline;
    }

    public UUID idOf(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (KPlayer player : players.values()) {
            if (player.name != null && player.name.equalsIgnoreCase(name)) {
                return player.uuid;
            }
        }
        return null;
    }

    public Kingdom kingdomOf(UUID player) {
        KPlayer data = players.get(player);
        return data == null || data.kingdom == null ? null : kingdoms.get(data.kingdom);
    }

    public Kingdom kingdom(UUID id) {
        return id == null ? null : kingdoms.get(id);
    }

    public Kingdom kingdom(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Kingdom> all() {
        return kingdoms.values();
    }

    public Collection<Nation> nations() {
        return nations.values();
    }

    public Nation nation(UUID id) {
        return id == null ? null : nations.get(id);
    }

    public Nation nation(String name) {
        for (Nation nation : nations.values()) {
            if (nation.name.equalsIgnoreCase(name)) {
                return nation;
            }
        }
        return null;
    }

    public Nation nationOf(Kingdom kingdom) {
        return kingdom == null ? null : nation(kingdom.nation);
    }

    public Kingdom owner(long chunk) {
        UUID id = claimIndex.get(chunk);
        return id == null ? null : kingdoms.get(id);
    }

    public Kingdom owner(Location location) {
        if (location.getWorld() != plugin.world()) {
            return null;
        }
        return owner(Lands.key(location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    public List<Player> online(Kingdom kingdom) {
        List<Player> list = new ArrayList<>();
        for (UUID member : kingdom.members.keySet()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /** Két királyság tényleges kapcsolata, a nemzetet és a fővárosok közös kapcsolatait is figyelembe véve. */
    public Relation relation(Kingdom a, Kingdom b) {
        if (a == null || b == null) {
            return Relation.NEUTRAL;
        }
        if (a.id.equals(b.id)) {
            return Relation.SELF;
        }
        if (a.nation != null && a.nation.equals(b.nation)) {
            return Relation.NATION;
        }
        Relation direct = Relation.resolve(a.stance(b.id), b.stance(a.id));
        if (direct != Relation.NEUTRAL) {
            return direct;
        }
        Kingdom capitalA = capital(a);
        Kingdom capitalB = capital(b);
        if (capitalA != a || capitalB != b) {
            return Relation.resolve(capitalA.stance(capitalB.id), capitalB.stance(capitalA.id));
        }
        return Relation.NEUTRAL;
    }

    private Kingdom capital(Kingdom kingdom) {
        Nation nation = nationOf(kingdom);
        Kingdom capital = nation == null ? null : kingdoms.get(nation.capital);
        return capital == null ? kingdom : capital;
    }

    public Relation relation(UUID player, Kingdom kingdom) {
        return relation(kingdomOf(player), kingdom);
    }

    public int level(Kingdom kingdom) {
        return cfg().level(kingdom.xp);
    }

    public int maxMembers(Kingdom kingdom) {
        KConfig.Upgrade upgrade = cfg().misc.get("max-members");
        int extra = upgrade == null ? 0 : (int) upgrade.value(kingdom.level(kingdom.misc, "max-members"));
        return cfg().maxMembersBase + cfg().maxMembersPerLevel * (level(kingdom) - 1) + extra;
    }

    public double power(Kingdom kingdom) {
        double total = 0;
        for (UUID member : kingdom.members.keySet()) {
            KPlayer player = players.get(member);
            total += player == null ? cfg().powerInitial : player.power;
        }
        return total;
    }

    public int nexusLevel(Kingdom kingdom) {
        for (Placed placed : kingdom.placed) {
            if (!placed.turret() && placed.type.equals("nexus")) {
                return placed.level;
            }
        }
        return 0;
    }

    /** Foglalható chunkok száma: alap + tagok + szint + fejlesztés + Nexus, és ha be van kapcsolva, a power is korlátozza. */
    public int maxClaims(Kingdom kingdom) {
        KConfig cfg = cfg();
        KConfig.Placeable nexus = cfg.structures.get("nexus");
        int nexusBonus = nexus == null ? 0 : (int) Math.round(nexus.values.getOrDefault("claims-per-level", 0.0) * nexusLevel(kingdom));
        int limit = cfg.claimsBase + cfg.claimsPerMember * kingdom.members.size() + cfg.claimsPerLevel * (level(kingdom) - 1)
                + cfg.claimsPerUpgrade * kingdom.level(kingdom.misc, "max-claims") + nexusBonus;
        if (cfg.powerEnabled) {
            int byPower = (int) Math.floor(Math.max(0, power(kingdom)) * cfg.claimsPerPower);
            limit = Math.min(limit, Math.max(cfg.freeClaims, byPower));
        }
        return Math.max(1, limit);
    }

    /** Erő a toplistához: szint, föld, tagok, power és a fejlesztések együtt. */
    public long might(Kingdom kingdom) {
        long upgrades = 0;
        for (int value : kingdom.misc.values()) {
            upgrades += value;
        }
        for (int value : kingdom.powerups.values()) {
            upgrades += value;
        }
        for (int value : kingdom.champion.values()) {
            upgrades += value;
        }
        return level(kingdom) * 100L + kingdom.claims.size() * 10L + kingdom.members.size() * 25L + Math.round(power(kingdom) * 5)
                + upgrades * 15L + kingdom.placed.size() * 20L + kingdom.resourcePoints / 100;
    }

    public List<Kingdom> top(Comparator<Kingdom> order) {
        List<Kingdom> list = new ArrayList<>(kingdoms.values());
        list.sort(order);
        return list;
    }

    public long nationMight(Nation nation) {
        long total = 0;
        for (UUID id : nation.kingdoms) {
            Kingdom kingdom = kingdoms.get(id);
            if (kingdom != null) {
                total += might(kingdom);
            }
        }
        return total;
    }

    // ---- megerősítés (feloszlatás, minden föld elengedése) ----

    private boolean confirmed(Player player, String what) {
        long now = System.currentTimeMillis();
        Long last = confirm.get(player.getUniqueId());
        if (last != null && now - last < 15_000L && what.equals(confirmWhat.get(player.getUniqueId()))) {
            confirm.remove(player.getUniqueId());
            confirmWhat.remove(player.getUniqueId());
            return true;
        }
        confirm.put(player.getUniqueId(), now);
        confirmWhat.put(player.getUniqueId(), what);
        return false;
    }

    // ---- alapítás, feloszlatás ----

    public boolean validName(String name) {
        return name != null && name.length() >= cfg().nameMin && name.length() <= cfg().nameMax && name.matches("[A-Za-z0-9_]+");
    }

    public Kingdom create(Player player, String name) {
        KPlayer data = player(player);
        if (data.kingdom != null) {
            plugin.send(player, "already-in-kingdom");
            return null;
        }
        if (!validName(name)) {
            plugin.send(player, "bad-name", "%min%", String.valueOf(cfg().nameMin), "%max%", String.valueOf(cfg().nameMax));
            return null;
        }
        if (kingdom(name) != null) {
            plugin.send(player, "name-taken");
            return null;
        }
        Kingdom kingdom = new Kingdom(UUID.randomUUID(), name, player.getUniqueId(), System.currentTimeMillis());
        for (Rank rank : cfg().defaultRanks) {
            kingdom.ranks.put(rank.id, rank.copy());
        }
        kingdom.members.put(player.getUniqueId(), Rank.KING);
        data.kingdom = kingdom.id;
        data.joined = System.currentTimeMillis();
        data.dirty = true;
        kingdom.log("Megalapította " + player.getName() + ".");
        kingdom.dirty = true;
        register(kingdom);
        store.save(kingdom);
        store.save(data);
        plugin.send(player, "created", "%kingdom%", kingdom.display());
        if (cfg().giveNexusOnCreate) {
            plugin.structures().giveNexus(player);
        }
        plugin.broadcast("created-broadcast", "%player%", player.getName(), "%kingdom%", kingdom.display());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        return kingdom;
    }

    public void disband(Player player) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (plugin.invasions().defending(kingdom) || plugin.invasions().attacking(kingdom)) {
            plugin.send(player, "invasion-running");
            return;
        }
        if (!confirmed(player, "disband")) {
            plugin.send(player, "disband-confirm");
            return;
        }
        String name = kingdom.display();
        disband(kingdom);
        plugin.broadcast("disbanded", "%kingdom%", name);
    }

    /** Feloszlatás (admin is hívhatja): földek, építmények, kapcsolatok, nemzet. */
    public void disband(Kingdom kingdom) {
        plugin.structures().removeAll(kingdom);
        for (long key : kingdom.claims) {
            claimIndex.remove(key);
        }
        for (UUID member : kingdom.members.keySet()) {
            KPlayer data = player(member);
            data.kingdom = null;
            data.kingdomChat = false;
            data.allyChat = false;
            data.autoClaim = false;
            data.dirty = true;
        }
        Nation nation = nationOf(kingdom);
        if (nation != null) {
            if (nation.capital.equals(kingdom.id)) {
                disbandNation(nation);
            } else {
                nation.kingdoms.remove(kingdom.id);
                nation.dirty = true;
            }
        }
        for (Kingdom other : kingdoms.values()) {
            if (other.relations.remove(kingdom.id) != null) {
                other.dirty = true;
            }
            other.relationRequests.remove(kingdom.id);
        }
        kingdoms.remove(kingdom.id);
        byName.remove(kingdom.name.toLowerCase(Locale.ROOT));
        store.deleteKingdom(kingdom.id);
    }

    // ---- tagok ----

    /** A játékos királysága, vagy üzenet, ha nincs neki. */
    public Kingdom mine(Player player) {
        Kingdom kingdom = kingdomOf(player.getUniqueId());
        if (kingdom == null) {
            plugin.send(player, "no-kingdom");
        }
        return kingdom;
    }

    /** A játékos királysága, ha megvan hozzá a jog (különben üzenet és null). */
    public Kingdom allowed(Player player, KPerm perm) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return null;
        }
        if (!kingdom.can(player.getUniqueId(), perm)) {
            plugin.send(player, "no-kingdom-permission", "%permission%", perm.title);
            return null;
        }
        return kingdom;
    }

    public void invite(Player player, String targetName) {
        Kingdom kingdom = allowed(player, KPerm.INVITE);
        if (kingdom == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.send(player, "no-player");
            return;
        }
        if (kingdomOf(target.getUniqueId()) != null) {
            plugin.send(player, "player-has-kingdom", "%player%", target.getName());
            return;
        }
        if (kingdom.members.size() >= maxMembers(kingdom)) {
            plugin.send(player, "kingdom-full", "%max%", String.valueOf(maxMembers(kingdom)));
            return;
        }
        invites.computeIfAbsent(target.getUniqueId(), id -> new HashMap<>()).put(kingdom.id, System.currentTimeMillis() + cfg().inviteSeconds * 1000L);
        plugin.send(player, "invited", "%player%", target.getName());
        plugin.click(target, "invite-received", "%player%", player.getName(), "%kingdom%", kingdom.display(), "%name%", kingdom.name);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
    }

    public boolean invited(UUID player, Kingdom kingdom) {
        Map<UUID, Long> list = invites.get(player);
        Long until = list == null ? null : list.get(kingdom.id);
        return until != null && until > System.currentTimeMillis();
    }

    public List<Kingdom> invitesOf(UUID player) {
        List<Kingdom> list = new ArrayList<>();
        Map<UUID, Long> map = invites.get(player);
        if (map != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : map.entrySet()) {
                Kingdom kingdom = kingdoms.get(entry.getKey());
                if (kingdom != null && entry.getValue() > now) {
                    list.add(kingdom);
                }
            }
        }
        return list;
    }

    public void join(Player player, String name) {
        Kingdom kingdom = kingdom(name);
        if (kingdom == null) {
            plugin.send(player, "no-such-kingdom");
            return;
        }
        if (kingdomOf(player.getUniqueId()) != null) {
            plugin.send(player, "already-in-kingdom");
            return;
        }
        if (!kingdom.open && !invited(player.getUniqueId(), kingdom)) {
            plugin.send(player, "no-invite");
            return;
        }
        if (kingdom.members.size() >= maxMembers(kingdom)) {
            plugin.send(player, "kingdom-full", "%max%", String.valueOf(maxMembers(kingdom)));
            return;
        }
        invites.remove(player.getUniqueId());
        kingdom.members.put(player.getUniqueId(), kingdom.lowest().id);
        KPlayer data = player(player);
        data.kingdom = kingdom.id;
        data.joined = System.currentTimeMillis();
        data.dirty = true;
        kingdom.log(player.getName() + " csatlakozott.");
        tell(kingdom, "joined", "%player%", player.getName());
        plugin.refreshTags(player);
    }

    public void leave(Player player) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "king-cant-leave");
            return;
        }
        removeMember(kingdom, player.getUniqueId());
        kingdom.log(player.getName() + " kilépett.");
        tell(kingdom, "left", "%player%", player.getName());
        plugin.send(player, "you-left");
        plugin.refreshTags(player);
    }

    private void removeMember(Kingdom kingdom, UUID member) {
        kingdom.members.remove(member);
        kingdom.dirty = true;
        KPlayer data = player(member);
        data.kingdom = null;
        data.kingdomChat = false;
        data.allyChat = false;
        data.autoClaim = false;
        data.dirty = true;
    }

    public void kick(Player player, String targetName) {
        Kingdom kingdom = allowed(player, KPerm.KICK);
        if (kingdom == null) {
            return;
        }
        UUID target = idOf(targetName);
        if (target == null || !kingdom.members.containsKey(target)) {
            plugin.send(player, "not-member", "%player%", targetName);
            return;
        }
        if (!kingdom.outranks(player.getUniqueId(), target)) {
            plugin.send(player, "cant-kick");
            return;
        }
        String name = nameOf(target);
        removeMember(kingdom, target);
        kingdom.log(name + " ki lett rúgva (" + player.getName() + ").");
        tell(kingdom, "kicked", "%player%", name);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.send(online, "you-were-kicked", "%kingdom%", kingdom.display());
            plugin.refreshTags(online);
        }
    }

    /** Rang állítása: csak a sajátodnál alacsonyabb rangú tagnak, és csak a sajátodnál alacsonyabb rangot adhatod. */
    public boolean setRank(Player player, UUID target, String rankId) {
        Kingdom kingdom = allowed(player, KPerm.SET_RANKS);
        if (kingdom == null) {
            return false;
        }
        Rank rank = kingdom.ranks.get(rankId);
        if (rank == null || !kingdom.members.containsKey(target)) {
            plugin.send(player, "not-member", "%player%", nameOf(target));
            return false;
        }
        if (rank.king()) {
            plugin.send(player, "use-king-command");
            return false;
        }
        Rank own = kingdom.rankOf(player.getUniqueId());
        boolean leader = player.getUniqueId().equals(kingdom.leader);
        if (!kingdom.outranks(player.getUniqueId(), target) || (!leader && own != null && rank.priority <= own.priority)) {
            plugin.send(player, "rank-too-high");
            return false;
        }
        kingdom.members.put(target, rank.id);
        kingdom.dirty = true;
        kingdom.log(nameOf(target) + " új rangja: " + rank.name + " (" + player.getName() + ").");
        tell(kingdom, "rank-set", "%player%", nameOf(target), "%rank%", rank.display());
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.refreshTags(online);
        }
        return true;
    }

    /** Egy ranggal feljebb vagy lejjebb. */
    public void shift(Player player, String targetName, boolean up) {
        Kingdom kingdom = mine(player);
        UUID target = idOf(targetName);
        if (kingdom == null) {
            return;
        }
        if (target == null || !kingdom.members.containsKey(target)) {
            plugin.send(player, "not-member", "%player%", targetName);
            return;
        }
        List<Rank> sorted = kingdom.sortedRanks();
        Rank current = kingdom.rankOf(target);
        int index = sorted.indexOf(current);
        int next = up ? index - 1 : index + 1;
        if (next < 1 || next >= sorted.size()) {
            plugin.send(player, up ? "already-highest" : "already-lowest", "%player%", nameOf(target));
            return;
        }
        setRank(player, target, sorted.get(next).id);
    }

    public void transfer(Player player, String targetName) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        UUID target = idOf(targetName);
        if (target == null || !kingdom.members.containsKey(target) || target.equals(player.getUniqueId())) {
            plugin.send(player, "not-member", "%player%", targetName);
            return;
        }
        List<Rank> sorted = kingdom.sortedRanks();
        kingdom.members.put(player.getUniqueId(), sorted.size() > 1 ? sorted.get(1).id : kingdom.lowest().id);
        kingdom.members.put(target, Rank.KING);
        kingdom.leader = target;
        kingdom.dirty = true;
        kingdom.log(nameOf(target) + " lett az új király.");
        tell(kingdom, "new-king", "%player%", nameOf(target));
        plugin.refreshTags(player);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.refreshTags(online);
        }
    }

    // ---- rangok szerkesztése ----

    private Kingdom rankEditor(Player player, Rank rank) {
        Kingdom kingdom = allowed(player, KPerm.MANAGE_RANKS);
        if (kingdom == null) {
            return null;
        }
        Rank own = kingdom.rankOf(player.getUniqueId());
        boolean leader = player.getUniqueId().equals(kingdom.leader);
        if (rank != null && (rank.king() || (!leader && own != null && rank.priority <= own.priority))) {
            plugin.send(player, "rank-too-high");
            return null;
        }
        return kingdom;
    }

    public Rank createRank(Player player, String name) {
        Kingdom kingdom = rankEditor(player, null);
        if (kingdom == null) {
            return null;
        }
        if (kingdom.ranks.size() >= 15) {
            plugin.send(player, "rank-limit");
            return null;
        }
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (id.isEmpty() || id.length() > 16 || kingdom.ranks.containsKey(id)) {
            plugin.send(player, "rank-bad-name");
            return null;
        }
        Rank lowest = kingdom.lowest();
        int priority = lowest.priority + 1;
        Rank rank = new Rank(id, name.length() > 16 ? name.substring(0, 16) : name, "&f", "♟", "STONE", priority, 0, KPerm.basic());
        kingdom.ranks.put(id, rank);
        kingdom.dirty = true;
        plugin.send(player, "rank-created", "%rank%", rank.display());
        return rank;
    }

    public void deleteRank(Player player, Rank rank) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom == null) {
            return;
        }
        if (kingdom.ranks.size() <= 2) {
            plugin.send(player, "rank-min");
            return;
        }
        kingdom.ranks.remove(rank.id);
        Rank fallback = kingdom.lowest();
        for (Map.Entry<UUID, String> entry : kingdom.members.entrySet()) {
            if (entry.getValue().equals(rank.id)) {
                entry.setValue(fallback.id);
            }
        }
        kingdom.dirty = true;
        plugin.send(player, "rank-deleted", "%rank%", rank.display(), "%fallback%", fallback.display());
    }

    public void renameRank(Player player, Rank rank, String name) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom == null) {
            return;
        }
        String clean = name.replace("&", "").trim();
        if (clean.isEmpty() || clean.length() > 16) {
            plugin.send(player, "rank-bad-name");
            return;
        }
        rank.name = clean;
        kingdom.dirty = true;
        plugin.send(player, "rank-renamed", "%rank%", rank.display());
    }

    private static final String[] COLORS = {"&f", "&7", "&8", "&e", "&6", "&c", "&4", "&d", "&5", "&b", "&3", "&9", "&a", "&2"};
    private static final String[] SYMBOLS = {"♟", "♞", "♝", "♜", "♛", "❖", "✦", "✪", "⚔", "☠", "❀", "⚒", "⛨", "☀"};

    public void cycleColor(Player player, Rank rank) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom != null) {
            rank.color = next(COLORS, rank.color);
            kingdom.dirty = true;
        }
    }

    public void cycleSymbol(Player player, Rank rank) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom != null) {
            rank.symbol = next(SYMBOLS, rank.symbol);
            kingdom.dirty = true;
        }
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    public void togglePerm(Player player, Rank rank, KPerm perm) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom == null) {
            return;
        }
        if (!kingdom.can(player.getUniqueId(), perm)) {
            plugin.send(player, "rank-cant-give", "%permission%", perm.title);
            return;
        }
        if (!rank.perms.remove(perm)) {
            rank.perms.add(perm);
        }
        kingdom.dirty = true;
    }

    public void changeMaxClaims(Player player, Rank rank, int delta) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom != null) {
            int value = rank.maxClaims + delta;
            rank.maxClaims = value < -1 ? -1 : Math.min(1000, value);
            kingdom.dirty = true;
        }
    }

    /** Egy hellyel feljebb vagy lejjebb a rangsorban (a királyt és a saját rangodat nem lehet átugrani). */
    public void moveRank(Player player, Rank rank, boolean up) {
        Kingdom kingdom = rankEditor(player, rank);
        if (kingdom == null) {
            return;
        }
        List<Rank> sorted = kingdom.sortedRanks();
        int index = sorted.indexOf(rank);
        int other = up ? index - 1 : index + 1;
        if (other < 1 || other >= sorted.size()) {
            return;
        }
        Rank swap = sorted.get(other);
        Rank own = kingdom.rankOf(player.getUniqueId());
        if (!player.getUniqueId().equals(kingdom.leader) && own != null && swap.priority <= own.priority) {
            plugin.send(player, "rank-too-high");
            return;
        }
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).priority = i;
        }
        int a = rank.priority;
        rank.priority = swap.priority;
        swap.priority = a;
        kingdom.dirty = true;
    }

    // ---- földek ----

    public boolean claim(Player player, boolean quiet) {
        Location at = player.getLocation();
        return claim(player, at, quiet);
    }

    /** A megadott hely chunkjának foglalása (a Nexus lerakásakor is ezt használjuk). */
    public boolean claim(Player player, Location at, boolean quiet) {
        if (at.getWorld() != plugin.world()) {
            plugin.send(player, "only-in-kingdoms");
            return false;
        }
        Kingdom kingdom = allowed(player, KPerm.CLAIM);
        if (kingdom == null) {
            return false;
        }
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        long key = Lands.key(cx, cz);
        if (plugin.chunkNearSpawn(cx, cz)) {
            plugin.send(player, "claim-spawn");
            return false;
        }
        if (!plugin.insideBorder(cx, cz)) {
            plugin.send(player, "claim-border");
            return false;
        }
        Kingdom owner = owner(key);
        if (owner != null) {
            if (!quiet) {
                plugin.send(player, owner == kingdom ? "claim-own" : "claim-taken", "%kingdom%", owner.display());
            }
            return false;
        }
        if (kingdom.claims.size() >= maxClaims(kingdom)) {
            plugin.send(player, "claim-limit", "%max%", String.valueOf(maxClaims(kingdom)));
            return false;
        }
        Rank rank = kingdom.rankOf(player.getUniqueId());
        if (rank != null && !rank.king() && rank.maxClaims >= 0 && kingdom.claimedBy(player.getUniqueId()) >= rank.maxClaims) {
            plugin.send(player, "claim-rank-limit", "%max%", String.valueOf(rank.maxClaims));
            return false;
        }
        if (cfg().claimAdjacent && !kingdom.claims.isEmpty()) {
            boolean touching = false;
            for (long next : Lands.neighbours(key)) {
                touching |= kingdom.claims.contains(next);
            }
            if (!touching) {
                plugin.send(player, "claim-adjacent");
                return false;
            }
        }
        int buffer = cfg().claimBuffer;
        for (int dx = -buffer; dx <= buffer; dx++) {
            for (int dz = -buffer; dz <= buffer; dz++) {
                Kingdom near = owner(Lands.key(cx + dx, cz + dz));
                if (near != null && near != kingdom) {
                    Relation relation = relation(kingdom, near);
                    if (relation != Relation.ALLY && relation != Relation.NATION) {
                        plugin.send(player, "claim-buffer", "%kingdom%", near.display());
                        return false;
                    }
                }
            }
        }
        boolean free = kingdom.claims.size() < cfg().freeClaims;
        long rp = free ? 0 : cfg().claimCostRp;
        long money = free ? 0 : cfg().claimCostMoney;
        if (kingdom.resourcePoints < rp) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(rp));
            return false;
        }
        if (kingdom.bank < money) {
            plugin.send(player, "not-enough-bank", "%need%", plugin.money(money));
            return false;
        }
        kingdom.resourcePoints -= rp;
        kingdom.bank -= money;
        kingdom.claims.add(key);
        kingdom.claimers.put(key, player.getUniqueId());
        claimIndex.put(key, kingdom.id);
        addXp(kingdom, cfg().xpPerClaim);
        kingdom.dirty = true;
        plugin.send(player, free ? "claimed-free" : "claimed", "%claims%", String.valueOf(kingdom.claims.size()), "%max%", String.valueOf(maxClaims(kingdom)),
                "%rp%", String.valueOf(rp));
        plugin.borders().show(player, cx, cz);
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        return true;
    }

    public boolean unclaim(Player player) {
        Location at = player.getLocation();
        Kingdom kingdom = allowed(player, KPerm.UNCLAIM);
        if (kingdom == null) {
            return false;
        }
        long key = Lands.key(at.getBlockX() >> 4, at.getBlockZ() >> 4);
        if (owner(key) != kingdom || at.getWorld() != plugin.world()) {
            plugin.send(player, "not-your-claim");
            return false;
        }
        String problem = unclaimProblem(kingdom, key);
        if (problem != null) {
            plugin.send(player, problem);
            return false;
        }
        release(kingdom, key, true);
        kingdom.log(player.getName() + " elengedett egy földet: " + Lands.text(key));
        plugin.send(player, "unclaimed", "%claims%", String.valueOf(kingdom.claims.size()), "%max%", String.valueOf(maxClaims(kingdom)));
        return true;
    }

    private String unclaimProblem(Kingdom kingdom, long key) {
        if (key == kingdom.nexusChunk()) {
            return "unclaim-nexus";
        }
        if (plugin.invasions().invadedChunk(key)) {
            return "invasion-running";
        }
        for (Placed placed : kingdom.placed) {
            if (placed.chunk() == key) {
                return "unclaim-structures";
            }
        }
        return null;
    }

    public void unclaimAll(Player player) {
        Kingdom kingdom = allowed(player, KPerm.UNCLAIM);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (plugin.invasions().defending(kingdom)) {
            plugin.send(player, "invasion-running");
            return;
        }
        if (!confirmed(player, "unclaimall")) {
            plugin.send(player, "unclaimall-confirm");
            return;
        }
        int count = 0;
        for (long key : new ArrayList<>(kingdom.claims)) {
            if (unclaimProblem(kingdom, key) == null) {
                release(kingdom, key, true);
                count++;
            }
        }
        kingdom.log(player.getName() + " elengedett " + count + " földet.");
        plugin.send(player, "unclaimed-all", "%count%", String.valueOf(count));
    }

    /** Föld elengedése (visszatérítéssel, ha fizetős volt). Inváziónál a nyertes kapja meg, ott nincs visszatérítés. */
    void release(Kingdom kingdom, long key, boolean refund) {
        if (!kingdom.claims.remove(key)) {
            return;
        }
        kingdom.claimers.remove(key);
        claimIndex.remove(key);
        if (refund && kingdom.claims.size() >= cfg().freeClaims) {
            kingdom.resourcePoints += cfg().claimCostRp * cfg().unclaimRefundPercent / 100;
        }
        kingdom.xp = Math.max(0, kingdom.xp - cfg().xpPerClaim);
        kingdom.dirty = true;
    }

    /** Invázió után: a föld átkerül a győzteshez (ha van még helye), különben szabad lesz. */
    void transferLand(Kingdom from, Kingdom to, long key) {
        release(from, key, false);
        from.landsLost++;
        if (to != null && to.claims.size() < maxClaims(to) && owner(key) == null) {
            to.claims.add(key);
            claimIndex.put(key, to.id);
            to.landsTaken++;
            to.dirty = true;
        }
    }

    public void setHome(Player player) {
        Kingdom kingdom = allowed(player, KPerm.SET_HOME);
        if (kingdom == null) {
            return;
        }
        if (owner(player.getLocation()) != kingdom) {
            plugin.send(player, "home-outside");
            return;
        }
        Location at = player.getLocation();
        kingdom.home = new double[]{at.getX(), at.getY(), at.getZ(), at.getYaw()};
        kingdom.dirty = true;
        plugin.send(player, "home-set");
    }

    public void home(Player player, String other) {
        Kingdom target;
        if (other == null) {
            target = allowed(player, KPerm.HOME);
        } else {
            target = kingdom(other);
            if (target == null) {
                plugin.send(player, "no-such-kingdom");
                return;
            }
            Relation relation = relation(player.getUniqueId(), target);
            if (relation != Relation.SELF && !relation.home) {
                plugin.send(player, "home-no-access", "%kingdom%", target.display());
                return;
            }
        }
        if (target == null) {
            return;
        }
        if (target.home == null) {
            plugin.send(player, "home-not-set");
            return;
        }
        if (plugin.combatTagged(player)) {
            plugin.send(player, "in-combat");
            return;
        }
        plugin.teleport(player, new Location(plugin.world(), target.home[0], target.home[1], target.home[2], (float) target.home[3], 0f));
    }

    // ---- bank és Nexuspont ----

    public void deposit(Player player, long amount) {
        Kingdom kingdom = mine(player);
        if (kingdom == null || amount <= 0) {
            return;
        }
        if (plugin.core().economy().withdraw(player, plugin.currency(), amount, "kingdoms:deposit") != Ledger.Result.OK) {
            plugin.send(player, "no-money", "%price%", plugin.money(amount));
            return;
        }
        kingdom.bank += amount;
        kingdom.bankLog(player.getName(), amount, "befizetés");
        plugin.send(player, "deposited", "%money%", plugin.money(amount), "%bank%", plugin.money(kingdom.bank));
    }

    public void withdraw(Player player, long amount) {
        Kingdom kingdom = allowed(player, KPerm.WITHDRAW);
        if (kingdom == null || amount <= 0) {
            return;
        }
        if (kingdom.bank < amount) {
            plugin.send(player, "not-enough-bank", "%need%", plugin.money(amount));
            return;
        }
        if (plugin.core().economy().deposit(player, plugin.currency(), amount, "kingdoms:withdraw") != Ledger.Result.OK) {
            plugin.send(player, "bank-error");
            return;
        }
        kingdom.bank -= amount;
        kingdom.bankLog(player.getName(), -amount, "kivét");
        plugin.send(player, "withdrew", "%money%", plugin.money(amount), "%bank%", plugin.money(kingdom.bank));
    }

    /** Bankpénz átváltása nexuspontra (money-per-point pénz = 1 NP). */
    public void convert(Player player, long points) {
        Kingdom kingdom = allowed(player, KPerm.WITHDRAW);
        if (kingdom == null || points <= 0) {
            return;
        }
        long cost = points * cfg().moneyPerPoint;
        if (kingdom.bank < cost) {
            plugin.send(player, "not-enough-bank", "%need%", plugin.money(cost));
            return;
        }
        kingdom.bank -= cost;
        kingdom.resourcePoints += points;
        kingdom.bankLog(player.getName(), -cost, points + " NP vásárlás");
        plugin.send(player, "converted", "%points%", String.valueOf(points), "%money%", plugin.money(cost));
    }

    /** Beadott tárgyak NP-re váltva (a menü hívja, a maradékot visszaadja). Visszaadja az NP-t. */
    public long donate(Player player, Kingdom kingdom, long points) {
        if (points <= 0) {
            return 0;
        }
        kingdom.resourcePoints += points;
        KPlayer data = player(player);
        data.donated += points;
        data.dirty = true;
        addXp(kingdom, points * cfg().xpPerResourcePoint);
        kingdom.dirty = true;
        return points;
    }

    public long pointsFor(String material) {
        return cfg().itemPoints.getOrDefault(material, 0L);
    }

    /** Királysági XP (a Dicsőség fejlesztés megnöveli); szintlépéskor szól a tagoknak. */
    public void addXp(Kingdom kingdom, long amount) {
        if (amount <= 0) {
            return;
        }
        KConfig.Upgrade glory = cfg().misc.get("glory");
        double bonus = glory == null ? 0 : glory.value(kingdom.level(kingdom.misc, "glory"));
        int before = level(kingdom);
        kingdom.xp += Math.round(amount * (1 + bonus / 100.0));
        kingdom.dirty = true;
        int after = level(kingdom);
        if (after > before) {
            kingdom.log("Szintet lépett: " + after + ".");
            tell(kingdom, "level-up", "%level%", String.valueOf(after));
            for (Player member : online(kingdom)) {
                member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            }
        }
    }

    // ---- fejlesztések ----

    public Map<String, Integer> levels(Kingdom kingdom, String category) {
        switch (category) {
            case "powerups":
                return kingdom.powerups;
            case "champion":
                return kingdom.champion;
            default:
                return kingdom.misc;
        }
    }

    public Map<String, KConfig.Upgrade> upgrades(String category) {
        switch (category) {
            case "powerups":
                return cfg().powerups;
            case "champion":
                return cfg().champion;
            default:
                return cfg().misc;
        }
    }

    public void buyUpgrade(Player player, String category, String id) {
        Kingdom kingdom = allowed(player, KPerm.UPGRADE);
        KConfig.Upgrade upgrade = upgrades(category).get(id);
        if (kingdom == null || upgrade == null) {
            return;
        }
        Map<String, Integer> levels = levels(kingdom, category);
        int level = kingdom.level(levels, id);
        long price = upgrade.priceFor(level);
        if (price < 0) {
            plugin.send(player, "upgrade-max", "%name%", upgrade.name);
            return;
        }
        if (level(kingdom) < upgrade.minLevel) {
            plugin.send(player, "need-level", "%level%", String.valueOf(upgrade.minLevel));
            return;
        }
        if (kingdom.resourcePoints < price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(price));
            return;
        }
        kingdom.resourcePoints -= price;
        levels.put(id, level + 1);
        kingdom.dirty = true;
        kingdom.log(player.getName() + " fejlesztett: " + plugin.strip(upgrade.name) + " " + (level + 1) + ".");
        plugin.send(player, "upgraded", "%name%", upgrade.name, "%level%", String.valueOf(level + 1), "%max%", String.valueOf(upgrade.max),
                "%price%", String.valueOf(price));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.3f);
    }

    public double powerup(Kingdom kingdom, String id) {
        KConfig.Upgrade upgrade = cfg().powerups.get(id);
        return kingdom == null || upgrade == null ? 0 : upgrade.value(kingdom.level(kingdom.powerups, id));
    }

    public boolean hasMisc(Kingdom kingdom, String id) {
        return kingdom != null && cfg().misc.containsKey(id) && kingdom.level(kingdom.misc, id) > 0;
    }

    // ---- pajzs és pacifizmus ----

    public void buyShield(Player player, int hours) {
        Kingdom kingdom = allowed(player, KPerm.UPGRADE);
        Long price = cfg().shields.get(hours);
        if (kingdom == null || price == null) {
            return;
        }
        if (plugin.invasions().defending(kingdom)) {
            plugin.send(player, "invasion-running");
            return;
        }
        if (kingdom.resourcePoints < price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(price));
            return;
        }
        kingdom.resourcePoints -= price;
        long now = System.currentTimeMillis();
        kingdom.shieldUntil = Math.max(now, kingdom.shieldUntil) + hours * 3_600_000L;
        kingdom.dirty = true;
        kingdom.log(player.getName() + " pajzsot vett (" + hours + " óra).");
        tell(kingdom, "shield-bought", "%player%", player.getName(), "%hours%", String.valueOf(hours));
    }

    public boolean shielded(Kingdom kingdom) {
        if (kingdom.shieldUntil > System.currentTimeMillis()) {
            return true;
        }
        Nation nation = nationOf(kingdom);
        Kingdom capital = nation == null ? null : kingdoms.get(nation.capital);
        return capital != null && capital != kingdom && capital.shieldUntil > System.currentTimeMillis() && plugin.getConfig().getBoolean("nations.shared-shield", true);
    }

    public void togglePacifist(Player player) {
        Kingdom kingdom = allowed(player, KPerm.SETTINGS);
        if (kingdom == null) {
            return;
        }
        if (!cfg().pacifismEnabled) {
            plugin.send(player, "pacifism-disabled");
            return;
        }
        long wait = kingdom.pacifistChanged + cfg().pacifistCooldownHours * 3_600_000L - System.currentTimeMillis();
        if (wait > 0) {
            plugin.send(player, "pacifism-cooldown", "%time%", plugin.duration(wait));
            return;
        }
        if (plugin.invasions().defending(kingdom) || plugin.invasions().attacking(kingdom)) {
            plugin.send(player, "invasion-running");
            return;
        }
        kingdom.pacifist = !kingdom.pacifist;
        kingdom.pacifistChanged = System.currentTimeMillis();
        if (kingdom.pacifist) {
            kingdom.relations.values().removeIf(relation -> relation == Relation.ENEMY);
            for (Kingdom other : kingdoms.values()) {
                if (other.relations.get(kingdom.id) == Relation.ENEMY) {
                    other.relations.remove(kingdom.id);
                    other.dirty = true;
                }
            }
        }
        kingdom.dirty = true;
        tell(kingdom, kingdom.pacifist ? "pacifist-on" : "pacifist-off", "%player%", player.getName());
    }

    // ---- beállítások ----

    public void toggleOpen(Player player) {
        Kingdom kingdom = allowed(player, KPerm.SETTINGS);
        if (kingdom != null) {
            kingdom.open = !kingdom.open;
            kingdom.dirty = true;
            plugin.send(player, kingdom.open ? "open-on" : "open-off");
        }
    }

    public void cycleKingdomColor(Player player) {
        Kingdom kingdom = allowed(player, KPerm.SETTINGS);
        if (kingdom != null) {
            kingdom.color = next(COLORS, kingdom.color);
            kingdom.dirty = true;
            for (Player member : online(kingdom)) {
                plugin.refreshTags(member);
            }
        }
    }

    public void describe(Player player, String text) {
        Kingdom kingdom = allowed(player, KPerm.SETTINGS);
        if (kingdom != null) {
            kingdom.description = text.length() > 60 ? text.substring(0, 60) : text;
            kingdom.dirty = true;
            plugin.send(player, "description-set");
        }
    }

    public void rename(Player player, String name) {
        Kingdom kingdom = mine(player);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (!validName(name)) {
            plugin.send(player, "bad-name", "%min%", String.valueOf(cfg().nameMin), "%max%", String.valueOf(cfg().nameMax));
            return;
        }
        Kingdom other = kingdom(name);
        if (other != null && other != kingdom) {
            plugin.send(player, "name-taken");
            return;
        }
        long cost = plugin.getConfig().getLong("rename-cost", 5000);
        if (kingdom.bank < cost) {
            plugin.send(player, "not-enough-bank", "%need%", plugin.money(cost));
            return;
        }
        kingdom.bank -= cost;
        byName.remove(kingdom.name.toLowerCase(Locale.ROOT));
        String old = kingdom.name;
        kingdom.name = name;
        byName.put(name.toLowerCase(Locale.ROOT), kingdom);
        kingdom.log("Új név: " + name + " (" + old + ").");
        kingdom.dirty = true;
        plugin.broadcast("renamed", "%old%", old, "%kingdom%", kingdom.display());
    }

    // ---- kapcsolatok ----

    public void requestRelation(Player player, String targetName, Relation relation) {
        Kingdom kingdom = allowed(player, KPerm.RELATIONS);
        Kingdom target = kingdom(targetName);
        if (kingdom == null) {
            return;
        }
        if (target == null || target == kingdom) {
            plugin.send(player, "no-such-kingdom");
            return;
        }
        if (kingdom.nation != null && kingdom.nation.equals(target.nation)) {
            plugin.send(player, "same-nation");
            return;
        }
        switch (relation) {
            case ENEMY -> declareEnemy(player, kingdom, target);
            case NEUTRAL -> makeNeutral(player, kingdom, target);
            case ALLY, TRUCE -> askMutual(player, kingdom, target, relation);
            default -> {
            }
        }
    }

    private void declareEnemy(Player player, Kingdom kingdom, Kingdom target) {
        if (cfg().pacifismEnabled && (kingdom.pacifist || target.pacifist)) {
            plugin.send(player, kingdom.pacifist ? "you-are-pacifist" : "target-pacifist", "%kingdom%", target.display());
            return;
        }
        if (kingdom.stance(target.id) == Relation.ENEMY) {
            plugin.send(player, "already-relation", "%relation%", Relation.ENEMY.display());
            return;
        }
        kingdom.relations.put(target.id, Relation.ENEMY);
        kingdom.relationRequests.remove(target.id);
        if (target.stance(kingdom.id).mutual()) {
            target.relations.remove(kingdom.id);
            target.dirty = true;
        }
        kingdom.dirty = true;
        kingdom.log(player.getName() + " hadat üzent: " + target.name + ".");
        target.log(kingdom.name + " hadat üzent nektek.");
        tell(kingdom, "enemy-declared", "%kingdom%", target.display());
        tell(target, "enemy-declared-you", "%kingdom%", kingdom.display());
    }

    private void makeNeutral(Player player, Kingdom kingdom, Kingdom target) {
        Relation before = relation(kingdom, target);
        kingdom.relations.remove(target.id);
        kingdom.relationRequests.remove(target.id);
        if (before.mutual()) {
            target.relations.remove(kingdom.id);
            target.dirty = true;
        }
        kingdom.dirty = true;
        if (target.stance(kingdom.id) == Relation.ENEMY) {
            plugin.send(player, "still-enemy", "%kingdom%", target.display());
            for (Player member : online(target)) {
                if (target.can(member.getUniqueId(), KPerm.RELATIONS)) {
                    plugin.click(member, "peace-offer", "%kingdom%", kingdom.display(), "%name%", kingdom.name);
                }
            }
            return;
        }
        tell(kingdom, "now-neutral", "%kingdom%", target.display());
        tell(target, "now-neutral", "%kingdom%", kingdom.display());
    }

    private void askMutual(Player player, Kingdom kingdom, Kingdom target, Relation relation) {
        if (relation(kingdom, target) == relation) {
            plugin.send(player, "already-relation", "%relation%", relation.display());
            return;
        }
        if (relation == Relation.ALLY || relation == Relation.TRUCE) {
            int limit = plugin.getConfig().getInt("relations.limits." + relation.name().toLowerCase(Locale.ROOT), 10);
            int count = 0;
            for (Kingdom other : kingdoms.values()) {
                if (relation(kingdom, other) == relation) {
                    count++;
                }
            }
            if (count >= limit) {
                plugin.send(player, "relation-limit", "%relation%", relation.display(), "%max%", String.valueOf(limit));
                return;
            }
        }
        if (kingdom.relationRequests.get(target.id) == relation) {
            kingdom.relations.put(target.id, relation);
            target.relations.put(kingdom.id, relation);
            kingdom.relationRequests.remove(target.id);
            kingdom.dirty = true;
            target.dirty = true;
            kingdom.log("Új kapcsolat: " + target.name + " (" + plugin.strip(relation.title) + ").");
            target.log("Új kapcsolat: " + kingdom.name + " (" + plugin.strip(relation.title) + ").");
            tell(kingdom, "relation-made", "%kingdom%", target.display(), "%relation%", relation.display());
            tell(target, "relation-made", "%kingdom%", kingdom.display(), "%relation%", relation.display());
            return;
        }
        target.relationRequests.put(kingdom.id, relation);
        plugin.send(player, "relation-requested", "%kingdom%", target.display(), "%relation%", relation.display());
        for (Player member : online(target)) {
            if (target.can(member.getUniqueId(), KPerm.RELATIONS)) {
                plugin.click(member, relation == Relation.ALLY ? "ally-request" : "truce-request", "%kingdom%", kingdom.display(), "%name%", kingdom.name);
            }
        }
    }

    public List<Kingdom> withRelation(Kingdom kingdom, Relation relation) {
        List<Kingdom> list = new ArrayList<>();
        for (Kingdom other : kingdoms.values()) {
            if (other != kingdom && relation(kingdom, other) == relation) {
                list.add(other);
            }
        }
        return list;
    }

    // ---- nemzetek ----

    public void createNation(Player player, String name) {
        Kingdom kingdom = allowed(player, KPerm.NATION);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (kingdom.nation != null) {
            plugin.send(player, "already-in-nation");
            return;
        }
        if (level(kingdom) < cfg().nationMinLevel) {
            plugin.send(player, "need-level", "%level%", String.valueOf(cfg().nationMinLevel));
            return;
        }
        if (!validName(name) || nation(name) != null) {
            plugin.send(player, nation(name) != null ? "name-taken" : "bad-name", "%min%", String.valueOf(cfg().nameMin), "%max%", String.valueOf(cfg().nameMax));
            return;
        }
        if (kingdom.bank < cfg().nationCost) {
            plugin.send(player, "not-enough-bank", "%need%", plugin.money(cfg().nationCost));
            return;
        }
        kingdom.bank -= cfg().nationCost;
        Nation nation = new Nation(UUID.randomUUID(), name, kingdom.id, System.currentTimeMillis());
        nations.put(nation.id, nation);
        kingdom.nation = nation.id;
        kingdom.dirty = true;
        nation.dirty = true;
        store.save(nation);
        kingdom.log("Megalapította a(z) " + name + " nemzetet.");
        plugin.broadcast("nation-created", "%nation%", name, "%kingdom%", kingdom.display());
    }

    private Nation nationLeader(Player player) {
        Kingdom kingdom = allowed(player, KPerm.NATION);
        Nation nation = nationOf(kingdom);
        if (kingdom == null) {
            return null;
        }
        if (nation == null) {
            plugin.send(player, "no-nation");
            return null;
        }
        if (!nation.capital.equals(kingdom.id) || !player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-nation-leader");
            return null;
        }
        return nation;
    }

    public void inviteToNation(Player player, String kingdomName) {
        Nation nation = nationLeader(player);
        Kingdom target = kingdom(kingdomName);
        if (nation == null) {
            return;
        }
        if (target == null) {
            plugin.send(player, "no-such-kingdom");
            return;
        }
        if (target.nation != null) {
            plugin.send(player, "kingdom-in-nation", "%kingdom%", target.display());
            return;
        }
        if (nation.kingdoms.size() >= cfg().nationMaxKingdoms) {
            plugin.send(player, "nation-full", "%max%", String.valueOf(cfg().nationMaxKingdoms));
            return;
        }
        nationInvites.put(target.id, nation.id);
        plugin.send(player, "nation-invited", "%kingdom%", target.display());
        Player king = Bukkit.getPlayer(target.leader);
        if (king != null) {
            plugin.click(king, "nation-invite-received", "%nation%", nation.name, "%name%", nation.name);
        }
    }

    public void joinNation(Player player, String nationName) {
        Kingdom kingdom = allowed(player, KPerm.NATION);
        Nation nation = nation(nationName);
        if (kingdom == null) {
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (nation == null || !nation.id.equals(nationInvites.get(kingdom.id))) {
            plugin.send(player, "no-nation-invite");
            return;
        }
        if (kingdom.nation != null) {
            plugin.send(player, "already-in-nation");
            return;
        }
        nationInvites.remove(kingdom.id);
        nation.kingdoms.add(kingdom.id);
        kingdom.nation = nation.id;
        kingdom.relations.keySet().removeIf(nation.kingdoms::contains);
        for (UUID member : nation.kingdoms) {
            Kingdom other = kingdoms.get(member);
            if (other != null && other.relations.remove(kingdom.id) != null) {
                other.dirty = true;
            }
        }
        kingdom.dirty = true;
        nation.dirty = true;
        tellNation(nation, "nation-joined", "%kingdom%", kingdom.display(), "%nation%", nation.name);
    }

    public void leaveNation(Player player) {
        Kingdom kingdom = allowed(player, KPerm.NATION);
        Nation nation = nationOf(kingdom);
        if (kingdom == null) {
            return;
        }
        if (nation == null) {
            plugin.send(player, "no-nation");
            return;
        }
        if (!player.getUniqueId().equals(kingdom.leader)) {
            plugin.send(player, "not-king");
            return;
        }
        if (nation.capital.equals(kingdom.id)) {
            plugin.send(player, "capital-cant-leave");
            return;
        }
        nation.kingdoms.remove(kingdom.id);
        kingdom.nation = null;
        kingdom.dirty = true;
        nation.dirty = true;
        tellNation(nation, "nation-left", "%kingdom%", kingdom.display(), "%nation%", nation.name);
        tell(kingdom, "nation-left", "%kingdom%", kingdom.display(), "%nation%", nation.name);
    }

    public void kickFromNation(Player player, String kingdomName) {
        Nation nation = nationLeader(player);
        Kingdom target = kingdom(kingdomName);
        if (nation == null) {
            return;
        }
        if (target == null || !nation.kingdoms.contains(target.id) || target.id.equals(nation.capital)) {
            plugin.send(player, "not-in-your-nation");
            return;
        }
        nation.kingdoms.remove(target.id);
        target.nation = null;
        target.dirty = true;
        nation.dirty = true;
        tellNation(nation, "nation-kicked", "%kingdom%", target.display(), "%nation%", nation.name);
        tell(target, "nation-kicked", "%kingdom%", target.display(), "%nation%", nation.name);
    }

    public void disbandNation(Player player) {
        Nation nation = nationLeader(player);
        if (nation == null) {
            return;
        }
        if (!confirmed(player, "nation-disband")) {
            plugin.send(player, "nation-disband-confirm");
            return;
        }
        String name = nation.name;
        disbandNation(nation);
        plugin.broadcast("nation-disbanded", "%nation%", name);
    }

    void disbandNation(Nation nation) {
        for (UUID id : nation.kingdoms) {
            Kingdom kingdom = kingdoms.get(id);
            if (kingdom != null) {
                kingdom.nation = null;
                kingdom.dirty = true;
            }
        }
        Kingdom capital = kingdoms.get(nation.capital);
        if (capital != null) {
            capital.bank += nation.bank;
            capital.resourcePoints += nation.resourcePoints;
        }
        nations.remove(nation.id);
        store.deleteNation(nation.id);
    }

    /** Nexuspont beadása a nemzet közös kasszájába. */
    public void donateToNation(Player player, long points) {
        Kingdom kingdom = allowed(player, KPerm.NATION);
        Nation nation = nationOf(kingdom);
        if (kingdom == null || points <= 0) {
            return;
        }
        if (nation == null) {
            plugin.send(player, "no-nation");
            return;
        }
        if (kingdom.resourcePoints < points) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(points));
            return;
        }
        kingdom.resourcePoints -= points;
        nation.resourcePoints += points;
        kingdom.dirty = true;
        nation.dirty = true;
        tellNation(nation, "nation-donated", "%kingdom%", kingdom.display(), "%points%", String.valueOf(points));
    }

    /** A nemzet pajzsa: a főváros pajzsa minden tagra érvényes (a nemzeti kasszából fizetve). */
    public void buyNationShield(Player player, int hours) {
        Nation nation = nationLeader(player);
        Long price = cfg().shields.get(hours);
        if (nation == null || price == null) {
            return;
        }
        long total = price * Math.max(1, nation.kingdoms.size());
        if (nation.resourcePoints < total) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(total));
            return;
        }
        Kingdom capital = kingdoms.get(nation.capital);
        nation.resourcePoints -= total;
        capital.shieldUntil = Math.max(System.currentTimeMillis(), capital.shieldUntil) + hours * 3_600_000L;
        capital.dirty = true;
        nation.dirty = true;
        tellNation(nation, "nation-shield", "%hours%", String.valueOf(hours));
    }

    // ---- power ----

    /** Percenként: aki fent van a Kingdoms világban, regen-minutes percenként power-t kap. */
    void tickPower() {
        if (!cfg().powerEnabled || plugin.world() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long every = cfg().powerRegenMinutes * 60_000L;
        for (Player player : plugin.world().getPlayers()) {
            KPlayer data = player(player);
            if (data.powerTick == 0) {
                data.powerTick = now;
                continue;
            }
            if (now - data.powerTick >= every) {
                data.powerTick = now;
                if (data.power < cfg().powerMax) {
                    data.power = Math.min(cfg().powerMax, data.power + cfg().powerRegenAmount);
                    data.dirty = true;
                }
            }
        }
    }

    /** Halál PvP-ben: power vesztés, a királyság NP-t veszít, a gyilkos királysága XP-t kap. */
    void death(Player victim, Player killer) {
        KPlayer data = player(victim);
        data.deaths++;
        if (cfg().powerEnabled) {
            data.power = Math.max(cfg().powerMin, data.power - cfg().powerDeathLoss);
        }
        data.dirty = true;
        Kingdom kingdom = kingdomOf(victim.getUniqueId());
        if (kingdom != null) {
            kingdom.deaths++;
            kingdom.resourcePoints = Math.max(0, kingdom.resourcePoints - cfg().deathPenalty);
            kingdom.dirty = true;
        }
        if (killer != null && !killer.equals(victim)) {
            KPlayer killerData = player(killer);
            killerData.kills++;
            killerData.dirty = true;
            Kingdom killerKingdom = kingdomOf(killer.getUniqueId());
            if (killerKingdom != null && killerKingdom != kingdom) {
                killerKingdom.kills++;
                addXp(killerKingdom, cfg().xpPerKill);
            }
        }
    }

    // ---- üzenetek ----

    /** Üzenet a királyság minden fent lévő tagjának. */
    public void tell(Kingdom kingdom, String key, String... pairs) {
        for (Player member : online(kingdom)) {
            plugin.send(member, key, pairs);
        }
    }

    public void tellNation(Nation nation, String key, String... pairs) {
        for (UUID id : nation.kingdoms) {
            Kingdom kingdom = kingdoms.get(id);
            if (kingdom != null) {
                tell(kingdom, key, pairs);
            }
        }
    }

    public void announce(Player player, String text) {
        Kingdom kingdom = allowed(player, KPerm.BROADCAST);
        if (kingdom != null && !text.isBlank()) {
            tell(kingdom, "announcement", "%player%", player.getName(), "%message%", text.replace("§", ""));
        }
    }

    void forget(UUID player) {
        confirm.remove(player);
        confirmWhat.remove(player);
    }
}
