package hu.icemine.core.fyland;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Egyedi varázslatok a Fylandon (/enchanter). Tapasztalat szintért varázskönyvet veszel, amit a táskádban egy
 * tárgyra húzol: a könyvön látszik, mekkora eséllyel sikerül, és mekkora eséllyel tönkremegy a tárgy, ha nem.
 * A varázslatok csak a Fyland világban hatnak (a varázsblokk bányászatánál is).
 */
public final class FyEnchants implements Listener {

    static final String BOOK_TAG = "fyland:book:";
    private static final String LORE_MARK = "§3§7";
    private static final NamespacedKey KEY = new NamespacedKey("iceminecore", "fyench");

    /** Egy varázslat: azonosító (a kód ehhez köti a hatást), név, max szint, árak (XP szint), mire tehető. */
    record Enchant(String id, String name, int max, List<Integer> costs, String applies, Material icon, List<String> description) {
        int cost(int level) {
            if (costs.isEmpty()) {
                return 10 * level;
            }
            return costs.get(Math.min(costs.size(), Math.max(1, level)) - 1);
        }
    }

    static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);

    static {
        SMELT.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT.put(Material.COBBLESTONE, Material.STONE);
        SMELT.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        SMELT.put(Material.SAND, Material.GLASS);
        SMELT.put(Material.RED_SAND, Material.GLASS);
        SMELT.put(Material.CLAY_BALL, Material.BRICK);
        SMELT.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT.put(Material.CACTUS, Material.GREEN_DYE);
        SMELT.put(Material.WET_SPONGE, Material.SPONGE);
        SMELT.put(Material.KELP, Material.DRIED_KELP);
    }

    private final FylandMode mode;
    private final Map<String, Enchant> enchants = new LinkedHashMap<>();
    private int successMin = 40;
    private int successMax = 100;
    private int destroyMin = 0;
    private int destroyMax = 30;
    private boolean exploding;

    FyEnchants(FylandMode mode) {
        this.mode = mode;
    }

    void load(YamlConfiguration config, List<String> problems) {
        enchants.clear();
        ConfigurationSection root = config.isSet("enchanter") ? config.getConfigurationSection("enchanter")
                : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection("enchanter");
        if (root == null) {
            return;
        }
        successMin = clamp(root.getInt("success.min", 40), 1, 100);
        successMax = clamp(root.getInt("success.max", 100), successMin, 100);
        destroyMin = clamp(root.getInt("destroy.min", 0), 0, 100);
        destroyMax = clamp(root.getInt("destroy.max", 30), destroyMin, 100);
        ConfigurationSection list = root.getConfigurationSection("enchants");
        if (list == null) {
            return;
        }
        for (String id : list.getKeys(false)) {
            ConfigurationSection entry = list.getConfigurationSection(id);
            String key = id.toLowerCase(Locale.ROOT);
            if (entry == null || !entry.getBoolean("enabled", true)) {
                continue;
            }
            if (!known(key)) {
                problems.add("fyland.yml -> enchanter.enchants." + id + ": ismeretlen varázslat (lehet: autoszmelt, telepatia, tapasztalat, robbano, vampir, mereg, gyorsasag, ejjellatas)");
                continue;
            }
            enchants.put(key, new Enchant(key, entry.getString("name", id), clamp(entry.getInt("max-level", 1), 1, 5), entry.getIntegerList("cost"),
                    entry.getString("applies", "tool").toLowerCase(Locale.ROOT), Items.material(entry.getString("icon", "ENCHANTED_BOOK"), Material.ENCHANTED_BOOK),
                    entry.getStringList("description")));
        }
    }

    private static boolean known(String id) {
        return switch (id) {
            case "autoszmelt", "telepatia", "tapasztalat", "robbano", "vampir", "mereg", "gyorsasag", "ejjellatas" -> true;
            default -> false;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    /** Ráillik-e a tárgyra (applies: tool, pickaxe, weapon, armor, boots, helmet). */
    static boolean fits(String applies, Material material) {
        String name = material.name();
        return switch (applies) {
            case "pickaxe" -> name.endsWith("_PICKAXE");
            case "tool" -> name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
            case "weapon" -> name.endsWith("_SWORD") || name.endsWith("_AXE");
            case "boots" -> name.endsWith("_BOOTS");
            case "helmet" -> name.endsWith("_HELMET");
            case "armor" -> name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
            default -> false;
        };
    }

    // ---- a tárgyon tárolt varázslatok ----

    /** A tárgy varázslatai: azonosító → szint. */
    static Map<String, Integer> read(ItemStack item) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) {
            return result;
        }
        ItemMeta meta = item.getItemMeta();
        String raw = meta == null ? null : meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return parse(raw);
    }

    static Map<String, Integer> parse(String raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String[] pieces = part.split(":");
            if (pieces.length == 2) {
                try {
                    result.put(pieces[0], Math.max(1, Integer.parseInt(pieces[1])));
                } catch (NumberFormatException ignored) {
                    // hibás bejegyzés, kihagyjuk
                }
            }
        }
        return result;
    }

    static String format(Map<String, Integer> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            parts.add(entry.getKey() + ":" + entry.getValue());
        }
        return String.join(",", parts);
    }

    private void write(ItemStack item, Map<String, Integer> values) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, format(values));
        List<String> lore = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            Enchant enchant = enchants.get(entry.getKey());
            String name = enchant == null ? entry.getKey() : enchant.name();
            lore.add(LORE_MARK + Text.color(name + (enchant != null && enchant.max() == 1 ? "" : " " + roman(entry.getValue()))));
        }
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (!line.startsWith(LORE_MARK)) {
                    lore.add(line);
                }
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private int level(Player player, ItemStack item, String id) {
        if (item == null || player.getWorld() != mode.world()) {
            return 0;
        }
        Integer level = read(item).get(id);
        return level == null ? 0 : level;
    }

    // ---- könyvek ----

    ItemStack book(Enchant enchant, int level, int success, int destroy) {
        return Items.of(Material.ENCHANTED_BOOK).name("&d&l" + Text.strip(Text.color(enchant.name())) + (enchant.max() == 1 ? "" : " " + roman(level)))
                .lore(mode.texts("enchant-book-lore", "%success%", String.valueOf(success), "%destroy%", String.valueOf(destroy),
                        "%applies%", mode.text("enchant-applies-" + enchant.applies())))
                .tag(BOOK_TAG + enchant.id() + ":" + level + ":" + success + ":" + destroy).build();
    }

    private ItemStack rollBook(Enchant enchant, int level) {
        Random random = mode.random();
        int success = successMin + random.nextInt(successMax - successMin + 1);
        int destroy = destroyMin + random.nextInt(destroyMax - destroyMin + 1);
        return book(enchant, level, success, destroy);
    }

    /** Egy véletlen varázskönyv (az ellátmány ládákba). */
    ItemStack randomBook() {
        if (enchants.isEmpty()) {
            return null;
        }
        List<Enchant> list = new ArrayList<>(enchants.values());
        Enchant enchant = list.get(mode.random().nextInt(list.size()));
        return rollBook(enchant, 1 + mode.random().nextInt(enchant.max()));
    }

    // ---- /enchanter ----

    void open(Player player) {
        if (!mode.inWorld(player)) {
            mode.send(player, "only-here");
            return;
        }
        Menu menu = new Menu("fyland-enchanter", 4, mode.text("menu.enchanter-title"));
        menu.frame(Material.PURPLE_STAINED_GLASS_PANE, null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int index = 0;
        for (Enchant enchant : enchants.values()) {
            if (index >= slots.length) {
                break;
            }
            List<String> lore = new ArrayList<>(enchant.description());
            lore.add("");
            lore.add(mode.text("enchant-applies", "%applies%", mode.text("enchant-applies-" + enchant.applies())));
            lore.add(mode.text("enchant-max", "%max%", String.valueOf(enchant.max())));
            lore.add(mode.text("enchant-cost", "%cost%", String.valueOf(enchant.cost(1))));
            lore.add("");
            lore.add(mode.text("enchant-click"));
            menu.set(slots[index++], Items.of(enchant.icon()).name(enchant.name()).lore(lore).glow().hideAll().build(), clicker -> levels(clicker, enchant));
        }
        menu.set(31, Items.of(Material.EXPERIENCE_BOTTLE).name(mode.text("menu.enchanter-xp", "%level%", String.valueOf(player.getLevel())))
                .lore(mode.texts("menu.enchanter-help")).build());
        menu.open(player);
    }

    private void levels(Player player, Enchant enchant) {
        Menu menu = new Menu("fyland-enchanter-levels", 3, mode.text("menu.enchanter-levels-title", "%enchant%", Text.strip(Text.color(enchant.name()))));
        menu.frame(Material.PURPLE_STAINED_GLASS_PANE, null);
        int[][] layouts = {{13}, {12, 14}, {11, 13, 15}, {10, 12, 14, 16}, {11, 12, 13, 14, 15}};
        int[] slots = layouts[enchant.max() - 1];
        for (int level = 1; level <= enchant.max(); level++) {
            int chosen = level;
            menu.set(slots[level - 1], Items.of(Material.ENCHANTED_BOOK, level)
                    .name(enchant.name() + (enchant.max() == 1 ? "" : " " + roman(level)))
                    .lore(mode.texts("menu.enchanter-level-lore", "%cost%", String.valueOf(enchant.cost(level)), "%level%", String.valueOf(player.getLevel())))
                    .build(), clicker -> buy(clicker, enchant, chosen));
        }
        menu.set(22, Items.of(Material.ARROW).name(mode.text("menu.back")).build(), this::open);
        menu.open(player);
    }

    private void buy(Player player, Enchant enchant, int level) {
        int cost = enchant.cost(level);
        if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < cost) {
            mode.send(player, "enchant-no-xp", "%cost%", String.valueOf(cost));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            mode.send(player, "inventory-full");
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(player.getLevel() - cost);
        }
        mode.give(player, rollBook(enchant, level));
        mode.send(player, "enchant-bought", "%enchant%", enchant.name() + (enchant.max() == 1 ? "" : " " + roman(level)), "%cost%", String.valueOf(cost));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.1f);
        levels(player, enchant);
    }

    // ---- könyv ráhúzása egy tárgyra ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onApply(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getWorld() != mode.world()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        String tag = Items.tagOf(cursor);
        if (tag == null || !tag.startsWith(BOOK_TAG) || target == null || target.getType().isAir() || event.getClick() != ClickType.LEFT) {
            return;
        }
        String[] parts = tag.substring(BOOK_TAG.length()).split(":");
        Enchant enchant = parts.length == 4 ? enchants.get(parts[0]) : null;
        if (enchant == null) {
            return;
        }
        event.setCancelled(true);
        int level;
        int success;
        int destroy;
        try {
            level = Integer.parseInt(parts[1]);
            success = Integer.parseInt(parts[2]);
            destroy = Integer.parseInt(parts[3]);
        } catch (NumberFormatException exception) {
            return;
        }
        if (target.getAmount() != 1 || !fits(enchant.applies(), target.getType())) {
            mode.send(player, "enchant-wrong-item", "%applies%", mode.text("enchant-applies-" + enchant.applies()));
            return;
        }
        Map<String, Integer> current = read(target);
        if (current.getOrDefault(enchant.id(), 0) >= level) {
            mode.send(player, "enchant-already");
            return;
        }
        cursor.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
        Random random = mode.random();
        String name = enchant.name() + (enchant.max() == 1 ? "" : " " + roman(level));
        if (random.nextInt(100) < success) {
            current.put(enchant.id(), Math.min(enchant.max(), level));
            write(target, current);
            event.setCurrentItem(target);
            mode.send(player, "enchant-success", "%enchant%", name);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
            return;
        }
        if (random.nextInt(100) < destroy) {
            event.setCurrentItem(null);
            mode.send(player, "enchant-destroyed", "%enchant%", name);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
        } else {
            mode.send(player, "enchant-failed", "%enchant%", name);
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1f);
        }
    }

    // ---- hatások ----

    /** Autoszmelt: a leeső tárgyak kiolvasztva. */
    Collection<ItemStack> drops(Player player, ItemStack tool, Collection<ItemStack> drops) {
        if (level(player, tool, "autoszmelt") <= 0) {
            return drops;
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack drop : drops) {
            Material smelted = SMELT.get(drop.getType());
            result.add(smelted == null ? drop : new ItemStack(smelted, drop.getAmount()));
        }
        return result;
    }

    /** Telepátia: a tárgy egyből a táskába kerül (ha van hely). Igazat ad, ha elintézte. */
    boolean telepathy(Player player, ItemStack tool, ItemStack drop) {
        if (level(player, tool, "telepatia") <= 0) {
            return false;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(drop);
        if (rest.isEmpty()) {
            return true;
        }
        for (ItemStack left : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        return true;
    }

    /** Tapasztalat: szintenként +50% XP a bányászatból. */
    int experience(Player player, int base) {
        int level = level(player, player.getInventory().getItemInMainHand(), "tapasztalat");
        return level <= 0 ? base : (int) Math.round(base * (1 + 0.5 * level)) + level;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExp(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == mode.world() && !exploding) {
            event.setExpToDrop(experience(event.getPlayer(), event.getExpToDrop()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != mode.world()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean smelt = level(player, tool, "autoszmelt") > 0;
        boolean telepathy = level(player, tool, "telepatia") > 0;
        if (!smelt && !telepathy) {
            return;
        }
        List<Item> items = event.getItems();
        for (Item item : new ArrayList<>(items)) {
            ItemStack stack = item.getItemStack();
            if (smelt) {
                Material smelted = SMELT.get(stack.getType());
                if (smelted != null) {
                    stack = new ItemStack(smelted, stack.getAmount());
                    item.setItemStack(stack);
                }
            }
            if (telepathy) {
                telepathy(player, tool, stack);
                items.remove(item);
            }
        }
    }

    /** Robbanó: esély, hogy a kiütött blokk körül (a nézési irányra merőleges 3x3-ban) a többi is kitörik. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosive(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (exploding || player.getWorld() != mode.world()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = level(player, tool, "robbano");
        if (level <= 0 || mode.random().nextDouble() >= 0.15 * level) {
            return;
        }
        Block center = event.getBlock();
        FyIsland island = mode.islandAt(center.getLocation());
        if (island == null || !island.trusted(player.getUniqueId())) {
            return;
        }
        BlockFace face = player.getFacing();
        float pitch = player.getLocation().getPitch();
        boolean vertical = pitch > 50 || pitch < -50;
        exploding = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    Block block;
                    if (vertical) {
                        block = center.getRelative(a, 0, b);
                    } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                        block = center.getRelative(a, b, 0);
                    } else {
                        block = center.getRelative(0, b, a);
                    }
                    if (!breakable(island, block)) {
                        continue;
                    }
                    mode.worth().removed(island, block.getType());
                    Collection<ItemStack> drops = drops(player, tool, block.getDrops(tool, player));
                    block.setType(Material.AIR, true);
                    for (ItemStack drop : drops) {
                        if (!telepathy(player, tool, drop)) {
                            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.3, 0.5), drop);
                        }
                    }
                }
            }
        } finally {
            exploding = false;
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.getLocation().add(0.5, 0.5, 0.5), 1);
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
    }

    private boolean breakable(FyIsland island, Block block) {
        Material type = block.getType();
        if (type.isAir() || type == Material.BEDROCK || type == Material.LODESTONE || type == Material.BARRIER || block.isLiquid()) {
            return false;
        }
        if (block.getX() == island.x && block.getY() == island.y && block.getZ() == island.z) {
            return false;
        }
        if (block.getState() instanceof TileState || type.getHardness() < 0) {
            return false;
        }
        return mode.inside(island, block.getLocation());
    }

    /** Vámpír és Méreg: a fegyveres ütésnél. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim) || player.getWorld() != mode.world()) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        int vampire = level(player, weapon, "vampir");
        if (vampire > 0) {
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double limit = max == null ? 20 : max.getValue();
            double heal = Math.min(4, event.getFinalDamage() * 0.08 * vampire);
            if (heal > 0 && !player.isDead()) {
                player.setHealth(Math.min(limit, player.getHealth() + heal));
            }
        }
        int poison = level(player, weapon, "mereg");
        if (poison > 0 && mode.random().nextDouble() < 0.12 * poison) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40 + 20 * poison, poison - 1, false, true, true));
        }
    }

    /** Másodpercenként: a páncél varázslatok (Gyorsaság, Éjjellátás) hatása. */
    void tick() {
        if (mode.world() == null || enchants.isEmpty()) {
            return;
        }
        for (Player player : mode.world().getPlayers()) {
            int speed = level(player, player.getInventory().getBoots(), "gyorsasag");
            if (speed > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, speed - 1, true, false, true));
            }
            if (level(player, player.getInventory().getHelmet(), "ejjellatas") > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
            }
        }
    }

    Map<String, Enchant> enchants() {
        return enchants;
    }
}
