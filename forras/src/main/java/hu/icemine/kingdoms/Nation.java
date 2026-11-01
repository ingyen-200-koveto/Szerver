package hu.icemine.kingdoms;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Több királyság szövetsége. A főváros (capital) királyának joga van a nemzetet vezetni. */
public final class Nation {

    public final UUID id;
    public String name;
    public UUID capital;
    public long created;
    public final Set<UUID> kingdoms = new LinkedHashSet<>();
    public long bank;
    public long resourcePoints;
    public String description = "";
    public transient boolean dirty;

    public Nation(UUID id, String name, UUID capital, long created) {
        this.id = id;
        this.name = name;
        this.capital = capital;
        this.created = created;
        kingdoms.add(capital);
    }

    void write(ConfigurationSection section) {
        section.set("name", name);
        section.set("capital", capital.toString());
        section.set("created", created);
        List<String> list = new ArrayList<>();
        for (UUID kingdom : kingdoms) {
            list.add(kingdom.toString());
        }
        section.set("kingdoms", list);
        section.set("bank", bank);
        section.set("resource-points", resourcePoints);
        section.set("description", description);
    }

    static Nation read(UUID id, ConfigurationSection section) {
        Nation nation = new Nation(id, section.getString("name", "?"), UUID.fromString(section.getString("capital", "")), section.getLong("created"));
        for (String text : section.getStringList("kingdoms")) {
            try {
                nation.kingdoms.add(UUID.fromString(text));
            } catch (IllegalArgumentException ignored) {
                // sérült bejegyzés
            }
        }
        nation.bank = section.getLong("bank");
        nation.resourcePoints = section.getLong("resource-points");
        nation.description = section.getString("description", "");
        return nation;
    }
}
