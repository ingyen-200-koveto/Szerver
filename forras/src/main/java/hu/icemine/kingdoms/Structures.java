package hu.icemine.kingdoms;

import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Építmények (Nexus, Erőmag, Kitermelő, Gyógyforrás, Bástya, Kereskedőház, Teleportkapu) és tornyok (íjász, láng,
 * ágyú, varázs, kaszárnya, gyógyító, akna). Lerakás tárggyal a saját földön, jobb klikk a menü, a tagok fel is vehetik.
 * Az ellenség csak a megtámadott földön, invázió közben tudja lerombolni őket.
 */
public final class Structures implements Listener {

    static final String TAG = "kingdoms:";
    private final IceMineKingdoms plugin;
    private final Map<Long, Placed> index = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, Long> soldierUntil = new HashMap<>();
    private final NamespacedKey soldierKey;
    private int ticks;

    Structures(IceMineKingdoms plugin) {
        this.plugin = plugin;
        this.soldierKey = new NamespacedKey(plugin.plugin(), "kingdoms_soldier");
    }

    private KingdomManager km() {
        return plugin.manager();
    }

    private KConfig cfg() {
        return plugin.settings();
    }

    static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Induláskor: a lerakott dolgok indexe, és hogy a blokk a helyén van-e. */
    void rebuild() {
        index.clear();
        owners.clear();
        long now = System.currentTimeMillis();
        for (Kingdom kingdom : km().all()) {
            for (Placed placed : kingdom.placed) {
                index.put(blockKey(placed.x, placed.y, placed.z), placed);
                owners.put(placed.id, kingdom.id);
                placed.last = now;
            }
        }
    }

    public KConfig.Placeable def(Placed placed) {
        return placed.turret() ? cfg().turrets.get(placed.type) : cfg().structures.get(placed.type);
    }

    public KConfig.Placeable def(String kind, String type) {
        return kind.equals("turret") ? cfg().turrets.get(type) : cfg().structures.get(type);
    }

    public Placed at(Block block) {
        return block.getWorld() != plugin.world() ? null : index.get(blockKey(block.getX(), block.getY(), block.getZ()));
    }

    public Kingdom ownerOf(Placed placed) {
        return km().kingdom(owners.get(placed.id));
    }

    public Location location(Placed placed) {
        return new Location(plugin.world(), placed.x + 0.5, placed.y + 0.5, placed.z + 0.5);
    }

    // ---- tárgyak ----

    public ItemStack item(String kind, String type, int level) {
        KConfig.Placeable def = def(kind, type);
        if (def == null) {
            return new ItemStack(Material.STONE);
        }
        List<String> lore = new ArrayList<>(def.description);
        lore.add("");
        lore.add("&7Szint: &f" + level + "&7/&f" + def.max);
        lore.add(plugin.text(kind.equals("turret") ? "item-turret-hint" : "item-structure-hint"));
        return Items.of(Items.material(def.block, Material.STONE)).name(def.name).lore(lore).glow().tag(TAG + kind + ":" + type).value(level).build();
    }

    /** "turret:arrow" vagy "structure:extractor" a tárgy jelöléséből, vagy null. */
    static String[] parse(ItemStack item) {
        String tag = Items.tagOf(item);
        if (tag == null || !tag.startsWith(TAG)) {
            return null;
        }
        String[] parts = tag.substring(TAG.length()).split(":");
        return parts.length == 2 ? parts : null;
    }

