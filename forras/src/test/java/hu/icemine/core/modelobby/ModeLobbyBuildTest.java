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
        assertEquals("fyland", ModeLobbies.modeOf("lobby_fyland"));
        assertNull(ModeLobbies.modeOf("fyland"));
        assertNull(ModeLobbies.modeOf(null));
        assertTrue(ModeLobbies.PREFIX.endsWith("_"));
    }

    @org.junit.jupiter.api.Test
    void everyThemeKeepsTheSameImportantSpots() {
        String[] themes = {"arena", "fyland", "combofly", "gens", "kingdoms", "duels", "bedwars", "snow"};
        java.util.Map<String, hu.icemine.core.build.Layout.Point> reference = null;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String theme : themes) {
            java.util.Map<String, String> blocks = new java.util.HashMap<>();
            ModeLobbyBuild build = new ModeLobbyBuild((x, y, z, data) -> blocks.put(x + "," + y + "," + z, data), 0, 0, 0, "red", "TEST", 0, theme);
            build.build();
            if (reference == null) {
                reference = build.layout().points;
            }
            // a közös pontok mindenhol ugyanazok, a téma saját pontjai (pl. a BedWars szobrai, npc:...) ezen felül vannak
            java.util.Set<String> common = new java.util.HashSet<>(build.layout().points.keySet());
            common.removeIf(key -> key.startsWith("npc:"));
            java.util.Set<String> expected = new java.util.HashSet<>(reference.keySet());
            expected.removeIf(key -> key.startsWith("npc:"));
            org.junit.jupiter.api.Assertions.assertEquals(expected, common, theme);
            org.junit.jupiter.api.Assertions.assertEquals("nether_portal[axis=x]", blocks.get("0,3," + ModeLobbyBuild.PORTAL_Z), theme + ": van portál");
            String ground = blocks.get("0,0," + ModeLobbyBuild.SPAWN_Z);
            org.junit.jupiter.api.Assertions.assertNotNull(ground, theme + ": van talaj a spawn alatt");
            org.junit.jupiter.api.Assertions.assertFalse(ground.startsWith("air"), theme);
            String above = blocks.get("0,1," + ModeLobbyBuild.SPAWN_Z);
            org.junit.jupiter.api.Assertions.assertTrue(above == null || above.equals("air"), theme + ": a spawn fölött szabad hely");
            seen.add(blocks.get("5,0,20"));
        }
        org.junit.jupiter.api.Assertions.assertTrue(seen.size() >= 5, "a témák tényleg különböző anyagból vannak");
    }

    @org.junit.jupiter.api.Test
    void bedwarsLobbyHasFourStatuesThatTheConfigPointsTo() throws Exception {
        java.util.Map<String, String> blocks = new java.util.HashMap<>();
        ModeLobbyBuild build = new ModeLobbyBuild((x, y, z, data) -> blocks.put(x + "," + y + "," + z, data), 0, 0, 0, "orange", "BEDWARS", 3, "bedwars");
        build.build();
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("modelobbies.yml")) {
            config.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        org.bukkit.configuration.ConfigurationSection npcs = config.getConfigurationSection("lobbies.bedwars.npcs");
        org.junit.jupiter.api.Assertions.assertNotNull(npcs);
        java.util.Set<String> modes = new java.util.HashSet<>();
        for (String key : npcs.getKeys(false)) {
            hu.icemine.core.build.Layout.Point point = build.layout().points.get("npc:" + npcs.getString(key + ".spot"));
            org.junit.jupiter.api.Assertions.assertNotNull(point, key + ": van ilyen hely a lobbyban");
            int x = (int) Math.floor(point.x);
            int z = (int) Math.floor(point.z);
            org.junit.jupiter.api.Assertions.assertTrue(blocks.get(x + ",0," + z).endsWith("_concrete"), key + ": a szobor a dobogón áll");
            String above = blocks.get(x + ",1," + z);
            org.junit.jupiter.api.Assertions.assertTrue(above == null || above.equals("air"), key + ": a szobor helye szabad");
            String action = npcs.getString(key + ".action");
            org.junit.jupiter.api.Assertions.assertTrue(action.startsWith("bedwars:"), key);
            modes.add(action.substring("bedwars:".length()));
            // a spawn felé néz
            double yaw = Math.toRadians(point.yaw);
            double lookX = -Math.sin(yaw);
            double lookZ = Math.cos(yaw);
            double toSpawnX = 0.5 - point.x;
            double toSpawnZ = ModeLobbyBuild.SPAWN_Z + 0.5 - point.z;
            org.junit.jupiter.api.Assertions.assertTrue(lookX * toSpawnX + lookZ * toSpawnZ > 0, key + ": a spawn felé néz");
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("solo", "duo", "3v3v3v3", "4v4v4v4"), modes);
        org.bukkit.configuration.file.YamlConfiguration bedwars = new org.bukkit.configuration.file.YamlConfiguration();
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("bedwars.yml")) {
            bedwars.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        org.junit.jupiter.api.Assertions.assertEquals(modes, bedwars.getConfigurationSection("modes").getKeys(false));
    }
}
