package hu.icemine.core.parkour;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CourseGen {

    public static final int MIN_X = -26;
    public static final int MAX_X = 40;
    public static final int MIN_Z = 11;
    public static final int MAX_Z = 86;
    public static final int MAX_Y = 42;

    private CourseGen() {
    }

    public static List<int[]> generate(int[] first, int count, long seed) {
        for (long attempt = 0; attempt < 500; attempt++) {
            List<int[]> course = tryGenerate(first, count, new Random(seed + attempt * 7919L));
            if (course != null) {
                return course;
            }
        }
        throw new IllegalStateException("Nem sikerült parkour pályát generálni");
    }

    private static List<int[]> tryGenerate(int[] first, int count, Random random) {
        List<int[]> course = new ArrayList<>();
        course.add(first.clone());
        double heading = Math.PI / 2;
        while (course.size() < count) {
            int[] last = course.get(course.size() - 1);
            int[] next = null;
            for (int tries = 0; tries < 80 && next == null; tries++) {
                double turn = tries < 40 ? (random.nextDouble() - 0.5) * 1.3 : (random.nextDouble() - 0.5) * Math.PI * 2;
                double angle = heading + turn;
                int dy = last[1] >= MAX_Y ? 0 : (random.nextDouble() < 0.5 ? 1 : 0);
                double distance = dy == 1 ? 2.6 + random.nextDouble() * 0.9 : 2.9 + random.nextDouble() * 1.1;
                int[] candidate = {
                        last[0] + (int) Math.round(Math.cos(angle) * distance),
                        last[1] + dy,
                        last[2] + (int) Math.round(Math.sin(angle) * distance)
                };
                if (valid(course, candidate)) {
                    next = candidate;
                    heading = Math.atan2(candidate[2] - last[2], candidate[0] - last[0]);
                }
            }
            if (next == null) {
                return null;
            }
            course.add(next);
        }
        return course;
    }

    public static boolean jumpable(int[] a, int[] b) {
        double horizontal = Math.hypot(a[0] - b[0], a[2] - b[2]);
        int dy = b[1] - a[1];
        if (dy > 1 || dy < -2) {
            return false;
        }
        double max = dy == 1 ? 3.7 : 4.3;
        return horizontal >= 2.0 && horizontal <= max;
    }

    static boolean valid(List<int[]> course, int[] candidate) {
        if (candidate[0] < MIN_X || candidate[0] > MAX_X || candidate[2] < MIN_Z || candidate[2] > MAX_Z) {
            return false;
        }
        int[] last = course.get(course.size() - 1);
        if (!jumpable(last, candidate)) {
            return false;
        }
        for (int i = 0; i < course.size() - 1; i++) {
            int[] other = course.get(i);
            double horizontal = Math.hypot(other[0] - candidate[0], other[2] - candidate[2]);
            int dy = Math.abs(other[1] - candidate[1]);
            if (horizontal < 3.0 && dy < 5) {
                return false;
            }
        }
        return true;
    }
}
