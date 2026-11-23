package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Hóvihar a lobbyban: időnként (alapból 40 percenként) 3 percre sűrű havazás jön,
 * és varázs hópelyhek jelennek meg a földön. Aki felveszi (odasétál), Kristályt kap.
 */
public final class Hovihar {

    private static final String TAG = "icemine_hopehely";

    private final IceMineCore core;
    private final List<ItemDisplay> pelyhek = new ArrayList<>();
    private final Map<UUID, Integer> gyujtott = new HashMap<>();
    private BukkitTask figyelo;
    private BukkitTask vihar;
    private boolean be = true;
    private int perc = 40;
    private int hossz = 180;
    private int darab = 30;
    private long jutalom = 5;
    private long kovetkezo;
    private long vege;
    private int cx;
    private int cz;
    private double sugar = 75;

    public Hovihar(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config) {
        be = config.getBoolean("snowstorm.enabled", true);
        perc = Math.max(5, config.getInt("snowstorm.every-minutes", 40));
        hossz = Math.max(30, config.getInt("snowstorm.seconds", 180));
        darab = Math.max(5, Math.min(80, config.getInt("snowstorm.flakes", 30)));
        jutalom = Math.max(0, config.getLong("snowstorm.reward", 5));
        cx = config.getInt("center.x", 0);
        cz = config.getInt("center.z", 0);
        stop();
        if (!be) {
            return;
        }
        kovetkezo = System.currentTimeMillis() + perc * 60_000L;
        figyelo = Bukkit.getScheduler().runTaskTimer(core, this::figyel, 200L, 200L);
    }

    public void stop() {
        if (figyelo != null) {
            figyelo.cancel();
            figyelo = null;
        }
        befejez(false);
    }

    public boolean aktiv() {
        return vihar != null;
    }

    private void figyel() {
        World world = core.lobby().world();
        if (world == null || vihar != null || System.currentTimeMillis() < kovetkezo || world.getPlayers().isEmpty()) {
            return;
        }
        indit();
    }

