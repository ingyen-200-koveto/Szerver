package hu.icemine.core.command;

import hu.icemine.core.IceMineCore;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

public final class CommandGuard implements Listener {

    public static final String BYPASS = "icemine.admin.commands";

    private final IceMineCore core;

    public CommandGuard(IceMineCore core) {
        this.core = core;
    }

    private Set<String> blocked() {
        Set<String> result = new HashSet<>();
        for (String entry : core.configs().get("config").getStringList("blocked-commands")) {
            String name = normalize(entry);
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
        return result;
    }

    static String normalize(String command) {
        String name = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        int space = name.indexOf(' ');
        if (space >= 0) {
            name = name.substring(0, space);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) {
            return;
        }
        String name = normalize(event.getMessage());
        if (name.equals("help") || name.equals("?")) {
            event.setCancelled(true);
            if (!core.lobby().info().send(player, "commands", core.boards()::global)) {
                core.messages().send(player, "general.no-permission");
            }
            return;
        }
        if (blocked().contains(name)) {
            event.setCancelled(true);
            core.messages().send(player, "general.no-permission");
            return;
        }
        if (core.auth() != null && !core.auth().isLoggedIn(player)) {
            // a be nem jelentkezett játékos parancsait az AuthMe kezeli (login, register), azokat nem fogjuk meg
            return;
        }
        String label = label(event.getMessage());
        Command command = label.isEmpty() ? null : Bukkit.getServer().getCommandMap().getCommand(label);
        if (command == null) {
            event.setCancelled(true);
            core.messages().send(player, "general.unknown-command");
            return;
        }
        if (!permitted(player, command)) {
            event.setCancelled(true);
            core.messages().send(player, "general.no-permission");
        }
    }

    /**
     * Van-e joga a parancshoz. A legtöbb pluginnál ezt a parancs saját joga mondja meg. Az Essentials viszont nem írja rá a
     * parancsaira a jogot, hanem futás közben ellenőriz, és a saját üzenetét küldi; ezért ott az essentials.&lt;parancs&gt; jogot
     * mi nézzük meg előre, így a játékos a mi üzenetünket kapja.
     */
    static boolean permitted(Player player, Command command) {
        if (!command.testPermissionSilent(player)) {
            return false;
        }
        if (command instanceof PluginCommand pluginCommand && pluginCommand.getPermission() == null) {
            String owner = pluginCommand.getPlugin().getName();
            if (owner.startsWith("Essentials")) {
                return player.hasPermission("essentials." + pluginCommand.getName().toLowerCase(Locale.ROOT));
            }
        }
        return true;
    }

    static String label(String message) {
        String text = message == null ? "" : message.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        return (space >= 0 ? text.substring(0, space) : text).toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandList(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission(BYPASS)) {
            return;
        }
        Set<String> blocked = blocked();
        Player player = event.getPlayer();
        event.getCommands().removeIf(command -> {
            if (command.indexOf(':') >= 0 || blocked.contains(normalize(command)) || command.equals("?")) {
                return true;
            }
            Command known = Bukkit.getServer().getCommandMap().getCommand(command);
            return known != null && !permitted(player, known);
        });
    }
}
