package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class FirstSetup {

    public static final String FOLDER = "kezdo";
    private static final String MARKER = "plugins/IceMine/.kezdo-kesz";
    private static final String[] RESTART = {"server.properties", "config/paper-global.yml"};
    private static final String[] FRESH_WORLDS = {"world", "world_nether", "world_the_end"};

    private FirstSetup() {
    }

    public static boolean run(File root, Logger logger) {
        Path base = root.toPath().toAbsolutePath().normalize();
        Path source = base.resolve(FOLDER);
        Path marker = base.resolve(MARKER);
        if (!Files.isDirectory(source) || Files.exists(marker)) {
            return false;
        }
        List<String> copied = new ArrayList<>();
        try {
            for (String name : RESTART) {
                Path from = source.resolve(name);
                if (Files.isRegularFile(from)) {
                    Path to = base.resolve(name);
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                    copied.add(name);
                }
            }
            try (Stream<Path> files = Files.walk(source)) {
                for (Path from : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(from)) {
                        continue;
                    }
                    String relative = source.relativize(from).toString().replace(File.separatorChar, '/');
                    if (relative.equals(RESTART[0]) || relative.equals(RESTART[1])) {
                        continue;
                    }
                    Path to = base.resolve(relative).normalize();
                    if (!to.startsWith(base) || Files.exists(to)) {
                        continue;
                    }
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to);
                    copied.add(relative);
                }
            }
            boolean freshWorlds = false;
            for (String world : FRESH_WORLDS) {
                Path folder = base.resolve(world);
                if (Files.isDirectory(folder) && !Files.exists(folder.resolve("level.dat"))) {
                    delete(folder);
                    freshWorlds = true;
                }
            }
            Files.createDirectories(marker.getParent());
            Files.write(marker, "kész\n".getBytes(StandardCharsets.UTF_8));
            logger.warning("============================================================");
            logger.warning(" IceMine: az első beállítás kész (" + copied.size() + " fájl a " + FOLDER + " mappából).");
            logger.warning(" A szerver most leáll. Indítsd el újra, és onnantól minden a helyén lesz.");
            logger.warning("============================================================");
            return !copied.isEmpty() || freshWorlds;
        } catch (IOException exception) {
            logger.severe("IceMine: nem sikerült az első beállítás: " + exception);
            return false;
        }
    }

    private static void delete(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            List<Path> all = new ArrayList<>();
            files.forEach(all::add);
            for (int i = all.size() - 1; i >= 0; i--) {
                Files.deleteIfExists(all.get(i));
            }
        }
    }
}
