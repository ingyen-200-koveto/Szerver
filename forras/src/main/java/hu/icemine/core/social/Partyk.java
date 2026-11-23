package hu.icemine.core.social;

import hu.icemine.core.IceMineCore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * /party: legfeljebb 8 fős csapat. A vezető beléptetheti a party-t BedWarsba (egy arénába, egy csapatba), és
 * maga után hívhatja őket (/party warp). Party chat: /party chat vagy /pc.
 */
public final class Partyk implements CommandExecutor, TabCompleter, Listener {

    public static final int MAX = 8;
    static final long HIVAS_IDO = 60_000L;

    public static final class Party {
        public UUID vezeto;
        public final Set<UUID> tagok = new LinkedHashSet<>();
        /** Ha a vezető belép egy módba, a szabad tagok vele mennek. */
        public boolean kovetes = true;
        final Map<UUID, Long> meghivottak = new ConcurrentHashMap<>();

        Party(UUID vezeto) {
            this.vezeto = vezeto;
            tagok.add(vezeto);
        }

        public List<Player> online() {
            List<Player> out = new ArrayList<>();
            for (UUID id : tagok) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }
    }

    private final IceMineCore core;
    private final Map<UUID, Party> partyja = new ConcurrentHashMap<>();

    public Partyk(IceMineCore core) {
        this.core = core;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, core);
    }

    /** A játékos partyja, vagy null. */
    public Party of(UUID id) {
        return partyja.get(id);
    }

    public boolean vezetoE(UUID id) {
        Party p = of(id);
        return p != null && p.vezeto.equals(id);
    }

    void mindenkinek(Party party, String text) {
        for (Player p : party.online()) {
            Szoveg.kuld(core, p, text);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        kilep(event.getPlayer().getUniqueId(), event.getPlayer().getName(), true);
    }

    private void kilep(UUID id, String nev, boolean lelepett) {
        Party party = partyja.remove(id);
        if (party == null) {
            return;
        }
        party.tagok.remove(id);
        mindenkinek(party, "&7" + nev + (lelepett ? " lelépett, ezért kikerült a partyból." : " kilépett a partyból."));
        if (party.tagok.size() <= 1) {
            for (UUID m : party.tagok) {
                partyja.remove(m);
                Player p = Bukkit.getPlayer(m);
                if (p != null) {
                    Szoveg.kuld(core, p, "&7A party feloszlott, mert egyedül maradtál.");
                }
            }
            party.tagok.clear();
            return;
        }
        if (party.vezeto.equals(id)) {
            party.vezeto = party.tagok.iterator().next();
            Player uj = Bukkit.getPlayer(party.vezeto);
            mindenkinek(party, "&eAz új vezető: &f" + (uj == null ? "?" : uj.getName()));
        }
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
        if (command.getName().equalsIgnoreCase("pc")) {
            chat(p, String.join(" ", args));
            return true;
        }
        if (args.length == 0) {
            lista(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String nev = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "meghiv", "meghív", "invite" -> {
                if (nev == null) {
                    Szoveg.kuld(core, p, "&7Használat: &f/party meghiv <játékos>");
                } else {
                    meghiv(p, nev);
                }
            }
            case "elfogad", "accept" -> {
                if (nev == null) {
                    nev = egyetlenMeghivo(p);
                }
                if (nev == null) {
                    Szoveg.kuld(core, p, "&7Használat: &f/party elfogad <játékos>");
                } else {
                    elfogad(p, nev);
                }
            }
            case "kilep", "kilép", "leave" -> {
                if (of(p.getUniqueId()) == null) {
                    Szoveg.kuld(core, p, "&cNem vagy partyban.");
                } else {
                    kilep(p.getUniqueId(), p.getName(), false);
                    Szoveg.kuld(core, p, "&7Kiléptél a partyból.");
                }
            }
            case "kirug", "kirúg", "kick" -> kirug(p, nev);
            case "atad", "átad", "transfer" -> atad(p, nev);
            case "feloszlat", "disband" -> feloszlat(p);
            case "lista", "info" -> lista(p);
            case "warp", "hiv", "hív" -> warp(p);
            case "kovetes", "követés", "follow" -> kovetesKapcsolo(p);
            case "chat" -> chat(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            case "segitseg", "help" -> sugo(p);
            default -> meghiv(p, args[0]);
        }
        return true;
    }

    private void sugo(Player p) {
        Szoveg.kuld(core, p, "&b&lParty &7(legfeljebb " + MAX + " fő)");
        for (String s : new String[] {
                "&f/party <játékos> &7- meghívás",
                "&f/party lista &7- kik vannak benne",
                "&f/party warp &7- a tagok hozzád jönnek (vezető)",
                "&f/party kovetes &7- a tagok veled jönnek, ha belépsz egy módba (be/ki)",
                "&f/pc <üzenet> &7- party chat",
                "&f/party kirug <játékos>&7, &f/party atad <játékos>&7, &f/party feloszlat",
                "&f/party kilep &7- kilépés",
                "&7BedWarsba a vezető lép be, a többiek vele mennek, egy csapatba."}) {
            p.sendMessage(hu.icemine.core.util.Text.color(s));
        }
    }

    private void meghiv(Player p, String nev) {
        if (!Szoveg.nevJo(nev)) {
            Szoveg.kuld(core, p, "&cEz nem érvényes játékosnév.");
            return;
        }
        Player cel = Bukkit.getPlayerExact(nev);
        if (cel == null || !p.canSee(cel)) {
            Szoveg.kuld(core, p, "&c" + nev + " nincs fent.");
            return;
        }
        if (cel.equals(p)) {
            Szoveg.kuld(core, p, "&cMagadat nem hívhatod meg.");
            return;
        }
        Party party = of(p.getUniqueId());
        if (party != null && !party.vezeto.equals(p.getUniqueId())) {
            Szoveg.kuld(core, p, "&cCsak a party vezetője hívhat meg valakit.");
            return;
        }
        if (of(cel.getUniqueId()) != null) {
            Szoveg.kuld(core, p, "&c" + cel.getName() + " már egy másik partyban van.");
            return;
        }
        if (party == null) {
            party = new Party(p.getUniqueId());
            partyja.put(p.getUniqueId(), party);
        }
        if (party.tagok.size() >= MAX) {
            Szoveg.kuld(core, p, "&cA party tele van (" + MAX + " fő).");
            return;
        }
        party.meghivottak.put(cel.getUniqueId(), System.currentTimeMillis());
        Szoveg.kuld(core, p, "&aMeghívó elküldve: &f" + cel.getName() + "&a. 60 másodpercig érvényes.");
        Szoveg.kuld(core, cel, "&b" + p.getName() + "&f meghívott a partyjába. [[&a[Csatlakozom]|run:/party elfogad " + p.getName() + "]]");
        cel.playSound(cel.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7F, 1.0F);
    }

    /** Ha pontosan egy élő meghívója van, annak a party vezetőjének a neve. */
    private String egyetlenMeghivo(Player p) {
        String talalt = null;
        long most = System.currentTimeMillis();
        for (Party party : new java.util.HashSet<>(partyja.values())) {
            Long t = party.meghivottak.get(p.getUniqueId());
            if (t != null && most - t <= HIVAS_IDO) {
                if (talalt != null) {
                    return null;
                }
                talalt = Bukkit.getOfflinePlayer(party.vezeto).getName();
            }
        }
        return talalt;
    }

    private void elfogad(Player p, String nev) {
        Player vez = Bukkit.getPlayerExact(nev);
        Party party = vez == null ? null : of(vez.getUniqueId());
        Long t = party == null ? null : party.meghivottak.get(p.getUniqueId());
        if (t == null || System.currentTimeMillis() - t > HIVAS_IDO) {
            Szoveg.kuld(core, p, "&cNincs érvényes meghívásod tőle (60 másodpercig él).");
            return;
        }
        if (of(p.getUniqueId()) != null) {
            Szoveg.kuld(core, p, "&cMár partyban vagy. Előbb lépj ki: &f/party kilep");
            return;
        }
        if (party.tagok.size() >= MAX) {
            Szoveg.kuld(core, p, "&cA party közben megtelt.");
            return;
        }
        party.meghivottak.remove(p.getUniqueId());
        party.tagok.add(p.getUniqueId());
        partyja.put(p.getUniqueId(), party);
        mindenkinek(party, "&a" + p.getName() + " csatlakozott a partyhoz. &7(" + party.tagok.size() + "/" + MAX + ")");
    }

    private Player tagNevvel(Party party, String nev) {
        for (Player m : party.online()) {
            if (m.getName().equalsIgnoreCase(nev)) {
                return m;
            }
        }
        return null;
    }

    private Party vezetokent(Player p) {
        Party party = of(p.getUniqueId());
        if (party == null) {
            Szoveg.kuld(core, p, "&cNem vagy partyban.");
            return null;
        }
        if (!party.vezeto.equals(p.getUniqueId())) {
            Szoveg.kuld(core, p, "&cEzt csak a party vezetője teheti meg.");
            return null;
        }
        return party;
    }

    private void kirug(Player p, String nev) {
        Party party = vezetokent(p);
        if (party == null || nev == null) {
            return;
        }
        Player m = tagNevvel(party, nev);
        if (m == null || m.equals(p)) {
            Szoveg.kuld(core, p, "&c" + nev + " nincs a partyban.");
            return;
        }
        kilep(m.getUniqueId(), m.getName(), false);
        Szoveg.kuld(core, m, "&cKirúgtak a partyból.");
    }

    private void atad(Player p, String nev) {
        Party party = vezetokent(p);
        if (party == null || nev == null) {
            return;
        }
        Player m = tagNevvel(party, nev);
        if (m == null || m.equals(p)) {
            Szoveg.kuld(core, p, "&c" + nev + " nincs a partyban.");
            return;
        }
        party.vezeto = m.getUniqueId();
        mindenkinek(party, "&eMostantól &f" + m.getName() + "&e a party vezetője.");
    }

    private void feloszlat(Player p) {
        Party party = vezetokent(p);
        if (party == null) {
            return;
        }
        mindenkinek(party, "&7" + p.getName() + " feloszlatta a partyt.");
        for (UUID m : party.tagok) {
            partyja.remove(m);
        }
        party.tagok.clear();
    }

    private void lista(Player p) {
        Party party = of(p.getUniqueId());
        if (party == null) {
            Szoveg.kuld(core, p, "&7Nem vagy partyban. Hívj meg valakit: &f/party <játékos> &7(súgó: &f/party segitseg&7)");
            return;
        }
        Szoveg.kuld(core, p, "&b&lParty &7(" + party.tagok.size() + "/" + MAX + ")");
        for (UUID id : party.tagok) {
            Player m = Bukkit.getPlayer(id);
            String n = m != null ? m.getName() : Bukkit.getOfflinePlayer(id).getName();
            p.sendMessage(hu.icemine.core.util.Text.color((id.equals(party.vezeto) ? "&6★ " : "&7• ") + "&f" + n));
        }
    }

    private void chat(Player p, String msg) {
        Party party = of(p.getUniqueId());
        if (party == null) {
            Szoveg.kuld(core, p, "&cNem vagy partyban.");
            return;
        }
        String tiszta = msg == null ? "" : msg.replace('§', ' ').replace('&', ' ').trim();
        if (tiszta.isEmpty()) {
            Szoveg.kuld(core, p, "&7Használat: &f/pc <üzenet>");
            return;
        }
        for (Player m : party.online()) {
            m.sendMessage(hu.icemine.core.util.Text.color("&9[Party] &b" + p.getName() + "&8: &f") + tiszta);
        }
        core.getLogger().fine("[Party] " + p.getName() + ": " + tiszta);
    }

    private void warp(Player p) {
        Party party = vezetokent(p);
        if (party == null) {
            return;
        }
        String mod = core.modes().current(p);
        int db = 0;
        for (Player m : party.online()) {
            if (m.equals(p) || elfoglalt(m)) {
                continue;
            }
            if (mod == null || mod.equals(core.modes().current(m))) {
                core.guard().allowNext(m);
                m.teleport(p.getLocation());
            } else {
                core.modes().sendAndReport(m, mod);
            }
            Szoveg.kuld(core, m, "&7A party vezetője magához hívott.");
            db++;
        }
        Szoveg.kuld(core, p, db == 0 ? "&7Senkit nem tudtam idehívni (mindenki játékban van)." : "&a" + db + " tagot idehívtam.");
    }

    private void kovetesKapcsolo(Player p) {
        Party party = vezetokent(p);
        if (party == null) {
            return;
        }
        party.kovetes = !party.kovetes;
        mindenkinek(party, party.kovetes
                ? "&aKövetés bekapcsolva: &7ha " + p.getName() + " belép egy játékmódba, a party vele megy."
                : "&7Követés kikapcsolva: mindenki maga választ játékmódot.");
    }

    /** A ModeService hívja, amikor valaki sikeresen belépett egy módba (vagy annak lobbyjába). */
    public void kovet(Player vezeto, String mod) {
        Party party = of(vezeto.getUniqueId());
        if (party == null || !party.kovetes || !vezeto.getUniqueId().equals(party.vezeto) || !vezeto.isOnline()) {
            return;
        }
        String itt = core.modes().current(vezeto);
        String lobbyja = hu.icemine.core.modelobby.ModeLobbies.PREFIX + mod;
        if (itt == null || !(itt.equals(mod) || itt.equals(lobbyja))) {
            return;
        }
        String nev = core.modes().info(mod) == null ? mod : core.modes().info(mod).name;
        for (Player m : party.online()) {
            if (m.equals(vezeto) || elfoglalt(m)) {
                continue;
            }
            String ahol = core.modes().current(m);
            boolean szabad = hu.icemine.core.mode.ModeService.LOBBY.equals(ahol) || (ahol != null && ahol.startsWith(hu.icemine.core.modelobby.ModeLobbies.PREFIX));
            if (!szabad || itt.equals(ahol)) {
                continue;
            }
            if (core.modes().sendFollow(m, itt.equals(lobbyja) ? mod : mod) == hu.icemine.core.mode.ModeService.SendResult.OK) {
                Szoveg.kuld(core, m, "&7A party vezetője ide ment: " + nev + "&7, jössz vele. &8(/party kilep, ha nem szeretnéd)");
            }
        }
    }

    /** Épp játszik-e (BedWars arénában vagy Duels meccsen), mert akkor nem visszük sehova. */
    public boolean elfoglalt(Player m) {
        try {
            if (core.bedwars() != null && core.bedwars().arenaOf(m) != null) {
                return true;
            }
            if (core.duels() != null && core.duels().match(m) != null) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        return core.combat() != null && core.combat().tagged(m);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pc") || !(sender instanceof Player p)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String a = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : new String[] {"meghiv", "lista", "warp", "chat", "kilep", "kirug", "atad", "feloszlat", "segitseg"}) {
                if (s.startsWith(a)) {
                    out.add(s);
                }
            }
        }
        if (args.length <= 2) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(p) && p.canSee(o) && o.getName().toLowerCase(Locale.ROOT).startsWith(a)) {
                    out.add(o.getName());
                }
            }
        }
        return out;
    }
}
