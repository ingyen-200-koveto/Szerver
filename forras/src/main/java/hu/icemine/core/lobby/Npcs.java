package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public final class Npcs implements Listener {

    public static final String TAG = "icemine_npc";
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots", "hand"};

    public static final class Npc {
        public final String id;
        public final Location location;
        public final List<String> name;
        public final String type;
        public final String profession;
        public final Map<String, String> equipment;
        public final String action;
        public LivingEntity entity;
        public TextDisplay label;
        String shown;
        boolean failed;
        String skin = "";
        int citizenId = -1;
        boolean external;

        Npc(String id, Location location, List<String> name, String type, String profession, Map<String, String> equipment, String action) {
            this.id = id;
            this.location = location;
            this.name = name;
            this.type = type;
            this.profession = profession;
            this.equipment = equipment;
            this.action = action;
        }

        Npc moved(Location to) {
            Npc copy = new Npc(id, to, name, type, profession, equipment, action);
            copy.skin = skin;
            copy.citizenId = citizenId;
            return copy;
        }
    }

    private final IceMineCore core;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<UUID, Npc> byEntity = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final CitizensHook citizens;
    private final long citizensReadyAt = System.currentTimeMillis() + 15000L;
    private int ticks;

    public Npcs(IceMineCore core) {
        this.core = core;
        this.citizens = new CitizensHook(core.getLogger());
    }

    public void load(YamlConfiguration config, List<String> problems) {
        despawn();
        npcs.clear();
        World world = core.lobby().world();
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Location location = Locs.read(section.getConfigurationSection(id + ".location"), world);
            if (location == null) {
                problems.add("lobby.yml -> npcs." + id + ": hiányzik a hely (location)");
                continue;
            }
            String action = section.getString(id + ".action", "");
            if (action.isBlank()) {
                problems.add("lobby.yml -> npcs." + id + ".action: nincs megadva, mit csináljon kattintásra");
            }
            String type = section.getString(id + ".type", "villager").toLowerCase(Locale.ROOT);
            if (!type.equals("villager") && !type.equals("statue")) {
                problems.add("lobby.yml -> npcs." + id + ".type: villager vagy statue lehet, villagert használok");
                type = "villager";
            }
            Map<String, String> equipment = new LinkedHashMap<>();
            for (String slot : SLOTS) {
                String spec = section.getString(id + ".equipment." + slot, "");
                if (!spec.isBlank()) {
                    if (gear(spec) == null) {
                        problems.add("lobby.yml -> npcs." + id + ".equipment." + slot + ": ismeretlen tárgy (" + spec + ")");
                    } else {
                        equipment.put(slot, spec);
                    }
                }
            }
            Npc npc = new Npc(id, location, new ArrayList<>(section.getStringList(id + ".name")), type,
                    section.getString(id + ".profession", "librarian").toLowerCase(Locale.ROOT), equipment, action);
            npc.skin = section.getString(id + ".skin", "");
            npc.citizenId = section.getInt(id + ".citizens", -1);
            npcs.put(id, npc);
        }
    }

    static ItemStack gear(String spec) {
        String[] parts = spec.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || material.isAir()) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        if (parts.length > 1 && parts[1].startsWith("#")) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof LeatherArmorMeta) {
                try {
                    ((LeatherArmorMeta) meta).setColor(Color.fromRGB(Integer.parseInt(parts[1].substring(1), 16) & 0xFFFFFF));
                    item.setItemMeta(meta);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return item;
    }

    public Map<String, Npc> all() {
        return npcs;
    }

    public void move(String id, Location location) {
        YamlConfiguration config = core.configs().get("lobby");
        Locs.write(config, "npcs." + id + ".location", location);
        core.configs().save("lobby");
        Npc npc = npcs.get(id);
        if (npc != null) {
            remove(npc);
            Npc moved = npc.moved(location.clone());
            npcs.put(id, moved);
            spawn(moved);
        }
    }

    private void remove(Npc npc) {
        if (npc.entity != null) {
            byEntity.remove(npc.entity.getUniqueId());
            npc.entity.remove();
            npc.entity = null;
        }
        if (npc.label != null) {
            npc.label.remove();
            npc.label = null;
        }
        npc.shown = null;
    }

    public void despawn() {
        for (Npc npc : npcs.values()) {
            remove(npc);
        }
        byEntity.clear();
        World world = core.lobby() == null ? null : core.lobby().world();
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }

    public void spawnAll() {
        for (Npc npc : npcs.values()) {
            spawn(npc);
        }
    }

    private void spawn(Npc npc) {
        if (npc.failed) {
            return;
        }
        try {
            spawnNow(npc);
        } catch (RuntimeException | LinkageError error) {
            npc.failed = true;
            remove(npc);
            core.getLogger().warning("Nem sikerült lerakni ezt az NPC-t: " + npc.id + " (" + error + ")");
        }
    }

    private void spawnNow(Npc npc) {
        World world = npc.location.getWorld();
        world.setChunkForceLoaded(npc.location.getBlockX() >> 4, npc.location.getBlockZ() >> 4, true);
        if (citizens.available()) {
            npc.external = true;
            if (System.currentTimeMillis() >= citizensReadyAt) {
                citizen(npc);
            }
            label(world, npc);
            return;
        }
        LivingEntity entity;
        if (npc.type.equals("statue")) {
            entity = world.spawn(npc.location, ArmorStand.class);
        } else {
            entity = world.spawn(npc.location, Villager.class);
        }
        npc.entity = entity;
        byEntity.put(entity.getUniqueId(), npc);
        entity.addScoreboardTag(TAG);
        entity.setPersistent(false);
        entity.setSilent(true);
        entity.setGravity(false);
        entity.setRotation(npc.location.getYaw(), 0f);
        if (entity instanceof ArmorStand) {
            dress((ArmorStand) entity, npc);
        } else {
            setup((Villager) entity, npc);
        }
        label(world, npc);
    }

    private void citizen(Npc npc) {
        if (npc.citizenId >= 0 && citizens.ensure(npc.citizenId, npc.location)) {
            return;
        }
        String name = "icemine_" + npc.id;
        int id = citizens.find(name);
        if (id >= 0 && citizens.ensure(id, npc.location)) {
            remember(npc, id);
            return;
        }
        String hand = npc.equipment.get("hand");
        id = citizens.create(name, npc.location, npc.skin, hand == null ? null : gear(hand));
        if (id >= 0) {
            remember(npc, id);
        }
    }

    private void remember(Npc npc, int id) {
        npc.citizenId = id;
        YamlConfiguration config = core.configs().get("lobby");
        config.set("npcs." + npc.id + ".citizens", id);
        core.configs().save("lobby");
        if (!npc.skin.isBlank()) {
            citizens.skin(id, npc.skin);
        }
    }

    private Npc near(Entity entity) {
        if (!entity.hasMetadata("NPC")) {
            return null;
        }
        Location at = entity.getLocation();
        Npc best = null;
        double bestDistance = 2.25;
        for (Npc npc : npcs.values()) {
            if (npc.external && npc.location.getWorld() == at.getWorld()) {
                double distance = npc.location.distanceSquared(at);
                if (distance < bestDistance) {
                    best = npc;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private void label(World world, Npc npc) {
        if (npc.label != null && npc.label.isValid()) {
            return;
        }
        if (!npc.name.isEmpty()) {
            Location above = npc.location.clone().add(0, 2.35, 0);
            TextDisplay label = world.spawn(above, TextDisplay.class);
            npc.label = label;
            npc.shown = null;
            label.setPersistent(false);
            label.addScoreboardTag(TAG);
            label.setBillboard(Display.Billboard.CENTER);
            label.setDefaultBackground(false);
            label.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            label.setShadowed(true);
            refresh(npc);
        }
    }

    private static Object lookup(Iterable<?> registry, String id) {
        NamespacedKey key = NamespacedKey.minecraft(id);
        for (Object value : registry) {
            if (value instanceof Keyed && key.equals(((Keyed) value).getKey())) {
                return value;
            }
        }
        return null;
    }

    private void setup(Villager villager, Npc npc) {
        villager.setRemoveWhenFarAway(false);
        villager.setAI(false);
        villager.setCollidable(false);
        Object profession = lookup(Registry.VILLAGER_PROFESSION, npc.profession);
        if (profession != null) {
            villager.setProfession((Villager.Profession) profession);
        }
        Object type = lookup(Registry.VILLAGER_TYPE, "plains");
        if (type != null) {
            villager.setVillagerType((Villager.Type) type);
        }
    }

    private void dress(ArmorStand stand, Npc npc) {
        stand.setArms(true);
        stand.setBasePlate(false);
        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) {
            String head = npc.equipment.getOrDefault("helmet", "PLAYER_HEAD");
            equipment.setHelmet(gear(head));
            if (npc.equipment.containsKey("chestplate")) {
                equipment.setChestplate(gear(npc.equipment.get("chestplate")));
            }
            if (npc.equipment.containsKey("leggings")) {
                equipment.setLeggings(gear(npc.equipment.get("leggings")));
            }
            if (npc.equipment.containsKey("boots")) {
                equipment.setBoots(gear(npc.equipment.get("boots")));
            }
            if (npc.equipment.containsKey("hand")) {
                equipment.setItemInMainHand(gear(npc.equipment.get("hand")));
            }
        }
    }

    private void sweep(Npc npc) {
        World world = npc.location.getWorld();
        if (world == null || !world.isChunkLoaded(npc.location.getBlockX() >> 4, npc.location.getBlockZ() >> 4)) {
            return;
        }
        for (Entity nearby : world.getNearbyEntities(npc.location, 1.5, 3.0, 1.5)) {
            if ((nearby instanceof Villager || nearby instanceof ArmorStand) && !nearby.getScoreboardTags().contains(TAG)) {
                nearby.remove();
            }
        }
    }

    private void refresh(Npc npc) {
        if (npc.label == null || !npc.label.isValid()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (String line : npc.name) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(core.boards().global(line));
        }
        String value = Text.color(text.toString());
        if (!value.equals(npc.shown)) {
            npc.label.setText(value);
            npc.shown = value;
        }
    }

    public void tick() {
        ticks++;
        boolean check = ticks % 5 == 0 && System.currentTimeMillis() >= citizensReadyAt;
        for (Npc npc : npcs.values()) {
            sweep(npc);
            if (npc.external) {
                World world = npc.location.getWorld();
                if (world != null && world.isChunkLoaded(npc.location.getBlockX() >> 4, npc.location.getBlockZ() >> 4)) {
                    label(world, npc);
                    if (check || npc.citizenId < 0 && System.currentTimeMillis() >= citizensReadyAt) {
                        citizen(npc);
                    }
                }
                refresh(npc);
                continue;
            }
            if (npc.entity == null || !npc.entity.isValid()) {
                World world = npc.location.getWorld();
                if (world != null && world.isChunkLoaded(npc.location.getBlockX() >> 4, npc.location.getBlockZ() >> 4)) {
                    remove(npc);
                    spawn(npc);
                }
                continue;
            }
            if (npc.entity.getLocation().distanceSquared(npc.location) > 0.25) {
                npc.entity.teleport(npc.location);
            }
            refresh(npc);
        }
    }

    private void use(Player player, Npc npc) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < 600) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        core.lobby().handleAction(player, npc.action);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        Npc npc = byEntity.get(event.getRightClicked().getUniqueId());
        if (npc == null) {
            npc = near(event.getRightClicked());
        }
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            use(event.getPlayer(), npc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        Npc npc = byEntity.get(event.getRightClicked().getUniqueId());
        if (npc == null) {
            npc = near(event.getRightClicked());
        }
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            use(event.getPlayer(), npc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (byEntity.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (byEntity.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent event) {
        Npc npc = byEntity.get(event.getEntity().getUniqueId());
        if (npc == null) {
            npc = near(event.getEntity());
        }
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player) {
            use((Player) event.getDamager(), npc);
        }
    }

    public void forget(UUID player) {
        lastUse.remove(player);
    }
}
