package hu.icemine.core.lobby;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class LobbyClicksTest {

    private static LobbyClicks.Target box(double x, double y, double z) {
        return new LobbyClicks.Target(new BoundingBox(x - 0.6, y, z - 0.6, x + 0.6, y + 3, z + 0.6), player -> { });
    }

    @Test
    void picksTheNearestLabelInFront() {
        LobbyClicks.Target near = box(0, 0, 3);
        LobbyClicks.Target far = box(0, 0, 4.2);
        Vector eye = new Vector(0, 1.6, 0);
        Vector south = new Vector(0, 0, 1);
        assertTrue(near == LobbyClicks.nearest(eye, south, List.of(far, near), LobbyClicks.REACH));
        assertNull(LobbyClicks.nearest(eye, new Vector(0, 0, -1), List.of(far, near), LobbyClicks.REACH), "a háta mögött lévőre nem kattint");
        assertNull(LobbyClicks.nearest(eye, south, List.of(box(0, 0, 8)), LobbyClicks.REACH), "túl messze van");
        assertNull(LobbyClicks.nearest(eye, south, List.of(near), 1.5), "a kattintott blokk közelebb van");
    }

    @Test
    void looksUpAtTheNameAboveTheHead() {
        LobbyClicks.Target npc = box(0, 0, 2.5);
        Vector eye = new Vector(0, 1.6, 0);
        Vector up = new Vector(0, 1.0, 2.5).normalize();
        assertTrue(npc == LobbyClicks.nearest(eye, up, List.of(npc), LobbyClicks.REACH), "a név a feje fölött van, arra is lehet kattintani");
    }
}
