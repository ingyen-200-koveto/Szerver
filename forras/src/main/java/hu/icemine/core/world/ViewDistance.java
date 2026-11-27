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

    public ViewDistance(FileConfiguration config, Logger logger) {
        this.logger = logger;
        this.enabled = config.getBoolean("view.enabled", true);
        this.send = clamp(config.getInt("view.send-distance", 32));
        this.view = config.getInt("view.view-distance", 0);
        this.simulation = config.getInt("view.simulation-distance", 0);
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
            world.setSendViewDistance(Math.max(send, world.getViewDistance()));
        } catch (RuntimeException | NoSuchMethodError exception) {
            logger.warning("Látótáv beállítása nem sikerült (" + world.getName() + "): " + exception.getMessage());
        }
    }
}
