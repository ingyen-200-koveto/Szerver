package hu.icemine.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class Locs {

    private Locs() {
    }

    public static Location read(ConfigurationSection section, World world) {
        if (section == null || world == null || !section.contains("x")) {
            return null;
        }
        return new Location(world, section.getDouble("x", 0), section.getDouble("y", 0), section.getDouble("z", 0),
                (float) section.getDouble("yaw", 0), (float) section.getDouble("pitch", 0));
    }

    public static void write(ConfigurationSection section, String path, Location location) {
        section.set(path + ".x", round(location.getX()));
        section.set(path + ".y", round(location.getY()));
        section.set(path + ".z", round(location.getZ()));
        section.set(path + ".yaw", round(location.getYaw()));
        section.set(path + ".pitch", round(location.getPitch()));
    }

    public static void write(ConfigurationSection section, String path, double x, double y, double z, double yaw) {
        section.set(path + ".x", round(x));
        section.set(path + ".y", round(y));
        section.set(path + ".z", round(z));
        section.set(path + ".yaw", round(yaw));
        section.set(path + ".pitch", 0.0);
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static boolean sameBlock(Location a, int x, int y, int z) {
        return a.getBlockX() == x && a.getBlockY() == y && a.getBlockZ() == z;
    }

    public static final class Box {
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;

        public Box(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean contains(Location location) {
            return contains(location.getX(), location.getY(), location.getZ());
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }

        public double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }

        public static Box read(ConfigurationSection section) {
            if (section == null || !section.contains("min.x") || !section.contains("max.x")) {
                return null;
            }
            return new Box(section.getInt("min.x"), section.getInt("min.y"), section.getInt("min.z"),
                    section.getInt("max.x"), section.getInt("max.y"), section.getInt("max.z"));
        }

        public void write(ConfigurationSection section, String path) {
            section.set(path + ".min.x", minX);
            section.set(path + ".min.y", minY);
            section.set(path + ".min.z", minZ);
            section.set(path + ".max.x", maxX);
            section.set(path + ".max.y", maxY);
            section.set(path + ".max.z", maxZ);
        }
    }
}
