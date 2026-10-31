package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class FirstSetupTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    void copiesOnceAndOverwritesServerConfig() throws IOException {
        Path root = Files.createTempDirectory("setup");
        write(root.resolve("kezdo/server.properties"), "online-mode=false");
        write(root.resolve("kezdo/config/paper-global.yml"), "velocity");
        write(root.resolve("kezdo/plugins/AuthMe/config.yml"), "uj");
        write(root.resolve("kezdo/combofly_map/level.dat"), "map");
        write(root.resolve("server.properties"), "online-mode=true");
        write(root.resolve("plugins/AuthMe/config.yml"), "regi");
        assertTrue(FirstSetup.run(root.toFile(), LOG));
        assertEquals("online-mode=false", read(root.resolve("server.properties")));
        assertEquals("velocity", read(root.resolve("config/paper-global.yml")));
        assertEquals("regi", read(root.resolve("plugins/AuthMe/config.yml")));
        assertEquals("map", read(root.resolve("combofly_map/level.dat")));
        write(root.resolve("server.properties"), "valtozott");
        assertFalse(FirstSetup.run(root.toFile(), LOG));
        assertEquals("valtozott", read(root.resolve("server.properties")));
    }

    @Test
    void keepsRealWorlds() throws IOException {
        Path root = Files.createTempDirectory("setup");
        write(root.resolve("kezdo/server.properties"), "x");
        write(root.resolve("world/level.dat"), "real");
        Files.createDirectories(root.resolve("world_nether/region"));
        FirstSetup.run(root.toFile(), LOG);
        assertTrue(Files.exists(root.resolve("world/level.dat")));
        assertFalse(Files.exists(root.resolve("world_nether")));
    }

    @Test
    void nothingWithoutFolder() throws IOException {
        Path root = Files.createTempDirectory("setup");
        assertFalse(FirstSetup.run(root.toFile(), LOG));
        assertFalse(Files.exists(root.resolve("plugins/IceMineCore/.kezdo-kesz")));
    }
}
