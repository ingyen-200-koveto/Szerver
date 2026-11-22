package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** 10 elrejtett ajándékdoboz-fej a lobbyban. Aki mindet megtalálja, nagy jutalmat kap. */
public final class RejtettFejek implements Listener {

    /** Helyek a lobby közepéhez képest (x, z); a fej a legmagasabb blokk tetejére kerül. */
    private static final int[][] HELYEK = {
            {-36, -28}, {37, -47}, {-24, -28}, {13, -47}, {8, -57},
            {-13, -63}, {17, -74}, {102, -38}, {-96, -30}, {-18, -66}
    };
    private static final String[] TULAJ = {"MHF_Present1", "MHF_Present2"};
    private static final String KULCS = "lobby.heads";

    private final IceMineCore core;
    private final List<Location> fejek = new ArrayList<>();
    private boolean be = true;
    private long jutalom = 25;
    private long fodij = 1000;

    public RejtettFejek(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, World world) {
        fejek.clear();
        be = config.getBoolean("secret-heads.enabled", true);
        jutalom = Math.max(0, config.getLong("secret-heads.reward", 25));
        fodij = Math.max(0, config.getLong("secret-heads.reward-all", 1000));
        if (!be || world == null) {
            return;
        }
        int cx = config.getInt("center.x", 0);
        int cy = config.getInt("center.y", 100);
        int cz = config.getInt("center.z", 0);
        boolean changed = false;
        for (int i = 0; i < HELYEK.length; i++) {
            String path = "secret-heads.places." + (i + 1);
            Location loc;
            if (config.isSet(path + ".x")) {
                loc = new Location(world, config.getInt(path + ".x"), config.getInt(path + ".y"), config.getInt(path + ".z"));
            } else {
                int x = cx + HELYEK[i][0];
                int z = cz + HELYEK[i][1];
                loc = null;
                for (int y = cy - 15; y < cy + 60 && loc == null; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if ((b.getType().isAir() || b.getType() == Material.PLAYER_HEAD) && b.getRelative(BlockFace.DOWN).getType().isSolid()
                            && b.getRelative(BlockFace.UP).getType().isAir()) {
                        loc = b.getLocation();
                    }
                }
                if (loc == null) {
                    loc = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING).getRelative(BlockFace.UP).getLocation();
                }
                config.set(path + ".x", loc.getBlockX());
                config.set(path + ".y", loc.getBlockY());
                config.set(path + ".z", loc.getBlockZ());
                changed = true;
            }
            fejek.add(loc);
            place(loc.getBlock(), i);
        }
        if (changed) {
            core.configs().save("lobby");
        }
    }

    private static void place(Block block, int index) {
        if (block.getType() != Material.PLAYER_HEAD && !block.getType().isAir()) {
            return;
        }
        if (block.getType() != Material.PLAYER_HEAD) {
            block.setType(Material.PLAYER_HEAD, false);
            if (block.getBlockData() instanceof Rotatable rot) {
                BlockFace[] faces = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
                rot.setRotation(faces[index % faces.length]);
                block.setBlockData(rot, false);
            }
        }
        if (block.getState() instanceof Skull skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(TULAJ[index % TULAJ.length]));
            skull.update(true, false);
        }
    }

    public int count() {
        return fejek.size();
    }

    private int index(Block block) {
        for (int i = 0; i < fejek.size(); i++) {
            Location l = fejek.get(i);
            if (l.getBlockX() == block.getX() && l.getBlockY() == block.getY() && l.getBlockZ() == block.getZ() && l.getWorld() == block.getWorld()) {
                return i;
            }
        }
        return -1;
    }

    public static Set<Integer> found(PlayerProfile profile) {
        Set<Integer> out = new LinkedHashSet<>();
        if (profile == null) {
            return out;
        }
        for (String part : profile.setting(KULCS, "").split(",")) {
            try {
                if (!part.isBlank()) {
                    out.add(Integer.parseInt(part.trim()));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (!be || block == null || block.getType() != Material.PLAYER_HEAD || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        int i = index(block);
        if (i < 0) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        Set<Integer> found = found(profile);
        int all = fejek.size();
        if (!found.add(i + 1)) {
            core.messages().sendTagged(player, "&7Ezt az ajándékot már megtaláltad. &8(" + found.size() + "/" + all + ")");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int n : found) {
            text.append(text.length() == 0 ? "" : ",").append(n);
        }
        profile.put(KULCS, text.toString());
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(Particle.FIREWORK, at, 25, 0.3, 0.3, 0.3, 0.05);
        player.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1F, 1.4F);
        String currency = core.economy().defaultCurrency();
        if (jutalom > 0) {
            core.economy().deposit(player, currency, jutalom, "rejtett fej");
        }
        if (core.levels() != null) {
            core.levels().add(profile, 20);
        }
        if (found.size() >= all) {
            if (fodij > 0) {
                core.economy().deposit(player, currency, fodij, "minden rejtett fej");
            }
            player.sendTitle(Text.color("&b&lMEGVAN MIND!"), Text.color("&7Mind a " + all + " ajándékot megtaláltad"), 5, 60, 15);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
            String msg = Text.color("&b[IceMine] &f" + player.getName() + " &7megtalálta mind a " + all + " elrejtett ajándékot a lobbyban!");
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.sendMessage(msg);
            }
            core.messages().sendTagged(player, "&aGratulálok! &7Jutalmad: &f" + core.economy().format(currency, fodij));
        } else {
            core.messages().sendTagged(player, "&aTaláltál egy ajándékot! &f" + found.size() + "/" + all + " &7(&f+" + core.economy().format(currency, jutalom) + "&7)");
        }
    }
}
