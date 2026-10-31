package hu.icemine.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ForeignMessagesTest {

    @Test
    void essentialsGetsHungarianAndIceMineColours() {
        String original = "#locale: en\nmessage-colors:\n  primary: '#ffaa00'\n  secondary: '#ff5555'\n";
        String patched = ForeignMessages.patchEssentials(original);
        assertTrue(patched.contains("\nlocale: hu\n") || patched.startsWith("locale: hu\n"));
        assertTrue(patched.contains("primary: '#aaaaaa'"));
        assertTrue(patched.contains("secondary: '#ffffff'"));
    }

    @Test
    void customEssentialsSettingsStay() {
        String original = "locale: de\nmessage-colors:\n  primary: '#123456'\n";
        assertEquals(original, ForeignMessages.patchEssentials(original));
    }

    @Test
    void canBeSwitchedOff() throws IOException {
        Path dir = Files.createTempDirectory("icemine");
        Path config = dir.resolve("config.yml");
        assertTrue(ForeignMessages.enabled(config));
        Files.writeString(config, "valami: 1\nkulso-uzenetek: false\n");
        assertFalse(ForeignMessages.enabled(config));
    }

    @Test
    void writesOnceAndKeepsBackup() throws IOException {
        Path plugins = Files.createTempDirectory("plugins");
        Files.createDirectories(plugins.resolve("AuthMe/messages"));
        Files.writeString(plugins.resolve("AuthMe/messages/messages_hu.yml"), "régi");
        List<String> first = ForeignMessages.run(plugins.toFile(), "IceMine",
                name -> name.startsWith("kulso/AuthMe") ? new ByteArrayInputStream("új".getBytes(StandardCharsets.UTF_8)) : null,
                Logger.getLogger("teszt"));
        assertEquals(List.of("AuthMe/messages/messages_hu.yml"), first);
        assertEquals("új", Files.readString(plugins.resolve("AuthMe/messages/messages_hu.yml")));
        assertEquals("régi", Files.readString(plugins.resolve("AuthMe/messages/messages_hu.yml.regi")));
        Files.writeString(plugins.resolve("AuthMe/messages/messages_hu.yml"), "saját");
        List<String> second = ForeignMessages.run(plugins.toFile(), "IceMine",
                name -> new ByteArrayInputStream("új".getBytes(StandardCharsets.UTF_8)), Logger.getLogger("teszt"));
        assertTrue(second.isEmpty());
        assertEquals("saját", Files.readString(plugins.resolve("AuthMe/messages/messages_hu.yml")));
    }
}
