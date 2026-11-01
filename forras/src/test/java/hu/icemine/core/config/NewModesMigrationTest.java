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
    void oldModesAreReplacedEverywhere() {
        Map<String, YamlConfiguration> files = new HashMap<>();
        files.put("modes", withDefaults("modes", "modes:\n  kitpvp:\n    name: KitPvP\n    slot: 12\n  skypvp:\n    name: SkyPvP\n    slot: 10\n  gens:\n    name: Gens\n"));
        files.put("modelobbies", withDefaults("modelobbies", "lobbies:\n  kitpvp:\n    world: lobby_kitpvp\n    built: true\n  skypvp:\n    world: lobby_skypvp\n"));
        files.put("lobby", withDefaults("lobby", "npcs:\n  mode_kitpvp:\n    location: {world: world, x: 5.5, y: 70.0, z: -11.5, yaw: 20.0}\n    action: 'mode:kitpvp'\n"
                + "  mode_skypvp:\n    location: {world: world, x: -5.5, y: 70.0, z: -11.5, yaw: -20.0}\nmenus:\n  stats:\n    icons:\n      kitpvp: DIAMOND_SWORD\n      skypvp: FEATHER\n"
                + "info-texts:\n  mode_kitpvp: [a]\n  mode_skypvp: [b]\n"));
        files.put("config", withDefaults("config", "leaderboards:\n  boards:\n    kitpvp:\n      key: kitpvp.kills\n    streak:\n      key: kitpvp.best_streak\n"));
        files.put("scoreboard", withDefaults("scoreboard", "boards:\n  kitpvp: [a]\n  skypvp: [b]\n  lobby: [c]\n"));
        files.put("economy", withDefaults("economy", "currencies:\n  kristaly:\n    name: Kristály\n"));
        files.put("ranks", withDefaults("ranks", "ranks:\n  default:\n    kits: [harcos, ijasz, tank]\n  vip:\n    kits: [orgyilkos, combo]\n"));
        files.put("fullpvp", withDefaults("fullpvp", ""));
        files.put("comboarena", withDefaults("comboarena", ""));
        Set<String> saved = new HashSet<>();
        List<String> notes = Migrations.newModes(files::get, saved::add);
        assertFalse(notes.isEmpty());

        YamlConfiguration modes = files.get("modes");
        assertFalse(modes.isSet("modes.kitpvp"));
        assertFalse(modes.isSet("modes.skypvp"));
        assertEquals(12, modes.getInt("modes.fullpvp.slot"));
        assertEquals(10, modes.getInt("modes.fyland.slot"));
        assertTrue(modes.isSet("modes.gens"));

        YamlConfiguration lobbies = files.get("modelobbies");
        assertFalse(lobbies.isSet("lobbies.kitpvp"));
        assertEquals("lobby_fullpvp", lobbies.getString("lobbies.fullpvp.world"));
        assertFalse(lobbies.getBoolean("lobbies.fyland.built"));

        YamlConfiguration lobby = files.get("lobby");
        assertFalse(lobby.isSet("npcs.mode_kitpvp"));
        assertEquals(5.5, lobby.getDouble("npcs.mode_fullpvp.location.x"));
        assertEquals("mode:fullpvp", lobby.getString("npcs.mode_fullpvp.action"));
        assertEquals("mode:fyland", lobby.getString("npcs.mode_fyland.action"));
        assertNull(lobby.get("menus.stats.icons.kitpvp", null));
        assertEquals("GRASS_BLOCK", lobby.getString("menus.stats.icons.fyland"));
        assertTrue(lobby.isSet("info-texts.mode_fullpvp"));
        assertFalse(lobby.isSet("info-texts.mode_skypvp"));

        YamlConfiguration main = files.get("config");
        assertFalse(main.isSet("leaderboards.boards.kitpvp"));
        assertEquals("fullpvp.kills", main.getString("leaderboards.boards.fullpvp.key"));
        assertEquals("fullpvp.best_streak", main.getString("leaderboards.boards.streak.key"));

        YamlConfiguration board = files.get("scoreboard");
        assertFalse(board.isSet("boards.kitpvp"));
        assertTrue(board.isSet("boards.fyland"));
        assertTrue(board.isSet("boards.lobby"));

        assertTrue(files.get("economy").isSet("currencies.fyland.name"));

        YamlConfiguration ranks = files.get("ranks");
        assertEquals(List.of("harcos", "ijasz"), ranks.getStringList("ranks.default.kits"), "ami FullPvP kitként is létezik, marad");
        assertEquals(List.of("combo", "vip"), ranks.getStringList("ranks.vip.kits"));

        assertTrue(saved.containsAll(Set.of("modes", "modelobbies", "lobby", "config", "scoreboard", "economy", "ranks")));
        saved.clear();
        assertTrue(Migrations.newModes(files::get, saved::add).isEmpty(), "másodszor már nem fut le");
        assertTrue(saved.isEmpty());
    }

    @Test
    void oldWorldNamesAreSafe() {
        assertTrue(OldModes.safeName("ice_kitpvp"));
        assertFalse(OldModes.safeName("world"));
        assertFalse(OldModes.safeName("../plugins"));
        assertFalse(OldModes.safeName(""));
    }
}
