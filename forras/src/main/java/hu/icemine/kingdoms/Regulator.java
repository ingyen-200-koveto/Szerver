package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Szabályzó: egy építmény, amivel a királyság a saját földjén (chunkonként) beállítja, mi történhet ott:
 * szörnyek és állatok születése, PvP, tűz. A beállítás a lerakott Szabályzó "label" mezőjében van, vesszővel
 * elválasztva a letiltott dolgok (pl. "pvp,monsters").
 */
final class Regulator implements Listener {

    /** A beállítható dolgok: kulcs → [anyag, név]. */
    static final Map<String, String[]> FLAGS = new LinkedHashMap<>();

    static {
        FLAGS.put("monsters", new String[]{"ZOMBIE_HEAD", "Szörnyek születése"});
        FLAGS.put("animals", new String[]{"COW_SPAWN_EGG", "Állatok születése"});
        FLAGS.put("pvp", new String[]{"IRON_SWORD", "PvP"});
        FLAGS.put("fire", new String[]{"FLINT_AND_STEEL", "Tűz terjedése"});
    }

    private final IceMineKingdoms plugin;

    Regulator(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    static List<String> disabled(Placed placed) {
        List<String> list = new ArrayList<>();
        if (placed.label == null || placed.label.isBlank()) {
            return list;
        }
        for (String part : placed.label.split(",")) {
            String flag = part.trim().toLowerCase(Locale.ROOT);
            if (FLAGS.containsKey(flag) && !list.contains(flag)) {
                list.add(flag);
            }
        }
        return list;
    }

    static boolean off(Placed placed, String flag) {
        return disabled(placed).contains(flag);
    }

    /** Átkapcsolja a dolgot; true, ha most le van tiltva. */
    static boolean toggle(Placed placed, String flag) {
        List<String> list = disabled(placed);
        boolean nowOff = !list.remove(flag);
        if (nowOff) {
            list.add(flag);
        }
        placed.label = String.join(",", list);
        return nowOff;
    }

    static Material icon(String flag) {
        String[] data = FLAGS.get(flag);
        Material material = data == null ? null : Material.matchMaterial(data[0]);
        return material == null ? Material.PAPER : material;
    }

    static String title(String flag) {
        String[] data = FLAGS.get(flag);
        return data == null ? flag : data[1];
    }

    static List<String> flags() {
        return Arrays.asList(FLAGS.keySet().toArray(new String[0]));
    }

    private boolean regulated(Location location, String flag) {
        return plugin.structures() != null && plugin.structures().regulated(location, flag);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL && reason != CreatureSpawnEvent.SpawnReason.SPAWNER
                && reason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS && reason != CreatureSpawnEvent.SpawnReason.PATROL
                && reason != CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Monster && reason != CreatureSpawnEvent.SpawnReason.BREEDING && regulated(event.getLocation(), "monsters")) {
            event.setCancelled(true);
        } else if (entity instanceof Animals && regulated(event.getLocation(), "animals")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker == null || attacker == victim || !regulated(victim.getLocation(), "pvp")) {
            return;
        }
        long chunk = Lands.key(victim.getLocation().getBlockX() >> 4, victim.getLocation().getBlockZ() >> 4);
        Kingdom owner = plugin.manager().owner(chunk);
        if (owner != null && plugin.invasions().defending(owner)) {
            return;
        }
        event.setCancelled(true);
        plugin.actionBar(attacker, "regulator-no-pvp");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL
                && regulated(event.getBlock().getLocation(), "fire")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (regulated(event.getBlock().getLocation(), "fire")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE && regulated(event.getBlock().getLocation(), "fire")) {
            event.setCancelled(true);
        }
    }
}
