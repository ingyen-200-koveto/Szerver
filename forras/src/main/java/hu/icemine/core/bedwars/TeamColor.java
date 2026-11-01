package hu.icemine.core.bedwars;

import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Material;

/** A BedWars csapatszínek. A 4 csapatos pályák az első négyet használják, a 8 csapatosak mindet. */
public enum TeamColor {
    PIROS("Piros", "&c", "P", "red", 0xB02E26),
    KEK("Kék", "&9", "K", "blue", 0x3C44AA),
    ZOLD("Zöld", "&a", "Z", "lime", 0x5EA816),
    SARGA("Sárga", "&e", "S", "yellow", 0xFED83D),
    TURKIZ("Türkiz", "&b", "T", "cyan", 0x169C9C),
    FEHER("Fehér", "&f", "F", "white", 0xF0F0F0),
    ROZSASZIN("Rózsaszín", "&d", "R", "pink", 0xF38BAA),
    SZURKE("Szürke", "&8", "Sz", "gray", 0x474F52);

    public final String display;
    public final String chat;
    public final String letter;
    public final String block;
    private final int rgb;

    TeamColor(String display, String chat, String letter, String block, int rgb) {
        this.display = display;
        this.chat = chat;
        this.letter = letter;
        this.block = block;
        this.rgb = rgb;
    }

    /** A bőrpáncél színe (csak itt jön létre a Bukkit Color, így a pálya építő tesztelhető szerver nélkül). */
    public Color leather() {
        return Color.fromRGB(rgb);
    }

    public String colored() {
        return chat + display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    private Material material(String suffix) {
        return Material.valueOf(block.toUpperCase(Locale.ROOT) + suffix);
    }

    public Material wool() {
        return material("_WOOL");
    }

    public Material terracotta() {
        return material("_TERRACOTTA");
    }

    public Material glass() {
        return material("_STAINED_GLASS");
    }

    public Material bed() {
        return material("_BED");
    }

    static String simple(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replace("é", "e").replace("á", "a").replace("ö", "o").replace("ü", "u")
                .replace("í", "i").replace("ó", "o").replace("ő", "o").replace("ű", "u").replace("ú", "u");
    }

    public static TeamColor parse(String text) {
        if (text == null) {
            return null;
        }
        String wanted = simple(text);
        for (TeamColor color : values()) {
            if (color.id().equals(wanted) || simple(color.display).equals(wanted)) {
                return color;
            }
        }
        return null;
    }
}
