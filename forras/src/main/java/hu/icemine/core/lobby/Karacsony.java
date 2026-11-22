package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Build;
import hu.icemine.core.build.Placer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

/**
 * Karácsonyi lobby: december 1. és január 6. között nagy karácsonyfa nő ki a tér szökőkútjából, a házak gerendáin
 * színes fényfüzér világít, és havazik. Utána minden magától visszaáll. lobby.yml -> christmas: auto | on | off.
 */
public final class Karacsony {

    static final ZoneId ZONA = ZoneId.of("Europe/Budapest");
    static final String[] LIGHTS = {"ochre_froglight", "verdant_froglight", "pearlescent_froglight", "shroomlight"};
    static final String[] ORNAMENTS = {"red_concrete", "gold_block", "light_blue_concrete", "white_concrete", "red_concrete"};

    private final IceMineCore core;
    private BukkitTask check;
    private BukkitTask snow;
    private UUID felirat;
    private String mode = "auto";

    public Karacsony(IceMineCore core) {
        this.core = core;
    }

    /** Az idő szerint most karácsony van-e (dec. 1. - jan. 6.). */
    static boolean szezon(LocalDate d) {
        return d.getMonthValue() == 12 || (d.getMonthValue() == 1 && d.getDayOfMonth() <= 6);
    }

    boolean aktiv() {
        return switch (mode) {
            case "on" -> true;
            case "off" -> false;
            default -> szezon(LocalDate.now(ZONA));
        };
    }

    public void start() {
        stop();
        YamlConfiguration config = core.configs().get("lobby");
        Object v = config.get("christmas", "auto");
        mode = v instanceof Boolean b ? (b ? "on" : "off") : String.valueOf(v).toLowerCase(Locale.ROOT);
        check = Bukkit.getScheduler().runTaskTimer(core, this::frissit, 40L, 20L * 600);
    }

    public void stop() {
        if (check != null) {
            check.cancel();
            check = null;
        }
        if (snow != null) {
            snow.cancel();
            snow = null;
        }
        feliratTorol();
    }

    void frissit() {
        YamlConfiguration config = core.configs().get("lobby");
        World world = core.lobby().world();
        if (world == null) {
            return;
        }
        boolean kint = config.getBoolean("christmas-applied", false);
        boolean kell = aktiv();
        int cx = config.getInt("center.x", 0);
        int cy = config.getInt("center.y", 100);
        int cz = config.getInt("center.z", 0);
        if (kell && !kint) {
            Map<String, String> overlay = overlay(cx, cy, cz);
            Placer placer = new Placer(world, core.getLogger());
            for (Map.Entry<String, String> e : overlay.entrySet()) {
                int[] p = pos(e.getKey());
                placer.set(p[0], p[1], p[2], e.getValue());
            }
            config.set("christmas-applied", true);
            core.configs().save("lobby");
            core.getLogger().info("Karácsonyi lobby: feldíszítve (" + overlay.size() + " blokk).");
        } else if (!kell && kint) {
            Map<String, String> overlay = overlay(cx, cy, cz);
            Map<String, String> eredeti = new HashMap<>();
            new LobbyBuild((x, y, z, data) -> {
                String k = x + ";" + y + ";" + z;
                if (overlay.containsKey(k)) {
                    eredeti.put(k, data);
                }
            }, cx, cy, cz).build();
            Placer placer = new Placer(world, core.getLogger());
            for (String k : overlay.keySet()) {
                int[] p = pos(k);
                placer.set(p[0], p[1], p[2], eredeti.getOrDefault(k, "air"));
            }
            config.set("christmas-applied", false);
            core.configs().save("lobby");
            core.getLogger().info("Karácsonyi lobby: a díszítés lekerült.");
        }
        if (kell) {
            felirat(world, cx, cy, cz);
            if (snow == null) {
                snow = Bukkit.getScheduler().runTaskTimer(core, () -> havazas(world), 20L, 10L);
            }
        } else {
            feliratTorol();
            if (snow != null) {
                snow.cancel();
                snow = null;
            }
        }
    }

