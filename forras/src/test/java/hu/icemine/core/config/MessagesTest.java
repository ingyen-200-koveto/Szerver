package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void recognisesTheTagInsideColours() {
        assertTrue(Messages.hasTag("&b[IceMine] &7Szia"));
        assertTrue(Messages.hasTag("§b[IceMine] §7Szia"));
        assertFalse(Messages.hasTag("&b&lIce&f&lMine &8» &7Szia"));
    }

    @Test
    void blockWithoutTagGetsPrefixOnFirstRealLine() {
        List<String> lines = List.of("&8&m          ", " &b&lA szerverről", " &7IP: play.icemine.hu");
        List<String> result = Messages.withHeader(lines, "&b[IceMine] &7");
        assertEquals("&8&m          ", result.get(0));
        assertEquals("&b[IceMine] &7&b&lA szerverről", result.get(1));
        assertEquals(" &7IP: play.icemine.hu", result.get(2));
    }

    @Test
    void blockThatAlreadyHasTagIsUntouched() {
        List<String> lines = List.of("", "&b[IceMine] &fParancsok", " &b/icem help");
        assertEquals(lines, Messages.withHeader(lines, "&b[IceMine] &7"));
    }

    @Test
    void defaultPrefixIsTheNetworkPrefix() {
        assertEquals("&b[IceMine] &7", Messages.DEFAULT_PREFIX);
    }
}
