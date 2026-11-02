package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CreativeStatueTest {

    @Test
    void soonStatueBecomesCreative() {
        YamlConfiguration lobby = new YamlConfiguration();
        lobby.set("npcs.mode_fyland.location.x", 1.0);
        lobby.set("npcs.soon_3.location.x", 12.5);
        lobby.set("npcs.soon_3.location.z", -3.5);
        lobby.set("npcs.soon_3.location.world", "lobby");
        assertTrue(Migrations.creativeStatue(lobby));
        assertFalse(lobby.isSet("npcs.soon_3"));
        assertEquals(12.5, lobby.getDouble("npcs.mode_creative.location.x"));
        assertEquals("lobby", lobby.getString("npcs.mode_creative.location.world"));
        assertEquals("mode:creative", lobby.getString("npcs.mode_creative.action"));
        assertEquals("AMETHYST_SHARD", lobby.getString("npcs.mode_creative.equipment.hand"));
        assertFalse(Migrations.creativeStatue(lobby), "csak egyszer fut le");
    }

    @Test
    void nothingToReplace() {
        YamlConfiguration lobby = new YamlConfiguration();
        lobby.set("npcs.mode_fyland.location.x", 1.0);
        assertTrue(Migrations.creativeStatue(lobby));
        assertFalse(lobby.isSet("npcs.mode_creative"));
        assertFalse(Migrations.creativeStatue(lobby));
    }
}
