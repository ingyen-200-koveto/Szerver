package hu.icemine.core.bedwars;

import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** A bolt kínálata. Az árak a bedwars.yml prices részében felülírhatók. */
public final class Shop {

    public static final String[][] CATEGORIES = {
            {"gyors", "Gyors vásárlás", "NETHER_STAR"},
            {"blokk", "Blokkok", "TERRACOTTA"},
            {"kard", "Közelharc", "GOLDEN_SWORD"},
            {"pancel", "Páncél", "CHAINMAIL_BOOTS"},
            {"eszkoz", "Eszközök", "STONE_PICKAXE"},
            {"ij", "Távolsági", "BOW"},
            {"bajital", "Bájitalok", "BREWING_STAND"},
            {"hasznos", "Hasznos cuccok", "TNT"}
    };
    public static final int[] TOOL_PRICE = {10, 10, 3, 6};
    public static final Generator.Type[] TOOL_CURRENCY = {Generator.Type.IRON, Generator.Type.IRON, Generator.Type.GOLD, Generator.Type.GOLD};
    public static final String[] PICKAXE_NAMES = {"Fa", "Vas", "Arany", "Gyémánt"};
    public static final String[] AXE_NAMES = {"Fa", "Kő", "Vas", "Gyémánt"};

    public enum Kind {
        ITEM, WOOL, TERRACOTTA, GLASS, SWORD, ARMOR, PICKAXE, AXE, SHEARS, POTION, FIREBALL
    }

    public static final class Offer {
        public final String id;
        public final String category;
        public final String name;
        public final String material;
        public final int amount;
        public final Kind kind;
        /** Páncélnál a szint (1 lánc, 2 vas, 3 gyémánt). */
        public final int value;
        public final String[] extra;
        public Generator.Type currency;
        public int price;

