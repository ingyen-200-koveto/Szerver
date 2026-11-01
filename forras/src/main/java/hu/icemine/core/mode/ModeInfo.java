package hu.icemine.core.mode;

import hu.icemine.core.config.Values;
import hu.icemine.core.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ModeInfo {

    public enum Status {
        OPEN,
        MAINTENANCE,
        CLOSED,
        /** Még készül: a választóban "Hamarosan" felirattal látszik, belépni nem lehet. */
        SOON
    }

    public enum Inventory {
        KIT,
        PERSISTENT,
        EXTERNAL
    }

    public final String id;
    public final String name;
    public final Material icon;
    /** Ha az ikon ezen a szerver verzión még nem létezik (pl. a buzogány 1.21 előtt), ezt mutatjuk helyette. */
    public final Material iconFallback;
    public final int slot;
    public final List<String> description;
    public Status status;
    public final String world;
    public final String server;
    public final boolean internal;
    public final boolean combatTag;
    public final boolean comboFly;
    public final Inventory inventory;
    public final boolean coreScoreboard;
    public final String color;

    private ModeInfo(String id, String name, Material icon, Material iconFallback, int slot, List<String> description, Status status, String world, String server,
                     boolean internal, boolean combatTag, boolean comboFly, Inventory inventory, boolean coreScoreboard, String color) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.iconFallback = iconFallback;
        this.slot = slot;
        this.description = description;
        this.status = status;
        this.world = world;
        this.server = server;
        this.internal = internal;
        this.combatTag = combatTag;
        this.comboFly = comboFly;
        this.inventory = inventory;
        this.coreScoreboard = coreScoreboard;
        this.color = color;
    }

    public static ModeInfo read(YamlConfiguration config, String id, boolean internal, List<String> problems) {
        Values values = new Values("modes.yml", config::get);
        String base = "modes." + id + ".";
        String statusText = values.choice(base + "status", "open", "open", "maintenance", "closed", "soon");
        Status status = Status.valueOf(statusText.toUpperCase(Locale.ROOT));
        String inventoryText = values.choice(base + "inventory", internal ? "kit" : "external", "kit", "persistent", "external");
        Material icon = Items.material(values.text(base + "icon", "COMPASS"), Material.COMPASS);
        Material fallback = Items.material(values.text(base + "icon-fallback", "COMPASS"), Material.COMPASS);
        List<String> description = new ArrayList<>(config.getStringList(base + "description"));
        ModeInfo info = new ModeInfo(id,
                values.text(base + "name", id),
                icon,
                fallback,
                values.integer(base + "slot", 10, 0, 44),
                description,
                status,
                values.text(base + "world", id),
                values.text(base + "server", ""),
                internal,
                values.bool(base + "combat-tag", false),
                values.bool(base + "combofly", false),
                Inventory.valueOf(inventoryText.toUpperCase(Locale.ROOT)),
                values.bool(base + "core-scoreboard", internal),
                values.text(base + "color", "&b"));
        problems.addAll(values.problems());
        return info;
    }

    /** Az ikon, amit a menüben mutatunk: ha a beállított tárgy ezen a verzión még kísérleti (nincs bekapcsolva), a tartalék. */
    public Material displayIcon(org.bukkit.World world) {
        try {
            return world == null || icon.isEnabledByFeature(world) ? icon : iconFallback;
        } catch (RuntimeException | LinkageError error) {
            return icon;
        }
    }

    public boolean usesProxy() {
        return server != null && !server.isBlank();
    }
}
