package hu.icemine.core.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Konzol jogú parancsküldő, aminek a válaszai nem kerülnek a konzolra (pl. a LuckPerms beállító parancsoknál). */
public final class SilentSender {

    private static CommandSender sender;

    private SilentSender() {
    }

    public static synchronized CommandSender get() {
        if (sender == null) {
            try {
                sender = Bukkit.createCommandSender(message -> {
                });
            } catch (Throwable error) {
                sender = Bukkit.getConsoleSender();
            }
        }
        return sender;
    }
}
