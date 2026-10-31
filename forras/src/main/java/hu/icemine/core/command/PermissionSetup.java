package hu.icemine.core.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Központi jogkezelés (permissions.yml): minden IceMine parancsnak és /icem alparancsnak itt van a joga és az, hogy alapból
 * ki kapja meg. Betöltéskor a parancsokra rátesszük a jogot, így a CommandGuard már a parancs lefutása előtt kiszűri, akinek
 * nincs joga, és a tab listában sem látja.
 */
public final class PermissionSetup {

    public static final class Entry {
        public final String permission;
        public final PermissionDefault fallback;

        Entry(String permission, PermissionDefault fallback) {
            this.permission = permission;
            this.fallback = fallback;
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private final Map<String, Entry> subcommands = new LinkedHashMap<>();

    public PermissionSetup(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        commands.clear();
        subcommands.clear();
        read(config.getConfigurationSection("commands"), commands, "icemine.command.", "permissions.yml -> commands", problems);
        read(config.getConfigurationSection("icem"), subcommands, "icemine.command.", "permissions.yml -> icem", problems);
        PluginManager manager = plugin.getServer().getPluginManager();
        for (Map.Entry<String, Entry> entry : commands.entrySet()) {
            PluginCommand command = plugin.getCommand(entry.getKey());
            if (command == null) {
                problems.add("permissions.yml -> commands." + entry.getKey() + ": nincs ilyen IceMine parancs");
                continue;
            }
            command.setPermission(entry.getValue().permission);
            register(manager, entry.getValue());
        }
        for (Entry entry : subcommands.values()) {
            register(manager, entry);
        }
    }

    private static void read(ConfigurationSection section, Map<String, Entry> target, String base, String where, List<String> problems) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String id = key.toLowerCase(Locale.ROOT);
            String permission;
            String fallback;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                permission = entry.getString("permission", base + id);
                fallback = entry.getString("default", "true");
            } else {
                permission = base + id;
                fallback = section.getString(key, "true");
            }
            PermissionDefault value = parseDefault(fallback);
            if (value == null) {
                problems.add(where + "." + key + ".default: true, op vagy false lehet (" + fallback + ")");
                value = PermissionDefault.OP;
            }
            if (permission == null || permission.isBlank() || permission.contains(" ")) {
                problems.add(where + "." + key + ".permission: érvénytelen jog (" + permission + ")");
                permission = base + id;
            }
            target.put(id, new Entry(permission.trim(), value));
        }
    }

    static PermissionDefault parseDefault(String text) {
        if (text == null) {
            return null;
        }
        switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true":
            case "mindenki":
                return PermissionDefault.TRUE;
            case "op":
                return PermissionDefault.OP;
            case "false":
            case "senki":
                return PermissionDefault.FALSE;
            case "notop":
            case "not_op":
                return PermissionDefault.NOT_OP;
            default:
                return null;
        }
    }

    private static void register(PluginManager manager, Entry entry) {
        Permission existing = manager.getPermission(entry.permission);
        if (existing == null) {
            manager.addPermission(new Permission(entry.permission, entry.fallback));
        } else if (existing.getDefault() != entry.fallback) {
            existing.setDefault(entry.fallback);
        }
    }

    /** Az /icem alparancs joga, vagy egy alapértelmezett, ha a permissions.yml-ből hiányzik. */
    public String sub(String id) {
        Entry entry = subcommands.get(id.toLowerCase(Locale.ROOT));
        return entry == null ? "icemine.command." + id.toLowerCase(Locale.ROOT) : entry.permission;
    }

    public Map<String, Entry> commands() {
        return commands;
    }

    public Map<String, Entry> subcommands() {
        return subcommands;
    }
}
