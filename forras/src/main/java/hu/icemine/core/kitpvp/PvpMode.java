package hu.icemine.core.kitpvp;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.combat.CombatTag;
import hu.icemine.core.config.Values;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public abstract class PvpMode implements ModeHandler, Listener {

    protected final IceMineCore core;
    protected final String id;
    protected World world;
    protected final Map<String, Kit> kits = new LinkedHashMap<>();
    protected String defaultKit = "";
    protected final Map<UUID, String> equipped = new HashMap<>();
    protected final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<String, Long> farm = new HashMap<>();
    private final Set<UUID> died = new HashSet<>();
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Map<UUID, Long> jumped = new HashMap<>();
    private String currency = "kristaly";
    private long killReward = 10;
    private final TreeMap<Integer, Long> streakBonus = new TreeMap<>();
    private final Set<Integer> broadcastStreaks = new HashSet<>();
    private long sameVictimCooldown = 300000L;
    private boolean sameIpRewards = false;
    private long dailyCap = 2000;
    private double healOnKill = 6.0;
    protected double voidY = 0;
    protected boolean hunger = false;
    protected boolean keepLoot = false;
    protected final AbilityRunner abilities;

    protected PvpMode(IceMineCore core, String id) {
        this.core = core;
        this.id = id;
        this.abilities = new AbilityRunner(core, this);
    }

    /** Ugrás vagy lendülés után pár másodpercig nincs esési sebzés. */
    protected void markJumped(UUID player) {
        jumped.put(player, System.currentTimeMillis());
    }

    /** A képesség cooldown szorzója (a KitPvP fejlesztése csökkenti). */
    protected double cooldownMultiplier(Player player) {
        return 1.0;
    }

    /** Az ölésért járó pénz szorzója a módon belül (a KitPvP fejlesztése növeli). A rang szorzója ezen felül jön. */
    protected double rewardMultiplier(Player player) {
        return 1.0;
    }

    /** A kit felvétele után fut (a KitPvP itt teszi rá a fejlesztéseket). */
    protected void afterEquip(Player player, Kit kit) {
    }

    /** A kit menü alsó sora (a KitPvP ide teszi a fejlesztések gombot). */
    protected void decorateKitMenu(hu.icemine.core.menu.Menu menu, Player player) {
    }

    /** Másodpercenként: szól, ha a képesség újra kész. */
    public void tickAbilities() {
        abilities.tick();
    }

    public long abilityCooldown(Player player) {
        return abilities.remainingMillis(player);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public World world() {
        return world;
    }

    protected World createWorld(String name) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\"}");
        creator.generateStructures(false);
        return creator.createWorld();
    }

    protected void prepareWorld(World target, long time) {
        target.setDifficulty(Difficulty.EASY);
        target.setPVP(true);
        target.setSpawnFlags(false, false);
        target.setGameRuleValue("doDaylightCycle", "false");
        target.setGameRuleValue("doWeatherCycle", "false");
        target.setGameRuleValue("doMobSpawning", "false");
        target.setGameRuleValue("doFireTick", "false");
        target.setGameRuleValue("mobGriefing", "false");
        target.setGameRuleValue("keepInventory", "false");
        target.setGameRuleValue("doImmediateRespawn", "true");
        target.setGameRuleValue("showDeathMessages", "false");
        target.setGameRuleValue("announceAdvancements", "false");
        target.setGameRuleValue("naturalRegeneration", "true");
        target.setGameRuleValue("randomTickSpeed", "0");
        target.setStorm(false);
        target.setThundering(false);
        target.setTime(time);
    }

    protected void loadCommon(YamlConfiguration config, String file, List<String> problems) {
        Values values = new Values(file, config::get);
        kits.clear();
        ConfigurationSection section = config.getConfigurationSection("kits");
        if (section != null) {
            for (String kitId : section.getKeys(false)) {
                ConfigurationSection kitSection = section.getConfigurationSection(kitId);
                if (kitSection != null) {
                    Kit kit = Kit.read(file, kitSection, kitId, problems);
                    kits.put(kit.id, kit);
                }
            }
        }
        defaultKit = values.text("default-kit", kits.isEmpty() ? "" : kits.keySet().iterator().next()).toLowerCase(Locale.ROOT);
        if (!kits.containsKey(defaultKit) && !kits.isEmpty()) {
            problems.add(file + " -> default-kit: ismeretlen kit (" + defaultKit + ")");
            defaultKit = kits.keySet().iterator().next();
        }
        currency = values.text("rewards.currency", core.economy().defaultCurrency());
        if (core.economy().currency(currency) == null) {
            problems.add(file + " -> rewards.currency: ismeretlen pénznem (" + currency + ")");
            currency = core.economy().defaultCurrency();
        }
        killReward = values.number("rewards.kill", 10, 0, 100000);
        streakBonus.clear();
        ConfigurationSection bonus = config.getConfigurationSection("rewards.streak-bonus");
        if (bonus != null) {
            for (String key : bonus.getKeys(false)) {
                try {
                    streakBonus.put(Integer.parseInt(key), Math.max(0, bonus.getLong(key)));
                } catch (NumberFormatException exception) {
                    problems.add(file + " -> rewards.streak-bonus." + key + ": a kulcs csak szám lehet");
                }
            }
        }
        broadcastStreaks.clear();
        broadcastStreaks.addAll(config.getIntegerList("broadcast-streaks"));
        sameVictimCooldown = values.integer("rewards.same-victim-cooldown-seconds", 300, 0, 86400) * 1000L;
        sameIpRewards = values.bool("rewards.same-ip-rewards", false);
        dailyCap = values.number("rewards.daily-cap", 2000, 0, 100_000_000);
        healOnKill = values.decimal("heal-on-kill", 6.0, 0, 20);
        voidY = values.decimal("void-y", 0, -64, 320);
        hunger = values.bool("hunger", false);
        keepLoot = values.bool("drop-loot", false);
        problems.addAll(values.problems());
    }

    public Map<String, Kit> kits() {
        return kits;
    }

    protected String currency() {
        return currency;
    }

    /** A biztonságos zóna hotbarjának extra tárgyai (a KitPvP ide teszi a fejlesztéseket). */
    protected void extraHotbar(org.bukkit.inventory.PlayerInventory inventory) {
    }

    /** A fejlesztések menü (csak a KitPvP-ben van). */
    protected void openUpgrades(Player player) {
    }

    public boolean inWorld(Player player) {
        return world != null && player.getWorld() == world;
    }

    protected abstract boolean inSafe(Player player, Location location);

    protected abstract void prepare(Player player);

    protected abstract Location respawn(Player player);

    public String equipped(Player player) {
        return equipped.get(player.getUniqueId());
    }

    public boolean equip(Player player, String kitId) {
        Kit kit = kits.get(kitId);
        if (kit == null) {
            return false;
        }
        if (!core.ranks().canUse(player, kit)) {
            hu.icemine.core.rank.Ranks.Rank need = core.ranks().requiredFor(kit.id);
            if (need != null) {
                core.messages().send(player, "kit.locked-rank", "%rank%", need.name);
            } else {
                core.messages().send(player, "kit.locked");
            }
            return false;
        }
        kit.equip(player);
        equipped.put(player.getUniqueId(), kit.id);
        giveAbility(player, kit);
        afterEquip(player, kit);
        PlayerProfile profile = core.profiles().profile(player);
        if (profile != null) {
            profile.put(id + ".kit", kit.id);
        }
        return true;
    }

    private void giveAbility(Player player, Kit kit) {
        KitAbility ability = kit.ability();
        if (ability == null) {
            return;
        }
        ItemStack item = ability.item(kit.id, abilityLore(ability));
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        if (inventory.getItem(8) == null || inventory.getItem(8).getType().isAir()) {
            inventory.setItem(8, item);
        } else {
            inventory.addItem(item);
        }
        long left = abilities.remainingMillis(player);
        if (left > 0) {
            player.setCooldown(ability.material(), (int) (left / 50L));
        }
    }

    List<String> abilityLore(KitAbility ability) {
        List<String> lore = new ArrayList<>(ability.description);
        lore.add(core.messages().raw("ability.lore-cooldown").replace("%seconds%", String.valueOf(ability.cooldown)));
        lore.add(core.messages().raw("ability.lore-use"));
        return lore;
    }

    public Kit lastKit(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        String last = profile == null ? defaultKit : profile.setting(id + ".kit", defaultKit);
        Kit kit = kits.get(last);
        if (kit == null || !core.ranks().canUse(player, kit)) {
            kit = kits.get(defaultKit);
        }
        return kit;
    }

    protected void resetState(Player player, GameMode mode) {
        player.setGameMode(mode);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        try {
            player.setHealth(player.getMaxHealth());
        } catch (IllegalArgumentException ignored) {
        }
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setLevel(0);
        player.setExp(0f);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    @Override
    public void leave(Player player, boolean quitting) {
        equipped.remove(player.getUniqueId());
        abilities.reset(player.getUniqueId());
        streaks.remove(player.getUniqueId());
        warned.remove(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    @Override
    public void placeholders(Player player, Map<String, String> values) {
        PlayerProfile profile = core.profiles().profile(player);
        long kills = profile == null ? 0 : profile.stat(id + ".kills");
        long deaths = profile == null ? 0 : profile.stat(id + ".deaths");
        values.put("%kills%", Text.number(kills));
        values.put("%deaths%", Text.number(deaths));
        values.put("%kd%", Text.ratio(kills, deaths));
        values.put("%streak%", String.valueOf(streaks.getOrDefault(player.getUniqueId(), 0)));
        values.put("%best_streak%", String.valueOf(profile == null ? 0 : profile.stat(id + ".best_streak")));
        values.put("%combo%", String.valueOf(core.combo().current(player)));
        long ability = abilities.remainingMillis(player);
        values.put("%ability%", ability > 0 ? (ability + 999) / 1000 + " mp" : core.messages().raw("ability.board-ready"));
        String kit = equipped.get(player.getUniqueId());
        values.put("%kit%", kit == null || kits.get(kit) == null ? core.messages().get("kit.none") : Text.color(kits.get(kit).name));
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last == null || now - last > 2000) {
            warned.put(player.getUniqueId(), now);
            core.messages().actionBar(player, key);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getEntity().getWorld() != world) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = CombatTag.attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (inSafe(victim, victim.getLocation()) || inSafe(attacker, attacker.getLocation())) {
            event.setCancelled(true);
            warn(attacker, "pvp.safe-zone");
            return;
        }
        if (equipped.get(attacker.getUniqueId()) == null) {
            event.setCancelled(true);
            return;
        }
        Kit kit = kits.get(equipped.get(attacker.getUniqueId()));
        if (kit != null && event.getDamager() instanceof Player && !kit.onHit.isEmpty()) {
            for (Kit.HitEffect effect : kit.onHit) {
                if (Math.random() < effect.chance) {
                    victim.addPotionEffect(new PotionEffect(effect.type, effect.ticks, effect.amplifier, false, true));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getEntity().getWorld() != world || event instanceof EntityDamageByEntityEvent) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (inSafe(player, player.getLocation()) && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long left = jumped.remove(player.getUniqueId());
            if (left != null && System.currentTimeMillis() - left < 6000L) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!hunger && event.getEntity().getWorld() == world) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getWorld() != world) {
            return;
        }
        died.add(victim.getUniqueId());
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack item = drops.next();
            if (!keepLoot || Items.kitOf(item) != null || Items.tagOf(item) != null) {
                drops.remove();
            }
        }
        event.setDeathMessage(null);
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || killer.getWorld() != world) {
            killer = core.combat().lastAttacker(victim);
            if (killer != null && killer.getWorld() != world) {
                killer = null;
            }
        }
        PlayerProfile victimProfile = core.profiles().profile(victim);
        int lost = streaks.getOrDefault(victim.getUniqueId(), 0);
        streaks.remove(victim.getUniqueId());
        equipped.remove(victim.getUniqueId());
        abilities.reset(victim.getUniqueId());
        if (victimProfile != null) {
            victimProfile.addStat(id + ".deaths", 1);
        }
        String text;
        if (killer != null) {
            text = core.messages().prefixed("pvp.killed", "%victim%", victim.getName(), "%killer%", killer.getName(),
                    "%health%", String.format(Locale.ROOT, "%.1f", killer.getHealth() / 2.0));
            credit(killer, victim);
        } else {
            text = core.messages().prefixed("pvp.died", "%victim%", victim.getName());
        }
        if (lost >= 5) {
            text = text + core.messages().get("pvp.streak-ended", "%streak%", String.valueOf(lost));
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(text);
        }
    }

    private void credit(Player killer, Player victim) {
        PlayerProfile profile = core.profiles().profile(killer);
        if (profile == null) {
            return;
        }
        profile.addStat(id + ".kills", 1);
        int streak = streaks.getOrDefault(killer.getUniqueId(), 0) + 1;
        streaks.put(killer.getUniqueId(), streak);
        profile.maxStat(id + ".best_streak", streak);
        if (healOnKill > 0) {
            try {
                killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + healOnKill));
            } catch (IllegalArgumentException ignored) {
            }
        }
        long reward = rewardFor(killer, victim, profile, streak);
        if (reward > 0 && core.economy().deposit(killer, currency, reward, id + ":kill") == Ledger.Result.OK) {
            core.messages().actionBar(killer, "pvp.reward", "%reward%", core.economy().format(currency, reward), "%streak%", String.valueOf(streak));
        } else {
            core.messages().actionBar(killer, "pvp.kill-no-reward", "%streak%", String.valueOf(streak));
        }
        if (broadcastStreaks.contains(streak)) {
            String text = core.messages().prefixed("pvp.streak", "%player%", killer.getName(), "%streak%", String.valueOf(streak));
            for (Player player : world.getPlayers()) {
                player.sendMessage(text);
            }
        }
        onKill(killer, victim, streak);
    }

    protected void onKill(Player killer, Player victim, int streak) {
    }

    private long rewardFor(Player killer, Player victim, PlayerProfile profile, int streak) {
        if (!sameIpRewards && sameAddress(killer, victim)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = farm.get(key);
        if (last != null && now - last < sameVictimCooldown) {
            return 0;
        }
        farm.put(key, now);
        if (farm.size() > 5000) {
            farm.entrySet().removeIf(entry -> now - entry.getValue() > sameVictimCooldown);
        }
        long reward = killReward;
        Long bonus = streakBonus.get(streak);
        if (bonus != null) {
            reward += bonus;
        }
        reward = Math.round(reward * rewardMultiplier(killer));
        reward = core.ranks().boost(killer, reward);
        String today = LocalDate.now().toString();
        long earned = today.equals(profile.setting(id + ".reward.day", "")) ? parse(profile.setting(id + ".reward.sum", "0")) : 0;
        if (dailyCap > 0) {
            reward = Math.max(0, Math.min(reward, dailyCap - earned));
        }
        if (reward > 0) {
            profile.put(id + ".reward.day", today);
            profile.put(id + ".reward.sum", String.valueOf(earned + reward));
        }
        return reward;
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static boolean sameAddress(Player a, Player b) {
        InetSocketAddress first = a.getAddress();
        InetSocketAddress second = b.getAddress();
        return first != null && second != null && first.getAddress() != null && first.getAddress().equals(second.getAddress());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!died.remove(player.getUniqueId())) {
            return;
        }
        Location location = respawn(player);
        if (location != null) {
            event.setRespawnLocation(location);
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(core, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getWorld() == world) {
                prepare(online);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() != world) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (to.getY() < voidY) {
            if (!player.isDead()) {
                player.setHealth(0.0);
            }
            return;
        }
        boolean wasSafe = inSafe(player, from);
        boolean nowSafe = inSafe(player, to);
        if (!wasSafe && nowSafe && core.combat().tagged(player)) {
            Vector push = from.toVector().subtract(to.toVector()).setY(0);
            if (push.lengthSquared() < 1.0E-4) {
                push = new Vector(0, 0, 0);
            } else {
                push.normalize().multiply(0.6);
            }
            player.setVelocity(push.setY(0.25));
            event.setTo(from);
            warn(player, "pvp.no-safe-in-combat");
            return;
        }
        if (wasSafe && !nowSafe) {
            jumped.put(player.getUniqueId(), System.currentTimeMillis());
            leftSafe(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlIntoSafe(org.bukkit.event.player.PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() != world || event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (inSafe(player, to) && !inSafe(player, event.getFrom())) {
            event.setCancelled(true);
            core.messages().actionBar(player, "pvp.no-pearl-safe");
        }
    }

    public Map<UUID, Integer> currentStreaks() {
        return java.util.Collections.unmodifiableMap(streaks);
    }

    public void backToSpawn(Player player) {
        equipped.remove(player.getUniqueId());
        abilities.reset(player.getUniqueId());
        streaks.remove(player.getUniqueId());
        prepare(player);
    }

    protected void leftSafe(Player player) {
        if (equipped.get(player.getUniqueId()) == null) {
            Kit kit = lastKit(player);
            if (kit != null && equip(player, kit.id)) {
                core.messages().send(player, "kit.auto", "%kit%", kit.name);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (event.getPlayer().getWorld() == world && (Items.kitOf(dropped) != null || Items.tagOf(dropped) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (who.getWorld() != world) {
            return;
        }
        if (event.getInventory().getType() == InventoryType.CRAFTING || event.getInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean kitItem = Items.kitOf(current) != null || Items.kitOf(cursor) != null;
        if (kitItem) {
            event.setCancelled(true);
        }
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbar = who.getInventory().getItem(event.getHotbarButton());
            if (Items.kitOf(hotbar) != null) {
                event.setCancelled(true);
            }
        }
    }

    protected boolean hasArenas() {
        return false;
    }

    protected void openArenas(Player player) {
    }

    public void giveSafeHotbar(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItem(0, Items.of(org.bukkit.Material.CHEST).name(core.messages().raw("kit.item.menu")).lore(core.messages().list("kit.item.menu-lore")).tag("pvp:kits").build());
        Kit last = lastKit(player);
        if (last != null) {
            inventory.setItem(1, Items.of(last.icon).name(core.messages().raw("kit.item.last").replace("%kit%", last.name)).lore(core.messages().list("kit.item.last-lore")).hideAll().tag("pvp:last").build());
        }
        if (hasArenas()) {
            inventory.setItem(2, Items.of(org.bukkit.Material.COMPASS).name(core.messages().raw("arena.item")).lore(core.messages().list("arena.item-lore")).tag("pvp:arenas").build());
        }
        inventory.setItem(8, Items.of(org.bukkit.Material.RED_BED).name(core.messages().raw("pvp.item.lobby")).tag("pvp:lobby").build());
        inventory.setItem(7, Items.of(org.bukkit.Material.NETHER_STAR).name(core.messages().raw("mainmenu.item")).tag("pvp:menu").build());
        extraHotbar(inventory);
        inventory.setHeldItemSlot(0);
    }

    public void selectKit(Player player, String kitId) {
        if (player.getWorld() != world) {
            return;
        }
        if (!inSafe(player, player.getLocation())) {
            core.messages().send(player, "kit.only-safe");
            return;
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        Kit kit = kits.get(kitId);
        if (kit == null) {
            return;
        }
        if (equip(player, kit.id)) {
            core.messages().send(player, "kit.selected", "%kit%", kit.name);
            player.playSound(player.getLocation(), "minecraft:item.armor.equip_diamond", 0.8f, 1.2f);
        }
    }

    public void openKits(Player player) {
        hu.icemine.core.menu.Menu menu = new hu.icemine.core.menu.Menu(id + "-kits", Math.max(4, Math.min(6, (kits.size() + 6) / 7 + 2)),
                core.messages().raw("kit.menu-title"));
        menu.frame(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        String current = equipped.get(player.getUniqueId());
        int slot = 10;
        for (Kit kit : kits.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= menu.size() - 9) {
                break;
            }
            List<String> lore = new ArrayList<>(kit.description);
            if (kit.ability() != null) {
                lore.add("");
                lore.add(core.messages().raw("kit.lore.ability").replace("%name%", kit.ability().name)
                        .replace("%seconds%", String.valueOf(kit.ability().cooldown)));
                lore.addAll(kit.ability().description);
            }
            lore.add("");
            boolean allowed = core.ranks().canUse(player, kit);
            hu.icemine.core.rank.Ranks.Rank need = core.ranks().requiredFor(kit.id);
            if (kit.id.equals(current)) {
                lore.add(core.messages().raw("kit.lore.current"));
            } else if (allowed) {
                lore.add(core.messages().raw(need == null ? "kit.lore.click" : "kit.lore.click-rank").replace("%rank%", need == null ? "" : need.name));
            } else {
                if (need != null) {
                    lore.add(core.messages().raw("kit.lore.rank").replace("%rank%", need.name));
                }
                lore.add(core.messages().raw("kit.lore.locked"));
            }
            Items item = Items.of(allowed ? kit.icon : org.bukkit.Material.GRAY_DYE).name(kit.name).lore(lore).hideAll();
            if (kit.id.equals(current)) {
                item.glow();
            }
            String kitId = kit.id;
            menu.set(slot++, item.build(), clicker -> {
                clicker.closeInventory();
                selectKit(clicker, kitId);
            });
        }
        decorateKitMenu(menu, player);
        menu.open(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != world) {
            return;
        }
        String tag = Items.tagOf(event.getItem());
        if (tag != null && tag.startsWith(KitAbility.TAG)) {
            event.setCancelled(true);
            org.bukkit.event.block.Action action = event.getAction();
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                    && (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
                Kit kit = kits.get(tag.substring(KitAbility.TAG.length()));
                if (kit != null) {
                    abilities.use(player, kit);
                }
            }
            return;
        }
        if (tag == null || !tag.startsWith("pvp:")) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            return;
        }
        switch (tag) {
            case "pvp:kits":
                openKits(player);
                break;
            case "pvp:last":
                Kit last = lastKit(player);
                if (last != null) {
                    selectKit(player, last.id);
                }
                break;
            case "pvp:arenas":
                openArenas(player);
                break;
            case "pvp:upgrades":
                openUpgrades(player);
                break;
            case "pvp:menu":
                core.menus().main(player);
                break;
            case "pvp:lobby":
                core.modes().sendAndReport(player, hu.icemine.core.mode.ModeService.LOBBY);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        abilities.reset(uuid);
        died.remove(uuid);
        warned.remove(uuid);
        jumped.remove(uuid);
        streaks.remove(uuid);
        equipped.remove(uuid);
    }
}
