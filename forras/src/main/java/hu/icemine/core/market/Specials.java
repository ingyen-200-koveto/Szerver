package hu.icemine.core.market;

import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Különleges blokkok, amiket a boltban lehet venni:
 * eladó láda (eladja, ami benne van, és felszívja és eladja, ami a közelébe esik),
 * craftoló (a beletett vasat, aranyat, gyémántot... blokká tömöríti),
 * farm blokk (betöltve tartja a környező chunkokat, hogy a farmok akkor is menjenek, ha senki nincs ott).
 */
public final class Specials implements Listener {

    static final String TAG = "market:special:";
    static final String SELL_CHEST = "sellchest";
    static final String CRAFTER = "crafter";
    static final String FARM_BLOCK = "farmblock";

    /** Egy lerakott különleges blokk. */
    static final class Placed {
        final String world;
        final int x;
        final int y;
        final int z;
        final String type;
        final UUID owner;
        final String scope;
        long earned;

        Placed(String world, int x, int y, int z, String type, UUID owner, String scope, long earned) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.owner = owner;
            this.scope = scope;
            this.earned = earned;
        }

        String key() {
            return key(world, x, y, z);
        }

        static String key(String world, int x, int y, int z) {
            return world + ":" + x + ":" + y + ":" + z;
        }

