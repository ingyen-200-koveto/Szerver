package hu.icemine.core.anticheat;

import hu.icemine.core.IceMineCore;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * A GrimAC jelzéseit játékosonként összeszámolja, és ha valaki rövid idő alatt sokszor gyanús, a weboldalon át
 * a Discord csalásgyanú csatornába küld egy kártyát. A Grim API-ját reflexióval használjuk, így Grim nélkül is fut.
 */
public final class CsalasGyanu implements Listener {

    static final class Allapot {
        String nev;
        long elso;
        long utolso;
        long riasztva;
        int db;
        final Map<String, Integer> checkek = new HashMap<>();
        final Map<String, Double> maxVl = new HashMap<>();
    }

    private final IceMineCore core;
    private final Map<UUID, Allapot> allapot = new ConcurrentHashMap<>();
    private Method getUser;
    private Method getCheck;
    private Method getViolations;
    private Method getCheckName;
    private Method userName;
    private Method userId;

    public CsalasGyanu(IceMineCore core) {
        this.core = core;
    }

    @SuppressWarnings("unchecked")
    public void start() throws Exception {
        if (!core.getConfig().getBoolean("csalasgyanu.be", true) || Bukkit.getPluginManager().getPlugin("GrimAC") == null) {
            return;
        }
        ClassLoader loader = Bukkit.getPluginManager().getPlugin("GrimAC").getClass().getClassLoader();
        Class<? extends Event> flag = (Class<? extends Event>) Class.forName("ac.grim.grimac.api.events.FlagEvent", true, loader);
        getUser = flag.getMethod("getUser");
        getCheck = flag.getMethod("getCheck");
        getViolations = flag.getMethod("getViolations");
        Class<?> check = Class.forName("ac.grim.grimac.api.AbstractCheck", true, loader);
        getCheckName = check.getMethod("getCheckName");
        Class<?> user = Class.forName("ac.grim.grimac.api.GrimUser", true, loader);
        userName = user.getMethod("getName");
        userId = Class.forName("ac.grim.grimac.api.GrimIdentity", true, loader).getMethod("getUniqueId");
        Bukkit.getPluginManager().registerEvent(flag, this, EventPriority.MONITOR, (listener, event) -> {
            if (flag.isInstance(event)) {
                jelzes(event);
            }
        }, core, true);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    private void jelzes(Event event) {
        try {
            Object user = getUser.invoke(event);
            Object check = getCheck.invoke(event);
            String checkNev = String.valueOf(getCheckName.invoke(check));
            double vl = ((Number) getViolations.invoke(event)).doubleValue();
            UUID id = (UUID) userId.invoke(user);
            String nev = String.valueOf(userName.invoke(user));
            rogzit(id, nev, checkNev, vl, System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }

    void rogzit(UUID id, String nev, String check, double vl, long most) {
        List<String> kihagy = core.getConfig().getStringList("csalasgyanu.kihagy-checkek");
        for (String k : kihagy) {
            if (check.toLowerCase(Locale.ROOT).startsWith(k.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        long ablak = core.getConfig().getLong("csalasgyanu.ablak-perc", 5) * 60_000L;
        long szunet = core.getConfig().getLong("csalasgyanu.szunet-perc", 10) * 60_000L;
        int kuszob = Math.max(3, core.getConfig().getInt("csalasgyanu.kuszob", 20));
        Allapot a = allapot.computeIfAbsent(id, x -> new Allapot());
        String json = lep(a, id, nev, check, vl, most, ablak, kuszob, szunet);
        if (json == null) {
            return;
        }
        String body = json;
        Bukkit.getScheduler().runTask(core, () -> core.iceCoins().post("/api/mc/csalasgyanu", kiegeszit(body, id), null));
    }

    /** Egy jelzés feldolgozása. Ha riasztani kell, a JSON elejét adja vissza, különben null. */
    static String lep(Allapot a, UUID id, String nev, String check, double vl, long most, long ablak, int kuszob, long szunet) {
        synchronized (a) {
            if (a.db == 0 || most - a.utolso > ablak) {
                a.db = 0;
                a.elso = most;
                a.checkek.clear();
                a.maxVl.clear();
            }
            a.nev = nev;
            a.utolso = most;
            a.db++;
            a.checkek.merge(check, 1, Integer::sum);
            a.maxVl.merge(check, vl, Math::max);
            if (a.db < kuszob || (a.riasztva > 0 && most - a.riasztva < szunet)) {
                return null;
            }
            a.riasztva = most;
            String json = json(a, id);
            a.db = 0;
            a.checkek.clear();
            a.maxVl.clear();
            return json;
        }
    }

    static String json(Allapot a, UUID id) {
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(a.checkek.entrySet());
        lista.sort((x, y) -> y.getValue() - x.getValue());
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : lista.subList(0, Math.min(6, lista.size()))) {
            items.add("{\"nev\":" + q(e.getKey()) + ",\"db\":" + e.getValue() + ",\"vl\":" + Math.round(a.maxVl.getOrDefault(e.getKey(), 0.0) * 10) / 10.0 + "}");
        }
        return "{\"jatekos\":" + q(a.nev) + ",\"uuid\":\"" + id + "\",\"db\":" + a.db + ",\"mp\":" + Math.max(1, (a.utolso - a.elso) / 1000)
                + ",\"checkek\":[" + String.join(",", items) + "]";
    }

    private String kiegeszit(String json, UUID id) {
        Player p = Bukkit.getPlayer(id);
        String vilag = p == null ? "" : p.getWorld().getName();
        int ping = p == null ? -1 : p.getPing();
        return json + ",\"vilag\":" + q(vilag) + ",\"ping\":" + ping + ",\"online\":" + (p != null) + "}";
    }

    static String q(String value) {
        return hu.icemine.core.report.ReportCommands.q(value == null ? "" : value);
    }
}
