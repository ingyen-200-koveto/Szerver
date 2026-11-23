package hu.icemine.core.duels;

import hu.icemine.core.util.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Saját építésű Duels pályák felvétele: /duels palya ...
 * A pályát a Duels világban kell megépíteni (kreatívban, icemine.admin.build joggal), utána:
 * uj -> piros, kek (kezdőpontok) -> sarok1, sarok2 (határ) -> [nev, ikon] -> kesz.
 */
final class CustomArenas {

    private static final class Piszkozat {
        String id;
        String type = "classic";
        String name;
        Material icon = Material.GRASS_BLOCK;
        Location red;
        Location blue;
        Location c1;
        Location c2;
    }

    private final Duels duels;
    private final Map<UUID, Piszkozat> drafts = new HashMap<>();

    CustomArenas(Duels duels) {
        this.duels = duels;
    }

    private void say(Player p, String text) {
        duels.core().messages().sendTagged(p, text);
    }

    static Location parse(World world, String text) {
        if (world == null || text == null) {
            return null;
        }
        String[] p = text.trim().split("\\s+");
        if (p.length < 3) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    p.length > 3 ? Float.parseFloat(p[3]) : 0F, p.length > 4 ? Float.parseFloat(p[4]) : 0F);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(Location l) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f", l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private static String pos(Location l) {
        return l == null ? "&cnincs" : "&f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    void command(Player p, String[] args) {
        if (!p.hasPermission("icemine.admin.duels")) {
            duels.core().messages().send(p, "general.no-permission");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        Piszkozat d = drafts.get(p.getUniqueId());
        switch (sub) {
            case "uj", "new" -> {
                if (args.length < 3) {
                    say(p, "&7Használat: &f/duels palya uj <azonosító> [típus]&7 (típus: classic, sumo, ... alapból classic)");
                    return;
                }
                if (p.getWorld() != duels.duelWorld()) {
                    say(p, "&cA pályát a Duels világban kell megépíteni. Odajutsz (op): &f/execute in minecraft:"
                            + (duels.duelWorld() == null ? "duels" : duels.duelWorld().getName()) + " run tp @s 0 120 3000");
                    return;
                }
                d = new Piszkozat();
                d.id = args[2].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                d.name = "&f" + args[2];
                if (args.length > 3) {
                    d.type = args[3].toLowerCase(Locale.ROOT);
                }
                if (d.id.isEmpty()) {
                    say(p, "&cAz azonosító csak betű, szám, - és _ lehet.");
                    return;
                }
                drafts.put(p.getUniqueId(), d);
                say(p, "&aÚj pálya: &f" + d.id + " &7(" + d.type + "). Most állj a piros kezdőpontra: &f/duels palya piros");
            }
            case "piros", "red", "kek", "kék", "blue", "sarok1", "sarok2", "nev", "név", "name", "ikon", "icon", "kesz", "kész", "save", "info" -> {
                if (d == null) {
                    say(p, "&cElőbb kezdj egy új pályát: &f/duels palya uj <azonosító>");
                    return;
                }
                if (!sub.equals("info") && !sub.startsWith("nev") && !sub.equals("név") && !sub.equals("name") && p.getWorld() != duels.duelWorld()
                        && !sub.startsWith("ikon") && !sub.equals("icon")) {
                    say(p, "&cA Duels világban állj.");
                    return;
                }
                switch (sub) {
                    case "piros", "red" -> {
                        d.red = p.getLocation().clone();
                        say(p, "&aPiros kezdőpont megvan. &7Most a kék: &f/duels palya kek");
                    }
                    case "kek", "kék", "blue" -> {
                        d.blue = p.getLocation().clone();
                        say(p, "&aKék kezdőpont megvan. &7Most a pálya egyik sarka (lent): &f/duels palya sarok1");
                    }
                    case "sarok1" -> {
                        d.c1 = p.getLocation().getBlock().getLocation();
                        say(p, "&aElső sarok megvan. &7Most az átellenes sarok (fent): &f/duels palya sarok2");
                    }
                    case "sarok2" -> {
                        d.c2 = p.getLocation().getBlock().getLocation();
                        say(p, "&aMásodik sarok megvan. &7Név: &f/duels palya nev <név>&7, ikon: &f/duels palya ikon &7(a kezedben lévő tárgy), mentés: &f/duels palya kesz");
                    }
                    case "nev", "név", "name" -> {
                        if (args.length < 3) {
                            say(p, "&7Használat: &f/duels palya nev <név> &7(színkód mehet, pl. &f&&6Arany vár&7)");
                            return;
                        }
                        d.name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                        say(p, "&aNév: " + d.name);
                    }
                    case "ikon", "icon" -> {
                        ItemStack hand = p.getInventory().getItemInMainHand();
                        if (hand.getType().isAir()) {
                            say(p, "&cVegyél a kezedbe egy tárgyat, az lesz az ikon.");
                            return;
                        }
                        d.icon = hand.getType();
                        say(p, "&aIkon: &f" + d.icon.name().toLowerCase(Locale.ROOT));
                    }
                    case "info" -> say(p, "&7Pálya &f" + d.id + " &7(" + d.type + ") " + d.name + "&7 | piros: " + pos(d.red) + " &7| kék: " + pos(d.blue)
                            + " &7| sarok1: " + pos(d.c1) + " &7| sarok2: " + pos(d.c2));
                    default -> save(p, d);
                }
            }
            case "lista", "list" -> {
                ConfigurationSection c = config().getConfigurationSection("custom-arenas");
                if (c == null || c.getKeys(false).isEmpty()) {
                    say(p, "&7Még nincs saját pálya. Új: &f/duels palya uj <azonosító>");
                    return;
                }
                say(p, "&7Saját pályák:");
                for (String id : c.getKeys(false)) {
                    p.sendMessage(Text.color("  &8- &f" + id + " &7(" + c.getString(id + ".type") + ") " + c.getString(id + ".name", id)));
                }
            }
            case "torol", "töröl", "delete" -> {
                if (args.length < 3) {
                    say(p, "&7Használat: &f/duels palya torol <azonosító>");
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!config().isSet("custom-arenas." + id)) {
                    say(p, "&cNincs ilyen pálya: &f" + id);
                    return;
                }
                if (!duels.removeCustom(id)) {
                    say(p, "&cA pályán most meccs megy, próbáld később.");
                    return;
                }
                config().set("custom-arenas." + id, null);
                duels.core().configs().save(Duels.FILE);
                say(p, "&aTörölve: &f" + id + "&7 (a megépített blokkok maradnak a világban).");
            }
            case "tp" -> {
                if (args.length < 3) {
                    say(p, "&7Használat: &f/duels palya tp <azonosító>");
                    return;
                }
                Location red = parse(duels.duelWorld(), config().getString("custom-arenas." + args[2].toLowerCase(Locale.ROOT) + ".red"));
                if (red == null) {
                    say(p, "&cNincs ilyen pálya.");
                    return;
                }
                duels.core().guard().allowNext(p);
                p.teleport(red);
            }
            default -> {
                for (String line : List.of(
                        "&b&lSaját Duels pálya &7(a Duels világban építsd meg kreatívban)",
                        "&f/duels palya uj <azonosító> [típus] &7- kezdés (típus: classic, sumo...)",
                        "&f/duels palya piros &7| &f/duels palya kek &7- a két kezdőpont (ahol állsz, arra néz)",
                        "&f/duels palya sarok1 &7| &f/duels palya sarok2 &7- a pálya két átellenes sarka",
                        "&f/duels palya nev <név> &7| &f/duels palya ikon &7- név és ikon (kézben lévő tárgy)",
                        "&f/duels palya kesz &7- mentés, utána megjelenik a pályaválasztóban",
                        "&f/duels palya lista &7| &f/duels palya tp <id> &7| &f/duels palya torol <id>",
                        "&7Építős módokhoz (BuildUHC, Bridge) a saját pálya nem használható.")) {
                    p.sendMessage(Text.color(line));
                }
            }
        }
    }

