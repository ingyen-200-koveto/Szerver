package hu.icemine.core.lobby;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CitizensHook {

    private final Logger logger;
    private ClassLoader loader;
    private Object registry;
    private Class<?> npcType;
    private boolean failed;

    public CitizensHook(Logger logger) {
        this.logger = logger;
    }

    public boolean available() {
        if (registry != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) {
            return false;
        }
        try {
            loader = citizens.getClass().getClassLoader();
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI", true, loader);
            npcType = Class.forName("net.citizensnpcs.api.npc.NPC", true, loader);
            registry = api.getMethod("getNPCRegistry").invoke(null);
            logger.info("Citizens megtalálva, a lobby NPC-i játékos NPC-k lesznek.");
            return true;
        } catch (Throwable error) {
            failed = true;
            logger.warning("A Citizens fent van, de nem sikerült csatlakozni hozzá: " + error);
            return false;
        }
    }

    private Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        return type.getMethod(name, args);
    }

    private Object byId(int id) {
        try {
            Class<?> registryType = Class.forName("net.citizensnpcs.api.npc.NPCRegistry", true, loader);
            return method(registryType, "getById", int.class).invoke(registry, id);
        } catch (Throwable error) {
            return null;
        }
    }

    public int find(String name) {
        try {
            for (Object npc : (Iterable<?>) registry) {
                if (name.equals(method(npcType, "getName").invoke(npc))) {
                    return (Integer) method(npcType, "getId").invoke(npc);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * Az IceMine által csinált ("icemine_" kezdetű) Citizens NPC-k közül törli azokat, amik már nem kellenek
     * (nincs ilyen NPC a configban), vagy egy név kétszer szerepel (ilyenkor a megjegyzett marad). Visszaadja, hányat törölt.
     */
    public int cleanup(java.util.Map<String, Integer> keep) {
        int removed = 0;
        try {
            java.util.List<Object> gone = new java.util.ArrayList<>();
            java.util.Map<String, Integer> seen = new java.util.HashMap<>();
            for (Object npc : (Iterable<?>) registry) {
                String name = String.valueOf(method(npcType, "getName").invoke(npc));
                if (!name.startsWith("icemine_")) {
                    continue;
                }
                int id = (Integer) method(npcType, "getId").invoke(npc);
                Integer wanted = keep.get(name);
                if (!keep.containsKey(name)) {
                    gone.add(npc);
                } else if (wanted != null && wanted >= 0 ? wanted != id : seen.containsKey(name)) {
                    gone.add(npc);
                } else {
                    seen.put(name, id);
                }
            }
            for (Object npc : gone) {
                method(npcType, "destroy").invoke(npc);
                removed++;
            }
        } catch (Throwable error) {
            logger.warning("Nem sikerült rendet rakni a Citizens NPC-k között: " + error);
        }
        return removed;
    }

    public int create(String name, Location location, String skin, ItemStack hand) {
        try {
            Class<?> registryType = Class.forName("net.citizensnpcs.api.npc.NPCRegistry", true, loader);
            Object npc = method(registryType, "createNPC", EntityType.class, String.class).invoke(registry, EntityType.PLAYER, name);
            hideNameplate(npc);
            if (skin != null && !skin.isBlank()) {
                skin(npc, skin);
            }
            lookClose(npc);
            if (hand != null) {
                hand(npc, hand);
            }
            method(npcType, "spawn", Location.class).invoke(npc, location);
            return (Integer) method(npcType, "getId").invoke(npc);
        } catch (Throwable error) {
            logger.warning("Nem sikerült Citizens NPC-t csinálni (" + name + "): " + error);
            return -1;
        }
    }

    public boolean ensure(int id, Location location) {
        Object npc = byId(id);
        if (npc == null) {
            return false;
        }
        try {
            boolean spawned = (Boolean) method(npcType, "isSpawned").invoke(npc);
            if (!spawned) {
                method(npcType, "spawn", Location.class).invoke(npc, location);
                return true;
            }
            Object entity = method(npcType, "getEntity").invoke(npc);
            if (entity instanceof Entity) {
                Location now = ((Entity) entity).getLocation();
                if (now.getWorld() != location.getWorld() || now.distanceSquared(location) > 0.5) {
                    method(npcType, "teleport", Location.class, PlayerTeleportEvent.TeleportCause.class)
                            .invoke(npc, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
            return true;
        } catch (Throwable error) {
            return true;
        }
    }

    private void hideNameplate(Object npc) {
        try {
            Object data = method(npcType, "data").invoke(npc);
            Class<?> store = Class.forName("net.citizensnpcs.api.npc.MetadataStore", true, loader);
            try {
                Class<?> metadata = Class.forName("net.citizensnpcs.api.npc.NPC$Metadata", true, loader);
                Object key = enumValue(metadata, "NAMEPLATE_VISIBLE");
                method(store, "setPersistent", metadata, Object.class).invoke(data, key, false);
            } catch (Throwable modern) {
                method(store, "setPersistent", String.class, Object.class).invoke(data, "nameplate-visible", false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object enumValue(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private Object trait(Object npc, String className) throws Exception {
        Class<?> traitType = Class.forName(className, true, loader);
        return method(npcType, "getOrAddTrait", Class.class).invoke(npc, traitType);
    }

    private void skin(Object npc, String skin) {
        try {
            Object trait = trait(npc, "net.citizensnpcs.trait.SkinTrait");
            method(trait.getClass(), "setSkinName", String.class).invoke(trait, skin);
        } catch (Throwable ignored) {
        }
    }

    private void lookClose(Object npc) {
        try {
            Object trait = trait(npc, "net.citizensnpcs.trait.LookClose");
            method(trait.getClass(), "lookClose", boolean.class).invoke(trait, true);
        } catch (Throwable ignored) {
        }
    }

    private void hand(Object npc, ItemStack item) {
        try {
            Object trait = trait(npc, "net.citizensnpcs.api.trait.trait.Equipment");
            Class<?> slot = Class.forName("net.citizensnpcs.api.trait.trait.Equipment$EquipmentSlot", true, loader);
            method(trait.getClass(), "set", slot, ItemStack.class).invoke(trait, enumValue(slot, "HAND"), item);
        } catch (Throwable ignored) {
        }
    }

    public void skin(int id, String skin) {
        Object npc = byId(id);
        if (npc != null && skin != null && !skin.isBlank()) {
            skin(npc, skin);
        }
    }
}
