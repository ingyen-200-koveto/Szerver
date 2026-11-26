package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void recognisesTheTagInsideColours() {
        assertTrue(Messages.hasTag("&b&lIceMine &8• &7Szia"));
        assertTrue(Messages.hasTag("§b§lIceMine §8• §7Szia"));
        assertFalse(Messages.hasTag("&b&lIce&f&lMine &8» &7Szia"));
    }

    @Test
    void blockWithoutTagGetsPrefixOnFirstRealLine() {
        List<String> lines = List.of("&8&m          ", " &b&lA szerverről", " &7IP: play.icemine.hu");
        List<String> result = Messages.withHeader(lines, "&b&lIceMine &8• &7");
        assertEquals("&8&m          ", result.get(0));
        assertEquals("&b&lIceMine &8• &7&b&lA szerverről", result.get(1));
        assertEquals(" &7IP: play.icemine.hu", result.get(2));
    }

    @Test
    void blockThatAlreadyHasTagIsUntouched() {
        List<String> lines = List.of("", "&b&lIceMine &8• &fParancsok", " &b/icem help");
        assertEquals(lines, Messages.withHeader(lines, "&b&lIceMine &8• &7"));
    }

    @Test
    void defaultPrefixIsTheNetworkPrefix() {
        assertEquals("&b&lIceMine &8• &7", Messages.DEFAULT_PREFIX);
    }

    @Test
    void oldPrefixIsMigrated() {
        assertEquals("&b&lIceMine &8• &7Szia", Migrations.newPrefix("&b[IceMine] &7Szia"));
        assertEquals(" &b&lIceMine &8• &e&lDuels", Migrations.newPrefix(" &b[IceMine] &e&lDuels"));
    }

    @Test
    void prefixCoreDropsTrailingColours() {
        assertEquals("§b§lIceMine §8•", Messages.core("§b§lIceMine §8• §7"));
    }
}
