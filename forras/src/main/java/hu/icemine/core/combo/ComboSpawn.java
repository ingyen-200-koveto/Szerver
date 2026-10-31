package hu.icemine.core.combo;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.lobby.CitizensHook;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

final class ComboSpawn {

    static final String TAG = "icemine_combo";
    private static final String CITIZEN_NAME = "icemine_combofly_kit";

    private final IceMineCore core;
    private final ComboArenaMode arena;
    private final CitizensHook citizens;
    private final long citizensReadyAt = System.currentTimeMillis() + 15000L;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private World world;
    private boolean npcEnabled;
    private Location npcLocation;
    private String kit = "combo";
    private String skin = "";
    private List<String> npcName = new ArrayList<>();
    private Location streakLocation;
    private Location topLocation;
    private ArmorStand stand;
    private TextDisplay label;
    private TextDisplay streakBoard;
    private TextDisplay topBoard;
    private int citizenId = -1;
    private BukkitTask task;

    ComboSpawn(IceMineCore core, ComboArenaMode arena) {
        this.core = core;
        this.arena = arena;
        this.citizens = new CitizensHook(core.getLogger());
    }

    private static Location point(YamlConfiguration config, String path, World world) {
        if (!config.getBoolean(path + ".enabled", true)) {
            return null;
        }
        return new Location(world, config.getDouble(path + ".x"), config.getDouble(path + ".y"), config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"), 0f);
    }

    void load(YamlConfiguration config, World target) {
        despawn();
        world = target;
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
        boolean active = config.getBoolean("custom", false) || config.isSet("kit-npc");
        npcLocation = active ? point(config, "kit-npc", world) : null;
        npcEnabled = npcLocation != null;
        kit = config.getString("kit-npc.kit", "combo");
        skin = config.getString("kit-npc.skin", "");
        npcName = new ArrayList<>(config.getStringList("kit-npc.name"));
        citizenId = config.getInt("kit-npc.citizens", -1);
        streakLocation = active ? point(config, "holograms.streaks", world) : null;
        topLocation = active ? point(config, "holograms.top", world) : null;
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(core, this::tick, 20L, 40L);
    }

    void despawn() {
        if (stand != null) {
            stand.remove();
            stand = null;
        }
        for (TextDisplay display : new TextDisplay[]{label, streakBoard, topBoard}) {
            if (display != null) {
                display.remove();
            }
        }
        label = null;
        streakBoard = null;
        topBoard = null;
    }

    private boolean loaded(Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private TextDisplay display(Location location) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
        display.setShadowed(true);
        display.setLineWidth(400);
        return display;
    }

    private static boolean alive(Entity entity) {
        return entity != null && entity.isValid();
    }

    private void tick() {
        if (world == null) {
            return;
        }
        if (npcEnabled && loaded(npcLocation)) {
            if (citizens.available()) {
                if (System.currentTimeMillis() >= citizensReadyAt) {
                    citizen();
                }
            } else if (!alive(stand)) {
                stand = statue();
            }
            if (!npcName.isEmpty()) {
                if (!alive(label)) {
                    label = display(npcLocation.clone().add(0, 2.35, 0));
                    label.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                }
                label.setText(Text.color(String.join("\n", npcName)));
            }
        }
        if (loaded(streakLocation)) {
            if (!alive(streakBoard)) {
                streakBoard = display(streakLocation);
            }
            streakBoard.setText(Text.color(streakText()));
        }
        if (loaded(topLocation)) {
            if (!alive(topBoard)) {
                topBoard = display(topLocation);
            }
            topBoard.setText(Text.color(String.join("\n", core.leaderboards().lines("combokills"))));
        }
    }

    private String streakText() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(arena.currentStreaks().entrySet());
        list.removeIf(entry -> entry.getValue() <= 0 || Bukkit.getPlayer(entry.getKey()) == null);
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder text = new StringBuilder(core.messages().raw("comboarena.streak-title"));
        if (list.isEmpty()) {
            text.append('\n').append(core.messages().raw("comboarena.streak-empty"));
        }
        for (int i = 0; i < list.size() && i < 10; i++) {
            Player player = Bukkit.getPlayer(list.get(i).getKey());
            text.append('\n').append(Text.replace(core.messages().raw("leaderboard.line"), "%place%", String.valueOf(i + 1),
                    "%player%", player.getName(), "%value%", String.valueOf(list.get(i).getValue())));
        }
        return text.toString();
    }

    private ArmorStand statue() {
        ArmorStand created = world.spawn(npcLocation, ArmorStand.class);
        created.addScoreboardTag(TAG);
        created.setPersistent(false);
        created.setGravity(false);
        created.setArms(true);
        created.setBasePlate(false);
        created.setSilent(true);
        created.setRotation(npcLocation.getYaw(), 0f);
        EntityEquipment equipment = created.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            equipment.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        }
        return created;
    }

    private void citizen() {
        if (citizenId >= 0 && citizens.ensure(citizenId, npcLocation)) {
            return;
        }
        int id = citizens.find(CITIZEN_NAME);
        if (id < 0 || !citizens.ensure(id, npcLocation)) {
            id = citizens.create(CITIZEN_NAME, npcLocation, skin, new ItemStack(Material.DIAMOND_SWORD));
        }
        if (id >= 0) {
            citizenId = id;
            YamlConfiguration config = core.configs().get(ComboArenaMode.FILE);
            config.set("kit-npc.citizens", id);
            core.configs().save(ComboArenaMode.FILE);
            if (skin != null && !skin.isBlank()) {
                citizens.skin(id, skin);
            }
        }
    }

    boolean isNpc(Entity entity) {
        if (entity == null || !npcEnabled) {
            return false;
        }
        if (stand != null && entity.getUniqueId().equals(stand.getUniqueId())) {
            return true;
        }
        return entity.hasMetadata("NPC") && entity.getWorld() == npcLocation.getWorld()
                && entity.getLocation().distanceSquared(npcLocation) < 2.25;
    }

    void use(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < 800) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        arena.selectKit(player, kit);
    }

    void forget(UUID player) {
        lastUse.remove(player);
    }
}
