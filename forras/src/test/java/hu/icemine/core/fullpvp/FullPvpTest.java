package hu.icemine.core.fullpvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FullPvpTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(FullPvpTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    private static Map<String, String> built(FullPvpBuild[] out) {
        Map<String, String> blocks = new HashMap<>();
        FullPvpBuild build = new FullPvpBuild((x, y, z, data) -> blocks.put(x + "," + y + "," + z, data), 0, 0, 0);
        build.build();
        out[0] = build;
        return blocks;
    }

    @Test
    void spawnIsInsideTheSafeCastleAndStandsOnFloor() {
        FullPvpBuild[] build = new FullPvpBuild[1];
        Map<String, String> blocks = built(build);
        var spawn = build[0].layout().points.get("spawn");
        var safe = build[0].layout().points.get("safe");
        assertNotNull(spawn);
        assertNotNull(safe);
        double dx = spawn.x - (safe.x + 0.5);
        double dz = spawn.z - (safe.z + 0.5);
        assertTrue(Math.sqrt(dx * dx + dz * dz) < safe.yaw, "a spawn a biztonságos zónában van");
        assertTrue(spawn.y >= safe.y, "a spawn a zóna alja fölött van");
        String floor = blocks.get((int) Math.floor(spawn.x) + "," + (int) (spawn.y - 1) + "," + (int) Math.floor(spawn.z));
        assertNotNull(floor);
        assertFalse(floor.startsWith("air"));
        String feet = blocks.get((int) Math.floor(spawn.x) + "," + (int) spawn.y + "," + (int) Math.floor(spawn.z));
        assertTrue(feet == null || feet.startsWith("air"), "a spawn helyén nincs blokk");
    }

    @Test
    void stationsAreRealBlocksInsideTheSafeZone() {
        FullPvpBuild[] build = new FullPvpBuild[1];
        Map<String, String> blocks = built(build);
        var safe = build[0].layout().points.get("safe");
        Map<String, String> expected = Map.of("shop", "emerald_block", "kits", "chest", "vault", "ender_chest");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            var point = build[0].layout().points.get("station:" + entry.getKey());
            assertNotNull(point, entry.getKey());
            String block = blocks.get((int) point.x + "," + (int) point.y + "," + (int) point.z);
            assertTrue(block != null && block.startsWith(entry.getValue()), entry.getKey() + ": " + block);
            assertTrue(Math.sqrt(point.x * point.x + point.z * point.z) < safe.yaw, entry.getKey() + " a zónában");
            assertNotNull(build[0].layout().points.get("holo:" + entry.getKey()));
        }
    }

    @Test
    void rampsReachTheGroundOnEverySide() {
        Map<String, String> blocks = built(new FullPvpBuild[1]);
        int along = FullPvpBuild.SPAWN_RADIUS + FullPvpBuild.FLOOR;
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] side : sides) {
            int x = side[0] * along;
            int z = side[1] * along;
            int ground = FullPvpBuild.surface(x, z);
            String block = blocks.get(x + "," + ground + "," + z);
            assertNotNull(block);
            assertFalse(block.startsWith("air"));
            String first = blocks.get(side[0] * (FullPvpBuild.SPAWN_RADIUS + 1) + "," + FullPvpBuild.FLOOR + "," + side[1] * (FullPvpBuild.SPAWN_RADIUS + 1));
            assertTrue(first != null && first.startsWith("stone_brick_stairs"), "az első lépcsőfok a vár szélénél: " + first);
        }
    }

    @Test
    void arenaIsClosedByAWallAndBarriers() {
        Map<String, String> blocks = built(new FullPvpBuild[1]);
        String wall = blocks.get(FullPvpBuild.RADIUS + ",3,0");
        assertNotNull(wall);
        assertTrue(wall.contains("stone_brick"), wall);
        assertEquals("barrier", blocks.get(FullPvpBuild.RADIUS + ",20,0"));
        assertTrue(FullPvpBuild.surface(50, 50) >= 0 && FullPvpBuild.surface(50, 50) <= 4);
    }

    @Test
    void shippedConfigIsComplete() {
        YamlConfiguration config = resource("fullpvp.yml");
        ConfigurationSection kits = config.getConfigurationSection("kits");
        assertNotNull(kits);
        assertTrue(kits.contains(config.getString("respawn-kit")), "a kezdő kit létezik");
        for (String id : kits.getKeys(false)) {
            assertFalse(kits.getStringList(id + ".items").isEmpty(), id + ": van tárgy");
            for (String spec : kits.getStringList(id + ".items")) {
                assertNotNull(Material.matchMaterial(spec.split("\\s+")[0]), id + ": " + spec);
            }
        }
        ConfigurationSection shop = config.getConfigurationSection("shop");
        assertNotNull(shop);
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        for (String id : shop.getKeys(false)) {
            int slot = shop.getInt(id + ".slot");
            assertTrue(slot > 9 && slot < 27 && slot % 9 != 0 && slot % 9 != 8, id + ": a slot a menü belsejében van");
            assertTrue(slots.add(slot), id + ": egyedi slot");
            for (Map<?, ?> offer : shop.getMapList(id + ".items")) {
                assertNotNull(Material.matchMaterial(String.valueOf(offer.get("item")).split("\\s+")[0]), id + ": " + offer);
                assertTrue(offer.get("price") instanceof Number);
            }
        }
        for (String key : new String[]{"first-join", "welcome", "kit-claimed", "bought", "bounty-placed", "bounty-claimed", "only-here", "vault-only-safe"}) {
            assertNotNull(config.getString("messages." + key), key);
        }
        ConfigurationSection ranks = resource("ranks.yml").getConfigurationSection("ranks");
        for (String rank : ranks.getKeys(false)) {
            for (String kit : ranks.getStringList(rank + ".kits")) {
                assertTrue(kits.contains(kit), rank + " -> " + kit);
            }
        }
    }
}
