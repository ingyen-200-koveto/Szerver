package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HibaFigyeloTest {

    @Test
    void groupsSameErrorAndWaitsBeforeRepeating() {
        HibaFigyelo.torol();
        long t = 1_000_000L;
        HibaFigyelo.rogzit("ERROR", "BedWars", "Arena 3 nem töltött be", null, t);
        HibaFigyelo.rogzit("ERROR", "BedWars", "Arena 5 nem töltött be", null, t);
        HibaFigyelo.rogzit("WARN", "Szerver", "Can't keep up! Is the server overloaded?", null, t);
        String first = HibaFigyelo.kovetkezo("Lobby", t, 10);
        assertNotNull(first);
        assertTrue(first.contains("\"db\":2"), first);
        assertTrue(first.contains("\"forras\":\"Lobby\""));
        assertNull(HibaFigyelo.kovetkezo("Lobby", t + 1000, 10));
        HibaFigyelo.rogzit("ERROR", "BedWars", "Arena 7 nem töltött be", null, t + 2000);
        assertNull(HibaFigyelo.kovetkezo("Lobby", t + 60_000, 10));
        String later = HibaFigyelo.kovetkezo("Lobby", t + HibaFigyelo.ISMETLES + 1, 10);
        assertNotNull(later);
        assertTrue(later.contains("\"db\":1"));
    }

    @Test
    void skipsHarmlessLines() {
        HibaFigyelo.torol();
        HibaFigyelo.rogzit("WARN", "Szerver", "**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!", null, 5);
        HibaFigyelo.rogzit("WARN", "IceMine", "Weboldal (/api/mc/hibak): nem érem el (x).", null, 5);
        assertNull(HibaFigyelo.kovetkezo("Lobby", 5, 10));
    }

    @Test
    void stackTraceAndEscaping() {
        String v = HibaFigyelo.verem(new IllegalStateException("rossz", new RuntimeException("ok")));
        assertTrue(v.startsWith("java.lang.IllegalStateException: rossz"));
        assertTrue(v.contains("Caused by: java.lang.RuntimeException: ok"));
        assertEquals("\"a\\\"b\\nc\"", HibaFigyelo.q("a\"b\nc"));
        assertEquals("Szerver", HibaFigyelo.rovidLogger("net.minecraft.server.MinecraftServer"));
    }
}
