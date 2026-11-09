package hu.icemine.core.parkour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hu.icemine.core.lobby.LobbyBuild;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseTest {

    @Test
    void everyJumpIsPossibleAndTheCourseHas56Blocks() {
        LobbyBuild build = new LobbyBuild((x, y, z, d) -> { }, 0, 0, 0);
        build.build();
        List<int[]> course = build.course();
        assertEquals(56, course.size());
        int top = 0;
        for (int i = 1; i < course.size(); i++) {
            assertTrue(CourseGen.jumpable(course.get(i - 1), course.get(i)), "rossz ugrás " + i);
            top = Math.max(top, course.get(i)[1]);
        }
        assertTrue(top >= 20, "magasság: " + top);
        for (int i = 0; i < course.size(); i++) {
            for (int j = i + 2; j < course.size(); j++) {
                int[] a = course.get(i);
                int[] b = course.get(j);
                assertTrue(Math.hypot(a[0] - b[0], a[2] - b[2]) >= 3.0 || Math.abs(a[1] - b[1]) >= 5, "túl közel: " + i + " " + j);
            }
        }
    }

    @Test
    void theSameCourseEveryTime() {
        List<int[]> a = CourseGen.generate(new int[]{0, 5, 10}, 56, 56L);
        List<int[]> b = CourseGen.generate(new int[]{0, 5, 10}, 56, 56L);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i)[0], b.get(i)[0]);
            assertEquals(a.get(i)[1], b.get(i)[1]);
            assertEquals(a.get(i)[2], b.get(i)[2]);
        }
    }
}
