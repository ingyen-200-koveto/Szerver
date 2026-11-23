package hu.icemine.core.duels;

import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Items;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Attributes;
import hu.icemine.core.util.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Edzés egy gépi ellenfél ellen a Duels arénákban (könnyű, közepes, nehéz). */
public final class BotEdzes implements Listener {

    public static final String TAG = "icemine_duelbot";

    public enum Szint {
        KONNYU("konnyu", "&aKönnyű", 20, 0.22, 0.55, false, false, Material.LIME_DYE),
        KOZEPES("kozepes", "&eKözepes", 26, 0.27, 0.8, true, false, Material.YELLOW_DYE),
        NEHEZ("nehez", "&cNehéz", 32, 0.31, 1.0, true, true, Material.RED_DYE);

        final String id;
        final String name;
        final double health;
        final double speed;
        final double damage;
        final boolean strafe;
        final boolean crits;
        final Material icon;

        Szint(String id, String name, double health, double speed, double damage, boolean strafe, boolean crits, Material icon) {
            this.id = id;
            this.name = name;
            this.health = health;
            this.speed = speed;
            this.damage = damage;
            this.strafe = strafe;
            this.crits = crits;
            this.icon = icon;
        }

        static Szint of(String text) {
            if (text == null) {
                return null;
            }
            String t = text.toLowerCase(Locale.ROOT).replace("ű", "u").replace("ö", "o").replace("é", "e");
            for (Szint s : values()) {
                if (s.id.equals(t) || s.name().toLowerCase(Locale.ROOT).equals(t)) {
                    return s;
                }
            }
            return switch (t) {
                case "easy" -> KONNYU;
                case "medium", "normal" -> KOZEPES;
                case "hard" -> NEHEZ;
                default -> null;
            };
        }
    }

    private static final class Edzes {
        final UUID player;
        final ArenaSlot slot;
        final DuelKit kit;
        final Szint szint;
        Husk bot;
        BukkitTask task;
        int ticks;
        int strafeDir = 1;
        boolean fight;

        Edzes(UUID player, ArenaSlot slot, DuelKit kit, Szint szint) {
            this.player = player;
            this.slot = slot;
            this.kit = kit;
            this.szint = szint;
        }
    }

    private final Duels duels;
    private final Map<UUID, Edzes> sessions = new HashMap<>();
    private final Map<UUID, UUID> byBot = new HashMap<>();

    BotEdzes(Duels duels) {
        this.duels = duels;
    }

