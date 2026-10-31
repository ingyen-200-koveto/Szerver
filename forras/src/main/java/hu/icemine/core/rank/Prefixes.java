package hu.icemine.core.rank;

import hu.icemine.core.util.Text;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public final class Prefixes {

    private final Plugin plugin;
    private final Logger logger;
    private Object api;
    private Method adapterMethod;
    private Method metaMethod;
    private Method prefixMethod;
    private Method suffixMethod;
    private Method groupMethod;
    private boolean warned;

    public Prefixes(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public boolean hooked() {
        return connect();
    }

    private boolean connect() {
        if (api != null) {
            return true;
        }
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) {
            return false;
        }
        try {
            ClassLoader loader = luckPerms.getClass().getClassLoader();
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider", true, loader);
            Class<?> apiType = Class.forName("net.luckperms.api.LuckPerms", true, loader);
            Class<?> adapterType = Class.forName("net.luckperms.api.platform.PlayerAdapter", true, loader);
            Class<?> metaType = Class.forName("net.luckperms.api.cacheddata.CachedMetaData", true, loader);
            Object instance = provider.getMethod("get").invoke(null);
            adapterMethod = apiType.getMethod("getPlayerAdapter", Class.class);
            metaMethod = adapterType.getMethod("getMetaData", Object.class);
            prefixMethod = metaType.getMethod("getPrefix");
            suffixMethod = metaType.getMethod("getSuffix");
            groupMethod = metaType.getMethod("getPrimaryGroup");
            api = instance;
            logger.info("LuckPerms megtalálva, a rang előtagokat onnan veszem.");
            return true;
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                logger.warning("A LuckPerms fent van, de nem sikerült csatlakozni hozzá: " + error);
            }
            return false;
        }
    }

    private Object meta(Player player) {
        if (!connect()) {
            return null;
        }
        try {
            Object adapter = adapterMethod.invoke(api, Player.class);
            return metaMethod.invoke(adapter, player);
        } catch (Throwable error) {
            return null;
        }
    }

    private String read(Player player, Method method) {
        Object meta = meta(player);
        if (meta == null) {
            return "";
        }
        try {
            Object value = method.invoke(meta);
            return value == null ? "" : value.toString();
        } catch (Throwable error) {
            return "";
        }
    }

    public String prefix(Player player) {
        return read(player, prefixMethod);
    }

    public String suffix(Player player) {
        return read(player, suffixMethod);
    }

    public String group(Player player) {
        String group = read(player, groupMethod);
        return group.isEmpty() ? "-" : group;
    }

    public void apply(Player player) {
        player.setMetadata("icemine_prefix", new FixedMetadataValue(plugin, Text.color(prefix(player))));
    }

    public void clear(Player player) {
        player.removeMetadata("icemine_prefix", plugin);
    }
}
