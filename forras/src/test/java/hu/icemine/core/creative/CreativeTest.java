package hu.icemine.core.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CreativeTest {

    private final PlotGrid grid = new PlotGrid(48, 7, 64);

    @Test
    void plotsAndRoadsAlternate() {
        assertEquals(PlotGrid.NONE, grid.plotAt(0, 0), "a központban út van");
        assertEquals(PlotGrid.key(0, 0), grid.plotAt(7, 7));
        assertEquals(PlotGrid.key(0, 0), grid.plotAt(54, 54));
        assertEquals(PlotGrid.NONE, grid.plotAt(55, 10), "a következő cella útja");
        assertEquals(PlotGrid.key(1, 0), grid.plotAt(62, 10));
        assertEquals(PlotGrid.key(-1, -1), grid.plotAt(-1, -1));
        assertEquals(PlotGrid.NONE, grid.plotAt(-49, -10));
        long key = PlotGrid.key(-3, 5);
        assertEquals(-3, PlotGrid.keyX(key));
        assertEquals(5, PlotGrid.keyZ(key));
        assertEquals(7, grid.minX(0));
        assertEquals(-48, grid.minX(-1));
    }

    @Test
    void borderSurroundsEveryPlot() {
        for (int[] spot : grid.ringOf(0, 0)) {
            assertTrue(grid.ring(spot[0], spot[1]), spot[0] + "," + spot[1]);
            assertEquals(PlotGrid.NONE, grid.plotAt(spot[0], spot[1]), "a szegély az úton van");
        }
        assertEquals((48 + 1) * 4, grid.ringOf(0, 0).length);
        assertFalse(grid.ring(3, 20), "az út közepén nincs szegély");
        assertFalse(grid.ring(20, 20), "a telken nincs szegély");
    }

    @Test
    void spiralNeverRepeats() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            int[] spot = PlotGrid.spiral(i);
            assertTrue(seen.add(spot[0] + "," + spot[1]), "ismétlődik: " + i);
            assertTrue(Math.abs(spot[0]) <= 23 && Math.abs(spot[1]) <= 23);
        }
    }

    @Test
    void generatorLayers() {
        PlotGenerator generator = new PlotGenerator(grid, Material.GRASS_BLOCK, Material.DIRT, Material.SMOOTH_STONE, Material.SMOOTH_STONE_SLAB);
        assertEquals(Material.GRASS_BLOCK, generator.column(20, 64, 20));
        assertEquals(Material.DIRT, generator.column(20, 63, 20));
        assertEquals(Material.STONE, generator.column(20, 60, 20));
        assertEquals(Material.BEDROCK, generator.column(20, 58, 20));
        assertEquals(Material.AIR, generator.column(20, 57, 20));
        assertEquals(Material.AIR, generator.column(20, 65, 20));
        assertEquals(Material.SMOOTH_STONE, generator.column(3, 64, 20));
        assertEquals(Material.SMOOTH_STONE_SLAB, generator.column(6, 65, 20));
        assertEquals(Material.AIR, generator.column(3, 65, 20));
    }

    @Test
    void plotSurvivesSaveAndLoad() {
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID enemy = UUID.randomUUID();
        Plot plot = new Plot(-2, 7, owner, "Peti");
        plot.name = "Kastély";
        plot.created = 123;
        plot.trusted.put(friend, "Anna");
        plot.denied.put(enemy, "Rossz");
        Plot read = Plot.read(plot.id(), plot.write());
        assertNotNull(read);
        assertEquals(-2, read.px);
        assertEquals(7, read.pz);
        assertEquals("Kastély", read.display());
        assertTrue(read.canBuild(owner));
        assertTrue(read.canBuild(friend));
        assertFalse(read.canBuild(enemy));
        assertEquals("Rossz", read.denied.get(enemy));
        assertNull(Plot.read("rossz", "owner: x"));
    }

    @Test
    void shippedConfigIsValid() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(CreativeTest.class.getResourceAsStream("/creative.yml"), StandardCharsets.UTF_8));
        for (String key : new String[]{"plot.ground", "plot.fill", "plot.road-block", "plot.border", "plot.border-claimed"}) {
            assertNotNull(Material.matchMaterial(config.getString(key)), key);
        }
        for (String item : config.getStringList("blocked-items")) {
            assertNotNull(Material.matchMaterial(item), item);
        }
        for (String key : new String[]{"no-plot", "claimed", "no-build", "enter-plot", "clear-confirm", "visiting"}) {
            assertNotNull(config.getString("messages." + key), key);
        }
        assertFalse(config.getStringList("messages.help").isEmpty());
        YamlConfiguration modes = YamlConfiguration.loadConfiguration(new InputStreamReader(CreativeTest.class.getResourceAsStream("/modes.yml"), StandardCharsets.UTF_8));
        assertEquals("open", modes.getString("modes.creative.status"));
    }
}
