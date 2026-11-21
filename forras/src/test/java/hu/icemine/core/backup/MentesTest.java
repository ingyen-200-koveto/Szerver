package hu.icemine.core.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MentesTest {

    @TempDir
    Path dir;

    @Test
    void zipsWorldsAndPluginDataButNotJars() throws Exception {
        Path world = Files.createDirectories(dir.resolve("world/region"));
        Files.writeString(world.resolve("r.0.0.mca"), "chunk");
        Files.writeString(dir.resolve("world/session.lock"), "x");
        Path plugins = Files.createDirectories(dir.resolve("plugins/IceMine/mentesek"));
        Files.writeString(dir.resolve("plugins/IceMine/data.db"), "db");
        Files.writeString(dir.resolve("plugins/IceMine.jar"), "jar");
        Files.writeString(plugins.resolve("mentes_regi.zip"), "old");
        Files.createDirectories(dir.resolve("plugins/LibertyBans/internal/libraries"));
        Files.writeString(dir.resolve("plugins/LibertyBans/internal/libraries/x.jar"), "lib");
        Files.writeString(dir.resolve("server.properties"), "a=b");
        Path zip = Mentes.zip(dir.toFile(), List.of(dir.resolve("world").toFile()), dir.resolve("plugins").toFile(), plugins);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertTrue(z.getEntry("world/region/r.0.0.mca") != null);
            assertTrue(z.getEntry("plugins/IceMine/data.db") != null);
            assertTrue(z.getEntry("server.properties") != null);
            assertTrue(z.getEntry("world/session.lock") == null);
            assertTrue(z.getEntry("plugins/IceMine.jar") == null);
            assertTrue(z.getEntry("plugins/IceMine/mentesek/mentes_regi.zip") == null);
            assertTrue(z.getEntry("plugins/LibertyBans/internal/libraries/x.jar") == null);
        }
    }

    @Test
    void keepsNewest() throws Exception {
        for (String n : new String[] {"mentes_2026-01-01_04-00.zip", "mentes_2026-01-02_04-00.zip", "mentes_2026-01-03_04-00.zip", "mas.txt"}) {
            Files.writeString(dir.resolve(n), n);
        }
        Mentes.torolRegiek(dir, 2);
        assertFalse(Files.exists(dir.resolve("mentes_2026-01-01_04-00.zip")));
        assertTrue(Files.exists(dir.resolve("mentes_2026-01-03_04-00.zip")));
        assertTrue(Files.exists(dir.resolve("mas.txt")));
        assertEquals("04:30", Mentes.ido("4:30").toString());
        assertEquals("04:00", Mentes.ido("rossz").toString());
        assertTrue(new File("x").getName().equals("x"));
    }
}
