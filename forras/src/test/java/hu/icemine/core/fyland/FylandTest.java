package hu.icemine.core.fyland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FylandTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(FylandTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void gridGivesEveryIslandItsOwnPlace() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            int[] cell = FyIsland.grid(i);
            assertTrue(seen.add(cell[0] + "," + cell[1]), "ismétlődő hely: " + i);
            assertTrue(Math.abs(cell[0]) <= 36 && Math.abs(cell[1]) <= 36, "a spirál tömör marad");
        }
        assertEquals(0, FyIsland.grid(0)[0]);
        assertEquals(0, FyIsland.grid(0)[1]);
    }

    @Test
    void islandSurvivesSaveAndLoad() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        FyIsland island = new FyIsland(UUID.randomUUID(), owner, "Peti");
        island.members.put(member, "Anna");
        island.index = 17;
        island.x = 800;
        island.y = 100;
        island.z = -400;
        island.homeX = 801.5;
        island.homeY = 101;
        island.homeZ = -399.5;
        island.blocks = 1234;
        island.phase = 3;
        island.phaseBlocks = 42;
        island.cycle = 2;
        island.visitors = false;
        island.rewardPending = true;
        island.upgrades.put("size", 2);
        UUID banned = UUID.randomUUID();
        UUID rater = UUID.randomUUID();
        island.worth = 777;
        island.name = "Jégvár";
        island.moderators.add(member);
        island.banned.put(banned, "Rossz");
        island.hasWarp = true;
        island.warpX = 805.5;
        island.warpY = 101;
        island.warpZ = -402.5;
        island.warpYaw = 90f;
        island.warpName = "Bolt";
        island.ratings.put(rater, 4);
        island.coops.add(UUID.randomUUID());
        YamlConfiguration yaml = new YamlConfiguration();
        island.write(yaml);
        YamlConfiguration copy = new YamlConfiguration();
        assertTrue(copy.saveToString().isEmpty());
        try {
            copy.loadFromString(yaml.saveToString());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        FyIsland read = FyIsland.read(island.id, copy);
        assertNotNull(read);
        assertEquals(owner, read.owner);
        assertEquals("Anna", read.members.get(member));
        assertEquals(17, read.index);
        assertEquals(800, read.x);
        assertEquals(-400, read.z);
        assertEquals(1234, read.blocks);
        assertEquals(3, read.phase);
        assertEquals(42, read.phaseBlocks);
        assertEquals(2, read.cycle);
        assertFalse(read.visitors);
        assertTrue(read.rewardPending);
        assertEquals(2, read.level("size"));
        assertTrue(read.member(member));
        assertTrue(read.member(owner));
        assertFalse(read.member(UUID.randomUUID()));
        assertEquals(2, read.size());
        assertEquals(777, read.worth);
        assertEquals("Jégvár", read.display());
        assertTrue(read.manager(member), "a moderátor kezelheti a szigetet");
        assertTrue(read.manager(owner));
        assertEquals("Rossz", read.banned.get(banned));
        assertTrue(read.hasWarp);
        assertEquals(805.5, read.warpX);
        assertEquals(-402.5, read.warpZ);
        assertEquals("Bolt", read.warpName);
        assertEquals(4.0, read.rating());
        assertTrue(read.coops.isEmpty(), "a coop nem mentődik");
        read.coops.add(banned);
        assertTrue(read.trusted(banned));
        assertFalse(read.member(banned));
        read.members.remove(member);
        assertFalse(read.manager(member), "aki már nem tag, az moderátorként sem kezelheti");
        assertNull(FyIsland.read(UUID.randomUUID(), new YamlConfiguration()));
    }

    private static void material(String where, String name) {
        assertNotNull(Material.matchMaterial(name), where + ": ismeretlen anyag " + name);
    }

    @Test
    void shippedPhasesUseRealBlocksItemsAndMobs() {
        YamlConfiguration config = resource("fyland.yml");
        ConfigurationSection section = config.getConfigurationSection("phases");
        assertNotNull(section);
        List<String> ids = new ArrayList<>(section.getKeys(false));
        assertTrue(ids.size() >= 10, "legalább 10 fázis");
        assertEquals("siksag", ids.get(0));
        assertEquals("end", ids.get(ids.size() - 1));
        for (String id : ids) {
            ConfigurationSection phase = section.getConfigurationSection(id);
            assertTrue(phase.getInt("blocks") >= 100, id);
            ConfigurationSection pool = phase.getConfigurationSection("blocks-pool");
            assertFalse(pool.getKeys(false).isEmpty(), id);
            for (String block : pool.getKeys(false)) {
                material(id, block);
                assertTrue(pool.getDouble(block) > 0, id + ": " + block);
                assertFalse(block.equals("WATER") || block.equals("LAVA"), id);
            }
            for (String key : new String[]{"chest", "reward"}) {
                List<String> lines = phase.getStringList(key);
                assertFalse(lines.isEmpty(), id + ": " + key);
                for (String line : lines) {
                    assertTrue(line.matches("[A-Z_]+( \\d+(-\\d+)?( (0(\\.\\d+)?|1(\\.0+)?))?)?"), id + ": " + line);
                    material(id, line.split(" ")[0]);
                }
            }
            ConfigurationSection mobs = phase.getConfigurationSection("mobs");
            assertFalse(mobs.getKeys(false).isEmpty(), id);
            for (String mob : mobs.getKeys(false)) {
                assertNotNull(FyPhase.entity(mob), id + ": " + mob);
            }
        }
    }

    @Test
    void upgradesAndMarketAreConsistent() {
        YamlConfiguration config = resource("fyland.yml");
        ConfigurationSection upgrades = config.getConfigurationSection("upgrades");
        for (String id : new String[]{"size", "members", "luck"}) {
            assertNotNull(upgrades.getConfigurationSection(id), id);
            assertEquals(upgrades.getList(id + ".values").size(), upgrades.getList(id + ".prices").size() + 1, id);
        }
        List<Double> sizes = upgrades.getDoubleList("size.values");
        assertTrue(sizes.get(sizes.size() - 1) * 2 < config.getInt("island.spacing"), "a legnagyobb sziget is elfér két sziget között");
        ConfigurationSection sell = config.getConfigurationSection("market.sell");
        for (String key : sell.getKeys(false)) {
            assertNotNull(Material.matchMaterial(key), key);
        }
        for (Map<?, ?> offer : config.getMapList("market.buy")) {
            assertNotNull(Material.matchMaterial(String.valueOf(offer.get("item")).split("\\s+")[0]), String.valueOf(offer));
        }
        assertNotNull(resource("economy.yml").getConfigurationSection("currencies." + config.getString("currency")), "a pénznem létezik");
        for (String key : new String[]{"island-created", "bossbar", "phase-next", "no-build", "sold", "invite-received"}) {
            assertNotNull(config.getString("messages." + key), key);
        }
        assertFalse(config.getStringList("messages.help").isEmpty());
    }

    @Test
    void enchantsAreStoredAndFitTheRightItems() {
        java.util.Map<String, Integer> values = new java.util.LinkedHashMap<>();
        values.put("autoszmelt", 1);
        values.put("robbano", 3);
        assertEquals("autoszmelt:1,robbano:3", FyEnchants.format(values));
        assertEquals(values, FyEnchants.parse("autoszmelt:1,robbano:3"));
        assertTrue(FyEnchants.parse("rossz,valami:x,").isEmpty());
        assertTrue(FyEnchants.fits("pickaxe", Material.DIAMOND_PICKAXE));
        assertFalse(FyEnchants.fits("pickaxe", Material.DIAMOND_AXE));
        assertTrue(FyEnchants.fits("tool", Material.IRON_SHOVEL));
        assertTrue(FyEnchants.fits("weapon", Material.NETHERITE_AXE));
        assertTrue(FyEnchants.fits("boots", Material.LEATHER_BOOTS));
        assertTrue(FyEnchants.fits("helmet", Material.TURTLE_HELMET));
        assertFalse(FyEnchants.fits("helmet", Material.DIAMOND_CHESTPLATE));
        assertEquals("III", FyEnchants.roman(3));
        assertEquals(Material.IRON_INGOT, FyEnchants.SMELT.get(Material.RAW_IRON));
    }

    @Test
    void shippedExtrasAreValid() {
        YamlConfiguration config = resource("fyland.yml");
        ConfigurationSection values = config.getConfigurationSection("block-values");
        assertNotNull(values);
        for (String key : values.getKeys(false)) {
            assertNotNull(Material.matchMaterial(key), "block-values: " + key);
            assertTrue(values.getLong(key) > 0, key);
        }
        assertTrue(config.getLong("island.level-points") > 0);
        ConfigurationSection enchants = config.getConfigurationSection("enchanter.enchants");
        assertNotNull(enchants);
        assertEquals(8, enchants.getKeys(false).size());
        for (String id : enchants.getKeys(false)) {
            ConfigurationSection entry = enchants.getConfigurationSection(id);
            assertEquals(entry.getInt("max-level"), entry.getIntegerList("cost").size(), id + ": minden szintnek van ára");
            assertNotNull(Material.matchMaterial(entry.getString("icon")), id);
            assertNotNull(config.getString("messages.enchant-applies-" + entry.getString("applies")), id + ": applies");
        }
        for (String line : config.getStringList("airdrop.loot")) {
            assertTrue(line.matches("[A-Z_]+( \\d+(-\\d+)?( (0(\\.\\d+)?|1(\\.0+)?))?)?"), line);
            assertNotNull(Material.matchMaterial(line.split(" ")[0]), line);
        }
        int radius = config.getInt("battlefield.radius");
        int x = config.getInt("battlefield.x");
        assertTrue(x - radius > 8, "a csatatér nem lóg bele a központba");
        assertTrue(x + radius < config.getInt("island.spacing") / 2, "a csatatér nem lóg bele egy szigetbe");
        assertFalse(config.getBoolean("battlefield.built"));
        assertTrue(config.getBoolean("visit.require-warp"));
        for (String key : new String[]{"manager-only", "level-done", "coop-added", "banned", "warp-set", "pw-rated", "airdrop-spawned", "enchant-success"}) {
            assertNotNull(config.getString("messages." + key), key);
        }
        assertFalse(config.getStringList("messages.info").isEmpty());
        assertFalse(config.getStringList("messages.enchant-book-lore").isEmpty());
    }
}
