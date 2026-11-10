package hu.icemine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebLoginTest {

    @Test
    void bedrockNamesLoseTheirPrefix() {
        assertEquals("steve", WebLogin.webName(".Steve"));
        assertEquals("alex_12", WebLogin.webName("Alex_12"));
    }

    @Test
    void passwordsAreEscapedInJson() {
        assertEquals("\"a\\\"b\\\\c\"", WebLogin.json("a\"b\\c"));
        assertEquals("\"\\u0001\"", WebLogin.json("\u0001"));
    }

    @Test
    void loginAndRegisterAliases() {
        assertTrue(WebLogin.LOGIN.contains("l"));
        assertTrue(WebLogin.REGISTER.contains("reg"));
    }
}