        Offer(String id, String category, String name, String material, int amount, Generator.Type currency, int price, Kind kind, int value,
              String... extra) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.material = material;
            this.amount = amount;
            this.currency = currency;
            this.price = price;
            this.kind = kind;
            this.value = value;
            this.extra = extra;
        }
    }

    private final Map<String, Offer> offers = new LinkedHashMap<>();
    private final List<String> quick = new ArrayList<>();

    public Shop() {
        reset();
    }

    private void add(Offer offer) {
        offers.put(offer.id, offer);
    }

    /** Az alap kínálat és árak. */
    public void reset() {
        offers.clear();
        quick.clear();
        Generator.Type iron = Generator.Type.IRON;
        Generator.Type gold = Generator.Type.GOLD;
        Generator.Type emerald = Generator.Type.EMERALD;
        add(new Offer("gyapju", "blokk", "Gyapjú", "WHITE_WOOL", 16, iron, 4, Kind.WOOL, 0, "Gyors építéshez, a csapatod színében."));
        add(new Offer("terrakotta", "blokk", "Terrakotta", "TERRACOTTA", 16, iron, 12, Kind.TERRACOTTA, 0, "Erősebb, mint a gyapjú."));
        add(new Offer("uveg", "blokk", "Robbanásbiztos üveg", "GLASS", 4, iron, 12, Kind.GLASS, 0, "A TNT és a tűzgolyó nem töri ki."));
        add(new Offer("endko", "blokk", "End kő", "END_STONE", 12, iron, 24, Kind.ITEM, 0, "Kibírja a robbanásokat."));
        add(new Offer("letra", "blokk", "Létra", "LADDER", 8, iron, 4, Kind.ITEM, 0, "Felfelé, gyorsan."));
        add(new Offer("deszka", "blokk", "Deszka", "OAK_PLANKS", 16, gold, 4, Kind.ITEM, 0, "Fejszével gyorsan kivágható."));
        add(new Offer("obszidian", "blokk", "Obszidián", "OBSIDIAN", 4, emerald, 4, Kind.ITEM, 0, "A legerősebb védelem az ágyad köré."));
        add(new Offer("kokard", "kard", "Kőkard", "STONE_SWORD", 1, iron, 10, Kind.SWORD, 0));
        add(new Offer("vaskard", "kard", "Vaskard", "IRON_SWORD", 1, gold, 7, Kind.SWORD, 0));
        add(new Offer("gyemantkard", "kard", "Gyémántkard", "DIAMOND_SWORD", 1, emerald, 4, Kind.SWORD, 0));
        add(new Offer("lokobot", "kard", "Hátralökő bot", "STICK", 1, gold, 5, Kind.ITEM, 0, "Hátralökés I", "enchant:knockback:1"));
        add(new Offer("lancpancel", "pancel", "Lánc páncél", "CHAINMAIL_BOOTS", 1, iron, 40, Kind.ARMOR, 1, "Lánc nadrág és csizma.", "Halál után is megmarad."));
        add(new Offer("vaspancel", "pancel", "Vas páncél", "IRON_BOOTS", 1, gold, 12, Kind.ARMOR, 2, "Vas nadrág és csizma.", "Halál után is megmarad."));
        add(new Offer("gyemantpancel", "pancel", "Gyémánt páncél", "DIAMOND_BOOTS", 1, emerald, 6, Kind.ARMOR, 3, "Gyémánt nadrág és csizma.", "Halál után is megmarad."));
        add(new Offer("csakany", "eszkoz", "Csákány", "WOODEN_PICKAXE", 1, iron, 10, Kind.PICKAXE, 0, "Halálkor egy szintet visszaesik."));
        add(new Offer("balta", "eszkoz", "Balta", "WOODEN_AXE", 1, iron, 10, Kind.AXE, 0, "Halálkor egy szintet visszaesik."));
        add(new Offer("ollo", "eszkoz", "Olló", "SHEARS", 1, iron, 20, Kind.SHEARS, 0, "Gyorsan vágja a gyapjút.", "Halál után is megmarad."));
        add(new Offer("nyil", "ij", "Nyíl", "ARROW", 6, gold, 2, Kind.ITEM, 0));
        add(new Offer("ij", "ij", "Íj", "BOW", 1, gold, 12, Kind.ITEM, 0));
        add(new Offer("erosij", "ij", "Íj (Erő I)", "BOW", 1, gold, 20, Kind.ITEM, 0, "enchant:power:1"));
        add(new Offer("legjobbij", "ij", "Íj (Erő I, Hátralökés I)", "BOW", 1, emerald, 6, Kind.ITEM, 0, "enchant:power:1", "enchant:punch:1"));
        add(new Offer("gyorsasag", "bajital", "Gyorsaság II (45 mp)", "POTION", 1, emerald, 1, Kind.POTION, 0, "potion:speed:2:45"));
        add(new Offer("ugras", "bajital", "Ugrás V (45 mp)", "POTION", 1, emerald, 1, Kind.POTION, 0, "potion:jump_boost:5:45"));
        add(new Offer("lathatatlansag", "bajital", "Láthatatlanság (30 mp)", "POTION", 1, emerald, 2, Kind.POTION, 0, "potion:invisibility:1:30"));
        add(new Offer("aranyalma", "hasznos", "Aranyalma", "GOLDEN_APPLE", 1, gold, 3, Kind.ITEM, 0, "Gyors gyógyulás harc közben."));
        add(new Offer("tuzgolyo", "hasznos", "Tűzgolyó", "FIRE_CHARGE", 1, iron, 40, Kind.FIREBALL, 0, "Jobb klikkel kilövöd.", "Hidakat és játékosokat is elsöpör."));
        add(new Offer("tnt", "hasznos", "TNT", "TNT", 1, gold, 8, Kind.ITEM, 0, "Lerakva azonnal begyullad."));
        add(new Offer("enderpearl", "hasznos", "Enderpearl", "ENDER_PEARL", 1, emerald, 4, Kind.ITEM, 0, "Teleport oda, ahova dobod."));
        add(new Offer("viz", "hasznos", "Vödör víz", "WATER_BUCKET", 1, gold, 3, Kind.ITEM, 0, "Lassítja a támadókat, eloltja a tüzet."));
        add(new Offer("szivacs", "hasznos", "Szivacs", "SPONGE", 4, gold, 3, Kind.ITEM, 0, "Felszívja a vizet."));
        quick.addAll(Arrays.asList("gyapju", "kokard", "lancpancel", "csakany", "ij", "gyorsasag", "tnt",
                "terrakotta", "vaskard", "vaspancel", "balta", "nyil", "ugras", "viz",
                "uveg", "gyemantkard", "gyemantpancel", "ollo", "tuzgolyo", "aranyalma", "enderpearl"));
    }

    public Map<String, Offer> offers() {
        return offers;
    }

    public List<String> quick() {
        return quick;
    }

    /** Árak a configból, pl. gyapju: "4 vas". */
    public List<String> load(ConfigurationSection prices) {
        reset();
        List<String> problems = new ArrayList<>();
        if (prices == null) {
            return problems;
        }
        for (String id : prices.getKeys(false)) {
            Offer offer = offers.get(id);
            if (offer == null) {
                problems.add("bedwars.yml -> prices." + id + ": nincs ilyen tárgy a boltban");
                continue;
            }
            String[] parts = prices.getString(id, "").trim().split("\\s+");
            Generator.Type currency = parts.length == 2 ? currency(parts[1]) : null;
            int price = -1;
            try {
                price = parts.length == 2 ? Integer.parseInt(parts[0]) : -1;
            } catch (NumberFormatException ignored) {
            }
            if (price <= 0 || currency == null) {
                problems.add("bedwars.yml -> prices." + id + ": rossz formátum, így kell: \"4 vas\"");
                continue;
            }
            offer.price = price;
            offer.currency = currency;
        }
        return problems;
    }

    public static Generator.Type currency(String text) {
        switch (TeamColor.simple(text)) {
            case "vas":
            case "iron":
                return Generator.Type.IRON;
            case "arany":
            case "gold":
                return Generator.Type.GOLD;
            case "gyemant":
            case "diamond":
                return Generator.Type.DIAMOND;
            case "smaragd":
            case "emerald":
                return Generator.Type.EMERALD;
            default:
                return null;
        }
    }

    public static String currencyColor(Generator.Type type) {
        switch (type) {
            case IRON:
                return "&f";
            case GOLD:
                return "&6";
            case DIAMOND:
                return "&b";
            default:
                return "&2";
        }
    }

    public static String currencyName(Generator.Type type) {
        switch (type) {
            case IRON:
                return "Vas";
            case GOLD:
                return "Arany";
            case DIAMOND:
                return "Gyémánt";
            default:
                return "Smaragd";
        }
    }

    /** Pl. "&612 Arany". */
    public static String priceText(Generator.Type type, int amount) {
        return currencyColor(type) + amount + " " + currencyName(type);
    }

    public Material display(Offer offer, BwPlayer player, TeamColor color) {
        switch (offer.kind) {
            case WOOL:
                return color.wool();
            case TERRACOTTA:
                return color.terracotta();
            case GLASS:
                return color.glass();
            case PICKAXE:
                return Material.valueOf(BwItems.PICKAXES[Math.min(3, player.pickaxe)]);
            case AXE:
                return Material.valueOf(BwItems.AXES[Math.min(3, player.axe)]);
            default:
                return Material.valueOf(offer.material);
        }
    }

    public ItemStack product(Offer offer, BwTeam team) {
        Material material = Material.valueOf(offer.material);
        switch (offer.kind) {
            case WOOL:
                return new ItemStack(team.color.wool(), offer.amount);
            case TERRACOTTA:
                return new ItemStack(team.color.terracotta(), offer.amount);
            case GLASS:
                return new ItemStack(team.color.glass(), offer.amount);
            case SWORD:
                return BwItems.sword(material, team);
            case POTION:
                return potion(offer);
            case FIREBALL:
                return BwItems.tagged(BwItems.item(Material.FIRE_CHARGE, offer.amount, "&6Tűzgolyó"), "fireball");
            default:
                ItemStack item = new ItemStack(material, offer.amount);
                for (String extra : offer.extra) {
                    if (extra.startsWith("enchant:")) {
                        String[] parts = extra.split(":");
                        BwItems.enchant(item, parts[1], Integer.parseInt(parts[2]));
                    }
                }
                if (material == Material.STICK || material == Material.BOW) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(Text.color("&f" + offer.name));
                        item.setItemMeta(meta);
                    }
                }
                return item;
        }
    }

    private static ItemStack potion(Offer offer) {
        ItemStack item = new ItemStack(Material.POTION);
        for (String extra : offer.extra) {
            if (!extra.startsWith("potion:")) {
                continue;
            }
            String[] parts = extra.split(":");
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[1].toLowerCase(Locale.ROOT)));
            if (type != null && item.getItemMeta() instanceof PotionMeta meta) {
                meta.addCustomEffect(new PotionEffect(type, Integer.parseInt(parts[3]) * 20, Integer.parseInt(parts[2]) - 1), true);
                meta.setColor(type.getColor());
                meta.setDisplayName(Text.color("&b" + offer.name));
                item.setItemMeta(meta);
            }
        }
        return item;
    }
}
