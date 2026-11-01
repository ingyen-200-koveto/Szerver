package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class Cosmetics implements Listener {

    public static final String TRAIL = "cosmetic.trail";
    public static final String KILL = "cosmetic.kill";

    public static class Effect {
        public final String id;
        public final String name;
        public final Material icon;
        public final Particle particle;
        public final Object data;
        public final String permission;
        public final List<String> description;
        public final int count;

        Effect(String id, String name, Material icon, Particle particle, Object data, String permission, List<String> description, int count) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.particle = particle;
            this.data = data;
            this.permission = permission;
            this.description = description;
            this.count = count;
        }
    }

    public static final class Trail extends Effect {
        Trail(String id, String name, Material icon, Particle particle, Object data, String permission, List<String> description, int count) {
            super(id, name, icon, particle, data, permission, description, count);
        }
    }

    public static final class KillEffect extends Effect {
        public final String sound;
        public final boolean lightning;

        KillEffect(String id, String name, Material icon, Particle particle, Object data, String permission, List<String> description, int count,
                   String sound, boolean lightning) {
            super(id, name, icon, particle, data, permission, description, count);
            this.sound = sound;
            this.lightning = lightning;
        }
    }

    private final IceMineCore core;
    private final Map<String, Trail> trails = new LinkedHashMap<>();
    private final Map<String, KillEffect> kills = new LinkedHashMap<>();
    private final Map<UUID, Trail> active = new HashMap<>();
    private final Set<UUID> known = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();
    /** A kozmetikák (nyomvonal, ölés effekt) csak a lobbyban és a játékmódok lobbyjaiban látszanak, játék közben nem. */
    private boolean lobbyOnly = true;

    public Cosmetics(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        trails.clear();
        kills.clear();
        disabledWorlds.clear();
        lobbyOnly = config.getBoolean("cosmetics.lobby-only", true);
        for (String world : config.getStringList("cosmetics.disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        ConfigurationSection section = config.getConfigurationSection("cosmetics.trails");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                ConfigurationSection entry = section.getConfigurationSection(id);
                Particle particle = particle(entry, "lobby.yml -> cosmetics.trails." + id, problems);
                if (particle == null) {
                    continue;
                }
                trails.put(key, new Trail(key, entry.getString("name", id),
                        Items.material(entry.getString("icon", "SNOWBALL"), Material.SNOWBALL), particle, data(particle, entry),
                        entry.getString("permission", "icemine.cosmetic.trail." + key), new ArrayList<>(entry.getStringList("description")),
                        Math.max(1, Math.min(10, entry.getInt("count", 3)))));
            }
        }
        ConfigurationSection killSection = config.getConfigurationSection("cosmetics.kill-effects");
        if (killSection != null) {
            for (String id : killSection.getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                ConfigurationSection entry = killSection.getConfigurationSection(id);
                boolean lightning = entry.getBoolean("lightning", false);
                Particle particle = entry.isSet("particle") ? particle(entry, "lobby.yml -> cosmetics.kill-effects." + id, problems) : null;
                if (particle == null && !lightning) {
                    continue;
                }
                kills.put(key, new KillEffect(key, entry.getString("name", id),
                        Items.material(entry.getString("icon", "BONE"), Material.BONE), particle, particle == null ? null : data(particle, entry),
                        entry.getString("permission", "icemine.cosmetic.kill." + key), new ArrayList<>(entry.getStringList("description")),
                        Math.max(1, Math.min(200, entry.getInt("count", 30))), entry.getString("sound", ""), lightning));
            }
        }
        active.clear();
        known.clear();
    }

    private static Particle particle(ConfigurationSection entry, String where, List<String> problems) {
        String name = entry.getString("particle", "SNOWFLAKE").toUpperCase(Locale.ROOT);
        Particle particle;
        try {
            particle = Particle.valueOf(name);
        } catch (IllegalArgumentException exception) {
            problems.add(where + ".particle: ismeretlen részecske (" + name + "), kihagyom");
            return null;
        }
        Class<?> type = particle.getDataType();
        if (type != Void.class && type != BlockData.class && type != Particle.DustOptions.class) {
            problems.add(where + ".particle: ehhez a részecskéhez olyan extra adat kell, amit nem tudok megadni, válassz másikat");
            return null;
        }
        return particle;
    }

    private static Object data(Particle particle, ConfigurationSection entry) {
        Class<?> type = particle.getDataType();
        if (type == BlockData.class) {
            Material block = Items.material(entry.getString("block", "ICE"), Material.ICE);
            return (block.isBlock() ? block : Material.ICE).createBlockData();
        }
        if (type == Particle.DustOptions.class) {
            String hex = entry.getString("color", "#7FD0FF").replace("#", "");
            int rgb;
            try {
                rgb = Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                rgb = 0x7FD0FF;
            }
            return new Particle.DustOptions(Color.fromRGB(rgb & 0xFFFFFF), 1.2f);
        }
        return null;
    }

    public Map<String, Trail> trails() {
        return trails;
    }

    public Map<String, KillEffect> killEffects() {
        return kills;
    }

    public Trail current(Player player) {
        return active.get(player.getUniqueId());
    }

    public KillEffect currentKill(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return null;
        }
        KillEffect effect = kills.get(profile.setting(KILL, ""));
        return effect == null || !player.hasPermission(effect.permission) ? null : effect;
    }

    public boolean select(Player player, String id) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return false;
        }
        if (id == null) {
            profile.put(TRAIL, null);
            active.remove(player.getUniqueId());
            return true;
        }
        Trail trail = trails.get(id);
        if (trail == null || !player.hasPermission(trail.permission)) {
            return false;
        }
        profile.put(TRAIL, trail.id);
        refresh(player);
        return true;
    }

    public boolean selectKill(Player player, String id) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return false;
        }
        if (id == null) {
            profile.put(KILL, null);
            return true;
        }
        KillEffect effect = kills.get(id);
        if (effect == null || !player.hasPermission(effect.permission)) {
            return false;
        }
        profile.put(KILL, effect.id);
        return true;
    }

    public void refresh(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        known.add(player.getUniqueId());
        Trail trail = trails.get(profile.setting(TRAIL, ""));
        if (trail == null || !player.hasPermission(trail.permission)) {
            active.remove(player.getUniqueId());
            return;
        }
        active.put(player.getUniqueId(), trail);
    }

    public void stop(Player player) {
        active.remove(player.getUniqueId());
        known.remove(player.getUniqueId());
    }

    private boolean hidden(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.isInvisible() || player.hasMetadata("NPC") || player.hasMetadata("vanished")) {
            return true;
        }
        if (disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (lobbyOnly && !lobbyWorld(player.getWorld())) {
            return true;
        }
        return core.parkour().running(player);
    }

    private boolean lobbyWorld(World world) {
        if (world == null) {
            return false;
        }
        if (core.lobby() != null && world == core.lobby().world()) {
            return true;
        }
        return core.modeLobbies() != null && core.modeLobbies().isLobbyWorld(world);
    }

    private static void spawn(World world, Effect effect, double x, double y, double z, int count, double spread, double speed) {
        if (effect.data != null) {
            world.spawnParticle(effect.particle, x, y, z, count, spread, spread * 0.4, spread, speed, effect.data);
        } else {
            world.spawnParticle(effect.particle, x, y, z, count, spread, spread * 0.4, spread, speed);
        }
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!known.contains(player.getUniqueId())) {
                refresh(player);
            }
        }
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Trail> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || hidden(player)) {
                continue;
            }
            Location at = player.getLocation();
            Trail trail = entry.getValue();
            spawn(at.getWorld(), trail, at.getX(), at.getY() + 0.15, at.getZ(), trail.count, 0.25, 0.0);
        }
    }

    public void playKill(Player killer, Location at) {
        KillEffect effect = currentKill(killer);
        if (effect == null || at.getWorld() == null || lobbyOnly && !lobbyWorld(at.getWorld())) {
            return;
        }
        World world = at.getWorld();
        if (effect.lightning) {
            world.strikeLightningEffect(at);
        }
        if (effect.particle != null) {
            spawn(world, effect, at.getX(), at.getY() + 1.0, at.getZ(), effect.count, 0.45, 0.06);
        }
        if (effect.sound != null && !effect.sound.isBlank()) {
            world.playSound(at, effect.sound, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || killer.hasMetadata("NPC")) {
            return;
        }
        playKill(killer, victim.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }
}
