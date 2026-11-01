package hu.icemine.core.bedwars;

import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** A BedWars főmenüje (módválasztó), a statisztika és a néző menü. */
public final class BwMenus {

    private static final int[] DEFAULT_SLOTS = {10, 12, 14, 16};

    private final Bedwars plugin;

    public BwMenus(Bedwars plugin) {
        this.plugin = plugin;
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore != null) {
                meta.setLore(Text.color(lore));
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String raw(String key) {
        return plugin.core().messages().raw("bedwars." + key);
    }

    private String msg(String key, String... pairs) {
        return plugin.core().messages().get("bedwars." + key, pairs);
    }

    private List<String> list(String key, String... pairs) {
        return plugin.core().messages().list("bedwars." + key, pairs);
    }

    public void open(Player player) {
        Menu menu = new Menu("bw-main", 4, raw("menu.title"));
        menu.frame(Material.ORANGE_STAINED_GLASS_PANE, null);
        int index = 0;
        for (BwMode mode : plugin.modes().values()) {
            int slot = mode.slot >= 0 && mode.slot < 36 ? mode.slot : (index < DEFAULT_SLOTS.length ? DEFAULT_SLOTS[index] : -1);
            index++;
            if (slot < 0) {
                continue;
            }
            int playing = 0;
            int waiting = 0;
            int free = 0;
            for (BwArena arena : plugin.arenas(mode)) {
                if (arena.state() == BwArena.State.PLAYING || arena.state() == BwArena.State.ENDING) {
                    playing += arena.size();
                } else {
                    waiting += arena.size();
                    if (arena.joinable()) {
                        free++;
                    }
                }
            }
            List<String> lore = new ArrayList<>(mode.description);
            lore.add("");
            lore.addAll(list("menu.mode-lore", "%format%", mode.format(), "%playing%", String.valueOf(playing), "%waiting%", String.valueOf(waiting)));
            lore.add("");
            lore.add(msg(free > 0 ? "menu.click" : "menu.no-arena"));
            Material material = Items.material(mode.icon, Material.RED_BED);
            ItemStack icon = icon(material, mode.name, lore);
            icon.setAmount(Math.max(1, Math.min(64, playing + waiting)));
            menu.set(slot, icon, clicker -> {
                clicker.closeInventory();
                plugin.play(clicker, mode);
            });
        }
        menu.set(30, icon(Material.PAPER, raw("menu.stats"), list("menu.stats-lore")), this::stats);
        menu.set(31, icon(Material.ENDER_EYE, raw("menu.spectate"), list("menu.spectate-lore")), this::spectate);
        menu.set(32, icon(Material.GOLD_INGOT, raw("menu.top"), list("menu.top-lore")), clicker -> {
            clicker.closeInventory();
            for (String board : new String[]{"bedwarswins", "bedwarsfinals"}) {
                for (String line : plugin.core().leaderboards().lines(board)) {
                    clicker.sendMessage(Text.color(line));
                }
            }
        });
        menu.open(player);
    }

    public void stats(Player player) {
        stats(player, player);
    }

    public void stats(Player viewer, OfflinePlayer target) {
        PlayerProfile profile = plugin.core().profiles().profile(target.getUniqueId());
        Menu menu = new Menu("bw-stats", 4, msg("menu.stats-title", "%player%", target.getName() == null ? "?" : target.getName()));
        menu.frame(Material.ORANGE_STAINED_GLASS_PANE, null);
        long wins = stat(profile, "bedwars.wins");
        long games = stat(profile, "bedwars.games");
        long kills = stat(profile, "bedwars.kills");
        long deaths = stat(profile, "bedwars.deaths");
        long finals = stat(profile, "bedwars.final_kills");
        long finalDeaths = stat(profile, "bedwars.final_deaths");
        List<String> overall = list("stats.overall",
                "%wins%", Text.number(wins),
                "%games%", Text.number(games),
                "%losses%", Text.number(stat(profile, "bedwars.losses")),
                "%rate%", games == 0 ? "0%" : Math.round(wins * 100.0 / games) + "%",
                "%streak%", Text.number(stat(profile, "bedwars.streak")),
                "%best%", Text.number(stat(profile, "bedwars.best_streak")));
        List<String> combat = list("stats.combat",
                "%kills%", Text.number(kills),
                "%deaths%", Text.number(deaths),
                "%kd%", Text.ratio(kills, deaths),
                "%finals%", Text.number(finals),
                "%final_deaths%", Text.number(finalDeaths),
                "%fkdr%", Text.ratio(finals, finalDeaths),
                "%beds%", Text.number(stat(profile, "bedwars.beds")));
        menu.set(11, icon(Material.RED_BED, raw("stats.overall-title"), overall));
        menu.set(13, icon(Material.IRON_SWORD, raw("stats.combat-title"), combat));
        List<String> modes = new ArrayList<>();
        for (BwMode mode : plugin.modes().values()) {
            modes.add(msg("stats.mode-line", "%mode%", Text.color(mode.name), "%wins%", Text.number(stat(profile, "bedwars." + mode.id + ".wins")),
                    "%games%", Text.number(stat(profile, "bedwars." + mode.id + ".games"))));
        }
        menu.set(15, icon(Material.COMPASS, raw("stats.modes-title"), modes));
        menu.set(31, icon(Material.ARROW, plugin.core().messages().raw("menu.back"), null), this::open);
        menu.open(viewer);
    }

    private static long stat(PlayerProfile profile, String key) {
        return profile == null ? 0L : profile.stat(key);
    }

    public void spectate(Player player) {
        Menu menu = new Menu("bw-spectate", 4, raw("menu.spectate-title"));
        menu.frame(Material.ORANGE_STAINED_GLASS_PANE, null);
        int slot = 10;
        for (BwArena arena : plugin.allArenas()) {
            if (arena.state() != BwArena.State.PLAYING || slot > 25) {
                continue;
            }
            List<String> lore = list("menu.spectate-game", "%mode%", Text.color(arena.mode.name), "%map%", Text.color(arena.mapName()),
                    "%players%", String.valueOf(arena.size()));
            menu.set(slot, icon(Material.ENDER_EYE, arena.mapName(), lore), clicker -> {
                clicker.closeInventory();
                plugin.spectate(clicker, arena);
            });
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        if (slot == 10) {
            menu.set(13, icon(Material.BARRIER, raw("menu.spectate-none"), null));
        }
        menu.set(31, icon(Material.ARROW, plugin.core().messages().raw("menu.back"), null), this::open);
        menu.open(player);
    }

    static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }
}
