package hu.icemine.core.social;

import hu.icemine.core.IceMineCore;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** /barat: barátlista, kérések, értesítés, ha egy barát fellép vagy kilép. */
public final class Baratok implements CommandExecutor, TabCompleter, Listener {

    static final int MAX = 50;
    static final long KERES_IDO = 5 * 60_000L;

    private final IceMineCore core;
    private final Map<UUID, Set<UUID>> baratok = new ConcurrentHashMap<>();
    /** cél -> (küldő -> idő) */
    private final Map<UUID, Map<UUID, Long>> keresek = new ConcurrentHashMap<>();

    public Baratok(IceMineCore core) {
        this.core = core;
    }

    public void start() throws Exception {
        core.database().query(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS im_friends (a VARCHAR(36) NOT NULL, b VARCHAR(36) NOT NULL, since BIGINT NOT NULL, PRIMARY KEY (a, b))");
            }
            return null;
        }).get(15, TimeUnit.SECONDS);
        Bukkit.getPluginManager().registerEvents(this, core);
        for (Player p : Bukkit.getOnlinePlayers()) {
            betolt(p.getUniqueId(), null);
        }
    }

    public Set<UUID> of(UUID id) {
        return baratok.getOrDefault(id, Set.of());
    }

    public boolean baratE(UUID a, UUID b) {
        return of(a).contains(b);
    }

    private void betolt(UUID id, Runnable utana) {
        core.database().query(connection -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            try (PreparedStatement st = connection.prepareStatement("SELECT b FROM im_friends WHERE a = ?")) {
                st.setString(1, id.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        try {
                            set.add(UUID.fromString(rs.getString(1)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            return set;
        }).thenAccept(set -> Bukkit.getScheduler().runTask(core, () -> {
            baratok.put(id, set);
            if (utana != null) {
                utana.run();
            }
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        betolt(p.getUniqueId(), () -> {
            if (!p.isOnline()) {
                return;
            }
            int fent = 0;
            for (UUID f : of(p.getUniqueId())) {
                Player bp = Bukkit.getPlayer(f);
                if (bp != null) {
                    fent++;
                    Szoveg.kuld(core, bp, "&a+ &fA barátod, &b" + p.getName() + "&f fellépett.");
                }
            }
            if (fent > 0) {
                Szoveg.kuld(core, p, "&f" + fent + " barátod van fent. [[&b[Lista]|run:/barat lista]]");
            }
            Map<UUID, Long> var = keresek.get(p.getUniqueId());
            if (var != null && !var.isEmpty()) {
                Szoveg.kuld(core, p, "&eBarátkérésed van. [[&b[Megnézem]|run:/barat kerelmek]]");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        for (UUID f : of(p.getUniqueId())) {
            Player bp = Bukkit.getPlayer(f);
            if (bp != null) {
                Szoveg.kuld(core, bp, "&c- &7A barátod, &f" + p.getName() + "&7 kilépett.");
            }
        }
        baratok.remove(p.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Ezt csak játékos használhatja.");
            return true;
        }
        if (core.auth() != null && !core.auth().isLoggedIn(p)) {
            Szoveg.kuld(core, p, "&cElőbb jelentkezz be.");
            return true;
        }
        if (args.length == 0) {
            lista(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String nev = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "lista" -> lista(p);
            case "kerelmek", "kérelmek" -> kerelmek(p);
            case "hozzaad", "hozzáad" -> {
                if (nev == null) {
                    Szoveg.kuld(core, p, "&7Használat: &f/barat hozzaad <játékos>");
                } else {
                    hozzaad(p, nev);
                }
            }
            case "elfogad" -> {
                if (nev == null) {
                    kerelmek(p);
                } else {
                    elfogad(p, nev);
                }
            }
            case "elutasit", "elutasít" -> {
                if (nev != null) {
                    elutasit(p, nev);
                }
            }
            case "torol", "töröl" -> {
                if (nev == null) {
                    Szoveg.kuld(core, p, "&7Használat: &f/barat torol <játékos>");
                } else {
                    torol(p, nev);
                }
            }
            case "segitseg", "help" -> sugo(p);
            default -> hozzaad(p, args[0]);
        }
        return true;
    }

    private void sugo(Player p) {
        Szoveg.kuld(core, p, "&b&lBarátok");
        p.sendMessage(hu.icemine.core.util.Text.color("&f/barat <játékos> &7- barátkérés küldése"));
        p.sendMessage(hu.icemine.core.util.Text.color("&f/barat lista &7- kik a barátaid, és ki van fent"));
        p.sendMessage(hu.icemine.core.util.Text.color("&f/barat kerelmek &7- a neked küldött kérések"));
        p.sendMessage(hu.icemine.core.util.Text.color("&f/barat torol <játékos> &7- barát törlése"));
    }

    private void hozzaad(Player p, String nev) {
        if (!Szoveg.nevJo(nev)) {
            Szoveg.kuld(core, p, "&cEz nem érvényes játékosnév.");
            return;
        }
        Player cel = Bukkit.getPlayerExact(nev);
        if (cel == null) {
            Szoveg.kuld(core, p, "&c" + nev + " most nincs fent. Barátkérést csak fent lévő játékosnak küldhetsz.");
            return;
        }
        if (cel.equals(p)) {
            Szoveg.kuld(core, p, "&cMagadnak nem küldhetsz barátkérést.");
            return;
        }
        if (baratE(p.getUniqueId(), cel.getUniqueId())) {
            Szoveg.kuld(core, p, "&e" + cel.getName() + " már a barátod.");
            return;
        }
        Map<UUID, Long> nekem = keresek.get(p.getUniqueId());
        if (nekem != null && friss(nekem.get(cel.getUniqueId()))) {
            elfogad(p, cel.getName());
            return;
        }
        if (of(p.getUniqueId()).size() >= MAX) {
            Szoveg.kuld(core, p, "&cMár " + MAX + " barátod van, ennél több nem lehet. Törölj valakit: &f/barat torol <név>");
            return;
        }
        Map<UUID, Long> neki = keresek.computeIfAbsent(cel.getUniqueId(), x -> new ConcurrentHashMap<>());
        if (friss(neki.get(p.getUniqueId()))) {
            Szoveg.kuld(core, p, "&eMár küldtél neki kérést, várj, amíg elfogadja.");
            return;
        }
        neki.put(p.getUniqueId(), System.currentTimeMillis());
        Szoveg.kuld(core, p, "&aBarátkérés elküldve neki: &f" + cel.getName() + "&a. 5 percig érvényes.");
        Szoveg.kuld(core, cel, "&b" + p.getName() + "&f barátnak jelölt. [[&a[Elfogadom]|run:/barat elfogad " + p.getName() + "]] [[&c[Nem]|run:/barat elutasit " + p.getName() + "]]");
        cel.playSound(cel.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7F, 1.2F);
    }

    static boolean friss(Long t) {
        return t != null && System.currentTimeMillis() - t < KERES_IDO;
    }

    private UUID kerelmezo(Player p, String nev) {
        Map<UUID, Long> nekem = keresek.get(p.getUniqueId());
        if (nekem == null) {
            return null;
        }
        for (Map.Entry<UUID, Long> e : nekem.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            if (friss(e.getValue()) && op.getName() != null && op.getName().equalsIgnoreCase(nev)) {
                return e.getKey();
            }
        }
        return null;
    }

    private void elfogad(Player p, String nev) {
        UUID tole = kerelmezo(p, nev);
        if (tole == null) {
            Szoveg.kuld(core, p, "&c" + nev + " nem küldött neked barátkérést (vagy már lejárt).");
            return;
        }
        if (of(p.getUniqueId()).size() >= MAX) {
            Szoveg.kuld(core, p, "&cMár " + MAX + " barátod van, ennél több nem lehet.");
            return;
        }
        keresek.get(p.getUniqueId()).remove(tole);
        UUID en = p.getUniqueId();
        long most = System.currentTimeMillis();
        core.database().execute(connection -> {
            String sql = core.database().mysql() ? "INSERT IGNORE INTO im_friends (a, b, since) VALUES (?, ?, ?)" : "INSERT OR IGNORE INTO im_friends (a, b, since) VALUES (?, ?, ?)";
            try (PreparedStatement st = connection.prepareStatement(sql)) {
                for (UUID[] pair : new UUID[][] {{en, tole}, {tole, en}}) {
                    st.setString(1, pair[0].toString());
                    st.setString(2, pair[1].toString());
                    st.setLong(3, most);
                    st.executeUpdate();
                }
            }
        });
        baratok.computeIfAbsent(en, x -> ConcurrentHashMap.newKeySet()).add(tole);
        baratok.computeIfAbsent(tole, x -> ConcurrentHashMap.newKeySet()).add(en);
        String masik = Bukkit.getOfflinePlayer(tole).getName();
        Szoveg.kuld(core, p, "&aMostantól barátok vagytok: &f" + masik + "&a.");
        Player tp = Bukkit.getPlayer(tole);
        if (tp != null) {
            Szoveg.kuld(core, tp, "&b" + p.getName() + "&a elfogadta a barátkérésedet.");
            tp.playSound(tp.getLocation(), "minecraft:entity.player.levelup", 0.6F, 1.4F);
        }
    }

    private void elutasit(Player p, String nev) {
        UUID tole = kerelmezo(p, nev);
        if (tole != null) {
            keresek.get(p.getUniqueId()).remove(tole);
        }
        Szoveg.kuld(core, p, "&7Elutasítva.");
    }

    private void torol(Player p, String nev) {
        UUID cel = null;
        for (UUID f : of(p.getUniqueId())) {
            String n = Bukkit.getOfflinePlayer(f).getName();
            if (n != null && n.equalsIgnoreCase(nev)) {
                cel = f;
            }
        }
        if (cel == null) {
            Szoveg.kuld(core, p, "&c" + nev + " nincs a barátaid között.");
            return;
        }
        UUID en = p.getUniqueId(), masik = cel;
        core.database().execute(connection -> {
            try (PreparedStatement st = connection.prepareStatement("DELETE FROM im_friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)")) {
                st.setString(1, en.toString());
                st.setString(2, masik.toString());
                st.setString(3, masik.toString());
                st.setString(4, en.toString());
                st.executeUpdate();
            }
        });
        of(en).remove(masik);
        Set<UUID> tobbi = baratok.get(masik);
        if (tobbi != null) {
            tobbi.remove(en);
        }
        Szoveg.kuld(core, p, "&7" + nev + " már nem a barátod.");
    }

    private void kerelmek(Player p) {
        Map<UUID, Long> nekem = keresek.getOrDefault(p.getUniqueId(), Map.of());
        List<String> nevek = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : nekem.entrySet()) {
            String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (friss(e.getValue()) && n != null) {
                nevek.add(n);
            }
        }
        if (nevek.isEmpty()) {
            Szoveg.kuld(core, p, "&7Nincs barátkérésed.");
            return;
        }
        for (String n : nevek) {
            Szoveg.kuld(core, p, "&b" + n + "&f barátnak jelölt. [[&a[Elfogadom]|run:/barat elfogad " + n + "]] [[&c[Nem]|run:/barat elutasit " + n + "]]");
        }
    }

    private void lista(Player p) {
        Set<UUID> set = of(p.getUniqueId());
        if (set.isEmpty()) {
            Szoveg.kuld(core, p, "&7Még nincs barátod. Jelölj be valakit: &f/barat <játékos>");
            return;
        }
        List<String> fent = new ArrayList<>();
        List<UUID> lent = new ArrayList<>();
        for (UUID f : set) {
            Player bp = Bukkit.getPlayer(f);
            if (bp != null && p.canSee(bp)) {
                fent.add("&a● &f" + bp.getName() + " &8- &7" + hol(bp) + " [[&b[Party]|run:/party meghiv " + bp.getName() + "]]");
            } else {
                lent.add(f);
            }
        }
        Map<UUID, Long> utoljara = new HashMap<>();
        UUID en = p.getUniqueId();
        core.database().query(connection -> {
            Map<UUID, Long> m = new HashMap<>();
            for (UUID f : lent) {
                try (PreparedStatement st = connection.prepareStatement("SELECT last_seen FROM im_players WHERE uuid = ?")) {
                    st.setString(1, f.toString());
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next()) {
                            m.put(f, rs.getLong(1));
                        }
                    }
                }
            }
            return m;
        }).thenAccept(m -> Bukkit.getScheduler().runTask(core, () -> {
            Player me = Bukkit.getPlayer(en);
            if (me == null) {
                return;
            }
            utoljara.putAll(m);
            Szoveg.kuld(core, me, "&b&lBarátok &7(" + fent.size() + " fent, összesen " + set.size() + ")");
            for (String line : fent) {
                me.sendMessage(hu.icemine.core.util.ClickText.line(line, "&7Kattints: &f%value%", ""));
            }
            lent.sort((x, y) -> Long.compare(utoljara.getOrDefault(y, 0L), utoljara.getOrDefault(x, 0L)));
            for (UUID f : lent) {
                String n = Bukkit.getOfflinePlayer(f).getName();
                Long t = utoljara.get(f);
                me.sendMessage(hu.icemine.core.util.Text.color("&8○ &7" + (n == null ? "?" : n) + (t == null || t == 0 ? "" : " &8- " + mikor(t))));
            }
        }));
    }

    private String hol(Player bp) {
        String mod = core.modes().current(bp);
        hu.icemine.core.mode.ModeInfo info = mod == null ? null : core.modes().info(mod);
        return info != null ? hu.icemine.core.util.Text.strip(hu.icemine.core.util.Text.color(info.name)) : "Lobby";
    }

    static String mikor(long t) {
        long perc = Math.max(0, (System.currentTimeMillis() - t) / 60_000L);
        if (perc < 60) {
            return perc + " perce";
        }
        if (perc < 24 * 60) {
            return (perc / 60) + " órája";
        }
        return (perc / 60 / 24) + " napja";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : new String[] {"lista", "kerelmek", "torol", "elfogad"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        if (args.length == 2 && sender instanceof Player p) {
            Set<String> out = new LinkedHashSet<>();
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("torol")) {
                for (UUID f : of(p.getUniqueId())) {
                    String n = Bukkit.getOfflinePlayer(f).getName();
                    if (n != null && n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(n);
                    }
                }
            } else if (sub.equals("elfogad") || sub.equals("elutasit")) {
                for (UUID f : keresek.getOrDefault(p.getUniqueId(), Map.of()).keySet()) {
                    String n = Bukkit.getOfflinePlayer(f).getName();
                    if (n != null) {
                        out.add(n);
                    }
                }
            }
            return new ArrayList<>(out);
        }
        return List.of();
    }
}
