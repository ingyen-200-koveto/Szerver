package hu.icemine.kingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Két kis segéd: a chunk határának megmutatása részecskékkel (foglaláskor, /k map után), és a chatből bekért szöveg
 * (rang átnevezés, összeg megadása a bankban stb.).
 */
public final class Borders implements Listener {

    private record View(int cx, int cz, long until, Color color) {
    }

    private record Prompt(Consumer<String> action, long until) {
    }

    private final IceMineKingdoms plugin;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();

    Borders(IceMineKingdoms plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, int cx, int cz) {
        Kingdom owner = plugin.manager().owner(Lands.key(cx, cz));
        Relation relation = owner == null ? Relation.NEUTRAL : plugin.manager().relation(player.getUniqueId(), owner);
        Color color = owner == null ? Color.WHITE : switch (relation) {
            case SELF -> Color.LIME;
            case NATION, ALLY -> Color.AQUA;
            case TRUCE -> Color.YELLOW;
            case ENEMY -> Color.RED;
            default -> Color.SILVER;
        };
        views.put(player.getUniqueId(), new View(cx, cz, System.currentTimeMillis() + 6000L, color));
    }

    /** Tíz tickenként rajzol. */
    void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, View>> iterator = views.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, View> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            View view = entry.getValue();
            if (player == null || now > view.until || player.getWorld() != plugin.world()) {
                iterator.remove();
                continue;
            }
            draw(player, view);
        }
        prompts.values().removeIf(prompt -> now > prompt.until);
    }

    private static void draw(Player player, View view) {
        World world = player.getWorld();
        int x0 = view.cx << 4;
        int z0 = view.cz << 4;
        double y = player.getLocation().getY() + 1;
        Particle.DustOptions dust = new Particle.DustOptions(view.color, 1.2f);
        for (int i = 0; i <= 16; i += 2) {
            for (double dy = -1; dy <= 2; dy += 1.5) {
                player.spawnParticle(Particle.DUST, x0 + i, y + dy, z0, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x0 + i, y + dy, z0 + 16, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x0, y + dy, z0 + i, 1, 0, 0, 0, 0, dust);
                player.spawnParticle(Particle.DUST, x0 + 16, y + dy, z0 + i, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** A következő chat üzenetét a játékosnak nem küldjük ki, hanem átadjuk (30 mp-ig vár, a "mégse" megszakítja). */
    public void ask(Player player, String key, Consumer<String> action, String... pairs) {
        player.closeInventory();
        prompts.put(player.getUniqueId(), new Prompt(action, System.currentTimeMillis() + 30_000L));
        plugin.send(player, key, pairs);
        plugin.send(player, "prompt-cancel-hint");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null || System.currentTimeMillis() > prompt.until) {
            return;
        }
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase("mégse") || text.equalsIgnoreCase("megse") || text.equalsIgnoreCase("cancel")) {
                plugin.send(player, "prompt-cancelled");
                return;
            }
            prompt.action.accept(text);
        });
    }

    public boolean waiting(Player player) {
        return prompts.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
        prompts.remove(event.getPlayer().getUniqueId());
    }
}
