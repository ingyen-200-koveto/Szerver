package hu.icemine.core.skywars;

import hu.icemine.core.bedwars.BwBoard;
import hu.icemine.core.bedwars.TeamColor;
import hu.icemine.core.build.Placer;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Egy SkyWars aréna: várakozás a ketrecekben, visszaszámlálás, harc, ládák újratöltése, győzelem, takarítás. */
public final class SwArena {

    public enum State { BUILDING, WAITING, STARTING, PLAYING, ENDING }

    static final class Adat {
        final UUID id;
        final String name;
        final int island;
        boolean alive = true;
        int kills;

        Adat(UUID id, String name, int island) {
            this.id = id;
            this.name = name;
            this.island = island;
        }
    }

    static final int[] REFILL = {180, 360};
    static final int GLOW = 540;
    static final int END = 720;

    final Skywars plugin;
    public final String id;
    public final SwMode mode;
    public final SwMap.Theme map;
    final World world;
    public final int cx;
    public final int cy;
    public final int cz;
    private final Map<UUID, Adat> players = new LinkedHashMap<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, BwBoard> boards = new HashMap<>();
    private final Map<UUID, Object[]> lastHit = new HashMap<>();
    private final Random random = new Random();
    private State state = State.BUILDING;
    private int countdown = -1;
    private int elapsed;
    private int ending;

    SwArena(Skywars plugin, String id, SwMode mode, SwMap.Theme map, World world, int cx, int cy, int cz) {
        this.plugin = plugin;
        this.id = id;
        this.mode = mode;
        this.map = map;
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
    }

    public State state() {
        return state;
    }

    public int size() {
        return players.size();
    }

    public int capacity() {
        return mode.capacity();
    }

    public boolean joinable() {
        return (state == State.WAITING || state == State.STARTING) && players.size() < capacity();
    }

    public boolean playing() {
        return state == State.PLAYING || state == State.ENDING;
    }

    Adat data(UUID id) {
        return players.get(id);
    }

    boolean spectator(UUID id) {
        return spectators.contains(id);
    }

    public int alive() {
        int n = 0;
        for (Adat a : players.values()) {
            if (a.alive) {
                n++;
            }
        }
        return n;
    }

    boolean contains(Location loc) {
        return loc.getWorld() == world && Math.abs(loc.getBlockX() - cx) <= SwMap.REACH && Math.abs(loc.getBlockZ() - cz) <= SwMap.REACH;
    }

    boolean buildable(Block b) {
        return Math.abs(b.getX() - cx) <= SwMap.REACH && Math.abs(b.getZ() - cz) <= SwMap.REACH
                && b.getY() >= cy - SwMap.BELOW && b.getY() <= cy + SwMap.ABOVE;
    }

    Location spectatorPoint() {
        Location l = new Location(world, cx + 0.5, cy + 18, cz + 0.5);
        l.setPitch(60);
        return l;
    }

    Location cageSpawn(int island) {
        int[] c = SwMap.island(island, mode.islands);
        Location l = new Location(world, cx + c[0] + 0.5, cy + SwMap.CAGE + 1, cz + c[1] + 0.5);
        l.setDirection(new Location(world, cx + 0.5, l.getY(), cz + 0.5).toVector().subtract(l.toVector()));
        return l;
    }

    void send(Player p, String text) {
        plugin.core().messages().sendTagged(p, text);
    }

