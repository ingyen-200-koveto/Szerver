package hu.icemine.core.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Layout {

    public static final class Point {
        public final double x;
        public final double y;
        public final double z;
        public final double yaw;

        public Point(double x, double y, double z, double yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    public static final class Area {
        public final int x1;
        public final int y1;
        public final int z1;
        public final int x2;
        public final int y2;
        public final int z2;

        public Area(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }
    }

    public final Map<String, Point> points = new LinkedHashMap<>();
    public final Map<String, Area> areas = new LinkedHashMap<>();
    public final Map<String, List<Point>> lists = new LinkedHashMap<>();

    public Layout point(String id, double x, double y, double z, double yaw) {
        points.put(id, new Point(x, y, z, yaw));
        return this;
    }

    public Layout area(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        areas.put(id, new Area(x1, y1, z1, x2, y2, z2));
        return this;
    }

    public Layout add(String list, double x, double y, double z, double yaw) {
        lists.computeIfAbsent(list, key -> new ArrayList<>()).add(new Point(x, y, z, yaw));
        return this;
    }
}
