package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.text.Messages;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DebugFormatTest {
    @ParameterizedTest @ValueSource(strings={"messages.yml", "messages_en.yml"})
    void oneLocalizedLineIncludesAllFieldsAndTreatsNamesAsData(String catalogue) {
        var messages=messages(catalogue);
        var snapshot=new DebugView.Snapshot(Component.text("<red>Custodio"), Component.text("Ana"), false,
                "paused", "ability", Component.text("Carga"), Component.text("Jaula"), Component.text("Ocupado"),
                List.of(Component.text("Críticos"), Component.text("Escudo")));
        var text=PlainTextComponentSerializer.plainText().serialize(DebugView.format(messages,snapshot));
        for(var field:List.of("<red>Custodio", "Ana", "Carga", "Jaula", "Ocupado", "Críticos", "Escudo"))
            assertTrue(text.contains(field),text);
        assertTrue(text.contains(catalogue.equals("messages.yml")?"IA: no":"AI: no"),text);
        assertTrue(text.contains(catalogue.equals("messages.yml")?"pausa":"paused"),text);
        assertFalse(text.contains("\n"));
    }
    static Messages messages(String catalogue) {
        var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(
                DebugFormatTest.class.getResourceAsStream("/"+catalogue),StandardCharsets.UTF_8));
        var messages=new Messages();messages.load(yaml,"");return messages;
    }
}
