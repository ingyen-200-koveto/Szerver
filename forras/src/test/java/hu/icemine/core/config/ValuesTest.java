package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValuesTest {

    private static Values values(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return new Values("teszt.yml", map::get);
    }

    @Test
    void missingValueUsesFallbackSilently() {
        Values values = values();
        assertEquals(15, values.integer("ido", 15, 1, 60));
        assertEquals("x", values.text("nev", "x"));
        assertTrue(values.problems().isEmpty());
    }

    @Test
    void numbersFromTextAreAccepted() {
        Values values = values("ido", "20", "szorzo", "1,5", "nagy", 5_000_000_000L);
        assertEquals(20, values.integer("ido", 15, 1, 60));
        assertEquals(1.5, values.decimal("szorzo", 1.0, 0.0, 10.0), 1.0E-9);
        assertEquals(5_000_000_000L, values.number("nagy", 0, 0, Long.MAX_VALUE));
        assertTrue(values.problems().isEmpty());
    }

    @Test
    void outOfRangeIsClampedAndReported() {
        Values values = values("ido", 500, "szorzo", -3.0);
        assertEquals(60, values.integer("ido", 15, 1, 60));
        assertEquals(0.0, values.decimal("szorzo", 1.0, 0.0, 10.0), 1.0E-9);
        assertEquals(2, values.problems().size());
        assertTrue(values.problems().get(0).startsWith("teszt.yml -> ido:"));
    }

    @Test
    void hugeIntegerDoesNotWrapAround() {
        Values values = values("ido", 99_999_999_999L);
        assertEquals(60, values.integer("ido", 15, 1, 60));
    }

    @Test
    void garbageFallsBack() {
        Values values = values("ido", "tizenot", "szorzo", "sok", "be", "talán");
        assertEquals(15, values.integer("ido", 15, 1, 60));
        assertEquals(1.0, values.decimal("szorzo", 1.0, 0.0, 10.0), 1.0E-9);
        assertTrue(values.bool("be", true));
        assertEquals(3, values.problems().size());
    }

    @Test
    void hungarianBooleans() {
        Values values = values("a", "igen", "b", "nem", "c", "ON", "d", false);
        assertTrue(values.bool("a", false));
        assertFalse(values.bool("b", true));
        assertTrue(values.bool("c", false));
        assertFalse(values.bool("d", true));
        assertTrue(values.problems().isEmpty());
    }

    @Test
    void choiceAcceptsOnlyListedValues() {
        Values values = values("tipus", "MySQL", "masik", "oracle");
        assertEquals("mysql", values.choice("tipus", "sqlite", "sqlite", "mysql"));
        assertEquals("sqlite", values.choice("masik", "sqlite", "sqlite", "mysql"));
        assertEquals(1, values.problems().size());
    }
}
