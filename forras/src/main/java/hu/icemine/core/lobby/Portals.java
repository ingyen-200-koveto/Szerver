package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Locs;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class Portals implements Listener {

    public static final class Portal {
        public final String id;
        public final String mode;
        public final Locs.Box area;
        public final boolean walk;
        public final boolean click;

        Portal(String id, String mode, Locs.Box area, boolean walk, boolean click) {
            this.id = id;
            this.mode = mode;
            this.area = area;
            this.walk = walk;
            this.click = click;
        }
    }

    private final IceMineCore core;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<UUID, String> inside = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public Portals(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        portals.clear();
        ConfigurationSection section = config.getConfigurationSection("portals");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Locs.Box box = Locs.Box.read(section.getConfigurationSection(id + ".area"));
            String mode = section.getString(id + ".mode", id).toLowerCase(Locale.ROOT);
            if (box == null) {
                problems.add("lobby.yml -> portals." + id + ": hiányzik a terület (area.min / area.max)");
                continue;
            }
            if (core.modes().info(mode) == null) {
                problems.add("lobby.yml -> portals." + id + ": ismeretlen játékmód (" + mode + ")");
                continue;
            }
            String trigger = section.getString(id + ".trigger", "walk").toLowerCase(Locale.ROOT);
            boolean walk = trigger.equals("walk") || trigger.equals("both");
            boolean click = trigger.equals("click") || trigger.equals("both");
            if (!walk && !click) {
                problems.add("lobby.yml -> portals." + id + ".trigger: walk, click vagy both lehet");
                walk = true;
            }
            portals.put(id, new Portal(id, mode, box, walk, click));
        }
    }

    public Map<String, Portal> all() {
        return portals;
    }

    public void set(String mode, Location center, int radius, int height) {
        YamlConfiguration config = core.configs().get("lobby");
        Locs.Box box = new Locs.Box(center.getBlockX() - radius, center.getBlockY(), center.getBlockZ() - radius,
                center.getBlockX() + radius, center.getBlockY() + height - 1, center.getBlockZ() + radius);
        box.write(config, "portals." + mode + ".area");
        config.set("portals." + mode + ".mode", mode);
        if (!config.contains("portals." + mode + ".trigger")) {
            config.set("portals." + mode + ".trigger", "walk");
        }
        core.configs().save("lobby");
        portals.put(mode, new Portal(mode, mode, box, true, "click".equals(config.getString("portals." + mode + ".trigger")) || "both".equals(config.getString("portals." + mode + ".trigger"))));
    }

    private Portal at(Location location) {
        for (Portal portal : portals.values()) {
            if (portal.area.contains(location)) {
                return portal;
            }
        }
        return null;
    }

    public void check(Player player, Location to) {
        Portal portal = at(to);
        UUID id = player.getUniqueId();
        if (portal == null) {
            inside.remove(id);
            return;
        }
        if (portal.id.equals(inside.get(id))) {
            return;
        }
        inside.put(id, portal.id);
        if (portal.walk) {
            use(player, portal);
        }
    }

    private void use(Player player, Portal portal) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(player.getUniqueId());
        if (last != null && now - last < 3000) {
            return;
        }
        cooldown.put(player.getUniqueId(), now);
        if (core.parkour().running(player)) {
            core.messages().send(player, "parkour.portal-blocked");
            bounce(player, portal);
            return;
        }
        if (!core.modes().sendAndReport(player, portal.mode)) {
            bounce(player, portal);
        }
    }

    private void bounce(Player player, Portal portal) {
        Location location = player.getLocation();
        double dx = location.getX() - portal.area.centerX();
        double dz = location.getZ() - portal.area.centerZ();
        double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        player.setVelocity(new org.bukkit.util.Vector(dx / length * 0.8, 0.35, dz / length * 0.8));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || block.getWorld() != core.lobby().world()) {
            return;
        }
        Portal portal = at(block.getLocation().add(0.5, 0.5, 0.5));
        if (portal != null && portal.click) {
            event.setCancelled(true);
            use(player, portal);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        inside.remove(event.getPlayer().getUniqueId());
        cooldown.remove(event.getPlayer().getUniqueId());
    }
}
