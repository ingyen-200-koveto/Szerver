package hu.icemine.core.rank;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.kitpvp.Kit;
import hu.icemine.core.menu.Menu;
import hu.icemine.core.util.Items;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

/**
 * Rangok: a LuckPerms csoportokhoz rendelt IceMine jutalmak (ranks.yml). A rang nevét és előtagját a LuckPerms adja,
 * itt csak az dől el, mit kap a játékos: kiteket, kozmetikákat, pénz szorzót és egyéb jogokat.
 * A jogokat egy saját PermissionAttachment adja, amit belépéskor és félpercenként frissítünk.
 */
public final class Ranks {

    public static final String KIT_PERMISSION = "icemine.kit.";
    public static final String COSMETIC_PERMISSION = "icemine.cosmetic.";

    public static final class Rank {
        public final String id;
        public final String group;
        public final String name;
        /** Az ikon anyagának neve; csak a menü nyitásakor alakítjuk Material-lá. */
        public final String icon;
        public final double multiplier;
        public final List<String> kits;
        public final List<String> cosmetics;
        public final List<String> permissions;
        public final List<String> perks;
        final int order;
        /** Ez a rang és az összes gyengébb együtt mit ad (az öröklés miatt). */
        final Set<String> granted = new LinkedHashSet<>();
        final Set<String> allKits = new LinkedHashSet<>();

        Rank(String id, String group, String name, String icon, double multiplier, List<String> kits, List<String> cosmetics,
                List<String> permissions, List<String> perks, int order) {
            this.id = id;
            this.group = group;
            this.name = name;
            this.icon = icon;
            this.multiplier = multiplier;
            this.kits = kits;
            this.cosmetics = cosmetics;
            this.permissions = permissions;
            this.perks = perks;
            this.order = order;
        }

        public Set<String> granted() {
            return Collections.unmodifiableSet(granted);
        }

        public Set<String> allKits() {
            return Collections.unmodifiableSet(allKits);
        }
    }

    private final IceMineCore core;
    private final List<Rank> ranks = new ArrayList<>();
    /** Kit azonosító -> a leggyengébb rang, amelyik megadja. Az alap rang kitjei nincsenek benne (azok ingyenesek). */
    private final Map<String, Rank> kitOwners = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Rank> current = new HashMap<>();
    private String storeUrl = "";

