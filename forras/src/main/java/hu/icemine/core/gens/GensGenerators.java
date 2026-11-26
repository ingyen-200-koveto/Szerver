package hu.icemine.core.gens;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Generátorok a Gens szigeteken (mint a FyreMC Gens Tycoonban): a Generátor boltban veszed meg, lerakod a
 * szigeteden, és magától termel. A termést a generátor eltárolja (amíg meg nem telik), jobb klikkel begyűjtöd
 * vagy rögtön eladod, és ugyanitt fejlesztheted a következő szintre. Egy szigeten base-slots darab lehet,
 * ez a +5 férőhely jeggyel bővíthető. A generátorok csak akkor termelnek, ha a sziget gazdája fent van.
 */
public final class GensGenerators implements Listener {

    public static final String ITEM = "gens:gen:";
    public static final String DROP = "gens:drop:";
    public static final String SLOTS = "gens:slots";
    private static final String LABEL_TAG = "icemine_gens_gen";
    private static final String SETTING = "gens.generators";

    /** Egy generátor szint a gens.yml-ből. */
    public record Tier(int tier, String name, Material block, Material drop, String dropName, long value, double seconds, long price, int level) {
    }

    /** Egy lerakott generátor. */
    static final class Gen {
        final UUID owner;
        final int x;
        final int y;
        final int z;
        int tier;
        int stored;
        double progress;
        TextDisplay label;

        Gen(UUID owner, int x, int y, int z, int tier, int stored) {
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.stored = stored;
        }

        long key() {
            return GensData.key(x, y, z);
        }
    }

    private final IceMineCore core;
    private final GensMode gens;
    private final TreeMap<Integer, Tier> tiers = new TreeMap<>();
    private final Map<UUID, List<Gen>> byOwner = new ConcurrentHashMap<>();
    private final Map<Long, Gen> byBlock = new ConcurrentHashMap<>();
    private int baseSlots = 5;
    private int maxSlots = 50;
    private int capacityBase = 64;
    private int capacityPerTier = 32;
    private long slotPrice = 250000;
    private double upgradeRefund = 0.5;
    private long lastTick;
    private int labelTimer;

    GensGenerators(IceMineCore core, GensMode gens) {
        this.core = core;
        this.gens = gens;
    }

    void load(YamlConfiguration config, List<String> problems) {
        tiers.clear();
        ConfigurationSection section = GensExtras.section(core, config, "generators");
        if (section == null) {
            return;
        }
        baseSlots = Math.max(1, section.getInt("base-slots", 5));
        maxSlots = Math.max(baseSlots, section.getInt("max-slots", 50));
        capacityBase = Math.max(1, section.getInt("capacity", 64));
        capacityPerTier = Math.max(0, section.getInt("capacity-per-tier", 32));
        slotPrice = Math.max(0, section.getLong("slot-price", 250000));
        upgradeRefund = Math.max(0, Math.min(1, section.getDouble("upgrade-refund", 0.5)));
        ConfigurationSection list = section.getConfigurationSection("tiers");
        if (list == null) {
            problems.add("gens.yml -> generators.tiers: nincs egy generátor sem");
            return;
        }
        for (String key : list.getKeys(false)) {
            ConfigurationSection t = list.getConfigurationSection(key);
            int tier;
            try {
                tier = Integer.parseInt(key.trim());
            } catch (NumberFormatException exception) {
                problems.add("gens.yml -> generators.tiers." + key + ": a kulcs szám legyen");
                continue;
            }
            if (t == null) {
                continue;
            }
            Material block = Items.material(t.getString("block", "COAL_BLOCK"), Material.COAL_BLOCK);
            Material drop = Items.material(t.getString("drop", "COAL"), Material.COAL);
            if (!block.isBlock()) {
                problems.add("gens.yml -> generators.tiers." + key + ".block: ez nem blokk");
                block = Material.COAL_BLOCK;
            }
            tiers.put(tier, new Tier(tier, t.getString("name", "Generátor " + tier), block, drop, t.getString("drop-name", drop.name()),
                    Math.max(0, t.getLong("value", 1)), Math.max(0.5, t.getDouble("seconds", 5)), Math.max(0, t.getLong("price", 1000)),
                    Math.max(0, t.getInt("level", 1))));
        }
    }