        MarketStore.SpecialRow row() {
            return new MarketStore.SpecialRow(world, x, y, z, type, owner.toString(), scope, earned);
        }
    }

    /** Tömörítés: ennyi darabból lesz egy eredmény. */
    record Recipe(int count, Material result) {
    }

    static final Map<Material, Recipe> RECIPES = new EnumMap<>(Material.class);

    static {
        recipe(Material.IRON_INGOT, 9, Material.IRON_BLOCK);
        recipe(Material.GOLD_INGOT, 9, Material.GOLD_BLOCK);
        recipe(Material.DIAMOND, 9, Material.DIAMOND_BLOCK);
        recipe(Material.EMERALD, 9, Material.EMERALD_BLOCK);
        recipe(Material.REDSTONE, 9, Material.REDSTONE_BLOCK);
        recipe(Material.LAPIS_LAZULI, 9, Material.LAPIS_BLOCK);
        recipe(Material.COAL, 9, Material.COAL_BLOCK);
        recipe(Material.COPPER_INGOT, 9, Material.COPPER_BLOCK);
        recipe(Material.NETHERITE_INGOT, 9, Material.NETHERITE_BLOCK);
        recipe(Material.RAW_IRON, 9, Material.RAW_IRON_BLOCK);
        recipe(Material.RAW_GOLD, 9, Material.RAW_GOLD_BLOCK);
        recipe(Material.RAW_COPPER, 9, Material.RAW_COPPER_BLOCK);
        recipe(Material.IRON_NUGGET, 9, Material.IRON_INGOT);
        recipe(Material.GOLD_NUGGET, 9, Material.GOLD_INGOT);
        recipe(Material.WHEAT, 9, Material.HAY_BLOCK);
        recipe(Material.SLIME_BALL, 9, Material.SLIME_BLOCK);
        recipe(Material.BONE_MEAL, 9, Material.BONE_BLOCK);
        recipe(Material.DRIED_KELP, 9, Material.DRIED_KELP_BLOCK);
        recipe(Material.QUARTZ, 4, Material.QUARTZ_BLOCK);
        recipe(Material.AMETHYST_SHARD, 4, Material.AMETHYST_BLOCK);
        recipe(Material.CLAY_BALL, 4, Material.CLAY);
        recipe(Material.SNOWBALL, 4, Material.SNOW_BLOCK);
        recipe(Material.MELON_SLICE, 9, Material.MELON);
    }

    private static void recipe(Material from, int count, Material to) {
        RECIPES.put(from, new Recipe(count, to));
    }

    private final Market market;
    private final Map<String, Placed> placed = new LinkedHashMap<>();
    private final Map<String, List<String>> allowed = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, List<String>> lores = new HashMap<>();
    private int sellInterval = 10;
    private double pickupRadius = 2.5;
    private int crafterInterval = 5;
    private int farmRadius = 1;
    private int farmMax = 2;
    private int ticks;

    Specials(Market market) {
        this.market = market;
    }

    void loadAll() throws Exception {
        placed.clear();
        for (MarketStore.SpecialRow row : market.store().loadSpecials()) {
            try {
                Placed entry = new Placed(row.world(), row.x(), row.y(), row.z(), row.type(), UUID.fromString(row.owner()), row.scope(), row.stored());
                placed.put(entry.key(), entry);
            } catch (IllegalArgumentException ignored) {
                // sérült sor, kihagyjuk
            }
        }
        Bukkit.getScheduler().runTask(market.core(), () -> {
            for (Placed entry : placed.values()) {
                if (entry.type.equals(FARM_BLOCK)) {
                    tickets(entry, true);
                }
            }
        });
    }

    void load(YamlConfiguration config, List<String> problems) {
        sellInterval = Math.max(2, config.getInt("specials.sellchest.interval-seconds", 10));
        pickupRadius = Math.max(0, Math.min(8, config.getDouble("specials.sellchest.pickup-radius", 2.5)));
        crafterInterval = Math.max(1, config.getInt("specials.crafter.interval-seconds", 5));
        farmRadius = Math.max(0, Math.min(3, config.getInt("specials.farmblock.radius", 1)));
        farmMax = Math.max(1, config.getInt("specials.farmblock.max-per-player", 2));
        for (String type : new String[]{SELL_CHEST, CRAFTER, FARM_BLOCK}) {
            List<String> scopes = new ArrayList<>();
            for (String scope : config.getStringList("specials." + type + ".scopes")) {
                scopes.add(scope.toLowerCase(Locale.ROOT));
            }
            allowed.put(type, scopes);
            names.put(type, config.getString("specials." + type + ".name", type));
            lores.put(type, config.getStringList("specials." + type + ".lore"));
        }
    }

    static Material block(String type) {
        return switch (type) {
            case SELL_CHEST -> Material.CHEST;
            case CRAFTER -> Material.DROPPER;
            case FARM_BLOCK -> Material.LODESTONE;
            default -> null;
        };
    }

    public ItemStack item(String type) {
        Material material = block(type);
        if (material == null) {
            return null;
        }
        return Items.of(material).name(names.getOrDefault(type, type)).lore(lores.getOrDefault(type, List.of())).glow().tag(TAG + type).build();
    }

    static String typeOf(ItemStack item) {
        String tag = Items.tagOf(item);
        return tag != null && tag.startsWith(TAG) ? tag.substring(TAG.length()) : null;
    }

    Placed at(Block block) {
        return placed.get(Placed.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    private int count(UUID owner, String type) {
        int count = 0;
        for (Placed entry : placed.values()) {
            if (entry.owner.equals(owner) && entry.type.equals(type)) {
                count++;
            }
        }
        return count;
    }

    // ---- lerakás, felvétel ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        String type = typeOf(event.getItemInHand());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        Market.Scope scope = market.scopeOf(event.getBlock().getWorld());
        List<String> scopes = allowed.getOrDefault(type, List.of());
        if (scope == null || (!scopes.isEmpty() && !scopes.contains(scope.id()))) {
            event.setCancelled(true);
            market.send(player, "special-wrong-place");
            return;
        }
        if (type.equals(FARM_BLOCK) && count(player.getUniqueId(), FARM_BLOCK) >= farmMax && !player.hasPermission("icemine.market.farmblock.unlimited")) {
            event.setCancelled(true);
            market.send(player, "farmblock-limit", "%max%", String.valueOf(farmMax));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String type = typeOf(event.getItemInHand());
        Market.Scope scope = type == null ? null : market.scopeOf(event.getBlock().getWorld());
        if (scope == null) {
            return;
        }
        Block block = event.getBlock();
        Placed entry = new Placed(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), type, event.getPlayer().getUniqueId(), scope.id(), 0);
        placed.put(entry.key(), entry);
        market.store().saveSpecial(entry.row());
        if (type.equals(FARM_BLOCK)) {
            tickets(entry, true);
        }
        market.send(event.getPlayer(), "special-placed-" + type, "%radius%", String.valueOf(farmRadius * 2 + 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Placed entry = at(event.getBlock());
        if (entry == null) {
            return;
        }
        Block block = event.getBlock();
        BlockState state = block.getState();
        Location drop = block.getLocation().add(0.5, 0.5, 0.5);
        if (state instanceof Container container) {
            Inventory inventory = container.getInventory();
            for (ItemStack item : inventory.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(drop, item);
                }
            }
            inventory.clear();
        }
        event.setDropItems(false);
        ItemStack item = item(entry.type);
        if (item != null) {
            block.getWorld().dropItemNaturally(drop, item);
        }
        remove(entry);
    }

    private void remove(Placed entry) {
        placed.remove(entry.key());
        market.store().deleteSpecial(entry.world, entry.x, entry.y, entry.z);
        if (entry.type.equals(FARM_BLOCK)) {
            tickets(entry, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> at(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> at(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (at(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void tickets(Placed entry, boolean add) {
        World world = Bukkit.getWorld(entry.world);
        if (world == null) {
            return;
        }
        int cx = entry.x >> 4;
        int cz = entry.z >> 4;
        for (int dx = -farmRadius; dx <= farmRadius; dx++) {
            for (int dz = -farmRadius; dz <= farmRadius; dz++) {
                if (add) {
                    world.addPluginChunkTicket(cx + dx, cz + dz, market.core());
                } else if (!stillNeeded(entry, cx + dx, cz + dz)) {
                    world.removePluginChunkTicket(cx + dx, cz + dz, market.core());
                }
            }
        }
    }

    /** Egy másik farm blokk is tartja-e ezt a chunkot. */
    private boolean stillNeeded(Placed removed, int cx, int cz) {
        for (Placed other : placed.values()) {
            if (other != removed && other.type.equals(FARM_BLOCK) && other.world.equals(removed.world)
                    && Math.abs((other.x >> 4) - cx) <= farmRadius && Math.abs((other.z >> 4) - cz) <= farmRadius) {
                return true;
            }
        }
        return false;
    }

    // ---- működés ----

    /** Másodpercenként. */
    void tick() {
        ticks++;
        boolean sell = ticks % sellInterval == 0;
        boolean pickup = ticks % 2 == 0 && pickupRadius > 0;
        boolean craft = ticks % crafterInterval == 0;
        Map<UUID, Map<String, Long>> earnings = new HashMap<>();
        Iterator<Placed> iterator = new ArrayList<>(placed.values()).iterator();
        while (iterator.hasNext()) {
            Placed entry = iterator.next();
            World world = Bukkit.getWorld(entry.world);
            if (world == null || !world.isChunkLoaded(entry.x >> 4, entry.z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(entry.x, entry.y, entry.z);
            Material expected = block(entry.type);
            if (expected != null && block.getType() != expected) {
                remove(entry);
                continue;
            }
            switch (entry.type) {
                case SELL_CHEST -> {
                    long earned = 0;
                    if (pickup) {
                        earned += pickup(entry, block);
                    }
                    if (sell) {
                        earned += sellContents(entry, block);
                    }
                    if (earned > 0) {
                        entry.earned += earned;
                        earnings.computeIfAbsent(entry.owner, key -> new HashMap<>()).merge(entry.scope, earned, Long::sum);
                    }
                }
                case CRAFTER -> {
                    if (craft) {
                        compact(block);
                    }
                }
                default -> {
                }
            }
        }
        for (Map.Entry<UUID, Map<String, Long>> owner : earnings.entrySet()) {
            for (Map.Entry<String, Long> perScope : owner.getValue().entrySet()) {
                Market.Scope scope = market.scope(perScope.getKey());
                if (scope == null) {
                    continue;
                }
                long amount = perScope.getValue();
                Player online = Bukkit.getPlayer(owner.getKey());
                if (online != null) {
                    amount = market.core().ranks().boost(online, amount);
                    online.sendActionBar(Text.color(market.text("sellchest-earned", "%money%", market.money(scope, amount))));
                }
                market.pay(owner.getKey(), scope, amount, "market:sellchest");
            }
        }
        if (ticks % 300 == 0) {
            for (Placed entry : placed.values()) {
                if (entry.type.equals(SELL_CHEST) && entry.earned > 0) {
                    market.store().saveSpecial(entry.row());
                }
            }
        }
    }

    private long sellContents(Placed entry, Block block) {
        if (!(block.getState() instanceof Container container)) {
            return 0;
        }
        Inventory inventory = container.getInventory();
        double total = 0;
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            double unit = market.shop().unitSellPrice(entry.scope, item);
            if (unit <= 0) {
                continue;
            }
            total += unit * item.getAmount();
            market.shop().sold(entry.scope, item, item.getAmount());
            contents[i] = null;
            changed = true;
        }
        if (changed) {
            inventory.setContents(contents);
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.1, 0.5), 6, 0.3, 0.2, 0.3, 0);
        }
        return Math.round(total);
    }

    private long pickup(Placed entry, Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        double total = 0;
        for (Entity entity : block.getWorld().getNearbyEntities(center, pickupRadius, pickupRadius, pickupRadius)) {
            if (!(entity instanceof Item item) || !item.isValid() || item.getPickupDelay() > 1000) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            double unit = market.shop().unitSellPrice(entry.scope, stack);
            if (unit <= 0) {
                continue;
            }
            total += unit * stack.getAmount();
            market.shop().sold(entry.scope, stack, stack.getAmount());
            item.remove();
        }
        return Math.round(total);
    }

    /** A craftoló tartalmának tömörítése (pl. 9 vasrúd = 1 vasblokk). Ha az eredmény nem fér el, nem csinál semmit. */
    static boolean compact(Inventory inventory) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        List<ItemStack> others = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            boolean plain = item.getItemMeta() == null || !item.getItemMeta().hasDisplayName() && !item.getItemMeta().hasLore() && !item.getItemMeta().hasEnchants();
            if (plain && RECIPES.containsKey(item.getType())) {
                totals.merge(item.getType(), item.getAmount(), Integer::sum);
            } else {
                others.add(item);
            }
        }
        boolean any = false;
        Map<Material, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
            Recipe recipe = RECIPES.get(entry.getKey());
            int made = entry.getValue() / recipe.count();
            int left = entry.getValue() % recipe.count();
            if (made > 0) {
                any = true;
                result.merge(recipe.result(), made, Integer::sum);
            }
            if (left > 0) {
                result.merge(entry.getKey(), left, Integer::sum);
            }
        }
        if (!any) {
            return false;
        }
        List<ItemStack> fresh = new ArrayList<>(others);
        for (Map.Entry<Material, Integer> entry : result.entrySet()) {
            int amount = entry.getValue();
            int max = entry.getKey().getMaxStackSize();
            while (amount > 0) {
                int part = Math.min(max, amount);
                fresh.add(new ItemStack(entry.getKey(), part));
                amount -= part;
            }
        }
        if (fresh.size() > inventory.getSize()) {
            return false;
        }
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int i = 0; i < fresh.size(); i++) {
            contents[i] = fresh.get(i);
        }
        inventory.setContents(contents);
        return true;
    }

    private void compact(Block block) {
        if (block.getState() instanceof Container container && compact(container.getInventory())) {
            block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 1.1, 0.5), 5, 0.2, 0.1, 0.2, 0);
        }
    }

    void shutdown() {
        for (Placed entry : placed.values()) {
            if (entry.type.equals(SELL_CHEST) && entry.earned > 0) {
                market.store().saveSpecial(entry.row());
            }
        }
    }

    /** /market give &lt;játékos&gt; &lt;fajta&gt; (admin). */
    public boolean give(Player target, String type, int amount) {
        ItemStack item = item(type.toLowerCase(Locale.ROOT));
        if (item == null) {
            return false;
        }
        item.setAmount(Math.max(1, Math.min(64, amount)));
        Market.give(target, item);
        return true;
    }
}
