package hu.icemine.core.skywars;

import hu.icemine.core.data.PlayerProfile;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * SkyWars ketrecek. Az alap ketrec mindenkinek megvan, a többit feladatokkal lehet feloldani
 * (ölések, győzelmek, lejátszott meccsek, győzelmi sorozat). A választott ketrec a profilban marad
 * (skywars.cage), és a meccs elején abban a ketrecben vársz.
 */
public final class SwCages {

    public static final String SETTING = "skywars.cage";
    public static final String RAINBOW = "rainbow";
    private static final String[] RAINBOW_GLASS = {"red_stained_glass", "orange_stained_glass", "yellow_stained_glass",
            "lime_stained_glass", "light_blue_stained_glass", "blue_stained_glass", "purple_stained_glass", "magenta_stained_glass"};

    /** Egy ketrec. Ha a floor null, a pálya saját üvegéből épül (alap ketrec). */
    public record Cage(String id, String name, Material icon, String floor, String wall, String roof, String stat, long need, String task) {

        boolean free() {
            return stat == null || need <= 0;
        }
    }

    private final Skywars plugin;
    private final Map<String, Cage> cages = new LinkedHashMap<>();

    SwCages(Skywars plugin) {
        this.plugin = plugin;
        add(new Cage("alap", "&fAlap ketrec", Material.GLASS, null, null, null, null, 0, "Mindenkinek megvan"));
        add(new Cage("jegkocka", "&b&lJégkocka", Material.PACKED_ICE, "packed_ice", "light_blue_stained_glass", "blue_ice",
                "skywars.games", 20, "Játssz le 20 meccset"));
        add(new Cage("hovihar", "&f&lHóvihar", Material.SNOW_BLOCK, "snow_block", "white_stained_glass", "snow_block",
                "skywars.kills", 30, "Ölj meg 30 játékost"));
        add(new Cage("smaragd", "&a&lSmaragd", Material.EMERALD_BLOCK, "emerald_block", "lime_stained_glass", "emerald_block",
                "skywars.wins", 10, "Nyerj 10 meccset"));
        add(new Cage("tokfej", "&6&lTökfej", Material.JACK_O_LANTERN, "pumpkin", "orange_stained_glass", "jack_o_lantern",
                "skywars.games", 50, "Játssz le 50 meccset"));
        add(new Cage("slime", "&a&lZselé", Material.SLIME_BLOCK, "slime_block", "lime_stained_glass", "slime_block",
                "skywars.kills", 60, "Ölj meg 60 játékost"));
        add(new Cage("arany", "&6&lArany", Material.GOLD_BLOCK, "gold_block", "yellow_stained_glass", "gold_block",
                "skywars.kills", 100, "Ölj meg 100 játékost"));
        add(new Cage("ocean", "&3&lÓceán", Material.PRISMARINE, "prismarine", "cyan_stained_glass", "sea_lantern",
                "skywars.wins", 25, "Nyerj 25 meccset"));
        add(new Cage("tnt", "&c&lT&f&lN&c&lT", Material.TNT, "tnt", "red_stained_glass", "tnt",
                "skywars.kills", 150, "Ölj meg 150 játékost"));
        add(new Cage("lava", "&c&lLáva", Material.MAGMA_BLOCK, "magma_block", "orange_stained_glass", "nether_bricks",
                "skywars.kills", 200, "Ölj meg 200 játékost"));
        add(new Cage("ametiszt", "&d&lAmetiszt", Material.AMETHYST_BLOCK, "amethyst_block", "purple_stained_glass", "amethyst_block",
                "skywars.wins", 40, "Nyerj 40 meccset"));
        add(new Cage("nether", "&4&lNether", Material.CRIMSON_NYLIUM, "crimson_nylium", "red_stained_glass", "shroomlight",
                "skywars.games", 150, "Játssz le 150 meccset"));
        add(new Cage("end", "&5&lEnd", Material.END_STONE_BRICKS, "end_stone_bricks", "purple_stained_glass", "purpur_block",
                "skywars.wins", 60, "Nyerj 60 meccset"));
        add(new Cage("szivarvany", "&c&lS&6&lz&e&li&a&lv&b&lá&9&lr&d&lv&c&lá&6&ln&e&ly", Material.WHITE_STAINED_GLASS, "white_concrete", RAINBOW, "white_concrete",
                "skywars.best_streak", 5, "Nyerj 5 meccset egymás után"));
        add(new Cage("redstone", "&c&lRedstone", Material.REDSTONE_BLOCK, "redstone_block", "red_stained_glass", "redstone_lamp[lit=true]",
                "skywars.best_streak", 8, "Nyerj 8 meccset egymás után"));
        add(new Cage("gyemant", "&3&lGyémánt", Material.DIAMOND_BLOCK, "diamond_block", "cyan_stained_glass", "diamond_block",
                "skywars.wins", 100, "Nyerj 100 meccset"));
        add(new Cage("obszidian", "&5&lObszidián", Material.CRYING_OBSIDIAN, "crying_obsidian", "black_stained_glass", "obsidian",
                "skywars.kills", 400, "Ölj meg 400 játékost"));
        add(new Cage("jegkiraly", "&b&lJég&f&lkirály", Material.BEACON, "sea_lantern", "light_blue_stained_glass", "beacon",
                "skywars.wins", 200, "Nyerj 200 meccset"));
    }

