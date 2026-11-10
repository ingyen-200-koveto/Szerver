package hu.icemine.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Belépés a weboldalas jelszóval: a /login jelszavát a weboldal ellenőrzi (https://icemine.hu/api/mc/login), és ha jó,
 * az AuthMe beléptet, a szerveres jelszót pedig a weboldaléra állítja. Regisztrálni csak a weboldalon lehet.
 */
public final class WebLogin implements Listener {

    static final Set<String> LOGIN = Set.of("login", "l", "log", "authme:login", "authme:l", "authme:log");
    static final Set<String> REGISTER = Set.of("register", "reg", "authme:register", "authme:reg");
    private static final Pattern RESULT = Pattern.compile("\"result\"\\s*:\\s*\"([a-z]+)\"");
    private static final Pattern CREATED = Pattern.compile("\"created\"\\s*:\\s*(\\d+)");
    private static final Pattern WAIT = Pattern.compile("\"wait\"\\s*:\\s*(\\d+)");

    private final IceMineCore core;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<UUID, Integer> wrong = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<String> linked = new HashSet<>();
    private boolean enabled;
    private boolean oldAccounts;
    private boolean serverRegister;
    private int maxTries;
    private String url = "";
    private String key = "";
    private String site = "";
    private File linkFile;

    public WebLogin(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration main) {
        enabled = main.getBoolean("weboldal-belepes.enabled", true);
        oldAccounts = main.getBoolean("weboldal-belepes.regi-fiokok", true);
        serverRegister = main.getBoolean("weboldal-belepes.szerveren-regisztracio", false);
        maxTries = Math.max(1, Math.min(20, main.getInt("weboldal-belepes.probalkozas", 5)));
        url = main.getString("icecoin.url", "https://icemine.hu").trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        key = main.getString("icecoin.api-key", "").trim();
        site = main.getString("weboldal-belepes.weboldal", "").trim();
        if (site.isEmpty()) {
            site = url.replaceFirst("^https?://", "");
        }
        linkFile = new File(core.getDataFolder(), "weboldal-fiokok.yml");
        linked.clear();
        if (linkFile.exists()) {
            for (String name : YamlConfiguration.loadConfiguration(linkFile).getStringList("osszekotve")) {
                linked.add(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    public boolean active() {
        return enabled && core.auth() != null && core.auth().present() && url.startsWith("http");
    }

    /** A weboldalon a Bedrock játékosok neve a pont nélküli név. */
    static String webName(String name) {
        String result = name;
        while (result.startsWith(".") || result.startsWith("*")) {
            result = result.substring(1);
        }
        return result.toLowerCase(Locale.ROOT);
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!active()) {
            return;
        }
        String message = event.getMessage().trim();
        if (!message.startsWith("/")) {
            return;
        }
        String[] parts = message.substring(1).split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        Player player = event.getPlayer();
        if (REGISTER.contains(label)) {
            if (serverRegister || core.auth().isLoggedIn(player)) {
                return;
            }
            event.setCancelled(true);
            core.messages().send(player, "weblogin.register", "%url%", site, "%name%", webName(player.getName()));
            return;
        }
        if (!LOGIN.contains(label) || core.auth().isLoggedIn(player)) {
            return;
        }
        event.setCancelled(true);
        if (parts.length < 2 || parts[1].isEmpty()) {
            core.messages().send(player, "weblogin.usage");
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            core.messages().send(player, "weblogin.busy");
            return;
        }
        String password = parts[1];
        String name = webName(player.getName());
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            String[] answer = ask(name, password);
            Bukkit.getScheduler().runTask(core, () -> {
                busy.remove(player.getUniqueId());
                if (player.isOnline()) {
                    handle(player, password, answer);
                }
            });
        });
    }

    /** @return {eredmény, létrehozás ideje, várakozás} - eredmény: ok, hibas, nincs, megerosites, varj, hiba */
    String[] ask(String name, String password) {
        try {
            String body = "{\"name\":" + json(name) + ",\"password\":" + json(password) + "}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/mc/login")).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json").header("X-IceMine-Key", key).header("User-Agent", "IceMine-Minecraft")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String text = response.body() == null ? "" : response.body();
            if (response.statusCode() == 429) {
                Matcher wait = WAIT.matcher(text);
                return new String[]{"varj", "0", wait.find() ? wait.group(1) : "600"};
            }
            Matcher result = RESULT.matcher(text);
            if (response.statusCode() != 200 || !result.find()) {
                if (response.statusCode() == 401) {
                    core.getLogger().warning("Weboldalas belépés: rossz icecoin.api-key a config.yml-ben (a weboldal .env MC_API_KEY értéke kell).");
                } else if (response.statusCode() == 404) {
                    core.getLogger().warning("Weboldalas belépés: a weboldalon még nincs /api/mc/login (frissítsd a weboldalt).");
                }
                return new String[]{"hiba", "0", "0"};
            }
            Matcher created = CREATED.matcher(text);
            return new String[]{result.group(1), created.find() ? created.group(1) : "0", "0"};
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new String[]{"hiba", "0", "0"};
        } catch (Exception exception) {
            return new String[]{"hiba", "0", "0"};
        }
    }

    private void handle(Player player, String password, String[] answer) {
        AuthHook auth = core.auth();
        if (auth.isLoggedIn(player)) {
            return;
        }
        String local = player.getName();
        String lower = local.toLowerCase(Locale.ROOT);
        boolean registered = auth.isRegistered(local);
        switch (answer[0]) {
            case "ok": {
                long created = parse(answer[1]);
                boolean allowed = !registered || linked.contains(lower) || auth.checkPassword(local, password);
                if (!allowed) {
                    Instant at = auth.registeredAt(local);
                    allowed = at != null && created > 0 && at.toEpochMilli() >= created;
                }
                if (!allowed) {
                    core.messages().send(player, "weblogin.conflict");
                    return;
                }
                if (!auth.loginWith(player, password)) {
                    core.messages().send(player, "weblogin.failed");
                    return;
                }
                link(lower);
                wrong.remove(player.getUniqueId());
                core.messages().send(player, "weblogin.success");
                return;
            }
            case "nincs":
                if (oldAccounts && registered && auth.checkPassword(local, password)) {
                    loginLocal(player, "weblogin.success-local");
                } else if (oldAccounts && registered) {
                    wrongPassword(player);
                } else {
                    core.messages().send(player, "weblogin.no-account", "%url%", site, "%name%", webName(local));
                }
                return;
            case "hibas":
                if (oldAccounts && registered && !linked.contains(lower) && auth.checkPassword(local, password)) {
                    loginLocal(player, "weblogin.success-old");
                } else {
                    wrongPassword(player);
                }
                return;
            case "megerosites":
                core.messages().send(player, "weblogin.verify");
                return;
            case "varj":
                core.messages().send(player, "weblogin.wait", "%minutes%", String.valueOf(Math.max(1, (parse(answer[2]) + 59) / 60)));
                return;
            default:
                if (registered && auth.checkPassword(local, password)) {
                    loginLocal(player, "weblogin.success-offline");
                } else {
                    core.messages().send(player, "weblogin.offline");
                }
        }
    }

    private void loginLocal(Player player, String message) {
        if (core.auth().forceLogin(player)) {
            wrong.remove(player.getUniqueId());
            core.messages().send(player, message, "%url%", site);
        } else {
            core.messages().send(player, "weblogin.failed");
        }
    }

    private void wrongPassword(Player player) {
        int count = wrong.merge(player.getUniqueId(), 1, Integer::sum);
        if (count >= maxTries) {
            wrong.remove(player.getUniqueId());
            player.kickPlayer(core.messages().get("weblogin.kick", "%url%", site));
            return;
        }
        core.messages().send(player, "weblogin.wrong", "%url%", site, "%left%", String.valueOf(maxTries - count));
    }

    private void link(String lower) {
        if (!linked.add(lower) || linkFile == null) {
            return;
        }
        YamlConfiguration file = new YamlConfiguration();
        file.set("osszekotve", new java.util.ArrayList<>(linked));
        try {
            file.save(linkFile);
        } catch (IOException exception) {
            core.getLogger().warning("Nem sikerült menteni: " + linkFile.getName() + " (" + exception.getMessage() + ")");
        }
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wrong.remove(event.getPlayer().getUniqueId());
        busy.remove(event.getPlayer().getUniqueId());
    }
}
