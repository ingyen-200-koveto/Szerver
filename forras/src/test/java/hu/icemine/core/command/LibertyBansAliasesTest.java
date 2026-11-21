package hu.icemine.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibertyBansAliasesTest {

    @Test
    void removesConflictsAndSetsHungarian() {
        String config = "lang-file: 'en'\ncommands:\n  aliases:\n    - 'ban'\n    - 'ipban'\n    - 'ban-ip:ipban'\n    - 'mute'\n    - 'kick'\n    - 'unban'\n    - 'unbanip'\n    - 'unmute'\n    - 'warn'\n";
        String patched = LibertyBansAliases.patch(config);
        assertTrue(patched.startsWith("lang-file: 'hu'"));
        assertFalse(patched.contains("- 'ban'\n"));
        assertFalse(patched.contains("- 'kick'"));
        assertTrue(patched.contains("- 'ipban'"));
        assertTrue(patched.contains("- 'unmute'"));
        assertTrue(patched.contains("- 'warn'"));
        assertEquals(patched, LibertyBansAliases.patch(patched));
        assertEquals("lang-file: 'de'", LibertyBansAliases.patch("lang-file: 'de'"));
    }

    @Test
    void permissions() {
        assertEquals("libertybans.ban.do.target.uuid", LibertyBansAliases.permission("ban"));
        assertEquals("libertybans.ban.undo.target.uuid", LibertyBansAliases.permission("unban"));
    }
}