    static int[] pos(String k) {
        String[] p = k.split(";");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    /** A díszítés blokkjai (abszolút "x;y;z" -> blokk). */
    static Map<String, String> overlay(int cx, int cy, int cz) {
        Map<String, String> out = new LinkedHashMap<>();
        new Disz((x, y, z, data) -> out.put(x + ";" + y + ";" + z, data), cx, cy, cz).build();
        return out;
    }

    private void havazas(World world) {
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation().add(0, 7, 0);
            p.spawnParticle(Particle.SNOWFLAKE, l, 18, 9, 2.5, 9, 0.01);
        }
    }

    private void felirat(World world, int cx, int cy, int cz) {
        Entity e = felirat == null ? null : Bukkit.getEntity(felirat);
        if (e != null && e.isValid()) {
            return;
        }
        Location l = new Location(world, cx + 0.5, cy + Disz.TOP + 3.2, cz + LobbyBuild.FOUNTAIN_Z + 0.5);
        if (!world.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            return;
        }
        TextDisplay d = world.spawn(l, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&c&lBoldog karácsonyt! &f❄\n&7az IceMine csapata"));
            t.setShadowed(true);
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(2.5f, 2.5f, 2.5f), new org.joml.AxisAngle4f()));
        });
        felirat = d.getUniqueId();
    }

    private void feliratTorol() {
        Entity e = felirat == null ? null : Bukkit.getEntity(felirat);
        if (e != null) {
            e.remove();
        }
        felirat = null;
    }

    /** A díszítés: fa a szökőkút tetején, fényfüzér a házak felső gerendáin. */
    static final class Disz extends Build {

        static final int BASE = LobbyBuild.PLATFORM + 8;
        static final int TOP = BASE + 14;

        Disz(Sink sink, int cx, int cy, int cz) {
            super(sink, cx, cy, cz);
        }

        @Override
        public void build() {
            int fz = LobbyBuild.FOUNTAIN_Z;
            for (int y = BASE; y < TOP; y++) {
                put(0, y, fz, "spruce_log");
            }
            int coneFrom = BASE + 2;
            for (int y = coneFrom; y < TOP; y++) {
                double r = 4.6 * (1 - (double) (y - coneFrom) / (TOP - coneFrom)) + 0.4;
                int ri = (int) Math.ceil(r);
                for (int x = -ri; x <= ri; x++) {
                    for (int z = -ri; z <= ri; z++) {
                        double d = dist(x, z);
                        if (d > r || (x == 0 && z == 0)) {
                            continue;
                        }
                        String block = "spruce_leaves[persistent=true]";
                        if (d > r - 1.1) {
                            double n = noise(x, y * 3 + z, 91);
                            if (n < 0.17) {
                                block = ORNAMENTS[(int) (noise(x, y + z, 92) * ORNAMENTS.length)];
                            } else if (n < 0.29) {
                                block = LIGHTS[(int) (noise(x + y, z, 93) * LIGHTS.length)];
                            }
                        }
                        put(x, y, fz + z, block);
                    }
                }
            }
            put(0, TOP, fz, "gold_block");
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                put(d[0], TOP, fz + d[1], "gold_block");
            }
            put(0, TOP + 1, fz, "gold_block");
            put(0, TOP + 2, fz, "end_rod[facing=up]");
            for (LobbyBuild.House h : LobbyBuild.HOUSES) {
                int x0 = h.x - h.sx / 2;
                int x1 = x0 + h.sx - 1;
                int z0 = h.z - h.sz / 2;
                int z1 = z0 + h.sz - 1;
                int top = h.floors * 4;
                int i = 0;
                for (int x = x0 + 1; x < x1; x++) {
                    put(x, top, z0, LIGHTS[i++ % LIGHTS.length]);
                    put(x, top, z1, LIGHTS[i % LIGHTS.length]);
                }
                for (int z = z0 + 1; z < z1; z++) {
                    put(x0, top, z, LIGHTS[i++ % LIGHTS.length]);
                    put(x1, top, z, LIGHTS[i % LIGHTS.length]);
                }
            }
        }
    }
}
