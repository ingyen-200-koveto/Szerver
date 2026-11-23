package hu.icemine.core.skywars;

import hu.icemine.core.util.Items;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** A SkyWars szabályai: sebzés, halál, leesés, építés a pályán belül, kilépés tárgy. */
public final class SwListener implements Listener {

    private final Skywars plugin;

    public SwListener(Skywars plugin) {
        this.plugin = plugin;
    }

    private SwArena arena(Player p) {
        return plugin.arenaOf(p);
    }

    private static boolean harc(SwArena a, Player p) {
        SwArena.Adat d = a.data(p.getUniqueId());
        return a.state() == SwArena.State.PLAYING && d != null && d.alive;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        SwArena a = arena(p);
        if (a == null) {
            return;
        }
        if (!harc(a, p)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                p.teleport(a.state() == SwArena.State.PLAYING || a.state() == SwArena.State.ENDING ? a.spectatorPoint()
                        : a.cageSpawn(a.data(p.getUniqueId()) == null ? 0 : a.data(p.getUniqueId()).island));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            attacker = p;
        }
        SwArena a = arena(victim);
        if (a == null || attacker == null) {
            return;
        }
        if (arena(attacker) != a || !harc(a, attacker) || a.teammates(victim.getUniqueId(), attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        a.hit(victim.getUniqueId(), attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        SwArena a = arena(p);
        if (a == null) {
            return;
        }
        event.setDeathMessage(null);
        event.setKeepLevel(false);
        boolean ur = p.getLastDamageCause() != null && p.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID;
        a.die(p, p.getKiller(), ur);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a != null) {
            event.setRespawnLocation(a.spectatorPoint());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || event.getFrom().getBlockY() == to.getBlockY()) {
            return;
        }
        Player p = event.getPlayer();
        SwArena a = arena(p);
        if (a == null || to.getY() > a.cy - 25) {
            return;
        }
        if (harc(a, p)) {
            a.die(p, null, true);
            p.setHealth(0);
        } else if (a.state() == SwArena.State.WAITING || a.state() == SwArena.State.STARTING) {
            SwArena.Adat d = a.data(p.getUniqueId());
            p.teleport(a.cageSpawn(d == null ? 0 : d.island));
        } else {
            p.teleport(a.spectatorPoint());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a == null) {
            return;
        }
        if (!harc(a, event.getPlayer()) || !a.buildable(event.getBlock())) {
            event.setCancelled(true);
        } else {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a == null) {
            return;
        }
        if (!harc(a, event.getPlayer()) || !a.buildable(event.getBlockPlaced())) {
            event.setCancelled(true);
        } else {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a != null && (!harc(a, event.getPlayer()) || !a.buildable(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p) {
            SwArena a = arena(p);
            if (a != null && !harc(a, p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a != null && !harc(a, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A ketrecben (várakozás, visszaszámlálás) nem lehet a tárgyakat (pl. a Kilépés ágyat) arrébb tenni. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInvClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && locked(p, event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInvDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p && locked(p, event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        SwArena a = arena(event.getPlayer());
        if (a != null && !harc(a, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean locked(Player p, org.bukkit.inventory.InventoryView view) {
        SwArena a = arena(p);
        if (a == null || harc(a, p) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return false;
        }
        org.bukkit.event.inventory.InventoryType top = view.getTopInventory().getType();
        return top == org.bukkit.event.inventory.InventoryType.CRAFTING || top == org.bukkit.event.inventory.InventoryType.PLAYER;
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) {
            SwArena a = arena(p);
            if (a != null && !harc(a, p) && event.getInventory().getHolder() instanceof org.bukkit.block.Container) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = event.getPlayer();
        if (arena(p) != null && "sw:leave".equals(Items.tagOf(event.getItem()))) {
            event.setCancelled(true);
            arena(p).leave(p, true);
        } else if (arena(p) != null && "sw:cage".equals(Items.tagOf(event.getItem()))) {
            event.setCancelled(true);
            plugin.cages().menu(p);
        }
    }
}
