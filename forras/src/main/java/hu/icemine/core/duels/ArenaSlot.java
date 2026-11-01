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
        Layout.Point redPoint = layout.points.get("spawn:red");
        Layout.Point bluePoint = layout.points.get("spawn:blue");
        this.red = new Location(world, i + redPoint.x, j + redPoint.y, k + redPoint.z, (float)redPoint.yaw, 0.0F);
        this.blue = new Location(world, i + bluePoint.x, j + bluePoint.y, k + bluePoint.z, (float)bluePoint.yaw, 0.0F);
        this.bounds = this.area(layout.areas.get("bounds"));
        this.goalRed = this.area(layout.areas.get("goal:red"));
        this.goalBlue = this.area(layout.areas.get("goal:blue"));
        Layout.Point fall = layout.points.get("fall");
        this.fallY = j + (fall == null ? -10.0 : fall.y);
    }

    private int[] area(Layout.Area area) {
        return area == null
            ? null
            : new int[]{
                this.x + area.x1,
                this.y + area.y1,
                this.z + area.z1,
                this.x + area.x2,
                this.y + area.y2,
                this.z + area.z2
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