    /** Vásárlás a menüből: RP-t von le, és odaadja a lerakható tárgyat. */
    public void buy(Player player, String kind, String type) {
        KPerm perm = kind.equals("turret") ? KPerm.TURRETS : KPerm.STRUCTURES;
        Kingdom kingdom = km().allowed(player, perm);
        KConfig.Placeable def = def(kind, type);
        if (kingdom == null || def == null) {
            return;
        }
        if (type.equals("nexus")) {
            giveNexus(player);
            return;
        }
        if (km().level(kingdom) < def.minLevel) {
            plugin.send(player, "need-level", "%level%", String.valueOf(def.minLevel));
            return;
        }
        if (!kingdom.hasNexus()) {
            plugin.send(player, "need-nexus");
            return;
        }
        if (kingdom.resourcePoints < def.price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(def.price));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            plugin.send(player, "inventory-full");
            return;
        }
        kingdom.resourcePoints -= def.price;
        kingdom.dirty = true;
        player.getInventory().addItem(item(kind, type, 1));
        plugin.send(player, "bought-placeable", "%name%", def.name, "%price%", String.valueOf(def.price));
    }

    public void giveNexus(Player player) {
        Kingdom kingdom = km().allowed(player, KPerm.STRUCTURES);
        if (kingdom == null) {
            return;
        }
        if (kingdom.hasNexus()) {
            plugin.send(player, "nexus-exists");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            String[] parsed = parse(item);
            if (parsed != null && parsed[1].equals("nexus")) {
                plugin.send(player, "nexus-in-inventory");
                return;
            }
        }
        player.getInventory().addItem(item("structure", "nexus", Math.max(1, kingdom.misc.getOrDefault("nexus-level", 1))));
        plugin.send(player, "nexus-given");
    }

    // ---- lerakás, felvétel ----

    /** Lerakás előtt: a saját földön, a jogokkal és a limitekkel összhangban van-e. Ha nem, letiltjuk. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String[] parsed = parse(event.getItemInHand());
        if (parsed == null) {
            return;
        }
        String problem = placeProblem(event.getPlayer(), event.getBlockPlaced(), parsed[0], parsed[1]);
        if (problem != null) {
            event.setCancelled(true);
            if (!problem.isEmpty()) {
                plugin.send(event.getPlayer(), problem, "%max%", String.valueOf(limitFor(event.getPlayer(), parsed[0], parsed[1])));
            }
        }
    }

    /** Ha a lerakás végül megtörtént (más plugin sem tiltotta le), felvesszük a királysághoz. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        String[] parsed = parse(event.getItemInHand());
        if (parsed == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        String kind = parsed[0];
        String type = parsed[1];
        KConfig.Placeable def = def(kind, type);
        Kingdom kingdom = km().kingdomOf(player.getUniqueId());
        if (def == null || kingdom == null) {
            return;
        }
        int level = Math.max(1, Math.min(def.max, Items.valueOf(event.getItemInHand())));
        Placed placed = new Placed(UUID.randomUUID(), kind, type, block.getX(), block.getY(), block.getZ(), level);
        placed.last = System.currentTimeMillis();
        if (kind.equals("turret")) {
            placed.ammo = cfg().ammoPerRefill;
        }
        kingdom.placed.add(placed);
        index.put(blockKey(placed.x, placed.y, placed.z), placed);
        owners.put(placed.id, kingdom.id);
        if (type.equals("nexus")) {
            kingdom.nexus = new int[]{placed.x, placed.y, placed.z};
            kingdom.log(player.getName() + " lerakta a Nexust.");
            km().tell(kingdom, "nexus-placed", "%player%", player.getName());
        } else {
            kingdom.log(player.getName() + " lerakta: " + plugin.strip(def.name) + ".");
            plugin.send(player, "placed", "%name%", def.name);
        }
        kingdom.dirty = true;
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1, 0.5), 20, 0.4, 0.4, 0.4, 0);
    }

    private int limitFor(Player player, String kind, String type) {
        KConfig.Placeable def = def(kind, type);
        if (kind.equals("turret")) {
            Kingdom kingdom = km().kingdomOf(player.getUniqueId());
            return turretsPerLand(kingdom);
        }
        return def == null ? 0 : def.limit;
    }

    private int turretsPerLand(Kingdom kingdom) {
        KConfig.Upgrade upgrade = cfg().misc.get("max-turrets");
        int extra = kingdom == null || upgrade == null ? 0 : (int) Math.round(upgrade.value(kingdom.level(kingdom.misc, "max-turrets")));
        return plugin.getConfig().getInt("turret-settings.per-land", 4) + extra;
    }

    /** Miért nem rakható le (üzenet kulcs), "" ha az üzenetet már elküldtük, null ha rendben van. */
    private String placeProblem(Player player, Block block, String kind, String type) {
        if (block.getWorld() != plugin.world()) {
            return "only-in-kingdoms";
        }
        KConfig.Placeable def = def(kind, type);
        Kingdom kingdom = km().allowed(player, kind.equals("turret") ? KPerm.TURRETS : KPerm.STRUCTURES);
        if (kingdom == null || def == null) {
            return "";
        }
        long chunk = Lands.key(block.getX() >> 4, block.getZ() >> 4);
        if (km().owner(chunk) != kingdom) {
            return "place-own-land";
        }
        if (type.equals("nexus")) {
            return kingdom.hasNexus() ? "nexus-exists" : null;
        }
        if (!kingdom.hasNexus()) {
            return "need-nexus";
        }
        if (def.limit > 0 && kingdom.count(kind, type) >= def.limit) {
            return "place-limit";
        }
        if (kind.equals("turret")) {
            int here = 0;
            for (Placed other : kingdom.placed) {
                if (other.turret() && other.chunk() == chunk) {
                    here++;
                }
            }
            if (here >= turretsPerLand(kingdom)) {
                return "turret-land-limit";
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Placed placed = at(event.getBlock());
        if (placed == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Kingdom owner = ownerOf(placed);
        if (owner == null) {
            remove(null, placed, false);
            return;
        }
        Kingdom mine = km().kingdomOf(player.getUniqueId());
        if (mine == owner) {
            if (placed.type.equals("nexus")) {
                plugin.send(player, "nexus-break-own");
                return;
            }
            if (!owner.can(player.getUniqueId(), placed.turret() ? KPerm.TURRETS : KPerm.STRUCTURES)) {
                plugin.send(player, "no-kingdom-permission", "%permission%", (placed.turret() ? KPerm.TURRETS : KPerm.STRUCTURES).title);
                return;
            }
            pickUp(player, owner, placed);
            return;
        }
        if (plugin.admin(player)) {
            remove(owner, placed, false);
            return;
        }
        if (!placed.type.equals("nexus") && plugin.invasions().attackerHere(player, placed.chunk())) {
            KConfig.Placeable def = def(placed);
            remove(owner, placed, false);
            owner.log(player.getName() + " lerombolta: " + plugin.strip(def == null ? placed.type : def.name) + ".");
            km().tell(owner, "structure-destroyed", "%player%", player.getName(), "%name%", def == null ? placed.type : def.name);
            player.getWorld().playSound(event.getBlock().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            return;
        }
        plugin.send(player, "structure-protected");
    }

    public void pickUp(Player player, Kingdom kingdom, Placed placed) {
        if (placed.type.equals("nexus") && plugin.invasions().defending(kingdom)) {
            plugin.send(player, "invasion-running");
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            plugin.send(player, "inventory-full");
            return;
        }
        remove(kingdom, placed, false);
        player.getInventory().addItem(item(placed.kind, placed.type, placed.level));
        KConfig.Placeable def = def(placed);
        kingdom.log(player.getName() + " felvette: " + plugin.strip(def == null ? placed.type : def.name) + ".");
        plugin.send(player, "picked-up", "%name%", def == null ? placed.type : def.name);
    }

    /** Eltávolítás a világból és a királyságból (a Nexusnál a királyság Nexusa is törlődik). */
    public void remove(Kingdom kingdom, Placed placed, boolean effect) {
        index.remove(blockKey(placed.x, placed.y, placed.z));
        owners.remove(placed.id);
        if (kingdom != null) {
            kingdom.placed.remove(placed);
            if (placed.type.equals("nexus")) {
                kingdom.nexus = null;
            }
            kingdom.dirty = true;
        }
        World world = plugin.world();
        if (world != null) {
            Block block = world.getBlockAt(placed.x, placed.y, placed.z);
            block.setType(Material.AIR, false);
            if (effect) {
                world.spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 1);
            }
        }
    }

    public void removeAll(Kingdom kingdom) {
        for (Placed placed : new ArrayList<>(kingdom.placed)) {
            remove(kingdom, placed, false);
        }
    }

    /** Egy földön lévő összes építmény és torony lerombolása (elfoglalt föld). */
    void destroyChunk(Kingdom kingdom, long chunk) {
        for (Placed placed : new ArrayList<>(kingdom.placed)) {
            if (placed.chunk() == chunk) {
                remove(kingdom, placed, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() != plugin.world()) {
            return;
        }
        Placed placed = at(block);
        if (placed == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (placed.type.equals("mine")) {
                triggerMine(event.getPlayer(), placed);
            }
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Kingdom owner = ownerOf(placed);
        if (owner == null) {
            return;
        }
        Relation relation = km().relation(player.getUniqueId(), owner);
        if (placed.type.equals("nexus")) {
            if (relation == Relation.SELF && owner.can(player.getUniqueId(), KPerm.NEXUS)) {
                plugin.menus().main(player);
            } else {
                plugin.menus().info(player, owner);
            }
            return;
        }
        if (relation == Relation.SELF || (relation == Relation.ALLY || relation == Relation.NATION) && (placed.type.equals("warppad") || placed.type.equals("outpost"))) {
            plugin.menus().placed(player, owner, placed);
            return;
        }
        plugin.send(player, "structure-protected");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> at(block) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld() == plugin.world()) {
            event.blockList().removeIf(block -> at(block) != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---- menü műveletek ----

    public void upgrade(Player player, Kingdom kingdom, Placed placed) {
        KConfig.Placeable def = def(placed);
        if (def == null || !kingdom.can(player.getUniqueId(), placed.turret() ? KPerm.TURRETS : KPerm.STRUCTURES)) {
            plugin.send(player, "no-kingdom-permission", "%permission%", (placed.turret() ? KPerm.TURRETS : KPerm.STRUCTURES).title);
            return;
        }
        long price = def.upgradePrice(placed.level);
        if (price < 0) {
            plugin.send(player, "upgrade-max", "%name%", def.name);
            return;
        }
        if (kingdom.resourcePoints < price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(price));
            return;
        }
        kingdom.resourcePoints -= price;
        placed.level++;
        kingdom.dirty = true;
        kingdom.log(player.getName() + " fejlesztette: " + plugin.strip(def.name) + " " + placed.level + ". szint.");
        plugin.send(player, "upgraded", "%name%", def.name, "%level%", String.valueOf(placed.level), "%max%", String.valueOf(def.max), "%price%", String.valueOf(price));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.3f);
    }

    public void refill(Player player, Kingdom kingdom, Placed placed) {
        if (!kingdom.can(player.getUniqueId(), KPerm.TURRETS)) {
            plugin.send(player, "no-kingdom-permission", "%permission%", KPerm.TURRETS.title);
            return;
        }
        long price = cfg().ammoCost * cfg().ammoPerRefill;
        if (kingdom.resourcePoints < price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(price));
            return;
        }
        kingdom.resourcePoints -= price;
        placed.ammo += cfg().ammoPerRefill;
        kingdom.dirty = true;
        plugin.send(player, "refilled", "%ammo%", String.valueOf(placed.ammo), "%price%", String.valueOf(price));
    }

    public void toggle(Player player, Kingdom kingdom, Placed placed) {
        if (kingdom.can(player.getUniqueId(), placed.turret() ? KPerm.TURRETS : KPerm.STRUCTURES)) {
            placed.enabled = !placed.enabled;
            kingdom.dirty = true;
        }
    }

    public void collect(Player player, Kingdom kingdom, Placed placed) {
        if (placed.stored <= 0) {
            plugin.send(player, "nothing-to-collect");
            return;
        }
        long points = placed.stored;
        placed.stored = 0;
        km().donate(player, kingdom, points);
        plugin.send(player, "collected", "%points%", String.valueOf(points));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    public void warp(Player player, Kingdom kingdom, Placed from, Placed to) {
        KConfig.Placeable def = def(from);
        long cooldown = def == null ? 30_000L : Math.round(def.get("cooldown", from.level) * 1000);
        long now = System.currentTimeMillis();
        if (now - from.last < cooldown) {
            plugin.send(player, "warppad-cooldown", "%seconds%", String.valueOf((cooldown - (now - from.last)) / 1000 + 1));
            return;
        }
        if (plugin.combatTagged(player)) {
            plugin.send(player, "in-combat");
            return;
        }
        from.last = now;
        Location target = new Location(plugin.world(), to.x + 0.5, to.y + 1, to.z + 0.5, player.getLocation().getYaw(), 0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
        player.teleport(target);
        player.getWorld().spawnParticle(Particle.PORTAL, target, 60, 0.5, 1, 0.5, 0.2);
    }

    public void buyFromOutpost(Player player, Kingdom kingdom, Material material, long price) {
        if (kingdom.resourcePoints < price) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(price));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            plugin.send(player, "inventory-full");
            return;
        }
        kingdom.resourcePoints -= price;
        kingdom.dirty = true;
        player.getInventory().addItem(new ItemStack(material));
        plugin.send(player, "outpost-bought", "%item%", material.name().toLowerCase().replace('_', ' '), "%price%", String.valueOf(price));
    }

    public List<Placed> of(Kingdom kingdom, String kind, String type) {
        List<Placed> list = new ArrayList<>();
        for (Placed placed : kingdom.placed) {
            if (placed.kind.equals(kind) && (type == null || placed.type.equals(type))) {
                list.add(placed);
            }
        }
        return list;
    }

    /** Védi-e egy Erőmag ezt a földet (a saját földjét nem: azt kell előbb elfoglalni). */
    public boolean powercellProtects(Kingdom kingdom, long chunk) {
        int cx = Lands.x(chunk);
        int cz = Lands.z(chunk);
        for (Placed placed : kingdom.placed) {
            if (placed.turret() || !placed.type.equals("powercell") || !placed.enabled || placed.chunk() == chunk) {
                continue;
            }
            KConfig.Placeable def = def(placed);
            int radius = def == null ? 1 : (int) Math.floor(def.get("radius", placed.level));
            if (Math.abs((placed.x >> 4) - cx) <= radius && Math.abs((placed.z >> 4) - cz) <= radius) {
                return true;
            }
        }
        return false;
    }

    // ---- működés ----

    /** Tíz tickenként: tornyok; másodpercenként: építmények. */
    void tick() {
        World world = plugin.world();
        if (world == null) {
            return;
        }
        ticks++;
        long now = System.currentTimeMillis();
        for (Kingdom kingdom : km().all()) {
            for (Placed placed : kingdom.placed) {
                if (!placed.enabled || !world.isChunkLoaded(placed.x >> 4, placed.z >> 4)) {
                    if (!placed.turret() && placed.type.equals("extractor")) {
                        produce(kingdom, placed, now);
                    }
                    continue;
                }
                KConfig.Placeable def = def(placed);
                if (def == null) {
                    continue;
                }
                if (placed.turret()) {
                    turret(world, kingdom, placed, def, now);
                } else if (ticks % 2 == 0) {
                    structure(world, kingdom, placed, def, now);
                }
            }
        }
        if (ticks % 4 == 0) {
            cleanupSoldiers(now);
        }
        if (ticks % 20 == 0) {
            checkBlocks(world);
        }
    }

    /**
     * Ha egy lerakott dolog blokkja eltűnt (pl. az akna alól kibontották a blokkot, és leesett), töröljük a nyilvántartásból,
     * különben egy oda rakott sima blokk örökölné a szerepét.
     */
    private void checkBlocks(World world) {
        for (Kingdom kingdom : km().all()) {
            for (Placed placed : new ArrayList<>(kingdom.placed)) {
                if (!world.isChunkLoaded(placed.x >> 4, placed.z >> 4)) {
                    continue;
                }
                KConfig.Placeable def = def(placed);
                if (def == null) {
                    continue;
                }
                String expected = def.block.toUpperCase(java.util.Locale.ROOT).replace("_WALL_", "_");
                String actual = world.getBlockAt(placed.x, placed.y, placed.z).getType().name().replace("_WALL_", "_");
                if (!actual.equals(expected)) {
                    index.remove(blockKey(placed.x, placed.y, placed.z));
                    owners.remove(placed.id);
                    kingdom.placed.remove(placed);
                    if (placed.type.equals("nexus")) {
                        kingdom.nexus = null;
                    }
                    kingdom.dirty = true;
                    kingdom.log("Eltűnt: " + plugin.strip(def.name) + " (" + placed.x + ", " + placed.y + ", " + placed.z + ").");
                }
            }
        }
    }

    private void produce(Kingdom kingdom, Placed placed, long now) {
        KConfig.Placeable def = def(placed);
        if (def == null) {
            return;
        }
        long every = Math.max(60_000L, Math.round(def.get("minutes", placed.level) * 60_000));
        if (now - placed.last < every) {
            return;
        }
        long steps = (now - placed.last) / every;
        placed.last += steps * every;
        long capacity = Math.round(def.get("capacity", placed.level));
        long before = placed.stored;
        placed.stored = Math.min(capacity, placed.stored + steps * Math.round(def.get("points", placed.level)));
        if (placed.stored != before) {
            kingdom.dirty = true;
        }
    }

    private void structure(World world, Kingdom kingdom, Placed placed, KConfig.Placeable def, long now) {
        Location center = location(placed);
        switch (placed.type) {
            case "extractor" -> {
                produce(kingdom, placed, now);
                if (placed.stored > 0 && ticks % 10 == 0) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0);
                }
            }
            case "regenerator" -> {
                if (ticks % 6 != 0) {
                    return;
                }
                double radius = def.get("radius", placed.level);
                double heal = def.get("heal", placed.level) * 2;
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) > radius * radius || !friendly(kingdom, player)) {
                        continue;
                    }
                    AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double limit = max == null ? 20 : max.getValue();
                    if (player.getHealth() < limit && !player.isDead()) {
                        player.setHealth(Math.min(limit, player.getHealth() + heal));
                        world.spawnParticle(Particle.HEART, player.getLocation().add(0, 2.1, 0), 1, 0.2, 0.1, 0.2, 0);
                    }
                }
            }
            case "bastion" -> {
                if (ticks % 4 != 0) {
                    return;
                }
                double radius = def.get("radius", placed.level);
                int strength = (int) Math.floor(def.get("strength", placed.level));
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) > radius * radius || player.getGameMode() == GameMode.CREATIVE) {
                        continue;
                    }
                    if (friendly(kingdom, player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, strength, true, false, true));
                    } else if (hostile(kingdom, player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, strength, true, true, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, strength, true, true, true));
                    }
                }
            }
            case "powercell", "nexus" -> {
                if (ticks % 10 == 0) {
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.8, 0), 2, 0.2, 0.3, 0.2, 0.01);
                }
            }
            default -> {
            }
        }
    }

    /** Barát: saját tag, szövetséges vagy nemzettárs. */
    public boolean friendly(Kingdom kingdom, Player player) {
        Relation relation = km().relation(player.getUniqueId(), kingdom);
        return relation == Relation.SELF || relation == Relation.NATION || relation == Relation.ALLY;
    }

    /** Ellenség: háborúban áll velünk, vagy épp inváziót indított ellenünk. */
    public boolean hostile(Kingdom kingdom, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.isDead() || plugin.vanished(player)) {
            return false;
        }
        if (plugin.invasions().attackerAgainst(player, kingdom)) {
            return true;
        }
        Relation relation = km().relation(player.getUniqueId(), kingdom);
        return !relation.turretCeasefire && relation == Relation.ENEMY;
    }

    private LivingEntity target(World world, Kingdom kingdom, Location from, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDistance && hostile(kingdom, player) && visible(from, player.getEyeLocation())) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best == null && cfg().turretsTargetMonsters) {
            for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
                if (entity instanceof Monster monster && !monster.getPersistentDataContainer().has(soldierKey, PersistentDataType.STRING)
                        && !plugin.invasions().isChampion(monster)) {
                    double distance = monster.getLocation().distanceSquared(from);
                    if (distance < bestDistance && visible(from, monster.getEyeLocation())) {
                        best = monster;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean visible(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 0.5) {
            return true;
        }
        return from.getWorld().rayTraceBlocks(from, direction.normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }

    private void turret(World world, Kingdom kingdom, Placed placed, KConfig.Placeable def, long now) {
        if (placed.type.equals("mine")) {
            return;
        }
        long cooldown = Math.max(5, Math.round(def.get("cooldown", placed.level))) * 50L;
        if (now - placed.last < cooldown || placed.ammo <= 0) {
            return;
        }
        Location from = location(placed).add(0, 0.4, 0);
        double range = def.get("range", placed.level);
        if (placed.type.equals("healing")) {
            boolean healed = false;
            double heal = def.get("heal", placed.level);
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(from) <= range * range && friendly(kingdom, player)) {
                    AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double limit = max == null ? 20 : max.getValue();
                    if (player.getHealth() < limit && !player.isDead()) {
                        player.setHealth(Math.min(limit, player.getHealth() + heal));
                        beam(world, from, player.getEyeLocation(), Particle.HAPPY_VILLAGER);
                        healed = true;
                    }
                }
            }
            if (healed) {
                placed.last = now;
                placed.ammo--;
                kingdom.dirty = true;
            }
            return;
        }
        LivingEntity target = target(world, kingdom, from, range);
        if (target == null) {
            return;
        }
        placed.last = now;
        placed.ammo--;
        kingdom.dirty = true;
        Location eye = target.getEyeLocation().subtract(0, 0.3, 0);
        Vector direction = eye.toVector().subtract(from.toVector()).normalize();
        switch (placed.type) {
            case "arrow" -> {
                Arrow arrow = world.spawnArrow(from.clone().add(direction.clone().multiply(0.8)), direction, 2.2f, 1.5f);
                arrow.setDamage(def.get("damage", placed.level) / 2.0);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer().set(soldierKey, PersistentDataType.STRING, "arrow:" + kingdom.id);
                world.playSound(from, Sound.ENTITY_SKELETON_SHOOT, 0.8f, 1.0f);
            }
            case "flame" -> {
                beam(world, from, eye, Particle.FLAME);
                target.setFireTicks(Math.max(target.getFireTicks(), (int) def.get("fire", placed.level)));
                damage(target, def.get("damage", placed.level), kingdom);
                world.playSound(from, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
            }
            case "cannon" -> {
                beam(world, from, eye, Particle.SMOKE);
                Location boom = target.getLocation();
                world.createExplosion(boom.getX(), boom.getY(), boom.getZ(), (float) def.get("power", placed.level), false, false);
            }
            case "magic" -> {
                beam(world, from, eye, Particle.WITCH);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, true, true));
                damage(target, def.get("damage", placed.level), kingdom);
                world.playSound(from, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.4f);
            }
            case "soldier" -> {
                int max = Math.max(1, (int) Math.floor(def.get("soldiers", placed.level)));
                int alive = 0;
                for (Map.Entry<UUID, UUID> entry : soldierOwners.entrySet()) {
                    if (entry.getValue().equals(placed.id)) {
                        alive++;
                    }
                }
                if (alive >= max) {
                    placed.ammo++;
                    return;
                }
                spawnSoldier(world, kingdom, placed, target);
            }
            default -> {
            }
        }
    }

    private final Map<UUID, UUID> soldierOwners = new HashMap<>();

    private void spawnSoldier(World world, Kingdom kingdom, Placed placed, LivingEntity target) {
        Block above = world.getBlockAt(placed.x, placed.y, placed.z).getRelative(BlockFace.UP);
        Location spawn = above.getLocation().add(0.5, 0, 0.5);
        Zombie soldier = world.spawn(spawn, Zombie.class, zombie -> {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(true);
            zombie.setPersistent(false);
            zombie.setCustomName(plugin.color(plugin.text("soldier-name").replace("%kingdom%", kingdom.display())));
            zombie.setCustomNameVisible(true);
            zombie.getPersistentDataContainer().set(soldierKey, PersistentDataType.STRING, kingdom.id.toString());
            if (zombie.getEquipment() != null) {
                zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                zombie.getEquipment().setHelmetDropChance(0f);
                zombie.getEquipment().setItemInMainHandDropChance(0f);
            }
        });
        soldier.setTarget(target);
        soldierOwners.put(soldier.getUniqueId(), placed.id);
        soldierUntil.put(soldier.getUniqueId(), System.currentTimeMillis() + 40_000L);
        world.playSound(spawn, Sound.ENTITY_ZOMBIE_AMBIENT, 0.8f, 0.8f);
    }

    private void cleanupSoldiers(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = soldierUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || now > entry.getValue()) {
                if (entity != null) {
                    entity.remove();
                }
                soldierOwners.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    void removeSoldiers() {
        for (UUID id : soldierUntil.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        soldierUntil.clear();
        soldierOwners.clear();
    }

    private void triggerMine(Player player, Placed placed) {
        Kingdom owner = ownerOf(placed);
        if (owner == null || !hostile(owner, player) || placed.ammo <= 0) {
            return;
        }
        KConfig.Placeable def = def(placed);
        placed.ammo--;
        owner.dirty = true;
        Location at = location(placed);
        plugin.world().createExplosion(at.getX(), at.getY(), at.getZ(), (float) (def == null ? 2 : def.get("power", placed.level)), false, false);
    }

    private void damage(LivingEntity target, double amount, Kingdom kingdom) {
        if (amount > 0) {
            target.damage(amount);
        }
    }

    private static void beam(World world, Location from, Location to, Particle particle) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1) {
            return;
        }
        step.normalize().multiply(0.5);
        Location point = from.clone();
        for (double travelled = 0; travelled < length; travelled += 0.5) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    /** A katonák és a tornyok nyilai nem bántják a királyság tagjait és barátait. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        String owner = damager.getPersistentDataContainer().get(soldierKey, PersistentDataType.STRING);
        if (owner == null || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Kingdom kingdom = km().kingdom(Kingdom.uuid(owner.startsWith("arrow:") ? owner.substring(6) : owner));
        if (kingdom != null && friendly(kingdom, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        String owner = event.getEntity().getPersistentDataContainer().get(soldierKey, PersistentDataType.STRING);
        if (owner == null || !(event.getTarget() instanceof Player player)) {
            return;
        }
        Kingdom kingdom = km().kingdom(Kingdom.uuid(owner));
        if (kingdom != null && !hostile(kingdom, player)) {
            event.setCancelled(true);
        }
    }

    /** A katonák nem dobnak semmit. */
    boolean isSoldier(Entity entity) {
        return entity.getPersistentDataContainer().has(soldierKey, PersistentDataType.STRING);
    }

    /** Véletlen hely a földön belül (a kaszárnyához és a bajnok erősítéséhez). */
    static Location around(Location center, double radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return center.clone().add(random.nextDouble(-radius, radius), 0, random.nextDouble(-radius, radius));
    }
}
