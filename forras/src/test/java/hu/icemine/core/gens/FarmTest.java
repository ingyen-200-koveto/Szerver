package hu.icemine.core.gens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.build.Layout;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FarmTest {

    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(FarmTest.class.getResourceAsStream("/gens.yml"), StandardCharsets.UTF_8));
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Test
    void shippedFieldsLoadInOrder() {
        List<String> problems = new ArrayList<>();
        List<GensData.Field> fields = GensData.loadFields(shipped(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(FarmLayout.FIELDS, fields.size());
        assertEquals(Crop.WHEAT, fields.get(0).crop);
        assertEquals(Crop.NETHER_WART, fields.get(7).crop);
        assertEquals(0, fields.get(0).price, "az első föld ingyen jár");
        for (int i = 1; i < fields.size(); i++) {
            assertEquals(i, fields.get(i).index);
            assertTrue(fields.get(i).price > fields.get(i - 1).price, "minden föld drágább az előzőnél");
            assertTrue(fields.get(i).value > fields.get(i - 1).value, "és többet is ér a termése");
        }
        Set<Crop> crops = new HashSet<>();
        for (GensData.Field field : fields) {
            assertTrue(crops.add(field.crop), "minden földön más terem: " + field.crop);
        }
    }

    @Test
    void brokenFieldConfigFallsBack() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fields.1.crop", "banana");
        config.set("fields.1.price", 500);
        config.set("fields.9.crop", "wheat");
        config.set("fields.x.crop", "wheat");
        List<String> problems = new ArrayList<>();
        List<GensData.Field> fields = GensData.loadFields(config, problems);
        assertEquals(1, fields.size());
        assertEquals(Crop.WHEAT, fields.get(0).crop);
        assertEquals(0, fields.get(0).price, "az első föld akkor is ingyenes, ha árat írtak neki");
        assertEquals(3, problems.size(), problems.toString());
        assertEquals(1, GensData.loadFields(new YamlConfiguration(), new ArrayList<>()).size());
    }

    @Test
    void layoutFieldsDoNotTouchThePlazaOrEachOther() {
        Map<String, Integer> owner = new HashMap<>();
        for (int field = 0; field < FarmLayout.FIELDS; field++) {
            List<int[]> cells = FarmLayout.cells(field);
            assertEquals(FarmLayout.SIZE, cells.size());
            for (int[] cell : cells) {
                assertNull(owner.put(cell[0] + "," + cell[1], field), "két föld nem fedheti egymást");
                assertEquals(field, FarmLayout.fieldAt(cell[0], cell[1]));
                assertTrue(Math.abs(cell[0]) > 3 || Math.abs(cell[1]) > 3, "a tér szabad marad");
                assertTrue(Math.abs(cell[0]) <= GensBuild.FARM - 2 && Math.abs(cell[1]) <= GensBuild.FARM - 2, "körben marad egy út");
            }
        }
        assertEquals(-1, FarmLayout.fieldAt(FarmLayout.SELL[0], FarmLayout.SELL[1]));
        assertEquals(-1, FarmLayout.fieldAt(FarmLayout.HOE[0], FarmLayout.HOE[1]));
        assertEquals(-1, FarmLayout.fieldAt(0, 0));
        // két szomszédos föld között 2 széles út van
        assertEquals(-1, FarmLayout.fieldAt(4, -9));
        assertEquals(-1, FarmLayout.fieldAt(5, -9));
        assertEquals(1, FarmLayout.fieldAt(6, -9));
    }

    @Test
    void scytheStaysInsideTheField() {
        assertEquals(1, FarmLayout.around(0, 0, -9, 0).size());
        assertEquals(9, FarmLayout.around(0, 0, -9, 1).size());
        assertEquals(25, FarmLayout.around(0, 0, -9, 2).size());
        List<int[]> corner = FarmLayout.around(0, -3, -12, 1);
        assertEquals(4, corner.size(), "a föld sarkán csak a földön belüli növények");
        for (int[] cell : FarmLayout.around(2, 12, 0, 2)) {
            assertEquals(2, FarmLayout.fieldAt(cell[0], cell[1]));
        }
    }

    @Test
    void cropStates() {
        assertEquals("wheat[age=7]", Crop.WHEAT.mature());
        assertEquals("wheat[age=0]", Crop.WHEAT.young());
        assertEquals("wheat[age=3]", Crop.WHEAT.half());
        assertEquals("beetroots[age=1]", Crop.BEETROOTS.half());
        assertEquals("melon", Crop.MELON.mature());
        assertEquals("air", Crop.MELON.young());
        assertTrue(Crop.PUMPKIN.wholeBlock());
        assertFalse(Crop.NETHER_WART.wholeBlock());
        assertEquals(Crop.SWEET_BERRY_BUSH, Crop.of(" sweet_berry_bush "));
        assertNull(Crop.of("generator"));
        assertFalse(Crop.MELON.soil.startsWith("farmland"), "teli blokk alatt a termőföld földdé válna");
    }

    @Test
    void openFieldIsFullOfRipeCropsLockedOneIsEmpty() {
        for (Crop crop : Crop.values()) {
            Map<String, String> blocks = new HashMap<>();
            new FieldBuild((x, y, z, d) -> blocks.put(key(x, y, z), d), 0, 0, 0, 3, crop, true).build();
            for (int[] cell : FarmLayout.cells(3)) {
                assertEquals(crop.soil, blocks.get(key(cell[0], 0, cell[1])));
                assertEquals(crop.mature(), blocks.get(key(cell[0], 1, cell[1])));
            }
            Map<String, String> locked = new HashMap<>();
            new FieldBuild((x, y, z, d) -> locked.put(key(x, y, z), d), 0, 0, 0, 3, crop, false).build();
            for (String block : locked.values()) {
                assertFalse(block.startsWith(crop.block), "lezárt földön nem terem semmi");
            }
        }
    }

    @Test
    void plotHasPlazaPathsAndLeavesFieldsToFieldBuild() {
        Map<String, String> blocks = new HashMap<>();
        new GensBuild((x, y, z, d) -> blocks.put(key(x, y, z), d), 0, 0, 0, false, 7).build();
        assertEquals("barrel[facing=up]", blocks.get(key(FarmLayout.SELL[0], 1, FarmLayout.SELL[1])));
        assertTrue(blocks.get(key(FarmLayout.HOE[0], 1, FarmLayout.HOE[1])).startsWith("anvil"));
        Layout.Point spawn = GensBuild.plotSpawn();
        int sx = (int) Math.floor(spawn.x);
        int sz = (int) Math.floor(spawn.z);
        assertEquals("air", blocks.get(key(sx, 1, sz)));
        assertEquals("air", blocks.get(key(sx, 2, sz)));
        assertNotNull(blocks.get(key(sx, 0, sz)));
        for (int dx = -GensBuild.FARM; dx <= GensBuild.FARM; dx++) {
            for (int dz = -GensBuild.FARM; dz <= GensBuild.FARM; dz++) {
                String ground = blocks.get(key(dx, 0, dz));
                if (FarmLayout.fieldAt(dx, dz) >= 0) {
                    assertEquals("dirt", blocks.get(key(dx, -1, dz)), "a földek alatt föld van, a talajt a FieldBuild rakja");
                } else {
                    assertNotNull(ground, "nincs lyuk a farmon: " + dx + "," + dz);
                    assertFalse(ground.equals("air"));
                }
            }
        }
        for (String position : blocks.keySet()) {
            String[] parts = position.split(",");
            int x = Integer.parseInt(parts[0]);
            int z = Integer.parseInt(parts[2]);
            assertTrue(Math.abs(x) <= GensBuild.PLOT_CLEAR && Math.abs(z) <= GensBuild.PLOT_CLEAR, "a takarított részen belül épít: " + position);
        }
    }

    @Test
    void hoeUpgradesFromShippedConfig() {
        List<String> problems = new ArrayList<>();
        HoeUpgrades hoe = new HoeUpgrades();
        hoe.load(shipped(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(4, hoe.enchants().size());
        Map<String, Integer> none = Map.of();
        assertEquals(0, hoe.tier(none), "fa kapával kezdesz");
        assertEquals(0, hoe.radius(none));
        assertEquals(0, hoe.extraPercent(none), 1e-9);
        Map<String, Integer> some = Map.of("boseg", 5, "kaszalas", 1, "aranykez", 4, "szerencse", 2);
        assertEquals(12, hoe.total(some));
        assertEquals(1, hoe.tier(some), "5 szinttől kő kapa");
        assertEquals(50, hoe.extraPercent(some), 1e-9);
        assertEquals(1, hoe.radius(some));
        assertEquals(20, hoe.valuePercent(some), 1e-9);
        assertEquals(1, hoe.luckPercent(some), 1e-9);
        Map<String, Integer> maxed = Map.of("boseg", 99, "kaszalas", 99, "aranykez", 99, "szerencse", 99);
        assertEquals(3, hoe.radius(maxed), "a kaszálás legfeljebb az egész föld");
        assertEquals(250, hoe.extraPercent(maxed), 1e-9, "a szint nem lehet több a maximumnál");
        assertEquals(HoeUpgrades.MATERIALS.length - 1, hoe.tier(maxed), "netherit kapa a végén");
        assertEquals(58, hoe.total(maxed), "a szintek a maximumnál megállnak");
        HoeUpgrades.Enchant boseg = hoe.get("boseg");
        assertEquals(750, boseg.priceFor(0));
        assertTrue(boseg.priceFor(1) > boseg.priceFor(0));
        assertEquals(-1, boseg.priceFor(boseg.max));
        assertEquals(25_000, hoe.get("kaszalas").priceFor(0));
        assertEquals(250_000, hoe.get("kaszalas").priceFor(1));
    }

    @Test
    void hoeConfigIsValidated() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hoe.tiers", List.of(0, 1, 2));
        config.set("hoe.enchants.kaszalas.max", 50);
        config.set("hoe.enchants.repules.max", 3);
        List<String> problems = new ArrayList<>();
        HoeUpgrades hoe = new HoeUpgrades();
        hoe.load(config, problems);
        assertEquals(2, problems.size(), problems.toString());
        assertEquals(3, hoe.get("kaszalas").max, "a kaszálás legfeljebb 3 szintes");
        assertEquals(15, hoe.levelsFor(2), "rossz tiers esetén az alap marad");
    }

    @Test
    void harvestAmountRoundsByChance() {
        assertEquals(1, HoeUpgrades.amount(1.0, 0, 0.99));
        assertEquals(2, HoeUpgrades.amount(1.5, 0, 0.4));
        assertEquals(1, HoeUpgrades.amount(1.5, 0, 0.6));
        assertEquals(3, HoeUpgrades.amount(1.0, 200, 0.0));
        assertEquals(2.75, HoeUpgrades.expected(2.2, 25), 1e-9);
        assertEquals(64, HoeUpgrades.amount(50, 1000, 0.0), "egy növényért legfeljebb egy stack");
        assertEquals(1, HoeUpgrades.amount(0.2, 0, 0.0), "legalább egy mindig jár");
    }

    @Test
    void legacyRefund() {
        assertEquals(300, GensData.legacyGenerator(1));
        assertEquals(550, GensData.legacyGenerator(2));
        assertEquals(705_300, GensData.legacyGenerator(10));
        assertEquals(705_300, GensData.legacyGenerator(99));
        assertEquals(1200, GensData.legacyDrop("NETHERITE_SCRAP"));
        assertEquals(0, GensData.legacyDrop("WHEAT"), "a búza már a farm terménye, azt nem számoljuk régiként");
    }

    @Test
    void questTypesAcceptOldNames() {
        assertEquals("harvest", QuestBook.type("collect_items"));
        assertEquals("unlock", QuestBook.type("place_gen"));
        assertEquals("sell_items", QuestBook.type("SELL_ITEMS"));
        assertNull(QuestBook.type("fly"));
        YamlConfiguration config = shipped();
        for (String id : config.getConfigurationSection("quests.pool").getKeys(false)) {
            assertNotNull(QuestBook.type(config.getString("quests.pool." + id + ".type")), id);
        }
    }
}
