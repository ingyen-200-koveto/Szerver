package hu.icemine.core.mode;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface ModeHandler {

    String id();

    World world();

    Location spawn(Player player);

    void enter(Player player);

    void leave(Player player, boolean quitting);

    void placeholders(Player player, Map<String, String> values);

    default int online() {
        World world = world();
        return world == null ? 0 : (int) world.getPlayers().stream().filter(player -> !player.hasMetadata("NPC")).count();
    }

    default void shutdown() {
    }
}
