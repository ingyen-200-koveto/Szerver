package hu.icemine.core.fullpvp;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.build.Layout;
import hu.icemine.core.build.Placer;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.kitpvp.PvpMode;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.ClickText;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * FullPvP: egy nagy pálya, középen biztonságos vár udvarral. A felszerelés megmarad (kilépéskor elmentjük), halálkor
 * kiesik, és a gyilkos felveheti. Ölésért pénz jár, abból a boltban fegyvert, páncélt, ételt és bájitalt veszel.
 * Kitek várakozási idővel (rangos kitek is), széf a vár udvarán, fejpénz, sorozat jutalmak és XP a varázsasztalhoz.
 */
public final class FullPvpMode extends PvpMode {

    public static final String ID = "fullpvp";
    public static final String FILE = "fullpvp";
    static final String STARTER_TAG = "fullpvp";
    private static final String LABEL_TAG = "icemine_fullpvp_label";

    /** Egy kit: a tárgyai a játékosé lesznek (kiesnek halálkor), kivéve a kezdő kitet. */
    public static final class FpKit {
        public final String id;
        public final String name;
        public final Material icon;
        public final List<String> description;
        public final long cooldown;
        public final String permission;
        final List<ItemStack> items = new ArrayList<>();
        final ItemStack[] armor = new ItemStack[4];

