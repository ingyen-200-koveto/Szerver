package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Inváziók: a támadó egy ellenséges földön elindítja ("/k invade"), ott megjelenik a védő királyság bajnoka (champion).
 * Ha a támadók legyőzik az idő lejárta előtt, a föld az övék (a Nexus földjénél sok NP és pénz is), ha nem, a védők nyernek.
 * Ide tartozik a masswar is: időnként egy órára mindenki támadhat mindenkit, olcsóbban.
 */
public final class Invasions implements Listener {

    static final class Invasion {
        final UUID attacker;
        final UUID defender;
        final long chunk;
        final UUID starter;
        final boolean nexus;
        final long started;
        final long ends;
        final Location origin;
        LivingEntity champion;
        final List<LivingEntity> helpers = new ArrayList<>();
        final Set<Integer> reinforced = new HashSet<>();
        BossBar bar;
        long starterAway;
        double maxHealth;

        Invasion(UUID attacker, UUID defender, long chunk, UUID starter, boolean nexus, long started, long ends, Location origin) {
            this.attacker = attacker;
            this.defender = defender;
            this.chunk = chunk;
            this.starter = starter;
            this.nexus = nexus;
            this.started = started;
            this.ends = ends;
            this.origin = origin;
        }
    }

    private final IceMineKingdoms plugin;
    private final Map<Long, Invasion> active = new HashMap<>();
    private final NamespacedKey championKey;
    private boolean masswar;
    private long masswarEnds;
    private long masswarNext;
    private BossBar masswarBar;
    private int ticks;

    Invasions(IceMineKingdoms plugin) {
        this.plugin = plugin;
        this.championKey = new NamespacedKey(plugin.plugin(), "kingdoms_champion");
    }

    private KingdomManager km() {
        return plugin.manager();
    }

    private KConfig cfg() {
        return plugin.settings();
    }

    // ---- lekérdezések ----

    public boolean defending(Kingdom kingdom) {
        for (Invasion invasion : active.values()) {
            if (invasion.defender.equals(kingdom.id)) {
                return true;
            }
        }
        return false;
    }

    public boolean attacking(Kingdom kingdom) {
        for (Invasion invasion : active.values()) {
            if (invasion.attacker.equals(kingdom.id)) {
                return true;
            }
        }
        return false;
    }

    public boolean invadedChunk(long chunk) {
        return active.containsKey(chunk);
    }

    /** A játékos királysága támadja-e éppen ezt a földet (ilyenkor bonthat és építhet ott). */
    public boolean attackerHere(Player player, long chunk) {
        Invasion invasion = active.get(chunk);
        Kingdom mine = km().kingdomOf(player.getUniqueId());
        return invasion != null && mine != null && (mine.id.equals(invasion.attacker) || helper(mine, invasion));
    }

    /** A játékos királysága éppen támadja-e ezt a királyságot (a tornyok ilyenkor rá is lőnek). */
    public boolean attackerAgainst(Player player, Kingdom kingdom) {
        Kingdom mine = km().kingdomOf(player.getUniqueId());
        if (mine == null) {
            return false;
        }
        for (Invasion invasion : active.values()) {
            if (invasion.defender.equals(kingdom.id) && (invasion.attacker.equals(mine.id) || helper(mine, invasion))) {
                return true;
            }
        }
        return false;
    }

    /** A támadó szövetségesei és nemzettársai is segíthetnek. */
    private boolean helper(Kingdom kingdom, Invasion invasion) {
        Kingdom attacker = km().kingdom(invasion.attacker);
        Relation relation = km().relation(kingdom, attacker);
        return relation == Relation.ALLY || relation == Relation.NATION;
    }

    public boolean isChampion(Entity entity) {
        return entity.getPersistentDataContainer().has(championKey, PersistentDataType.STRING);
    }

