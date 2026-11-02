package hu.icemine.core.mode;

import hu.icemine.core.IceMineCore;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ModeService implements Listener {

    public static final String LOBBY = "lobby";
    public static final String CHANNEL = "BungeeCord";
    private static final String[] INTERNAL = {"fyland", "combofly", "gens", "duels", "bedwars", "creative"};

    public enum SendResult {
        OK,
        UNKNOWN,
        CLOSED,
        SOON,
        MAINTENANCE,
        COMBAT,
        COOLDOWN,
        NOT_LOGGED_IN,
        NO_WORLD,
        SAME
    }

    private final IceMineCore core;
    private final Map<String, ModeInfo> modes = new LinkedHashMap<>();
    private final Map<String, ModeHandler> handlers = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSend = new HashMap<>();
    private long cooldown = 2000L;

    public ModeService(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        Map<String, ModeInfo.Status> previous = new HashMap<>();
        for (ModeInfo info : modes.values()) {
            previous.put(info.id, info.status);
        }
        modes.clear();
        ConfigurationSection section = config.getConfigurationSection("modes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                if (key.equals(LOBBY)) {
                    problems.add("modes.yml -> modes.lobby: a lobby nem játékmód, kihagyom");
                    continue;
                }
                modes.put(key, ModeInfo.read(config, id, isInternal(key), problems));
            }
        }
        cooldown = Math.max(0, config.getLong("send-cooldown-ms", 2000L));
    }

    public static boolean isInternal(String id) {
        for (String internal : INTERNAL) {
            if (internal.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public void register(ModeHandler handler) {
        handlers.put(handler.id(), handler);
    }

    public ModeHandler handler(String id) {
        return id == null ? null : handlers.get(id);
    }

    public ModeInfo info(String id) {
        return id == null ? null : modes.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ModeInfo> all() {
        return modes.values();
    }

    public List<String> ids() {
        return new ArrayList<>(modes.keySet());
    }

    public String modeOf(World world) {
        if (world == null) {
            return null;
        }
        for (ModeHandler handler : handlers.values()) {
            if (handler.world() == world) {
                return handler.id();
            }
        }
        for (ModeInfo info : modes.values()) {
            if (!info.internal && world.getName().equalsIgnoreCase(info.world)) {
                return info.id;
            }
        }
        return null;
    }

    public String current(Player player) {
        return modeOf(player.getWorld());
    }

    public ModeHandler currentHandler(Player player) {
        return handler(current(player));
    }

    public int online(String id) {
        int lobby = core.modeLobbies() == null ? 0 : core.modeLobbies().online(id);
        ModeHandler handler = handlers.get(id);
        if (handler != null) {
            return handler.online() + lobby;
        }
        ModeInfo info = info(id);
        if (info == null || info.usesProxy()) {
            return -1;
        }
        World world = Bukkit.getWorld(info.world);
        return (world == null ? 0 : (int) world.getPlayers().stream().filter(player -> !player.hasMetadata("NPC")).count()) + lobby;
    }

    public boolean available(ModeInfo info) {
        if (info.usesProxy()) {
            return true;
        }
        if (info.internal) {
            ModeHandler handler = handlers.get(info.id);
            return handler != null && handler.world() != null;
        }
        return Bukkit.getWorld(info.world) != null;
    }

    public void setStatus(String id, ModeInfo.Status status) {
        ModeInfo info = info(id);
        if (info == null) {
            return;
        }
        info.status = status;
        YamlConfiguration config = core.configs().get("modes");
        config.set("modes." + id + ".status", status.name().toLowerCase(Locale.ROOT));
        core.configs().save("modes");
    }

    public SendResult send(Player player, String id, boolean force) {
        if (!core.auth().isLoggedIn(player)) {
            return SendResult.NOT_LOGGED_IN;
        }
        if (!force && core.combat().tagged(player)) {
            return SendResult.COMBAT;
        }
        long now = System.currentTimeMillis();
        Long last = lastSend.get(player.getUniqueId());
        if (!force && last != null && now - last < cooldown) {
            return SendResult.COOLDOWN;
        }
        String target = id.toLowerCase(Locale.ROOT);
        Location location;
        if (target.equals(LOBBY)) {
            if (LOBBY.equals(current(player)) && !force) {
                core.parkour().stop(player, true);
                core.guard().allowNext(player);
                player.teleport(core.lobby().spawn(player));
                return SendResult.OK;
            }
            location = core.lobby().spawn(player);
        } else {
            ModeInfo info = info(target);
            if (info == null) {
                return SendResult.UNKNOWN;
            }
            if (info.status == ModeInfo.Status.CLOSED && !force) {
                return SendResult.CLOSED;
            }
            if (info.status == ModeInfo.Status.SOON && !force) {
                return SendResult.SOON;
            }
            if (info.status == ModeInfo.Status.MAINTENANCE && !force && !player.hasPermission("icemine.admin.maintenance.bypass")) {
                return SendResult.MAINTENANCE;
            }
            if (target.equals(current(player))) {
                return SendResult.SAME;
            }
            if (info.usesProxy()) {
                lastSend.put(player.getUniqueId(), now);
                core.parkour().stop(player, true);
                connect(player, info.server);
                return SendResult.OK;
            }
            hu.icemine.core.modelobby.ModeLobbies lobbies = core.modeLobbies();
            String here = current(player);
            boolean fromOwnLobby = (hu.icemine.core.modelobby.ModeLobbies.PREFIX + target).equals(here);
            ModeHandler handler = handlers.get(target);
            if (lobbies != null && lobbies.has(target) && !fromOwnLobby) {
                location = lobbies.entry(target);
            } else if (lobbies != null && fromOwnLobby && lobbies.has(target) && !lobbies.lobby(target).playCommandEmpty()) {
                lobbies.play(player, target);
                return SendResult.OK;
            } else if (handler != null) {
                location = handler.world() == null ? null : handler.spawn(player);
            } else {
                World world = Bukkit.getWorld(info.world);
                location = world == null ? null : world.getSpawnLocation().add(0.5, 0, 0.5);
            }
        }
        if (location == null || location.getWorld() == null) {
            return SendResult.NO_WORLD;
        }
        lastSend.put(player.getUniqueId(), now);
        core.parkour().stop(player, true);
        core.guard().allowNext(player);
        player.teleport(location);
        return SendResult.OK;
    }

    public boolean sendAndReport(Player player, String id) {
        SendResult result = send(player, id, false);
        String name = id.equalsIgnoreCase(LOBBY) ? core.messages().get("mode.lobby-name") : (info(id) == null ? id : info(id).name);
        switch (result) {
            case OK:
                if (!id.equalsIgnoreCase(LOBBY)) {
                    core.messages().send(player, "mode.sending", "%mode%", name);
                }
                return true;
            case COMBAT:
                core.messages().send(player, "combat.blocked-mode");
                return false;
            case COOLDOWN:
                core.messages().send(player, "mode.cooldown");
                return false;
            case MAINTENANCE:
                core.messages().send(player, "mode.maintenance", "%mode%", name);
                return false;
            case CLOSED:
                core.messages().send(player, "mode.closed", "%mode%", name);
                return false;
            case SOON:
                core.messages().send(player, "mode.soon", "%mode%", name);
                return false;
            case SAME:
                core.messages().send(player, "mode.already-there", "%mode%", name);
                return false;
            case NOT_LOGGED_IN:
                core.messages().send(player, "auth.login-first");
                return false;
            case NO_WORLD:
                core.messages().send(player, "mode.unavailable", "%mode%", name);
                return false;
            default:
                core.messages().send(player, "mode.unknown");
                return false;
        }
    }

    private void connect(Player player, String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(core, CHANNEL, bytes.toByteArray());
        } catch (IOException exception) {
            core.getLogger().warning("Nem sikerült átküldeni " + player.getName() + " játékost ide: " + server);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveWorld(PlayerChangedWorldEvent event) {
        String from = modeOf(event.getFrom());
        ModeHandler handler = handler(from);
        if (handler != null) {
            handler.leave(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnterWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        core.cleanItems(player);
        ModeHandler handler = currentHandler(player);
        if (handler != null) {
            handler.enter(player);
        }
        core.boards().rebuild(player);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ModeHandler handler = currentHandler(player);
        if (handler != null) {
            handler.leave(player, true);
        }
        lastSend.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ModeHandler handler = currentHandler(player);
            if (handler != null) {
                handler.leave(player, true);
            }
        }
        for (ModeHandler handler : handlers.values()) {
            handler.shutdown();
        }
    }
}
