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

    @Test
    void readsTheSkins() {
        Map<String, long[]> skins = IceCoins.parseSkins("{\"players\":{\"steve\":{\"coins\":12,\"rank\":\"Bronz\",\"skin\":1795264392815,\"slim\":true},\"alex_2\":{\"coins\":0,\"rank\":\"default\",\"skin\":1700000000000,\"slim\":false},\"bob\":{\"coins\":5,\"rank\":\"default\",\"skin\":0}}}");
        assertEquals(3, skins.size());
        assertEquals(1795264392815L, skins.get("steve")[0]);
        assertEquals(1L, skins.get("steve")[1]);
        assertEquals(0L, skins.get("alex_2")[1]);
        assertEquals(0L, skins.get("bob")[0]);
        assertTrue(IceCoins.parseSkins("{\"players\":{\"x\":{\"coins\":1,\"rank\":\"default\"}}}").isEmpty());
        assertTrue(IceCoins.parseSkins(null).isEmpty());
    }
}
