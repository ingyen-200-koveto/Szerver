package hu.icemine.core.parkour;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.lobby.LobbyBuild;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseTest {

    @Test
    void everyJumpIsPossibleAndTheCourseIsLonger() {
        LobbyBuild build = new LobbyBuild((x, y, z, d) -> { }, 0, 0, 0);
        build.build();
        List<int[]> course = build.course();
        assertTrue(course.size() >= 60, "hossz: " + course.size());
        int top = 0;
        for (int i = 1; i < course.size(); i++) {
            int[] a = course.get(i - 1);
            int[] b = course.get(i);
            double horizontal = Math.hypot(a[0] - b[0], a[2] - b[2]);
            int dy = b[1] - a[1];
            assertTrue(dy <= 1, "túl magas ugrás " + i);
            assertTrue(horizontal <= 7.2, "túl messzi ugrás " + i + " " + horizontal);
            top = Math.max(top, b[1]);
        }
        assertTrue(top >= 30, "magasság: " + top);
    }
}
