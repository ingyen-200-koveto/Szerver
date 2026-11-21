package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptimalizalasTest {

    @TempDir
    Path dir;

    @Test
    void onlyExistingKeysAreChanged() throws Exception {
        File f = dir.resolve("paper.yml").toFile();
        Files.writeString(f.toPath(), "misc:\n  redstone-implementation: VANILLA\ncollisions:\n  max-entity-collisions: 8\n");
        int db = Optimalizalas.patch(f, Optimalizalas.PAPER, Logger.getAnonymousLogger());
        assertEquals(2, db);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        assertEquals("ALTERNATE_CURRENT", y.getString("misc.redstone-implementation"));
        assertEquals(2, y.getInt("collisions.max-entity-collisions"));
        assertFalse(y.contains("hopper.disable-move-event"));
        assertEquals(0, Optimalizalas.patch(f, Optimalizalas.PAPER, Logger.getAnonymousLogger()));
    }
}