    private void add(Cage cage) {
        cages.put(cage.id, cage);
    }

    public Map<String, Cage> all() {
        return cages;
    }

    public boolean unlocked(Player p, Cage cage) {
        if (cage.free() || p.hasPermission("icemine.skywars.cage.all") || p.hasPermission("icemine.skywars.cage." + cage.id)) {
            return true;
        }
        PlayerProfile pr = plugin.core().profiles().profile(p.getUniqueId());
        return pr != null && pr.stat(cage.stat) >= cage.need;
    }

    private long progress(Player p, Cage cage) {
        PlayerProfile pr = plugin.core().profiles().profile(p.getUniqueId());
        return pr == null || cage.stat == null ? 0 : pr.stat(cage.stat);
    }

    /** A játékos választott ketrece (ha közben elveszett a feltétel, az alap). */
    public Cage selected(Player p) {
        PlayerProfile pr = plugin.core().profiles().profile(p.getUniqueId());
        Cage cage = pr == null ? null : cages.get(pr.setting(SETTING, "alap"));
        return cage != null && unlocked(p, cage) ? cage : cages.get("alap");
    }

    public void menu(Player p) {
        Menu menu = new Menu("skywars-cages", 6, Text.color("&b&lSkyWars &8» &fKetrecek"));
        menu.frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        Cage current = selected(p);
        int i = 0;
        int open = 0;
        for (Cage cage : cages.values()) {
            if (i >= slots.length) {
                break;
            }
            boolean has = unlocked(p, cage);
            if (has) {
                open++;
            }
            boolean chosen = cage.id.equals(current.id);
            List<String> lore = new ArrayList<>();
            lore.add("&8━━━━━━━━━━━━━━━━━━");
            if (cage.free()) {
                lore.add("&7" + cage.task);
            } else {
                long have = Math.min(progress(p, cage), cage.need);
                lore.add("&7Feladat: &f" + cage.task);
                lore.add("&7Haladás: " + (has ? "&a" : "&e") + have + "&7/&f" + cage.need + " " + bar(have, cage.need));
            }
            lore.add("");
            lore.add(chosen ? "&a✔ Ezt használod" : has ? "&e» Kattints a kiválasztáshoz" : "&c✖ Még zárolva");
            Items item = Items.of(has ? cage.icon : Material.GRAY_DYE).name(cage.name).lore(lore).hideAll();
            if (chosen) {
                item.glow();
            }
            menu.set(slots[i++], item.build(), c -> {
                if (!unlocked(c, cage)) {
                    c.playSound(c.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 1F);
                    c.sendMessage(Text.color("&bSkyWars &8• &cEz a ketrec még zárolva van. Feladat: &f" + cage.task));
                    return;
                }
                PlayerProfile pr = plugin.core().profiles().profile(c.getUniqueId());
                if (pr != null) {
                    pr.put(SETTING, cage.id);
                }
                c.playSound(c.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1F, 1.4F);
                c.sendMessage(Text.color("&bSkyWars &8• &7Kiválasztott ketrec: " + cage.name));
                SwArena arena = plugin.arenaOf(c);
                if (arena != null) {
                    arena.refreshCage(c);
                    c.closeInventory();
                    return;
                }
                menu(c);
            });
        }
        menu.set(49, Items.of(Material.BOOK).name("&e&lFeloldva: &f" + open + "&7/&f" + cages.size())
                .lore(List.of("&7A ketrecet a SkyWars meccseiddel", "&7oldhatod fel: ölések, győzelmek,", "&7meccsek, győzelmi sorozat.")).build());
        menu.open(p);
    }

    private static String bar(long have, long need) {
        int full = (int) Math.round(10.0 * have / Math.max(1, need));
        return "&a" + "▮".repeat(full) + "&8" + "▮".repeat(10 - full);
    }

    /** A szivárvány ketrec üvegszíne egy adott helyen. */
    static String rainbow(int dx, int dy, int dz) {
        int i = Math.floorMod(dx * 3 + dz * 5 + dy, RAINBOW_GLASS.length);
        return RAINBOW_GLASS[i];
    }
}
