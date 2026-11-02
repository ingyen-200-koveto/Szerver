package hu.icemine.core.fyland;

import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * A sziget közösségi része: név, info, moderátorok, ideiglenes segítők (coop), kitiltás, zárás, látogatási pont
 * és a játékos warpok (/pw) értékeléssel.
 */
final class FySocial implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final FylandMode mode;

    FySocial(FylandMode mode) {
        this.mode = mode;
    }

    private FyIsland managed(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            mode.send(player, "no-island");
            return null;
        }
        if (!island.manager(player.getUniqueId())) {
            mode.send(player, "manager-only");
            return null;
        }
        return island;
    }

    private FyIsland owned(Player player) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            mode.send(player, "no-island");
            return null;
        }
        if (!island.owner.equals(player.getUniqueId())) {
            mode.send(player, "owner-only");
            return null;
        }
        return island;
    }

    private static UUID memberByName(FyIsland island, String name) {
        for (Map.Entry<UUID, String> entry : island.members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    static boolean validName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 20 && name.matches("[\\p{L}0-9_ ]+");
    }

    // ---- név, info ----

    /** /fy create <név>: ha még nincs szigeted, kapsz egyet; ha van és a tiéd, elnevezi. */
    void create(Player player, String name) {
        FyIsland island = mode.islandOf(player);
        if (island == null) {
            if (!mode.inWorld(player)) {
                mode.core().modes().sendAndReport(player, FylandMode.ID);
            }
            island = mode.islandOf(player);
            if (island == null) {
                return;
            }
        }
        if (name == null || name.isBlank()) {
            return;
        }
        if (!island.owner.equals(player.getUniqueId())) {
            mode.send(player, "owner-only");
            return;
        }
        if (!validName(name)) {
            mode.send(player, "name-bad");
            return;
        }
        for (FyIsland other : mode.islands()) {
            if (other != island && other.name.equalsIgnoreCase(name)) {
                mode.send(player, "name-taken");
                return;
            }
        }
        island.name = name;
        island.dirty = true;
        mode.tell(island, "name-set", "%name%", name);
    }

    void info(Player player, String target) {
        FyIsland island = target == null ? mode.islandOf(player) : find(target);
        if (island == null) {
            mode.send(player, target == null ? "no-island" : "visit-none", "%player%", String.valueOf(target));
            return;
        }
        List<String> members = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : island.members.entrySet()) {
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            members.add((island.moderators.contains(entry.getKey()) ? "&b★" : "") + (online ? "&a" : "&7") + entry.getValue());
        }
        FyPhase phase = mode.phase(island);
        for (String line : mode.texts("info", "%name%", island.display(), "%owner%", island.ownerName,
                "%members%", members.isEmpty() ? "&8-" : String.join("&8, ", members), "%level%", String.valueOf(mode.level(island)),
                "%worth%", Text.number(island.worth), "%next%", Text.number(mode.worth().toNext(island)), "%phase%", phase.name,
                "%blocks%", Text.number(island.blocks), "%cycle%", String.valueOf(island.cycle),
                "%visitors%", mode.text(island.visitors ? "info-open" : "info-closed"),
                "%warp%", island.warpName.isBlank() ? "&8-" : "&f" + island.warpName,
                "%rating%", island.ratings.isEmpty() ? "&8-" : "&e" + Text.decimal(island.rating()) + "★ &8(" + island.ratings.size() + ")")) {
            player.sendMessage(Text.color(line));
        }
    }

    /** Sziget név szerint: játékos név (tag vagy tulaj), sziget név vagy játékos warp név. */
    FyIsland find(String name) {
        FyIsland island = mode.findByName(name);
        if (island != null) {
            return island;
        }
        for (FyIsland other : mode.islands()) {
            if (other.name.equalsIgnoreCase(name) || other.warpName.equalsIgnoreCase(name)) {
                return other;
            }
        }
        return null;
    }

    // ---- rangok ----

    void promote(Player player, String name, boolean up) {
        FyIsland island = owned(player);
        if (island == null) {
            return;
        }
        UUID target = memberByName(island, name);
        if (target == null) {
            mode.send(player, "not-member", "%player%", name);
            return;
        }
        String targetName = island.members.get(target);
        if (up ? !island.moderators.add(target) : !island.moderators.remove(target)) {
            mode.send(player, up ? "already-moderator" : "not-moderator", "%player%", targetName);
            return;
        }
        island.dirty = true;
        mode.tell(island, up ? "promoted" : "demoted", "%player%", targetName);
    }

    // ---- coop ----

    void coop(Player player, String name, boolean add) {
        FyIsland island = managed(player);
        if (island == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (add) {
            if (target == null) {
                mode.send(player, "player-offline", "%player%", name);
                return;
            }
            if (island.member(target.getUniqueId())) {
                mode.send(player, "already-member", "%player%", target.getName());
                return;
            }
            island.coops.add(target.getUniqueId());
            mode.tell(island, "coop-added", "%player%", target.getName());
            mode.send(target, "coop-you", "%island%", island.display());
            return;
        }
        UUID id = target != null ? target.getUniqueId() : null;
        if (id == null) {
            for (UUID coop : island.coops) {
                Player online = Bukkit.getPlayer(coop);
                if (online != null && online.getName().equalsIgnoreCase(name)) {
                    id = coop;
                }
            }
        }
        if (id == null || !island.coops.remove(id)) {
            mode.send(player, "coop-none", "%player%", name);
            return;
        }
        mode.tell(island, "coop-removed", "%player%", name);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (FyIsland island : mode.islands()) {
            island.coops.remove(id);
            if (!island.coops.isEmpty() && island.member(id)) {
                boolean anyone = false;
                for (UUID member : mode.allMembers(island)) {
                    if (!member.equals(id) && Bukkit.getPlayer(member) != null) {
                        anyone = true;
                        break;
                    }
                }
                if (!anyone) {
                    island.coops.clear();
                }
            }
        }
    }

    // ---- kitiltás, zárás ----

    void ban(Player player, String name, boolean add) {
        FyIsland island = managed(player);
        if (island == null) {
            return;
        }
        if (!add) {
            UUID found = null;
            for (Map.Entry<UUID, String> entry : island.banned.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(name)) {
                    found = entry.getKey();
                }
            }
            if (found == null) {
                mode.send(player, "ban-none", "%player%", name);
                return;
            }
            String banned = island.banned.remove(found);
            island.dirty = true;
            mode.tell(island, "unbanned", "%player%", banned);
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        org.bukkit.OfflinePlayer cached = target == null ? Bukkit.getOfflinePlayerIfCached(name) : null;
        UUID id = target != null ? target.getUniqueId() : cached == null ? null : cached.getUniqueId();
        if (id == null) {
            mode.send(player, "player-offline", "%player%", name);
            return;
        }
        if (island.member(id)) {
            mode.send(player, "ban-member");
            return;
        }
        String targetName = target != null ? target.getName() : name;
        island.banned.put(id, targetName);
        island.coops.remove(id);
        island.dirty = true;
        mode.tell(island, "banned", "%player%", targetName);
        if (target != null && mode.inWorld(target) && mode.cellAt(target.getLocation()) == island) {
            kickAway(target);
            mode.send(target, "you-banned", "%island%", island.display());
        }
    }

    /** Elküldi a játékost a saját szigetére (vagy a központba). */
    void kickAway(Player target) {
        FyIsland own = mode.islandOf(target);
        mode.teleport(target, own == null ? mode.world().getSpawnLocation().add(0.5, 0, 0.5) : mode.home(own));
    }

    void lock(Player player, boolean locked) {
        FyIsland island = managed(player);
        if (island != null) {
            mode.setVisitors(player, island, !locked);
        }
    }

    // ---- látogatási pont, játékos warp ----

    void setWarp(Player player, String name) {
        FyIsland island = managed(player);
        if (island == null) {
            return;
        }
        Location here = player.getLocation();
        Block below = here.clone().subtract(0, 0.2, 0).getBlock();
        if (mode.islandAt(here) != island || !below.getType().isSolid()) {
            mode.send(player, "warp-bad");
            return;
        }
        if (name != null && !name.isBlank()) {
            if (!validName(name) || name.length() > 16) {
                mode.send(player, "name-bad");
                return;
            }
            for (FyIsland other : mode.islands()) {
                if (other != island && other.warpName.equalsIgnoreCase(name)) {
                    mode.send(player, "name-taken");
                    return;
                }
            }
            island.warpName = name;
        }
        island.hasWarp = true;
        island.warpX = here.getX();
        island.warpY = here.getY();
        island.warpZ = here.getZ();
        island.warpYaw = here.getYaw();
        island.dirty = true;
        mode.send(player, island.warpName.isBlank() ? "warp-set" : "warp-set-named", "%name%", island.warpName);
    }

    void delWarp(Player player) {
        FyIsland island = managed(player);
        if (island == null) {
            return;
        }
        island.hasWarp = false;
        island.warpName = "";
        island.ratings.clear();
        island.dirty = true;
        mode.send(player, "warp-deleted");
    }

    /** Ahová a látogatók érkeznek: a látogatási pont, vagy (ha nem kötelező) az otthon. Null, ha nem lehet látogatni. */
    Location visitPoint(FyIsland island) {
        if (island.hasWarp) {
            Location warp = new Location(mode.world(), island.warpX, island.warpY, island.warpZ, island.warpYaw, 0);
            if (mode.inside(island, warp) && warp.clone().subtract(0, 0.2, 0).getBlock().getType().isSolid()) {
                return warp;
            }
        }
        return mode.config().getBoolean("visit.require-warp", true) ? null : mode.home(island);
    }

    boolean visitable(FyIsland island, Player viewer) {
        return island.visitors && !island.banned.containsKey(viewer.getUniqueId()) && visitPoint(island) != null;
    }

    void rate(Player player, String name, String value) {
        FyIsland island = find(name);
        if (island == null || island.warpName.isBlank()) {
            mode.send(player, "pw-none", "%name%", name);
            return;
        }
        if (island.member(player.getUniqueId())) {
            mode.send(player, "pw-rate-own");
            return;
        }
        int stars;
        try {
            stars = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            stars = 0;
        }
        if (stars < 1 || stars > 5) {
            mode.send(player, "usage", "%usage%", "/pw rate <warp> <1-5>");
            return;
        }
        island.ratings.put(player.getUniqueId(), stars);
        island.dirty = true;
        mode.send(player, "pw-rated", "%name%", island.warpName, "%stars%", String.valueOf(stars));
    }

    void goWarp(Player player, String name) {
        FyIsland island = find(name);
        if (island == null || island.warpName.isBlank()) {
            mode.send(player, "pw-none", "%name%", name);
            return;
        }
        mode.visit(player, island, island.warpName);
    }

    /** /pw: a nyilvános játékos warpok, a legjobbra értékeltek elöl. */
    void warps(Player player, int page) {
        List<FyIsland> list = new ArrayList<>();
        for (FyIsland island : mode.islands()) {
            if (!island.warpName.isBlank() && (visitable(island, player) || island.member(player.getUniqueId()))) {
                list.add(island);
            }
        }
        list.sort(Comparator.comparingDouble(FyIsland::rating).thenComparingInt(island -> island.ratings.size()).thenComparingLong(island -> island.worth).reversed());
        Menu menu = new Menu("fyland-pw", 5, mode.text("menu.pw-title"));
        menu.frame(Material.BLACK_STAINED_GLASS_PANE, null);
        int from = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && from + i < list.size(); i++) {
            FyIsland island = list.get(from + i);
            menu.set(SLOTS[i], Items.head(Bukkit.getOfflinePlayer(island.owner)).name("&a&l" + island.warpName)
                    .lore(mode.texts("menu.pw-entry", "%owner%", island.ownerName, "%island%", island.display(), "%level%", String.valueOf(mode.level(island)),
                            "%rating%", island.ratings.isEmpty() ? "&8még senki" : "&e" + Text.decimal(island.rating()) + "★ &8(" + island.ratings.size() + ")"))
                    .build(), clicker -> {
                clicker.closeInventory();
                mode.visit(clicker, island, island.warpName);
            });
        }
        if (list.isEmpty()) {
            menu.set(22, Items.of(Material.PAPER).name(mode.text("menu.pw-empty")).lore(mode.texts("menu.pw-empty-lore")).build());
        }
        if (page > 0) {
            menu.set(36, Items.of(Material.ARROW).name(mode.text("menu.prev")).build(), clicker -> warps(clicker, page - 1));
        }
        if (from + SLOTS.length < list.size()) {
            menu.set(44, Items.of(Material.ARROW).name(mode.text("menu.next")).build(), clicker -> warps(clicker, page + 1));
        }
        menu.open(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }
}
