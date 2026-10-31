package hu.icemine.core.lobby;

import hu.icemine.core.IceMineCore;
import hu.icemine.core.config.Messages;
import hu.icemine.core.util.ClickText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class InfoTexts {

    private final Map<String, List<String>> texts = new LinkedHashMap<>();
    private String hoverRun = "&7Kattints: &f%value%";
    private String hoverUrl = "&7Kattints a megnyitáshoz";

    public void load(YamlConfiguration config, List<String> problems) {
        texts.clear();
        hoverRun = config.getString("info-hover.command", hoverRun);
        hoverUrl = config.getString("info-hover.link", hoverUrl);
        ConfigurationSection section = config.getConfigurationSection("info-texts");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            List<String> lines = section.getStringList(id);
            if (lines.isEmpty()) {
                problems.add("lobby.yml -> info-texts." + id + ": üres, kihagyom");
                continue;
            }
            texts.put(id.toLowerCase(Locale.ROOT), new ArrayList<>(lines));
        }
    }

    public boolean has(String id) {
        return id != null && texts.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public List<String> ids() {
        return new ArrayList<>(texts.keySet());
    }

    public boolean send(Player player, String id, java.util.function.UnaryOperator<String> placeholders) {
        List<String> lines = id == null ? null : texts.get(id.toLowerCase(Locale.ROOT));
        if (lines == null) {
            return false;
        }
        IceMineCore core = IceMineCore.get();
        String prefix = core == null || core.messages() == null ? Messages.DEFAULT_PREFIX : core.messages().prefix();
        for (String line : Messages.withHeader(lines, prefix)) {
            player.sendMessage(ClickText.line(placeholders == null ? line : placeholders.apply(line), hoverRun, hoverUrl));
        }
        return true;
    }
}
