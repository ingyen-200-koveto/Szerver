package hu.icemine.kingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

final class KingdomListener implements Listener {

    private final IceMineKingdoms plugin;
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Set<UUID> died = ConcurrentHashMap.newKeySet();

    KingdomListener(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private void deny(Player player, Location location) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last != null && now - last < 2000) {
            return;
        }
        warned.put(player.getUniqueId(), now);
        if (plugin.inSpawn(location)) {
            player.sendMessage(plugin.message("spawn-protected"));
            return;
        }
        Kingdom owner = plugin.owner(location);
        player.sendMessage(plugin.message("claim-protected").replace("%kingdom%", owner == null ? "?" : owner.name));
    }

    private boolean guarded(Location location) {
        return location.getWorld() == plugin.world() && (plugin.inSpawn(location) || plugin.owner(location) != null);
    }

    private boolean blocked(Player player, Location location) {
        return plugin.inKingdoms(player) && !plugin.canBuild(player, location);
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom() == plugin.world()) {
            plugin.leave(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnterWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.inKingdoms(player)) {
            plugin.enter(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.inKingdoms(player)) {
            plugin.leave(player);
        }
        warned.remove(player.getUniqueId());
        died.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.inKingdoms(player)) {
            died.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!died.remove(player.getUniqueId())) {
            return;
        }
        Location target = event.getRespawnLocation();
        if (target != null && target.getWorld() == plugin.world()) {
            return;
        }
        Kingdom kingdom = plugin.kingdomOf(player.getUniqueId());
        Location home = kingdom == null ? null : plugin.home(kingdom);
        event.setRespawnLocation(home != null ? home : plugin.spawn());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        if (blocked(player, location)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        if (blocked(player, location)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        if (blocked(player, block.getLocation())) {
            event.setCancelled(true);
            if (action == Action.RIGHT_CLICK_BLOCK) {
                deny(player, block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getBlockClicked();
        Location target = clicked.getRelative(event.getBlockFace()).getLocation();
        if (blocked(player, target) || blocked(player, clicked.getLocation())) {
            event.setCancelled(true);
            deny(player, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockClicked().getLocation();
        if (blocked(player, location)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Hanging) && !(entity instanceof ArmorStand)) {
            return;
        }
        Player player = event.getPlayer();
        if (blocked(player, entity.getLocation())) {
            event.setCancelled(true);
            deny(player, entity.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (blocked(player, event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attacker(event.getRemover());
        if (player != null && blocked(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            deny(player, event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && blocked(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            deny(player, event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim.getWorld() != plugin.world()) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (victim instanceof Player) {
            Player target = (Player) victim;
            if (target.equals(attacker)) {
                return;
            }
            if (plugin.inSpawn(target.getLocation()) || plugin.inSpawn(attacker.getLocation())) {
                event.setCancelled(true);
                attacker.sendMessage(plugin.message("no-pvp-spawn"));
                return;
            }
            Kingdom mine = plugin.kingdomOf(attacker.getUniqueId());
            if (mine != null && mine == plugin.kingdomOf(target.getUniqueId()) && !plugin.getConfig().getBoolean("friendly-fire", false)) {
                event.setCancelled(true);
                attacker.sendMessage(plugin.message("friendly-fire"));
            }
            return;
        }
        if ((victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Animals) && blocked(attacker, victim.getLocation())) {
            event.setCancelled(true);
            deny(attacker, victim.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> guarded(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> guarded(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Location location = event.getBlock().getLocation();
        if (location.getWorld() != plugin.world()) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null) {
            if (!plugin.canBuild(player, location)) {
                event.setCancelled(true);
                deny(player, location);
            }
            return;
        }
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD && guarded(location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (guarded(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (from.getWorld() != plugin.world()) {
            return;
        }
        Location to = event.getToBlock().getLocation();
        if (plugin.inSpawn(to) && !plugin.inSpawn(from.getLocation())) {
            event.setCancelled(true);
            return;
        }
        Kingdom target = plugin.owner(to);
        if (target != null && target != plugin.owner(from.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonBlocked(Block piston, List<Block> blocks, BlockFace direction) {
        if (piston.getWorld() != plugin.world()) {
            return false;
        }
        Kingdom source = plugin.owner(piston.getLocation());
        for (Block block : blocks) {
            for (Location location : new Location[]{block.getLocation(), block.getRelative(direction).getLocation()}) {
                if (plugin.inSpawn(location)) {
                    return true;
                }
                Kingdom owner = plugin.owner(location);
                if (owner != null && owner != source) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonBlocked(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonBlocked(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && guarded(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && plugin.inSpawn(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getWorld() != plugin.world()) {
            return;
        }
        event.setCancelled(true);
        Long last = warned.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last == null || now - last > 2000) {
            warned.put(player.getUniqueId(), now);
            player.sendMessage(plugin.message("no-portal"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.inKingdoms(player)) {
            plugin.checkArea(player, to);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.inKingdoms(player)) {
            return;
        }
        Kingdom kingdom = plugin.kingdomOf(player.getUniqueId());
        String message = event.getMessage();
        boolean quick = message.length() > 1 && message.startsWith("!");
        if (kingdom != null && (quick || plugin.chatMode(player.getUniqueId()))) {
            event.setCancelled(true);
            String line = Text.color(plugin.getConfig().getString("chat.kingdom", "&8[&6%kingdom%&8] &7%player%&8: &e")
                    .replace("%kingdom%", kingdom.name).replace("%player%", player.getName())) + (quick ? message.substring(1).trim() : message);
            for (UUID id : kingdom.members.keySet()) {
                Player member = Bukkit.getPlayer(id);
                if (member != null) {
                    member.sendMessage(line);
                }
            }
            plugin.getLogger().info("[" + kingdom.name + "] " + player.getName() + ": " + message);
            return;
        }
        String tag = kingdom == null ? plugin.getConfig().getString("chat.no-tag", "") : plugin.getConfig().getString("chat.tag", "&8[&6%kingdom%&8] ").replace("%kingdom%", kingdom.name);
        String raw = plugin.getConfig().getString("chat.format", "%tag%%prefix%&f%player%&8: &7");
        if (!raw.contains("%prefix%")) {
            raw = raw.replace("%player%", "%prefix%%player%");
        }
        String format = Text.color(raw.replace("%tag%", tag).replace("%prefix%", plugin.prefix(player)))
                .replace("%", "%%").replace("%%player%%", "%1$s") + "%2$s";
        event.setFormat(format);
    }
}
