package hu.icemine.core.bedwars;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/** A BedWars szabályai: építés, bontás, ágyak, sebzés, halál, bolt NPC-k, tárgyak. */
public final class BwListener implements Listener {

    /** Ezeket a parancsokat meccs közben is lehet használni. */
    private static final Set<String> ALLOWED = Set.of("bw", "bedwars", "lobby", "hub", "spawn", "l", "msg", "tell", "w", "r", "reply", "m",
            "kapcsolat", "kapcsolatok", "login", "register", "kozmetika", "profil");

    private final Bedwars plugin;
    private final Map<UUID, Long> warned = new HashMap<>();

    public BwListener(Bedwars plugin) {
        this.plugin = plugin;
    }

    private boolean inWorld(Location location) {
        return location != null && plugin.world() != null && location.getWorld() == plugin.world();
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 1500L) {
            warned.put(player.getUniqueId(), now);
            player.sendActionBar(plugin.core().messages().get("bedwars." + key));
        }
    }

    private static boolean admin(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission(Bedwars.ADMIN);
    }

    /** Az aréna, ha a játékos éppen játszik (él, nem néző, nem újraéledés közben). */
    private BwArena playing(Player player) {
        BwArena arena = plugin.arenaOf(player);
        if (arena == null || arena.state() != BwArena.State.PLAYING) {
            return null;
        }
        BwPlayer data = arena.data(player);
        return data != null && data.playing() ? arena : null;
    }

    // ---- építés, bontás ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!inWorld(block.getLocation())) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            if (!admin(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (playing(player) == null) {
            event.setCancelled(true);
            return;
        }
        String problem = arena.placeProblem(block);
        if (problem != null) {
            event.setCancelled(true);
            warn(player, problem);
            return;
        }
        if (block.getType() == Material.TNT) {
            event.setCancelled(true);
            ItemStack item = player.getInventory().getItem(event.getHand());
            if (item != null && item.getType() == Material.TNT) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(event.getHand(), item.getAmount() <= 0 ? null : item);
            }
            TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0.0, 0.5), TNTPrimed.class);
            tnt.setFuseTicks(52);
            tnt.setSource(player);
            tnt.addScoreboardTag(BwArena.TAG);
            return;
        }
        arena.markPlaced(block);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!inWorld(block.getLocation())) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            if (!admin(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (playing(player) == null) {
            event.setCancelled(true);
            return;
        }
        String problem = arena.placeProblem(block);
        if (problem != null) {
            event.setCancelled(true);
            warn(player, problem);
            return;
        }
        arena.markPlaced(block);
        // az üres vödör eltűnik, ne lehessen vele vizet gyűjteni
        Bukkit.getScheduler().runTask(plugin.core(), () -> {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == Material.BUCKET) {
                    player.getInventory().setItem(i, null);
                    break;
                }
            }
        });
    }

    /** A folyó víz is "lerakott blokk", így a meccs végén eltűnik. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!inWorld(event.getBlock().getLocation())) {
            return;
        }
        BwArena arena = plugin.arenaAt(event.getBlock().getLocation());
        if (arena == null || !arena.placed(event.getBlock())) {
            event.setCancelled(arena == null);
            return;
        }
        if (!arena.contains(event.getToBlock())) {
            event.setCancelled(true);
            return;
        }
        arena.markPlaced(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!inWorld(block.getLocation())) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            if (!admin(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (playing(player) == null) {
            event.setCancelled(true);
            return;
        }
        if (arena.isBed(block)) {
            event.setCancelled(true);
            BwTeam owner = arena.bedOwner(block);
            BwPlayer data = arena.data(player);
            if (owner == null) {
                return;
            }
            if (owner.color == data.team) {
                warn(player, "bed.own");
            } else {
                arena.breakBed(player, owner);
            }
            return;
        }
        if (!arena.placed(block)) {
            event.setCancelled(true);
            warn(player, "build.map");
            return;
        }
        arena.unmark(block);
        event.setExpToDrop(0);
    }

    private void explode(List<Block> blocks, Location location) {
        if (!inWorld(location)) {
            return;
        }
        BwArena arena = plugin.arenaAt(location);
        if (arena == null) {
            blocks.clear();
            return;
        }
        blocks.removeIf(block -> !arena.placed(block) || block.getType().name().endsWith("GLASS") || block.getType() == Material.OBSIDIAN
                || block.getType() == Material.END_STONE);
        for (Block block : blocks) {
            arena.unmark(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getLocation());
        if (inWorld(event.getLocation())) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getBlock().getLocation());
    }

    // ---- sebzés, halál ----

    private static Player source(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        if (entity instanceof TNTPrimed tnt) {
            return tnt.getSource() instanceof Player player ? player : null;
        }
        return null;
    }

    private static String cause(EntityDamageEvent event) {
        switch (event.getCause().name()) {
            case "ENTITY_ATTACK":
            case "ENTITY_SWEEP_ATTACK":
                return "melee";
            case "PROJECTILE":
                return "projectile";
            case "ENTITY_EXPLOSION":
            case "BLOCK_EXPLOSION":
                return "explosion";
            case "VOID":
                return "void";
            case "FALL":
                return "fall";
            case "FIRE":
            case "FIRE_TICK":
            case "LAVA":
                return "fire";
            default:
                return "other";
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!inWorld(entity.getLocation())) {
            return;
        }
        if (!(entity instanceof Player player)) {
            if (entity.getScoreboardTags().contains(BwArena.TAG) || entity instanceof Villager) {
                event.setCancelled(true);
            }
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            return;
        }
        BwPlayer data = arena.data(player);
        if (arena.state() != BwArena.State.PLAYING || data == null || !data.playing()) {
            event.setCancelled(true);
            return;
        }
        Player attacker = source(event.getDamager());
        if (attacker == null) {
            return;
        }
        BwPlayer attackerData = arena.data(attacker);
        if (attackerData == null) {
            event.setCancelled(true);
            return;
        }
        if (attacker.equals(player)) {
            return;
        }
        if (attackerData.playing() && attackerData.team != data.team) {
            data.lastDamager = attacker.getUniqueId();
            data.lastDamageAt = System.currentTimeMillis();
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inWorld(player.getLocation())) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && !admin(player)) {
                event.setCancelled(true);
                plugin.core().guard().allowNext(player);
                player.teleport(plugin.returnLocation(player));
            }
            return;
        }
        BwPlayer data = arena.data(player);
        if (arena.state() == BwArena.State.PLAYING && data != null && data.playing()) {
            double health = player.getHealth() + player.getAbsorptionAmount();
            if (event.getFinalDamage() >= health || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
                Player killer = event instanceof EntityDamageByEntityEvent hit ? source(hit.getDamager()) : null;
                if (killer != null && killer.equals(player)) {
                    killer = null;
                }
                arena.die(player, killer, cause(event));
            }
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            plugin.core().guard().allowNext(player);
            player.teleport(data != null && data.team != null || arena.watching(player) ? arena.spectator() : arena.lobby());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        event.setDeathMessage(null);
        Bukkit.getScheduler().runTaskLater(plugin.core(), () -> {
            if (player.isOnline() && !player.isDead() && plugin.arenaOf(player) == arena) {
                arena.die(player, null, "other");
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        BwArena arena = plugin.arenaOf(event.getPlayer());
        if (arena != null && arena.spectator() != null) {
            event.setRespawnLocation(arena.spectator());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || !inWorld(to)) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            return;
        }
        BwPlayer data = arena.data(player);
        boolean below = to.getY() < arena.cy - plugin.settings().voidDepth;
        if (arena.state() == BwArena.State.PLAYING && data != null && data.playing()) {
            if (below) {
                arena.die(player, null, "void");
            }
            return;
        }
        if (below || !arena.contains(to) || to.getY() > arena.cy + BwMap.LOBBY_Y + 30) {
            plugin.core().guard().allowNext(player);
            player.teleport(data != null && data.team != null || arena.watching(player) ? arena.spectator() : arena.lobby());
        }
    }

    // ---- tárgyak, NPC-k ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BwArena arena = plugin.arenaOf(player);
        if (arena == null) {
            if (inWorld(player.getLocation()) && !admin(player) && event.getClickedBlock() != null) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        ItemStack item = event.getItem();
        String tag = BwItems.tag(item);
        if (tag != null && tag.startsWith("menu:")) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
                return;
            }
            switch (tag) {
                case "menu:team":
                    arena.openTeamMenu(player);
                    break;
                case "menu:leave":
                    arena.leave(player, true);
                    break;
                case "menu:again":
                    BwMode mode = arena.mode;
                    arena.leave(player, false);
                    plugin.play(player, mode);
                    if (plugin.arenaOf(player) == null) {
                        plugin.core().guard().allowNext(player);
                        player.teleport(plugin.returnLocation(player));
                    }
                    break;
                default:
                    break;
            }
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && arena.isBed(clicked) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (item != null && item.getType() == Material.FIRE_CHARGE && rightClick) {
            event.setCancelled(true);
            if (playing(player) == null || event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            BwPlayer data = arena.data(player);
            long now = System.currentTimeMillis();
            if (now - data.lastFireball < 500L) {
                return;
            }
            data.lastFireball = now;
            item.setAmount(item.getAmount() - 1);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setYield(2.6F);
            fireball.setIsIncendiary(false);
            fireball.setVelocity(player.getLocation().getDirection().multiply(1.4));
            fireball.addScoreboardTag(BwArena.TAG);
            return;
        }
        if (arena.state() != BwArena.State.PLAYING && clicked != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!inWorld(entity.getLocation())) {
            return;
        }
        if (entity instanceof Villager || entity.getScoreboardTags().contains(BwArena.TAG)) {
            event.setCancelled(true);
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        BwArena arena = playing(player);
        if (arena == null) {
            return;
        }
        if (arena.isKeeper(entity, "shop")) {
            plugin.shopMenus().openShop(player, arena, "gyors");
        } else if (arena.isKeeper(entity, "upgrade")) {
            plugin.shopMenus().openUpgrades(player, arena);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStand(PlayerArmorStandManipulateEvent event) {
        if (inWorld(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (inWorld(event.getBed().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        BwArena arena = plugin.arenaOf(player);
        if (arena == null || event.getInventory().getHolder() instanceof hu.icemine.core.menu.Menu) {
            return;
        }
        if (arena.state() != BwArena.State.PLAYING) {
            event.setCancelled(true);
        } else if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
        } else if (event.getInventory().getType() == InventoryType.CRAFTING && event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (inWorld(event.getWhoClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        BwArena arena = plugin.arenaOf(event.getPlayer());
        if (arena == null) {
            return;
        }
        if (arena.state() != BwArena.State.PLAYING) {
            event.setCancelled(true);
            return;
        }
        String tag = BwItems.tag(event.getItemDrop().getItemStack());
        if (tag != null && !tag.equals("sword")) {
            event.setCancelled(true);
            return;
        }
        if ("sword".equals(tag)) {
            // ha nem maradt kardja, visszakapja a fakardot
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin.core(), () -> {
                BwArena current = plugin.arenaOf(player);
                BwPlayer data = current == null ? null : current.data(player);
                if (data == null || !data.playing()) {
                    return;
                }
                for (ItemStack item : player.getInventory().getStorageContents()) {
                    String other = BwItems.tag(item);
                    if (other != null && other.startsWith("sword")) {
                        return;
                    }
                }
                BwItems.give(player, BwItems.sword(Material.WOODEN_SWORD, current.team(data.team)));
            });
        }
    }

    /** Ha felvesz egy kardot, a fakard eltűnik. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwordPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !"sword".equals(BwItems.tag(event.getItem().getItemStack()))) {
            return;
        }
        if (plugin.arenaOf(player) != null) {
            Bukkit.getScheduler().runTask(plugin.core(), () -> BwItems.removeTagged(player, "sword:base"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        BwArena arena = plugin.arenaOf(event.getPlayer());
        if (arena != null && arena.state() != BwArena.State.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.arenaOf(player) != null && playing(player) == null) {
            event.setCancelled(true);
        }
    }

    /** A megivott bájital üvege eltűnik. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() != Material.POTION || plugin.arenaOf(player) == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin.core(), () -> {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == Material.GLASS_BOTTLE) {
                    player.getInventory().setItem(i, null);
                    break;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFood(FoodLevelChangeEvent event) {
        if (inWorld(event.getEntity().getLocation()) && event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.arenaOf(player) == null || player.hasPermission(Bedwars.ADMIN)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!ALLOWED.contains(label)) {
            event.setCancelled(true);
            plugin.core().messages().send(player, "bedwars.command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        warned.remove(event.getPlayer().getUniqueId());
    }
}
