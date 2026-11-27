package hu.icemine.core.world;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Látótáv: a szerver annyi chunkot küld, amennyit a játékos a saját beállításában kér (legfeljebb send-distance).
 * A chunkok csak a view-distance-en belül "élnek" (mobok, redstone), azon túl csak látszanak, így a szerver terhelése
 * alig nő. A Paper ezt világonként tudja: World#setSendViewDistance.
 */
public final class ViewDistance implements Listener {

    private final Logger logger;
    private final boolean enabled;
    private final int send;
    private final int view;
    private final int simulation;
    private final java.util.Map<String, Integer> perWorld = new java.util.HashMap<>();

    public ViewDistance(FileConfiguration config, Logger logger) {
        this.logger = logger;
        this.enabled = config.getBoolean("view.enabled", true);
        this.send = clamp(config.getInt("view.send-distance", 32));
        this.view = config.getInt("view.view-distance", 0);
        this.simulation = config.getInt("view.simulation-distance", 0);
        org.bukkit.configuration.ConfigurationSection worlds = config.getConfigurationSection("view.worlds");
        if (worlds != null) {
            for (String name : worlds.getKeys(false)) {
                perWorld.put(name.toLowerCase(java.util.Locale.ROOT), clamp(worlds.getInt(name)));
            }
        }
    }

    static int clamp(int value) {
        return Math.max(2, Math.min(32, value));
    }

    public void applyAll() {
        if (!enabled) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
        logger.info("Látótáv: a játékosok annyit látnak, amennyit beállítanak (max " + send + " chunk).");
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    private void later(org.bukkit.entity.Player player) {
        if (!enabled) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("IceMine"), () -> {
            if (!player.isOnline()) {
                return;
            }
            apply(player);
        }, 5L);
    }

    /** A játékos annyit kap, amennyit a kliense kér, legfeljebb a határig (a szerver nem tölt be fölöslegesen többet). */
    private void apply(org.bukkit.entity.Player player) {
        int limit = perWorld.getOrDefault(player.getWorld().getName().toLowerCase(java.util.Locale.ROOT), send);
        int wanted = clamp(Math.min(limit, Math.max(2, player.getClientViewDistance())));
        try {
            if (player.getViewDistance() != wanted) {
                player.setViewDistance(wanted);
            }
            if (player.getSendViewDistance() != wanted) {
                player.setSendViewDistance(wanted);
            }
        } catch (RuntimeException | NoSuchMethodError ignored) {
            // régebbi Paper: marad a világ beállítása
        }
    }

    @EventHandler
    public void onOptions(com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent event) {
        if (enabled && event.hasViewDistanceChanged()) {
            later(event.getPlayer());
        }
    }

    @EventHandler
    public void onLoad(WorldLoadEvent event) {
        if (enabled) {
            apply(event.getWorld());
        }
    }

    private void apply(World world) {
        try {
            if (view > 0) {
                world.setViewDistance(clamp(view));
            }
            if (simulation > 0) {
                world.setSimulationDistance(clamp(simulation));
            }
            int limit = perWorld.getOrDefault(world.getName().toLowerCase(java.util.Locale.ROOT), send);
            world.setSendViewDistance(Math.max(limit, world.getViewDistance()));
        } catch (RuntimeException | NoSuchMethodError exception) {
            logger.warning("Látótáv beállítása nem sikerült (" + world.getName() + "): " + exception.getMessage());
        }
    }
}
