package hu.icemine.core.backup;

import hu.icemine.core.IceMineCore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Napi mentés: a világok és a pluginok adatai (jarok nélkül) egy zip fájlba, a plugins/IceMine/mentesek mappába,
 * és feltöltés a weboldal szerverére (VPS), hogy a szolgáltatónál történt baj esetén is meglegyen.
 */
public final class Mentes implements CommandExecutor, TabCompleter {

    static final ZoneId ZONA = ZoneId.of("Europe/Budapest");
    static final DateTimeFormatter NEV = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    static final List<String> KIHAGY = List.of("session.lock", ".jar", "/mentesek/", "/internal/libraries/", "/.paper-remapped/",
            "/spark/", "/bstats/", "/cache/", "/logs/", "/libraries/", ".broken", ".tmp", "/frissites/");

    private final IceMineCore core;
    private final AtomicBoolean fut = new AtomicBoolean();
    private LocalDate utolsoNap;
    private String utolsoEredmeny = "Még nem volt mentés, mióta a szerver fut.";

    public Mentes(IceMineCore core) {
        this.core = core;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(core, this::idozito, 20L * 60, 20L * 30);
    }

    private void idozito() {
        if (!core.getConfig().getBoolean("mentes.be", true)) {
            return;
        }
        ZonedDateTime most = ZonedDateTime.now(ZONA);
        LocalTime cel = ido(core.getConfig().getString("mentes.idopont", "04:00"));
        if (most.toLocalDate().equals(utolsoNap) || most.toLocalTime().isBefore(cel) || most.toLocalTime().isAfter(cel.plusMinutes(30))) {
            return;
        }
        utolsoNap = most.toLocalDate();
        indit(null);
    }

    static LocalTime ido(String text) {
        try {
            return LocalTime.parse(text.length() == 4 ? "0" + text : text);
        } catch (RuntimeException error) {
            return LocalTime.of(4, 0);
        }
    }

