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

    public Updater(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("auto-update.enabled", true);
        String base = config.getString("auto-update.repo-raw", "https://raw.githubusercontent.com/ingyen-200-koveto/Szerver/main");
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
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
        if (!restartWhenEmpty || downloaded == null) {
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

    public String downloaded() {
        return downloaded;
    }
}
