package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Items;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PvP aréna a lobbyban: aki belép, felszerelést kap, és csak az arénában lévők üthetik egymást. Halál helyett aki
 * kiesne, az kirepül az arénából, és visszakapja a lobby tárgyait.
 */
public final class LobbyPvp implements Listener {

    static final String TAG = "lobbypvp:kit";

    private final IceMineCore core;
    private final Set<UUID> bent = new HashSet<>();
    private Location center;
    private Location exit;
    private double radius = hu.icemine.core.lobby.LobbyBuild.ARENA_R;

    public LobbyPvp(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, World world) {
        center = hu.icemine.core.util.Locs.read(config.getConfigurationSection("pvp-arena.center"), world);
        exit = hu.icemine.core.util.Locs.read(config.getConfigurationSection("pvp-arena.exit"), world);
        radius = config.getDouble("pvp-arena.radius", LobbyBuild.ARENA_R);
        figyel(world);
    }

    public boolean in(Player p) {
        return bent.contains(p.getUniqueId());
    }

    boolean inside(Location l) {
        if (center == null || l.getWorld() != center.getWorld() || l.getY() < center.getY() - 3 || l.getY() > center.getY() + 8) {
            return false;
        }
        double dx = l.getX() - center.getX();
        double dz = l.getZ() - center.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private org.bukkit.scheduler.BukkitTask task;

    /** Másodpercenként ellenőrzi, ki van bent (teleport, lökés után is). */
    void figyel(World world) {
        if (task != null) {
            task.cancel();
        }
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(core, () -> {
            if (center == null) {
                return;
            }
            for (Player p : world.getPlayers()) {
                boolean volt = bent.contains(p.getUniqueId());
                boolean most = inside(p.getLocation());
                if (most && !volt) {
                    belep(p);
                } else if (!most && volt) {
                    kilep(p, false);
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (center == null || event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        boolean volt = bent.contains(p.getUniqueId());
        boolean most = inside(event.getTo());
        if (most && !volt) {
            belep(p);
        } else if (!most && volt) {
            kilep(p, false);
        }
    }

    private void belep(Player p) {
        if (!core.auth().isLoggedIn(p) || (p.getGameMode() != GameMode.ADVENTURE && p.getGameMode() != GameMode.SURVIVAL)
                || (core.parkour() != null && core.parkour().running(p))) {
            return;
        }
        bent.add(p.getUniqueId());
        p.setFlying(false);
        p.setAllowFlight(false);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, kit(Material.IRON_SWORD));
        inv.setItem(1, kit(Material.BOW));
        inv.setItem(2, kit(Material.GOLDEN_APPLE, 2));
        inv.setItem(8, kit(Material.ARROW, 16));
        inv.setHelmet(kit(Material.IRON_HELMET));
        inv.setChestplate(kit(Material.IRON_CHESTPLATE));
        inv.setLeggings(kit(Material.CHAINMAIL_LEGGINGS));
        inv.setBoots(kit(Material.IRON_BOOTS));
        inv.setHeldItemSlot(0);
        heal(p);
        p.sendTitle(hu.icemine.core.util.Text.color("&c&l⚔ PvP"), hu.icemine.core.util.Text.color("&7Kilépés: sétálj ki az arénából"), 5, 30, 10);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1F, 1F);
    }

    private static ItemStack kit(Material m) {
        return kit(m, 1);
    }

    private static ItemStack kit(Material m, int n) {
        ItemStack item = Items.of(m).tag(TAG).build();
        item.setAmount(n);
        return item;
    }

    private void heal(Player p) {
        try {
            p.setHealth(p.getMaxHealth());
        } catch (IllegalArgumentException ignored) {
        }
        p.setFoodLevel(20);
        p.setFireTicks(0);
    }

    /** Kilépés az arénából: vissza a lobby tárgyai. */
    void kilep(Player p, boolean kiesett) {
        if (!bent.remove(p.getUniqueId())) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        heal(p);
        for (org.bukkit.potion.PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
        if (core.lobby().inLobby(p)) {
            core.lobby().giveHotbar(p);
            core.lobby().applyFlight(p);
        }
        for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 40, 20, 40)) {
            if (e instanceof org.bukkit.entity.Arrow arrow && arrow.getShooter() == p) {
                arrow.remove();
            }
        }
    }

    /** A lobby védelme ezt kérdezi meg: engedjük-e ezt a sebzést. */
    public boolean allowed(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !bent.contains(victim.getUniqueId())) {
            return false;
        }
        if (event instanceof EntityDamageByEntityEvent by) {
            Player attacker = by.getDamager() instanceof Player p ? p
                    : (by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null);
            return attacker != null && attacker != victim && bent.contains(attacker.getUniqueId());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !bent.contains(victim.getUniqueId())) {
            return;
        }
        if (event.getFinalDamage() < victim.getHealth()) {
            return;
        }
        event.setCancelled(true);
        Player killer = event.getDamager() instanceof Player p ? p
                : (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null);
        kilep(victim, true);
        if (exit != null) {
            core.guard().allowNext(victim);
            victim.teleport(exit);
        }
        victim.sendTitle(hu.icemine.core.util.Text.color("&c&lKIESTÉL"), killer == null ? "" : hu.icemine.core.util.Text.color("&7" + killer.getName() + " győzött"), 5, 30, 10);
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1F, 0.8F);
        if (killer != null) {
            PlayerProfile pr = core.profiles().profile(killer.getUniqueId());
            long db = pr == null ? 0 : pr.addStat("lobby.pvp_kills", 1);
            core.messages().sendTagged(killer, "&aKiütötted: &f" + victim.getName() + (db > 0 ? " &8(összesen " + db + ")" : ""));
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.2F);
            heal(killer);
            for (UUID id : bent) {
                Player o = org.bukkit.Bukkit.getPlayer(id);
                if (o != null && o != killer) {
                    o.sendMessage(hu.icemine.core.util.Text.color("&c⚔ &f" + killer.getName() + " &7kiütötte: &f" + victim.getName()));
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bent.remove(event.getPlayer().getUniqueId());
        event.getPlayer().getInventory().clear();
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (bent.contains(event.getPlayer().getUniqueId())) {
            bent.remove(event.getPlayer().getUniqueId());
            Player p = event.getPlayer();
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                if (TAG.equals(Items.tagOf(p.getInventory().getItem(i)))) {
                    p.getInventory().setItem(i, null);
                }
            }
            p.getInventory().setArmorContents(null);
        }
    }
}
