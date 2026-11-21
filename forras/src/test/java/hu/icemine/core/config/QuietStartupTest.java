package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

class QuietStartupTest {

    private static LogEvent event(Level level, String text) {
        return Log4jLogEvent.newBuilder().setLevel(level).setMessage(new SimpleMessage(text)).build();
    }

    @Test
    void hidesInfoOnlyWhileStarting() {
        QuietStartup.Gate gate = new QuietStartup.Gate(true);
        QuietStartup.activateForTest();
        assertEquals(Filter.Result.DENY, gate.filter(event(Level.INFO, "[Citizens] Loaded 12 NPCs.")));
        assertEquals(Filter.Result.NEUTRAL, gate.filter(event(Level.WARN, "Valami nem jó")));
        assertEquals(Filter.Result.NEUTRAL, gate.filter(event(Level.ERROR, "Hiba")));
        assertEquals(Filter.Result.NEUTRAL, gate.filter(event(Level.INFO, "Done (12.345s)! For help, type \"help\"")));
        assertEquals(Filter.Result.NEUTRAL, gate.filter(event(Level.INFO, "[IceMine] ✔ IceMine 3.23.0 elindult.")));
        int hidden = QuietStartup.finish();
        assertEquals(0, hidden < 0 ? -1 : 0);
        assertEquals(Filter.Result.NEUTRAL, gate.filter(event(Level.INFO, "[Citizens] Loaded 12 NPCs.")));
    }
}