    public boolean training(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Ezekkel a módokkal lehet edzeni (sima PvP szabály). */
    private boolean usable(DuelKit kit) {
        return kit != null && kit.rule == DuelKit.Rule.NORMAL;
    }

    public void menu(Player player) {
        Menu menu = new Menu("duels-bot", 3, "&8Edzés bot ellen");
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int[] slots = {11, 13, 15};
        int i = 0;
        for (Szint s : Szint.values()) {
            PlayerProfile profile = duels.core().profiles().profile(player);
            long wins = profile == null ? 0 : profile.stat("duels.bot." + s.id);
            menu.set(slots[i++], Items.of(s.icon).name(s.name + " bot").lore(List.of(
                    "&7Életerő: &f" + (int) s.health / 2 + " szív",
                    "&7Gyorsaság: &f" + (s.speed < 0.25 ? "lassú" : (s.speed < 0.3 ? "átlagos" : "gyors")),
                    s.strafe ? "&7Kitér oldalra, nem áll egy helyben." : "&7Egyenesen jön feléd.",
                    s.crits ? "&7Ugrálva üt (kritikus ütések)." : "",
                    "",
                    "&7Legyőzted: &f" + wins + "x",
                    "",
                    "&e» Kattints, és indul a classic edzés!")).hideAll().build(), p -> {
                p.closeInventory();
                start(p, s, null);
            });
        }
        menu.open(player);
    }

    public void start(Player player, Szint szint, String kitId) {
        if (training(player) || duels.match(player) != null || duels.queuedFor(player) != null) {
            duels.core().messages().sendTagged(player, "&cMost nem kezdhetsz edzést (meccsben vagy sorban állsz).");
            return;
        }
        if (duels.core().combat().tagged(player)) {
            duels.core().messages().sendTagged(player, "&cHarc közben nem kezdhetsz edzést.");
            return;
        }
        DuelKit kit = duels.kit(kitId == null ? "classic" : kitId);
        if (!usable(kit)) {
            kit = null;
            for (DuelKit k : duels.kits().values()) {
                if (usable(k)) {
                    kit = k;
                    break;
                }
            }
            if (kitId != null && kit != null) {
                duels.core().messages().sendTagged(player, "&7Ebben a módban nem lehet bot ellen edzeni, a &f" + Text.strip(Text.color(kit.name)) + " &7módot indítom.");
            }
        }
        if (kit == null) {
            duels.core().messages().sendTagged(player, "&cNincs olyan mód, amivel edzeni lehetne.");
            return;
        }
        ArenaSlot slot = duels.freeSlotFor(kit);
        if (slot == null) {
            duels.core().messages().send(player, "duels.no-arena");
            return;
        }
        Edzes e = new Edzes(player.getUniqueId(), slot, kit, szint);
        slot.training = true;
        sessions.put(player.getUniqueId(), e);
        duels.core().guard().allowNext(player);
        player.teleport(slot.red);
        setupPlayer(player, kit);
        spawnBot(e, player);
        duels.core().messages().sendTagged(player, "&7Edzés indul: " + szint.name + " bot &7ellen. &8(Kilépés: /duels kilep)");
        e.task = Bukkit.getScheduler().runTaskTimer(duels.core(), () -> tick(e), 1L, 2L);
    }

    private void setupPlayer(Player player, DuelKit kit) {
        player.setGameMode(kit.usesBlocks() ? GameMode.SURVIVAL : GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0F);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        kit.equip(player, DuelKit.Side.RED);
        Attributes.oldCombat(player, kit.oldCombat);
        player.setMaximumNoDamageTicks(kit.hitDelay);
        player.setNoDamageTicks(0);
    }

    private void spawnBot(Edzes e, Player player) {
        Location at = e.slot.blue;
        Husk bot = at.getWorld().spawn(at, Husk.class, h -> {
            h.setAdult();
            h.setPersistent(false);
            h.setRemoveWhenFarAway(false);
            h.setCanPickupItems(false);
            h.setSilent(false);
            h.addScoreboardTag(TAG);
            h.setCustomName(Text.color(e.szint.name + " &7Edző Bot"));
            h.setCustomNameVisible(true);
            h.setAware(false);
            AttributeInstance hp = h.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(e.szint.health);
            }
            h.setHealth(e.szint.health);
            AttributeInstance speed = h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(e.szint.speed);
            }
            AttributeInstance follow = h.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
            if (follow != null) {
                follow.setBaseValue(48);
            }
            AttributeInstance reinf = h.getAttribute(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS);
            if (reinf != null) {
                reinf.setBaseValue(0);
            }
            EntityEquipment eq = h.getEquipment();
            if (eq != null) {
                ItemStack[] armor = player.getInventory().getArmorContents();
                eq.setBoots(copy(armor[0]));
                eq.setLeggings(copy(armor[1]));
                eq.setChestplate(copy(armor[2]));
                eq.setHelmet(copy(armor[3]));
                eq.setItemInMainHand(copy(weapon(player)));
                eq.setHelmetDropChance(0F);
                eq.setChestplateDropChance(0F);
                eq.setLeggingsDropChance(0F);
                eq.setBootsDropChance(0F);
                eq.setItemInMainHandDropChance(0F);
            }
        });
        e.bot = bot;
        byBot.put(bot.getUniqueId(), e.player);
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack weapon(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && (item.getType().name().endsWith("_SWORD") || item.getType().name().endsWith("_AXE"))) {
                return item;
            }
        }
        return new ItemStack(Material.IRON_SWORD);
    }

    private void tick(Edzes e) {
        Player player = Bukkit.getPlayer(e.player);
        if (player == null || !player.isOnline() || e.bot == null) {
            stop(e, null);
            return;
        }
        e.ticks++;
        if (!e.fight) {
            int left = 3 - e.ticks / 10;
            if (e.ticks % 10 == 1 && left > 0) {
                player.sendTitle(Text.color("&e" + left), "", 0, 12, 4);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1F, 1F);
            }
            if (e.ticks >= 30) {
                e.fight = true;
                e.bot.setAware(true);
                e.bot.setTarget(player);
                player.sendTitle(Text.color("&a&lHARC!"), "", 0, 15, 5);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4F, 1.6F);
            }
            return;
        }
        if (!e.bot.isValid()) {
            return;
        }
        if (player.getLocation().getY() < e.slot.fallY) {
            lose(e, player);
            return;
        }
        if (e.bot.getLocation().getY() < e.slot.fallY) {
            win(e, player);
            return;
        }
        if (e.bot.getTarget() != player) {
            e.bot.setTarget(player);
        }
        double dist = e.bot.getLocation().distance(player.getLocation());
        if (e.szint.strafe && dist < 4.0 && e.bot.isOnGround()) {
            if (e.ticks % 12 == 0) {
                e.strafeDir = -e.strafeDir;
            }
            Vector to = player.getLocation().toVector().subtract(e.bot.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 0.01) {
                Vector side = new Vector(-to.getZ(), 0, to.getX()).normalize().multiply(0.12 * e.strafeDir);
                e.bot.setVelocity(e.bot.getVelocity().add(side));
            }
        }
        if (e.szint.crits && dist < 3.2 && e.bot.isOnGround() && e.ticks % 8 == 0) {
            e.bot.setVelocity(e.bot.getVelocity().setY(0.42));
        }
    }

    /** A Duels onDamage hívja: a játékos halálos sebzést kapott edzés közben. */
    boolean lethal(Player player, double finalDamage) {
        Edzes e = sessions.get(player.getUniqueId());
        if (e == null) {
            return false;
        }
        if (!e.fight) {
            return true;
        }
        if (finalDamage >= player.getHealth()) {
            lose(e, player);
            return true;
        }
        return false;
    }

    private void win(Edzes e, Player player) {
        PlayerProfile profile = duels.core().profiles().profile(player);
        if (profile != null) {
            profile.addStat("duels.bot." + e.szint.id, 1);
        }
        player.sendTitle(Text.color("&a&lGYŐZTÉL!"), Text.color("&7Legyőzted a " + e.szint.name + " &7botot"), 5, 50, 15);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.2F);
        double hp = Math.round(player.getHealth() * 5) / 10.0;
        duels.core().messages().sendTagged(player, "&aSzép volt! &7Maradt &c" + hp + " ❤&7. "
                + (e.szint != Szint.NEHEZ ? "&7Próbáld meg a nehezebbet is: &f/duels bot" : "&7A legnehezebb botot is megverted!"));
        stop(e, player);
    }

    private void lose(Edzes e, Player player) {
        double hp = e.bot != null && e.bot.isValid() ? Math.round(e.bot.getHealth() * 5) / 10.0 : 0;
        player.sendTitle(Text.color("&c&lVESZTETTÉL"), Text.color("&7A botnak &c" + hp + " ❤ &7maradt"), 5, 50, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1F, 1F);
        duels.core().messages().sendTagged(player, "&7Most nem jött össze. Újra: &f/duels bot");
        stop(e, player);
    }

    private void stop(Edzes e, Player player) {
        sessions.remove(e.player);
        if (e.task != null) {
            e.task.cancel();
        }
        if (e.bot != null) {
            byBot.remove(e.bot.getUniqueId());
            if (e.bot.isValid()) {
                e.bot.remove();
            }
        }
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTaskLater(duels.core(), () -> {
                e.slot.training = false;
                if (player.isOnline() && player.getWorld() == e.slot.world && !training(player)) {
                    duels.resetPlayer(player);
                    duels.sendHome(player);
                }
            }, 40L);
        } else {
            e.slot.training = false;
        }
    }

    /** Kilépés, lecsatlakozás: azonnali leállítás. */
    public void quit(Player player) {
        Edzes e = sessions.get(player.getUniqueId());
        if (e != null) {
            stop(e, null);
            duels.resetPlayer(player);
        }
    }

    public void leaveCommand(Player player) {
        Edzes e = sessions.get(player.getUniqueId());
        if (e != null) {
            duels.core().messages().sendTagged(player, "&7Abbahagytad az edzést.");
            stop(e, player);
        }
    }

    public void shutdown() {
        for (Edzes e : new java.util.ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(e.player);
            stop(e, null);
            if (p != null) {
                duels.resetPlayer(p);
            }
        }
    }

    private Edzes ofBot(Entity entity) {
        UUID owner = entity == null ? null : byBot.get(entity.getUniqueId());
        return owner == null ? null : sessions.get(owner);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotHit(EntityDamageByEntityEvent event) {
        Edzes byDamager = ofBot(event.getDamager());
        if (byDamager != null) {
            if (!(event.getEntity() instanceof Player p) || !p.getUniqueId().equals(byDamager.player) || !byDamager.fight) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(event.getDamage() * byDamager.szint.damage);
            return;
        }
        Edzes victim = ofBot(event.getEntity());
        if (victim != null) {
            Entity source = event.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : event.getDamager();
            if (!source.getUniqueId().equals(victim.player) || !victim.fight) {
                event.setCancelled(true);
            } else {
                event.setCancelled(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBotDeath(EntityDeathEvent event) {
        Edzes e = ofBot(event.getEntity());
        if (e == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player player = Bukkit.getPlayer(e.player);
        event.getEntity().getWorld().spawnParticle(Particle.CLOUD, event.getEntity().getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
        e.bot = null;
        byBot.values().remove(e.player);
        if (player != null) {
            win(e, player);
        } else {
            stop(e, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBotTarget(EntityTargetEvent event) {
        Edzes e = ofBot(event.getEntity());
        if (e != null && (event.getTarget() == null || !event.getTarget().getUniqueId().equals(e.player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBotBurn(EntityCombustEvent event) {
        if (ofBot(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
