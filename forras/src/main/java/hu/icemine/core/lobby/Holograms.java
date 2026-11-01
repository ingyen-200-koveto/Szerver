package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.util.Locs;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

public final class Holograms implements org.bukkit.event.Listener {

    public static final String TAG = "icemine_holo";

    public static final class Hologram {
        public final String id;
        public final Location location;
        public final String type;
        public final List<String> lines;
        public final String target;
        public final int background;
        public final String action;
        public TextDisplay entity;
        public org.bukkit.entity.Interaction hitbox;
        public String shown = "";
        public int shownLines;

        Hologram(String id, Location location, String type, List<String> lines, String target, int background, String action) {
            this.id = id;
            this.location = location;
            this.type = type;
            this.lines = lines;
            this.target = target;
            this.background = background;
            this.action = action == null ? "" : action.trim();
        }
    }

    private final IceMineCore core;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();
    private final Set<Long> forced = new HashSet<>();

    public Holograms(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        despawn();
        holograms.clear();
        World world = core.lobby().world();
        ConfigurationSection section = config.getConfigurationSection("holograms");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Location location = Locs.read(section.getConfigurationSection(id + ".location"), world);
            if (location == null) {
                problems.add("lobby.yml -> holograms." + id + ": hiányzik a hely (location)");
                continue;
            }
            String type = section.getString(id + ".type", "text").toLowerCase(Locale.ROOT);
            if (!type.equals("text") && !type.equals("leaderboard") && !type.equals("mode")) {
                problems.add("lobby.yml -> holograms." + id + ".type: text, leaderboard vagy mode lehet");
                type = "text";
            }
            String target = section.getString(id + ".target", "");
            int background = parseColor(section.getString(id + ".background", "60102030"));
            // ha egy régi lobby építésnél még nem került be a kattintás, az alapértelmezettet kapja (pl. "Kattints rám!" feliratúak)
            String action = section.getString(id + ".action", "");
            if (action.isBlank() && type.equals("text")) {
                action = LobbyDefaults.action(id);
            }
            holograms.put(id, new Hologram(id, location, type, new ArrayList<>(section.getStringList(id + ".lines")), target, background, action));
        }
    }

    private static int parseColor(String hex) {
        try {
            return (int) Long.parseLong(hex.replace("#", ""), 16);
        } catch (NumberFormatException exception) {
            return 0x60102030;
        }
    }

    public Map<String, Hologram> all() {
        return holograms;
    }

    public void move(String id, Location location) {
        YamlConfiguration config = core.configs().get("lobby");
        Locs.write(config, "holograms." + id + ".location", location);
        core.configs().save("lobby");
        Hologram hologram = holograms.get(id);
        if (hologram != null) {
            if (hologram.entity != null) {
                hologram.entity.remove();
            }
            if (hologram.hitbox != null) {
                hologram.hitbox.remove();
            }
            Hologram moved = new Hologram(id, location.clone(), hologram.type, hologram.lines, hologram.target, hologram.background, hologram.action);
            holograms.put(id, moved);
            spawn(moved);
        }
    }

    public void despawn() {
        for (Hologram hologram : holograms.values()) {
            if (hologram.entity != null && hologram.entity.isValid()) {
                hologram.entity.remove();
            }
            if (hologram.hitbox != null && hologram.hitbox.isValid()) {
                hologram.hitbox.remove();
            }
            hologram.entity = null;
            hologram.hitbox = null;
        }
        World world = core.lobby() == null ? null : core.lobby().world();
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
            for (Long key : forced) {
                world.setChunkForceLoaded((int) (key >> 32), (int) (long) key, false);
            }
        }
        forced.clear();
    }

    public void spawnAll() {
        for (Hologram hologram : holograms.values()) {
            spawn(hologram);
        }
    }

    private void keepLoaded(Location location) {
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        long key = ((long) x << 32) | (z & 0xffffffffL);
        if (forced.add(key)) {
            location.getWorld().setChunkForceLoaded(x, z, true);
        }
    }

    private void spawn(Hologram hologram) {
        keepLoaded(hologram.location);
        TextDisplay display = hologram.location.getWorld().spawn(hologram.location, TextDisplay.class);
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(hologram.background >>> 24 & 0xff, hologram.background >> 16 & 0xff, hologram.background >> 8 & 0xff, hologram.background & 0xff));
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setLineWidth(260);
        display.setViewRange(1.5f);
        hologram.entity = display;
        hologram.shown = "";
        update(hologram);
        if (!hologram.action.isEmpty()) {
            spawnHitbox(hologram);
        }
    }

    private void spawnHitbox(Hologram hologram) {
        if (hologram.hitbox != null && hologram.hitbox.isValid()) {
            hologram.hitbox.remove();
        }
        int lines = Math.max(1, hologram.shownLines);
        float height = (float) (lines * 0.27 + 0.15);
        org.bukkit.entity.Interaction box = hologram.location.getWorld().spawn(hologram.location.clone().add(0, -0.05, 0), org.bukkit.entity.Interaction.class);
        box.setPersistent(false);
        box.addScoreboardTag(TAG);
        box.addScoreboardTag(TAG + ":" + hologram.id);
        box.setInteractionWidth(2.2f);
        box.setInteractionHeight(height);
        box.setResponsive(true);
        hologram.hitbox = box;
    }

    private Hologram owner(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof org.bukkit.entity.Interaction) || !entity.getScoreboardTags().contains(TAG)) {
            return null;
        }
        for (Hologram hologram : holograms.values()) {
            if (hologram.hitbox != null && hologram.hitbox.getUniqueId().equals(entity.getUniqueId())) {
                return hologram;
            }
        }
        return null;
    }

    private final java.util.Map<java.util.UUID, Long> clicked = new java.util.HashMap<>();

    private void click(org.bukkit.entity.Player player, Hologram hologram) {
        long now = System.currentTimeMillis();
        Long last = clicked.get(player.getUniqueId());
        if (last != null && now - last < 700) {
            return;
        }
        clicked.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), "minecraft:ui.button.click", 0.6f, 1.4f);
        core.lobby().handleAction(player, hologram.action);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onRightClick(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Hologram hologram = owner(event.getRightClicked());
        if (hologram == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            click(event.getPlayer(), hologram);
        }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onLeftClick(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Hologram hologram = owner(event.getEntity());
        if (hologram == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof org.bukkit.entity.Player) {
            click((org.bukkit.entity.Player) event.getDamager(), hologram);
        }
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        clicked.remove(event.getPlayer().getUniqueId());
    }

    private List<String> content(Hologram hologram) {
        switch (hologram.type) {
            case "leaderboard":
                return core.leaderboards().lines(hologram.target);
            case "mode": {
                ModeInfo info = core.modes().info(hologram.target);
                List<String> lines = new ArrayList<>();
                if (info == null) {
                    return hologram.lines;
                }
                for (String line : hologram.lines) {
                    lines.add(line.replace("%mode_name%", info.name));
                }
                if (lines.isEmpty()) {
                    lines.add(info.name);
                    lines.addAll(info.description);
                    lines.add("%status_" + info.id + "%");
                }
                return lines;
            }
            default:
                return hologram.lines;
        }
    }

    private void update(Hologram hologram) {
        if (hologram.entity == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (String line : content(hologram)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(core.boards().global(line));
        }
        String value = Text.color(text.toString());
        if (!value.equals(hologram.shown)) {
            hologram.entity.setText(value);
            hologram.shown = value;
            int lines = value.split("\n", -1).length;
            if (lines != hologram.shownLines) {
                hologram.shownLines = lines;
                if (hologram.hitbox != null && hologram.hitbox.isValid()) {
                    hologram.hitbox.setInteractionHeight((float) (lines * 0.27 + 0.15));
                }
            }
        }
    }

    public void tick() {
        for (Hologram hologram : holograms.values()) {
            if (hologram.entity == null || !hologram.entity.isValid()) {
                World world = hologram.location.getWorld();
                if (world != null && world.isChunkLoaded(hologram.location.getBlockX() >> 4, hologram.location.getBlockZ() >> 4)) {
                    if (hologram.hitbox != null) {
                        hologram.hitbox.remove();
                        hologram.hitbox = null;
                    }
                    spawn(hologram);
                }
                continue;
            }
            if (!hologram.action.isEmpty() && (hologram.hitbox == null || !hologram.hitbox.isValid())) {
                spawnHitbox(hologram);
            }
            update(hologram);
        }
    }
}
