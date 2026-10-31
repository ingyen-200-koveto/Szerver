package hu.icemine.core.lobby;

public final class LaunchMath {

    public static final double GRAVITY = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    public static final double AIR = 0.91;
    public static final double FIRST_TICK = 0.546;
    public static final double MAX_HORIZONTAL = 6.0;
    public static final double MAX_VERTICAL = 3.0;

    private LaunchMath() {
    }

    public static int ticks(double distance, double dy) {
        return (int) Math.max(16, Math.min(45, Math.round(12 + distance * 0.75 + Math.max(0, dy) * 0.8)));
    }

    public static double[] velocity(double dx, double dy, double dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        int ticks = ticks(distance, dy);
        double reach = 1 + FIRST_TICK * (1 - Math.pow(AIR, ticks - 1)) / (1 - AIR);
        double horizontal = Math.min(MAX_HORIZONTAL, distance / reach);
        double sum = 0;
        double fall = 0;
        double scale = 1;
        double gravity = 0;
        for (int t = 0; t < ticks; t++) {
            sum += scale;
            fall += gravity;
            scale *= VERTICAL_DRAG;
            gravity = (gravity - GRAVITY) * VERTICAL_DRAG;
        }
        double vertical = Math.max(-MAX_VERTICAL, Math.min(MAX_VERTICAL, (dy - fall) / sum));
        if (distance < 1.0E-6) {
            return new double[]{0, vertical, 0};
        }
        return new double[]{dx / distance * horizontal, vertical, dz / distance * horizontal};
    }

    public static double[] simulate(double[] velocity, int ticks) {
        double x = 0;
        double y = 0;
        double z = 0;
        double vx = velocity[0];
        double vy = velocity[1];
        double vz = velocity[2];
        for (int t = 0; t < ticks; t++) {
            x += vx;
            y += vy;
            z += vz;
            double friction = t == 0 ? FIRST_TICK : AIR;
            vx *= friction;
            vz *= friction;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
        }
        return new double[]{x, y, z};
    }
}
