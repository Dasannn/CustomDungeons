package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.text.Messages;
import java.nio.file.Path;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildToolsTest {
    @Test void nineToolsIntegratePressurePlatesAndEntranceDoor() {
        var yaml=YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var messages=new Messages();messages.load(yaml,"");var plain=PlainTextComponentSerializer.plainText();
        var names=List.of("1 · Varita de área","2 · Varita de sala","3 · Puerta","4 · Colocar spawner",
                "5 · Placas de presión","6 · Puntos","7 · Sala activa: Sala 2","8 · Deshacer","9 · Menú de la dungeon");
        for(int i=0;i<9;i++) {
            assertEquals(names.get(i),plain.serialize(BuildTools.name(messages,i,1)));
            assertFalse(BuildTools.lore(messages,i).isEmpty());
            assertTrue(yaml.isString("build.tool-"+(i+1)+".name"));assertTrue(yaml.isString("build.tool-"+(i+1)+".lore"));
        }
        assertEquals(Material.MAP,BuildTools.material(0));assertEquals(Material.STONE_PRESSURE_PLATE,BuildTools.material(4));
        assertEquals(Material.BLAZE_ROD,BuildTools.material(1));assertEquals(Material.AMETHYST_SHARD,BuildTools.material(2));
        assertEquals(Material.BREEZE_ROD,BuildTools.material(3));assertEquals(Material.ECHO_SHARD,BuildTools.material(5));
        assertFalse(java.util.stream.IntStream.range(0,9).boxed().flatMap(i->BuildTools.lore(messages,i).stream()).map(plain::serialize).anyMatch(s->s.contains("no disponible")));
        assertTrue(BuildTools.lore(messages,6).stream().map(plain::serialize).anyMatch(s->s.contains("Crear sala")));
    }
}
