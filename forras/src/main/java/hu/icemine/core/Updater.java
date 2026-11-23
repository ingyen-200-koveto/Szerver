package hu.icemine.core;

import hu.icemine.core.util.Text;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Automatikus frissítés: időnként megnézi a GitHubon (a szerver repóban) a version.txt-t, és ha újabb IceMine van,
 * letölti a plugins/update mappába. A szerver a következő újraindításkor magától felrakja (Git Pull nem kell).
 * Ha be van kapcsolva, üres szervernél újra is indítja.
 */
public final class Updater {

    private final IceMineCore core;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private BukkitTask task;
    private BukkitTask restartTask;
    private boolean enabled = true;
    private String versionUrl = "";
    private String jarUrl = "";
    private long checkTicks = 20L * 60 * 10;
    private boolean restartWhenEmpty;
    private volatile String downloaded;
    private volatile boolean busy;
    private long emptySince;
    private boolean pluginsEnabled = true;
    private String listUrl = "";
    private final java.util.Set<String> pluginsDone = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Updater(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("auto-update.enabled", true);
        String base = config.getString("auto-update.repo-raw", "https://raw.githubusercontent.com/ingyen-200-koveto/Szerver/main");
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        pluginsEnabled = config.getBoolean("auto-update.plugins", true);
        String[] parts = base.replaceFirst("^https?://raw\\.githubusercontent\\.com/", "").split("/");
        listUrl = parts.length >= 3 && base.contains("raw.githubusercontent.com")
                ? "https://api.github.com/repos/" + parts[0] + "/" + parts[1] + "/contents/plugins?ref=" + parts[2] : "";
        versionUrl = base + "/version.txt";
        jarUrl = base + "/plugins/IceMine.jar";
        checkTicks = Math.max(1, config.getLong("auto-update.check-minutes", 10)) * 60L * 20L;
        restartWhenEmpty = config.getBoolean("auto-update.restart-when-empty", false);
        shutdown();
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(core, () -> check(null), 20L * 60, checkTicks);
            restartTask = Bukkit.getScheduler().runTaskTimer(core, this::restartCheck, 20L * 60, 20L * 30);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (restartTask != null) {
            restartTask.cancel();
            restartTask = null;
        }
    }

    public String current() {
        return core.getDescription().getVersion();
    }

    /** -1, ha a kisebb, 0 ha egyforma, 1 ha b nagyobb (pl. 3.14.2 vs 3.14.10). */
    static int compare(String a, String b) {
        String[] left = a.trim().split("[.\\-]");
        String[] right = b.trim().split("[.\\-]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int x = i < left.length ? number(left[i]) : 0;
            int y = i < right.length ? number(right[i]) : 0;
            if (x != y) {
                return x < y ? 1 : -1;
            }
        }
        return 0;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** /icem update: azonnali ellenőrzés. */
    public void checkNow(CommandSender sender) {
        if (busy) {
            tell(sender, "update.busy");
            return;
        }
        tell(sender, "update.checking");
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> check(sender));
    }

    private void tell(CommandSender sender, String key, String... pairs) {
        if (sender == null) {
            return;
        }
        Bukkit.getScheduler().runTask(core, () -> core.messages().send(sender, key, pairs));
    }

    /** A háttérszálon fut. */
    private void check(CommandSender sender) {
        checkIceMine(sender);
        syncPlugins(sender);
    }

