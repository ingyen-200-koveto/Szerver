package hu.icemine.core.bedwars;

import hu.icemine.core.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Különleges BedWars tárgyak: hídtojás, felpattanó torony, őrző gólem, ezüstmoly, mentőplatform, varázstej. */
public final class Specials implements Listener {

    public static final String MOB = "icemine_bw_mob";
    private static final String TEAM = "bwteam:";
    private static final String OWNER = "bwowner:";
    public static final int GOLEM_SECONDS = 240;
    public static final int SILVERFISH_SECONDS = 15;
    public static final int PLATFORM_SECONDS = 10;
    public static final int MILK_SECONDS = 30;

    private final Bedwars plugin;
    private final Map<UUID, Long> platformCooldown = new HashMap<>();

    public Specials(Bedwars plugin) {
        this.plugin = plugin;
    }

    private boolean inWorld(Location location) {
        return location != null && plugin.world() != null && location.getWorld() == plugin.world();
    }

    private BwArena playing(Player player) {
        BwArena arena = plugin.arenaOf(player);
        if (arena == null || arena.state() != BwArena.State.PLAYING) {
            return null;
        }
        BwPlayer data = arena.data(player);
        return data != null && data.playing() && data.team != null ? arena : null;
    }

    private static void say(Player player, String text) {
        player.sendMessage(Text.color(text));
    }

