package hu.icemine.core.fyland;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Csatatér: egy PvP aréna a Fyland központja mellett (híd vezet oda). Csak itt lehet egymást ütni, és aki itt hal
 * meg, annak leesnek a tárgyai. Időnként ellátmány láda (airdrop) érkezik az aréna egy véletlen pontjára.
 */
public final class FyBattle implements Listener {

    private final FylandMode mode;
    private final Map<Long, Long> airdrops = new HashMap<>();
    private final Set<UUID> inside = new HashSet<>();
    private List<FyPhase.Loot> loot = new ArrayList<>();
    private boolean enabled = true;
    private int centerX = 60;
    private int centerZ = 0;
    private int radius = 22;
    private boolean airdropEnabled = true;
    private long airdropEvery = 20 * 60_000L;
    private long airdropLife = 5 * 60_000L;
    private int airdropMinPlayers = 2;
    private double bookChance = 0.35;
    private long nextAirdrop;

    FyBattle(FylandMode mode) {
        this.mode = mode;
    }

    void load(YamlConfiguration config, List<String> problems) {
        enabled = config.getBoolean("battlefield.enabled", true);
        centerX = config.getInt("battlefield.x", 60);
        centerZ = config.getInt("battlefield.z", 0);
        radius = Math.max(8, Math.min(60, config.getInt("battlefield.radius", 22)));
        airdropEnabled = config.getBoolean("airdrop.enabled", true);
        airdropEvery = Math.max(1, config.getLong("airdrop.minutes", 20)) * 60_000L;
        airdropLife = Math.max(1, config.getLong("airdrop.despawn-minutes", 5)) * 60_000L;
        airdropMinPlayers = Math.max(0, config.getInt("airdrop.min-players", 2));
        bookChance = Math.max(0, Math.min(1, config.getDouble("airdrop.enchant-book-chance", 0.35)));
        List<String> specs = config.isSet("airdrop.loot") ? config.getStringList("airdrop.loot")
                : config.getDefaults() == null ? List.of() : config.getDefaults().getStringList("airdrop.loot");
        loot = FyPhase.loot("airdrop.loot", specs, problems);
        if (nextAirdrop == 0) {
            nextAirdrop = System.currentTimeMillis() + airdropEvery;
        }
        if (enabled && mode.world() != null) {
            build(config);
        }
    }

    boolean enabled() {
        return enabled;
    }

    private int floorY() {
        return mode.islandY();
    }

