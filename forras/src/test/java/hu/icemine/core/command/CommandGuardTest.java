package hu.icemine.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandGuardTest {

    @Test
    void stripsSlashArgumentsAndCase() {
        assertEquals("plugins", CommandGuard.normalize("/Plugins"));
        assertEquals("pl", CommandGuard.normalize("/pl list"));
    }

    @Test
    void stripsNamespace() {
        assertEquals("pl", CommandGuard.normalize("/bukkit:pl"));
        assertEquals("version", CommandGuard.normalize("bukkit:version"));
    }

    @Test
    void handlesEmpty() {
        assertEquals("", CommandGuard.normalize(null));
        assertEquals("", CommandGuard.normalize("  "));
    }

    @Test
    void labelKeepsNamespaceForLookup() {
        assertEquals("bukkit:pl", CommandGuard.label("/Bukkit:PL list"));
        assertEquals("icem", CommandGuard.label("/icem help"));
        assertEquals("", CommandGuard.label(null));
    }
}
