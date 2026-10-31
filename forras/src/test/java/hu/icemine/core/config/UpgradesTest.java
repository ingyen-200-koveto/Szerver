package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class UpgradesTest {

    private static YamlConfiguration config() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("prefix", "&b[IceMine] &7");
        defaults.set("general.no-permission", "Ehhez nincs jogod!");
        defaults.set("help", List.of("&b[IceMine] &fParancsok", " &b/icem help"));
        YamlConfiguration config = new YamlConfiguration();
        config.setDefaults(defaults);
        return config;
    }

    @Test
    void replacesValuesThatStillHaveTheOldDefault() {
        YamlConfiguration config = config();
        config.set("prefix", "&b&lIce&f&lMine &8» &7");
        config.set("help", List.of("&b&lIce&f&lMine &8» &fAdmin", " &b/im help"));
        int count = Upgrades.applyTo(config, "messages_hu", List.of(
                Map.of("file", "messages_hu", "path", "prefix", "old", "&b&lIce&f&lMine &8» &7"),
                Map.of("file", "messages_hu", "path", "help", "old", List.of("&b&lIce&f&lMine &8» &fAdmin", " &b/im help"))));
        assertEquals(2, count);
        assertEquals("&b[IceMine] &7", config.getString("prefix"));
        assertEquals("&b[IceMine] &fParancsok", config.getStringList("help").get(0));
    }

    @Test
    void keepsWhatTheAdminWrote() {
        YamlConfiguration config = config();
        config.set("general.no-permission", "&4Nem nem!");
        int count = Upgrades.applyTo(config, "messages_hu", List.of(
                Map.of("file", "messages_hu", "path", "general.no-permission", "old", "&cEhhez nincs jogod.")));
        assertEquals(0, count);
        assertEquals("&4Nem nem!", config.getString("general.no-permission"));
    }

    @Test
    void ignoresOtherFilesAndRemovesDroppedKeys() {
        YamlConfiguration config = config();
        config.set("messages.prefix", "&6&lKingdoms &8» &7");
        assertEquals(0, Upgrades.applyTo(config, "lobby", List.of(
                Map.of("file", "kingdoms", "path", "messages.prefix", "old", "&6&lKingdoms &8» &7"))));
        assertEquals(1, Upgrades.applyTo(config, "kingdoms", List.of(
                Map.of("file", "kingdoms", "path", "messages.prefix", "old", "&6&lKingdoms &8» &7"))));
        assertFalse(config.isSet("messages.prefix"));
    }

    @Test
    void comparesNumbersAndListsByText() {
        assertTrue(Upgrades.same(15, "15"));
        assertTrue(Upgrades.same(List.of(1, 2), List.of("1", "2")));
        assertFalse(Upgrades.same("a", List.of("a")));
    }
}