    /** Admin parancsból is indítható. */
    public boolean indit() {
        World world = core.lobby().world();
        if (world == null || vihar != null) {
            return false;
        }
        takarit(world);
        gyujtott.clear();
        vege = System.currentTimeMillis() + hossz * 1000L;
        for (int i = 0; i < darab; i++) {
            ujPehely(world);
        }
        for (Player p : world.getPlayers()) {
            p.sendTitle(Text.color("&b&l❄ HÓVIHAR ❄"), Text.color("&7Gyűjtsd a varázs hópelyheket!"), 10, 60, 15);
            p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.6F, 0.6F);
        }
        String msg = Text.color("&b[IceMine] &fHóvihar a lobbyban! &7" + hossz / 60 + " percig varázs hópelyhek hullanak, sétálj oda és szedd fel őket.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() != world) {
                p.sendMessage(msg);
            }
        }
        vihar = Bukkit.getScheduler().runTaskTimer(core, () -> tick(world), 5L, 5L);
        return true;
    }

    private int tickek;

    private void tick(World world) {
        tickek++;
        if (System.currentTimeMillis() >= vege) {
            befejez(true);
            return;
        }
        if (tickek % 2 == 0) {
            for (Player p : world.getPlayers()) {
                Location l = p.getLocation().add(0, 6, 0);
                p.spawnParticle(Particle.SNOWFLAKE, l, 60, 10, 4, 10, 0.02);
                p.spawnParticle(Particle.WHITE_ASH, l, 30, 10, 4, 10, 0.0);
            }
        }
        Iterator<ItemDisplay> it = pelyhek.iterator();
        int pot = 0;
        while (it.hasNext()) {
            ItemDisplay d = it.next();
            if (!d.isValid()) {
                it.remove();
                pot++;
                continue;
            }
            Location at = d.getLocation();
            double phase = tickek * 0.45 + (d.getEntityId() % 17);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(5);
            d.setTransformation(new Transformation(new Vector3f(0F, (float) (Math.sin(phase) * 0.22), 0F), new AxisAngle4f(),
                    new Vector3f(0.7F, 0.7F, 0.7F), new AxisAngle4f()));
            world.spawnParticle(Particle.END_ROD, at, 1, 0.15, 0.15, 0.15, 0.0);
            for (Player p : world.getPlayers()) {
                if (p.getLocation().add(0, 0.9, 0).distanceSquared(at) <= 2.6) {
                    felvesz(p, d);
                    it.remove();
                    pot++;
                    break;
                }
            }
        }
        for (int i = 0; i < pot; i++) {
            ujPehely(world);
        }
    }

    private void felvesz(Player p, ItemDisplay d) {
        Location at = d.getLocation();
        d.remove();
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 20, 0.3, 0.3, 0.3, 0.05);
        p.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1F, 1.6F);
        int db = gyujtott.merge(p.getUniqueId(), 1, Integer::sum);
        if (jutalom > 0) {
            core.economy().deposit(p, core.economy().defaultCurrency(), jutalom, "hópehely");
        }
        PlayerProfile profile = core.profiles().profile(p);
        if (profile != null) {
            profile.addStat("lobby.snowflakes", 1);
        }
        p.sendActionBar(Text.color("&b❄ Varázs hópehely &7(" + db + ") &f+" + core.economy().format(core.economy().defaultCurrency(), jutalom)));
    }

    private void ujPehely(World world) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int probe = 0; probe < 30; probe++) {
            double angle = r.nextDouble(Math.PI * 2);
            double dist = 6 + r.nextDouble(sugar);
            int x = cx + (int) Math.round(Math.cos(angle) * dist);
            int z = cz + (int) Math.round(Math.sin(angle) * dist);
            Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            Material m = top.getType();
            if (m == Material.BARRIER || m == Material.WATER || m.isAir() || !m.isOccluding() || top.getY() < 60 || top.getY() > 140
                    || !top.getRelative(0, 1, 0).getType().isAir() || !top.getRelative(0, 2, 0).getType().isAir()) {
                continue;
            }
            Location at = top.getLocation().add(0.5, 1.6, 0.5);
            ItemDisplay d = world.spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(new ItemStack(Material.PRISMARINE_CRYSTALS));
                e.setBillboard(Display.Billboard.CENTER);
                e.setGlowing(true);
                e.setGlowColorOverride(org.bukkit.Color.fromRGB(0x8FD8FF));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7F, 0.7F, 0.7F), new AxisAngle4f()));
                e.addScoreboardTag(TAG);
                e.setPersistent(false);
            });
            pelyhek.add(d);
            return;
        }
    }

    private void befejez(boolean bejelent) {
        if (vihar != null) {
            vihar.cancel();
            vihar = null;
        }
        for (ItemDisplay d : pelyhek) {
            if (d.isValid()) {
                d.remove();
            }
        }
        pelyhek.clear();
        kovetkezo = System.currentTimeMillis() + perc * 60_000L;
        World world = core.lobby() == null ? null : core.lobby().world();
        if (world != null) {
            takarit(world);
        }
        if (!bejelent || world == null) {
            return;
        }
        UUID best = null;
        int max = 0;
        for (Map.Entry<UUID, Integer> e : gyujtott.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        String nev = best == null ? null : Bukkit.getOfflinePlayer(best).getName();
        for (Player p : world.getPlayers()) {
            int db = gyujtott.getOrDefault(p.getUniqueId(), 0);
            core.messages().sendTagged(p, "&7Elállt a hóvihar. Te &f" + db + " &7hópelyhet gyűjtöttél."
                    + (nev != null ? " &7A legtöbbet &b" + nev + " &7szedte össze (&f" + max + "&7)." : ""));
        }
    }

    private void takarit(World world) {
        for (ItemDisplay d : world.getEntitiesByClass(ItemDisplay.class)) {
            if (d.getScoreboardTags().contains(TAG)) {
                d.remove();
            }
        }
    }
}
