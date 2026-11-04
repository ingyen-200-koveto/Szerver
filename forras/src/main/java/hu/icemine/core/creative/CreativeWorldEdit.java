package hu.icemine.core.creative;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

/**
 * WorldEdit a Creative telkeken: aki már legalább 2 órát játszott a szerveren, megkapja a biztonságos WorldEdit
 * parancsokat (kijelölés, set, replace, falak, másolás, beillesztés, forgatás, visszavonás, kisebb gömbök...).
 * Csak a saját (vagy oda felvett) telkén hat, a bedrockot és az alatta lévőt nem változtatja, a tiltott blokkokat
 * (TNT, parancsblokk...) nem rakja le, entitást nem illeszt be, és egyszerre csak korlátozott számú blokkot cserél.
 * Ez az osztály csak akkor töltődik be, ha a WorldEdit fent van.
 */
final class CreativeWorldEdit {

    private final CreativeMode mode;
    private final Map<UUID, PermissionAttachment> granted = new HashMap<>();
    private final Map<String, Material> types = new HashMap<>();

    CreativeWorldEdit(CreativeMode mode) {
        this.mode = mode;
        WorldEdit.getInstance().getEventBus().register(this);
    }

    void unregister() {
        try {
            WorldEdit.getInstance().getEventBus().unregister(this);
        } catch (RuntimeException ignored) {
            // már nincs regisztrálva
        }
        for (UUID id : granted.keySet().toArray(new UUID[0])) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                revoke(player);
            }
        }
    }

    // ---- jogok ----

    /** Megadja vagy elveszi a WorldEdit jogokat a játékos helyzete és játékideje szerint. */
    void update(Player player, boolean eligible, List<String> permissions, int limit) {
        if (!eligible) {
            revoke(player);
            return;
        }
        if (granted.containsKey(player.getUniqueId())) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(mode.core());
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }
        granted.put(player.getUniqueId(), attachment);
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
            session.setBlockChangeLimit(limit);
        } catch (RuntimeException ignored) {
            // ha a session nem érhető el, a WorldEdit saját korlátja marad
        }
        player.updateCommands();
    }

    void revoke(Player player) {
        PermissionAttachment attachment = granted.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // már levették
            }
            player.updateCommands();
        }
    }

    boolean has(Player player) {
        return granted.containsKey(player.getUniqueId());
    }

    // ---- a szerkesztés korlátozása ----

    private Material material(BlockStateHolder<?> block) {
        String id = block.getBlockType().id();
        return types.computeIfAbsent(id, key -> {
            Material material = Material.matchMaterial(key);
            return material == null ? Material.AIR : material;
        });
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || mode.world() == null || event.getWorld() == null
                || !event.getWorld().getName().equals(mode.world().getName())) {
            return;
        }
        Actor actor = event.getActor();
        if (actor == null || !actor.isPlayer()) {
            return;
        }
        Player player = Bukkit.getPlayer(actor.getUniqueId());
        if (player != null && player.hasPermission("icemine.admin.creative")) {
            return;
        }
        UUID id = actor.getUniqueId();
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) throws WorldEditException {
                if (!mode.canEdit(id, position.x(), position.y(), position.z()) || mode.isBlocked(material(block))) {
                    return false;
                }
                return super.setBlock(position, block);
            }

            @Override
            public com.sk89q.worldedit.entity.Entity createEntity(com.sk89q.worldedit.util.Location location, BaseEntity entity) {
                return null;
            }

            @Override
            public boolean setBiome(BlockVector3 position, BiomeType biome) {
                return false;
            }
        });
    }

    static Extent unwrap(Extent extent) {
        return extent;
    }
}
