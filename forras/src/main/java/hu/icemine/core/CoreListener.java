package hu.icemine.core;

import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class CoreListener implements Listener {

    private final IceMineCore core;

    CoreListener(IceMineCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        boolean profile = core.profiles().preload(event.getUniqueId(), event.getName());
        boolean gens = profile && core.gens().preload(event.getUniqueId());
        if (!profile || !gens) {
            core.profiles().discardPending(event.getUniqueId());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, core.messages().get("general.load-failed"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinEarly(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!core.profiles().attach(player)) {
            Bukkit.getScheduler().runTask(core, () -> player.kickPlayer(core.messages().get("general.load-failed")));
            return;
        }
        core.gens().attach(player);
        core.prefixes().apply(player);
        core.ranks().apply(player);
        // ha a szerver leállt, mialatt valakin KitPvP extra élet volt, itt visszaáll a normál 10 szívre
        hu.icemine.core.util.Attributes.setMaxHealth(player, 20.0);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String format = core.configs().get("config").getString("join-message", "");
        event.setJoinMessage(format == null || format.isBlank() ? null
                : Text.color(format.replace("%player%", player.getName()).replace("%prefix%", core.prefixes().prefix(player))));
        if (core.profiles().profile(player) == null) {
            return;
        }
        core.iceCoins().refreshSoon();
        if (player.isDead()) {
            Bukkit.getScheduler().runTask(core, () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
                route(player);
            });
            return;
        }
        route(player);
    }

    private void route(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (core.profiles().profile(player) != null && core.profiles().profile(player).fresh()) {
            core.messages().sendList(player, "general.first-join", "%player%", player.getName());
        }
        if (core.lobby().inLobby(player) || core.lobby().joinToLobby()) {
            if (!core.lobby().inLobby(player)) {
                core.guard().allowNext(player);
                player.teleport(core.lobby().spawn(player));
                return;
            }
            core.guard().allowNext(player);
            player.teleport(core.lobby().spawn(player));
            core.lobby().enter(player);
        } else {
            ModeHandler handler = core.modes().currentHandler(player);
            if (handler != null) {
                handler.enter(player);
            }
        }
        core.boards().rebuild(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String format = core.configs().get("config").getString("quit-message", "");
        event.setQuitMessage(format == null || format.isBlank() ? null
                : Text.color(format.replace("%player%", player.getName()).replace("%prefix%", core.prefixes().prefix(player))));
        core.lobby().quit(player);
        core.npcs().forget(player.getUniqueId());
        core.iceCoins().forget(player);
        core.prefixes().clear(player);
        core.ranks().clear(player);
        core.profiles().detach(player);
    }
}