    private void checkIceMine(CommandSender sender) {
        if (busy) {
            return;
        }
        busy = true;
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(versionUrl + "?t=" + System.currentTimeMillis()))
                    .timeout(Duration.ofSeconds(10)).header("Cache-Control", "no-cache").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                tell(sender, "update.failed", "%reason%", "HTTP " + response.statusCode());
                return;
            }
            String latest = response.body().trim().split("\\s+")[0];
            String have = downloaded != null ? downloaded : current();
            if (latest.isEmpty() || compare(have, latest) <= 0) {
                tell(sender, downloaded != null ? "update.ready" : "update.latest", "%version%", downloaded != null ? downloaded : current());
                return;
            }
            File folder = Bukkit.getUpdateFolderFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                tell(sender, "update.failed", "%reason%", "nem tudom létrehozni: " + folder.getPath());
                return;
            }
            File temp = new File(folder, "IceMine.jar.letoltes");
            HttpResponse<InputStream> jar = client.send(HttpRequest.newBuilder(URI.create(jarUrl + "?t=" + System.currentTimeMillis()))
                    .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (jar.statusCode() != 200) {
                tell(sender, "update.failed", "%reason%", "HTTP " + jar.statusCode());
                return;
            }
            try (InputStream in = jar.body()) {
                Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            String inside = versionInJar(temp);
            if (inside == null || compare(current(), inside) <= 0) {
                Files.deleteIfExists(temp.toPath());
                tell(sender, "update.failed", "%reason%", "a letöltött fájl hibás vagy régi (" + inside + ")");
                return;
            }
            File target = new File(folder, jarName());
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            downloaded = inside;
            String version = inside;
            core.getLogger().info("Új IceMine letöltve (" + version + "), a következő újraindításkor települ: " + target.getPath());
            Bukkit.getScheduler().runTask(core, () -> {
                if (sender != null) {
                    core.messages().send(sender, "update.downloaded", "%version%", version);
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != sender && player.hasPermission("icemine.command.update")) {
                        core.messages().send(player, "update.downloaded", "%version%", version);
                    }
                }
            });
        } catch (Exception exception) {
            tell(sender, "update.failed", "%reason%", String.valueOf(exception.getMessage()));
            if (sender == null) {
                core.getLogger().fine("Frissítés ellenőrzése nem sikerült: " + exception);
            }
        } finally {
            busy = false;
        }
    }

    /** A plugin mostani jar fájljának neve (a frissítés ugyanilyen néven kell legyen az update mappában). */
    private String jarName() {
        try {
            File file = new File(core.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return file.getName().endsWith(".jar") ? file.getName() : "IceMine.jar";
        } catch (Exception exception) {
            return "IceMine.jar";
        }
    }

    static String versionInJar(File file) {
        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                return "IceMine".equals(yaml.getString("name")) ? yaml.getString("version") : null;
            }
        } catch (Exception exception) {
            return null;
        }
    }

    /** Ha be van kapcsolva: letöltött frissítésnél, ha 2 perce senki nincs fent, újraindítjuk a szervert. */
    private void restartCheck() {
        if (!restartWhenEmpty || (downloaded == null && pluginsDone.isEmpty())) {
            emptySince = 0;
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            emptySince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (emptySince == 0) {
            emptySince = now;
            return;
        }
        if (now - emptySince >= 120_000L) {
            core.getLogger().info(Text.strip("Üres a szerver, újraindítás a frissítés (" + downloaded + ") miatt."));
            Bukkit.spigot().restart();
        }
    }

    /**
     * A többi plugin (a GitHubon a plugins mappában lévő jarok): ha valamelyik megváltozott vagy új, letölti.
     * Ami már fent van, az a plugins/update mappába kerül a mostani fájlnévvel (a szerver újraindításkor cseréli),
     * az új plugin egyből a plugins mappába. A régi jarokhoz, amik nincsenek a GitHubon, nem nyúl.
     */
    private synchronized void syncPlugins(CommandSender sender) {
        if (!pluginsEnabled || listUrl.isEmpty()) {
            return;
        }
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(listUrl))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "IceMine-Updater").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                core.getLogger().fine("Plugin lista: HTTP " + response.statusCode());
                return;
            }
            com.google.gson.JsonArray list = com.google.gson.JsonParser.parseString(response.body()).getAsJsonArray();
            File plugins = core.getDataFolder().getParentFile();
            File update = Bukkit.getUpdateFolderFile();
            java.util.Map<String, File> installed = new java.util.HashMap<>();
            File[] jars = plugins.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    String name = pluginName(jar);
                    if (name != null) {
                        installed.put(name.toLowerCase(java.util.Locale.ROOT), jar);
                    }
                }
            }
            java.util.List<String> changed = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement element : list) {
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                String file = entry.get("name").getAsString();
                if (!"file".equals(entry.get("type").getAsString()) || !file.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
                        || file.equalsIgnoreCase("IceMine.jar") || file.contains("/") || file.contains("\\") || file.startsWith(".")) {
                    continue;
                }
                String sha = entry.get("sha").getAsString();
                if (pluginsDone.contains(sha)) {
                    continue;
                }
                File sameName = new File(plugins, file);
                if (sameName.isFile() && sha.equals(gitSha(sameName)) && !new File(update, file).exists()) {
                    continue;
                }
                if (!entry.has("download_url") || entry.get("download_url").isJsonNull()) {
                    continue;
                }
                File temp = new File(plugins, "." + file + ".letoltes");
                HttpResponse<InputStream> jar = client.send(HttpRequest.newBuilder(URI.create(entry.get("download_url").getAsString()))
                        .timeout(Duration.ofSeconds(120)).header("User-Agent", "IceMine-Updater").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (jar.statusCode() != 200) {
                    continue;
                }
                try (InputStream in = jar.body()) {
                    Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                String name = pluginName(temp);
                if (!sha.equals(gitSha(temp)) || name == null) {
                    Files.deleteIfExists(temp.toPath());
                    core.getLogger().warning("Plugin frissítés: hibás letöltés, kihagyom: " + file);
                    continue;
                }
                File old = installed.get(name.toLowerCase(java.util.Locale.ROOT));
                if (old != null && sha.equals(gitSha(old))) {
                    Files.deleteIfExists(temp.toPath());
                    pluginsDone.add(sha);
                    continue;
                }
                File target;
                if (old != null) {
                    if (!update.isDirectory() && !update.mkdirs()) {
                        Files.deleteIfExists(temp.toPath());
                        continue;
                    }
                    target = new File(update, old.getName());
                } else {
                    target = sameName;
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                pluginsDone.add(sha);
                changed.add(name + (old == null ? " (új)" : ""));
                core.getLogger().info("Plugin letöltve: " + name + " -> " + target.getPath() + " (újraindításkor települ)");
            }
            if (!changed.isEmpty()) {
                String names = String.join(", ", changed);
                Bukkit.getScheduler().runTask(core, () -> {
                    if (sender != null) {
                        core.messages().send(sender, "update.plugins", "%list%", names);
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player != sender && player.hasPermission("icemine.command.update")) {
                            core.messages().send(player, "update.plugins", "%list%", names);
                        }
                    }
                });
            }
        } catch (Exception exception) {
            core.getLogger().fine("Plugin frissítés nem sikerült: " + exception);
        }
    }

    /** Ugyanaz az azonosító, amit a GitHub ad a fájlnak (git blob sha1). */
    static String gitSha(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + file.length() + "\0").getBytes(StandardCharsets.UTF_8));
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    /** A jarban lévő plugin neve (plugin.yml vagy paper-plugin.yml), vagy null. */
    static String pluginName(File file) {
        try (JarFile jar = new JarFile(file)) {
            for (String yml : new String[]{"paper-plugin.yml", "plugin.yml"}) {
                ZipEntry entry = jar.getEntry(yml);
                if (entry == null) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    String name = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)).getString("name");
                    if (name != null && !name.isBlank()) {
                        return name.trim();
                    }
                }
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    public String downloaded() {
        return downloaded;
    }
}
