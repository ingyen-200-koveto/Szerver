package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViaFixTest {

    @Test
    void turnsOffOnlyThatOption() {
        String in = "# x\nfix-1_21-placement-rotation: true\nother: true\n";
        assertEquals("# x\nfix-1_21-placement-rotation: false\nother: true\n", ViaFix.patch(in));
        assertEquals("fix-1_21-placement-rotation: false\n", ViaFix.patch("fix-1_21-placement-rotation: false\n"));
    }
}
