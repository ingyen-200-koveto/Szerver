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
    private static final Pattern RANK = Pattern.compile("\"([a-z0-9_]{1,16})\"\\s*:\\s*\\{[^{}]*?\"rank\"\\s*:\\s*\"([^\"]{0,40})\"");

    private final IceMineCore core;
    private final Map<String, Long> coins = new ConcurrentHashMap<>();
    private final Set<String> checked = ConcurrentHashMap.newKeySet();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private BukkitTask task;
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
        refresh = Math.max(10, Math.min(600, config.getInt("icecoin.refresh-seconds", 30)));
        if (enabled && !url.startsWith("http")) {
            problems.add("config.yml -> icecoin.url: http:// vagy https:// kezdetű cím kell (most: " + url + ")");
            enabled = false;
        }
        stop();
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimer(core, this::poll, 60L, refresh * 20L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
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
            Bukkit.getScheduler().runTask(core, () -> {
                if (core.webRanks() != null) {
                    core.webRanks().update(ranks);
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