    public List<String> describe(Kingdom kingdom) {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Invasion invasion : active.values()) {
            boolean defending = invasion.defender.equals(kingdom.id);
            if (!defending && !invasion.attacker.equals(kingdom.id)) {
                continue;
            }
            Kingdom other = km().kingdom(defending ? invasion.attacker : invasion.defender);
            lines.add(plugin.text(defending ? "war-line-defending" : "war-line-attacking", "%kingdom%", other == null ? "?" : other.display(),
                    "%chunk%", Lands.text(invasion.chunk), "%time%", plugin.duration(Math.max(0, invasion.ends - now))));
        }
        return lines;
    }

    public boolean masswar() {
        return masswar;
    }

    public long masswarEnds() {
        return masswarEnds;
    }

    public long masswarNext() {
        return masswarNext;
    }

    // ---- indítás ----

    public void start(Player player) {
        if (player.getWorld() != plugin.world()) {
            plugin.send(player, "only-in-kingdoms");
            return;
        }
        Kingdom attacker = km().allowed(player, KPerm.INVADE);
        if (attacker == null) {
            return;
        }
        long chunk = Lands.key(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
        Kingdom defender = km().owner(chunk);
        if (defender == null || defender == attacker) {
            plugin.send(player, "invade-stand");
            return;
        }
        Relation relation = km().relation(attacker, defender);
        boolean allowed = relation.invade || masswar && cfg().masswarNeutralInvade && relation == Relation.NEUTRAL;
        if (!allowed) {
            plugin.send(player, "invade-not-enemy", "%kingdom%", defender.display());
            return;
        }
        if (cfg().pacifismEnabled && (attacker.pacifist || defender.pacifist)) {
            plugin.send(player, attacker.pacifist ? "you-are-pacifist" : "target-pacifist", "%kingdom%", defender.display());
            return;
        }
        if (km().shielded(defender)) {
            plugin.send(player, "invade-shielded", "%kingdom%", defender.display(), "%time%", plugin.duration(defender.shieldUntil - System.currentTimeMillis()));
            return;
        }
        long now = System.currentTimeMillis();
        if (attacker.invadeCooldownUntil > now) {
            plugin.send(player, "invade-cooldown", "%time%", plugin.duration(attacker.invadeCooldownUntil - now));
            return;
        }
        if (attacking(attacker)) {
            plugin.send(player, "invade-one-at-a-time");
            return;
        }
        if (active.containsKey(chunk)) {
            plugin.send(player, "invade-already");
            return;
        }
        if (plugin.structures().powercellProtects(defender, chunk)) {
            plugin.send(player, "invade-powercell");
            return;
        }
        if (km().online(defender).size() < cfg().minOnlineDefenders) {
            plugin.send(player, "invade-few-defenders", "%min%", String.valueOf(cfg().minOnlineDefenders));
            return;
        }
        long cost = masswar && cfg().masswarFree ? 0 : cfg().invadeCost;
        if (attacker.resourcePoints < cost) {
            plugin.send(player, "not-enough-rp", "%need%", String.valueOf(cost));
            return;
        }
        attacker.resourcePoints -= cost;
        attacker.shieldUntil = 0;
        attacker.dirty = true;
        boolean nexus = chunk == defender.nexusChunk();
        Invasion invasion = new Invasion(attacker.id, defender.id, chunk, player.getUniqueId(), nexus, now, now + cfg().invasionSeconds * 1000L,
                player.getLocation().clone());
        active.put(chunk, invasion);
        spawnChampion(invasion, defender);
        if (nexus) {
            spawnGuards(invasion, defender);
        }
        attacker.log(player.getName() + " inváziót indított: " + defender.name + " (" + Lands.text(chunk) + ").");
        defender.log(attacker.name + " megtámadta a(z) " + Lands.text(chunk) + " földet.");
        km().tell(attacker, "invasion-started", "%player%", player.getName(), "%kingdom%", defender.display(),
                "%time%", plugin.duration(cfg().invasionSeconds * 1000L));
        km().tell(defender, nexus ? "invasion-nexus-defend" : "invasion-defend", "%kingdom%", attacker.display(), "%x%", String.valueOf(player.getLocation().getBlockX()),
                "%z%", String.valueOf(player.getLocation().getBlockZ()));
        if (km().hasMisc(defender, "invasion-alert")) {
            for (Player member : km().online(defender)) {
                member.sendTitle(plugin.color(plugin.text("invasion-alert-title")), plugin.color(plugin.text("invasion-alert-subtitle", "%kingdom%", attacker.display())), 10, 60, 20);
                member.playSound(member.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
    }

    private void spawnChampion(Invasion invasion, Kingdom defender) {
        World world = plugin.world();
        EntityType type;
        try {
            type = EntityType.valueOf(cfg().championEntity);
        } catch (IllegalArgumentException exception) {
            type = EntityType.ZOMBIE;
        }
        if (type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            type = EntityType.ZOMBIE;
        }
        org.bukkit.util.Vector facing = invasion.origin.getDirection().setY(0);
        if (facing.lengthSquared() < 0.0001) {
            facing = new org.bukkit.util.Vector(0, 0, 1);
        }
        Location at = invasion.origin.clone().add(facing.normalize().multiply(2));
        at.setY(world.getHighestBlockYAt(at) + 1);
        Entity spawned = world.spawnEntity(at, type);
        if (!(spawned instanceof LivingEntity champion)) {
            spawned.remove();
            return;
        }
        double health = cfg().championHealth + upgrade(defender, "health");
        if (invasion.nexus) {
            KConfig.Placeable nexus = cfg().structures.get("nexus");
            double perLevel = nexus == null ? 0 : nexus.values.getOrDefault("champion-health-per-level", 0.0);
            health += perLevel * km().nexusLevel(defender);
        }
        set(champion, Attribute.GENERIC_MAX_HEALTH, health);
        champion.setHealth(health);
        set(champion, Attribute.GENERIC_ATTACK_DAMAGE, cfg().championDamage + upgrade(defender, "damage"));
        set(champion, Attribute.GENERIC_MOVEMENT_SPEED, cfg().championSpeed + upgrade(defender, "speed"));
        set(champion, Attribute.GENERIC_KNOCKBACK_RESISTANCE, Math.min(1.0, upgrade(defender, "resistance")));
        set(champion, Attribute.GENERIC_FOLLOW_RANGE, 32);
        champion.setCustomName(plugin.color(plugin.text("champion-name", "%kingdom%", defender.display())));
        champion.setCustomNameVisible(true);
        champion.setRemoveWhenFarAway(false);
        champion.setPersistent(false);
        champion.setCanPickupItems(false);
        champion.getPersistentDataContainer().set(championKey, PersistentDataType.STRING, defender.id + ":" + invasion.chunk);
        if (champion instanceof Zombie zombie) {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
        } else {
            champion.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        dress(champion, defender.level(defender.champion, "armor"));
        invasion.champion = champion;
        invasion.maxHealth = health;
        BossBar bar = Bukkit.createBossBar(plugin.color(plugin.text("champion-bar", "%kingdom%", defender.display())), BarColor.RED, BarStyle.SEGMENTED_20);
        bar.setProgress(1.0);
        invasion.bar = bar;
        world.strikeLightningEffect(at);
    }

    private void spawnGuards(Invasion invasion, Kingdom defender) {
        int guards = defender.level(defender.misc, "nexus-guards");
        for (int i = 0; i < guards; i++) {
            Location at = Structures.around(invasion.origin, 3);
            at.setY(plugin.world().getHighestBlockYAt(at) + 1);
            Zombie guard = plugin.world().spawn(at, Zombie.class, zombie -> {
                zombie.setAdult();
                zombie.setShouldBurnInDay(false);
                zombie.setPersistent(false);
                zombie.setCustomName(plugin.color(plugin.text("guard-name", "%kingdom%", defender.display())));
                zombie.getPersistentDataContainer().set(championKey, PersistentDataType.STRING, defender.id + ":" + invasion.chunk + ":guard");
                dress(zombie, 3);
            });
            invasion.helpers.add(guard);
        }
    }

    private double upgrade(Kingdom kingdom, String id) {
        KConfig.Upgrade upgrade = cfg().champion.get(id);
        return upgrade == null ? 0 : upgrade.value(kingdom.level(kingdom.champion, id));
    }

    private static void set(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void dress(LivingEntity entity, int armor) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        String[] tiers = {null, "LEATHER", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
        String tier = tiers[Math.max(0, Math.min(tiers.length - 1, armor))];
        if (tier != null) {
            equipment.setHelmet(new ItemStack(Material.valueOf(tier + "_HELMET")));
            equipment.setChestplate(new ItemStack(Material.valueOf(tier + "_CHESTPLATE")));
            equipment.setLeggings(new ItemStack(Material.valueOf(tier + "_LEGGINGS")));
            equipment.setBoots(new ItemStack(Material.valueOf(tier + "_BOOTS")));
        }
        equipment.setItemInMainHand(new ItemStack(armor >= 4 ? Material.DIAMOND_SWORD : armor >= 2 ? Material.IRON_SWORD : Material.STONE_SWORD));
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
    }

    // ---- menet ----

    void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        tickMasswar(now);
        Iterator<Map.Entry<Long, Invasion>> iterator = active.entrySet().iterator();
        List<Object[]> finished = new ArrayList<>();
        while (iterator.hasNext()) {
            Invasion invasion = iterator.next().getValue();
            Kingdom attacker = km().kingdom(invasion.attacker);
            Kingdom defender = km().kingdom(invasion.defender);
            if (attacker == null || defender == null || invasion.champion == null) {
                finished.add(new Object[]{invasion, false, "invasion-cancelled"});
                continue;
            }
            if (!invasion.champion.isValid() || invasion.champion.isDead()) {
                if (invasion.champion.isDead()) {
                    continue;
                }
                finished.add(new Object[]{invasion, false, "invasion-cancelled"});
                continue;
            }
            if (now >= invasion.ends) {
                finished.add(new Object[]{invasion, false, "invasion-timeout"});
                continue;
            }
            Player starter = Bukkit.getPlayer(invasion.starter);
            boolean present = starter != null && !starter.isDead() && starter.getWorld() == plugin.world()
                    && Math.abs((starter.getLocation().getBlockX() >> 4) - Lands.x(invasion.chunk)) <= 1
                    && Math.abs((starter.getLocation().getBlockZ() >> 4) - Lands.z(invasion.chunk)) <= 1;
            if (present) {
                invasion.starterAway = 0;
            } else if (invasion.starterAway == 0) {
                invasion.starterAway = now;
                if (starter != null) {
                    plugin.send(starter, "invasion-come-back", "%seconds%", String.valueOf(cfg().invasionLeaveSeconds));
                }
            } else if (now - invasion.starterAway > cfg().invasionLeaveSeconds * 1000L) {
                finished.add(new Object[]{invasion, false, "invasion-starter-left"});
                continue;
            }
            behave(invasion, defender, attacker, now);
        }
        for (Object[] done : finished) {
            end((Invasion) done[0], (Boolean) done[1], (String) done[2], null);
        }
    }

    private void behave(Invasion invasion, Kingdom defender, Kingdom attacker, long now) {
        LivingEntity champion = invasion.champion;
        if (champion.getLocation().distanceSquared(invasion.origin) > 24 * 24) {
            champion.teleport(invasion.origin);
        }
        List<Player> attackers = new ArrayList<>();
        for (Player player : plugin.world().getPlayers()) {
            Kingdom kingdom = km().kingdomOf(player.getUniqueId());
            if (kingdom != null && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE
                    && (kingdom.id.equals(invasion.attacker) || helper(kingdom, invasion)) && player.getLocation().distanceSquared(champion.getLocation()) < 40 * 40) {
                attackers.add(player);
            }
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : attackers) {
            double distance = player.getLocation().distanceSquared(champion.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (champion instanceof Mob mob && nearest != null) {
            mob.setTarget(nearest);
            for (LivingEntity helper : invasion.helpers) {
                if (helper instanceof Mob helperMob && helper.isValid()) {
                    helperMob.setTarget(nearest);
                }
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int thor = defender.level(defender.champion, "thor");
        if (thor > 0 && ticks % 5 == 0 && random.nextDouble() < 0.15 * thor) {
            for (Player player : attackers) {
                if (player.getLocation().distanceSquared(champion.getLocation()) < 36) {
                    plugin.world().strikeLightningEffect(player.getLocation());
                    player.damage(4.0, champion);
                }
            }
        }
        int drag = defender.level(defender.champion, "drag");
        if (drag > 0 && ticks % 3 == 0) {
            for (Player player : attackers) {
                double distance = player.getLocation().distance(champion.getLocation());
                if (distance > 8 && distance < 25 && random.nextDouble() < 0.25 * drag) {
                    Vector pull = champion.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.2).setY(0.4);
                    player.setVelocity(pull);
                    plugin.send(player, "champion-drag");
                }
            }
        }
        int reinforcements = defender.level(defender.champion, "reinforcements");
        if (reinforcements > 0 && invasion.maxHealth > 0) {
            double ratio = champion.getHealth() / invasion.maxHealth;
            for (int threshold : new int[]{75, 50, 25}) {
                if (ratio * 100 <= threshold && invasion.reinforced.add(threshold)) {
                    for (int i = 0; i < reinforcements; i++) {
                        Location at = Structures.around(champion.getLocation(), 2);
                        Zombie helper = plugin.world().spawn(at, Zombie.class, zombie -> {
                            zombie.setAdult();
                            zombie.setShouldBurnInDay(false);
                            zombie.setPersistent(false);
                            zombie.getPersistentDataContainer().set(championKey, PersistentDataType.STRING, defender.id + ":" + invasion.chunk + ":helper");
                            dress(zombie, 1);
                        });
                        invasion.helpers.add(helper);
                    }
                    plugin.world().playSound(champion.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.8f);
                }
            }
        }
        if (invasion.bar != null) {
            invasion.bar.setProgress(Math.max(0, Math.min(1, champion.getHealth() / Math.max(1, invasion.maxHealth))));
            invasion.bar.setTitle(plugin.color(plugin.text("champion-bar", "%kingdom%", defender.display())
                    + " &8| &f" + plugin.duration(Math.max(0, invasion.ends - now))));
            Set<Player> viewers = new HashSet<>();
            for (Player player : plugin.world().getPlayers()) {
                if (player.getLocation().distanceSquared(champion.getLocation()) < 50 * 50) {
                    viewers.add(player);
                }
            }
            for (Player viewer : new ArrayList<>(invasion.bar.getPlayers())) {
                if (!viewers.contains(viewer)) {
                    invasion.bar.removePlayer(viewer);
                }
            }
            for (Player viewer : viewers) {
                if (!invasion.bar.getPlayers().contains(viewer)) {
                    invasion.bar.addPlayer(viewer);
                }
            }
        }
        if (ticks % 2 == 0) {
            champion.getWorld().spawnParticle(Particle.FLAME, champion.getLocation().add(0, 2.3, 0), 3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /** Vége: true, ha a támadók nyertek. */
    void end(Invasion invasion, boolean attackersWon, String reason, Player killer) {
        active.remove(invasion.chunk);
        if (invasion.bar != null) {
            invasion.bar.removeAll();
        }
        if (invasion.champion != null && invasion.champion.isValid()) {
            invasion.champion.remove();
        }
        for (LivingEntity helper : invasion.helpers) {
            if (helper.isValid()) {
                helper.remove();
            }
        }
        Kingdom attacker = km().kingdom(invasion.attacker);
        Kingdom defender = km().kingdom(invasion.defender);
        long now = System.currentTimeMillis();
        if (attacker == null || defender == null) {
            return;
        }
        if (!attackersWon) {
            attacker.invadeCooldownUntil = now + cfg().cooldownFailed * 1000L;
            attacker.invasionsLost++;
            defender.invasionsWon++;
            attacker.dirty = true;
            defender.dirty = true;
            attacker.log("Az invázió nem sikerült: " + defender.name + ".");
            defender.log("Visszavertétek " + attacker.name + " támadását.");
            km().tell(attacker, reason, "%kingdom%", defender.display());
            km().tell(defender, "invasion-defended", "%kingdom%", attacker.display());
            return;
        }
        plugin.structures().destroyChunk(defender, invasion.chunk);
        double lootRp = invasion.nexus ? cfg().nexusLootRp : cfg().landLootRp;
        double lootBank = invasion.nexus ? cfg().nexusLootBank : cfg().landLootBank;
        long rp = Math.round(defender.resourcePoints * lootRp);
        long bank = Math.round(defender.bank * lootBank);
        defender.resourcePoints -= rp;
        defender.bank -= bank;
        attacker.resourcePoints += rp;
        attacker.bank += bank;
        if (defender.home != null && Lands.key((int) Math.floor(defender.home[0]) >> 4, (int) Math.floor(defender.home[2]) >> 4) == invasion.chunk) {
            defender.home = null;
        }
        km().transferLand(defender, attacker, invasion.chunk);
        attacker.invasionsWon++;
        defender.invasionsLost++;
        attacker.invadeCooldownUntil = now + cfg().cooldownSuccess * 1000L;
        defender.shieldUntil = Math.max(defender.shieldUntil, now + cfg().defeatShieldMinutes * 60_000L);
        km().addXp(attacker, cfg().xpPerInvasion);
        for (Player player : km().online(attacker)) {
            if (player.getWorld() == plugin.world() && player.getLocation().distanceSquared(invasion.origin) < 60 * 60) {
                KPlayer data = km().player(player);
                data.invasions++;
                data.dirty = true;
            }
        }
        attacker.dirty = true;
        defender.dirty = true;
        attacker.log("Elfoglaltátok " + defender.name + " földjét: " + Lands.text(invasion.chunk) + (invasion.nexus ? " (Nexus!)" : "") + ".");
        defender.log(attacker.name + " elfoglalta a(z) " + Lands.text(invasion.chunk) + " földet" + (invasion.nexus ? " a Nexusszal együtt" : "") + ".");
        km().tell(attacker, invasion.nexus ? "invasion-won-nexus" : "invasion-won", "%kingdom%", defender.display(),
                "%rp%", String.valueOf(rp), "%money%", plugin.money(bank));
        km().tell(defender, invasion.nexus ? "invasion-lost-nexus" : "invasion-lost", "%kingdom%", attacker.display(),
                "%rp%", String.valueOf(rp), "%money%", plugin.money(bank));
        if (invasion.nexus) {
            plugin.broadcast("nexus-fallen", "%kingdom%", defender.display(), "%attacker%", attacker.display());
        }
        plugin.world().playSound(invasion.origin, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
    }

    /** Leállításkor: minden invázió véget ér (a támadók nem nyernek, a bajnokok eltűnnek). */
    void stopAll() {
        for (Invasion invasion : new ArrayList<>(active.values())) {
            end(invasion, false, "invasion-cancelled", null);
        }
        if (masswarBar != null) {
            masswarBar.removeAll();
        }
    }

    public void surrender(Player player) {
        Kingdom kingdom = km().allowed(player, KPerm.INVADE);
        if (kingdom == null) {
            return;
        }
        for (Invasion invasion : new ArrayList<>(active.values())) {
            if (invasion.attacker.equals(kingdom.id)) {
                end(invasion, false, "invasion-surrendered", null);
                return;
            }
        }
        plugin.send(player, "no-invasion");
    }

    public void adminStop(long chunk) {
        Invasion invasion = active.get(chunk);
        if (invasion != null) {
            end(invasion, false, "invasion-cancelled", null);
        }
    }

    // ---- események ----

    private Invasion invasionOf(Entity entity) {
        String value = entity.getPersistentDataContainer().get(championKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        try {
            return active.get(Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Invasion invasion = invasionOf(victim);
        if (invasion != null) {
            if (!(damager instanceof Player player) || !attackerHere(player, invasion.chunk)) {
                event.setCancelled(true);
                return;
            }
            Kingdom defender = km().kingdom(invasion.defender);
            if (victim == invasion.champion && defender != null) {
                double determination = upgrade(defender, "determination");
                if (determination > 0) {
                    double cap = Math.max(3, 30 - determination);
                    event.setDamage(Math.min(event.getDamage(), cap));
                }
            }
            return;
        }
        Invasion attackerInvasion = invasionOf(damager);
        if (attackerInvasion != null && victim instanceof Player player && !attackerHere(player, attackerInvasion.chunk)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Invasion invasion = invasionOf(event.getEntity());
        if (invasion != null && event.getTarget() instanceof Player player && !attackerHere(player, invasion.chunk)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isChampion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!isChampion(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Invasion invasion = invasionOf(event.getEntity());
        if (invasion != null && event.getEntity() == invasion.champion) {
            end(invasion, true, "", event.getEntity().getKiller());
        }
    }

    /** Gúny: a bajnok közelében a támadók nem rakhatnak le blokkot. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        for (Invasion invasion : active.values()) {
            Kingdom defender = km().kingdom(invasion.defender);
            if (defender == null || invasion.champion == null || defender.level(defender.champion, "mock") <= 0) {
                continue;
            }
            if (event.getBlock().getWorld() == invasion.champion.getWorld()
                    && event.getBlock().getLocation().distanceSquared(invasion.champion.getLocation()) < 64 && attackerHere(event.getPlayer(), invasion.chunk)) {
                event.setCancelled(true);
                plugin.send(event.getPlayer(), "champion-mock");
                return;
            }
        }
    }

    // ---- masswar ----

    void loadMasswar() {
        masswarNext = plugin.getConfig().getLong("masswar.next", 0);
        if (masswarNext <= 0) {
            masswarNext = System.currentTimeMillis() + cfg().masswarIntervalHours * 3_600_000L;
            plugin.getConfig().set("masswar.next", masswarNext);
            plugin.saveConfig();
        }
    }

    private void tickMasswar(long now) {
        if (!cfg().masswarEnabled) {
            if (masswar) {
                stopMasswar();
            }
            return;
        }
        if (!masswar && masswarNext > 0 && now >= masswarNext) {
            startMasswar();
        } else if (masswar && now >= masswarEnds) {
            stopMasswar();
        }
        if (masswar && masswarBar != null) {
            masswarBar.setProgress(Math.max(0, Math.min(1, (masswarEnds - now) / (double) (cfg().masswarMinutes * 60_000L))));
            masswarBar.setTitle(plugin.color(plugin.text("masswar-bar", "%time%", plugin.duration(masswarEnds - now))));
            if (plugin.world() != null) {
                for (Player player : plugin.world().getPlayers()) {
                    if (!masswarBar.getPlayers().contains(player)) {
                        masswarBar.addPlayer(player);
                    }
                }
                for (Player player : new ArrayList<>(masswarBar.getPlayers())) {
                    if (player.getWorld() != plugin.world()) {
                        masswarBar.removePlayer(player);
                    }
                }
            }
        }
    }

    public void startMasswar() {
        masswar = true;
        masswarEnds = System.currentTimeMillis() + cfg().masswarMinutes * 60_000L;
        masswarNext = masswarEnds + cfg().masswarIntervalHours * 3_600_000L;
        plugin.getConfig().set("masswar.next", masswarNext);
        plugin.saveConfig();
        masswarBar = Bukkit.createBossBar(plugin.color(plugin.text("masswar-bar", "%time%", plugin.duration(cfg().masswarMinutes * 60_000L))), BarColor.RED, BarStyle.SOLID);
        plugin.broadcast("masswar-started", "%minutes%", String.valueOf(cfg().masswarMinutes));
        if (plugin.world() != null) {
            for (Player player : plugin.world().getPlayers()) {
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            }
        }
    }

    public void stopMasswar() {
        if (!masswar) {
            return;
        }
        masswar = false;
        if (masswarBar != null) {
            masswarBar.removeAll();
            masswarBar = null;
        }
        plugin.broadcast("masswar-ended");
    }
}