    void broadcast(String text) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                send(p, text);
            }
        }
    }

    List<UUID> all() {
        List<UUID> out = new ArrayList<>(players.keySet());
        out.addAll(spectators);
        return out;
    }

    // ---- építés, visszaállítás ----

    void build() {
        Placer placer = new Placer(world, plugin.core().getLogger());
        placer.clear(cx - SwMap.REACH, cy - SwMap.BELOW, cz - SwMap.REACH, cx + SwMap.REACH, cy + SwMap.ABOVE, cz + SwMap.REACH);
        new SwMap(placer, cx, cy, cz, map, mode.islands).build();
        for (Entity e : world.getNearbyEntities(new Location(world, cx, cy, cz), SwMap.REACH, SwMap.ABOVE + SwMap.BELOW, SwMap.REACH)) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        state = State.WAITING;
        countdown = -1;
        elapsed = 0;
        ending = 0;
    }

    void fillChests() {
        for (int i = 0; i < mode.islands; i++) {
            int[] c = SwMap.island(i, mode.islands);
            List<List<ItemStack>> loot = SwLoot.island(random);
            List<int[]> pos = SwMap.islandChests();
            for (int k = 0; k < pos.size(); k++) {
                chest(cx + c[0] + pos.get(k)[0], cy + pos.get(k)[1], cz + c[1] + pos.get(k)[2], loot.get(k));
            }
        }
        for (int[] p : SwMap.centerChests()) {
            chest(cx + p[0], cy + p[1], cz + p[2], SwLoot.center(random));
        }
    }

    private void chest(int x, int y, int z, List<ItemStack> items) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != Material.CHEST) {
            b.setType(Material.CHEST);
        }
        if (b.getState() instanceof Chest chest) {
            SwLoot.fill(chest.getBlockInventory(), items, random);
        }
    }

    // ---- be- és kilépés ----

    public boolean join(Player p) {
        if (!joinable()) {
            send(p, state == State.PLAYING || state == State.ENDING ? "&cEz a meccs már megy." : "&cEz a pálya tele van.");
            return false;
        }
        int island = chooseIsland(p.getUniqueId());
        if (island < 0) {
            send(p, "&cEz a pálya tele van.");
            return false;
        }
        boolean first = true;
        for (Adat a : players.values()) {
            if (a.island == island) {
                first = false;
                break;
            }
        }
        if (first) {
            try {
                new SwMap(new Placer(world, plugin.core().getLogger()), cx, cy, cz, map, mode.islands).cage(island, plugin.cages().selected(p));
            } catch (Exception exception) {
                plugin.core().getLogger().warning("SkyWars ketrec: " + exception.getMessage());
            }
        }
        players.put(p.getUniqueId(), new Adat(p.getUniqueId(), p.getName(), island));
        plugin.track(p, this);
        reset(p, GameMode.ADVENTURE);
        teleport(p, cageSpawn(island));
        p.getInventory().setItem(8, Items.of(Material.RED_BED).name("&c&lKilépés &7(jobb klikk)").tag("sw:leave").build());
        boards.put(p.getUniqueId(), new BwBoard(p, "&b&lSKYWARS"));
        broadcast("&b" + p.getName() + " &7csatlakozott. &8(" + players.size() + "/" + capacity() + ")");
        if (mode.teamSize > 1) {
            for (Adat a : players.values()) {
                if (a.island == island && !a.id.equals(p.getUniqueId())) {
                    send(p, "&7A csapattársad: &f" + a.name);
                }
            }
        }
        updateBoards();
        return true;
    }

    /** Partytárs szigete, ha van még hely; különben üres sziget; különben ahol van hely. */
    int chooseIsland(UUID id) {
        int[] count = new int[mode.islands];
        for (Adat a : players.values()) {
            count[a.island]++;
        }
        hu.icemine.core.social.Partyk partyk = plugin.core().partyk();
        Object party = partyk == null ? null : partyk.of(id);
        if (party != null && mode.teamSize > 1) {
            for (Adat a : players.values()) {
                if (partyk.of(a.id) == party && count[a.island] < mode.teamSize) {
                    return a.island;
                }
            }
        }
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < mode.islands; i++) {
            if (count[i] == 0) {
                empty.add(i);
            }
        }
        if (!empty.isEmpty()) {
            return empty.get(random.nextInt(empty.size()));
        }
        for (int i = 0; i < mode.islands; i++) {
            if (count[i] < mode.teamSize) {
                return i;
            }
        }
        return -1;
    }

    private void teleport(Player p, Location l) {
        plugin.core().guard().allowNext(p);
        p.teleport(l);
    }

    void reset(Player p, GameMode gm) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setGameMode(gm);
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setExp(0);
        p.setLevel(0);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        try {
            p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        } catch (RuntimeException ignored) {
            p.setHealth(20);
        }
    }

    /** Kilépés: home = visszaküldjük-e a lobbyba. Meccs közben ez vereségnek számít. */
    public void leave(Player p, boolean home) {
        UUID id = p.getUniqueId();
        Adat a = players.get(id);
        boolean spec = spectators.remove(id);
        if (a == null && !spec) {
            return;
        }
        plugin.untrack(p);
        BwBoard board = boards.remove(id);
        if (board != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        if (a != null) {
            if (state == State.PLAYING && a.alive) {
                a.alive = false;
                Player killer = recentKiller(id);
                plugin.stat(id, "skywars.deaths", 1);
                plugin.resetStreak(id);
                if (killer != null) {
                    credit(killer);
                    broadcast("&c" + a.name + " &7kilépett, &f" + killer.getName() + " &7kapja a pontot.");
                } else {
                    broadcast("&c" + a.name + " &7kilépett a meccsből.");
                }
                checkWin();
            } else if (state == State.WAITING || state == State.STARTING) {
                players.remove(id);
                broadcast("&7" + a.name + " kilépett. &8(" + players.size() + "/" + capacity() + ")");
                if (players.size() < mode.minPlayers && state == State.STARTING) {
                    state = State.WAITING;
                    countdown = -1;
                    broadcast("&eKevesen maradtatok, a visszaszámlálás leállt.");
                }
            }
            if (state != State.PLAYING && state != State.ENDING) {
                players.remove(id);
            }
        }
        if (home) {
            reset(p, GameMode.ADVENTURE);
            plugin.sendHome(p);
        }
        updateBoards();
    }

    public boolean spectate(Player p) {
        if (state != State.PLAYING) {
            return false;
        }
        spectators.add(p.getUniqueId());
        plugin.track(p, this);
        reset(p, GameMode.SPECTATOR);
        teleport(p, spectatorPoint());
        boards.put(p.getUniqueId(), new BwBoard(p, "&b&lSKYWARS"));
        updateBoards();
        return true;
    }

    // ---- harc ----

    void hit(UUID victim, UUID attacker) {
        lastHit.put(victim, new Object[]{attacker, System.currentTimeMillis()});
    }

    boolean teammates(UUID a, UUID b) {
        Adat x = players.get(a);
        Adat y = players.get(b);
        return x != null && y != null && x.island == y.island && !a.equals(b);
    }

    Player recentKiller(UUID victim) {
        Object[] h = lastHit.get(victim);
        if (h == null || System.currentTimeMillis() - (Long) h[1] > 12_000L) {
            return null;
        }
        Player k = Bukkit.getPlayer((UUID) h[0]);
        Adat ka = k == null ? null : players.get(k.getUniqueId());
        return ka != null && ka.alive ? k : null;
    }

    private void credit(Player killer) {
        Adat ka = players.get(killer.getUniqueId());
        if (ka != null) {
            ka.kills++;
        }
        plugin.stat(killer.getUniqueId(), "skywars.kills", 1);
        plugin.reward(killer, plugin.killReward(), "skywars_kill");
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.3F);
    }

    /** A játékos meghalt (megölték vagy leesett). direct: aki közvetlenül megölte, ha van. */
    void die(Player p, Player direct, boolean voidDeath) {
        Adat a = players.get(p.getUniqueId());
        if (a == null || !a.alive || state != State.PLAYING) {
            return;
        }
        a.alive = false;
        Player killer = direct != null && players.containsKey(direct.getUniqueId()) && !direct.equals(p) ? direct : recentKiller(p.getUniqueId());
        plugin.stat(p.getUniqueId(), "skywars.deaths", 1);
        plugin.resetStreak(p.getUniqueId());
        if (killer != null) {
            credit(killer);
            plugin.killEffect(killer, p.getLocation());
            broadcast("&c" + a.name + " &7" + (voidDeath ? "lezuhant, &f" + killer.getName() + " &7lökte le." : "meghalt, &f" + killer.getName() + " &7ölte meg."));
        } else {
            broadcast("&c" + a.name + " &7" + (voidDeath ? "lezuhant." : "meghalt."));
        }
        send(p, "&cKiestél. Nézheted a meccs végét, vagy kiléphetsz: &f/sw leave");
        p.sendTitle(Text.color("&c&lKIESTÉL"), Text.color("&7" + alive() + " játékos maradt"), 5, 40, 10);
        Bukkit.getScheduler().runTask(plugin.core(), () -> {
            if (p.isOnline() && players.containsKey(p.getUniqueId())) {
                reset(p, GameMode.SPECTATOR);
                teleport(p, spectatorPoint());
            }
        });
        checkWin();
        updateBoards();
    }

    private void checkWin() {
        if (state != State.PLAYING) {
            return;
        }
        Set<Integer> islands = new HashSet<>();
        for (Adat a : players.values()) {
            if (a.alive) {
                islands.add(a.island);
            }
        }
        if (islands.size() <= 1) {
            finish(islands.isEmpty() ? -1 : islands.iterator().next());
        }
    }

    private void finish(int island) {
        state = State.ENDING;
        ending = 8;
        List<String> names = new ArrayList<>();
        for (Adat a : players.values()) {
            if (island >= 0 && a.island == island) {
                names.add(a.name);
                plugin.stat(a.id, "skywars.wins", 1);
                plugin.winStreak(a.id);
                Player w = Bukkit.getPlayer(a.id);
                if (w != null) {
                    plugin.reward(w, plugin.winReward(), "skywars_win");
                    w.sendTitle(Text.color("&6&lGYŐZTÉL!"), Text.color("&7" + a.kills + " ölés"), 5, 60, 15);
                    w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
                    firework(w.getLocation());
                }
            }
        }
        if (names.isEmpty()) {
            broadcast("&eLejárt az idő, ez a meccs döntetlen.");
        } else {
            broadcast("&6&l" + String.join(" és ", names) + " &e" + (names.size() > 1 ? "nyerték" : "nyerte") + " a meccset!");
            Adat best = null;
            for (Adat a : players.values()) {
                if (best == null || a.kills > best.kills) {
                    best = a;
                }
            }
            if (best != null && best.kills > 0) {
                broadcast("&7Legtöbb ölés: &f" + best.name + " &8(" + best.kills + ")");
            }
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && (island < 0 || players.get(id) == null || players.get(id).island != island)) {
                p.sendTitle(Text.color(names.isEmpty() ? "&e&lDÖNTETLEN" : "&c&lVÉGE"), Text.color(names.isEmpty() ? "" : "&7" + String.join(", ", names) + " nyert"), 5, 50, 10);
            }
        }
    }

    private void firework(Location l) {
        try {
            org.bukkit.entity.Firework fw = l.getWorld().spawn(l, org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.AQUA, org.bukkit.Color.WHITE).with(org.bukkit.FireworkEffect.Type.BALL_LARGE).build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (RuntimeException ignored) {
        }
    }

    // ---- időzítés (másodpercenként) ----

    void tick() {
        switch (state) {
            case WAITING -> {
                if (players.size() >= mode.minPlayers) {
                    state = State.STARTING;
                    countdown = plugin.countdown();
                    broadcast("&aMegvan a létszám! A meccs &f" + countdown + " &amásodperc múlva indul.");
                }
            }
            case STARTING -> {
                if (players.size() < mode.minPlayers) {
                    state = State.WAITING;
                    countdown = -1;
                    break;
                }
                if (players.size() >= capacity() && countdown > plugin.fullCountdown()) {
                    countdown = plugin.fullCountdown();
                }
                if (countdown <= 0) {
                    start();
                } else {
                    if (countdown == 20 || countdown == 10 || countdown <= 5) {
                        broadcast("&eA meccs &f" + countdown + " &emásodperc múlva indul!");
                        for (UUID id : players.keySet()) {
                            Player p = Bukkit.getPlayer(id);
                            if (p != null) {
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1F, 1F);
                                if (countdown <= 5) {
                                    p.sendTitle(Text.color("&e&l" + countdown), "", 0, 25, 5);
                                }
                            }
                        }
                    }
                    countdown--;
                }
            }
            case PLAYING -> {
                elapsed++;
                for (int r : REFILL) {
                    if (elapsed == r) {
                        fillChests();
                        broadcast("&a&lA ládák újratöltődtek!");
                        sound(Sound.BLOCK_CHEST_OPEN);
                    }
                }
                if (elapsed == GLOW) {
                    broadcast("&c&lVégső harc! &7Mostantól mindenki világít, nincs hova bújni.");
                    for (Adat a : players.values()) {
                        Player p = Bukkit.getPlayer(a.id);
                        if (p != null && a.alive) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 600, 0, false, false));
                        }
                    }
                    sound(Sound.ENTITY_ENDER_DRAGON_GROWL);
                }
                if (elapsed == END - 60) {
                    broadcast("&eMég &f1 perc&e, utána döntetlen!");
                }
                if (elapsed >= END) {
                    finish(-1);
                }
            }
            case ENDING -> {
                if (--ending <= 0) {
                    close();
                }
            }
            default -> {
            }
        }
        updateBoards();
    }

    private void sound(Sound s) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p.getLocation(), s, 0.8F, 1F);
            }
        }
    }

    private void start() {
        state = State.PLAYING;
        elapsed = 0;
        fillChests();
        new SwMap(new Placer(world, plugin.core().getLogger()), cx, cy, cz, map, mode.islands).cages(false);
        for (Adat a : players.values()) {
            Player p = Bukkit.getPlayer(a.id);
            if (p == null) {
                continue;
            }
            reset(p, GameMode.SURVIVAL);
            p.sendTitle(Text.color("&b&lHARC!"), Text.color("&7Az utolsó túlélő nyer"), 0, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
            plugin.stat(a.id, "skywars.games", 1);
            BwBoard board = boards.get(a.id);
            if (board != null && mode.teamSize > 1) {
                for (Adat o : players.values()) {
                    board.color(o.name, TeamColor.values()[o.island % TeamColor.values().length]);
                }
            }
        }
        broadcast("&b&lIndul a SkyWars! &7Szedd össze a ládákat, és ne ess le. 3 percenként újratöltődnek.");
    }

    /** Vége: mindenki vissza a lobbyba, utána a pálya újraépül. */
    private void close() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            plugin.untrackId(id);
            BwBoard b = boards.remove(id);
            if (p != null) {
                if (b != null) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                reset(p, GameMode.ADVENTURE);
                plugin.sendHome(p);
            }
        }
        players.clear();
        spectators.clear();
        boards.clear();
        lastHit.clear();
        state = State.BUILDING;
        plugin.queueBuild(this);
    }

    /** Leállításkor: mindenkit hazaküld, statisztika nélkül. */
    void kickAll() {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                reset(p, GameMode.ADVENTURE);
                plugin.sendHome(p);
            }
            plugin.untrackId(id);
        }
        players.clear();
        spectators.clear();
        boards.clear();
    }

    public void forceStart() {
        if (state == State.WAITING || state == State.STARTING) {
            if (players.size() < 2) {
                return;
            }
            state = State.STARTING;
            countdown = Math.min(countdown < 0 ? 5 : countdown, 5);
        }
    }

    public void forceStop() {
        if (state == State.PLAYING) {
            finish(-1);
        }
    }

    static String time(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    String nextEvent() {
        for (int r : REFILL) {
            if (elapsed < r) {
                return "&fÚjratöltés: &a" + time(r - elapsed);
            }
        }
        if (elapsed < GLOW) {
            return "&fVégső harc: &c" + time(GLOW - elapsed);
        }
        return "&fDöntetlen: &e" + time(END - elapsed);
    }

    void updateBoards() {
        for (Map.Entry<UUID, BwBoard> e : boards.entrySet()) {
            Adat a = players.get(e.getKey());
            List<String> lines = new ArrayList<>();
            lines.add("&8" + id);
            lines.add(" ");
            lines.add("&fMód: &b" + Text.strip(Text.color(mode.name)));
            lines.add("&fPálya: &a" + map.display);
            lines.add("  ");
            if (state == State.WAITING || state == State.STARTING || state == State.BUILDING) {
                lines.add("&fJátékosok: &a" + players.size() + "/" + capacity());
                lines.add(state == State.STARTING ? "&fIndul: &e" + Math.max(0, countdown) + " mp" : "&7Várakozás... (min. " + mode.minPlayers + ")");
            } else {
                lines.add(nextEvent());
                lines.add("&fÉletben: &a" + alive());
                if (a != null) {
                    lines.add("&fÖléseid: &c" + a.kills);
                }
            }
            lines.add("   ");
            lines.add("&bplay.icemine.hu");
            e.getValue().lines(lines);
        }
    }
}
