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
        key = ApiKulcs.resolve(core.getDataFolder(), config.getString("icecoin.api-key", ""), core.getLogger());
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

    static final long DARAB = 50L * 1024 * 1024;

    /**
     * Fájl feltöltése a weboldalra (a mentésekhez) 50 MB-os darabokban, mert a Cloudflare egyben legfeljebb
     * 100 MB-ot enged át. Aszinkron szálról hívandó, blokkol. Visszaadja az utolsó HTTP kódot (-1: hiba).
     */
    public int uploadBlocking(String path, java.nio.file.Path file) throws Exception {
        if (!enabled || url.isEmpty() || key.isEmpty()) {
            return -1;
        }
        long size = java.nio.file.Files.size(file);
        int parts = (int) Math.max(1, (size + DARAB - 1) / DARAB);
        int status = -1;
        for (int i = 0; i < parts; i++) {
            long from = i * DARAB;
            long len = Math.min(DARAB, size - from);
            int tries = 0;
            while (true) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                    in.skipNBytes(from);
                    java.io.InputStream part = new java.io.FilterInputStream(in) {
                        long left = len;

                        @Override
                        public int read() throws java.io.IOException {
                            if (left <= 0) {
                                return -1;
                            }
                            int b = super.read();
                            if (b >= 0) {
                                left--;
                            }
                            return b;
                        }

                        @Override
                        public int read(byte[] buf, int off, int n) throws java.io.IOException {
                            if (left <= 0) {
                                return -1;
                            }
                            int r = super.read(buf, off, (int) Math.min(n, left));
                            if (r > 0) {
                                left -= r;
                            }
                            return r;
                        }
                    };
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url + path + "&resz=" + i + "&osszes=" + parts))
                            .timeout(Duration.ofMinutes(15))
                            .header("X-IceMine-Key", key).header("Content-Type", "application/octet-stream").header("User-Agent", "IceMine-Minecraft")
                            .POST(HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> part), len)).build();
                    status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                }
                if (status >= 200 && status < 300) {
                    break;
                }
                if (status == 401 || status == 507 || status == 409 || ++tries >= 3) {
                    return status;
                }
                Thread.sleep(5000L);
            }
        }
        return status;
    }

    public boolean hasKey() {
        return !key.isEmpty();
    }

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

    /** Az IceCoin költés eredménye: OK, nincs elég, nincs fiók, vagy nem érem el a weboldalt. */
    public enum Spend { OK, NOT_ENOUGH, NO_ACCOUNT, ERROR }

    /**
     * IceCoin levonása a weboldalon (pl. játékon belüli vásárláshoz). Az id egyedi azonosító: ha a kérés
     * kétszer megy el, a weboldal csak egyszer vonja le. A done a fő szálon fut, a második érték az új egyenleg.
     */
    public void spend(Player player, long amount, String item, java.util.function.BiConsumer<Spend, Long> done) {
        String name = key(player);
        if (!enabled || url.isEmpty() || key.isEmpty() || amount <= 0) {
            done.accept(Spend.ERROR, null);
            return;
        }
        String id = java.util.UUID.randomUUID().toString();
        String json = "{\"name\":\"" + name + "\",\"amount\":" + amount + ",\"item\":\"" + item.replace("\\", "").replace("\"", "") + "\",\"id\":\"" + id + "\"}";
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            Spend result = Spend.ERROR;
            Long balance = null;
            for (int attempt = 0; attempt < 2 && result == Spend.ERROR; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/mc/ic-koltes")).timeout(Duration.ofSeconds(8))
                            .header("X-IceMine-Key", key).header("Content-Type", "application/json").header("User-Agent", "IceMine-Minecraft")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    Matcher m = Pattern.compile("\"coins\"\\s*:\\s*(-?\\d+)").matcher(response.body());
                    balance = m.find() ? Long.parseLong(m.group(1)) : null;
                    result = switch (response.statusCode()) {
                        case 200 -> Spend.OK;
                        case 402 -> Spend.NOT_ENOUGH;
                        case 404 -> Spend.NO_ACCOUNT;
                        default -> Spend.ERROR;
                    };
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception exception) {
                    core.getLogger().warning("IceCoin költés: nem érem el a weboldalt (" + exception.getMessage() + ").");
                }
            }
            if (balance != null) {
                coins.put(name, balance);
            }
            final Spend r = result;
            final Long b = balance;
            if (core.isEnabled()) {
                Bukkit.getScheduler().runTask(core, () -> done.accept(r, b));
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
