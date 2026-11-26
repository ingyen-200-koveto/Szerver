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
            scene(kinds[i], gx, gy + 5, gz);
        }
        giant(0, 22, -20, 1, "grass");
        starter(-17, 15);
        lone(17, 15);
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

    /** A klasszikus kezdő sziget kicsiben: föld, fa, láda. */
    private void starter(int x, int z) {
        if (!free.test(x, z)) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    put(x + dx, y, z + dz, y == 3 ? "grass_block" : "dirt");
                }
            }
        }
        for (int y = 4; y <= 7; y++) {
            put(x - 1, y, z - 1, "oak_log[axis=y]");
        }
        blobLeaves(x - 1, 8, z - 1, 2.4, "oak_leaves[persistent=true]");
        put(x + 1, 4, z + 1, "chest[facing=south]");
        put(x + 1, 4, z - 1, "short_grass");
    }

    /** Az az egy blokk: egy fűblokk egy vékony bedrock oszlop tetején. */
    private void lone(int x, int z) {
        if (!free.test(x, z)) {
            return;
        }
        for (int y = 1; y <= 4; y++) {
            put(x, y, z, y == 4 ? "grass_block" : "bedrock");
        }
        put(x, 5, z, "short_grass");
        put(x + 1, 1, z, "stone_brick_slab[type=bottom]");
        put(x - 1, 1, z, "stone_brick_slab[type=bottom]");
        put(x, 1, z + 1, "stone_brick_slab[type=bottom]");
        put(x, 1, z - 1, "stone_brick_slab[type=bottom]");
    }

    /** Kis jelenet az óriás blokk tetején, ami a fázisra jellemző. */
    private void scene(String kind, int x, int y, int z) {
        switch (kind) {
            case "grass":
                for (int dy = 0; dy < 4; dy++) {
                    put(x - 1, y + dy, z - 1, "oak_log[axis=y]");
                }
                blobLeaves(x - 1, y + 4, z - 1, 2.6, "oak_leaves[persistent=true]");
                put(x + 2, y, z + 1, "poppy");
                put(x + 1, y, z + 2, "dandelion");
                put(x + 2, y, z - 2, "short_grass");
                put(x - 2, y, z + 2, "cornflower");
                break;
            case "log":
                put(x, y, z, "crafting_table");
                put(x + 1, y, z, "chest[facing=south]");
                put(x - 2, y, z + 1, "oak_log[axis=x]");
                put(x - 1, y, z + 1, "oak_log[axis=x]");
                put(x - 2, y + 1, z + 1, "oak_log[axis=x]");
                put(x + 2, y, z - 2, "oak_sapling");
                put(x - 2, y, z - 2, "birch_sapling");
                put(x + 2, y, z + 2, "spruce_sapling");
                break;
            case "stone":
                put(x, y, z, "furnace[facing=south,lit=true]");
                put(x + 1, y, z, "blast_furnace[facing=south,lit=true]");
                put(x - 2, y, z - 1, "iron_ore");
                put(x - 2, y + 1, z - 1, "coal_ore");
                put(x - 1, y, z - 2, "diamond_ore");
                put(x + 2, y, z + 2, "pointed_dripstone[vertical_direction=up,thickness=tip]");
                put(x - 2, y, z + 2, "lantern[hanging=false]");
                break;
            case "snow":
                put(x, y, z, "snow_block");
                put(x, y + 1, z, "snow_block");
                put(x, y + 2, z, "carved_pumpkin[facing=south]");
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if ((dx != 0 || dz != 0) && noise(x + dx, z + dz, 590) < 0.5) {
                            put(x + dx, y, z + dz, "snow[layers=" + (1 + (int) (noise(x + dz, z + dx, 591) * 3)) + "]");
                        }
                    }
                }
                put(x + 2, y, z - 2, "spruce_sapling");
                break;
            case "nether":
                for (int dx = -2; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 4; dy++) {
                        boolean frame = dx == -2 || dx == 1 || dy == 0 || dy == 4;
                        put(x + dx, y + dy, z, frame ? "obsidian" : "nether_portal[axis=x]");
                    }
                }
                put(x + 2, y, z + 2, "fire");
                put(x - 3, y, z + 2, "crimson_fungus");
                put(x + 3, y, z - 2, "nether_wart[age=3]");
                break;
            case "ocean":
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        put(x + dx, y - 1, z + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 3 ? "prismarine_bricks" : "water");
                    }
                }
                put(x, y - 1, z, "sea_lantern");
                put(x - 1, y - 1, z + 1, "sea_pickle[pickles=3,waterlogged=true]");
                put(x + 1, y - 1, z - 1, "brain_coral[waterlogged=true]");
                put(x + 2, y - 1, z + 1, "tube_coral[waterlogged=true]");
                break;
            default:
                String[] facing = {"south", "west", "north", "east"};
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == 2 && !(Math.abs(dx) == 2 && Math.abs(dz) == 2);
                        boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                        if (inner) {
                            put(x + dx, y - 1, z + dz, "end_portal");
                        } else if (ring) {
                            String f = dz == -2 ? facing[0] : (dx == 2 ? facing[1] : (dz == 2 ? facing[2] : facing[3]));
                            put(x + dx, y - 1, z + dz, "end_portal_frame[eye=true,facing=" + f + "]");
                        }
                    }
                }
                put(x + 3, y, z + 3, "end_rod[facing=up]");
                put(x - 3, y, z - 3, "chorus_flower[age=5]");
        }
    }

    private void blobLeaves(int bx, int by, int bz, double r, String leaves) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz) <= r) {
                        put(bx + dx, by + dy, bz + dz, leaves);
                    }
                }
            }
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
