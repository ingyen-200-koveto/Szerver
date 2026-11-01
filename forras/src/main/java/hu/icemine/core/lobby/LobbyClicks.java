package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A "Kattints rám!" feliratok (hologramok, NPC-k neve) kattintása a szerver oldalon is: ha a játékos a levegőbe vagy egy
 * blokkra kattint, és közben egy ilyen feliratra néz, azt vesszük kattintásnak.
 *
 * Erre azért van szükség, mert a felirat mögötti láthatatlan "kattintható doboz" (Interaction entitás) nem mindig működik:
 * a régi (1.19.3 előtti) kliensek és a Bedrock játékosok nem látják, üres kézzel jobb klikkre pedig a kliens semmit nem küld.
 * Ha a kattintás így talált, a hotbar tárgy (pl. az iránytű) nem nyílik meg mellé.
 */
public final class LobbyClicks implements Listener {

    /** Ilyen messziről lehet egy feliratra kattintani (blokkban). */
    static final double REACH = 4.5;

    /** Egy kattintható felirat a világban: a doboza, és hogy mi történjen. */
    public record Target(BoundingBox box, Consumer<Player> action) {
    }

    private final IceMineCore core;
    private PlayerInteractEvent handled;

    public LobbyClicks(IceMineCore core) {
        this.core = core;
    }

    /** Ezt a kattintást már egy feliratra értettük (a hotbar tárgyak ilyenkor nem csinálnak semmit). */
    public boolean handled(PlayerInteractEvent event) {
        return event != null && event == handled;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        World world = player.getWorld();
        boolean lobby = core.lobby() != null && world == core.lobby().world();
        boolean modeLobby = core.modeLobbies() != null && core.modeLobbies().isLobbyWorld(world);
        if (!lobby && !modeLobby) {
            return;
        }
        List<Target> targets = new ArrayList<>();
        if (lobby && core.holograms() != null) {
            core.holograms().targets(targets);
        }
        if (modeLobby) {
            core.modeLobbies().targets(world, targets);
        }
        if (core.npcs() != null) {
            core.npcs().targets(world, targets);
        }
        if (targets.isEmpty()) {
            return;
        }
        Location eye = player.getEyeLocation();
        double reach = REACH;
        Block block = event.getClickedBlock();
        if (block != null) {
            reach = Math.min(reach, eye.toVector().distance(block.getLocation().add(0.5, 0.5, 0.5).toVector()));
        }
        Target target = nearest(eye.toVector(), eye.getDirection(), targets, reach);
        if (target == null) {
            return;
        }
        handled = event;
        event.setCancelled(true);
        target.action().accept(player);
    }

    /** A legközelebbi felirat, amin a nézési irány átmegy, legfeljebb reach távolságra; null, ha egyik sem. */
    static Target nearest(Vector start, Vector direction, List<Target> targets, double reach) {
        Target best = null;
        double bestDistance = reach;
        for (Target target : targets) {
            if (target.box().contains(start)) {
                return target;
            }
            RayTraceResult hit = target.box().rayTrace(start, direction, bestDistance);
            if (hit == null) {
                continue;
            }
            double distance = hit.getHitPosition().distance(start);
            if (distance <= bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }
}
