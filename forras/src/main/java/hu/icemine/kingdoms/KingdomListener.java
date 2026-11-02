package hu.icemine.kingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
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
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Földvédelem, PvP szabályok, halál, chat és a világba be- és kilépés. A földön a tulajdonos királyság tagjai a rangjuk
 * jogai szerint építhetnek és használhatnak dolgokat; a többiek a kapcsolat szerint (szövetséges ajtót nyithat stb.).
 * Invázió közben a támadók a megtámadott földön bonthatnak és építhetnek.
 */
final class KingdomListener implements Listener {

    enum Act { BUILD, USE, INTERACT }

    private final IceMineKingdoms plugin;
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Set<UUID> died = ConcurrentHashMap.newKeySet();

    KingdomListener(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    private KingdomManager km() {
        return plugin.manager();
    }

    /** Szabad-e ezt a játékosnak ezen a helyen. */
    boolean allowed(Player player, Location location, Act act) {
        if (location.getWorld() != plugin.world() || plugin.admin(player)) {
            return true;
        }
        if (plugin.inSpawn(location)) {
            return false;
        }
        Kingdom owner = km().owner(location);
        if (owner == null) {
            return true;
        }
        long chunk = Lands.key(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Kingdom mine = km().kingdomOf(player.getUniqueId());
        if (mine == owner) {
            return switch (act) {
                case BUILD -> owner.can(player.getUniqueId(), KPerm.BUILD) && (chunk != owner.nexusChunk() || owner.can(player.getUniqueId(), KPerm.NEXUS_BUILD));
                case USE -> owner.can(player.getUniqueId(), KPerm.USE);
                case INTERACT -> owner.can(player.getUniqueId(), KPerm.INTERACT);
            };
        }
        if (act != Act.USE && plugin.invasions().attackerHere(player, chunk)) {
            return true;
        }
        Relation relation = km().relation(mine, owner);
        return act == Act.INTERACT ? relation.interact || relation.build : relation.build;
    }

    private void deny(Player player, Location location) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last != null && now - last < 2000) {
            return;
        }
        warned.put(player.getUniqueId(), now);
        if (plugin.inSpawn(location)) {
            plugin.send(player, "spawn-protected");
            return;
        }
        Kingdom owner = km().owner(location);
        if (owner != null && owner == km().kingdomOf(player.getUniqueId())) {
            plugin.send(player, "rank-cant-here");
            return;
        }
        plugin.send(player, "claim-protected", "%kingdom%", owner == null ? "?" : owner.display());
    }

    private boolean guarded(Location location) {
        return location.getWorld() == plugin.world() && (plugin.inSpawn(location) || km().owner(location) != null);
    }

