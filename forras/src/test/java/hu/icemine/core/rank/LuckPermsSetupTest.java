package hu.icemine.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LuckPermsSetupTest {

    private static YamlConfiguration shipped() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(LuckPermsSetupTest.class.getResourceAsStream("/ranks.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void everyRankFromTheListIsCreated() {
        YamlConfiguration config = shipped();
        Set<String> groups = LuckPermsSetup.groups(config);
        for (String group : new String[]{"default", "bronz", "wizard", "iceking", "yeti", "media", "epitesz", "jnmoderator", "moderator",
                "srmoderator", "admin", "sradmin", "fejleszto", "manager", "tulajdonos"}) {
            assertTrue(groups.contains(group), group);
        }
        List<String> commands = LuckPermsSetup.commands(config, new HashSet<>());
        assertFalse(commands.contains("lp creategroup default"), "a default csoport már létezik");
        int lastCreate = -1;
        int firstSetup = Integer.MAX_VALUE;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).startsWith("lp creategroup")) {
                lastCreate = i;
            } else {
                firstSetup = Math.min(firstSetup, i);
            }
        }
        assertTrue(lastCreate < firstSetup, "előbb minden csoport létrejön, utána jönnek a szülők");
        assertTrue(commands.contains("lp group iceking meta setprefix 30 \"&b&lICE&f&lKING &b\""));
        assertTrue(commands.contains("lp group fejleszto setdisplayname \"Fejlesztő\""));
        assertTrue(commands.contains("lp group yeti parent add iceking"));
        assertTrue(commands.contains("lp group tulajdonos permission set * true"));
        assertTrue(commands.contains("lp group default setweight 0"));
    }

    @Test
    void doneGroupsAreLeftAlone() {
        List<String> commands = LuckPermsSetup.commands(shipped(), Set.of("bronz", "default"));
        for (String command : commands) {
            assertFalse(command.equals("lp creategroup bronz") || command.startsWith("lp group bronz "), command);
            assertFalse(command.startsWith("lp group default "), command);
        }
        assertTrue(commands.contains("lp group wizard parent add bronz"));
    }

    @Test
    void ranksAreOrderedByWeight() {
        YamlConfiguration config = shipped();
        int previous = -1;
        for (String id : config.getConfigurationSection("ranks").getKeys(false)) {
            int weight = config.getInt("ranks." + id + ".luckperms.weight");
            assertTrue(weight > previous, id);
            previous = weight;
        }
        assertEquals(15, config.getConfigurationSection("ranks").getKeys(false).size());
    }
}
