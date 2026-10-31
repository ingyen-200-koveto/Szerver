package hu.icemine.core.modelobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.build.Layout;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModeLobbyBuildTest {

    private static Map<String, String> build(ModeLobbyBuild[] out) {
        Map<String, String> blocks = new HashMap<>();
        ModeLobbyBuild build = new ModeLobbyBuild((x, y, z, d) -> blocks.put(x + " " + y + " " + z, d), 0, 100, 0, "red", "KITPVP", 3);
        build.build();
        out[0] = build;
        return blocks;
    }

    @Test
    void portalIsWalkableAndFramed() {
        ModeLobbyBuild[] out = new ModeLobbyBuild[1];
        Map<String, String> blocks = build(out);
        Layout.Area portal = out[0].layout().areas.get("portal");
        assertNotNull(portal);
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 5; y++) {
                assertEquals("nether_portal[axis=x]", blocks.get(x + " " + (100 + y) + " " + ModeLobbyBuild.PORTAL_Z));
            }
        }
        assertEquals("obsidian", blocks.get("-3 101 " + ModeLobbyBuild.PORTAL_Z));
        assertEquals("obsidian", blocks.get("0 100 " + ModeLobbyBuild.PORTAL_Z));
    }

    @Test
    void spawnHasFloorAndAir() {
        ModeLobbyBuild[] out = new ModeLobbyBuild[1];
        Map<String, String> blocks = build(out);
        Layout.Point spawn = out[0].layout().points.get("spawn");
        int x = (int) Math.floor(spawn.x);
        int z = (int) Math.floor(spawn.z);
        assertNotNull(blocks.get(x + " 100 " + z));
        assertNull(blocks.get(x + " 101 " + z));
        assertNull(blocks.get(x + " 102 " + z));
    }

    @Test
    void handlerIdsMapBack() {
        assertEquals("kitpvp", ModeLobbies.modeOf("lobby_kitpvp"));
        assertNull(ModeLobbies.modeOf("kitpvp"));
        assertNull(ModeLobbies.modeOf(null));
        assertTrue(ModeLobbies.PREFIX.endsWith("_"));
    }
}
