package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A kivett játékmódok (KitPvP, SkyPvP, FullPvP) maradékainak eltakarítása: a világaik (a mód világa és a saját lobbyja),
 * a configjuk és a mentett tárgyaik. Csak akkor töröl világot, ha az nincs betöltve. Módonként egyszer fut le, a jelölő
 * fájlba beírjuk, melyik mód van már kész.
 */
public final class OldModes {

    static final String[] MODES = {"kitpvp", "skypvp", "fullpvp"};
    private static final String MARKER = ".regi-modok-torolve";

    private OldModes() {
    }

    public static void run(File dataFolder, File worldContainer, Logger logger) {
        File marker = new File(dataFolder, MARKER);
        Set<String> done = new LinkedHashSet<>();
        if (marker.isFile()) {
            try {
                for (String line : Files.readAllLines(marker.toPath())) {
                    if (!line.isBlank()) {
                        done.add(line.trim());
                    }
                }
            } catch (IOException ignored) {
                // ha nem olvasható, a takarítás legfeljebb újra lefut, az nem árt
            }
            if (done.isEmpty()) {
                // a 3.11-es (üres) jelölő: a KitPvP és a SkyPvP már el van takarítva
                done.add("kitpvp");
                done.add("skypvp");
            }
        }
        Set<String> todo = new LinkedHashSet<>();
        for (String mode : MODES) {
            if (!done.contains(mode)) {
                todo.add(mode);
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (String mode : todo) {
            File config = new File(dataFolder, mode + ".yml");
            String world = "ice_" + mode;
            if (config.isFile()) {
                world = YamlConfiguration.loadConfiguration(config).getString("world", world);
            }
            worlds.add(world);
            worlds.add("lobby_" + mode);
        }
        File lobbies = new File(dataFolder, "modelobbies.yml");
        if (lobbies.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(lobbies);
            for (String mode : todo) {
                String world = yaml.getString("lobbies." + mode + ".world");
                if (world != null && !world.isBlank()) {
                    worlds.add(world);
                }
            }
        }
        int removed = 0;
        for (String name : worlds) {
            if (!safeName(name) || Bukkit.getWorld(name) != null) {
                continue;
            }
            File folder = new File(worldContainer, name);
            if (folder.isDirectory() && new File(folder, "level.dat").isFile()) {
                if (delete(folder.toPath(), logger)) {
                    removed++;
                }
            }
        }
        for (String mode : todo) {
            File config = new File(dataFolder, mode + ".yml");
            if (config.isFile() && !config.delete()) {
                logger.warning("Nem sikerült törölni: " + config.getName());
            }
            for (String folder : new String[]{mode, mode + "_vault"}) {
                File inventories = new File(new File(dataFolder, "inventories"), folder);
                if (inventories.isDirectory()) {
                    delete(inventories.toPath(), logger);
                }
            }
            done.add(mode);
        }
        try {
            Files.write(marker.toPath(), String.join("\n", done).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            logger.warning("Nem sikerült írni: " + MARKER + " (" + exception.getMessage() + ")");
        }
        logger.info("Kivett játékmódok (" + String.join(", ", todo) + "): a régi világaikat (" + removed + " db) és a configjukat töröltem.");
    }

    /** Csak egyszerű világnév (nincs benne útvonal), és nem a fő világ. */
    static boolean safeName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }
        return !name.equals("world") && !name.equals("world_nether") && !name.equals("world_the_end");
    }

    private static boolean delete(Path root, Logger logger) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException exception) {
            logger.warning("Nem sikerült törölni: " + root.getFileName() + " (" + exception.getMessage() + ")");
            return false;
        }
    }
}
