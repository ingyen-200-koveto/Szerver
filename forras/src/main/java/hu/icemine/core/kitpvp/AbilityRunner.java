package hu.icemine.core.kitpvp;

import hu.icemine.core.IceMineCore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * A kit képességek végrehajtása és a cooldownok. Minden PvP módnak (KitPvP, SkyPvP, ComboFly aréna) saját példánya van.
 * A képesség nem használható a biztonságos zónában, és nem hat azokra, akik ott állnak.
 */
final class AbilityRunner {

    static final String PROJECTILE = "icemine_ability";

    private final IceMineCore core;
    private final PvpMode mode;
    private final Map<UUID, Long> readyAt = new HashMap<>();
    /** Akiknek szólni kell, amikor újra kész a képesség (kit azonosító szerint). */
    private final Map<UUID, String> waiting = new HashMap<>();

    AbilityRunner(IceMineCore core, PvpMode mode) {
        this.core = core;
        this.mode = mode;
    }

    long remainingMillis(Player player) {
        Long ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0L : Math.max(0L, ready - System.currentTimeMillis());
    }

    void reset(UUID player) {
        readyAt.remove(player);
        waiting.remove(player);
    }

    /** Jobb klikk a képesség tárggyal. */
    void use(Player player, Kit kit) {
        KitAbility ability = kit.ability();
        if (ability == null) {
            return;
        }
        if (!kit.id.equals(mode.equipped(player))) {
            core.messages().actionBar(player, "ability.no-kit");
            return;
        }
        if (mode.inSafe(player, player.getLocation())) {
            core.messages().actionBar(player, "ability.safe-zone");
            return;
        }
        long left = remainingMillis(player);
        if (left > 0) {
            core.messages().actionBar(player, "ability.cooldown", "%seconds%", String.valueOf((left + 999) / 1000));
            return;
        }
        if (!run(player, ability)) {
            return;
        }
        long cooldown = ability.cooldownMillis(mode.cooldownMultiplier(player));
        readyAt.put(player.getUniqueId(), System.currentTimeMillis() + cooldown);
        waiting.put(player.getUniqueId(), kit.id);
        player.setCooldown(ability.material(), (int) (cooldown / 50L));
        core.messages().actionBar(player, "ability.used", "%name%", ability.name);
    }

