package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NewModesMigrationTest {

    private static YamlConfiguration withDefaults(String file, String userText) {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                NewModesMigrationTest.class.getResourceAsStream("/" + file + ".yml"), StandardCharsets.UTF_8));
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(userText);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        config.setDefaults(defaults);
        return config;
    }

    @Test
    void oldModesAreRemovedAndFylandAdded() {
        Map<String, YamlConfiguration> files = new HashMap<>();
        files.put("modes", withDefaults("modes", "modes:\n  kitpvp:\n    name: KitPvP\n    slot: 12\n  skypvp:\n    name: SkyPvP\n    slot: 10\n  fullpvp:\n    slot: 12\n  gens:\n    name: Gens\n"));
        files.put("modelobbies", withDefaults("modelobbies", "lobbies:\n  kitpvp:\n    world: lobby_kitpvp\n    built: true\n  skypvp:\n    world: lobby_skypvp\n  fullpvp:\n    world: lobby_fullpvp\n"));
        files.put("lobby", withDefaults("lobby", "npcs:\n  mode_fullpvp:\n    location: {world: world, x: 5.5, y: 70.0, z: -11.5, yaw: 20.0}\n    action: 'mode:fullpvp'\n"
                + "  mode_skypvp:\n    location: {world: world, x: -5.5, y: 70.0, z: -11.5, yaw: -20.0}\nholograms:\n  top_x:\n    target: fullpvp\n  top_y:\n    target: gens\n"
                + "menus:\n  stats:\n    icons:\n      fullpvp: DIAMOND_SWORD\n      skypvp: FEATHER\n      streak: BLAZE_POWDER\n"
                + "info-texts:\n  mode_fullpvp: [a]\n  mode_skypvp: [b]\n"));
        files.put("config", withDefaults("config", "leaderboards:\n  boards:\n    fullpvp:\n      key: fullpvp.kills\n    streak:\n      key: fullpvp.best_streak\n    gens:\n      key: gens.earned\n"));
        files.put("scoreboard", withDefaults("scoreboard", "boards:\n  fullpvp: [a]\n  skypvp: [b]\n  lobby: [c]\n"));
        files.put("economy", withDefaults("economy", "currencies:\n  kristaly:\n    name: Kristály\n"));
        files.put("ranks", withDefaults("ranks", "ranks:\n  default:\n    kits: [harcos, ijasz]\n    perks: ['&7FullPvP: Kezdő kit', '&7Hónyom']\n  vip:\n    kits: [vip, combo]\n    perks: ['&7Orgyilkos kit', '&710% bónusz']\n"));
        files.put("comboarena", withDefaults("comboarena", ""));
        Set<String> saved = new HashSet<>();
        List<String> notes = Migrations.newModes(files::get, saved::add);
        assertFalse(notes.isEmpty());

        YamlConfiguration modes = files.get("modes");
        for (String old : new String[]{"kitpvp", "skypvp", "fullpvp"}) {
            assertFalse(modes.isSet("modes." + old), old);
        }
        assertEquals(10, modes.getInt("modes.fyland.slot"));
        assertTrue(modes.isSet("modes.gens"));

        YamlConfiguration lobbies = files.get("modelobbies");
        assertFalse(lobbies.isSet("lobbies.fullpvp"));
        assertFalse(lobbies.isSet("lobbies.kitpvp"));
        assertEquals("lobby_fyland", lobbies.getString("lobbies.fyland.world"));

        YamlConfiguration lobby = files.get("lobby");
        assertFalse(lobby.isSet("npcs.mode_fullpvp"));
        assertEquals(5.5, lobby.getDouble("npcs.soon_2.location.x"), "a FullPvP szobor helyén hamarosan szobor");
        assertEquals("mode:fyland", lobby.getString("npcs.mode_fyland.action"));
        assertFalse(lobby.isSet("holograms.top_x"));
        assertTrue(lobby.isSet("holograms.top_y"));
        assertFalse(lobby.isSet("menus.stats.icons.fullpvp"));
        assertFalse(lobby.isSet("menus.stats.icons.streak"));
        assertEquals("GRASS_BLOCK", lobby.getString("menus.stats.icons.fyland"));
        assertFalse(lobby.isSet("info-texts.mode_fullpvp"));
        assertTrue(lobby.isSet("info-texts.mode_fyland"));

        YamlConfiguration main = files.get("config");
        assertFalse(main.isSet("leaderboards.boards.fullpvp"));
        assertFalse(main.isSet("leaderboards.boards.streak"));
        assertEquals("fyland.blocks", main.getString("leaderboards.boards.fyland.key"));
        assertTrue(main.isSet("leaderboards.boards.gens"));

        YamlConfiguration board = files.get("scoreboard");
        assertFalse(board.isSet("boards.fullpvp"));
        assertTrue(board.isSet("boards.fyland"));
        assertTrue(board.isSet("boards.lobby"));

        assertTrue(files.get("economy").isSet("currencies.fyland.name"));

        YamlConfiguration ranks = files.get("ranks");
        assertTrue(ranks.getStringList("ranks.default.kits").isEmpty());
        assertEquals(List.of("combo"), ranks.getStringList("ranks.vip.kits"));
        assertEquals(List.of("&7Hónyom"), ranks.getStringList("ranks.default.perks"));
        assertEquals(List.of("&710% bónusz"), ranks.getStringList("ranks.vip.perks"));

        assertTrue(saved.containsAll(Set.of("modes", "modelobbies", "lobby", "config", "scoreboard", "economy", "ranks")));
        saved.clear();
        assertTrue(Migrations.newModes(files::get, saved::add).isEmpty(), "másodszor már nem fut le");
        assertTrue(saved.isEmpty());
    }

    @Test
    void hotbarGetsCompassInTheMiddle() {
        Map<String, YamlConfiguration> files = new HashMap<>();
        files.put("lobby", withDefaults("lobby", "hotbar:\n  items:\n    selector: {slot: 0, material: COMPASS}\n    profile: {slot: 1}\n"
                + "    cosmetics: {slot: 3}\n    parkour: {slot: 5}\n    settings: {slot: 7}\n    info: {slot: 8}\n"));
        Set<String> saved = new HashSet<>();
        assertFalse(Migrations.hotbarV3(files::get, saved::add).isEmpty());
        YamlConfiguration lobby = files.get("lobby");
        assertFalse(lobby.isSet("hotbar.items.profile"));
        assertFalse(lobby.isSet("hotbar.items.cosmetics"));
        assertEquals(4, lobby.getInt("hotbar.items.selector.slot"));
        assertEquals(0, lobby.getInt("hotbar.items.parkour.slot"));
        assertEquals(8, lobby.getInt("hotbar.items.info.slot"));
        assertEquals(Set.of("hotbar.items.selector", "hotbar.items.parkour", "hotbar.items.settings", "hotbar.items.info"),
                new HashSet<>(lobby.getConfigurationSection("hotbar.items").getKeys(false)).stream().map(k -> "hotbar.items." + k).collect(java.util.stream.Collectors.toSet()));
        assertTrue(Migrations.hotbarV3(files::get, saved::add).isEmpty());
    }

    @Test
    void oldWorldNamesAreSafe() {
        assertTrue(OldModes.safeName("ice_kitpvp"));
        assertFalse(OldModes.safeName("world"));
        assertFalse(OldModes.safeName("../plugins"));
        assertFalse(OldModes.safeName(""));
    }
}