        FpKit(String id, String name, Material icon, List<String> description, long cooldown, String permission) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.cooldown = cooldown;
            this.permission = permission;
        }
    }

    /** Egy bolti ajánlat. */
    public static final class Offer {
        final ItemStack item;
        final long price;
        final int slot;

        Offer(ItemStack item, long price, int slot) {
            this.item = item;
            this.price = price;
            this.slot = slot;
        }
    }

    /** Egy bolti kategória. */
    public static final class Category {
        final String id;
        final String name;
        final Material icon;
        final int slot;
        final List<Offer> offers = new ArrayList<>();

        Category(String id, String name, Material icon, int slot) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
        }
    }

    /** A széf inventory jelölője. */
    static final class Vault implements InventoryHolder {
        final UUID owner;
        Inventory inventory;

        Vault(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Map<String, FpKit> fpKits = new LinkedHashMap<>();
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Integer, List<ItemStack>> streakItems = new TreeMap<>();
    private final Map<String, int[]> stations = new HashMap<>();
    private final Map<String, double[]> holograms = new LinkedHashMap<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private YamlConfiguration config;
    private Location spawn;
    private double safeX;
    private double safeZ;
    private double safeY;
    private double safeRadius = 15.5;
    private String respawnKit = "kezdo";
    private int xpPerKill = 3;
    private long bountyMin = 100;
    private long bountyMax = 1_000_000;
    private int vaultRows = 3;
    private int vaultRowsLarge = 6;
    private long labelCheck;

    public FullPvpMode(IceMineCore core) {
        super(core, ID);
    }

    public IceMineCore core() {
        return core;
    }

    // ---- betöltés, pálya ----

    public void load(YamlConfiguration config, List<String> problems) {
        this.config = config;
        String name = config.getString("world", "ice_fullpvp");
        if (world == null) {
            world = createWorld(name);
            if (world == null) {
                problems.add("fullpvp.yml -> world: nem sikerült létrehozni a világot (" + name + ")");
                return;
            }
        }
        prepareWorld(world, config.getLong("time", 6000));
        world.setGameRuleValue("naturalRegeneration", "true");
        if (!config.getBoolean("built", false)) {
            build(config);
        }
        loadCommon(config, "fullpvp.yml", problems);
        // a PvpMode kit rendszerét (kit felvétel, kit tárgyak) itt nem használjuk, a FullPvP kitjei a játékosé lesznek
        kits.clear();
        defaultKit = "";
        spawn = Locs.read(config.getConfigurationSection("spawn"), world);
        if (spawn == null) {
            spawn = new Location(world, 0.5, config.getInt("center.y", 80) + FullPvpBuild.FLOOR + 1, 9.5, 180, 0);
        }
        world.setSpawnLocation(spawn);
        safeX = config.getDouble("safe-zone.x", spawn.getX());
        safeZ = config.getDouble("safe-zone.z", spawn.getZ());
        safeY = config.getDouble("safe-zone.y", spawn.getY() - 2);
        safeRadius = Math.max(3, config.getDouble("safe-zone.radius", 15.5));
        stations.clear();
        ConfigurationSection stationSection = config.getConfigurationSection("stations");
        if (stationSection != null) {
            for (String id : stationSection.getKeys(false)) {
                stations.put(id, new int[]{stationSection.getInt(id + ".x"), stationSection.getInt(id + ".y"), stationSection.getInt(id + ".z")});
            }
        }
        holograms.clear();
        ConfigurationSection holoSection = config.getConfigurationSection("holograms");
        if (holoSection != null) {
            for (String id : holoSection.getKeys(false)) {
                holograms.put(id, new double[]{holoSection.getDouble(id + ".x"), holoSection.getDouble(id + ".y"), holoSection.getDouble(id + ".z")});
            }
        }
        respawnKit = config.getString("respawn-kit", "kezdo").toLowerCase(Locale.ROOT);
        xpPerKill = Math.max(0, Math.min(30, config.getInt("xp-per-kill", 3)));
        bountyMin = Math.max(1, config.getLong("bounty.min", 100));
        bountyMax = Math.max(bountyMin, config.getLong("bounty.max", 1_000_000));
        vaultRows = Math.max(1, Math.min(6, config.getInt("vault.rows", 3)));
        vaultRowsLarge = Math.max(vaultRows, Math.min(6, config.getInt("vault.rows-large", 6)));
        loadKits(config, problems);
        loadShop(config, problems);
        loadStreakItems(config, problems);
        int radius = FullPvpBuild.RADIUS + 6;
        world.getWorldBorder().setCenter(config.getInt("center.x", 0) + 0.5, config.getInt("center.z", 0) + 0.5);
        world.getWorldBorder().setSize(radius * 2 + 20);
        spawnLabels();
    }

    private void build(YamlConfiguration config) {
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 80);
        int z = config.getInt("center.z", 0);
        core.getLogger().info("Építem a FullPvP pályát...");
        Placer placer = new Placer(world, core.getLogger());
        int r = FullPvpBuild.RADIUS + 3;
        placer.clear(x - r, y - FullPvpBuild.DEPTH - 2, z - r, x + r, y + 40, z + r);
        FullPvpBuild build = new FullPvpBuild(placer, x, y, z);
        build.build();
        Layout layout = build.layout();
        config.set("stations", null);
        config.set("holograms", null);
        for (Map.Entry<String, Layout.Point> entry : layout.points.entrySet()) {
            String key = entry.getKey();
            Layout.Point point = entry.getValue();
            if (key.equals("spawn")) {
                Locs.write(config, "spawn", x + point.x, y + point.y, z + point.z, point.yaw);
            } else if (key.equals("safe")) {
                config.set("safe-zone.x", x + point.x + 0.5);
                config.set("safe-zone.y", y + point.y);
                config.set("safe-zone.z", z + point.z + 0.5);
                config.set("safe-zone.radius", point.yaw);
            } else if (key.startsWith("station:")) {
                String id = key.substring("station:".length());
                config.set("stations." + id + ".x", (int) (x + point.x));
                config.set("stations." + id + ".y", (int) (y + point.y));
                config.set("stations." + id + ".z", (int) (z + point.z));
            } else if (key.startsWith("holo:")) {
                String id = key.substring("holo:".length());
                config.set("holograms." + id + ".x", x + point.x);
                config.set("holograms." + id + ".y", y + point.y);
                config.set("holograms." + id + ".z", z + point.z);
            }
        }
        config.set("void-y", y - FullPvpBuild.DEPTH - 12);
        config.set("built", true);
        core.configs().save(FILE);
        core.getLogger().info("A FullPvP pálya kész (" + placer.placed() + " blokk).");
    }

    public void rebuild() {
        if (world == null || config == null) {
            return;
        }
        removeLabels();
        config.set("built", false);
        List<String> problems = new ArrayList<>();
        load(config, problems);
        core.configs().report(problems);
    }

    private void loadKits(YamlConfiguration config, List<String> problems) {
        fpKits.clear();
        ConfigurationSection section = config.getConfigurationSection("kits");
        if (section == null) {
            return;
        }
        List<String> ignored = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            FpKit kit = new FpKit(key, entry.getString("name", id), Items.material(entry.getString("icon", "IRON_SWORD"), Material.IRON_SWORD),
                    new ArrayList<>(entry.getStringList("description")), Math.max(0, entry.getLong("cooldown", 0)), entry.getString("permission", ""));
            String tag = key.equals(respawnKit) ? STARTER_TAG : null;
            for (String spec : entry.getStringList("items")) {
                ItemStack item = Kit.parse(tag, spec, "fullpvp.yml -> kits." + id, ignored);
                if (item != null) {
                    kit.items.add(item);
                }
            }
            String[] slots = {"boots", "leggings", "chestplate", "helmet"};
            for (int i = 0; i < slots.length; i++) {
                String spec = entry.getString("armor." + slots[i], "");
                if (!spec.isBlank()) {
                    kit.armor[i] = Kit.parse(tag, spec, "fullpvp.yml -> kits." + id, ignored);
                }
            }
            fpKits.put(key, kit);
        }
        if (!fpKits.containsKey(respawnKit) && !respawnKit.isBlank()) {
            problems.add("fullpvp.yml -> respawn-kit: ismeretlen kit (" + respawnKit + ")");
        }
    }

    private void loadShop(YamlConfiguration config, List<String> problems) {
        categories.clear();
        ConfigurationSection section = config.getConfigurationSection("shop");
        if (section == null) {
            return;
        }
        int index = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Category category = new Category(id, entry.getString("name", id), Items.material(entry.getString("icon", "CHEST"), Material.CHEST),
                    entry.getInt("slot", 10 + index * 2));
            index++;
            int offerIndex = 0;
            for (Map<?, ?> raw : entry.getMapList("items")) {
                Object spec = raw.get("item");
                Object price = raw.get("price");
                if (spec == null || !(price instanceof Number)) {
                    problems.add("fullpvp.yml -> shop." + id + ": minden tételnek kell item és price");
                    continue;
                }
                ItemStack item = Kit.parse(null, String.valueOf(spec), "fullpvp.yml -> shop." + id, problems);
                if (item == null) {
                    continue;
                }
                Object slot = raw.get("slot");
                category.offers.add(new Offer(item, Math.max(0, ((Number) price).longValue()), slot instanceof Number ? ((Number) slot).intValue() : -1));
                offerIndex++;
            }
            if (offerIndex == 0) {
                problems.add("fullpvp.yml -> shop." + id + ": üres kategória");
            }
            categories.put(id, category);
        }
    }

    private void loadStreakItems(YamlConfiguration config, List<String> problems) {
        streakItems.clear();
        ConfigurationSection section = config.getConfigurationSection("streak-items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int streak = Integer.parseInt(key);
                List<ItemStack> items = new ArrayList<>();
                for (String spec : section.getStringList(key)) {
                    ItemStack item = Kit.parse(null, spec, "fullpvp.yml -> streak-items." + key, problems);
                    if (item != null) {
                        items.add(item);
                    }
                }
                streakItems.put(streak, items);
            } catch (NumberFormatException exception) {
                problems.add("fullpvp.yml -> streak-items." + key + ": a kulcs csak szám lehet");
            }
        }
    }

    // ---- üzenetek ----

    private String text(String key, String... pairs) {
        String value = config == null ? null : config.getString("messages." + key);
        return Text.replace(value == null ? "&c[fullpvp: " + key + "]" : value, pairs);
    }

    void send(Player player, String key, String... pairs) {
        core.messages().sendTagged(player, text(key, pairs));
    }

    private void broadcast(String key, String... pairs) {
        if (world == null) {
            return;
        }
        String line = core.messages().tagged(text(key, pairs));
        for (Player player : world.getPlayers()) {
            player.sendMessage(line);
        }
    }

    private String money(long amount) {
        return core.economy().format(currency(), amount);
    }

    // ---- feliratok a vár udvarán ----

    private void spawnLabels() {
        removeLabels();
        if (world == null) {
            return;
        }
        for (Map.Entry<String, double[]> entry : holograms.entrySet()) {
            String value = config.getString("labels." + entry.getKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            double[] at = entry.getValue();
            Location location = new Location(world, at[0], at[1], at[2]);
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            TextDisplay display = world.spawn(location, TextDisplay.class);
            display.setPersistent(false);
            display.addScoreboardTag(LABEL_TAG);
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setText(Text.color(value.replace("\\n", "\n")));
            labels.add(display);
        }
        labelCheck = System.currentTimeMillis();
    }

    private void removeLabels() {
        for (TextDisplay display : labels) {
            display.remove();
        }
        labels.clear();
        if (world != null) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().contains(LABEL_TAG)) {
                    display.remove();
                }
            }
        }
    }

    /** Másodpercenként: ha egy felirat eltűnt (chunk kiürült), újra kirakjuk. */
    public void tick() {
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - labelCheck < 10_000L) {
            return;
        }
        labelCheck = now;
        boolean missing = labels.size() < holograms.size() && !world.getPlayers().isEmpty();
        for (TextDisplay display : labels) {
            missing |= !display.isValid();
        }
        if (missing) {
            spawnLabels();
        }
    }

    @Override
    public void shutdown() {
        removeLabels();
    }

    // ---- PvpMode ----

    @Override
    public Location spawn(Player player) {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location location) {
        spawn = location.clone();
        Locs.write(config, "spawn", location);
        core.configs().save(FILE);
    }

    @Override
    protected boolean inSafe(Player player, Location location) {
        if (location == null || location.getWorld() != world) {
            return false;
        }
        double dx = location.getX() - safeX;
        double dz = location.getZ() - safeZ;
        return dx * dx + dz * dz <= safeRadius * safeRadius && location.getY() >= safeY;
    }

    public boolean inSafe(Player player) {
        return inSafe(player, player.getLocation());
    }

    @Override
    protected Location respawn(Player player) {
        return spawn(player);
    }

    /** Halál után: a kezdő kit (ha be van állítva). */
    @Override
    protected void prepare(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setFoodLevel(20);
        player.setSaturation(8f);
        giveStarter(player);
    }

    @Override
    public void backToSpawn(Player player) {
        player.setFallDistance(0f);
    }

    @Override
    protected void leftSafe(Player player) {
    }

    @Override
    public void enter(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        boolean restored = core.inventories().restore(ID, player);
        if (!restored) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.setLevel(0);
            player.setExp(0f);
            try {
                player.setHealth(player.getMaxHealth());
            } catch (IllegalArgumentException ignored) {
            }
            player.setFoodLevel(20);
            giveStarter(player);
            send(player, "first-join");
        } else {
            send(player, "welcome");
        }
    }

    @Override
    public void leave(Player player, boolean quitting) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Vault vault) {
            saveVault(vault);
        }
        core.inventories().save(ID, player);
        super.leave(player, quitting);
        player.setLevel(0);
        player.setExp(0f);
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        super.placeholders(player, values);
        values.put("%bounty%", money(bounty(player)));
        values.put("%fp_money%", money(core.economy().balance(player, currency())));
    }

    @Override
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getEntity().getWorld() != world) {
            return;
        }
        Player attacker = hu.icemine.core.combat.CombatTag.attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (inSafe(victim, victim.getLocation()) || inSafe(attacker, attacker.getLocation())) {
            event.setCancelled(true);
            core.messages().actionBar(attacker, "pvp.safe-zone");
        }
    }

    @Override
    protected void onKill(Player killer, Player victim, int streak) {
        if (xpPerKill > 0) {
            killer.giveExpLevels(xpPerKill);
        }
        List<ItemStack> reward = streakItems.get(streak);
        if (reward != null && !reward.isEmpty()) {
            for (ItemStack item : reward) {
                give(killer, item.clone());
            }
            send(killer, "streak-reward", "%streak%", String.valueOf(streak));
        }
        long bounty = bounty(victim);
        if (bounty > 0) {
            setBounty(victim, 0);
            if (core.economy().deposit(killer, currency(), bounty, ID + ":bounty") == Ledger.Result.OK) {
                broadcast("bounty-claimed", "%killer%", killer.getName(), "%victim%", victim.getName(), "%amount%", money(bounty));
            }
        }
    }

    // ---- kitek ----

    public Map<String, FpKit> fullKits() {
        return fpKits;
    }

    private void giveStarter(Player player) {
        FpKit kit = fpKits.get(respawnKit);
        if (kit == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : kit.items) {
            give(player, item.clone());
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < 4; i++) {
            if (kit.armor[i] != null && (armor[i] == null || armor[i].getType().isAir())) {
                armor[i] = kit.armor[i].clone();
            }
        }
        inventory.setArmorContents(armor);
    }

    private long lastClaim(Player player, String kit) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return 0;
        }
        try {
            return Long.parseLong(profile.setting(ID + ".kit." + kit, "0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private long kitLeft(Player player, FpKit kit) {
        if (kit.cooldown <= 0) {
            return 0;
        }
        long left = lastClaim(player, kit.id) + kit.cooldown * 1000L - System.currentTimeMillis();
        return Math.max(0, left);
    }

    private boolean kitAllowed(Player player, FpKit kit) {
        return core.ranks().canUse(player, kit.id, kit.permission);
    }

    /** Kit felvétele (menüből vagy /kit &lt;név&gt;). */
    public void claim(Player player, String kitId) {
        FpKit kit = fpKits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null || player.getWorld() != world) {
            send(player, "kit-unknown", "%kits%", String.join(", ", fpKits.keySet()));
            return;
        }
        if (kit.id.equals(respawnKit)) {
            send(player, "kit-starter");
            return;
        }
        if (!kitAllowed(player, kit)) {
            hu.icemine.core.rank.Ranks.Rank need = core.ranks().requiredFor(kit.id);
            send(player, "kit-locked", "%rank%", need == null ? "?" : need.name);
            return;
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        long left = kitLeft(player, kit);
        if (left > 0) {
            send(player, "kit-cooldown", "%time%", Text.duration(left));
            return;
        }
        for (ItemStack item : kit.items) {
            give(player, item.clone());
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < 4; i++) {
            if (kit.armor[i] == null) {
                continue;
            }
            if (armor[i] == null || armor[i].getType().isAir() || Items.kitOf(armor[i]) != null) {
                armor[i] = kit.armor[i].clone();
            } else {
                give(player, kit.armor[i].clone());
            }
        }
        inventory.setArmorContents(armor);
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null && kit.cooldown > 0) {
            profile.put(ID + ".kit." + kit.id, String.valueOf(System.currentTimeMillis()));
        }
        send(player, "kit-claimed", "%kit%", kit.name);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.8f, 1.2f);
    }

    /** Tárgy a játékosnak; ha nincs hely, a lába elé esik. */
    void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        for (ItemStack left : rest.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    @Override
    public void selectKit(Player player, String kitId) {
        claim(player, kitId);
    }

    @Override
    public void openKits(Player player) {
        int rows = Math.max(3, Math.min(6, (fpKits.size() + 6) / 7 + 2));
        Menu menu = new Menu("fullpvp-kits", rows, text("menu.kits-title"));
        menu.frame(Material.RED_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (FpKit kit : fpKits.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= menu.size() - 9) {
                break;
            }
            List<String> lore = new ArrayList<>(kit.description);
            lore.add("");
            boolean starter = kit.id.equals(respawnKit);
            boolean allowed = kitAllowed(player, kit);
            long left = kitLeft(player, kit);
            if (starter) {
                lore.add(text("kit-lore.starter"));
            } else {
                lore.add(text("kit-lore.cooldown", "%time%", kit.cooldown <= 0 ? text("kit-lore.no-cooldown") : Text.duration(kit.cooldown * 1000L)));
                if (!allowed) {
                    hu.icemine.core.rank.Ranks.Rank need = core.ranks().requiredFor(kit.id);
                    lore.add(text("kit-lore.locked", "%rank%", need == null ? "?" : need.name));
                } else if (left > 0) {
                    lore.add(text("kit-lore.wait", "%time%", Text.duration(left)));
                } else {
                    lore.add(text("kit-lore.ready"));
                }
            }
            Items item = Items.of(allowed && !starter ? kit.icon : (starter ? kit.icon : Material.GRAY_DYE)).name(kit.name).lore(lore).hideAll();
            if (allowed && left <= 0 && !starter) {
                item.glow();
            }
            String id = kit.id;
            menu.set(slot++, item.build(), clicker -> {
                claim(clicker, id);
                openKits(clicker);
            });
        }
        menu.set(menu.size() - 5, Items.of(Material.ARROW).name(text("menu.back")).build(), this::openMain);
        menu.open(player);
    }

    /** /icem kit create: a nálad lévő tárgyakból új FullPvP kit (a fullpvp.yml-be kerül). */
    public boolean createKit(Player admin, String kitId, String rankId) {
        List<String> items = new ArrayList<>();
        for (ItemStack item : admin.getInventory().getStorageContents()) {
            if (item == null || Items.tagOf(item) != null) {
                continue;
            }
            String spec = Kit.spec(item);
            if (spec != null) {
                items.add(spec);
            }
        }
        if (items.isEmpty()) {
            core.messages().send(admin, "kit.create-empty");
            return false;
        }
        String base = "kits." + kitId;
        ItemStack hand = admin.getInventory().getItemInMainHand();
        config.set(base, null);
        config.set(base + ".name", "&f&l" + Character.toUpperCase(kitId.charAt(0)) + kitId.substring(1));
        config.set(base + ".icon", (hand == null || hand.getType().isAir() ? Material.IRON_SWORD : hand.getType()).name());
        config.set(base + ".description", List.of("&7Egyedi kit."));
        config.set(base + ".cooldown", 3600);
        config.set(base + ".items", items);
        ItemStack[] armor = admin.getInventory().getArmorContents();
        String[] slots = {"boots", "leggings", "chestplate", "helmet"};
        for (int i = 0; i < slots.length && i < armor.length; i++) {
            String spec = Kit.spec(armor[i]);
            if (spec != null) {
                config.set(base + ".armor." + slots[i], spec);
            }
        }
        core.configs().save(FILE);
        if (rankId != null) {
            YamlConfiguration ranks = core.configs().get("ranks");
            String path = "ranks." + rankId + ".kits";
            List<String> list = new ArrayList<>(ranks.getStringList(path));
            if (!list.contains(kitId)) {
                list.add(kitId);
            }
            ranks.set(path, list);
            core.configs().save("ranks");
            core.reloadConfigs("ranks");
        }
        core.reloadConfigs(FILE);
        return true;
    }

    public boolean deleteKit(String kitId) {
        if (!config.isSet("kits." + kitId)) {
            return false;
        }
        config.set("kits." + kitId, null);
        core.configs().save(FILE);
        YamlConfiguration ranks = core.configs().get("ranks");
        ConfigurationSection section = ranks.getConfigurationSection("ranks");
        boolean changed = false;
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                List<String> list = new ArrayList<>(ranks.getStringList("ranks." + rank + ".kits"));
                if (list.remove(kitId)) {
                    ranks.set("ranks." + rank + ".kits", list);
                    changed = true;
                }
            }
        }
        if (changed) {
            core.configs().save("ranks");
            core.reloadConfigs("ranks");
        }
        core.reloadConfigs(FILE);
        return true;
    }

    // ---- bolt ----

    public void openShop(Player player) {
        Menu menu = new Menu("fullpvp-shop", 4, text("menu.shop-title"));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        for (Category category : categories.values()) {
            if (category.slot < 0 || category.slot >= menu.size()) {
                continue;
            }
            menu.set(category.slot, Items.of(category.icon).name(category.name)
                    .lore(text("menu.category-lore", "%count%", String.valueOf(category.offers.size()))).hideAll().build(), clicker -> openCategory(clicker, category.id));
        }
        menu.set(31, Items.of(Material.GOLD_INGOT).name(text("menu.balance", "%money%", money(core.economy().balance(player, currency())))).build());
        menu.set(27, Items.of(Material.ARROW).name(text("menu.back")).build(), this::openMain);
        menu.open(player);
    }

    void openCategory(Player player, String id) {
        Category category = categories.get(id);
        if (category == null) {
            openShop(player);
            return;
        }
        int rows = Math.max(3, Math.min(6, (category.offers.size() + 6) / 7 + 2));
        Menu menu = new Menu("fullpvp-shop-" + id, rows, text("menu.category-title", "%category%", Text.strip(Text.color(category.name))));
        menu.frame(Material.LIME_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (Offer offer : category.offers) {
            int target = offer.slot;
            if (target < 0) {
                if (slot % 9 == 8) {
                    slot += 2;
                }
                target = slot++;
            }
            if (target >= menu.size() - 9) {
                continue;
            }
            ItemStack shown = offer.item.clone();
            org.bukkit.inventory.meta.ItemMeta meta = shown.getItemMeta();
            List<String> lore = new ArrayList<>();
            if (meta != null && meta.hasLore() && meta.getLore() != null) {
                lore.addAll(meta.getLore());
            }
            lore.add("");
            lore.add(Text.color(text("menu.price", "%price%", money(offer.price))));
            lore.add(Text.color(text(shown.getMaxStackSize() > 1 ? "menu.buy-stack" : "menu.buy-one", "%amount%", String.valueOf(shown.getAmount() * 4))));
            if (meta != null) {
                meta.setLore(lore);
                shown.setItemMeta(meta);
            }
            menu.set(target, shown, clicker -> buy(clicker, offer, 1, id), clicker -> buy(clicker, offer, offer.item.getMaxStackSize() > 1 ? 4 : 1, id));
        }
        menu.set(menu.size() - 5, Items.of(Material.ARROW).name(text("menu.back")).build(), this::openShop);
        menu.set(menu.size() - 1, Items.of(Material.GOLD_INGOT).name(text("menu.balance", "%money%", money(core.economy().balance(player, currency())))).build());
        menu.open(player);
    }

    private void buy(Player player, Offer offer, int times, String category) {
        if (player.getWorld() != world) {
            return;
        }
        long price = offer.price * times;
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        int stacks = (int) Math.ceil(offer.item.getAmount() * times / (double) Math.max(1, offer.item.getMaxStackSize()));
        if (free < stacks) {
            send(player, "inventory-full");
            return;
        }
        if (core.economy().withdraw(player, currency(), price, ID + ":shop") != Ledger.Result.OK) {
            send(player, "not-enough-money", "%price%", money(price));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        for (int i = 0; i < times; i++) {
            give(player, offer.item.clone());
        }
        String name = itemName(offer.item);
        send(player, "bought", "%item%", name, "%amount%", String.valueOf(offer.item.getAmount() * times), "%price%", money(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        openCategory(player, category);
    }

    /** A tárgy neve az üzenetekhez: a saját neve, vagy a fullpvp.yml item-names részéből, vagy az anyag neve. */
    String itemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String key = item.getType().name().toLowerCase(Locale.ROOT);
        String custom = config.getString("item-names." + key);
        if (custom != null) {
            return custom;
        }
        StringBuilder name = new StringBuilder();
        for (String part : key.split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    // ---- széf ----

    public void openVault(Player player) {
        if (player.getWorld() != world) {
            return;
        }
        if (!inSafe(player)) {
            send(player, "vault-only-safe");
            return;
        }
        int rows = player.hasPermission("icemine.fullpvp.vault.large") ? vaultRowsLarge : vaultRows;
        Vault holder = new Vault(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.color(text("menu.vault-title")));
        holder.inventory = inventory;
        ItemStack[] stored = core.inventories().loadItems("fullpvp_vault", player.getUniqueId(), 54);
        ItemStack[] contents = new ItemStack[rows * 9];
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] == null) {
                continue;
            }
            if (i < contents.length) {
                contents[i] = stored[i];
            } else {
                overflow.add(stored[i]);
            }
        }
        inventory.setContents(contents);
        for (ItemStack item : overflow) {
            Map<Integer, ItemStack> rest = inventory.addItem(item);
            for (ItemStack left : rest.values()) {
                give(player, left);
            }
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1f);
    }

    private void saveVault(Vault vault) {
        ItemStack[] contents = vault.inventory.getContents();
        ItemStack[] copy = new ItemStack[54];
        for (int i = 0; i < contents.length && i < copy.length; i++) {
            copy[i] = contents[i];
        }
        core.inventories().saveItems("fullpvp_vault", vault.owner, copy);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVaultClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Vault vault) {
            saveVault(vault);
        }
    }

    // ---- fejpénz ----

    public long bounty(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(profile.setting(ID + ".bounty", "0")));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void setBounty(Player player, long amount) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.put(ID + ".bounty", String.valueOf(Math.max(0, amount)));
        }
    }

    public void placeBounty(Player player, String targetName, String amountText) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.getWorld() != world) {
            send(player, "bounty-not-here");
            return;
        }
        if (target.equals(player)) {
            send(player, "bounty-self");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(amountText.replace(" ", "").replace(".", ""));
        } catch (NumberFormatException exception) {
            send(player, "bounty-usage");
            return;
        }
        if (amount < bountyMin || amount > bountyMax) {
            send(player, "bounty-range", "%min%", money(bountyMin), "%max%", money(bountyMax));
            return;
        }
        if (core.economy().withdraw(player, currency(), amount, ID + ":bounty-set") != Ledger.Result.OK) {
            send(player, "not-enough-money", "%price%", money(amount));
            return;
        }
        long total = bounty(target) + amount;
        setBounty(target, total);
        broadcast("bounty-placed", "%player%", player.getName(), "%target%", target.getName(), "%amount%", money(amount), "%total%", money(total));
    }

    public void openBounties(Player player) {
        List<Player> wanted = new ArrayList<>();
        for (Player online : world.getPlayers()) {
            if (bounty(online) > 0) {
                wanted.add(online);
            }
        }
        wanted.sort(Comparator.comparingLong(this::bounty).reversed());
        Menu menu = new Menu("fullpvp-bounties", 4, text("menu.bounties-title"));
        menu.frame(Material.RED_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (Player target : wanted) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 26) {
                break;
            }
            menu.set(slot++, Items.head(target).name("&c" + target.getName()).lore(text("menu.bounty-lore", "%amount%", money(bounty(target)))).build());
        }
        if (wanted.isEmpty()) {
            menu.set(13, Items.of(Material.PAPER).name(text("menu.no-bounties")).build());
        }
        menu.set(27, Items.of(Material.ARROW).name(text("menu.back")).build(), this::openMain);
        menu.set(31, Items.of(Material.WRITABLE_BOOK).name(text("menu.bounty-how")).lore(text("menu.bounty-how-lore")).build());
        menu.open(player);
    }

    // ---- főmenü, statisztika ----

    public void openMain(Player player) {
        Menu menu = new Menu("fullpvp-main", 4, text("menu.main-title"));
        menu.frame(Material.RED_STAINED_GLASS_PANE, null);
        menu.set(10, Items.of(Material.EMERALD).name(text("menu.shop")).lore(text("menu.shop-lore")).build(), this::openShop);
        menu.set(12, Items.of(Material.CHEST).name(text("menu.kits")).lore(text("menu.kits-lore")).build(), this::openKits);
        menu.set(14, Items.of(Material.ENDER_CHEST).name(text("menu.vault")).lore(text("menu.vault-lore")).build(), clicker -> {
            clicker.closeInventory();
            openVault(clicker);
        });
        menu.set(16, Items.of(Material.SKELETON_SKULL).name(text("menu.bounties")).lore(text("menu.bounties-lore")).build(), this::openBounties);
        PlayerProfile profile = core.profiles().profile(player);
        long kills = profile == null ? 0 : profile.stat(ID + ".kills");
        long deaths = profile == null ? 0 : profile.stat(ID + ".deaths");
        menu.set(22, Items.head(player).name(text("menu.stats", "%player%", player.getName())).lore(
                text("menu.stats-kills", "%kills%", Text.number(kills)),
                text("menu.stats-deaths", "%deaths%", Text.number(deaths)),
                text("menu.stats-kd", "%kd%", Text.ratio(kills, deaths)),
                text("menu.stats-streak", "%streak%", String.valueOf(currentStreaks().getOrDefault(player.getUniqueId(), 0)),
                        "%best%", String.valueOf(profile == null ? 0 : profile.stat(ID + ".best_streak"))),
                text("menu.stats-bounty", "%amount%", money(bounty(player))),
                text("menu.stats-money", "%money%", money(core.economy().balance(player, currency())))).build());
        menu.set(31, Items.of(Material.RED_BED).name(text("menu.lobby")).build(), clicker -> {
            clicker.closeInventory();
            core.modes().sendAndReport(clicker, hu.icemine.core.mode.ModeService.LOBBY);
        });
        menu.open(player);
    }

    public void help(Player player) {
        for (String line : config.getStringList("messages.help")) {
            player.sendMessage(ClickText.line(line, "&7Kattints: &f%value%", "&7Kattints"));
        }
    }

    // ---- védelem, állomások ----

    private boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("icemine.admin.build");
    }

    private String stationAt(Block block) {
        for (Map.Entry<String, int[]> entry : stations.entrySet()) {
            int[] at = entry.getValue();
            if (block.getX() == at[0] && block.getY() == at[1] && block.getZ() == at[2]) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == world && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == world && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.getBlock().getWorld() == world && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilDamage(com.destroystokyo.paper.event.block.AnvilDamagedEvent event) {
        if (event.getInventory().getLocation() != null && event.getInventory().getLocation().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStation(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            Material type = block.getType();
            if (type == Material.FARMLAND || type == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String station = stationAt(block);
        if (station != null) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            switch (station) {
                case "shop" -> openShop(player);
                case "kits" -> openKits(player);
                case "vault" -> openVault(player);
                default -> openMain(player);
            }
            return;
        }
        if (builder(player)) {
            return;
        }
        Material type = block.getType();
        boolean allowed = type == Material.ENCHANTING_TABLE || type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL
                || type == Material.CRAFTING_TABLE;
        if (!allowed && (type.isInteractable() || type == Material.ENDER_CHEST)) {
            event.setCancelled(true);
        }
    }
}
