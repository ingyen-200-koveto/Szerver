package hu.icemine.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClickTextTest {

    private static String kinds(String line) {
        StringBuilder out = new StringBuilder();
        for (ClickText.Part part : ClickText.split(line)) {
            if (!part.kind.isEmpty()) {
                out.append(part.kind).append('=').append(part.value).append(' ');
            }
        }
        return out.toString().trim();
    }

    @Test
    void findsCommandsAfterColorCodes() {
        assertEquals("run=/lobby", kinds(" &f/lobby &8- &7vissza a lobbyba"));
        assertEquals("run=/penz run=/napi", kinds("&f/penz &8| &f/napi"));
    }

    @Test
    void ignoresSlashesInsideWords() {
        assertEquals("", kinds("&71.18 - 1.20.x &7(Java és Bedrock)"));
        assertEquals("", kinds("és/vagy"));
    }

    @Test
    void linksAreClickable() {
        assertEquals("url=https://discord.gg/icemine", kinds(" &7Discord: &bhttps://discord.gg/icemine"));
    }

    @Test
    void markupWins() {
        assertEquals("suggest=/fizet", kinds(" &f/penz &8| [[&f/fizet|suggest:/fizet ]]").replace("run=/penz ", ""));
        assertEquals("url=https://discord.gg/icemine", kinds("szólj a [[&b&nDiscordon|url:https://discord.gg/icemine]]&7!"));
    }

    @Test
    void textIsKeptInOrder() {
        List<ClickText.Part> parts = ClickText.split("a [[b|run:/x]] c /d e");
        StringBuilder joined = new StringBuilder();
        for (ClickText.Part part : parts) {
            joined.append(part.text);
        }
        assertEquals("a b c /d e", joined.toString());
    }

    @Test
    void colorsCarryOver() {
        assertEquals("&b", ClickText.lastColors("&7szia &bvilág"));
        assertEquals("&b&l", ClickText.lastColors("&b&lIce"));
        assertEquals("", ClickText.lastColors("&rsemmi"));
        assertTrue(ClickText.line("&7a &f/lobby", "%value%", "%value%") != null);
    }
}
