package hu.icemine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatLogTest {

    @Test
    void passwordsAreHidden() {
        assertEquals("/login ****", ChatLog.mask("/login titkos123"));
        assertEquals("/REGISTER ****", ChatLog.mask("/REGISTER a b"));
        assertEquals("/changepassword ****", ChatLog.mask("/changepassword regi uj"));
        assertEquals("/login", ChatLog.mask("/login"));
        assertEquals("/msg Peti szia", ChatLog.mask("/msg Peti szia"));
        assertEquals("/plot home 2", ChatLog.mask("/plot home 2"));
    }
}
