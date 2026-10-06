package dev.dasan.customdungeons.config;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigLoaderTest {
    @Test void missingValuesUseDefaults() {
        var c = new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration());
        assertEquals(3,c.defaults().lives()); assertEquals(50,c.limits().maxAliveMobsPerSession());
        assertEquals("&8[&6CustomDungeons&8] ",c.prefix()); assertEquals(12,c.armorCapable().size());
        assertEquals("sqlite",c.database().type()); assertEquals(300,c.liveTestMaxSeconds());
    }
    @Test void invalidValuesDefaultAndWarnWithoutSecrets() {
        var warnings = new ArrayList<String>(); var y = new YamlConfiguration();
        y.set("database.port",-1); y.set("database.password",List.of("secret"));
        y.set("dungeon-defaults.min-players",5); y.set("dungeon-defaults.max-players",2);
        y.set("performance.particle-density",Double.NaN); y.set("door-material","AIR");
        y.set("language","xx"); y.set("dungeon-defaults.keep-inventory","yes");
        var c = new ConfigLoader(warnings::add,material->material == org.bukkit.Material.IRON_BLOCK).load(y);
        assertEquals(3306,c.database().port()); assertEquals("",c.database().password());
        assertEquals(0,c.defaults().maxPlayers()); assertEquals(1,c.limits().particleDensity());
        assertEquals("IRON_BLOCK",c.doorMaterial().name()); assertEquals("es",c.language());
        assertFalse(c.defaults().keepInventory()); assertEquals(7,warnings.size());
        assertTrue(warnings.stream().noneMatch(w->w.contains("secret")));
    }
    @Test void configuredValuesAreLoaded() throws Exception {
        var y = new YamlConfiguration(); y.loadFromString("language: en\ncommand-aliases: [cd]\narmor-capable-mobs: [minecraft:zombie]\nmusic-lengths:\n  custom:theme: 1200\n");
        var c = new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(y); assertEquals("en",c.language());
        assertEquals(List.of("cd"),c.commandAliases()); assertEquals(1,c.armorCapable().size());
        assertEquals(1200,c.musicLengthTicks().get("custom:theme"));
    }
    @Test void localizedMessageDefaultsPreserveOverrides(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("messages_en.yml"),"plugin:\n  enabled: Custom text\n");
        var messages = DefinitionStore.loadMessages(directory,"en",path->fail(path));
        assertEquals("Custom text",messages.getString("plugin.enabled"));
        assertTrue(messages.getString("validation.lives").contains("Lives"));
        assertTrue(DefinitionStore.loadMessages(directory,"es",path->fail(path)).getString("validation.lives").contains("vidas"));
    }
    @Test void invalidMessageYamlFallsBackWithoutSourceSnippet(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.writeString(directory.resolve("messages.yml"),"plugin: [PRIVATE_VALUE\n");
        var warnings = new ArrayList<String>(); var messages = DefinitionStore.loadMessages(directory,"es",warnings::add);
        assertNotNull(messages.getString("plugin.enabled")); assertEquals(1,warnings.size()); assertFalse(warnings.getFirst().contains("PRIVATE_VALUE"));
    }
    @Test void bundledConfigMatchesDefaults() throws Exception {
        var y = new YamlConfiguration(); try(var in = getClass().getResourceAsStream("/config.yml")) {
            y.load(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration()),new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(y));
    }
}
