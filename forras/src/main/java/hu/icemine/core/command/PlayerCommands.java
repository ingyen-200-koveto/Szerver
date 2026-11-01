package hu.icemine.core.command;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.combo.ComboArenaMode;
import hu.icemine.core.combo.ComboFly;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.economy.Ledger;
import hu.icemine.core.kitpvp.PvpMode;
import hu.icemine.core.mode.ModeHandler;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.mode.ModeService;
import hu.icemine.core.util.Text;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PlayerCommands implements CommandExecutor, TabCompleter {

    private final IceMineCore core;
    private final Map<UUID, Long> payCooldown = new HashMap<>();

    public PlayerCommands(IceMineCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("penz")) {
            balance(sender, args);
            return true;
        }
        if (!(sender instanceof Player)) {
            core.messages().send(sender, "general.only-players");
            return true;
        }
        Player player = (Player) sender;
        if (!core.auth().isLoggedIn(player)) {
            core.messages().send(player, "auth.login-first");
            return true;
        }
        switch (name) {
            case "lobby":
                core.modes().sendAndReport(player, ModeService.LOBBY);
                break;
            case "spawn":
                spawn(player);
                break;
            case "profil":
                if (args.length > 0) {
                    core.menus().openProfileOf(player, args[0]);
                } else {
                    core.menus().open(player, "profile");
                }
                break;
            case "top":
                core.menus().stats(player);
                break;
            case "fizet":
                pay(player, args);
                break;
            case "napi":
                daily(player);
                break;
            case "kozmetika":
                core.menus().cosmetics(player);
                break;
            case "kapcsolat":
                if (core.lobby().inLobby(player)) {
                    core.contacts().open(player);
                } else {
                    core.contacts().chat(player);
                }
                break;
            case "parkour":
                parkour(player, args);
                break;
            case "combofly":
                combofly(player, args);
                break;
            case "kit":
                kit(player);
                break;
            case "arena":
                if (core.kitpvp().inWorld(player)) {
                    core.kitpvp().openArenas(player);
                } else {
                    core.messages().send(player, "arena.only-kitpvp");
                }
                break;
            case "gens":
                gens(player, args);
                break;
            case "elad":
                core.gens().sellAll(player);
                break;
            default:
                break;
        }
        return true;
    }

    private void spawn(Player player) {
        String mode = core.modes().current(player);
        if (mode == null || ModeService.LOBBY.equals(mode)) {
            core.modes().sendAndReport(player, ModeService.LOBBY);
            core.messages().send(player, "spawn.lobby");
            return;
        }
        if (core.combat().tagged(player)) {
            core.messages().send(player, "combat.blocked-generic");
            return;
        }
        String base = hu.icemine.core.modelobby.ModeLobbies.modeOf(mode);
        ModeInfo info = core.modes().info(base == null ? mode : base);
        String name = info == null ? mode : info.name;
        ModeHandler handler = core.modes().currentHandler(player);
        if (handler == null) {
            if (mode.equals("kingdoms") && core.kingdoms() != null && core.kingdoms().enabled()) {
                player.performCommand("k spawn");
                return;
            }
            Location location = player.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
            core.guard().allowNext(player);
            player.teleport(location);
            core.messages().send(player, "spawn.mode", "%mode%", name);
            return;
        }
        Location location = handler.spawn(player);
        if (location == null || location.getWorld() == null) {
            core.modes().sendAndReport(player, ModeService.LOBBY);
            return;
        }
        core.parkour().stop(player, true);
        core.guard().allowNext(player);
        player.teleport(location);
        if (handler instanceof PvpMode) {
            ((PvpMode) handler).backToSpawn(player);
        }
        core.messages().send(player, "spawn.mode", "%mode%", name);
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length > 0 && sender.hasPermission("icemine.player.balance.others")) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && core.profiles().profile(target) != null) {
                showBalance(sender, core.profiles().profile(target));
                return;
            }
            core.profiles().lookup(args[0]).whenComplete((profile, error) -> Bukkit.getScheduler().runTask(core, () -> {
                if (profile == null) {
                    core.messages().send(sender, "general.no-player");
                } else {
                    showBalance(sender, profile);
                }
            }));
            return;
        }
        if (!(sender instanceof Player)) {
            core.messages().send(sender, "general.only-players");
            return;
        }
        PlayerProfile profile = core.profiles().profile((Player) sender);
        if (profile != null) {
            showBalance(sender, profile);
        }
    }

    private void showBalance(CommandSender sender, PlayerProfile profile) {
        sender.sendMessage(core.messages().prefixed("economy.balance-header", "%player%", profile.name()));
        for (hu.icemine.core.economy.Economy.Currency currency : core.economy().currencies()) {
            sender.sendMessage(Text.color(" &7" + currency.name + ": " + core.economy().format(currency.id, profile.balance(currency.id))));
        }
    }

    private void pay(Player player, String[] args) {
        if (!core.configs().get("economy").getBoolean("pay.enabled", true)) {
            core.messages().send(player, "economy.pay-disabled");
            return;
        }
        if (args.length < 2) {
            core.messages().send(player, "economy.pay-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || core.profiles().profile(target) == null) {
            core.messages().send(player, "general.no-player");
            return;
        }
        long amount = Ledger.parseAmount(args[1]);
        long min = core.configs().get("economy").getLong("pay.min", 10);
        if (amount < min) {
            core.messages().send(player, "economy.pay-min", "%min%", String.valueOf(min));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = core.configs().get("economy").getLong("pay.cooldown-seconds", 5) * 1000L;
        Long last = payCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            core.messages().send(player, "economy.pay-cooldown");
            return;
        }
        String currency = core.configs().get("economy").getString("pay.currency", core.economy().defaultCurrency());
        Ledger.Result result = core.economy().transfer(player, target, currency, amount, "pay");
        switch (result) {
            case OK:
                payCooldown.put(player.getUniqueId(), now);
                core.messages().send(player, "economy.paid", "%player%", target.getName(), "%amount%", core.economy().format(currency, amount));
                core.messages().send(target, "economy.received", "%player%", player.getName(), "%amount%", core.economy().format(currency, amount));
                break;
            case INSUFFICIENT:
                core.messages().send(player, "economy.not-enough", "%amount%", core.economy().format(currency, amount));
                break;
            case SAME_PLAYER:
                core.messages().send(player, "economy.pay-self");
                break;
            default:
                core.messages().send(player, "economy.failed", "%reason%", result.name());
        }
    }

    private void daily(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        if (!core.configs().get("economy").getBoolean("daily.enabled", true)) {
            core.messages().send(player, "daily.disabled");
            return;
        }
        long today = LocalDate.now().toEpochDay();
        long last;
        long streak;
        try {
            last = Long.parseLong(profile.setting("daily.last", "0"));
            streak = Long.parseLong(profile.setting("daily.streak", "0"));
        } catch (NumberFormatException exception) {
            last = 0;
            streak = 0;
        }
        if (last >= today) {
            core.messages().send(player, "daily.taken");
            return;
        }
        streak = last == today - 1 ? streak + 1 : 1;
        long maxStreak = Math.max(1, core.configs().get("economy").getLong("daily.max-streak", 7));
        long base = Math.max(0, core.configs().get("economy").getLong("daily.amount", 150));
        long bonus = Math.max(0, core.configs().get("economy").getLong("daily.streak-bonus", 25)) * (Math.min(streak, maxStreak) - 1);
        String currency = core.configs().get("economy").getString("daily.currency", core.economy().defaultCurrency());
        long amount = core.ranks().boost(player, base + bonus);
        if (core.economy().deposit(player, currency, amount, "daily") != Ledger.Result.OK) {
            core.messages().send(player, "economy.failed", "%reason%", "LIMIT");
            return;
        }
        profile.put("daily.last", String.valueOf(today));
        profile.put("daily.streak", String.valueOf(streak));
        core.messages().send(player, "daily.claimed", "%amount%", core.economy().format(currency, amount), "%streak%", String.valueOf(streak));
    }

    private void parkour(Player player, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "start";
        if (action.equals("leave") || action.equals("kilep")) {
            if (core.parkour().running(player)) {
                core.parkour().stop(player, false);
            } else {
                core.messages().send(player, "parkour.not-running");
            }
            return;
        }
        core.parkour().teleportToStart(player);
    }

    private void combofly(Player player, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        PlayerProfile profile = core.profiles().profile(player);
        switch (action) {
            case "toggle":
                if (profile == null) {
                    return;
                }
                boolean value = !profile.flag(ComboFly.DISPLAY, true);
                profile.putFlag(ComboFly.DISPLAY, value);
                profile.putFlag(ComboFly.EFFECTS, value);
                core.messages().send(player, value ? "combo.display-on" : "combo.display-off");
                break;
            case "stats":
                if (profile == null) {
                    return;
                }
                long kills = profile.stat("combofly.kills");
                long deaths = profile.stat("combofly.deaths");
                core.messages().sendList(player, "combo.stats", "%best%", String.valueOf(profile.stat("combofly.best")),
                        "%combos%", Text.number(profile.stat("combofly.combos")), "%kills%", Text.number(kills),
                        "%deaths%", Text.number(deaths), "%kd%", Text.ratio(kills, deaths));
                break;
            case "reload":
                if (!player.hasPermission("icemine.admin.combofly")) {
                    core.messages().send(player, "general.no-permission");
                    return;
                }
                core.reloadConfigs("combofly");
                core.messages().send(player, "combo.reloaded");
                core.adminLog(player, "combofly", "reload", null);
                break;
            case "admin":
                comboAdmin(player, args);
                break;
            default:
                String mode = core.modes().current(player);
                if (args.length == 0 && !ComboArenaMode.ID.equals(mode)) {
                    core.modes().sendAndReport(player, ComboArenaMode.ID);
                    return;
                }
                core.messages().sendList(player, "combo.help", "%status%", core.messages().get(core.combo().activeIn(mode) ? "combo.status-on" : "combo.status-off"),
                        "%display%", core.messages().get(profile != null && profile.flag(ComboFly.DISPLAY, true) ? "combo.status-on" : "combo.status-off"));
        }
    }

    private void comboAdmin(Player player, String[] args) {
        if (!player.hasPermission("icemine.admin.combofly")) {
            core.messages().send(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            core.messages().send(player, "admin.usage", "%usage%", "/combofly admin <játékos> [reset]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || core.profiles().profile(target) == null) {
            core.messages().send(player, "general.no-player");
            return;
        }
        PlayerProfile profile = core.profiles().profile(target);
        if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
            profile.setStat("combofly.best", 0);
            profile.setStat("combofly.combos", 0);
            core.messages().send(player, "combo.admin-reset", "%player%", target.getName());
            core.adminLog(player, "combofly", target.getName(), "reset");
            return;
        }
        core.messages().sendList(player, "combo.admin-info", "%player%", target.getName(), "%current%", String.valueOf(core.combo().current(target)),
                "%best%", String.valueOf(profile.stat("combofly.best")), "%combos%", Text.number(profile.stat("combofly.combos")),
                "%mode%", String.valueOf(core.modes().current(target)), "%active%", core.messages().get(core.combo().activeIn(core.modes().current(target)) ? "combo.status-on" : "combo.status-off"));
    }

    private void kit(Player player) {
        PvpMode mode = core.kitpvp().inWorld(player) ? core.kitpvp() : (core.skypvp().inWorld(player) ? core.skypvp()
                : (core.comboArena().inWorld(player) ? core.comboArena() : null));
        if (mode == null) {
            core.messages().send(player, "kit.only-pvp");
            return;
        }
        mode.openKits(player);
    }

    private void gens(Player player, String[] args) {
        if (!core.gens().inWorld(player)) {
            if (args.length == 0 || args[0].equalsIgnoreCase("home")) {
                core.modes().sendAndReport(player, "gens");
            } else {
                core.messages().send(player, "gens.only-in-gens");
            }
            return;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "home":
            case "haza":
                core.gens().goHome(player);
                break;
            case "visit":
            case "latogat":
                if (args.length < 2) {
                    core.messages().send(player, "admin.usage", "%usage%", "/gens visit <játékos>");
                } else {
                    core.gens().visit(player, args[1]);
                }
                break;
            case "trust":
                if (args.length < 2) {
                    core.messages().send(player, "admin.usage", "%usage%", "/gens trust <játékos>");
                } else {
                    core.gens().trust(player, args[1], true);
                }
                break;
            case "untrust":
                if (args.length < 2) {
                    core.messages().send(player, "admin.usage", "%usage%", "/gens untrust <játékos>");
                } else {
                    core.gens().trust(player, args[1], false);
                }
                break;
            case "sell":
                core.gens().sellAll(player);
                break;
            case "daily":
                core.gens().daily(player);
                break;
            case "quests":
                core.gensMenu().quests(player);
                break;
            case "shop":
            case "bolt":
                core.gensMenu().shop(player);
                break;
            case "backpack":
            case "hatizsak":
            case "bp":
                core.gens().openBackpack(player);
                break;
            case "autosell":
                core.gens().toggleAutosell(player);
                break;
            case "prestige":
                core.gens().prestige(player);
                break;
            case "rebirth":
                core.gens().rebirth(player);
                break;
            case "members":
            case "tagok":
                core.gensMenu().members(player);
                break;
            case "upgrades":
            case "fejlesztes":
                core.gensMenu().upgrades(player);
                break;
            case "lock":
            case "zar":
                core.gens().toggleLock(player);
                break;
            default:
                core.gensMenu().open(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            switch (name) {
                case "combofly":
                    return AdminCommand.filter(sender.hasPermission("icemine.admin.combofly")
                            ? Arrays.asList("toggle", "stats", "reload", "admin") : Arrays.asList("toggle", "stats"), args[0]);
                case "parkour":
                    return AdminCommand.filter(Arrays.asList("start", "leave"), args[0]);
                case "gens":
                    return AdminCommand.filter(Arrays.asList("home", "visit", "trust", "untrust", "sell", "daily", "quests", "shop", "backpack", "autosell",
                            "prestige", "rebirth", "members", "upgrades", "lock"), args[0]);
                case "fizet":
                case "profil":
                case "penz":
                    return null;
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 2 && (name.equals("gens") || name.equals("combofly"))) {
            return null;
        }
        return Collections.emptyList();
    }
}
