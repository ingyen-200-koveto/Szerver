package hu.icemine.core.lobby;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LobbyFillTest {

    @Test
    void districtsAreBuilt() {
        Map<String, String> blocks = new HashMap<>();
        new LobbyBuild((x, y, z, data) -> blocks.put(x + "," + y + "," + z, data), 0, 100, 0).build();
        long wool = blocks.values().stream().filter(v -> v.equals("white_wool")).count();
        long crops = blocks.values().stream().filter(v -> v.startsWith("wheat") || v.startsWith("carrots") || v.startsWith("potatoes")).count();
        long stalls = blocks.values().stream().filter(v -> v.equals("yellow_wool") || v.equals("lime_wool")).count();
        long anvils = blocks.values().stream().filter(v -> v.startsWith("blast_furnace")).count();
        long rink = blocks.values().stream().filter(v -> v.equals("ice")).count();
        long spires = blocks.values().stream().filter(v -> v.equals("blue_ice")).count();
        System.out.println("rink=" + rink + " blueice=" + spires);
        assertTrue(rink > 100, "korcsolyapálya");
        long beehive = blocks.values().stream().filter(v -> v.startsWith("beehive")).count();
        System.out.println("wool=" + wool + " crops=" + crops + " stalls=" + stalls + " forge=" + anvils + " beehive=" + beehive);
        assertTrue(wool > 0, "szélmalom");
        assertTrue(crops > 50, "földek");
        assertTrue(stalls > 0, "piac");
        assertTrue(anvils > 0, "kovács");
        assertTrue(beehive > 0, "tanya");
    }
}
