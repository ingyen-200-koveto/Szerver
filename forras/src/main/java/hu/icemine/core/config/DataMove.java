package hu.icemine.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class DataMove {
    public static final String OLD_CORE = "IceMineCore";
    public static final String OLD_KINGDOMS = "IceMineKingdoms";

    private DataMove() {
    }

    public static List<String> run(File file1, String s, Logger logger) {
        List<String> arraylist = new ArrayList<>();
        Path path = file1.toPath();
        Path path1 = path.resolve(s);

        try {
            Path path2 = path.resolve("IceMineCore");
            if (Files.isDirectory(path2)) {
                if (!Files.exists(path1)) {
                    Files.move(path2, path1);
                    arraylist.add("IceMineCore -> " + s);
                } else {
                    merge(path2, path1);
                    arraylist.add("IceMineCore -> " + s + " (hiányzó fájlok)");
                }
            }

            Path path3 = path.resolve("IceMineKingdoms");
            if (Files.isDirectory(path3)) {
                Files.createDirectories(path1);
                Path path4 = path1.resolve("kingdoms");
                Path path5 = path3.resolve("config.yml");
                Path path6 = path1.resolve("kingdoms.yml");
                if (Files.isRegularFile(path5) && !Files.exists(path6)) {
                    Files.move(path5, path6);
                }

                if (!Files.exists(path4)) {
                    Files.move(path3, path4);
                } else {
                    merge(path3, path4);
                }

                arraylist.add("IceMineKingdoms -> " + s + "/kingdoms");
            }
        } catch (IOException ioexception) {
            logger.severe("Nem sikerült átköltöztetni a régi adatokat: " + ioexception.getMessage());
        }

        for (String s1 : arraylist) {
            logger.info("Adatok átköltöztetve: " + s1);
        }

        return arraylist;
    }

    private static void merge(Path path, Path path1) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path path2 : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(path2)) {
                    Path path3 = path1.resolve(path.relativize(path2).toString());
                    if (!Files.exists(path3)) {
                        Files.createDirectories(path3.getParent());
                        Files.copy(path2, path3, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
        }

        Path path4 = path.resolveSibling(path.getFileName() + "_regi");
        if (!Files.exists(path4)) {
            Files.move(path, path4);
        }
    }
}
