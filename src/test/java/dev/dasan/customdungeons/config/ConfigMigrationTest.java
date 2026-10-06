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
            assertEquals(defaults.getInt("version"), installed.getInt("version"));
            assertEquals("Personal text", installed.getString("plugin.enabled"));
            assertEquals(defaults.getString("tool.region.name"), installed.getString("tool.region.name"));
            assertTrue(result.updated() > 0, stem); // T31 changes GUI texts in both languages.
            assertTrue(result.added() > 0, stem);
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !key.equals("plugin.enabled"))
                    assertEquals(defaults.get(key), installed.get(key), key);
            }
            assertFalse(ConfigMigration.merge(installed, defaults, List.of(old), true).changed());
        }
    }

    @Test void versionThreeGuiTextsUpgradeWithoutOverwritingCustomizations() throws Exception {
        for (String stem : List.of("messages", "messages_en")) {
            var old = resource("defaults-history/" + stem + "-v3.yml");
            var defaults = resource(stem + ".yml");
            assertEquals(3, old.getInt("version"));
            assertEquals(6, defaults.getInt("version"));
            var installed = yaml(old.saveToString());
            installed.set("gui.mob.name-lore", "Personal GUI text");
            var result = ConfigMigration.merge(installed, defaults, List.of(old), true);
            assertEquals(defaults.getInt("version"), installed.getInt("version"));
            assertTrue(result.updated() > 0, stem);
            assertTrue(result.added() > 0, stem);
            assertEquals(defaults.getString("gui.mob.editor"), installed.getString("gui.mob.editor"));
            assertEquals(defaults.getString("gui.dungeon.section-players-lore"),
                    installed.getString("gui.dungeon.section-players-lore"));
            assertEquals("Personal GUI text", installed.getString("gui.mob.name-lore"));
            assertFalse(ConfigMigration.merge(installed, defaults, List.of(old), true).changed());
        }
    }

    @Test void entityHeightsMigrateIntoExistingConfigWithoutOverwritingCustomValues() throws Exception {
        var file=directory.resolve("config.yml");
        Files.writeString(file,"version: 2\nentity-heights:\n  warden: 3.25\n  slime: 2.0\n");
        var defaults=resource("config.yml");
        assertNotNull(defaults.getConfigurationSection("entity-heights"));
        assertTrue(ConfigMigration.migrate(file,defaults,List.of(),false).changed());
        var installed=yaml(Files.readString(file));
        assertEquals(3.25,installed.getDouble("entity-heights.warden"));
        assertEquals(2,installed.getDouble("entity-heights.slime"));
        for(String key:defaults.getConfigurationSection("entity-heights").getKeys(false))
            if(!key.equals("warden")) assertEquals(defaults.get("entity-heights."+key),installed.get("entity-heights."+key),key);
        assertEquals(defaults.getInt("version"),installed.getInt("version"));
        assertFalse(ConfigMigration.migrate(file,defaults,List.of(),false).changed());
    }

    @Test void versionFiveGuiDesignMigratesAndPreservesCustomHelp() throws Exception {
        for(String stem:List.of("messages","messages_en")) {
            var old=resource("defaults-history/"+stem+"-v5.yml");var defaults=resource(stem+".yml");
            assertEquals(5,old.getInt("version"));
            var installed=yaml(old.saveToString());installed.set("gui.common.close-lore","Personal close lore");
            var result=ConfigMigration.merge(installed,defaults,List.of(old),true);
            assertTrue(result.added()>0);assertTrue(result.updated()>0);assertEquals(6,installed.getInt("version"));
            assertEquals(defaults.getString("gui.dungeon.add-spawner"),installed.getString("gui.dungeon.add-spawner"));
            assertEquals(defaults.getString("gui.dungeon.help-room-1"),installed.getString("gui.dungeon.help-room-1"));
            assertEquals("Personal close lore",installed.getString("gui.common.close-lore"));
            assertFalse(ConfigMigration.merge(installed,defaults,List.of(old),true).changed());
        }
    }
    @Test void versionFourScaleAndLiveTestTextsUpgradeAndKeepCustomLore() throws Exception {
        for(String stem:List.of("messages","messages_en")) {
            var old=resource("defaults-history/"+stem+"-v4.yml");
            var defaults=resource(stem+".yml");
            assertEquals(4,old.getInt("version"));
            var installed=yaml(old.saveToString());
            installed.set("gui.mob.speed-lore","Personal speed lore");
            var result=ConfigMigration.merge(installed,defaults,List.of(old),true);
            assertTrue(result.updated()>0); assertTrue(result.added()>0);
            for(String key:List.of("gui.mob.scale-lore","gui.mob.invalid-stat","gui.mob.test-lore",
                    "validation.mob-height","gui.dungeon.warnings","gui.dungeon.warning-line","livetest.space-warning"))
                assertEquals(defaults.getString(key),installed.getString(key),key);
            assertEquals("Personal speed lore",installed.getString("gui.mob.speed-lore"));
            assertEquals(6,installed.getInt("version"));
            assertFalse(ConfigMigration.merge(installed,defaults,List.of(old),true).changed());
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
            assertEquals(resource(file).getInt("version"), yaml(Files.readString(directory.resolve(file))).getInt("version"));
        assertEquals("PRIVATE_VALUE", yaml(Files.readString(directory.resolve("config.yml"))).getString("database.password"));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.times(3)).info(org.mockito.Mockito.argThat(
                (String text) -> text.contains("añadidas=") && text.contains("actualizadas=")
                        && text.contains("conservadas=") && !text.contains("PRIVATE_VALUE")));
        ConfigMigration.run(plugin); // reload with complete files is idempotent
        org.mockito.Mockito.verifyNoMoreInteractions(logger);
    }

    @Test void writeDeniedKeepsOriginalAndReportsIoCause() throws Exception {
        var plugin = org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var logger = org.mockito.Mockito.mock(java.util.logging.Logger.class);
        org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(directory.toFile());
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(logger);
        var file = directory.resolve("config.yml");
        String original = "version: 1\n";
        Files.writeString(file, original);
        var permissions = Files.getPosixFilePermissions(directory);
        try {
            Files.setPosixFilePermissions(directory, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            var failure = assertThrows(ConfigMigration.MigrationException.class, () -> ConfigMigration.run(plugin));
            assertEquals("config.yml", failure.file());
            assertEquals("config.migration-write-failed", failure.messageKey());
            assertInstanceOf(java.io.IOException.class, failure.getCause());
            assertEquals(original, Files.readString(file));
            org.mockito.Mockito.verify(logger).log(org.mockito.Mockito.eq(java.util.logging.Level.SEVERE),
                    org.mockito.Mockito.contains("config.yml"), org.mockito.Mockito.same(failure));
        } finally {
            Files.setPosixFilePermissions(directory, permissions);
        }
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
