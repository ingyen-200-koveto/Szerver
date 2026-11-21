package hu.icemine.core.market;

import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Kulcsok (lootbox) és kulcsos ládák. Ha egy kulcshoz van lerakott láda, a kulcsot a ládánál lehet beváltani:
 * jobb klikk a ládára, és ha van nálad kulcs (kézben vagy az inventoryban), kapsz valamit a jutalmai közül
 * (tárgy, pénz vagy másik kulcs). Kulcs nélkül a láda hátralök. Bal klikk a ládára: mi lehet benne.
 * Ha egy kulcshoz nincs láda, a régi módon, magával a kulccsal lehet nyitni. Ládát lerakni: /market lada <kulcs>.
 */
public final class Keys implements Listener {

    static final String TAG = "market:key:";

    record Reward(ItemStack item, long money, String key, int keyAmount, double weight, String label) {
    }

    static final class Key {
        final String id;
        final String name;
        final Material material;
        final List<String> lore;
        final List<Reward> rewards = new ArrayList<>();
        final double announceBelow;
        double total;

        Key(String id, String name, Material material, List<String> lore, double announceBelow) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.lore = lore;
            this.announceBelow = announceBelow;
        }
    }

    private final Market market;
    private final Random random = new Random();
    private final Map<String, Key> keys = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> rankPackages = new LinkedHashMap<>();
    private long rankCooldown = 7 * 86_400_000L;
    /** "világ;x;y;z" -> kulcs azonosító */
    private final Map<String, String> crates = new LinkedHashMap<>();
    private final Map<String, UUID> holos = new LinkedHashMap<>();
    private final Set<UUID> nyit = new HashSet<>();

    Keys(Market market) {
        this.market = market;
    }

    void load(YamlConfiguration config, List<String> problems) {
        keys.clear();
        ConfigurationSection section = config.getConfigurationSection("keys");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                Key key = new Key(id.toLowerCase(Locale.ROOT), entry.getString("name", id), Items.material(entry.getString("material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK),
                        entry.getStringList("lore"), entry.getDouble("announce-below", 0));
                for (Map<?, ?> raw : entry.getMapList("rewards")) {
                    double weight = raw.get("weight") instanceof Number number ? number.doubleValue() : 1;
                    String label = raw.get("label") == null ? null : String.valueOf(raw.get("label"));
                    if (raw.get("item") != null) {
                        ItemStack item = Kit.parse(null, String.valueOf(raw.get("item")), "market.yml -> keys." + id, problems);
                        if (item != null) {
                            key.rewards.add(new Reward(item, 0, null, 0, weight, label));
                            key.total += weight;
                        }
                    } else if (raw.get("money") instanceof Number money) {
                        key.rewards.add(new Reward(null, money.longValue(), null, 0, weight, label));
                        key.total += weight;
                    } else if (raw.get("key") != null) {
                        int amount = raw.get("amount") instanceof Number number ? number.intValue() : 1;
                        key.rewards.add(new Reward(null, 0, String.valueOf(raw.get("key")).toLowerCase(Locale.ROOT), Math.max(1, amount), weight, label));
                        key.total += weight;
                    } else {
                        problems.add("market.yml -> keys." + id + ": minden jutalomnak kell item, money vagy key");
                    }
                }
                if (key.rewards.isEmpty()) {
                    problems.add("market.yml -> keys." + id + ": nincs egy jutalom sem");
                }
                keys.put(key.id, key);
            }
        }
        crates.clear();
        for (String line : config.getStringList("crates")) {
            String[] p = line.split(";");
            if (p.length == 5 && keys.containsKey(p[4].toLowerCase(Locale.ROOT))) {
                crates.put(p[0] + ";" + p[1] + ";" + p[2] + ";" + p[3], p[4].toLowerCase(Locale.ROOT));
            } else {
                problems.add("market.yml -> crates: hibás sor vagy ismeretlen kulcs: " + line);
            }
        }
        Bukkit.getScheduler().runTask(market.core(), this::holoAll);
        rankCooldown = Math.max(1, config.getLong("rank-keys.cooldown-days", 7)) * 86_400_000L;
        rankPackages.clear();
        ConfigurationSection ranks = config.getConfigurationSection("rank-keys.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) {
                Map<String, Integer> pack = new LinkedHashMap<>();
                ConfigurationSection entry = ranks.getConfigurationSection(rank);
                if (entry != null) {
                    for (String key : entry.getKeys(false)) {
                        if (!keys.containsKey(key.toLowerCase(Locale.ROOT))) {
                            problems.add("market.yml -> rank-keys.ranks." + rank + ": ismeretlen kulcs (" + key + ")");
                            continue;
                        }
                        pack.put(key.toLowerCase(Locale.ROOT), Math.max(1, entry.getInt(key)));
                    }
                }
                rankPackages.put(rank.toLowerCase(Locale.ROOT), pack);
            }
        }
    }

    String name(String id) {
        Key key = keys.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        return key == null ? String.valueOf(id) : key.name;
    }

    ItemStack item(String id, int amount) {
        Key key = keys.get(id.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        List<String> lore = new ArrayList<>(key.lore);
        lore.addAll(market.texts("key-lore"));
        ItemStack item = Items.of(key.material, Math.max(1, Math.min(64, amount))).name(key.name).lore(lore).glow().tag(TAG + key.id).build();
        return item;
    }

    public boolean give(Player player, String id, int amount) {
        ItemStack item = item(id, amount);
        if (item == null) {
            return false;
        }
        Market.give(player, item);
        return true;
    }

    public java.util.Set<String> ids() {
        return keys.keySet();
    }

    private Reward roll(Key key) {
        double value = random.nextDouble() * key.total;
        for (Reward reward : key.rewards) {
            value -= reward.weight();
            if (value <= 0) {
                return reward;
            }
        }
        return key.rewards.get(key.rewards.size() - 1);
    }

    private String label(Market.Scope scope, Reward reward) {
        if (reward.label() != null) {
            return reward.label();
        }
        if (reward.item() != null) {
            return reward.item().getAmount() + "x " + market.itemName(reward.item());
        }
        if (reward.key() != null) {
            return reward.keyAmount() + "x " + name(reward.key());
        }
        return market.money(scope, reward.money());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && crates.containsKey(loc(event.getClickedBlock()))) {
            crateClick(event);
            return;
        }
        String tag = Items.tagOf(event.getItem());
        if (tag == null || !tag.startsWith(TAG)) {
            return;
        }
        if (crates.containsValue(tag.substring(TAG.length())) && event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            market.core().messages().sendTagged(event.getPlayer(), "&7Ezt a kulcsot a kulcsos ládánál tudod kinyitni: jobb klikk a ládára.");
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        Key key = keys.get(tag.substring(TAG.length()));
        if (key == null || key.rewards.isEmpty()) {
            return;
        }
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            preview(player, key, scope);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            market.send(player, "inventory-full");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        open(player, key, scope, player.getLocation());
    }

    private Reward open(Player player, Key key, Market.Scope scope, Location at) {
        Reward reward = roll(key);
        if (reward.item() != null) {
            Market.give(player, reward.item().clone());
        } else if (reward.key() != null) {
            give(player, reward.key(), reward.keyAmount());
        } else if (reward.money() > 0) {
            market.core().economy().deposit(player, scope.currency(), reward.money(), "market:key");
        }
        String label = label(scope, reward);
        market.send(player, "key-opened", "%key%", key.name, "%reward%", label);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.4, 0.5, 0.4, 0.15);
        if (key.announceBelow > 0 && reward.weight() <= key.announceBelow) {
            market.broadcast(scope, "key-rare", "%player%", player.getName(), "%key%", key.name, "%reward%", label);
        }
        return reward;
    }

    // ---- kulcsos ládák ----

    static String loc(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Block block(String loc) {
        String[] p = loc.split(";");
        World w = Bukkit.getWorld(p[0]);
        return w == null ? null : w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    private void crateClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block b = event.getClickedBlock();
        Key key = keys.get(crates.get(loc(b)));
        if (key == null) {
            return;
        }
        Market.Scope scope = market.scopeOf(b.getWorld());
        if (scope == null) {
            scope = market.scopeOf(player);
        }
        if (scope == null) {
            market.core().messages().sendTagged(player, "&cEz a láda itt nem használható.");
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            preview(player, key, scope);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !nyit.add(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(market.core(), () -> nyit.remove(player.getUniqueId()), 30L);
        int slot = keySlot(player, key.id);
        if (slot < 0) {
            Vector v = player.getLocation().toVector().subtract(b.getLocation().add(0.5, 0, 0.5).toVector()).setY(0);
            if (v.lengthSquared() < 0.01) {
                v = player.getLocation().getDirection().multiply(-1).setY(0);
            }
            player.setVelocity(v.normalize().multiply(1.1).setY(0.45));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
            market.core().messages().sendTagged(player, "&cEhhez a ládához kulcs kell: " + key.name + "&c. Bal klikk: mi lehet benne.");
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            market.send(player, "inventory-full");
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
        Location at = b.getLocation().add(0.5, 1.1, 0.5);
        Reward reward = open(player, key, scope, at);
        b.getWorld().spawnParticle(Particle.FIREWORK, at, 40, 0.3, 0.4, 0.3, 0.12);
        b.getWorld().playSound(at, Sound.BLOCK_CHEST_OPEN, 1F, 1F);
        animate(player, key, scope, reward);
    }

    /** Rövid pörgetés a képernyő közepén (csak látvány, a nyeremény már megvan). */
    private void animate(Player player, Key key, Market.Scope scope, Reward final_) {
        int[] step = {0};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(market.core(), () -> {
            if (!player.isOnline() || step[0] >= 12) {
                task[0].cancel();
                return;
            }
            boolean last = step[0] == 11;
            Reward r = last ? final_ : key.rewards.get(random.nextInt(key.rewards.size()));
            player.sendTitle(Text.color(key.name), Text.color((last ? "&a&l" : "&7") + label(scope, r)), 0, last ? 40 : 6, last ? 10 : 0);
            player.playSound(player.getLocation(), last ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, last ? 1.4F : 1F + step[0] * 0.05F);
            step[0]++;
        }, 0L, 3L);
    }

    private static int keySlot(Player player, String keyId) {
        String want = TAG + keyId;
        if (want.equals(Items.tagOf(player.getInventory().getItemInMainHand()))) {
            return player.getInventory().getHeldItemSlot();
        }
        ItemStack[] items = player.getInventory().getStorageContents();
        for (int i = 0; i < items.length; i++) {
            if (want.equals(Items.tagOf(items[i]))) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (crates.containsKey(loc(event.getBlock()))) {
            event.setCancelled(true);
            market.core().messages().sendTagged(event.getPlayer(), "&cEz kulcsos láda. Levétel: &f/market lada torol &c(rá kell nézni).");
        }
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent event) {
        for (String l : crates.keySet()) {
            Block b = block(l);
            if (b != null && b.getWorld() == event.getWorld() && b.getX() >> 4 == event.getChunk().getX() && b.getZ() >> 4 == event.getChunk().getZ()) {
                holo(l);
            }
        }
    }

    private void holoAll() {
        for (UUID id : holos.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        holos.clear();
        for (String l : crates.keySet()) {
            Block b = block(l);
            if (b != null && b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                holo(l);
            }
        }
    }

    private void holo(String l) {
        UUID old = holos.get(l);
        Entity existing = old == null ? null : Bukkit.getEntity(old);
        if (existing != null && existing.isValid()) {
            return;
        }
        Block b = block(l);
        Key key = keys.get(crates.get(l));
        if (b == null || key == null) {
            return;
        }
        try {
            TextDisplay d = b.getWorld().spawn(b.getLocation().add(0.5, 1.6, 0.5), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                t.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(key.name + "\n&fJobb klikk kulccsal: &anyitás\n&7Bal klikk: mi lehet benne"));
                t.setShadowed(true);
            });
            holos.put(l, d.getUniqueId());
        } catch (RuntimeException ignored) {
        }
    }

    public void shutdown() {
        for (UUID id : holos.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        holos.clear();
    }

    /** /market lada <kulcs> | torol | lista */
    public void crateCommand(Player player, String[] args) {
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("lista") || sub.isEmpty()) {
            if (crates.isEmpty()) {
                market.core().messages().sendTagged(player, "&7Még nincs kulcsos láda. Nézz egy blokkra, és: &f/market lada <kulcs> &7(" + String.join(", ", keys.keySet()) + ")");
            }
            for (Map.Entry<String, String> e : crates.entrySet()) {
                market.core().messages().sendTagged(player, "&f" + e.getKey().replace(";", " ") + " &8- " + name(e.getValue()));
            }
            return;
        }
        Block b = player.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) {
            market.core().messages().sendTagged(player, "&cNézz rá egy blokkra (legfeljebb 6 blokkra).");
            return;
        }
        String l = loc(b);
        if (sub.equals("torol") || sub.equals("töröl")) {
            if (crates.remove(l) == null) {
                market.core().messages().sendTagged(player, "&cEz nem kulcsos láda.");
                return;
            }
            UUID h = holos.remove(l);
            Entity e = h == null ? null : Bukkit.getEntity(h);
            if (e != null) {
                e.remove();
            }
            saveCrates();
            market.core().messages().sendTagged(player, "&aKulcsos láda törölve.");
            return;
        }
        if (!keys.containsKey(sub)) {
            market.core().messages().sendTagged(player, "&cNincs ilyen kulcs. Ezek vannak: &f" + String.join(", ", keys.keySet()));
            return;
        }
        if (market.scopeOf(b.getWorld()) == null) {
            market.core().messages().sendTagged(player, "&cKulcsos ládát csak a Kingdoms vagy a OneBlock világában lehet lerakni, mert a nyeremények (pénz, tárgyak) oda szólnak.");
            return;
        }
        if (b.getType() != Material.CHEST && b.getType() != Material.ENDER_CHEST && b.getType() != Material.TRAPPED_CHEST) {
            b.setType(Material.ENDER_CHEST);
        }
        crates.put(l, sub);
        saveCrates();
        holo(l);
        market.core().messages().sendTagged(player, "&aKész: ez mostantól " + name(sub) + " &aláda. Kulccsal jobb klikk nyitja, kulcs nélkül hátralök.");
    }

    private void saveCrates() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : crates.entrySet()) {
            out.add(e.getKey() + ";" + e.getValue());
        }
        market.config().set("crates", out);
        market.core().configs().save("market");
    }

    public java.util.Set<String> crateKeys() {
        return new java.util.HashSet<>(crates.values());
    }

    private void preview(Player player, Key key, Market.Scope scope) {
        int rows = Math.max(2, Math.min(6, (key.rewards.size() + 8) / 9 + 1));
        Menu menu = new Menu("market-key-" + key.id, rows, market.text("key-preview-title", "%key%", Text.strip(Text.color(key.name))));
        int slot = 0;
        for (Reward reward : key.rewards) {
            if (slot >= (rows - 1) * 9) {
                break;
            }
            ItemStack icon;
            if (reward.item() != null) {
                icon = reward.item().clone();
            } else if (reward.key() != null) {
                ItemStack keyItem = item(reward.key(), reward.keyAmount());
                icon = keyItem == null ? new ItemStack(Material.TRIPWIRE_HOOK) : keyItem;
            } else {
                icon = Items.of(Material.GOLD_INGOT).name("&e" + market.money(scope, reward.money())).build();
            }
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(Text.color(market.text("key-chance", "%chance%", Text.decimal(reward.weight() / key.total * 100))));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            menu.set(slot++, icon);
        }
        menu.open(player);
    }

    /** /kulcs: a rang heti kulcs csomagja (piaconként egyszer). */
    public void claimRank(Player player) {
        Market.Scope scope = market.require(player);
        if (scope == null) {
            return;
        }
        hu.icemine.core.rank.Ranks.Rank rank = market.core().ranks().of(player);
        Map<String, Integer> pack = rank == null ? null : rankPackages.get(rank.id);
        if (pack == null || pack.isEmpty()) {
            market.send(player, "kulcs-no-rank");
            return;
        }
        PlayerProfile profile = market.core().profiles().profile(player);
        if (profile == null) {
            return;
        }
        long last;
        try {
            last = Long.parseLong(profile.setting("market.kulcs." + scope.id(), "0"));
        } catch (NumberFormatException exception) {
            last = 0;
        }
        long left = last + rankCooldown - System.currentTimeMillis();
        if (left > 0) {
            market.send(player, "kulcs-cooldown", "%time%", Text.duration(left));
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pack.entrySet()) {
            give(player, entry.getKey(), entry.getValue());
            parts.add(entry.getValue() + "x " + name(entry.getKey()));
        }
        profile.put("market.kulcs." + scope.id(), String.valueOf(System.currentTimeMillis()));
        market.send(player, "kulcs-claimed", "%keys%", String.join("&7, ", parts), "%rank%", rank.name);
    }
}
