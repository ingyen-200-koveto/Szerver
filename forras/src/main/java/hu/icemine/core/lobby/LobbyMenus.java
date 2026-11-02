package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.board.Leaderboards;
import hu.icemine.core.combo.ComboFly;
import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.data.ProfileService;
import hu.icemine.core.economy.Economy;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.mode.ModeInfo;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class LobbyMenus {

    private final IceMineCore core;

    public LobbyMenus(IceMineCore core) {
        this.core = core;
    }

    private YamlConfiguration config() {
        return core.configs().get("lobby");
    }

    private String title(String id, String fallback) {
        return config().getString("menus." + id + ".title", fallback);
    }

    public void open(Player player, String id) {
        switch (id.toLowerCase(Locale.ROOT)) {
            case "main":
            case "fomenu":
                main(player);
                break;
            case "ranks":
                core.ranks().openMenu(player);
                break;
            case "selector":
                selector(player);
                break;
            case "profile":
                PlayerProfile own = core.profiles().profile(player);
                if (own != null) {
                    profile(player, own, player);
                }
                break;
            case "stats":
                stats(player);
                break;
            case "cosmetics":
                cosmetics(player);
                break;
            case "settings":
                settings(player);
                break;
            case "info":
                info(player);
                break;
            default:
                core.messages().send(player, "menu.unknown");
        }
    }

    /**
     * Főmenü: innen minden elérhető (játékmódok, kitek, Duels, profil, toplisták, rangok, napi jutalom, kozmetikák,
     * beállítások, kapcsolatok, információk, parkour). Megnyitás: a lobby NPC-k és tárgyak "menu:main" művelete.
     */
    public void main(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        Menu menu = new Menu("main", 4, title("main", "&3[IceMine] &8Főmenü"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        menu.set(10, button(Material.COMPASS, "mainmenu.selector"), clicker -> selector(clicker));
        menu.set(11, button(Material.CHEST, "mainmenu.kits"), clicker -> {
            hu.icemine.core.kitpvp.PvpMode mode = core.pvpModeOf(clicker);
            if (mode == null) {
                clicker.closeInventory();
                core.messages().send(clicker, "kit.only-pvp");
                return;
            }
            mode.openKits(clicker);
        });
        menu.set(12, button(Material.GOLDEN_SWORD, "mainmenu.duels"), clicker -> {
            if (core.duels() != null && clicker.getWorld().getName().equalsIgnoreCase("lobby_duels")) {
                core.duels().menu().open(clicker);
            } else {
                clicker.closeInventory();
                core.modes().sendAndReport(clicker, "duels");
            }
        });
        List<String> summary = new ArrayList<>();
        if (profile != null) {
            hu.icemine.core.rank.Ranks.Rank rank = core.ranks().of(player);
            summary.add(core.messages().raw("mainmenu.rank").replace("%rank%", rank.name));
            for (Economy.Currency currency : core.economy().currencies()) {
                summary.add("&7" + currency.name + ": " + core.economy().format(currency.id, profile.balance(currency.id)));
            }
            summary.add(core.messages().raw("mainmenu.playtime").replace("%time%", Text.duration(profile.playtime())));
            summary.add("");
        }
        summary.add(core.messages().raw("mainmenu.profile-click"));
        menu.set(13, Items.head(player).name("&b&l" + player.getName()).lore(summary).build(), clicker -> open(clicker, "profile"));
        menu.set(14, button(Material.GOLD_INGOT, "mainmenu.stats"), this::stats);
        menu.set(15, button(Material.NETHER_STAR, "mainmenu.ranks"), clicker -> core.ranks().openMenu(clicker));
        boolean daily = dailyReady(profile);
        Items dailyItem = Items.of(daily ? Material.CHEST_MINECART : Material.MINECART).name(core.messages().raw("mainmenu.daily.name"))
                .lore(core.messages().list(daily ? "mainmenu.daily.ready" : "mainmenu.daily.taken")).hideAll();
        if (daily) {
            dailyItem.glow();
        }
        menu.set(16, dailyItem.build(), clicker -> {
            clicker.closeInventory();
            clicker.performCommand("napi");
        });
        menu.set(19, button(Material.AMETHYST_SHARD, "mainmenu.cosmetics"), this::cosmetics);
        menu.set(20, button(Material.COMPARATOR, "mainmenu.settings"), this::settings);
        menu.set(21, button(Material.BOOK, "mainmenu.contacts"), clicker -> {
            clicker.closeInventory();
            core.contacts().chat(clicker);
        });
        menu.set(22, button(Material.RED_BED, "mainmenu.lobby"), clicker -> {
            clicker.closeInventory();
            core.modes().sendAndReport(clicker, hu.icemine.core.mode.ModeService.LOBBY);
        });
        menu.set(23, button(Material.HEART_OF_THE_SEA, "mainmenu.info"), this::info);
        menu.set(24, button(Material.FEATHER, "mainmenu.parkour"), clicker -> {
            clicker.closeInventory();
            clicker.performCommand("parkour");
        });
        menu.set(25, button(Material.EMERALD, "mainmenu.gens"), clicker -> {
            clicker.closeInventory();
            clicker.performCommand("gens");
        });
        menu.open(player);
    }

    private org.bukkit.inventory.ItemStack button(Material icon, String key) {
        return Items.of(icon).name(core.messages().raw(key + ".name")).lore(core.messages().list(key + ".lore")).hideAll().build();
    }

    private static boolean dailyReady(PlayerProfile profile) {
        if (profile == null) {
            return false;
        }
        try {
            return Long.parseLong(profile.setting("daily.last", "0")) < java.time.LocalDate.now().toEpochDay();
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private void back(Menu menu, int slot) {
        menu.set(slot, Items.of(Material.ARROW).name(core.messages().raw("menu.back")).build(), this::main);
    }

    public void selector(Player player) {
        Menu menu = new Menu("selector", 5, title("selector", "&b&lIce&f&lMine &8» &fJátékmódok"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE);
        for (ModeInfo info : core.modes().all()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(info.description);
            lore.add("");
            int online = info.status == ModeInfo.Status.SOON ? -1 : core.modes().online(info.id);
            if (online >= 0) {
                lore.add(core.messages().raw("selector.online").replace("%online%", String.valueOf(online)));
            }
            lore.add(statusLine(info));
            Items item = Items.of(info.displayIcon(core.lobby().world())).name(info.name).lore(lore).hideAll();
            if (info.status == ModeInfo.Status.OPEN && core.modes().available(info)) {
                item.glow();
            }
            menu.set(info.slot, item.build(), clicker -> {
                clicker.closeInventory();
                core.modes().sendAndReport(clicker, info.id);
            });
        }
        back(menu, 40);
        menu.open(player);
    }

    private String statusLine(ModeInfo info) {
        if (info.status == ModeInfo.Status.SOON) {
            return core.messages().raw("selector.status.soon");
        }
        if (!core.modes().available(info)) {
            return core.messages().raw("selector.status.unavailable");
        }
        switch (info.status) {
            case MAINTENANCE:
                return core.messages().raw("selector.status.maintenance");
            case CLOSED:
                return core.messages().raw("selector.status.closed");
            default:
                return core.messages().raw("selector.status.open");
        }
    }

    public String statusText(ModeInfo info) {
        return Text.color(statusLine(info));
    }

    public void profile(Player viewer, PlayerProfile profile, OfflinePlayer head) {
        Menu menu = new Menu("profile", 6, title("profile", "&b&lIce&f&lMine &8» &fProfil"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        SimpleDateFormat date = new SimpleDateFormat("yyyy.MM.dd.");
        List<String> lore = new ArrayList<>();
        Player online = Bukkit.getPlayer(profile.uuid());
        if (online != null && core.prefixes().hooked()) {
            String prefix = core.prefixes().prefix(online).trim();
            lore.add("&7Rang: " + (prefix.isEmpty() ? "&f" + core.prefixes().group(online) : prefix));
        }
        lore.add("&7Először itt: &f" + date.format(new Date(profile.firstJoin())));
        lore.add("&7Játékidő: &f" + Text.duration(profile.playtime()));
        lore.add("");
        for (Economy.Currency currency : core.economy().currencies()) {
            lore.add("&7" + currency.name + ": " + core.economy().format(currency.id, profile.balance(currency.id)));
        }
        Items headItem = head == null ? Items.of(Material.PLAYER_HEAD) : Items.head(head);
        menu.set(13, headItem.name("&b&l" + profile.name()).lore(lore).build());
        if (online != null) {
            hu.icemine.core.rank.Ranks.Rank rank = core.ranks().of(online);
            List<String> rankLore = new ArrayList<>(core.messages().list("mainmenu.rank-info", "%rank%", rank.name,
                    "%multiplier%", Text.decimal(rank.multiplier)));
            menu.set(11, Items.of(Material.NETHER_STAR).name(core.messages().raw("mainmenu.ranks.name")).lore(rankLore).build(),
                    clicker -> core.ranks().openMenu(clicker));
        }
        menu.set(15, Items.of(Material.GOLD_INGOT).name(core.messages().raw("mainmenu.stats.name")).lore(core.messages().list("mainmenu.stats.lore")).hideAll().build(),
                this::stats);
        List<String> fyland = new ArrayList<>();
        fyland.add("&7Kibányászott blokkok: &f" + Text.number(profile.stat("fyland.blocks")));
        fyland.add("&7Egyenleg: " + core.economy().format(core.economy().currency("fyland") == null ? core.economy().defaultCurrency() : "fyland",
                profile.balance(core.economy().currency("fyland") == null ? core.economy().defaultCurrency() : "fyland")));
        menu.set(31, Items.of(Material.GRASS_BLOCK).name("&a&lFyland").lore(fyland).hideAll().build());
        List<String> gens = new ArrayList<>();
        gens.add("&7Egyenleg: " + core.economy().format("gens", profile.balance("gens")));
        gens.add("&7Összes bevétel: &e" + Text.number(profile.stat("gens.earned")));
        gens.add("&7Földek: &f" + Math.max(1, profile.stat("gens.level")) + "&7/&f" + core.gens().fields().size());
        gens.add("&7Learatott növények: &f" + Text.number(profile.stat("gens.harvested")));
        gens.add("&7Prestige: &d" + profile.stat("gens.prestige") + " &8| &7Rebirth: &5" + profile.stat("gens.rebirth"));
        gens.add("&7Teljesített küldetések: &f" + profile.stat("gens.quests"));
        menu.set(33, Items.of(Material.EMERALD).name("&a&lGens").lore(gens).hideAll().build());
        List<String> duels = new ArrayList<>();
        long wins = profile.stat("duels.wins");
        long losses = profile.stat("duels.losses");
        duels.add("&7Győzelmek: &a" + Text.number(wins) + " &8| &7Vereségek: &c" + Text.number(losses));
        duels.add("&7Arány: &f" + Text.ratio(wins, losses));
        duels.add("&7Győzelmi sorozat: &e" + profile.stat("duels.streak") + " &8(legjobb: " + profile.stat("duels.best_streak") + ")");
        duels.add("&7Legjobb Elo: &b" + Math.max(1000, profile.stat("duels.elo_best")));
        duels.add("&7Lejátszott meccsek: &f" + profile.stat("duels.played"));
        menu.set(37, Items.of(Material.GOLDEN_SWORD).name("&e&lDuels").lore(duels).hideAll().build());
        List<String> parkour = new ArrayList<>();
        long best = profile.stat("parkour2.best_ms");
        parkour.add("&7Legjobb idő: &f" + (best > 0 ? Text.timer(best) : "-"));
        parkour.add("&7Teljesítve: &f" + profile.stat("parkour2.completions") + "x");
        menu.set(43, Items.of(Material.LEATHER_BOOTS).name("&f&lParkour").lore(parkour).hideAll().build());
        List<String> combo = new ArrayList<>();
        long comboKills = profile.stat("combofly.kills");
        long comboDeaths = profile.stat("combofly.deaths");
        combo.add("&7Legnagyobb kombó: &b" + profile.stat("combofly.best") + "x");
        combo.add("&7Kombók száma: &f" + profile.stat("combofly.combos"));
        combo.add("&7Ölések: &a" + Text.number(comboKills) + " &8| &7Halálok: &c" + Text.number(comboDeaths));
        combo.add("&7K/D: &f" + Text.ratio(comboKills, comboDeaths) + " &8| &7Legjobb sorozat: &e" + profile.stat("combofly.best_streak"));
        menu.set(41, Items.of(Material.PRISMARINE_SHARD).name("&3&lComboFly").lore(combo).hideAll().build());
        List<String> bedwars = new ArrayList<>();
        long bwWins = profile.stat("bedwars.wins");
        long bwGames = profile.stat("bedwars.games");
        bedwars.add("&7Győzelmek: &a" + Text.number(bwWins) + " &8| &7Meccsek: &f" + Text.number(bwGames));
        bedwars.add("&7Végső ölések: &b" + Text.number(profile.stat("bedwars.final_kills")) + " &8| &7Végső K/D: &f"
                + Text.ratio(profile.stat("bedwars.final_kills"), profile.stat("bedwars.final_deaths")));
        bedwars.add("&7Ölések: &a" + Text.number(profile.stat("bedwars.kills")) + " &8| &7K/D: &f"
                + Text.ratio(profile.stat("bedwars.kills"), profile.stat("bedwars.deaths")));
        bedwars.add("&7Lerombolt ágyak: &e" + Text.number(profile.stat("bedwars.beds")));
        bedwars.add("&7Győzelmi sorozat: &e" + profile.stat("bedwars.streak") + " &8(legjobb: " + profile.stat("bedwars.best_streak") + ")");
        menu.set(39, Items.of(Material.RED_BED).name("&6&lBedWars").lore(bedwars).hideAll().build());
        back(menu, 49);
        menu.open(viewer);
    }

    public void stats(Player player) {
        Menu menu = new Menu("stats", 6, title("stats", "&b&lIce&f&lMine &8» &fToplisták"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int index = 0;
        for (Leaderboards.Board board : core.leaderboards().boards().values()) {
            if (index >= slots.length) {
                break;
            }
            List<String> lore = new ArrayList<>();
            List<ProfileService.Entry> entries = core.leaderboards().entries(board.id);
            for (int i = 0; i < Math.min(10, entries.size()); i++) {
                lore.add("&b" + (i + 1) + ". &f" + entries.get(i).name + " &8- &e" + core.leaderboards().format(board, entries.get(i).value));
            }
            if (entries.isEmpty()) {
                lore.add(core.messages().raw("leaderboard.no-data"));
            }
            Material icon = Items.material(config().getString("menus.stats.icons." + board.id, "PAPER"), Material.PAPER);
            menu.set(slots[index++], Items.of(icon).name(board.title).lore(lore).hideAll().build());
        }
        menu.set(22, Items.head(player).name(core.messages().raw("selector.profile.name")).lore(core.messages().list("selector.profile.lore")).build(), clicker -> open(clicker, "profile"));
        back(menu, 49);
        menu.open(player);
    }

    public void cosmetics(Player player) {
        Menu menu = new Menu("cosmetics", 6, title("cosmetics", "&b&lIce&f&lMine &8» &fKozmetikák"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        menu.set(4, Items.of(Material.BLAZE_POWDER).name(core.messages().raw("cosmetics.trails-title"))
                .lore(core.messages().list("cosmetics.trails-lore")).hideAll().build());
        Cosmetics.Trail current = core.cosmetics().current(player);
        int slot = 10;
        for (Cosmetics.Trail trail : core.cosmetics().trails().values()) {
            if (slot > 16) {
                break;
            }
            boolean selected = current != null && current.id.equals(trail.id);
            menu.set(slot++, cosmeticItem(player, trail, selected), clicker -> {
                if (!clicker.hasPermission(trail.permission)) {
                    core.messages().send(clicker, "cosmetics.no-permission");
                    return;
                }
                core.cosmetics().select(clicker, selected ? null : trail.id);
                core.messages().send(clicker, selected ? "cosmetics.disabled" : "cosmetics.enabled", "%name%", trail.name);
                cosmetics(clicker);
            });
        }
        menu.set(22, Items.of(Material.IRON_SWORD).name(core.messages().raw("cosmetics.kills-title"))
                .lore(core.messages().list("cosmetics.kills-lore")).hideAll().build());
        Cosmetics.KillEffect currentKill = core.cosmetics().currentKill(player);
        slot = 28;
        for (Cosmetics.KillEffect effect : core.cosmetics().killEffects().values()) {
            if (slot > 34) {
                break;
            }
            boolean selected = currentKill != null && currentKill.id.equals(effect.id);
            menu.set(slot++, cosmeticItem(player, effect, selected), clicker -> {
                if (!clicker.hasPermission(effect.permission)) {
                    core.messages().send(clicker, "cosmetics.no-permission");
                    return;
                }
                core.cosmetics().selectKill(clicker, selected ? null : effect.id);
                core.messages().send(clicker, selected ? "cosmetics.disabled" : "cosmetics.enabled", "%name%", effect.name);
                if (!selected) {
                    core.cosmetics().playKill(clicker, clicker.getLocation().add(clicker.getLocation().getDirection().setY(0).normalize().multiply(2.5)));
                }
                cosmetics(clicker);
            });
        }
        back(menu, 49);
        menu.open(player);
    }

    private org.bukkit.inventory.ItemStack cosmeticItem(Player player, Cosmetics.Effect effect, boolean selected) {
        List<String> lore = new ArrayList<>(effect.description);
        lore.add("");
        boolean owned = player.hasPermission(effect.permission);
        if (selected) {
            lore.add(core.messages().raw("cosmetics.selected"));
        } else if (owned) {
            lore.add(core.messages().raw("cosmetics.click"));
        } else {
            lore.add(core.messages().raw("cosmetics.locked"));
        }
        Items item = Items.of(owned ? effect.icon : Material.GRAY_DYE).name(effect.name).lore(lore).hideAll();
        if (selected) {
            item.glow();
        }
        return item.build();
    }

    public void settings(Player player) {
        PlayerProfile profile = core.profiles().profile(player);
        if (profile == null) {
            return;
        }
        Menu menu = new Menu("settings", 3, title("settings", "&b&lIce&f&lMine &8» &fBeállítások"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        toggle(menu, 10, player, profile, LobbyModule.VISIBILITY, false, Material.ENDER_EYE, "settings.visibility", true, p -> core.lobby().applyVisibility(p));
        boolean canFly = player.hasPermission("icemine.cosmetic.fly");
        toggle(menu, 12, player, profile, LobbyModule.FLY, false, Material.ELYTRA, "settings.fly", canFly, p -> core.lobby().applyFlight(p));
        toggle(menu, 14, player, profile, ComboFly.DISPLAY, true, Material.PRISMARINE_SHARD, "settings.combo-display", true, null);
        toggle(menu, 15, player, profile, ComboFly.EFFECTS, true, Material.FIREWORK_STAR, "settings.combo-effects", true, null);
        toggle(menu, 16, player, profile, "board.hidden", false, Material.OAK_SIGN, "settings.scoreboard", true, p -> core.boards().rebuild(p));
        back(menu, 22);
        menu.open(player);
    }

    private interface Apply {
        void run(Player player);
    }

    private void toggle(Menu menu, int slot, Player player, PlayerProfile profile, String key, boolean fallback, Material icon, String text, boolean allowed, Apply apply) {
        boolean value = profile.flag(key, fallback);
        List<String> lore = new ArrayList<>(core.messages().list(text + ".lore"));
        lore.add("");
        if (!allowed) {
            lore.add(core.messages().raw("settings.locked"));
        } else {
            lore.add(core.messages().raw(value ? "settings.on" : "settings.off"));
        }
        Items item = Items.of(allowed ? icon : Material.GRAY_DYE).name(core.messages().raw(text + ".name")).lore(lore).hideAll();
        if (value && allowed) {
            item.glow();
        }
        menu.set(slot, item.build(), clicker -> {
            if (!allowed) {
                core.messages().send(clicker, "settings.no-permission");
                return;
            }
            PlayerProfile current = core.profiles().profile(clicker);
            if (current == null) {
                return;
            }
            current.putFlag(key, !current.flag(key, fallback));
            if (apply != null) {
                apply.run(clicker);
            }
            settings(clicker);
        });
    }

    public void info(Player player) {
        Menu menu = new Menu("info", 3, title("info", "&b&lIce&f&lMine &8» &fInformációk"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        ConfigurationSection section = config().getConfigurationSection("menus.info.items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                int slot = section.getInt(id + ".slot", 13);
                Material material = Items.material(section.getString(id + ".material", "PAPER"), Material.PAPER);
                String action = section.getString(id + ".action", "");
                Items item = Items.of(material).name(section.getString(id + ".name", id)).lore(section.getStringList(id + ".lore")).hideAll();
                menu.set(slot, item.build(), action.isBlank() ? null : clicker -> {
                    clicker.closeInventory();
                    core.lobby().handleAction(clicker, action);
                });
            }
        }
        back(menu, 22);
        menu.open(player);
    }

    public void openProfileOf(Player viewer, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerProfile profile = core.profiles().profile(online);
            if (profile != null) {
                profile(viewer, profile, online);
                return;
            }
        }
        core.profiles().lookup(name).whenComplete((profile, error) -> Bukkit.getScheduler().runTask(core, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (profile == null) {
                core.messages().send(viewer, "general.no-player");
                return;
            }
            profile(viewer, profile, Bukkit.getOfflinePlayer(profile.uuid()));
        }));
    }
}
