package hu.icemine.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IceMineProxy {

    public static final String VERSION = "1.2.0";
    static final String REPO = "https://raw.githubusercontent.com/ingyen-200-koveto/Szerver/main/proxy/";
    static final String GEYSER_API = "https://download.geysermc.org/v2/projects/%s/versions/latest/builds/latest";
    static final Pattern SHA = Pattern.compile("\"velocity\"\\s*:\\s*\\{[^}]*\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"");
    static final Pattern BUILD = Pattern.compile("\"build\"\\s*:\\s*(\\d+)");
    static final String MODRINTH = "https://api.modrinth.com/v2/project/sonar/version";
    static final String SONAR_OLD = "2.1.49";
    static final Pattern MR_VERSION = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
    static final Pattern MR_TYPE = Pattern.compile("\"version_type\"\\s*:\\s*\"([^\"]+)\"");
    static final Pattern MR_SHA1 = Pattern.compile("\"sha1\"\\s*:\\s*\"([0-9a-f]{40})\"");
    static final Pattern MR_URL = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"");

    private final Path root = Paths.get("").toAbsolutePath();
    private final Path plugins = root.resolve("plugins");
    private final Path dataDir = plugins.resolve("icemineproxy");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Object log;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "IceMineProxy");
        thread.setDaemon(true);
        return thread;
    });
    private final Path staging = dataDir.resolve("frissites");
    private final java.util.Map<Path, Path> staged = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean restartNoted;

    public IceMineProxy() {
        this.log = slf4j();
        info("IceMineProxy " + VERSION + " indul.");
        try {
            Files.createDirectories(staging);
        } catch (IOException ignored) {
        }
        applySettings();
        installIcon();
        Runtime.getRuntime().addShutdownHook(new Thread(this::installStaged, "IceMineProxy-frissites"));
        timer.scheduleWithFixedDelay(this::checkUpdates, 20, 30 * 60, TimeUnit.SECONDS);
        startErrorWatch();
    }

    /**
     * Hibafigyelő: a proxy hibái a weboldalon át a Discord szerver-hibák csatornájába. A weboldal címét és a kulcsot
     * (MC_API_KEY, ugyanaz, mint a lobby IceMine configjában az icecoin.api-key) a plugins/icemineproxy/hibak.properties
     * fájlba kell írni.
     */
    void startErrorWatch() {
        Path file = dataDir.resolve("hibak.properties");
        java.util.Properties props = new java.util.Properties();
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, String.join("\n",
                        "# Hibafigyelő: a proxy hibái a Discord szerver-hibák csatornájába (a weboldalon keresztül).",
                        "# kulcs: ugyanaz, mint a weboldal .env fájljában az MC_API_KEY (és a lobbyn az icecoin.api-key).",
                        "# Ha üres, a hibafigyelő nem küld semmit. Átírás után indítsd újra a proxyt.",
                        "be=true",
                        "weboldal=https://icemine.hu",
                        "kulcs=",
                        "nev=Proxy",
                        "csak-hibak=false",
                        "# Vesszővel elválasztva: ha egy sor ezek valamelyikét tartalmazza, nem küldi el",
                        "kihagy=",
                        ""), StandardCharsets.UTF_8);
            }
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException e) {
            warn("A hibak.properties nem olvasható: " + e.getMessage());
            return;
        }
        String url = props.getProperty("weboldal", "").strip().replaceAll("/+$", "");
        String key = props.getProperty("kulcs", "").strip();
        if (!"true".equalsIgnoreCase(props.getProperty("be", "true").strip())) {
            return;
        }
        if (url.isEmpty() || key.isEmpty()) {
            info("Hibafigyelő: írd be a kulcsot a plugins/icemineproxy/hibak.properties fájlba, és a proxy hibái a Discordra kerülnek.");
            return;
        }
        String name = props.getProperty("nev", "Proxy").strip();
        try {
            HibaFigyelo.start(java.util.Arrays.asList(props.getProperty("kihagy", "").split(",")), "true".equalsIgnoreCase(props.getProperty("csak-hibak", "false").strip()));
        } catch (Throwable t) {
            warn("Hibafigyelő nem indult el: " + t);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                HibaFigyelo.stop();
            } catch (Throwable ignored) {
            }
        }, "IceMineProxy-hibafigyelo-stop"));
        timer.scheduleWithFixedDelay(() -> {
            try {
                HibaFigyelo.kuld(name, json -> postJson(url + "/api/mc/hibak", key, json));
            } catch (Throwable ignored) {
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private long lastPostWarning;

    void postJson(String url, String key, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "IceMineProxy/" + VERSION)
                    .header("Content-Type", "application/json")
                    .header("X-IceMine-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 401 && System.currentTimeMillis() - lastPostWarning > 3_600_000L) {
                lastPostWarning = System.currentTimeMillis();
                info("Hibafigyelő: a weboldal nem fogadta el a kulcsot (hibak.properties -> kulcs).");
            }
        } catch (Exception ignored) {
        }
    }

    void applySettings() {
        String text = fetchText(REPO + "beallitasok.txt");
        Path cache = dataDir.resolve("beallitasok.txt");
        try {
            if (text != null) {
                Files.writeString(cache, text, StandardCharsets.UTF_8);
            } else if (Files.exists(cache)) {
                text = Files.readString(cache, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            warn("A beállítások mentése nem sikerült: " + e.getMessage());
        }
        if (text == null) {
            warn("A beállításokat nem sikerült letölteni a GitHubról.");
            return;
        }
        for (Setting setting : Setting.parse(text)) {
            Path file = plugins.resolve(setting.file()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                String original = Files.readString(file, StandardCharsets.UTF_8);
                String changed = file.toString().endsWith(".toml")
                        ? TomlPatch.set(original, setting.key(), setting.value())
                        : YamlPatch.set(original, setting.key(), setting.value());
                if (!changed.equals(original)) {
                    Files.writeString(file, changed, StandardCharsets.UTF_8);
                    info("Beállítva: " + setting.file() + " -> " + setting.key() + " = " + setting.value());
                }
            } catch (IOException e) {
                warn("Nem sikerült átírni: " + setting.file() + " (" + e.getMessage() + ")");
            }
        }
    }

    void checkUpdates() {
        try {
            boolean changed = false;
            changed |= updateGeyserProject("geyser", "geyser", "Geyser-Velocity.jar");
            changed |= updateGeyserProject("floodgate", "floodgate", "floodgate-velocity.jar");
            changed |= updateSonar();
            changed |= updateSelf();
            if (changed && !restartNoted) {
                restartNoted = true;
                info("Frissítés letöltve. A proxy következő újraindításakor lesz érvényes.");
            }
        } catch (Throwable t) {
            warn("Frissítés ellenőrzése nem sikerült: " + t);
        }
    }

    boolean updateGeyserProject(String project, String prefix, String defaultName) throws Exception {
        String json = fetchText(String.format(GEYSER_API, project));
        if (json == null) {
            return false;
        }
        Matcher sha = SHA.matcher(json);
        if (!sha.find()) {
            return false;
        }
        String wanted = sha.group(1).toLowerCase(Locale.ROOT);
        Path jar = findJar(prefix);
        if (jar == null) {
            jar = plugins.resolve(defaultName);
        } else if (wanted.equals(sha256(jar))) {
            return false;
        }
        Path pending = staging.resolve(project + ".jar");
        if (staged.containsKey(pending) && wanted.equals(sha256(pending))) {
            return false;
        }
        Path temp = staging.resolve(project + ".jar.resz");
        if (!download(String.format(GEYSER_API, project) + "/downloads/velocity", temp)) {
            return false;
        }
        if (!wanted.equals(sha256(temp))) {
            Files.deleteIfExists(temp);
            warn(project + ": a letöltött fájl hibás, kihagyva.");
            return false;
        }
        Files.move(temp, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        staged.put(pending, jar);
        Matcher build = BUILD.matcher(json);
        info(project + " új verzió letöltve" + (build.find() ? " (build " + build.group(1) + ")" : "") + ".");
        return true;
    }

    /** A szerverlista ikonja (server-icon.png) a GitHubról. Kézzel lecserélt ikont nem ír felül. */
    void installIcon() {
        try {
            Path icon = root.resolve("server-icon.png");
            Path mark = dataDir.resolve("ikon.sha256");
            String mine = Files.exists(mark) ? Files.readString(mark, StandardCharsets.UTF_8).strip() : "";
            if (Files.exists(icon) && !sha256(icon).equals(mine)) {
                return;
            }
            Path temp = dataDir.resolve("server-icon.png.resz");
            if (!download(REPO + "server-icon.png", temp) || Files.size(temp) < 100) {
                Files.deleteIfExists(temp);
                return;
            }
            String got = sha256(temp);
            if (Files.exists(icon) && got.equals(sha256(icon))) {
                Files.deleteIfExists(temp);
                return;
            }
            Files.move(temp, icon, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(mark, got, StandardCharsets.UTF_8);
            info("Szerverlista ikon frissítve (a proxy újraindítása után látszik).");
        } catch (Exception e) {
            warn("Az ikon frissítése nem sikerült: " + e.getMessage());
        }
    }

    /** Sonar botvédelem: a Velocity verziójához illő Sonar Velocity build a Modrinthról. */
    boolean updateSonar() throws Exception {
        int[] velocity = velocityVersion();
        boolean modern = velocity != null && (velocity[0] > 3 || velocity[0] == 3 && velocity[1] >= 6);
        String json = fetchText(modern ? MODRINTH + "?loaders=%5B%22velocity%22%5D" : MODRINTH + "/" + SONAR_OLD);
        if (json == null) {
            return false;
        }
        String chosen = null;
        for (String item : topLevelObjects(json)) {
            Matcher type = MR_TYPE.matcher(item);
            if (type.find() && type.group(1).equals("release")) {
                chosen = item;
                break;
            }
        }
        if (chosen == null) {
            return false;
        }
        Matcher version = MR_VERSION.matcher(chosen);
        Matcher sha1 = MR_SHA1.matcher(chosen);
        Matcher url = MR_URL.matcher(chosen);
        if (!version.find() || !sha1.find() || !url.find()) {
            return false;
        }
        String wanted = sha1.group(1);
        Path jar = findJar("sonar");
        if (jar == null) {
            jar = plugins.resolve("Sonar-Velocity.jar");
        } else if (wanted.equals(sha1(jar))) {
            return false;
        }
        Path pending = staging.resolve("sonar.jar");
        if (staged.containsKey(pending) && wanted.equals(sha1(pending))) {
            return false;
        }
        Path temp = staging.resolve("sonar.jar.resz");
        if (!download(url.group(1), temp)) {
            return false;
        }
        if (!wanted.equals(sha1(temp))) {
            Files.deleteIfExists(temp);
            warn("Sonar: a letöltött fájl hibás, kihagyva.");
            return false;
        }
        Files.move(temp, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        staged.put(pending, jar);
        info("Sonar " + version.group(1) + " letöltve (Velocity " + (velocity == null ? "ismeretlen" : velocity[0] + "." + velocity[1]) + ").");
        return true;
    }

    static int[] velocityVersion() {
        for (String name : new String[] {"com.velocitypowered.proxy.Velocity", "com.velocitypowered.api.proxy.ProxyServer"}) {
            try {
                Package pkg = Class.forName(name).getPackage();
                String text = pkg == null ? null : pkg.getImplementationVersion();
                int[] parsed = parseVersion(text);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static int[] parseVersion(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(text);
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    /** Egy JSON tömb (vagy egyetlen objektum) legfelső szintű objektumai, szövegként. */
    static List<String> topLevelObjects(String json) {
        List<String> list = new ArrayList<>();
        String text = json.strip();
        int base = text.startsWith("[") ? 1 : 0;
        int depth = 0;
        int start = -1;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                if (c == '{' && depth == base) {
                    start = i;
                }
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (c == '}' && depth == base && start >= 0) {
                    list.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    static String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    boolean updateSelf() throws Exception {
        String remote = fetchText(REPO + "version.txt");
        if (remote == null) {
            return false;
        }
        remote = remote.trim();
        if (remote.isEmpty() || remote.equals(VERSION)) {
            return false;
        }
        Path self = selfJar();
        if (self == null) {
            return false;
        }
        Path pending = staging.resolve("IceMineProxy.jar");
        if (staged.containsKey(pending)) {
            return false;
        }
        Path temp = staging.resolve("IceMineProxy.jar.resz");
        if (!download(REPO + "IceMineProxy.jar", temp) || Files.size(temp) < 1000 || sha256(temp).equals(sha256(self))) {
            Files.deleteIfExists(temp);
            return false;
        }
        Files.move(temp, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        staged.put(pending, self);
        info("IceMineProxy " + remote + " letöltve.");
        return true;
    }

    void installStaged() {
        staged.forEach((from, to) -> {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(from);
                } catch (IOException ignored) {
                }
            }
        });
        staged.clear();
    }

    Path findJar(String prefix) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(plugins, "*.jar")) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith(prefix)) {
                    return file;
                }
            }
        }
        return null;
    }

    Path selfJar() {
        try {
            Path path = Paths.get(IceMineProxy.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    String fetchText(String url) {
        try {
            HttpResponse<String> response = http.send(request(url), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    boolean download(String url, Path target) {
        try {
            HttpResponse<InputStream> response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    return false;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            warn("Letöltés nem sikerült: " + url + " (" + e.getMessage() + ")");
            return false;
        }
    }

    HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "IceMineProxy/" + VERSION)
                .header("Cache-Control", "no-cache")
                .build();
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static Object slf4j() {
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
            return factory.getMethod("getLogger", String.class).invoke(null, "IceMineProxy");
        } catch (Throwable t) {
            return null;
        }
    }

    void info(String message) {
        emit("info", message, System.out);
    }

    void warn(String message) {
        emit("warn", message, System.err);
    }

    private void emit(String level, String message, PrintStream fallback) {
        if (log != null) {
            try {
                Method method = log.getClass().getMethod(level, String.class);
                method.invoke(log, message);
                return;
            } catch (Throwable ignored) {
            }
        }
        fallback.println("[IceMineProxy] " + message);
    }

    record Setting(String file, String key, String value) {
        static List<Setting> parse(String text) {
            List<Setting> list = new ArrayList<>();
            for (String raw : text.split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int bar = line.indexOf('|');
                int eq = line.indexOf('=', bar + 1);
                if (bar < 0 || eq < 0) {
                    continue;
                }
                list.add(new Setting(line.substring(0, bar).strip(), line.substring(bar + 1, eq).strip(), line.substring(eq + 1).strip()));
            }
            return list;
        }
    }
}
