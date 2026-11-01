package hu.icemine.core.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BedwarsTest {

    private static Map<String, String> build(BwMap.Theme theme, BwMap[] out) {
        Map<String, String> blocks = new HashMap<>();
        BwMap map = new BwMap((x, y, z, data) -> blocks.put(x + "," + y + "," + z, data), 0, 0, 0, theme);
        map.build();
        out[0] = map;
        return blocks;
    }

    private static List<BwMap.Spot> spots(BwMap map, String kind) {
        List<BwMap.Spot> result = new ArrayList<>();
        for (BwMap.Spot spot : map.spots()) {
            if (spot.kind.equals(kind)) {
                result.add(spot);
            }
        }
        return result;
    }

    private static boolean free(String block) {
        return block == null || block.equals("air") || block.startsWith("short_grass") || block.startsWith("snow[");
    }

    @Test
    void everyMapHasAllSpotsForEveryTeam() {
        for (BwMap.Theme theme : BwMap.Theme.values()) {
            BwMap[] out = new BwMap[1];
            build(theme, out);
            BwMap map = out[0];
            String name = theme.id;
            for (String kind : new String[]{"spawn", "bed", "bedfoot", "base", "generator", "shop", "upgrade"}) {
                List<BwMap.Spot> found = spots(map, kind);
                assertEquals(theme.teams, found.size(), name + ": " + kind);
                boolean[] seen = new boolean[theme.teams];
                for (BwMap.Spot spot : found) {
                    seen[spot.team] = true;
                }
                for (boolean team : seen) {
                    assertTrue(team, name + ": minden csapatnak van " + kind);
                }
            }
            assertEquals(4, spots(map, "diamond").size(), name);
            assertEquals(theme.teams > 4 ? 4 : 2, spots(map, "emerald").size(), name);
            assertEquals(1, spots(map, "lobby").size(), name);
            assertEquals(1, spots(map, "spectator").size(), name);
            int extent = BwMap.extent(theme.teams);
            for (BwMap.Spot spot : map.spots()) {
                assertTrue(Math.abs(spot.x) <= extent && Math.abs(spot.z) <= extent, name + ": " + spot.kind + " a pályán belül");
            }
        }
    }

    @Test
    void spawnsAndGeneratorsStandOnGround() {
        for (BwMap.Theme theme : BwMap.Theme.values()) {
            BwMap[] out = new BwMap[1];
            Map<String, String> blocks = build(theme, out);
            for (BwMap.Spot spot : out[0].spots()) {
                if (spot.kind.equals("bed") || spot.kind.equals("bedfoot") || spot.kind.equals("spectator") || spot.kind.equals("base")) {
                    continue;
                }
                int x = (int) Math.floor(spot.x);
                int y = (int) Math.floor(spot.y);
                int z = (int) Math.floor(spot.z);
                String ground = blocks.get(x + "," + (y - 1) + "," + z);
                assertNotNull(ground, theme.id + ": " + spot.kind + " alatt van talaj");
                assertFalse(free(ground), theme.id + ": " + spot.kind + " alatt " + ground);
                assertTrue(free(blocks.get(x + "," + y + "," + z)), theme.id + ": " + spot.kind + " helye szabad, ott ez van: " + blocks.get(x + "," + y + "," + z));
                assertTrue(free(blocks.get(x + "," + (y + 1) + "," + z)), theme.id + ": " + spot.kind + " fölött szabad");
            }
        }
    }

    @Test
    void bedsArePlacedAndPointTheRightWay() {
        for (BwMap.Theme theme : BwMap.Theme.values()) {
            BwMap[] out = new BwMap[1];
            Map<String, String> blocks = build(theme, out);
            List<BwMap.Spot> heads = spots(out[0], "bed");
            List<BwMap.Spot> feet = spots(out[0], "bedfoot");
            for (BwMap.Spot head : heads) {
                BwMap.Spot foot = null;
                for (BwMap.Spot candidate : feet) {
                    if (candidate.team == head.team) {
                        foot = candidate;
                    }
                }
                assertNotNull(foot);
                int dx = (int) (head.x - foot.x);
                int dz = (int) (head.z - foot.z);
                assertEquals(1, Math.abs(dx) + Math.abs(dz), theme.id + ": a fej és a láb szomszédos");
                assertEquals(BwMap.facing(dx, dz), head.facing, theme.id + ": az ágy a fej felé néz");
                String color = TeamColor.values()[head.team].block;
                assertEquals(color + "_bed[part=head,facing=" + head.facing + "]", blocks.get((int) head.x + "," + (int) head.y + "," + (int) head.z));
                assertEquals(color + "_bed[part=foot,facing=" + head.facing + "]", blocks.get((int) foot.x + "," + (int) foot.y + "," + (int) foot.z));
            }
        }
    }

    @Test
    void nobodyCanBuildOnTheBedButTheBedCanBeProtected() {
        // a védett pontok (spawn, generátor, boltosok) 2,5 blokkos körében nem lehet építeni, az ágy körül viszont kell tudni
        for (BwMap.Theme theme : BwMap.Theme.values()) {
            BwMap[] out = new BwMap[1];
            build(theme, out);
            for (BwMap.Spot bed : spots(out[0], "bed")) {
                for (BwMap.Spot spot : out[0].spots()) {
                    if (!Arrays.asList("spawn", "generator", "shop", "upgrade").contains(spot.kind)) {
                        continue;
                    }
                    double dx = bed.x + 0.5 - spot.x;
                    double dz = bed.z + 0.5 - spot.z;
                    assertTrue(dx * dx + dz * dz > 4.5 * 4.5, theme.id + ": " + spot.kind + " túl közel van egy ágyhoz");
                }
            }
        }
    }

    @Test
    void teamIslandsDoNotOverlap() {
        for (int teams : new int[]{4, 8}) {
            List<BwMap.Slot> slots = BwMap.slots(teams);
            assertEquals(teams, slots.size());
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    double d = Math.hypot(slots.get(i).x - slots.get(j).x, slots.get(i).z - slots.get(j).z);
                    assertTrue(d > BwMap.ISLAND * 2 + 6, "csapat szigetek távolsága: " + d);
                }
                for (int[] diamond : BwMap.diamonds(teams)) {
                    double d = Math.hypot(slots.get(i).x - diamond[0], slots.get(i).z - diamond[1]);
                    assertTrue(d > BwMap.ISLAND + 5 + 6, "gyémánt sziget távolsága: " + d);
                }
            }
        }
    }

    @Test
    void balanceKeepsTeamsFairAndHonoursChoices() {
        List<TeamColor> colors = Arrays.asList(TeamColor.values());
        Map<UUID, TeamColor> wanted = new LinkedHashMap<>();
        UUID picky = UUID.randomUUID();
        wanted.put(picky, TeamColor.ROZSASZIN);
        for (int i = 0; i < 9; i++) {
            wanted.put(UUID.randomUUID(), null);
        }
        Map<UUID, TeamColor> result = Balance.assign(wanted, colors, 2, new Random(1));
        assertEquals(10, result.size());
        assertEquals(TeamColor.ROZSASZIN, result.get(picky));
        Map<TeamColor, Integer> sizes = new HashMap<>();
        for (TeamColor color : result.values()) {
            sizes.merge(color, 1, Integer::sum);
        }
        for (int size : sizes.values()) {
            assertTrue(size <= 2);
        }

        // ha mindketten ugyanazt választják egy 4 fős csapatban, akkor is két csapat lesz
        Map<UUID, TeamColor> same = new LinkedHashMap<>();
        same.put(UUID.randomUUID(), TeamColor.PIROS);
        same.put(UUID.randomUUID(), TeamColor.PIROS);
        Map<UUID, TeamColor> split = Balance.assign(same, colors.subList(0, 4), 4, new Random(2));
        assertEquals(2, new java.util.HashSet<>(split.values()).size());
    }

    @Test
    void modesAreReadAndWrongMapsAreReported() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("teams", 8);
        config.set("team-size", 2);
        config.set("maps", Arrays.asList("tolgyes", "sziklavar", "nincsilyen"));
        config.set("aliases", Arrays.asList("duok"));
        List<String> problems = new ArrayList<>();
        BwMode mode = BwMode.read("Duo", config, problems);
        assertEquals("duo", mode.id);
        assertEquals(16, mode.capacity());
        assertEquals("8x2", mode.format());
        assertEquals(Arrays.asList(BwMap.Theme.TOLGYES), mode.maps);
        assertEquals(2, problems.size());
        assertTrue(mode.matches("DUOK"));
        assertTrue(mode.matches("duo"));
        assertFalse(mode.matches("solo"));
    }

    @Test
    void pricesCanBeOverridden() {
        Shop shop = new Shop();
        YamlConfiguration prices = new YamlConfiguration();
        prices.set("gyapju", "8 arany");
        prices.set("tnt", "rossz");
        prices.set("nincsilyen", "1 vas");
        List<String> problems = shop.load(prices);
        assertEquals(8, shop.offers().get("gyapju").price);
        assertEquals(Generator.Type.GOLD, shop.offers().get("gyapju").currency);
        assertEquals(2, problems.size());
        assertEquals(Generator.Type.DIAMOND, Shop.currency("Gyémánt"));
        assertNull(Shop.currency("kő"));
        for (String id : shop.quick()) {
            assertNotNull(shop.offers().get(id), "gyors vásárlás: " + id);
        }
    }

    @Test
    void timelineAndGenerators() {
        BwSettings settings = BwSettings.read(null);
        assertEquals("diamond-2", settings.next(0).type);
        assertEquals("beds", settings.next(1440).type);
        assertNull(settings.next(2400));
        assertEquals(24, Generator.interval(Generator.Type.IRON, 1, 1.0, settings.diamondTiers, settings.emeraldTiers, 24, 160));
        assertEquals(12, Generator.interval(Generator.Type.IRON, 1, 2.0, settings.diamondTiers, settings.emeraldTiers, 24, 160));
        assertEquals(240, Generator.interval(Generator.Type.DIAMOND, 3, 1.0, settings.diamondTiers, settings.emeraldTiers, 24, 160));
        assertEquals(1, Trap.price(0));
        assertEquals(2, Trap.price(1));
        assertEquals(4, Trap.price(2));
    }

    @Test
    void keysRoundTrip() {
        int[][] points = {{0, 80, 0}, {-1234, 64, 5678}, {2400, -60, -800}, {-1, 319, -1}};
        for (int[] p : points) {
            long key = Keys.of(p[0], p[1], p[2]);
            assertEquals(p[0], Keys.x(key));
            assertEquals(p[1], Keys.y(key));
            assertEquals(p[2], Keys.z(key));
        }
    }

    @Test
    void teamColorsParseHungarianNames() {
        assertEquals(TeamColor.ROZSASZIN, TeamColor.parse("Rózsaszín"));
        assertEquals(TeamColor.TURKIZ, TeamColor.parse("turkiz"));
        assertEquals(TeamColor.KEK, TeamColor.parse("Kék"));
        assertNull(TeamColor.parse("lila"));
        assertEquals(BwMap.Theme.HOMOKKO, BwMap.Theme.of("Homokkő"));
    }

    @Test
    void everyBlockIsInsideTheArenaAndIslandsGotFurtherApart() {
        for (BwMap.Theme theme : BwMap.Theme.values()) {
            for (int layout = 1; layout <= BwMap.LAYOUT; layout++) {
                int extent = BwMap.extent(theme.teams, layout);
                int[] max = {0};
                new BwMap((x, y, z, data) -> max[0] = Math.max(max[0], Math.max(Math.abs(x), Math.abs(z))), 0, 0, 0, theme, layout).build();
                assertTrue(max[0] <= extent, theme.id + " (" + layout + "): " + max[0] + " > " + extent);
            }
        }
        for (int teams : new int[]{4, 8}) {
            BwMap.Slot oldSlot = BwMap.slots(teams, 1).get(0);
            BwMap.Slot newSlot = BwMap.slots(teams, BwMap.LAYOUT).get(0);
            assertTrue(Math.hypot(newSlot.x, newSlot.z) > Math.hypot(oldSlot.x, oldSlot.z) + 10, "nagyobb távolság: " + teams);
            assertTrue(Math.abs(BwMap.diamonds(teams, BwMap.LAYOUT)[0][0]) > Math.abs(BwMap.diamonds(teams, 1)[0][0]));
        }
    }
}
