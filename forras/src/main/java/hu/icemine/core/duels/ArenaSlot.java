package hu.icemine.core.duels;

import hu.icemine.core.build.Layout;
import org.bukkit.Location;
import org.bukkit.World;

public final class ArenaSlot {
    public final String id;
    public final String type;
    public final String theme;
    public final World world;
    public final int x;
    public final int y;
    public final int z;
    public final Location red;
    public final Location blue;
    public final int[] bounds;
    public final int[] goalRed;
    public final int[] goalBlue;
    public final double fallY;
    Match match;

    ArenaSlot(String s, String s1, String s2, World world, int i, int j, int k, Layout layout) {
        this.id = s;
        this.type = s1;
        this.theme = s2;
        this.world = world;
        this.x = i;
        this.y = j;
        this.z = k;
        Layout.Point layout$point = layout.points.get("spawn:red");
        Layout.Point layout$point1 = layout.points.get("spawn:blue");
        this.red = new Location(world, i + layout$point.x, j + layout$point.y, k + layout$point.z, (float)layout$point.yaw, 0.0F);
        this.blue = new Location(world, i + layout$point1.x, j + layout$point1.y, k + layout$point1.z, (float)layout$point1.yaw, 0.0F);
        this.bounds = this.area(layout.areas.get("bounds"));
        this.goalRed = this.area(layout.areas.get("goal:red"));
        this.goalBlue = this.area(layout.areas.get("goal:blue"));
        Layout.Point layout$point2 = layout.points.get("fall");
        this.fallY = j + (layout$point2 == null ? -10.0 : layout$point2.y);
    }

    private int[] area(Layout.Area layout$area) {
        return layout$area == null
            ? null
            : new int[]{
                this.x + layout$area.x1,
                this.y + layout$area.y1,
                this.z + layout$area.z1,
                this.x + layout$area.x2,
                this.y + layout$area.y2,
                this.z + layout$area.z2
            };
    }

    public boolean free() {
        return this.match == null;
    }

    static boolean inside(int[] aint, Location location) {
        return aint == null
            ? false
            : location.getX() >= aint[0]
                && location.getX() < aint[3] + 1
                && location.getY() >= aint[1]
                && location.getY() < aint[4] + 1
                && location.getZ() >= aint[2]
                && location.getZ() < aint[5] + 1;
    }

    public boolean contains(Location location) {
        return location.getWorld() == this.world && inside(this.bounds, location);
    }
}
