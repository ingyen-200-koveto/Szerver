package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ContactBook {

    public static final class Contact {
        public final String label;
        public final String value;
        public final boolean email;

        Contact(String label, String value, boolean email) {
            this.label = label;
            this.value = value;
            this.email = email;
        }
    }

    private final IceMineCore core;
    private final List<Contact> contacts = new ArrayList<>();
    private String title = "Kapcsolatok";
    private String intro = "";
    private String footer = "";
    private String hover = "";
    private String copyHover = "";

    public ContactBook(IceMineCore core) {
        this.core = core;
    }

    public void load(YamlConfiguration config, List<String> problems) {
        contacts.clear();
        title = config.getString("book-title", "&b&lIce&f&lMine &f| &bKapcsolatok");
        intro = config.getString("intro", "");
        footer = config.getString("footer", "");
        hover = config.getString("hover-open", "&7Kattints a megnyitáshoz");
        copyHover = config.getString("hover-copy", "&7Kattints a másoláshoz");
        add(config, "discord-url", "label.discord", "Discord", false, problems);
        add(config, "website-url", "label.website", "Weboldal", false, problems);
        add(config, "youtube-url", "label.youtube", "YouTube", false, problems);
        add(config, "tiktok-url", "label.tiktok", "TikTok", false, problems);
        add(config, "instagram-url", "label.instagram", "Instagram", false, problems);
        add(config, "contact-email", "label.email", "E-mail", true, problems);
        for (Map<?, ?> extra : config.getMapList("extra")) {
            Object name = extra.get("name");
            Object url = extra.get("url");
            if (name != null && url != null && !url.toString().isBlank()) {
                contacts.add(new Contact(name.toString(), url.toString().trim(), false));
            }
        }
    }

    private void add(YamlConfiguration config, String key, String labelKey, String fallback, boolean email, List<String> problems) {
        String value = config.getString(key, "");
        if (value == null || value.isBlank()) {
            return;
        }
        value = value.trim();
        if (!email && !value.startsWith("https://") && !value.startsWith("http://")) {
            problems.add("contacts.yml -> " + key + ": a linknek http:// vagy https:// kezdetűnek kell lennie, kihagyom");
            return;
        }
        contacts.add(new Contact(config.getString(labelKey, fallback), value, email));
    }

    public List<Contact> contacts() {
        return contacts;
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(Text.color(text));
    }

    private Component entry(Contact contact, String color) {
        Component line = legacy(color + "▶ " + contact.label);
        if (contact.email) {
            return line.clickEvent(ClickEvent.copyToClipboard(contact.value)).hoverEvent(HoverEvent.showText(legacy(copyHover + "\n&f" + contact.value)));
        }
        return line.clickEvent(ClickEvent.openUrl(contact.value)).hoverEvent(HoverEvent.showText(legacy(hover + "\n&f" + contact.value)));
    }

    public static boolean bedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0L;
    }

    public void open(Player player) {
        chat(player);
    }

    public void chat(Player player) {
        player.sendMessage(Text.color("&8&m                                        "));
        player.sendMessage(core.messages().tagged(" " + title));
        if (!intro.isBlank()) {
            player.sendMessage(Text.color(" " + intro.replace("&8", "&7")));
        }
        player.sendMessage("");
        if (contacts.isEmpty()) {
            player.sendMessage(core.messages().get("contacts.empty"));
        }
        for (Contact contact : contacts) {
            Component line = legacy(" ").append(entry(contact, "&b")).append(legacy(" &8- &7" + contact.value));
            player.sendMessage(line);
        }
        if (!footer.isBlank()) {
            player.sendMessage("");
            player.sendMessage(Text.color(" " + footer.replace("&8", "&7")));
        }
        player.sendMessage(Text.color("&8&m                                        "));
    }
}
