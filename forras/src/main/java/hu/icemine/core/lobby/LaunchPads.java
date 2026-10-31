package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Locs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class LaunchPads {

    private static final class Pad {
        final String id;
        final Location target;

        Pad(String id, Location target) {
            this.id = id;
            this.target = target;
        }
    }

    private final IceMineCore core;
    private final Map<Long, Pad> pads = new HashMap<>();
    private final Map<UUID, Long> used = new HashMap<>();

    public LaunchPads(IceMineCore core) {
        this.core = core;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public void load(YamlConfiguration config, World world, List<String> problems) {
        pads.clear();
        ConfigurationSection section = config.getConfigurationSection("launchpads");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Location location = Locs.read(section.getConfigurationSection(id + ".location"), world);
            Location target = Locs.read(section.getConfigurationSection(id + ".target"), world);
            if (location == null || target == null) {
                problems.add("lobby.yml -> launchpads." + id + ": kell location és target is");
                continue;
            }
            pads.put(key(location.getBlockX(), location.getBlockY(), location.getBlockZ()), new Pad(id, target));
        }
    }

    public int size() {
        return pads.size();
    }

    public boolean check(Player player, Location to) {
        if (pads.isEmpty()) {
            return false;
        }
        Pad pad = pads.get(key(to.getBlockX(), to.getBlockY(), to.getBlockZ()));
        if (pad == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = used.get(player.getUniqueId());
        if (last != null && now - last < 1200) {
            return true;
        }
        used.put(player.getUniqueId(), now);
        if (core.parkour().running(player)) {
            core.parkour().stop(player, true);
        }
        double[] velocity = LaunchMath.velocity(pad.target.getX() - to.getX(), pad.target.getY() - to.getY(), pad.target.getZ() - to.getZ());
        player.setFallDistance(0f);
        player.setVelocity(new Vector(velocity[0], velocity[1], velocity[2]));
        player.playSound(to, "minecraft:entity.firework_rocket.launch", 0.9f, 1.1f);
        to.getWorld().spawnParticle(Particle.CLOUD, to.getX(), to.getY() + 0.2, to.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
        return true;
    }

    public void forget(UUID player) {
        used.remove(player);
    }
}
