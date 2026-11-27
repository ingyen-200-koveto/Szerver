package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangelogTest {

    private List<Changelog.Entry> shipped() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("changelog.yml")) {
            return Changelog.load(in);
        }
    }

    @Test
    void firstRunSendsOnlyTheLatest() throws Exception {
        List<Changelog.Entry> todo = Changelog.pending(shipped(), "3.70.0", null);
        assertEquals(1, todo.size());
        assertEquals("3.70.0", todo.get(0).version());
    }

    @Test
    void skippedVersionsComeTogether() throws Exception {
        List<Changelog.Entry> todo = Changelog.pending(shipped(), "3.70.0", "3.67.0");
        assertEquals(List.of("3.68.0", "3.69.0", "3.70.0"), todo.stream().map(Changelog.Entry::version).toList());
        assertTrue(Changelog.pending(shipped(), "3.70.2", "3.70.0").isEmpty(), "javításnál nincs üzenet");
    }

    @Test
    void currentVersionHasAnEntry() throws Exception {
        String version = new String(getClass().getClassLoader().getResourceAsStream("plugin.yml").readAllBytes())
                .lines().filter(l -> l.startsWith("version:")).findFirst().orElse("").replaceAll("[^0-9.]", "");
        if (version.endsWith(".0")) {
            assertTrue(shipped().stream().anyMatch(e -> e.version().equals(version)), "a(z) " + version + " nagy frissítéshez kell bejegyzés a changelog.yml-be");
        }
    }
}
