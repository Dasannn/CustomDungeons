package dev.dasan.customdungeons.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {
    @TempDir Path directory;

    private YamlConfiguration yaml(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(text);
        return yaml;
    }

    @Test void oldDefaultsAreUpdatedAndMissingKeysAdded() throws Exception {
        var installed = yaml("tool: old\n");
        var result = ConfigMigration.merge(installed, yaml("version: 2\ntool: new\nadded: value\n"),
                List.of(yaml("tool: old\n")), true);
        assertEquals("new", installed.getString("tool"));
        assertEquals("value", installed.getString("added"));
        assertEquals(2, installed.getInt("version"));
        assertEquals(new ConfigMigration.Result(1, 1, 0, true), result);
    }

    @Test void personalizedValuesAndUnknownKeysArePreserved() throws Exception {
        var installed = yaml("tool: personal\nextra: keep\n");
        var result = ConfigMigration.merge(installed, yaml("version: 2\ntool: new\n"),
                List.of(yaml("tool: old\n")), true);
        assertEquals("personal", installed.getString("tool"));
        assertEquals("keep", installed.getString("extra"));
        assertEquals(1, result.preserved());
        assertEquals(0, result.updated());
    }

    @Test void equalVersionDoesNotUpdateOldTextOrWriteFile() throws Exception {
        var file = directory.resolve("messages.yml");
        String original = "# administrator comment\nversion: 2\ntool: old\n";
        Files.writeString(file, original);
        var result = ConfigMigration.migrate(file, yaml("version: 2\ntool: new\n"),
                List.of(yaml("tool: old\n")), true);
        assertFalse(result.changed());
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void missingKeysAreAddedEvenAtEqualVersion() throws Exception {
        var installed = yaml("version: 2\ntool: personal\n");
        var result = ConfigMigration.merge(installed, yaml("version: 2\ntool: new\nadded: value\n"), List.of(), true);
        assertEquals("personal", installed.getString("tool"));
        assertEquals("value", installed.getString("added"));
        assertEquals(1, result.added());
        assertTrue(result.changed());
    }

    @Test void configNeverOverwritesAdminValuesAndKeepsCommentsAndBackup() throws Exception {
        var file = directory.resolve("config.yml");
        String original = "# Custom config\nversion: 1\n# Admin limit\nlimit: 10 # tuned\n";
        Files.writeString(file, original);
        var result = ConfigMigration.migrate(file, yaml("version: 2\nlimit: 20\n# New option\nnew-option: true\n"),
                List.of(yaml("limit: 10\n")), false);
        var saved = yaml(Files.readString(file));
        assertEquals(10, saved.getInt("limit"));
        assertTrue(saved.getBoolean("new-option"));
        assertEquals(2, saved.getInt("version"));
        assertEquals(0, result.updated());
        assertTrue(Files.readString(file).contains("Admin limit"));
        assertTrue(Files.readString(file).contains("tuned"));
        assertTrue(Files.readString(file).contains("New option"));
        try (var files = Files.list(directory)) {
            var backup = files.filter(p -> p.getFileName().toString().startsWith("config.yml.bak-")).findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
    }

    @Test void invalidYamlIsNotOverwritten() throws Exception {
        var file = directory.resolve("messages.yml");
        Files.writeString(file, "private: [broken\n");
        assertThrows(Exception.class, () -> ConfigMigration.migrate(file, yaml("version: 2\n"), List.of(), true));
        assertEquals("private: [broken\n", Files.readString(file));
    }

    @Test void publishedDefaultsMigrateToBundledTextsInBothLanguages() throws Exception {
        for (String stem : List.of("messages", "messages_en")) {
            var old = resource("defaults-history/" + stem + "-v1.yml");
            var defaults = resource(stem + ".yml");
            var installed = yaml(old.saveToString());
            installed.set("plugin.enabled", "Personal text");
            var result = ConfigMigration.merge(installed, defaults, List.of(old), true);
            assertEquals(2, installed.getInt("version"));
            assertEquals("Personal text", installed.getString("plugin.enabled"));
            assertEquals(defaults.getString("tool.region.name"), installed.getString("tool.region.name"));
            if (stem.equals("messages")) assertTrue(result.updated() > 0, stem);
            else assertEquals(0, result.updated()); // v1 English keys retained their text.
            assertTrue(result.added() > 0, stem);
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !key.equals("plugin.enabled"))
                    assertEquals(defaults.get(key), installed.get(key), key);
            }
            assertFalse(ConfigMigration.merge(installed, defaults, List.of(old), true).changed());
        }
    }

    @Test void entryPointMigratesAllFilesAndLogsOnlyCounts() throws Exception {
        var plugin = org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var logger = org.mockito.Mockito.mock(java.util.logging.Logger.class);
        org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(directory.toFile());
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(logger);
        Files.writeString(directory.resolve("messages.yml"),
                resource("defaults-history/messages-v1.yml").saveToString());
        Files.writeString(directory.resolve("config.yml"), "database:\n  password: PRIVATE_VALUE\n");
        ConfigMigration.run(plugin);
        for (String file : List.of("config.yml", "messages.yml", "messages_en.yml"))
            assertEquals(2, yaml(Files.readString(directory.resolve(file))).getInt("version"));
        assertEquals("PRIVATE_VALUE", yaml(Files.readString(directory.resolve("config.yml"))).getString("database.password"));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.times(3)).info(org.mockito.Mockito.argThat(
                (String text) -> text.contains("añadidas=") && text.contains("actualizadas=")
                        && text.contains("conservadas=") && !text.contains("PRIVATE_VALUE")));
        ConfigMigration.run(plugin); // reload with complete files is idempotent
        org.mockito.Mockito.verifyNoMoreInteractions(logger);
    }

    @Test void everyRegisteredAbilityHasSpanishNameAndLore() throws Exception {
        var messages = resource("messages.yml");
        var registry = new dev.dasan.customdungeons.ability.AbilityRegistry();
        dev.dasan.customdungeons.ability.Abilities.registerDefaults(registry);
        for (var ability : registry.all()) {
            assertNotNull(messages.getString("ability." + ability.id() + ".name"), ability.id());
            assertNotNull(messages.getString("ability." + ability.id() + ".lore"), ability.id());
        }
    }

    @Test void olderKnownDefaultFromAnyPublishedVersionCanBeUpdated() throws Exception {
        var installed = yaml("version: 2\ntool: first\n");
        var result = ConfigMigration.merge(installed, yaml("version: 3\ntool: third\n"),
                List.of(yaml("tool: first\n"), yaml("tool: second\n")), true);
        assertEquals("third", installed.getString("tool"));
        assertEquals(1, result.updated());
    }

    private YamlConfiguration resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name)) {
            assertNotNull(stream, name);
            return yaml(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test void futureVersionIsNeverDowngraded() throws Exception {
        var installed = yaml("version: 3\ntool: old\n");
        var result = ConfigMigration.merge(installed, yaml("version: 2\ntool: new\n"),
                List.of(yaml("tool: old\n")), true);
        assertFalse(result.changed());
        assertEquals(3, installed.getInt("version"));
    }
}