    public boolean inArena(Location location) {
        if (!enabled || location == null || location.getWorld() != mode.world()) {
            return false;
        }
        double dx = location.getX() - (centerX + 0.5);
        double dz = location.getZ() - (centerZ + 0.5);
        double y = location.getY();
        return dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5) && y > floorY() - 12 && y < floorY() + 40;
    }

    /** Lehet-e ütni: csak ha mindketten a csatatéren vannak. */
    boolean pvp(Player attacker, Player victim) {
        return attacker != victim && inArena(attacker.getLocation()) && inArena(victim.getLocation());
    }

    Location entrance() {
        return new Location(mode.world(), centerX - radius + 2.5, floorY() + 1, centerZ + 0.5, -90, 0);
    }

    void teleport(Player player) {
        if (!enabled || mode.world() == null) {
            mode.send(player, "battle-off");
            return;
        }
        if (!mode.inWorld(player)) {
            mode.core().modes().sendAndReport(player, FylandMode.ID);
            if (!mode.inWorld(player)) {
                return;
            }
        }
        if (mode.core().combat().tagged(player)) {
            mode.core().messages().send(player, "combat.blocked-generic");
            return;
        }
        mode.teleport(player, entrance());
        mode.send(player, "battle-teleport");
    }

    // ---- építés ----

    private void build(YamlConfiguration config) {
        if (config.getBoolean("battlefield.built", false)) {
            return;
        }
        World world = mode.world();
        int y = floorY();
        for (int cx = (centerX - radius - 1) >> 4; cx <= (centerX + radius + 1) >> 4; cx++) {
            for (int cz = (centerZ - radius - 1) >> 4; cz <= (centerZ + radius + 1) >> 4; cz++) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.4) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                Material top;
                if (d > radius - 0.8) {
                    top = Material.POLISHED_BLACKSTONE_BRICKS;
                } else if ((Math.abs(dx) + Math.abs(dz)) % 7 == 0) {
                    top = Material.CRACKED_STONE_BRICKS;
                } else if (d < 3.5) {
                    top = Material.CHISELED_STONE_BRICKS;
                } else {
                    top = (dx * 31 + dz * 17 & 3) == 0 ? Material.ANDESITE : Material.POLISHED_ANDESITE;
                }
                world.getBlockAt(x, y, z).setType(top, false);
                world.getBlockAt(x, y - 1, z).setType(Material.SMOOTH_STONE, false);
                if (d < radius - 3) {
                    world.getBlockAt(x, y - 2, z).setType(Material.STONE, false);
                }
                if (d > radius - 0.8) {
                    boolean gate = Math.abs(dz) <= 1 && dx < 0;
                    if (!gate) {
                        world.getBlockAt(x, y + 1, z).setType(Material.POLISHED_BLACKSTONE_WALL, false);
                        if ((dx + dz & 3) == 0) {
                            world.getBlockAt(x, y + 2, z).setType(Material.SOUL_LANTERN, false);
                        }
                    }
                }
            }
        }
        for (int[] cover : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}, {12, 0}, {0, 12}, {0, -12}}) {
            for (int h = 1; h <= 2; h++) {
                world.getBlockAt(centerX + cover[0], y + h, centerZ + cover[1]).setType(Material.MOSSY_STONE_BRICKS, false);
            }
            world.getBlockAt(centerX + cover[0] + 1, y + 1, centerZ + cover[1]).setType(Material.MOSSY_STONE_BRICK_SLAB, false);
        }
        int bridgeFrom = 5;
        int bridgeTo = centerX - radius;
        for (int x = bridgeFrom; x <= bridgeTo; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x, y, centerZ + dz).setType(Material.SPRUCE_PLANKS, false);
            }
            world.getBlockAt(x, y, centerZ - 2).setType(Material.SPRUCE_LOG, false);
            world.getBlockAt(x, y, centerZ + 2).setType(Material.SPRUCE_LOG, false);
            world.getBlockAt(x, y + 1, centerZ - 2).setType(Material.SPRUCE_FENCE, false);
            world.getBlockAt(x, y + 1, centerZ + 2).setType(Material.SPRUCE_FENCE, false);
            if ((x - bridgeFrom) % 6 == 0) {
                world.getBlockAt(x, y + 2, centerZ - 2).setType(Material.LANTERN, false);
                world.getBlockAt(x, y + 2, centerZ + 2).setType(Material.LANTERN, false);
            }
        }
        Location label = new Location(world, bridgeTo - 1.5, y + 3.2, centerZ + 0.5);
        world.spawn(label, TextDisplay.class, display -> {
            display.setText(Text.color(mode.text("battle-sign")));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(true);
            display.addScoreboardTag("icemine_fyland_sign");
        });
        config.set("battlefield.built", true);
        mode.core().configs().save(FylandMode.FILE);
    }

    // ---- halál ----

    /** Halál a csatatéren: a tárgyak leesnek (a szint megmarad). */
    void died(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location at = player.getLocation();
        event.getDrops().clear();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, item);
            }
        }
        player.getInventory().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Player killer = player.getKiller();
        if (killer != null) {
            for (Player other : mode.world().getPlayers()) {
                if (inArena(other.getLocation())) {
                    mode.send(other, "battle-kill", "%killer%", killer.getName(), "%victim%", player.getName());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (event.getEntity().getWorld() != mode.world() || !(event.getEntity().getShooter() instanceof Player thrower)) {
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player target && target != thrower && !pvp(thrower, target)) {
                event.setIntensity(target, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        if (event.getEntity().getWorld() != mode.world() || !(event.getEntity().getSource() instanceof Player thrower)) {
            return;
        }
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player target && target != thrower && !pvp(thrower, target));
    }

    // ---- ellátmány láda ----

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    boolean airdropAt(Block block) {
        return block.getWorld() == mode.world() && airdrops.containsKey(key(block.getX(), block.getY(), block.getZ()));
    }

    void tick() {
        World world = mode.world();
        if (world == null || !enabled) {
            return;
        }
        for (Player player : world.getPlayers()) {
            boolean now = inArena(player.getLocation());
            if (now && inside.add(player.getUniqueId())) {
                player.sendTitle(Text.color(mode.text("battle-enter-title")), Text.color(mode.text("battle-enter-subtitle")), 5, 40, 10);
            } else if (!now && inside.remove(player.getUniqueId())) {
                player.sendActionBar(Text.color(mode.text("battle-leave")));
            }
        }
        inside.removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            return player == null || player.getWorld() != world;
        });
        long time = System.currentTimeMillis();
        Iterator<Map.Entry<Long, Long>> iterator = airdrops.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            Block block = blockOf(world, entry.getKey());
            if (block.getType() != Material.CHEST || entry.getValue() < time) {
                clear(block);
                iterator.remove();
                continue;
            }
            Location top = block.getLocation().add(0.5, 1.2, 0.5);
            world.spawnParticle(Particle.END_ROD, top, 6, 0.15, 1.5, 0.15, 0.02);
            world.spawnParticle(Particle.FIREWORK, top.clone().add(0, 3, 0), 2, 0.3, 0.3, 0.3, 0.02);
        }
        if (airdropEnabled && time >= nextAirdrop) {
            nextAirdrop = time + airdropEvery;
            if (world.getPlayers().size() >= airdropMinPlayers) {
                spawnAirdrop();
            }
        }
    }

    private static Block blockOf(World world, long key) {
        int x = (int) (key >> 38);
        int z = (int) (key << 26 >> 38);
        int y = (int) (key << 52 >> 52);
        return world.getBlockAt(x, y, z);
    }

    private void clear(Block block) {
        if (block.getType() == Material.CHEST && block.getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
        }
        if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR, false);
        }
    }

    /** Új ellátmány láda a csatatér egy véletlen pontjára. Igazat ad, ha sikerült. */
    boolean spawnAirdrop() {
        World world = mode.world();
        if (world == null || !enabled) {
            return false;
        }
        java.util.Random random = mode.random();
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * (radius - 3);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int z = centerZ + (int) Math.round(Math.sin(angle) * distance);
            Block block = world.getBlockAt(x, floorY() + 1, z);
            if (!block.getType().isAir() || !block.getRelative(0, -1, 0).getType().isSolid()) {
                continue;
            }
            block.setType(Material.CHEST, false);
            if (!(block.getState() instanceof Chest chest)) {
                block.setType(Material.AIR, false);
                return false;
            }
            fill(chest.getBlockInventory());
            airdrops.put(key(x, block.getY(), z), System.currentTimeMillis() + airdropLife);
            world.strikeLightningEffect(block.getLocation());
            for (Player player : world.getPlayers()) {
                mode.click(player, "airdrop-spawned", "%x%", String.valueOf(x), "%z%", String.valueOf(z),
                        "%minutes%", String.valueOf(airdropLife / 60_000L));
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 0.8f);
            }
            return true;
        }
        return false;
    }

    private void fill(Inventory inventory) {
        java.util.Random random = mode.random();
        List<ItemStack> items = new ArrayList<>();
        for (FyPhase.Loot entry : loot) {
            ItemStack item = entry.roll(random);
            if (item != null) {
                items.add(item);
            }
        }
        if (random.nextDouble() < bookChance) {
            ItemStack book = mode.enchants().randomBook();
            if (book != null) {
                items.add(book);
            }
        }
        if (items.isEmpty()) {
            items.add(new ItemStack(Material.GOLDEN_APPLE, 2));
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots, random);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inventory.setItem(slots.get(i), items.get(i));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest) || !airdropAt(chest.getBlock())) {
            return;
        }
        if (event.getInventory().isEmpty()) {
            Block block = chest.getBlock();
            airdrops.remove(key(block.getX(), block.getY(), block.getZ()));
            block.setType(Material.AIR, false);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    void shutdown() {
        World world = mode.world();
        if (world == null) {
            return;
        }
        for (long key : airdrops.keySet()) {
            clear(blockOf(world, key));
        }
        airdrops.clear();
    }

    // ---- /fy warp menü ----

    void warpMenu(Player player) {
        if (!mode.inWorld(player)) {
            mode.send(player, "only-here");
            return;
        }
        Menu menu = new Menu("fyland-warps", 3, mode.text("menu.warps-title"));
        menu.frame(Material.BLACK_STAINED_GLASS_PANE, null);
        menu.set(10, Items.of(Material.RED_BED).name(mode.text("menu.warp-home")).lore(mode.texts("menu.warp-home-lore")).build(), clicker -> {
            clicker.closeInventory();
            mode.goHome(clicker);
        });
        menu.set(11, Items.of(Material.LODESTONE).name(mode.text("menu.warp-spawn")).lore(mode.texts("menu.warp-spawn-lore")).build(), clicker -> {
            clicker.closeInventory();
            mode.teleport(clicker, mode.world().getSpawnLocation().add(0.5, 0, 0.5));
        });
        if (enabled) {
            menu.set(13, Items.of(Material.DIAMOND_SWORD).name(mode.text("menu.warp-battle")).lore(mode.texts("menu.warp-battle-lore",
                    "%players%", String.valueOf(inside.size()), "%airdrops%", String.valueOf(airdrops.size()),
                    "%next%", Text.duration(Math.max(0, nextAirdrop - System.currentTimeMillis())))).hideAll().build(), clicker -> {
                clicker.closeInventory();
                teleport(clicker);
            });
        }
        menu.set(15, Items.of(Material.OAK_SIGN).name(mode.text("menu.warp-pw")).lore(mode.texts("menu.warp-pw-lore")).build(), clicker -> mode.social().warps(clicker, 0));
        menu.set(16, Items.of(Material.ENCHANTING_TABLE).name(mode.text("menu.warp-enchanter")).lore(mode.texts("menu.warp-enchanter-lore")).build(),
                clicker -> mode.enchants().open(clicker));
        menu.open(player);
    }
}
