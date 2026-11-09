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

    public static final String VERSION = "1.0.0";
    static final String REPO = "https://raw.githubusercontent.com/ingyen-200-koveto/Szerver/main/proxy/";
    static final String GEYSER_API = "https://download.geysermc.org/v2/projects/%s/versions/latest/builds/latest";
    static final Pattern SHA = Pattern.compile("\"velocity\"\\s*:\\s*\\{[^}]*\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"");
    static final Pattern BUILD = Pattern.compile("\"build\"\\s*:\\s*(\\d+)");

    private final Path plugins = Paths.get("plugins").toAbsolutePath();
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
        Runtime.getRuntime().addShutdownHook(new Thread(this::installStaged, "IceMineProxy-frissites"));
        timer.scheduleWithFixedDelay(this::checkUpdates, 20, 30 * 60, TimeUnit.SECONDS);
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
            if (!file.startsWith(plugins) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                String original = Files.readString(file, StandardCharsets.UTF_8);
                String changed = YamlPatch.set(original, setting.key(), setting.value());
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