    private YamlConfiguration config() {
        return duels.core().configs().get(Duels.FILE);
    }

    private void save(Player p, Piszkozat d) {
        if (d.red == null || d.blue == null || d.c1 == null || d.c2 == null) {
            say(p, "&cMég hiányzik valami: &f/duels palya info");
            return;
        }
        int[] b = {Math.min(d.c1.getBlockX(), d.c2.getBlockX()), Math.min(d.c1.getBlockY(), d.c2.getBlockY()), Math.min(d.c1.getBlockZ(), d.c2.getBlockZ()),
                Math.max(d.c1.getBlockX(), d.c2.getBlockX()), Math.max(d.c1.getBlockY(), d.c2.getBlockY()), Math.max(d.c1.getBlockZ(), d.c2.getBlockZ())};
        ArenaSlot probe = new ArenaSlot("custom-" + d.id, d.type, d.name, d.icon, duels.duelWorld(), d.red, d.blue, b);
        if (!probe.contains(d.red) || !probe.contains(d.blue)) {
            say(p, "&cA két kezdőpont legyen a két sarok közötti dobozon belül.");
            return;
        }
        boolean typeOk = false;
        for (DuelKit kit : duels.kits().values()) {
            if (kit.arena.equals(d.type) && !kit.usesBlocks()) {
                typeOk = true;
                break;
            }
        }
        if (!typeOk) {
            say(p, "&cA(z) &f" + d.type + " &ctípushoz nincs építés nélküli mód. Jó típus pl.: classic, sumo.");
            return;
        }
        String path = "custom-arenas." + d.id;
        YamlConfiguration c = config();
        c.set(path + ".type", d.type);
        c.set(path + ".name", d.name);
        c.set(path + ".icon", d.icon.name());
        c.set(path + ".red", format(d.red));
        c.set(path + ".blue", format(d.blue));
        c.set(path + ".bounds", List.of(b[0], b[1], b[2], b[3], b[4], b[5]));
        duels.core().configs().save(Duels.FILE);
        duels.addCustom(probe);
        drafts.remove(p.getUniqueId());
        say(p, "&aKész! &f" + d.name + " &amostantól választható a pályák között (" + d.type + " típusú módoknál).");
    }
}
