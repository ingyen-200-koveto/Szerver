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
 * A kivett játékmódok (KitPvP, SkyPvP) maradékainak eltakarítása: a világaik (a mód világa és a saját lobbyja) és a
 * configjuk. Csak akkor töröl világot, ha az nincs betöltve. Egyszer fut le, utána egy jelölő fájl jelzi, hogy kész.
 */
public final class OldModes {

    static final String[] MODES = {"kitpvp", "skypvp"};
    private static final String MARKER = ".regi-modok-torolve";

    private OldModes() {
    }

    public static void run(File dataFolder, File worldContainer, Logger logger) {
        File marker = new File(dataFolder, MARKER);
        if (marker.exists()) {
            return;
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (String mode : MODES) {
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
            for (String mode : MODES) {
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
        for (String mode : MODES) {
            File config = new File(dataFolder, mode + ".yml");
            if (config.isFile() && !config.delete()) {
                logger.warning("Nem sikerült törölni: " + config.getName());
            }
            File inventories = new File(new File(dataFolder, "inventories"), mode);
            if (inventories.isDirectory()) {
                delete(inventories.toPath(), logger);
            }
        }
        try {
            if (!marker.createNewFile()) {
                logger.warning("Nem sikerült létrehozni: " + MARKER);
            }
        } catch (IOException exception) {
            logger.warning("Nem sikerült létrehozni: " + MARKER + " (" + exception.getMessage() + ")");
        }
        logger.info("A KitPvP és a SkyPvP kikerült, a régi világaikat (" + removed + " db) és a configjukat töröltem.");
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
