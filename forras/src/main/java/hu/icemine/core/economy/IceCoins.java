package hu.icemine.core.economy;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Text;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A weboldalon lévő IceCoin egyenleg. A szerver időnként lekérdezi a weboldaltól (https://icemine.hu/api/mc/coins)
 * az online játékosok egyenlegét, a scoreboard ezt mutatja (%icecoin%). Csak olvas, a pénzt a weboldal kezeli.
 */
public final class IceCoins {

    private static final Pattern ENTRY = Pattern.compile("\"([a-z0-9_]{1,16})\"\\s*:\\s*\\{\\s*\"coins\"\\s*:\\s*(-?\\d+)");
    private static final Pattern SKIN = Pattern.compile("\"([a-z0-9_]{1,16})\"\\s*:\\s*\\{[^{}]*?\"skin\"\\s*:\\s*(\\d+)(?:\\s*,\\s*\"slim\"\\s*:\\s*(true|false))?");
    private static final Pattern RANK = Pattern.compile("\"([a-z0-9_]{1,16})\"\\s*:\\s*\\{[^{}]*?\"rank\"\\s*:\\s*\"([^\"]{0,40})\"");

    private final IceMineCore core;
    private final Map<String, Long> coins = new ConcurrentHashMap<>();
    private final Set<String> checked = ConcurrentHashMap.newKeySet();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private BukkitTask task;
    private BukkitTask statusTask;
    private volatile boolean statusBusy;
    private volatile boolean busy;
    private volatile long lastWarning;
    private boolean enabled;
    private String url = "";
    private String key = "";
    private int refresh = 30;

    public IceCoins(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        enabled = config.getBoolean("icecoin.enabled", true);
        url = config.getString("icecoin.url", "https://icemine.hu").trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        key = config.getString("icecoin.api-key", "").trim();
        if (core.webSkins() != null) {
            core.webSkins().setUrl(url);
        }
        refresh = Math.max(10, Math.min(600, config.getInt("icecoin.refresh-seconds", 30)));
        if (enabled && !url.startsWith("http")) {
            problems.add("config.yml -> icecoin.url: http:// vagy https:// kezdetű cím kell (most: " + url + ")");
            enabled = false;
        }
        stop();
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimer(core, this::poll, 60L, refresh * 20L);
            statusTask = Bukkit.getScheduler().runTaskTimer(core, this::pushStatus, 100L, refresh * 20L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (statusTask != null) {
            statusTask.cancel();
            statusTask = null;
        }
    }

    /** Belépés után pár másodperccel lekérdezzük, hogy ne kelljen a következő körig várni. */
    public void refreshSoon() {
        if (enabled) {
            Bukkit.getScheduler().runTaskLater(core, this::poll, 40L);
        }
    }

    public void forget(Player player) {
        String name = key(player);
        coins.remove(name);
        checked.remove(name);
    }

    private void poll() {
        if (!enabled || busy) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasMetadata("NPC")) {
                names.add(key(player));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        busy = true;
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            try {
                for (int i = 0; i < names.size(); i += 100) {
                    fetch(names.subList(i, Math.min(names.size(), i + 100)));
                }
            } finally {
                busy = false;
            }
        });
    }

    private void fetch(List<String> names) {
        try {
            URI uri = URI.create(url + "/api/mc/coins?names=" + URLEncoder.encode(String.join(",", names), StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("X-IceMine-Key", key)
                    .header("User-Agent", "IceMine-Minecraft").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warn("a weboldal " + response.statusCode() + " hibát adott" + (response.statusCode() == 401 ? " (rossz icecoin.api-key a config.yml-ben)" : ""));
                return;
            }
            Map<String, Long> found = parse(response.body());
            Map<String, String> ranks = parseRanks(response.body());
            Map<String, long[]> skins = parseSkins(response.body());
            Bukkit.getScheduler().runTask(core, () -> {
                if (core.webRanks() != null) {
                    core.webRanks().update(ranks);
                }
                if (core.webSkins() != null) {
                    core.webSkins().update(names, skins);
                }
            });
            for (String name : names) {
                Long value = found.get(name);
                if (value == null) {
                    coins.remove(name);
                } else {
                    coins.put(name, value);
                }
                checked.add(name);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            warn("nem értem el a weboldalt (" + exception.getClass().getSimpleName() + ": " + exception.getMessage() + ")");
        }
    }

    /**
     * A weboldal Kezdőlapján látszik, melyik játékmódban hányan vannak.
     * Ezt küldjük el (https://icemine.hu/api/mc/status): {"modes":{"lobby":3,"fyland":5,"bedwars":2}}
     */
    private void pushStatus() {
        if (!enabled || statusBusy || core.modes() == null) {
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("lobby", 0);
        for (String id : core.modes().ids()) {
            hu.icemine.core.mode.ModeInfo info = core.modes().info(id);
            if (info != null && !info.internal) {
                counts.put(id.toLowerCase(Locale.ROOT), 0);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasMetadata("NPC")) {
                continue;
            }
            String id = core.modes().current(player);
            String k = id == null ? "lobby" : id.toLowerCase(Locale.ROOT);
            counts.merge(counts.containsKey(k) ? k : "lobby", 1, Integer::sum);
        }
        StringBuilder json = new StringBuilder("{\"modes\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_-]{1,24}")) {
                continue;
            }
            json.append(first ? "" : ",").append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        String body = json.append("}}").toString();
        statusBusy = true;
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/mc/status")).timeout(Duration.ofSeconds(8))
                        .header("X-IceMine-Key", key).header("Content-Type", "application/json").header("User-Agent", "IceMine-Minecraft")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // a létszám csak tájékoztató, ha most nem megy át, a következő körben újra próbáljuk
            } finally {
                statusBusy = false;
            }
        });
    }

    /** JSON-t küld a weboldalnak a háttérben; a válasz HTTP kódját (vagy -1-et) a fő szálon adja vissza. */
    private final java.util.Map<String, Long> postWarnings = new java.util.concurrent.ConcurrentHashMap<>();

    public void post(String path, String json, java.util.function.IntConsumer done) {
        if (!enabled || url.isEmpty()) {
            if (done != null) {
                done.accept(-1);
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            int status = -1;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(8))
                        .header("X-IceMine-Key", key).header("Content-Type", "application/json").header("User-Agent", "IceMine-Minecraft")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
                status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                long now = System.currentTimeMillis();
                Long last = postWarnings.get(path);
                if (last == null || now - last > 600000L) {
                    postWarnings.put(path, now);
                    String why = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    core.getLogger().warning("Weboldal (" + path + "): nem érem el (" + why + ").");
                }
            }
            final int result = status;
            if (done != null && core.isEnabled()) {
                Bukkit.getScheduler().runTask(core, () -> done.accept(result));
            }
        });
    }

    private void warn(String text) {
        long now = System.currentTimeMillis();
        if (now - lastWarning > 600000L) {
            lastWarning = now;
            core.getLogger().warning("IceCoin: " + text + ". Cím: " + url + "/api/mc/coins");
        }
    }

    /** A weboldalon a játékos neve (Bedrockon a pont nélkül). */
    public static String key(Player player) {
        return hu.icemine.core.WebLogin.webName(player.getName());
    }

    /** A weboldal válaszából a skinek: {"players":{"steve":{"coins":1,"rank":"VIP","skin":1712345,"slim":true}}} -> steve = [1712345, 1]. */
    static Map<String, long[]> parseSkins(String body) {
        Map<String, long[]> result = new HashMap<>();
        if (body == null) {
            return result;
        }
        Matcher matcher = SKIN.matcher(body);
        while (matcher.find()) {
            try {
                result.put(matcher.group(1), new long[] {Long.parseLong(matcher.group(2)), "true".equals(matcher.group(3)) ? 1 : 0});
            } catch (NumberFormatException ignored) {
                // túl nagy szám: nem skin verzió, kihagyjuk
            }
        }
        return result;
    }

    /** A weboldal válaszából a rangok: {"players":{"steve":{"coins":1234,"rank":"VIP"}}} -> steve = VIP. */
    static Map<String, String> parseRanks(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null) {
            return result;
        }
        Matcher matcher = RANK.matcher(body);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    /** A weboldal válaszából: {"players":{"steve":{"coins":1234,"rank":"VIP"}}} -> steve = 1234. */
    static Map<String, Long> parse(String body) {
        Map<String, Long> result = new HashMap<>();
        if (body == null) {
            return result;
        }
        Matcher matcher = ENTRY.matcher(body);
        while (matcher.find()) {
            try {
                result.put(matcher.group(1), Long.parseLong(matcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /** A scoreboardra: az egyenleg, vagy "nincs fiók", ha nem regisztrált a weboldalon. */
    public String display(Player player) {
        String name = key(player);
        Long value = coins.get(name);
        if (value != null) {
            return Text.number(value);
        }
        if (!enabled) {
            return "-";
        }
        return core.messages().raw(checked.contains(name) ? "icecoin.no-account" : "icecoin.loading");
    }

    public Long balance(Player player) {
        return coins.get(key(player));
    }
}
