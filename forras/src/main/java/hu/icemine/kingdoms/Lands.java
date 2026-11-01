package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/** Chunk kulcsok és a /k map szöveges térképe. Szerver nélkül is tesztelhető. */
public final class Lands {

    private Lands() {
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    /** "x;z" formában (a régi mentés is így tárolta). */
    public static String text(long key) {
        return x(key) + ";" + z(key);
    }

    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length != 2) {
            return null;
        }
        try {
            return key(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static long[] neighbours(long key) {
        int cx = x(key);
        int cz = z(key);
        return new long[]{key(cx + 1, cz), key(cx - 1, cz), key(cx, cz + 1), key(cx, cz - 1)};
    }

    /**
     * A szöveges térkép sorai: középen a játékos, észak felfelé. A cell függvény minden chunkhoz egy színes jelet ad
     * (pl. "&a■"), a játékos helyén a you jel látszik.
     */
    public static List<String> render(int cx, int cz, int halfWidth, int halfHeight, LongFunction<String> cell, String you) {
        List<String> lines = new ArrayList<>();
        for (int dz = -halfHeight; dz <= halfHeight; dz++) {
            StringBuilder line = new StringBuilder();
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                line.append(dx == 0 && dz == 0 ? you : cell.apply(key(cx + dx, cz + dz)));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Hány darabban vannak a földek (egymással oldalról érintkező chunkok egy darab). */
    public static int islands(java.util.Set<Long> claims) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int count = 0;
        for (long start : claims) {
            if (!seen.add(start)) {
                continue;
            }
            count++;
            java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                for (long next : neighbours(queue.poll())) {
                    if (claims.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return count;
    }
}