    /** Elindítja a mentést. A küldőnek (ha van) a végén szól. */
    public boolean indit(CommandSender sender) {
        if (!fut.compareAndSet(false, true)) {
            return false;
        }
        Map<World, Boolean> autosave = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            try {
                world.save();
            } catch (RuntimeException ignored) {
            }
            autosave.put(world, world.isAutoSave());
            world.setAutoSave(false);
        }
        File root = Bukkit.getWorldContainer().getAbsoluteFile();
        List<File> vilagok = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            vilagok.add(world.getWorldFolder().getAbsoluteFile());
        }
        File pluginsDir = core.getDataFolder().getParentFile().getAbsoluteFile();
        File mappa = new File(core.getDataFolder(), "mentesek");
        int megtart = Math.max(1, core.getConfig().getInt("mentes.helyi-megtart", 3));
        boolean feltolt = core.getConfig().getBoolean("mentes.feltoltes", true);
        long kezdet = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            Path kesz = null;
            String hiba = null;
            try {
                kesz = zip(root, vilagok, pluginsDir, mappa.toPath());
            } catch (Throwable error) {
                hiba = error.toString();
            }
            Path fajl = kesz;
            String zipHiba = hiba;
            Bukkit.getScheduler().runTask(core, () -> autosave.forEach((w, v) -> w.setAutoSave(v)));
            if (fajl == null) {
                vege(sender, "A mentés nem sikerült: " + zipHiba, true);
                return;
            }
            torolRegiek(mappa.toPath(), megtart);
            String meret = meret(fajl);
            long mp = (System.currentTimeMillis() - kezdet) / 1000;
            if (!feltolt || !core.iceCoins().hasKey()) {
                vege(sender, "Mentés kész: " + fajl.getFileName() + " (" + meret + ", " + mp + " mp)."
                        + (feltolt ? " A feltöltéshez az icecoin.api-key kell." : ""), false);
                return;
            }
            int status;
            try {
                status = core.iceCoins().uploadBlocking("/api/mc/mentes?nev=" + fajl.getFileName(), fajl);
            } catch (Throwable error) {
                status = -1;
                hiba = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if (status >= 200 && status < 300) {
                vege(sender, "Mentés kész és feltöltve a VPS-re: " + fajl.getFileName() + " (" + meret + ", " + mp + " mp).", false);
            } else {
                vege(sender, "A mentés elkészült (" + fajl.getFileName() + ", " + meret + "), de a VPS-re nem sikerült feltölteni ("
                        + (status > 0 ? "HTTP " + status : hiba) + ").", true);
            }
        });
        return true;
    }

    private void vege(CommandSender sender, String szoveg, boolean baj) {
        utolsoEredmeny = szoveg;
        fut.set(false);
        if (baj) {
            core.getLogger().warning(szoveg);
        } else {
            core.getLogger().info(szoveg);
        }
        if (sender != null) {
            Bukkit.getScheduler().runTask(core, () -> sender.sendMessage((baj ? "§c" : "§a") + szoveg));
        }
    }

    static boolean kihagyando(String relativ) {
        String r = "/" + relativ.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String k : KIHAGY) {
            if (k.startsWith("/") ? r.contains(k) : r.endsWith(k)) {
                return true;
            }
        }
        return false;
    }

    static Path zip(File root, List<File> vilagok, File pluginsDir, Path mappa) throws IOException {
        Files.createDirectories(mappa);
        String nev = "mentes_" + ZonedDateTime.now(ZONA).format(NEV) + ".zip";
        Path tmp = mappa.resolve(nev + ".resz");
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.BEST_SPEED);
            for (File vilag : vilagok) {
                hozzaad(zip, root.toPath(), vilag.toPath());
            }
            hozzaad(zip, root.toPath(), pluginsDir.toPath());
            for (String fajl : new String[] {"server.properties", "bukkit.yml", "spigot.yml", "purpur.yml", "commands.yml", "permissions.yml", "ops.json", "whitelist.json", "banned-players.json"}) {
                Path p = root.toPath().resolve(fajl);
                if (Files.isRegularFile(p)) {
                    egyFajl(zip, fajl, p);
                }
            }
            Path config = root.toPath().resolve("config");
            if (Files.isDirectory(config)) {
                hozzaad(zip, root.toPath(), config);
            }
        } catch (IOException error) {
            Files.deleteIfExists(tmp);
            throw error;
        }
        Path kesz = mappa.resolve(nev);
        Files.move(tmp, kesz, StandardCopyOption.REPLACE_EXISTING);
        return kesz;
    }

    private static void hozzaad(ZipOutputStream zip, Path root, Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> fajlok;
        try (Stream<Path> walk = Files.walk(dir)) {
            fajlok = walk.filter(Files::isRegularFile).toList();
        }
        for (Path p : fajlok) {
            String relativ = root.relativize(p).toString().replace('\\', '/');
            if (kihagyando(relativ)) {
                continue;
            }
            try {
                egyFajl(zip, relativ, p);
            } catch (IOException error) {
                // egy épp írt vagy zárolt fájl ne állítsa meg az egész mentést
            }
        }
    }

    private static void egyFajl(ZipOutputStream zip, String nev, Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            zip.putNextEntry(new ZipEntry(nev));
            in.transferTo(zip);
            zip.closeEntry();
        }
    }

    static void torolRegiek(Path mappa, int megtart) {
        try (Stream<Path> list = Files.list(mappa)) {
            List<Path> zipek = list.filter(p -> p.getFileName().toString().startsWith("mentes_") && p.toString().endsWith(".zip"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
            for (int i = megtart; i < zipek.size(); i++) {
                Files.deleteIfExists(zipek.get(i));
            }
        } catch (IOException ignored) {
        }
    }

    static String meret(Path p) {
        try {
            long b = Files.size(p);
            return b >= 1 << 30 ? String.format(Locale.ROOT, "%.1f GB", b / (double) (1 << 30)) : (b / (1 << 20)) + " MB";
        } catch (IOException error) {
            return "?";
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("icemine.admin.mentes")) {
            sender.sendMessage("§cEhhez nincs jogod.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("most")) {
            if (indit(sender)) {
                sender.sendMessage("§eMentés elindult. Pár perc, a végén szólok.");
            } else {
                sender.sendMessage("§cMár fut egy mentés.");
            }
            return true;
        }
        sender.sendMessage("§bMentés: §fminden nap " + core.getConfig().getString("mentes.idopont", "04:00") + "-kor. "
                + (fut.get() ? "§eMost is fut." : ""));
        sender.sendMessage("§7Utolsó: §f" + utolsoEredmeny);
        sender.sendMessage("§7Azonnali mentés: §f/mentes most");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && sender.hasPermission("icemine.admin.mentes") ? List.of("most") : List.of();
    }
}
