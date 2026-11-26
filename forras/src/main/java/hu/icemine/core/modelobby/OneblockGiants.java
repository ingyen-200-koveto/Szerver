package hu.icemine.core.modelobby;

/**
 * A OneBlock lobby: a szigetet körbeveszik a OneBlock fázisok óriás blokkjai felhőkön (fű, tölgyfa, kő ércekkel,
 * hó, nether, óceán, End), középen a tér fölött lebeg "az egy blokk".
 */
final class OneblockGiants extends ThemeBase {

    OneblockGiants(Sink sink, int cx, int cy, int cz, Free free) {
        super(sink, cx, cy, cz, free);
    }

    @Override
    public void build() {
        String[] kinds = {"grass", "log", "stone", "snow", "nether", "ocean", "end"};
        for (int i = 0; i < kinds.length; i++) {
            double a = Math.PI * 2 * i / kinds.length - Math.PI / 2 + Math.PI / kinds.length;
            int gx = (int) Math.round(Math.cos(a) * 40);
            int gz = (int) Math.round(Math.sin(a) * 40);
            int gy = 9 + (int) Math.round(Math.sin(i * 2.1) * 4);
            cloud(gx, gy - 5, gz, 6.5);
            giant(gx, gy, gz, 4, kinds[i]);
        }
        giant(0, 22, -20, 1, "grass");
    }

    /** Óriás blokk: (2*h+1) élű kocka, a blokk "pixeleivel". */
    private void giant(int gx, int gy, int gz, int h, String kind) {
        for (int dx = -h; dx <= h; dx++) {
            for (int dy = -h; dy <= h; dy++) {
                for (int dz = -h; dz <= h; dz++) {
                    boolean shell = Math.abs(dx) == h || Math.abs(dy) == h || Math.abs(dz) == h;
                    int x = gx + dx;
                    int y = gy + dy;
                    int z = gz + dz;
                    if (!shell) {
                        put(x, y, z, "stone");
                        continue;
                    }
                    put(x, y, z, pixel(kind, dx, dy, dz, h, x, y, z));
                }
            }
        }
    }

    private String pixel(String kind, int dx, int dy, int dz, int h, int x, int y, int z) {
        double n = noise(x * 3 + dy, z * 5 - dy, 580);
        boolean top = dy == h;
        boolean side = !top && dy != -h;
        int fromTop = h - dy;
        switch (kind) {
            case "grass":
                if (top) {
                    return n < 0.3 ? "moss_block" : (n < 0.45 ? "green_concrete" : "grass_block");
                }
                if (side && (fromTop == 0 || fromTop == 1 && n < 0.45)) {
                    return n < 0.5 ? "moss_block" : "green_concrete_powder";
                }
                return n < 0.25 ? "coarse_dirt" : (n < 0.35 ? "rooted_dirt" : "dirt");
            case "log":
                if (top || dy == -h) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    return ring == h ? "oak_log[axis=y]" : (ring % 2 == 0 ? "stripped_oak_wood" : "oak_planks");
                }
                return n < 0.3 ? "stripped_oak_log[axis=y]" : "oak_log[axis=y]";
            case "stone":
                if (n < 0.08) {
                    return "diamond_ore";
                }
                if (n < 0.18) {
                    return "iron_ore";
                }
                if (n < 0.3) {
                    return "coal_ore";
                }
                if (n < 0.34) {
                    return "gold_ore";
                }
                return n < 0.6 ? "stone" : "andesite";
            case "snow":
                if (top) {
                    return n < 0.2 ? "powder_snow" : "snow_block";
                }
                if (side && fromTop <= 1) {
                    return "snow_block";
                }
                return n < 0.5 ? "packed_ice" : "dirt";
            case "nether":
                if (n < 0.12) {
                    return "nether_quartz_ore";
                }
                if (n < 0.18) {
                    return "nether_gold_ore";
                }
                if (top) {
                    return n < 0.6 ? "crimson_nylium" : "netherrack";
                }
                return n < 0.8 ? "netherrack" : "magma_block";
            case "ocean":
                if (n < 0.12) {
                    return "sea_lantern";
                }
                return n < 0.5 ? "prismarine" : (n < 0.8 ? "prismarine_bricks" : "dark_prismarine");
            default:
                if (n < 0.1) {
                    return "purpur_block";
                }
                return n < 0.7 ? "end_stone" : "end_stone_bricks";
        }
    }

    private void cloud(int cx, int cy, int cz, double r) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 4.0 + dz * dz);
                    if (d <= r - noise(cx + dx, cz + dz + dy * 3, 581) * 1.6) {
                        put(cx + dx, cy + dy, cz + dz, noise(cx + dx, cy + dy + cz + dz, 582) < 0.6 ? "white_wool" : "white_concrete");
                    }
                }
            }
        }
    }
}