    /** Robbanás: a földeken nincs (kivéve, ha invázió zajlik ott, és a királyságnak nincs robbanásvédelme). */
    private boolean explosionProtected(Location location) {
        if (location.getWorld() != plugin.world()) {
            return false;
        }
        if (plugin.inSpawn(location)) {
            return true;
        }
        Kingdom owner = km().owner(location);
        if (owner == null) {
            return false;
        }
        boolean anti = km().hasMisc(owner, "anti-explosion");
        if (anti) {
            return true;
        }
        long chunk = Lands.key(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        return plugin.settings().protectExplosions && !plugin.invasions().invadedChunk(chunk);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // ---- világ ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom() == plugin.world()) {
            plugin.leave(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnterWorld(PlayerChangedWorldEvent event) {
        if (plugin.inKingdoms(event.getPlayer())) {
            plugin.enter(event.getPlayer());
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
        if (!plugin.inKingdoms(player)) {
            return;
        }
        died.add(player.getUniqueId());
        km().death(player, player.getKiller());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!died.remove(player.getUniqueId())) {
            return;
        }
        Location target = event.getRespawnLocation();
        if (target != null && target.getWorld() == plugin.world() && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            target = null;
        }
        if (target != null && target.getWorld() == plugin.world()) {
            return;
        }
        Kingdom kingdom = km().kingdomOf(player.getUniqueId());
        Location home = kingdom == null || kingdom.home == null ? null
                : new Location(plugin.world(), kingdom.home[0], kingdom.home[1], kingdom.home[2], (float) kingdom.home[3], 0f);
        event.setRespawnLocation(home != null ? home : plugin.spawn());
    }

    // ---- földvédelem ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        if (!allowed(player, location, Act.BUILD)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        if (!allowed(player, location, Act.BUILD)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() != plugin.world()) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            if (block.getType() == Material.FARMLAND) {
                Kingdom owner = km().owner(block.getLocation());
                if (km().hasMisc(owner, "anti-trample") || !allowed(player, block.getLocation(), Act.BUILD)) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!allowed(player, block.getLocation(), Act.INTERACT)) {
                event.setCancelled(true);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Act act = block.getState() instanceof Container ? Act.USE
                : interactive(block.getType()) ? Act.INTERACT : event.isBlockInHand() || event.getItem() != null && isPlacing(event.getItem().getType()) ? Act.BUILD : null;
        if (act == null && event.getItem() != null && !event.getItem().getType().isAir()) {
            act = Act.BUILD;
        }
        if (act != null && !allowed(player, block.getLocation(), act)) {
            event.setCancelled(true);
            deny(player, block.getLocation());
        }
    }

    private static boolean interactive(Material type) {
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.BUTTONS.isTagged(type)
                || type == Material.LEVER || Tag.BEDS.isTagged(type) || type == Material.BELL || type == Material.CRAFTING_TABLE
                || type == Material.ENCHANTING_TABLE || type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL
                || type == Material.REPEATER || type == Material.COMPARATOR || type == Material.NOTE_BLOCK || type == Material.CAKE
                || type == Material.LOOM || type == Material.CARTOGRAPHY_TABLE || type == Material.STONECUTTER || type == Material.GRINDSTONE
                || type == Material.SMITHING_TABLE || type == Material.DAYLIGHT_DETECTOR || type == Material.JUKEBOX;
    }

    private static boolean isPlacing(Material type) {
        return type.isBlock() || type.name().endsWith("_BUCKET") || type.name().endsWith("_SPAWN_EGG") || type == Material.BONE_MEAL
                || type == Material.FLINT_AND_STEEL || type == Material.FIRE_CHARGE || type == Material.ARMOR_STAND || type.name().endsWith("_BOAT")
                || type.name().endsWith("MINECART") || type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME || type == Material.PAINTING;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getBlockClicked();
        Location target = clicked.getRelative(event.getBlockFace()).getLocation();
        if (!allowed(player, target, Act.BUILD) || !allowed(player, clicked.getLocation(), Act.BUILD)) {
            event.setCancelled(true);
            deny(player, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockClicked().getLocation();
        if (!allowed(player, location, Act.BUILD)) {
            event.setCancelled(true);
            deny(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        Act act = entity instanceof Hanging || entity instanceof ArmorStand ? Act.BUILD
                : entity instanceof Vehicle || entity instanceof Animals || entity instanceof org.bukkit.entity.Villager ? Act.INTERACT : null;
        if (act != null && !allowed(player, entity.getLocation(), act)) {
            event.setCancelled(true);
            deny(player, entity.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!allowed(event.getPlayer(), event.getRightClicked().getLocation(), Act.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attacker(event.getRemover());
        if (player != null && !allowed(player, event.getEntity().getLocation(), Act.BUILD)) {
            event.setCancelled(true);
            deny(player, event.getEntity().getLocation());
        } else if (player == null && explosionProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !allowed(player, event.getEntity().getLocation(), Act.BUILD)) {
            event.setCancelled(true);
            deny(player, event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicle(VehicleDestroyEvent event) {
        Player player = attacker(event.getAttacker());
        if (player != null && !allowed(player, event.getVehicle().getLocation(), Act.BUILD)) {
            event.setCancelled(true);
            deny(player, event.getVehicle().getLocation());
        }
    }

    // ---- PvP ----

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
        if (victim instanceof Player target) {
            if (target.equals(attacker)) {
                return;
            }
            if (plugin.inSpawn(target.getLocation()) || plugin.inSpawn(attacker.getLocation())) {
                event.setCancelled(true);
                warn(attacker, "no-pvp-spawn");
                return;
            }
            Kingdom mine = km().kingdomOf(attacker.getUniqueId());
            Kingdom theirs = km().kingdomOf(target.getUniqueId());
            if (mine != null && mine == theirs) {
                if (!plugin.settings().friendlyFire) {
                    event.setCancelled(true);
                    warn(attacker, "friendly-fire");
                }
                return;
            }
            Relation relation = km().relation(mine, theirs);
            if (mine != null && theirs != null && relation.ceasefire) {
                event.setCancelled(true);
                warn(attacker, "ceasefire", "%relation%", relation.display());
                return;
            }
            KConfig cfg = plugin.settings();
            if (cfg.pacifismEnabled && cfg.pacifistNoPvp && (mine != null && mine.pacifist || theirs != null && theirs.pacifist)) {
                event.setCancelled(true);
                warn(attacker, "pacifist-pvp");
                return;
            }
            double damage = event.getDamage();
            if (mine != null && km().owner(attacker.getLocation()) == mine) {
                damage *= 1 + km().powerup(mine, "damage-boost") / 100.0;
                if (event.getDamager() instanceof Projectile) {
                    damage *= 1 + km().powerup(mine, "arrow-boost") / 100.0;
                }
            }
            if (theirs != null && km().owner(target.getLocation()) == theirs) {
                damage *= Math.max(0, 1 - km().powerup(theirs, "damage-reduction") / 100.0);
            }
            event.setDamage(damage);
            return;
        }
        if ((victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Animals || victim instanceof org.bukkit.entity.Villager)
                && !allowed(attacker, victim.getLocation(), Act.BUILD)) {
            event.setCancelled(true);
            deny(attacker, victim.getLocation());
        }
    }

    private void warn(Player player, String key, String... pairs) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            warned.put(player.getUniqueId(), now);
            plugin.send(player, key, pairs);
        }
    }

    /** Gyors gyógyulás erősítés a saját földön. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.getWorld() != plugin.world()) {
            return;
        }
        Kingdom kingdom = km().kingdomOf(player.getUniqueId());
        if (kingdom != null && km().owner(player.getLocation()) == kingdom) {
            double boost = km().powerup(kingdom, "regeneration-boost");
            if (boost > 0) {
                event.setAmount(event.getAmount() * (1 + boost / 100.0));
            }
        }
    }

    // ---- környezet ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> explosionProtected(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> explosionProtected(block.getLocation()));
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
            if (!allowed(player, location, Act.BUILD)) {
                event.setCancelled(true);
                deny(player, location);
            }
            return;
        }
        if ((event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) && guarded(location)) {
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
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE && guarded(event.getBlock().getLocation())) {
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
        Kingdom target = km().owner(to);
        if (target != null && target != km().owner(from.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonBlocked(Block piston, List<Block> blocks, BlockFace direction) {
        if (piston.getWorld() != plugin.world()) {
            return false;
        }
        Kingdom source = km().owner(piston.getLocation());
        for (Block block : blocks) {
            for (Location location : new Location[]{block.getLocation(), block.getRelative(direction).getLocation()}) {
                if (plugin.inSpawn(location)) {
                    return true;
                }
                Kingdom owner = km().owner(location);
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
        if (event.getFrom().getWorld() != plugin.world()) {
            return;
        }
        event.setCancelled(true);
        warn(event.getPlayer(), "no-portal");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (plugin.inKingdoms(event.getPlayer())) {
            plugin.moved(event.getPlayer(), to);
        }
    }

    // ---- chat ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.inKingdoms(player)) {
            return;
        }
        Kingdom kingdom = km().kingdomOf(player.getUniqueId());
        String message = event.getMessage();
        boolean quick = message.length() > 1 && message.startsWith("!");
        KPlayer data = km().player(player.getUniqueId());
        if (kingdom != null && data.allyChat && !quick) {
            event.setCancelled(true);
            Rank rank = kingdom.rankOf(player.getUniqueId());
            String line = Text.color(plugin.getConfig().getString("chat.ally", "&8[&d⚑ Szövetség&8] &8[%color%%kingdom%&8] &d%player%&8: &f")
                    .replace("%color%", kingdom.color).replace("%kingdom%", kingdom.name)
                    .replace("%player%", (rank == null ? "" : rank.color + rank.symbol + " ") + player.getName())) + message;
            for (Player other : Bukkit.getOnlinePlayers()) {
                Kingdom theirs = km().kingdomOf(other.getUniqueId());
                if (theirs == null) {
                    continue;
                }
                Relation relation = km().relation(kingdom, theirs);
                if (relation == Relation.SELF || relation == Relation.NATION || relation == Relation.ALLY) {
                    other.sendMessage(line);
                }
            }
            plugin.getLogger().info("[Szövetség " + kingdom.name + "] " + player.getName() + ": " + message);
            return;
        }
        if (kingdom != null && (quick || data.kingdomChat)) {
            event.setCancelled(true);
            Rank rank = kingdom.rankOf(player.getUniqueId());
            String line = Text.color(plugin.getConfig().getString("chat.kingdom", "&8[&6♛ %kingdom%&8] &e%player%&8: &f")
                    .replace("%kingdom%", kingdom.name).replace("%player%", (rank == null ? "" : rank.color + rank.symbol + " ") + player.getName()))
                    + (quick ? message.substring(1).trim() : message);
            for (UUID id : kingdom.members.keySet()) {
                Player member = Bukkit.getPlayer(id);
                if (member != null) {
                    member.sendMessage(line);
                }
            }
            plugin.getLogger().info("[" + kingdom.name + "] " + player.getName() + ": " + message);
            return;
        }
        String tag = kingdom == null ? plugin.getConfig().getString("chat.no-tag", "")
                : plugin.getConfig().getString("chat.tag", "&8[%color%%kingdom%&8] ").replace("%color%", kingdom.color).replace("%kingdom%", kingdom.name);
        String raw = plugin.getConfig().getString("chat.format", "%tag%%prefix%&f%player%&8: &7");
        if (!raw.contains("%prefix%")) {
            raw = raw.replace("%player%", "%prefix%%player%");
        }
        String format = Text.color(raw.replace("%tag%", tag).replace("%prefix%", plugin.rankPrefix(player)))
                .replace("%", "%%").replace("%%player%%", "%1$s") + "%2$s";
        event.setFormat(format);
    }
}
