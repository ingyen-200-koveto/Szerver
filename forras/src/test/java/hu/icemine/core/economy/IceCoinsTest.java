package hu.icemine.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IceCoinsTest {

    @Test
    void readsTheWebsiteAnswer() {
        Map<String, Long> coins = IceCoins.parse("{\"players\":{\"steve\":{\"coins\":1234,\"rank\":\"VIP\"},\"alex_2\":{\"coins\":0,\"rank\":\"default\"}}}");
        assertEquals(2, coins.size());
        assertEquals(1234L, coins.get("steve"));
        assertEquals(0L, coins.get("alex_2"));
        assertTrue(IceCoins.parse("{\"players\":{}}").isEmpty());
        assertTrue(IceCoins.parse("{\"error\":\"Hibás kulcs.\"}").isEmpty());
        assertTrue(IceCoins.parse(null).isEmpty());
    }

    @Test
    void readsTheRanks() {
        Map<String, String> ranks = IceCoins.parseRanks("{\"players\":{\"steve\":{\"coins\":1234,\"rank\":\"IceKing\"},\"alex_2\":{\"coins\":0,\"rank\":\"default\"}}}");
        assertEquals("IceKing", ranks.get("steve"));
        assertEquals("default", ranks.get("alex_2"));
        assertTrue(IceCoins.parseRanks("{\"players\":{}}").isEmpty());
    }
}
