package dev.dasan.customdungeons.config;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigLoaderTest {
    @Test void respawnWorldLoadsSeparatelyWithoutChangingTheT01Contract() {
        var warnings=new ArrayList<String>();var loader=new ConfigLoader(warnings::add);
        var yaml=new YamlConfiguration();assertEquals("",loader.loadRespawnWorld(yaml));
        yaml.set("respawn-world","  primary  ");assertEquals("primary",loader.loadRespawnWorld(yaml));
        yaml.set("respawn-world",17);assertEquals("",loader.loadRespawnWorld(yaml));
        assertEquals(List.of("respawn-world"),warnings);
    }
    @Test void missingValuesUseDefaults() {
        var c = new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(new YamlConfiguration());
        assertEquals(3,c.defaults().lives()); assertEquals(50,c.limits().maxAliveMobsPerSession());
        assertEquals("&8[&6CustomDungeons&8] ",c.prefix()); assertEquals(12,c.armorCapable().size());
        assertEquals("sqlite",c.database().type()); assertEquals(0,c.liveTestMaxSeconds());
    }
    @Test void retiredLiveTestLimitIsIgnoredSilentlyRegardlessOfItsValue() {
        var loader=new ConfigLoader(path->fail("Unexpected warning: "+path),material->material == org.bukkit.Material.IRON_BLOCK);
        var defaults=loader.load(new YamlConfiguration());
        for(Object legacy:List.of(1,300,Integer.MAX_VALUE,-1,"invalid",List.of("invalid"))) {
            var yaml=new YamlConfiguration();
            yaml.set("live-test.max-seconds",legacy);
            assertEquals(defaults,loader.load(yaml));
        }
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
        var y = new YamlConfiguration(); y.loadFromString("language: en\ncommand-aliases: [cd]\narmor-capable-mobs: [minecraft:zombie]\nmusic-lengths:\n  - key: custom:theme\n    ticks: 1200\n");
        var c = new ConfigLoader(path->{},material->material == org.bukkit.Material.IRON_BLOCK).load(y); assertEquals("en",c.language());
        assertEquals(List.of("cd"),c.commandAliases()); assertEquals(1,c.armorCapable().size());
        assertEquals(1200,c.musicLengthTicks().get("custom:theme"));
    }
    @Test void musicKeysWithDotsSurviveYamlSaveAndReload() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                music-lengths:
                  - key: minecraft:music_disc.cat
                    ticks: 3700
                """);
        var reloaded = new YamlConfiguration(); reloaded.loadFromString(yaml.saveToString());
        var config = new ConfigLoader(path->fail(path),material->material == org.bukkit.Material.IRON_BLOCK).load(reloaded);
        assertEquals(Map.of("minecraft:music_disc.cat",3700),config.musicLengthTicks());
    }
    @Test void entityHeightsLoadDefaultsOverridesAndAdditionalTypesWithoutChangingPluginConfig() {
        var loader=new ConfigLoader(path->fail(path),material->material == org.bukkit.Material.IRON_BLOCK);
        var yaml=new YamlConfiguration();
        var defaults=loader.loadEntityHeights(yaml);
        assertEquals(2.9,defaults.height(org.bukkit.entity.EntityType.WARDEN));
        assertTrue(Double.isNaN(defaults.height(org.bukkit.entity.EntityType.SLIME)));
        yaml.set("entity-heights.warden",.5);
        yaml.set("entity-heights.minecraft:slime",2);
        var configured=loader.loadEntityHeights(yaml);
        assertEquals(.5,configured.height(org.bukkit.entity.EntityType.WARDEN));
        assertEquals(2,configured.height(org.bukkit.entity.EntityType.SLIME));
        assertEquals(4,configured.height(org.bukkit.entity.EntityType.GHAST));
        assertThrows(UnsupportedOperationException.class,()->configured.values().clear());
        yaml.set("entity-heights.warden",9);
        assertEquals(.5,configured.height(org.bukkit.entity.EntityType.WARDEN),"Snapshots must not follow later YAML edits");
    }
    @Test void invalidEntityHeightsWarnOnlyPathsAndFallBackToBundledValues() {
        var warnings=new ArrayList<String>();
        var loader=new ConfigLoader(warnings::add,material->material == org.bukkit.Material.IRON_BLOCK);
        for(Object invalid:List.of(-1,0,Double.NaN,Double.POSITIVE_INFINITY,"PRIVATE_VALUE")) {
            var yaml=new YamlConfiguration(); yaml.set("entity-heights.warden",invalid);
            assertEquals(2.9,loader.loadEntityHeights(yaml).height(org.bukkit.entity.EntityType.WARDEN));
            assertEquals("entity-heights.warden",warnings.removeLast());
        }
        var yaml=new YamlConfiguration(); yaml.set("entity-heights.UNKNOWN",3);
        yaml.set("entity-heights.ARROW",3);
        var configured=loader.loadEntityHeights(yaml);
        assertEquals(List.of("entity-heights.UNKNOWN","entity-heights.ARROW"),warnings);
        assertFalse(configured.values().containsKey(org.bukkit.entity.EntityType.ARROW));
        yaml.set("entity-heights","PRIVATE_VALUE");
        assertEquals(2.9,loader.loadEntityHeights(yaml).height(org.bukkit.entity.EntityType.WARDEN));
        assertEquals("entity-heights",warnings.getLast());
        assertTrue(warnings.stream().noneMatch(w->w.contains("PRIVATE_VALUE")));
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
