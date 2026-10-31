package hu.icemine.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SecondsTest {

    @Test
    void oneDecimalWithSuffix() {
        assertEquals("29.4s", BoardService.seconds(29.43));
        assertEquals("4.5s", BoardService.seconds(4.45 + 1.0E-9));
    }

    @Test
    void neverNegative() {
        assertEquals("0.0s", BoardService.seconds(-3));
        assertEquals("0.0s", BoardService.seconds(0));
    }
}