    public Ranks(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        ranks.clear();
        kitOwners.clear();
        storeUrl = config.getString("store-url", "");
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section == null) {
            problems.add("ranks.yml: hiányzik a ranks rész, csak az alap rang lesz");
        }
        int order = 0;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                String group = entry.getString("group", id).toLowerCase(Locale.ROOT).trim();
                if (!group.matches("[a-z0-9_\\-]+")) {
                    problems.add("ranks.yml -> ranks." + id + ".group: a LuckPerms csoport neve csak kisbetű, szám, _ és - lehet (" + group + ")");
                }
                double multiplier = Math.max(0.0, Math.min(10.0, entry.getDouble("coin-multiplier", 1.0)));
                ranks.add(new Rank(id.toLowerCase(Locale.ROOT), group, entry.getString("name", id),
                        entry.getString("icon", "PAPER"), multiplier,
                        lower(entry.getStringList("kits")), lower(entry.getStringList("cosmetics")), entry.getStringList("permissions"),
                        entry.getStringList("perks"), order++));
            }
        }
        if (ranks.isEmpty()) {
            ranks.add(new Rank("default", "default", "&7Játékos", "GRAY_DYE", 1.0, List.of(), List.of(), List.of(), List.of(), 0));
        }
        Set<String> granted = new LinkedHashSet<>();
        Set<String> kits = new LinkedHashSet<>();
        for (int i = 0; i < ranks.size(); i++) {
            Rank rank = ranks.get(i);
            for (String kit : rank.kits) {
                kits.add(kit);
                granted.add(KIT_PERMISSION + kit);
                if (i > 0 && !kitOwners.containsKey(kit) && !ranks.get(0).kits.contains(kit)) {
                    kitOwners.put(kit, rank);
                }
            }
            for (String cosmetic : rank.cosmetics) {
                granted.add(COSMETIC_PERMISSION + cosmetic);
            }
            for (String permission : rank.permissions) {
                if (permission != null && !permission.isBlank()) {
                    granted.add(permission.trim());
                }
            }
            rank.granted.addAll(granted);
            rank.allKits.addAll(kits);
        }
    }

    private static List<String> lower(List<String> values) {
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    public List<Rank> all() {
        return Collections.unmodifiableList(ranks);
    }

    public Rank base() {
        return ranks.get(0);
    }

    public Rank byId(String id) {
        for (Rank rank : ranks) {
            if (rank.id.equalsIgnoreCase(id) || rank.group.equalsIgnoreCase(id)) {
                return rank;
            }
        }
        return null;
    }

    /**
     * A játékos legerősebb rangja. A LuckPerms a csoporttagságot "group.&lt;név&gt;" jogként adja (az örökölt csoportokat is),
     * ezért az isPermissionSet-et nézzük: így egy OP nem lesz automatikusan tulajdonos csak azért, mert minden joga megvan.
     */
    public Rank resolve(Player player) {
        for (int i = ranks.size() - 1; i > 0; i--) {
            String node = "group." + ranks.get(i).group;
            if (player.isPermissionSet(node) && player.hasPermission(node)) {
                return ranks.get(i);
            }
        }
        return ranks.get(0);
    }

    public Rank of(Player player) {
        Rank rank = current.get(player.getUniqueId());
        return rank == null ? resolve(player) : rank;
    }

    public void apply(Player player) {
        Rank rank = resolve(player);
        Rank previous = current.put(player.getUniqueId(), rank);
        PermissionAttachment attachment = attachments.get(player.getUniqueId());
        if (attachment != null && previous == rank && attachment.getPermissions().keySet().equals(rank.granted)) {
            return;
        }
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // már nincs rajta (pl. újracsatlakozás után)
            }
        }
        attachment = player.addAttachment(core);
        for (String permission : rank.granted) {
            attachment.setPermission(permission, true);
        }
        attachments.put(player.getUniqueId(), attachment);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    public void clear(Player player) {
        current.remove(player.getUniqueId());
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // a játékos már kilépett, a Bukkit eldobta
            }
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        attachments.clear();
        current.clear();
    }

    /** A pénz szorzó (ölés, napi jutalom). */
    public double multiplier(Player player) {
        return of(player).multiplier;
    }

    public long boost(Player player, long amount) {
        if (amount <= 0) {
            return amount;
        }
        return Math.round(amount * multiplier(player));
    }

    /** A leggyengébb rang, amelyik megadja ezt a kitet, vagy null, ha ingyenes. */
    public Rank requiredFor(String kitId) {
        return kitId == null ? null : kitOwners.get(kitId.toLowerCase(Locale.ROOT));
    }

    /**
     * Használhatja-e a kitet. Ha a kitnek a configban saját joga van, az dönt. Ha egy rang adja, akkor az icemine.kit.&lt;id&gt; jog
     * (ezt a rang adja, de LuckPermsben kézzel is meg lehet adni). Ha egyik sem, a kit ingyenes.
     */
    public boolean canUse(Player player, Kit kit) {
        if (kit.permission != null && !kit.permission.isBlank()) {
            return player.hasPermission(kit.permission);
        }
        if (requiredFor(kit.id) != null) {
            return player.hasPermission(KIT_PERMISSION + kit.id);
        }
        return true;
    }

    public String storeUrl() {
        return storeUrl;
    }

    public void openMenu(Player viewer) {
        int rows = Math.max(3, Math.min(6, 2 + (ranks.size() + 6) / 7));
        Menu menu = new Menu("ranks", rows, core.messages().raw("rank.menu-title"));
        menu.frame(Material.CYAN_STAINED_GLASS_PANE, null);
        Rank mine = of(viewer);
        int index = 0;
        for (Rank rank : ranks) {
            int row = 1 + index / 7;
            if (row >= rows - 1) {
                break;
            }
            int slot = row * 9 + 1 + index % 7;
            index++;
            List<String> lore = new ArrayList<>();
            for (String perk : rank.perks) {
                lore.add(perk);
            }
            lore.add("");
            lore.add(core.messages().get("rank.multiplier", "%multiplier%", Text.decimal(rank.multiplier)));
            List<String> kitNames = new ArrayList<>(rank.allKits);
            lore.add(core.messages().get("rank.kits", "%kits%", kitNames.isEmpty() ? core.messages().raw("rank.none") : String.join(", ", kitNames)));
            lore.add("");
            if (rank == mine) {
                lore.add(core.messages().get("rank.yours"));
            } else if (rank.order < mine.order) {
                lore.add(core.messages().get("rank.have"));
            } else {
                lore.add(core.messages().get("rank.missing"));
            }
            Items item = Items.of(Items.material(rank.icon, Material.PAPER)).name(rank.name).lore(colored(lore)).hideAll();
            if (rank == mine) {
                item.glow();
            }
            menu.set(slot, item.build());
        }
        if (!storeUrl.isBlank()) {
            menu.set(rows * 9 - 5, Items.of(Material.GOLD_INGOT).name(core.messages().get("rank.store", "%url%", storeUrl)).build(), player -> {
                player.closeInventory();
                core.messages().send(player, "rank.store", "%url%", storeUrl);
            });
        }
        menu.open(viewer);
    }

    private static List<String> colored(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(Text.color(line));
        }
        return result;
    }

    /** Csak a teszteknek és a /icem rank konzol kimenetnek: rang azonosító -> amit ad. */
    public Map<String, Set<String>> grants() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Rank rank : ranks) {
            result.put(rank.id, rank.granted());
        }
        return result;
    }
}