    private static void consumeHand(Player player, EquipmentSlot hand) {
        ItemStack item = player.getInventory().getItem(hand);
        if (item == null) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);
    }

    /** Lerak egy blokkot, ha ott levegő van és szabad építeni. */
    private static boolean put(BwArena arena, Block block, Material material) {
        if (!block.getType().isAir() || arena.placeProblem(block) != null) {
            return false;
        }
        block.setType(material, false);
        arena.markPlaced(block);
        return true;
    }

    // ---- hídtojás ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg) || !(egg.getShooter() instanceof Player player) || !inWorld(egg.getLocation())) {
            return;
        }
        BwArena arena = playing(player);
        if (arena == null) {
            return;
        }
        Material wool = arena.data(player).team.wool();
        egg.setGravity(false);
        Vector dir = egg.getVelocity().clone().setY(0);
        boolean alongX = Math.abs(dir.getX()) >= Math.abs(dir.getZ());
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!egg.isValid() || ticks > 24 || arena.state() != BwArena.State.PLAYING) {
                    if (egg.isValid()) {
                        egg.remove();
                    }
                    cancel();
                    return;
                }
                if (ticks < 2) {
                    return;
                }
                Block base = egg.getLocation().clone().subtract(0, 2.2, 0).getBlock();
                boolean any = put(arena, base, wool);
                any |= put(arena, alongX ? base.getRelative(BlockFace.NORTH) : base.getRelative(BlockFace.WEST), wool);
                any |= put(arena, alongX ? base.getRelative(BlockFace.SOUTH) : base.getRelative(BlockFace.EAST), wool);
                if (any) {
                    base.getWorld().playSound(base.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.5F, 1.6F);
                }
            }
        }.runTaskTimer(plugin.core(), 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggHatch(PlayerEggThrowEvent event) {
        if (inWorld(event.getEgg().getLocation())) {
            event.setHatching(false);
        }
    }

    // ---- felpattanó torony ----

    /** A BwListener hívja, amikor a játékos lerakja a tornyot (ládát). */
    public void tower(Player player, BwArena arena, Block at) {
        Material wool = arena.data(player).team.wool();
        BlockFace facing = player.getFacing();
        int fx = facing.getModX();
        int fz = facing.getModZ();
        int bx = at.getX();
        int by = at.getY();
        int bz = at.getZ();
        player.getWorld().playSound(at.getLocation(), Sound.BLOCK_WOOL_PLACE, 1F, 0.8F);
        new BukkitRunnable() {
            int layer;

            @Override
            public void run() {
                if (layer > 7 || arena.state() != BwArena.State.PLAYING) {
                    cancel();
                    return;
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean wall = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                        int lx = dx * fx + dz * fz;
                        int lz = dz * fx - dx * fz;
                        boolean front = lx == 2 && lz == 0;
                        Block block = at.getWorld().getBlockAt(bx + dx, by + layer, bz + dz);
                        if (layer <= 5) {
                            if (wall && !(front && layer <= 1)) {
                                put(arena, block, wool);
                            }
                        } else if (layer == 6) {
                            boolean hole = dx == -fx && dz == -fz;
                            if (!hole) {
                                put(arena, block, wool);
                            }
                        } else if (wall && Math.floorMod(dx + dz, 2) == 0) {
                            put(arena, block, wool);
                        }
                    }
                }
                if (layer <= 6) {
                    Block ladder = at.getWorld().getBlockAt(bx - fx, by + layer, bz - fz);
                    if (ladder.getType().isAir() && arena.placeProblem(ladder) == null) {
                        ladder.setType(Material.LADDER, false);
                        if (ladder.getBlockData() instanceof Ladder data) {
                            data.setFacing(facing);
                            ladder.setBlockData(data, false);
                        }
                        arena.markPlaced(ladder);
                    }
                }
                at.getWorld().playSound(at.getLocation().add(0, layer, 0), Sound.BLOCK_WOOL_PLACE, 0.8F, 1.0F + layer * 0.1F);
                layer++;
            }
        }.runTaskTimer(plugin.core(), 0L, 2L);
    }

    // ---- őrző gólem, mentőplatform ----

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !inWorld(player.getLocation()) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!right) {
            return;
        }
        if (item.getType() == Material.IRON_GOLEM_SPAWN_EGG) {
            event.setCancelled(true);
            BwArena arena = playing(player);
            if (arena == null || event.getClickedBlock() == null) {
                return;
            }
            Location spot = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            if (!arena.contains(spot)) {
                return;
            }
            consumeHand(player, EquipmentSlot.HAND);
            golem(player, arena, spot);
        } else if (item.getType() == Material.BLAZE_ROD && "mentoplatform".equals(BwItems.tag(item))) {
            event.setCancelled(true);
            BwArena arena = playing(player);
            if (arena == null) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = platformCooldown.get(player.getUniqueId());
            if (last != null && now - last < 15000L) {
                say(player, "&cMég &f" + ((15000L - (now - last)) / 1000 + 1) + " mp&c, mire újra használhatod.");
                return;
            }
            platformCooldown.put(player.getUniqueId(), now);
            consumeHand(player, EquipmentSlot.HAND);
            platform(player, arena);
        }
    }

    private void golem(Player player, BwArena arena, Location spot) {
        TeamColor color = arena.data(player).team;
        IronGolem golem = spot.getWorld().spawn(spot, IronGolem.class, g -> {
            g.setPlayerCreated(true);
            g.setRemoveWhenFarAway(false);
            g.addScoreboardTag(MOB);
            g.addScoreboardTag(TEAM + color.id());
            g.addScoreboardTag(OWNER + player.getUniqueId());
            g.setCustomNameVisible(true);
        });
        spot.getWorld().playSound(spot, Sound.ENTITY_IRON_GOLEM_REPAIR, 1F, 0.8F);
        spot.getWorld().spawnParticle(Particle.CLOUD, spot.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
        mobLife(golem, arena, color, GOLEM_SECONDS, "Őrző");
        say(player, "&aLeraktad az őrzőt. &7" + GOLEM_SECONDS / 60 + " percig védi a szigeted.");
    }

    private void platform(Player player, BwArena arena) {
        Location loc = player.getLocation();
        int y = loc.getBlockY() - 2;
        int cx = loc.getBlockX();
        int cz = loc.getBlockZ();
        java.util.List<Block> made = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                Block block = loc.getWorld().getBlockAt(cx + dx, y, cz + dz);
                if (put(arena, block, Material.SLIME_BLOCK)) {
                    made.add(block);
                }
            }
        }
        if (made.isEmpty()) {
            say(player, "&cIde nem lehet platformot rakni.");
            return;
        }
        player.setFallDistance(0F);
        player.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 1F, 1F);
        Bukkit.getScheduler().runTaskLater(plugin.core(), () -> {
            for (Block block : made) {
                if (block.getType() == Material.SLIME_BLOCK) {
                    block.setType(Material.AIR, false);
                    arena.unmark(block);
                }
            }
        }, PLATFORM_SECONDS * 20L);
    }

    // ---- ezüstmoly ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball) || !(ball.getShooter() instanceof Player player) || !inWorld(ball.getLocation())) {
            return;
        }
        BwArena arena = playing(player);
        if (arena == null) {
            return;
        }
        TeamColor color = arena.data(player).team;
        Location spot = ball.getLocation();
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            spot = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
        }
        if (!arena.contains(spot)) {
            return;
        }
        Location where = spot;
        Silverfish fish = where.getWorld().spawn(where, Silverfish.class, s -> {
            s.addScoreboardTag(MOB);
            s.addScoreboardTag(TEAM + color.id());
            s.addScoreboardTag(OWNER + player.getUniqueId());
            s.setCustomNameVisible(true);
        });
        mobLife(fish, arena, color, SILVERFISH_SECONDS, "Ezüstmoly");
    }

    // ---- közös: élettartam, célpont ----

    private void mobLife(Mob mob, BwArena arena, TeamColor color, int seconds, String name) {
        new BukkitRunnable() {
            int left = seconds;

            @Override
            public void run() {
                if (!mob.isValid() || left <= 0 || arena.state() != BwArena.State.PLAYING) {
                    if (mob.isValid()) {
                        mob.getWorld().spawnParticle(Particle.POOF, mob.getLocation().add(0, 0.5, 0), 12, 0.3, 0.5, 0.3, 0.02);
                        mob.remove();
                    }
                    cancel();
                    return;
                }
                mob.setCustomName(Text.color(color.chat + name + " &7" + (left >= 60 ? left / 60 + ":" + String.format("%02d", left % 60) : left + " mp")));
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isValid() || !enemy(arena, color, target)) {
                    mob.setTarget(nearestEnemy(arena, color, mob.getLocation(), 14));
                }
                left--;
            }
        }.runTaskTimer(plugin.core(), 0L, 20L);
    }

    private static boolean enemy(BwArena arena, TeamColor color, Entity entity) {
        if (entity instanceof Player player) {
            BwPlayer data = arena.data(player);
            return data != null && data.playing() && data.team != null && data.team != color;
        }
        TeamColor other = teamOf(entity);
        return other != null && other != color;
    }

    private static Player nearestEnemy(BwArena arena, TeamColor color, Location from, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : from.getWorld().getPlayers()) {
            if (!enemy(arena, color, player)) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    public static TeamColor teamOf(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(TEAM)) {
                for (TeamColor color : TeamColor.values()) {
                    if (color.id().equals(tag.substring(TEAM.length()))) {
                        return color;
                    }
                }
            }
        }
        return null;
    }

    /** A gólem vagy ezüstmoly gazdája (az ölést ő kapja). */
    public static Player ownerOf(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(OWNER)) {
                try {
                    return Bukkit.getPlayer(UUID.fromString(tag.substring(OWNER.length())));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getScoreboardTags().contains(MOB) || event.getTarget() == null) {
            return;
        }
        BwArena arena = plugin.arenaAt(entity.getLocation());
        TeamColor color = teamOf(entity);
        if (arena == null || color == null || !enemy(arena, color, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** Csapattárs nem üti a saját gólemét, a gólem nem üti a csapattársat. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHit(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();
        if (!inWorld(victim.getLocation())) {
            return;
        }
        if (victim.getScoreboardTags().contains(MOB)) {
            BwArena arena = plugin.arenaAt(victim.getLocation());
            TeamColor color = teamOf(victim);
            Entity source = damager instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof Entity e ? e : damager;
            if (arena == null || color == null || !enemy(arena, color, source)) {
                event.setCancelled(true);
            }
            return;
        }
        if (damager.getScoreboardTags().contains(MOB) && victim instanceof Player player) {
            BwArena arena = plugin.arenaOf(player);
            TeamColor color = teamOf(damager);
            if (arena == null || color == null || !enemy(arena, color, player)) {
                event.setCancelled(true);
                return;
            }
            Player owner = ownerOf(damager);
            BwPlayer data = arena.data(player);
            if (owner != null && data != null) {
                data.lastDamager = owner.getUniqueId();
                data.lastDamageAt = System.currentTimeMillis();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MOB)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // ---- varázstej ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onMilk(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() != Material.MILK_BUCKET || !inWorld(player.getLocation())) {
            return;
        }
        BwArena arena = playing(player);
        if (arena == null) {
            return;
        }
        BwPlayer data = arena.data(player);
        data.trapImmuneUntil = System.currentTimeMillis() + MILK_SECONDS * 1000L;
        event.setReplacement(new ItemStack(Material.AIR));
        say(player, "&bVarázstej: &f" + MILK_SECONDS + " mp&7-ig nem kapnak el a csapdák.");
    }
}
