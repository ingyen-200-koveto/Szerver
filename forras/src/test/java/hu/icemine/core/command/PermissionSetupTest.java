package hu.icemine.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.Test;

class PermissionSetupTest {

    @Test
    void parsesDefaults() {
        assertEquals(PermissionDefault.TRUE, PermissionSetup.parseDefault("true"));
        assertEquals(PermissionDefault.OP, PermissionSetup.parseDefault(" OP "));
        assertEquals(PermissionDefault.FALSE, PermissionSetup.parseDefault("senki"));
        assertEquals(PermissionDefault.NOT_OP, PermissionSetup.parseDefault("notop"));
        assertNull(PermissionSetup.parseDefault("talán"));
    }

    @Test
    void icemSubcommandsHaveTheirOwnOrder() {
        assertEquals("help", IcemCommand.SUBS.get(0));
        assertEquals(10, IcemCommand.SUBS.size());
    }
}
