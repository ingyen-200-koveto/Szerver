package hu.icemine.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReportCommandsTest {

    @Test
    void escapesJson() {
        assertEquals("\"Steve\"", ReportCommands.q("Steve"));
        assertEquals("\"a \\\"b\\\" \\\\ c\\nd\"", ReportCommands.q("a \"b\" \\ c\nd"));
        assertEquals("\"\\u0001x\"", ReportCommands.q("\u0001x"));
    }
}