    public Map<Integer, Tier> tiers() {
        return tiers;
    }

    public Tier tier(int tier) {
        return tiers.get(tier);
    }

    public int capacity(int tier) {
        return capacityBase + capacityPerTier * Math.max(0, tier - 1);
    }

    // ---- férőhelyek ----

    public int slots(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        long extra = profile == null ? 0 : profile.stat("gens.genslots");
        return (int) Math.min(maxSlots, baseSlots + extra);
    }

    public int placed(UUID owner) {
        List<Gen> list = byOwner.get(owner);
        return list == null ? 0 : list.size();
    }

    public ItemStack slotItem(int amount) {
        return Items.of(Material.PRISMARINE_CRYSTALS, Math.max(1, amount)).name("&b&lGenerátor férőhely &f(+5)")
                .lore("&7+5 generátort helyezhetsz le", "&7a szigeteden.", "", "&eJobb klikk &7» Felhasználás").glow().tag(SLOTS).build();
    }

    private boolean useSlotItem(Player player, ItemStack hand) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return false;
        }
        if (baseSlots + profile.stat("gens.genslots") >= maxSlots) {
            core.messages().sendTagged(player, "&cMár a legtöbb generátor férőhelyed megvan (" + maxSlots + ").");
            return false;
        }
        profile.addStat("gens.genslots", 5);
        hand.setAmount(hand.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
        core.messages().sendTagged(player, "&aMostantól &f" + slots(player) + " &agenerátort rakhatsz le.");
        return true;
    }

    // ---- tárgyak ----

    public ItemStack item(int tier) {
        Tier t = tiers.get(tier);
        if (t == null) {
            return null;
        }
        return Items.of(t.block()).name("&e&l" + t.name() + " &7(" + tier + ". szint)").lore(tierLore(t, null)).glow().tag(ITEM + tier).build();
    }

    private List<String> tierLore(Tier t, Player viewer) {
        List<String> lore = new ArrayList<>();
        lore.add("&8━━━━━━━━━━━━━━━━━━");
        lore.add("&7Drop: &f" + t.dropName());
        lore.add("&7Drop eladási ár: &e" + core.economy().format(gens.currency(), t.value()));
        lore.add("&7Sebesség: &f1 drop / " + Text.decimal(t.seconds()) + " mp");
        lore.add("&7Tároló: &f" + capacity(t.tier()) + " db");
        lore.add("");
        lore.add("&7Tedd le a szigeteden, és magától termel.");
        lore.add("&7Jobb klikk rá: begyűjtés, eladás, fejlesztés.");
        return lore;
    }

    public ItemStack dropItem(int tier, int amount) {
        Tier t = tiers.get(tier);
        if (t == null) {
            return null;
        }
        return Items.of(t.drop(), Math.max(1, Math.min(t.drop().getMaxStackSize(), amount))).name("&f" + t.dropName())
                .lore("&7Generátor drop &8(" + tier + ". szint)", "&7Eladási ár: &e" + core.economy().format(gens.currency(), t.value()) + " &7/ db",
                        "", "&7Eladás: &f/elad &7vagy a hordó").tag(DROP + tier).build();
    }

    /** Ha ez egy generátor drop, az ára darabonként (különben -1). */
    public long dropValue(ItemStack item) {
        String tag = Items.tagOf(item);
        if (tag == null || !tag.startsWith(DROP)) {
            return -1;
        }
        try {
            Tier t = tiers.get(Integer.parseInt(tag.substring(DROP.length())));
            return t == null ? -1 : t.value();
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static int tierOf(ItemStack item, String prefix) {
        String tag = Items.tagOf(item);
        if (tag == null || !tag.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(tag.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    // ---- betöltés, mentés (a gazda profiljában: x,y,z,szint,tárolt;...) ----

    void attach(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        detachQuiet(player.getUniqueId());
        List<Gen> list = new ArrayList<>();
        String text = profile.setting(SETTING, "");
        for (String part : text.split(";")) {
            String[] p = part.split(",");
            if (p.length < 4) {
                continue;
            }
            try {
                Gen gen = new Gen(player.getUniqueId(), Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), p.length > 4 ? Integer.parseInt(p[4]) : 0);
                if (!tiers.containsKey(gen.tier)) {
                    continue;
                }
                list.add(gen);
                byBlock.put(gen.key(), gen);
            } catch (NumberFormatException ignored) {
                // hibás bejegyzés: kihagyjuk
            }
        }
        byOwner.put(player.getUniqueId(), list);
    }

    void save(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        List<Gen> list = byOwner.get(player.getUniqueId());
        if (profile == null || list == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Gen gen : list) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(gen.x).append(',').append(gen.y).append(',').append(gen.z).append(',').append(gen.tier).append(',').append(gen.stored);
        }
        profile.put(SETTING, out.toString());
    }

    void detach(Player player) {
        save(player);
        detachQuiet(player.getUniqueId());
    }

    private void detachQuiet(UUID uuid) {
        List<Gen> list = byOwner.remove(uuid);
        if (list == null) {
            return;
        }
        for (Gen gen : list) {
            byBlock.remove(gen.key());
            if (gen.label != null) {
                gen.label.remove();
                gen.label = null;
            }
        }
    }

    void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (byOwner.containsKey(player.getUniqueId())) {
                save(player);
            }
        }
        for (UUID uuid : new ArrayList<>(byOwner.keySet())) {
            detachQuiet(uuid);
        }
    }

    void cleanLabels(World world) {
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(LABEL_TAG)) {
                entity.remove();
            }
        }
    }

    // ---- termelés ----

    void tick() {
        World world = gens.world();
        long now = System.currentTimeMillis();
        double elapsed = lastTick == 0 ? 0 : Math.min(5.0, (now - lastTick) / 1000.0);
        lastTick = now;
        boolean labels = ++labelTimer >= 2;
        if (labels) {
            labelTimer = 0;
        }
        if (world == null || elapsed <= 0) {
            return;
        }
        double eventSpeed = gens.extras().eventMultiplier(GensExtras.EVENT_GEN_SPEED);
        double eventDrop = gens.extras().eventMultiplier(GensExtras.EVENT_GEN_DROP);
        for (Map.Entry<UUID, List<Gen>> entry : byOwner.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null) {
                continue;
            }
            double speed = eventSpeed * gens.extras().boost(owner, GensExtras.BOOST_GEN);
            GensData.Player data = gens.data(owner);
            long autoMoney = 0;
            for (Gen gen : entry.getValue()) {
                Tier t = tiers.get(gen.tier);
                if (t == null) {
                    continue;
                }
                gen.progress += elapsed * speed / t.seconds();
                int made = (int) gen.progress;
                if (made > 0) {
                    gen.progress -= made;
                    int amount = (int) Math.round(made * eventDrop);
                    if (data != null && data.autosell) {
                        autoMoney += amount * t.value();
                    } else {
                        gen.stored = Math.min(capacity(gen.tier), gen.stored + amount);
                    }
                }
                if (labels && world.isChunkLoaded(gen.x >> 4, gen.z >> 4)) {
                    label(world, gen, t, data != null && data.autosell);
                }
            }
            if (autoMoney > 0) {
                gens.addAutosell(owner.getUniqueId(), autoMoney);
            }
        }
    }

    private void label(World world, Gen gen, Tier t, boolean auto) {
        String text = Text.color("&e&l" + t.name().toUpperCase(java.util.Locale.ROOT) + " &f" + gen.tier + ". szint\n"
                + (auto ? "&aAutomatikus eladás" : "&7Tárolt: " + (gen.stored >= capacity(gen.tier) ? "&c" : "&a") + gen.stored + "&7/&f" + capacity(gen.tier)));
        if (gen.label == null || !gen.label.isValid()) {
            Location at = new Location(world, gen.x + 0.5, gen.y + 1.35, gen.z + 0.5);
            gen.label = world.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(LABEL_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setViewRange(0.35F);
                d.setText(text);
            });
        } else if (!text.equals(gen.label.getText())) {
            gen.label.setText(text);
        }
    }

    // ---- események ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        int tier = tierOf(event.getItemInHand(), ITEM);
        if (tier < 0) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        GensData.Player data = gens.data(player);
        if (block.getWorld() != gens.world() || data == null || gens.plotAt(block.getLocation()) != data.plot || !tiers.containsKey(tier)) {
            event.setCancelled(true);
            core.messages().sendTagged(player, "&cGenerátort csak a saját szigetedre rakhatsz le.");
            return;
        }
        List<Gen> list = byOwner.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        if (list.size() >= slots(player)) {
            event.setCancelled(true);
            core.messages().sendTagged(player, "&cNincs több hely generátornak (&f" + list.size() + "/" + slots(player)
                    + "&c). Bővítés: Generátor bolt -> férőhely.");
            return;
        }
        Gen gen = new Gen(player.getUniqueId(), block.getX(), block.getY(), block.getZ(), tier, 0);
        list.add(gen);
        byBlock.put(gen.key(), gen);
        save(player);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.4F);
        core.messages().sendTagged(player, "&aLeraktál egy generátort! &7(" + list.size() + "/" + slots(player) + ")");
        gens.progress(player, "generator", 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Gen gen = byBlock.get(GensData.key(block.getX(), block.getY(), block.getZ()));
        if (gen == null || block.getWorld() != gens.world()) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        Player player = event.getPlayer();
        if (!gen.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            core.messages().sendTagged(player, "&cEzt a generátort csak a gazdája szedheti fel.");
            return;
        }
        pickUp(player, gen, false);
    }

    private void pickUp(Player player, Gen gen, boolean breakBlock) {
        collect(player, gen, false);
        List<Gen> list = byOwner.get(gen.owner);
        if (list != null) {
            list.remove(gen);
        }
        byBlock.remove(gen.key());
        if (gen.label != null) {
            gen.label.remove();
            gen.label = null;
        }
        if (breakBlock) {
            gens.world().getBlockAt(gen.x, gen.y, gen.z).setType(Material.AIR);
        }
        give(player, item(gen.tier));
        save(player);
        core.messages().sendTagged(player, "&eFelvetted a generátort. &7(" + placed(player.getUniqueId()) + "/" + slots(player) + ")");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld() != gens.world()) {
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && SLOTS.equals(Items.tagOf(event.getItem()))) {
            event.setCancelled(true);
            useSlotItem(player, event.getItem());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Gen gen = byBlock.get(GensData.key(block.getX(), block.getY(), block.getZ()));
        if (gen == null) {
            return;
        }
        event.setCancelled(true);
        if (!gen.owner.equals(player.getUniqueId()) && !gens.canCollect(player, block.getLocation())) {
            core.messages().sendTagged(player, "&cEz nem a te generátorod.");
            return;
        }
        if (player.isSneaking()) {
            collect(player, gen, true);
        } else {
            menu(player, gen);
        }
    }

    // ---- menü ----

    private void menu(Player player, Gen gen) {
        Tier t = tiers.get(gen.tier);
        if (t == null) {
            return;
        }
        Menu menu = new Menu("gens-generator", 3, Text.color("&8" + t.name() + " &7(" + gen.tier + ". szint)"));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE, null);
        String cur = gens.currency();
        menu.set(10, Items.of(Material.HOPPER).name("&a&lBegyűjtés")
                .lore("&7Tárolt: &f" + gen.stored + "&7/&f" + capacity(gen.tier), "", "&7A dropokat a táskádba teszi.",
                        "&8(Guggolva jobb klikk a generátorra: ugyanez)", "", "&e» Kattints").build(), p -> {
            collect(p, gen, true);
            menu(p, gen);
        });
        menu.set(11, Items.of(Material.GOLD_INGOT).name("&e&lEladás")
                .lore("&7Tárolt: &f" + gen.stored + " db", "&7Érték: &e" + core.economy().format(cur, gen.stored * t.value()), "", "&e» Kattints").build(), p -> {
            sellStored(p, gen);
            menu(p, gen);
        });
        menu.set(13, Items.of(t.block()).name("&e&l" + t.name()).lore(tierLore(t, player)).glow().build());
        Tier next = tiers.higherEntry(gen.tier) == null ? null : tiers.higherEntry(gen.tier).getValue();
        if (next == null) {
            menu.set(15, Items.of(Material.NETHER_STAR).name("&a&lMaximum szint").lore("&7Ennél jobb generátor nincs.").build());
        } else {
            long cost = upgradeCost(t, next);
            int level = gens.extras().level(player);
            List<String> lore = new ArrayList<>(List.of("&7Következő: &f" + next.name() + " &7(" + next.tier() + ". szint)",
                    "&7Drop: &f" + next.dropName() + " &7(" + core.economy().format(cur, next.value()) + "&7/db)",
                    "&7Sebesség: &f1 drop / " + Text.decimal(next.seconds()) + " mp", "",
                    "&7Ár: &e" + core.economy().format(cur, cost), "&7Szükséges Gens szint: " + (level >= next.level() ? "&a" : "&c") + next.level(), ""));
            lore.add(level >= next.level() ? "&e» Kattints a fejlesztéshez" : "&c✖ Még nem elég a szinted");
            menu.set(15, Items.of(Material.ANVIL).name("&b&lFejlesztés").lore(lore).build(), p -> {
                upgrade(p, gen);
                menu(p, gen);
            });
        }
        menu.set(16, Items.of(Material.BARRIER).name("&c&lFelvétel").lore("&7Visszakapod a generátort tárgyként", "&7(a tárolt dropokkal együtt).",
                "", "&e» Kattints").build(), p -> {
            if (gen.owner.equals(p.getUniqueId())) {
                p.closeInventory();
                pickUp(p, gen, true);
            } else {
                core.messages().sendTagged(p, "&cCsak a gazdája veheti fel.");
            }
        });
        menu.open(player);
    }

    private long upgradeCost(Tier from, Tier to) {
        return Math.max(0, to.price() - Math.round(from.price() * upgradeRefund));
    }

    private void upgrade(Player player, Gen gen) {
        Tier t = tiers.get(gen.tier);
        Map.Entry<Integer, Tier> nextEntry = tiers.higherEntry(gen.tier);
        if (t == null || nextEntry == null) {
            return;
        }
        Tier next = nextEntry.getValue();
        if (gens.extras().level(player) < next.level()) {
            core.messages().sendTagged(player, "&cEhhez &f" + next.level() + ". &cGens szint kell.");
            return;
        }
        long cost = upgradeCost(t, next);
        if (cost > 0 && core.economy().withdraw(player, gens.currency(), cost, "gens:generator-upgrade") != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(gens.currency(), cost));
            return;
        }
        collect(player, gen, false);
        gen.tier = next.tier();
        gen.progress = 0;
        World world = gens.world();
        world.getBlockAt(gen.x, gen.y, gen.z).setType(next.block(), false);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, gen.x + 0.5, gen.y + 1.0, gen.z + 0.5, 30, 0.4, 0.5, 0.4, 0.2);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.3F);
        Player owner = Bukkit.getPlayer(gen.owner);
        if (owner != null) {
            save(owner);
        }
        core.messages().sendTagged(player, "&aSikeresen fejlesztetted: &f" + next.name() + " &7(" + next.tier() + ". szint)");
        gens.progress(player, "upgrade", 1);
    }

    /** A tárolt dropok a táskába (ami nem fér, a hátizsákba vagy a lábad elé). */
    private void collect(Player player, Gen gen, boolean tell) {
        if (gen.stored <= 0) {
            if (tell) {
                core.messages().sendTagged(player, "&7Ez a generátor még üres.");
            }
            return;
        }
        int amount = gen.stored;
        gen.stored = 0;
        Tier t = tiers.get(gen.tier);
        while (amount > 0) {
            int stack = Math.min(amount, t.drop().getMaxStackSize());
            give(player, dropItem(gen.tier, stack));
            amount -= stack;
        }
        if (tell) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.2F);
        }
    }

    private void sellStored(Player player, Gen gen) {
        Tier t = tiers.get(gen.tier);
        if (t == null || gen.stored <= 0) {
            core.messages().sendTagged(player, "&7Nincs mit eladni.");
            return;
        }
        long money = gens.sellBoost(player, gens.data(player), gen.stored * t.value());
        if (core.economy().deposit(player, gens.currency(), money, "gens:generator-sell") != Ledger.Result.OK) {
            core.messages().send(player, "gens.sell-limit");
            return;
        }
        int sold = gen.stored;
        gen.stored = 0;
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.addStat("gens.earned", money);
        }
        gens.extras().addXp(player, Math.max(1, sold / 4));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6F, 1.1F);
        core.messages().sendTagged(player, "&aEladtál &f" + sold + " &adropot ennyiért: &e" + core.economy().format(gens.currency(), money));
        gens.progress(player, "earn", money);
    }

    private void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    // ---- bolt ----

    public void shop(Player player) {
        Menu menu = new Menu("gens-generator-shop", 6, Text.color("&8Generátor bolt"));
        menu.frame(Material.YELLOW_STAINED_GLASS_PANE, null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int level = gens.extras().level(player);
        String cur = gens.currency();
        int i = 0;
        for (Tier t : tiers.values()) {
            if (i >= slots.length) {
                break;
            }
            boolean ok = level >= t.level();
            List<String> lore = new ArrayList<>(tierLore(t, player));
            lore.add("");
            lore.add("&7Ár: &e" + core.economy().format(cur, t.price()));
            lore.add("&7Szükséges Gens szint: " + (ok ? "&a" : "&c") + t.level());
            lore.add("");
            lore.add(ok ? "&e» Kattints a vásárláshoz" : "&c✖ Még nem elég a szinted");
            menu.set(slots[i++], Items.of(ok ? t.block() : Material.BLACK_STAINED_GLASS).name((ok ? "&e&l" : "&8&l") + t.name() + " &7(" + t.tier() + ". szint)")
                    .lore(lore).hideAll().build(), p -> {
                buy(p, t);
                shop(p);
            });
        }
        long bought = core.profiles().profile(player) == null ? 0 : core.profiles().profile(player).stat("gens.genslots") / 5;
        long price = slotPrice * (bought + 1);
        menu.set(48, Items.of(Material.PRISMARINE_CRYSTALS).name("&b&lGenerátor férőhely &f(+5)")
                .lore("&7Most: &f" + placed(player.getUniqueId()) + "&7/&f" + slots(player) + " &7generátor", "&7Legfeljebb: &f" + maxSlots, "",
                        "&7Ár: &e" + core.economy().format(cur, price), "", "&e» Kattints a vásárláshoz").glow().build(), p -> {
            if (slots(p) >= maxSlots) {
                core.messages().sendTagged(p, "&cMár a legtöbb férőhelyed megvan.");
                return;
            }
            if (core.economy().withdraw(p, cur, price, "gens:genslots") != Ledger.Result.OK) {
                core.messages().send(p, "gens.no-money", "%price%", core.economy().format(cur, price));
                return;
            }
            give(p, slotItem(1));
            core.messages().sendTagged(p, "&aMegvetted! Jobb klikkel használd fel.");
            shop(p);
        });
        menu.set(50, Items.of(Material.BOOK).name("&e&lGenerátoraid").lore("&7Lerakva: &f" + placed(player.getUniqueId()) + "&7/&f" + slots(player),
                "&7Gens szinted: &f" + level, "", "&7Jobb klikk a generátorra: menü", "&7Guggolva jobb klikk: begyűjtés").build());
        menu.open(player);
    }

    private void buy(Player player, Tier t) {
        if (gens.extras().level(player) < t.level()) {
            core.messages().sendTagged(player, "&cEhhez &f" + t.level() + ". &cGens szint kell.");
            return;
        }
        if (t.price() > 0 && core.economy().withdraw(player, gens.currency(), t.price(), "gens:generator") != Ledger.Result.OK) {
            core.messages().send(player, "gens.no-money", "%price%", core.economy().format(gens.currency(), t.price()));
            return;
        }
        give(player, item(t.tier()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
        core.messages().sendTagged(player, "&aMegvetted: &f" + t.name() + "&a! Tedd le a szigeteden.");
    }

    /** Admin: generátor adása (/gens give <játékos> <szint>). */
    public boolean give(Player target, int tier, int amount) {
        ItemStack item = item(tier);
        if (item == null) {
            return false;
        }
        item.setAmount(Math.max(1, Math.min(64, amount)));
        give(target, item);
        return true;
    }

    /** Hány generátor van lerakva összesen (a toplistához / statisztikához). */
    Map<UUID, Integer> counts() {
        Map<UUID, Integer> out = new HashMap<>();
        for (Map.Entry<UUID, List<Gen>> e : byOwner.entrySet()) {
            out.put(e.getKey(), e.getValue().size());
        }
        return out;
    }
}