    /** Másodpercenként: szól, ha újra kész a képesség. */
    void tick() {
        if (waiting.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, String>> iterator = waiting.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            Long ready = readyAt.get(entry.getKey());
            if (ready != null && ready > now) {
                continue;
            }
            iterator.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            Kit kit = mode.kits().get(entry.getValue());
            if (player != null && kit != null && kit.ability() != null && entry.getValue().equals(mode.equipped(player))) {
                core.messages().actionBar(player, "ability.ready", "%name%", kit.ability().name);
                player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 0.6f, 1.8f);
            }
        }
    }

    private boolean run(Player player, KitAbility ability) {
        World world = player.getWorld();
        Location at = player.getLocation();
        switch (ability.type) {
            case DASH: {
                Vector direction = at.getDirection().setY(0);
                if (direction.lengthSquared() < 1.0E-4) {
                    direction = new Vector(0, 0, 1);
                }
                player.setVelocity(direction.normalize().multiply(0.9 + ability.power * 0.5).setY(0.3));
                mode.markJumped(player.getUniqueId());
                world.spawnParticle(Particle.CLOUD, at.getX(), at.getY() + 0.2, at.getZ(), 20, 0.3, 0.1, 0.3, 0.02);
                world.playSound(at, "minecraft:entity.ender_dragon.flap", 0.8f, 1.6f);
                return true;
            }
            case LEAP: {
                Vector direction = at.getDirection().setY(0);
                if (direction.lengthSquared() > 1.0E-4) {
                    direction.normalize().multiply(0.4 + ability.power * 0.35);
                }
                player.setVelocity(direction.setY(0.6 + ability.power * 0.3));
                mode.markJumped(player.getUniqueId());
                world.spawnParticle(Particle.POOF, at.getX(), at.getY() + 0.1, at.getZ(), 16, 0.3, 0.05, 0.3, 0.03);
                world.playSound(at, "minecraft:entity.rabbit.jump", 1.0f, 0.7f);
                return true;
            }
            case HEAL: {
                double max = maxHealth(player);
                player.setHealth(Math.min(max, player.getHealth() + ability.power * 2.0));
                effect(player, "regeneration", 1, ability.duration);
                world.spawnParticle(Particle.HEART, at.getX(), at.getY() + 2.0, at.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
                world.playSound(at, "minecraft:entity.player.levelup", 0.6f, 1.6f);
                return true;
            }
            case RAGE: {
                effect(player, "strength", (int) Math.max(1, ability.power), ability.duration);
                effect(player, "speed", 1, ability.duration);
                world.spawnParticle(Particle.ANGRY_VILLAGER, at.getX(), at.getY() + 1.0, at.getZ(), 8, 0.5, 0.5, 0.5, 0.0);
                world.playSound(at, "minecraft:entity.ravager.roar", 0.7f, 1.2f);
                return true;
            }
            case SHIELD: {
                effect(player, "resistance", (int) Math.max(1, ability.power), ability.duration);
                world.spawnParticle(Particle.END_ROD, at.getX(), at.getY() + 1.0, at.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
                world.playSound(at, "minecraft:item.shield.block", 1.0f, 0.8f);
                return true;
            }
            case INVISIBLE: {
                effect(player, "invisibility", 1, ability.duration);
                effect(player, "speed", 1, ability.duration);
                world.spawnParticle(Particle.LARGE_SMOKE, at.getX(), at.getY() + 1.0, at.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
                world.playSound(at, "minecraft:entity.illusioner.mirror_move", 1.0f, 1.0f);
                return true;
            }
            case ARROWS: {
                int count = (int) Math.max(1, Math.min(12, ability.power));
                double spread = Math.toRadians(6);
                for (int i = 0; i < count; i++) {
                    double offset = (i - (count - 1) / 2.0) * spread;
                    Vector direction = rotate(player.getEyeLocation().getDirection(), offset).multiply(2.2);
                    Arrow arrow = player.launchProjectile(Arrow.class, direction);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setMetadata(PROJECTILE, new FixedMetadataValue(core, true));
                    Bukkit.getScheduler().runTaskLater(core, () -> {
                        if (arrow.isValid()) {
                            arrow.remove();
                        }
                    }, 100L);
                }
                world.playSound(at, "minecraft:entity.arrow.shoot", 1.0f, 0.8f);
                return true;
            }
            case FIREBALL: {
                Vector direction = player.getEyeLocation().getDirection().multiply(0.6 + ability.power * 0.4);
                SmallFireball fireball = player.launchProjectile(SmallFireball.class, direction);
                fireball.setIsIncendiary(false);
                fireball.setYield(0f);
                fireball.setMetadata(PROJECTILE, new FixedMetadataValue(core, true));
                world.playSound(at, "minecraft:entity.blaze.shoot", 1.0f, 1.0f);
                return true;
            }
            case FREEZE: {
                List<Player> targets = targets(player, ability.radius);
                for (Player target : targets) {
                    effect(target, "slowness", (int) Math.max(1, ability.power), ability.duration);
                    // csak a látvány (dér a képernyőn): 140 tick alatt a fagyás nem sebez
                    target.setFreezeTicks(Math.min(139, Math.max(target.getFreezeTicks(), ability.duration * 20 + 40)));
                    hurt(player, target, ability.damage);
                }
                ring(world, at, ability.radius, Particle.SNOWFLAKE);
                world.playSound(at, "minecraft:block.glass.break", 1.0f, 0.6f);
                return true;
            }
            case SHOCKWAVE: {
                for (Player target : targets(player, ability.radius)) {
                    Vector away = target.getLocation().toVector().subtract(at.toVector()).setY(0);
                    if (away.lengthSquared() < 1.0E-4) {
                        away = new Vector(0, 0, 1);
                    }
                    target.setVelocity(away.normalize().multiply(0.6 + ability.power * 0.4).setY(0.45));
                    hurt(player, target, ability.damage);
                }
                ring(world, at, ability.radius, Particle.SPLASH);
                world.spawnParticle(Particle.EXPLOSION, at.getX(), at.getY() + 0.5, at.getZ(), 1, 0, 0, 0, 0);
                world.playSound(at, "minecraft:entity.generic.explode", 0.7f, 1.4f);
                return true;
            }
            case SMOKE: {
                for (Player target : targets(player, ability.radius)) {
                    effect(target, "blindness", 1, ability.duration);
                    core.combat().tag(player, target);
                }
                world.spawnParticle(Particle.LARGE_SMOKE, at.getX(), at.getY() + 1.0, at.getZ(), 80, ability.radius / 2.0, 1.0, ability.radius / 2.0, 0.01);
                world.playSound(at, "minecraft:entity.generic.extinguish_fire", 1.0f, 0.6f);
                return true;
            }
            case LIGHTNING: {
                Player target = aimed(player, ability.radius);
                if (target == null) {
                    core.messages().actionBar(player, "ability.no-target", "%radius%", String.valueOf((int) ability.radius));
                    return false;
                }
                world.strikeLightningEffect(target.getLocation());
                hurt(player, target, Math.max(1.0, ability.damage));
                return true;
            }
            case PULL: {
                Player target = aimed(player, ability.radius);
                if (target == null) {
                    core.messages().actionBar(player, "ability.no-target", "%radius%", String.valueOf((int) ability.radius));
                    return false;
                }
                Vector toward = at.toVector().subtract(target.getLocation().toVector());
                double distance = toward.length();
                if (distance > 1.0E-4) {
                    toward.normalize().multiply(Math.min(2.5, 0.3 + distance * 0.12 * Math.max(0.5, ability.power)));
                }
                target.setVelocity(toward.setY(0.4));
                core.combat().tag(player, target);
                world.playSound(at, "minecraft:entity.fishing_bobber.retrieve", 1.0f, 0.8f);
                return true;
            }
            default:
                return false;
        }
    }

    /** A közeli, megtámadható ellenfelek: ugyanebben a világban, nem a biztonságos zónában, túlélő módban. */
    private List<Player> targets(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        double squared = radius * radius;
        for (Player other : player.getWorld().getPlayers()) {
            if (attackable(player, other) && other.getLocation().distanceSquared(player.getLocation()) <= squared) {
                result.add(other);
            }
        }
        return result;
    }

    private boolean attackable(Player player, Player other) {
        return !other.equals(player) && !other.isDead() && (other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE)
                && mode.equipped(other) != null && !mode.inSafe(other, other.getLocation());
    }

    /** A célkeresztben lévő ellenfél (vagy aki pár fokon belül van a nézési irányban). */
    private Player aimed(Player player, double range) {
        Entity looked = player.getTargetEntity((int) Math.ceil(range));
        if (looked instanceof Player target && attackable(player, target)) {
            return target;
        }
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Player best = null;
        double bestAngle = Math.toRadians(8);
        for (Player other : targets(player, range)) {
            Vector to = other.getLocation().add(0, 1.0, 0).toVector().subtract(eye);
            double angle = to.angle(direction);
            if (angle < bestAngle && player.hasLineOfSight(other)) {
                bestAngle = angle;
                best = other;
            }
        }
        return best;
    }

    private void hurt(Player attacker, Player target, double damage) {
        core.combat().tag(attacker, target);
        if (damage > 0) {
            target.damage(damage, attacker);
        }
    }

    private static void effect(Player player, String name, int level, int seconds) {
        PotionEffectType type = Kit.effect(name);
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, seconds * 20, Math.max(0, level - 1), false, true, true));
        }
    }

    private static void ring(World world, Location center, double radius, Particle particle) {
        int points = (int) Math.max(16, radius * 8);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(particle, center.getX() + Math.cos(angle) * radius, center.getY() + 0.3, center.getZ() + Math.sin(angle) * radius,
                    2, 0.05, 0.1, 0.05, 0.0);
        }
    }

    static Vector rotate(Vector vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    private static double maxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = hu.icemine.core.util.Attributes.maxHealth(player);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
