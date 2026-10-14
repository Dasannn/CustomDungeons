package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.intelligence.IntelligenceRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DebugCatalogTest {
    private YamlConfiguration catalog(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/"+name),StandardCharsets.UTF_8));
    }
    @ParameterizedTest @ValueSource(strings={"messages.yml","messages_en.yml"})
    void allDynamicKeysExistAndV27MigratesWithCustomTextPreserved(String name) {
        var yaml=catalog(name);
        for(var reason:AbilityEngine.DiscardReason.values())assertTrue(yaml.isString("debug.reason."+reason.key()));
        for(var state:List.of("active","warning","paused","stunned","controlling"))assertTrue(yaml.isString("debug.state."+state));
        for(var cause:List.of("none","ability","collision","interrupted","native-stun","native-roar","ai-disabled"))assertTrue(yaml.isString("debug.cause."+cause));
        for(var rule:IntelligenceRules.defaults().all())assertTrue(yaml.isString("intelligence.adaptation."+rule.id()));
        assertTrue(yaml.isString("debug.yes"));assertTrue(yaml.isString("debug.no"));
        var previous=catalog("defaults-history/"+name.replace(".yml","-v27.yml"));assertEquals(27,previous.getInt("version"));
        assertFalse(previous.contains("debug"));assertTrue(yaml.getInt("version")>=28);
        previous.set("command.debug-off","Customized");
        var history=catalog("defaults-history/"+name.replace(".yml","-v27.yml"));
        ConfigMigration.merge(previous,yaml,List.of(history),true);
        assertEquals("Customized",previous.getString("command.debug-off"));assertEquals(yaml.getString("command.debug-on"),previous.getString("command.debug-on"));
        assertEquals(yaml.getString("debug.line"),previous.getString("debug.line"));
    }
    @ParameterizedTest @ValueSource(strings={"messages.yml","messages_en.yml"})
    void debugHelpDescribesBothFunctionsAndV28MigrationPreservesCustomization(String name) {
        var yaml=catalog(name);
        String help=yaml.getString("command.debug-help");
        assertTrue(help.contains("customdungeons.admin.debug"));
        assertFalse(help.contains("customdungeons.admin.test"));
        assertTrue(help.contains("skipwave"));assertTrue(help.contains("invulnerable"));
        var historical=catalog("defaults-history/"+name.replace(".yml","-v28.yml"));
        assertEquals(28,historical.getInt("version"));assertTrue(yaml.getInt("version")>=29);
        var installed=catalog("defaults-history/"+name.replace(".yml","-v28.yml"));
        installed.set("command.debug-off","Customized");
        ConfigMigration.merge(installed,yaml,List.of(historical),true);
        assertEquals(yaml.getString("command.debug-help"),installed.getString("command.debug-help"));
        assertEquals(yaml.getString("command.debug-on"),installed.getString("command.debug-on"));
        assertEquals("Customized",installed.getString("command.debug-off"));
        installed=catalog("defaults-history/"+name.replace(".yml","-v28.yml"));
        installed.set("command.debug-help","Personal help");
        ConfigMigration.merge(installed,yaml,List.of(historical),true);
        assertEquals("Personal help",installed.getString("command.debug-help"));
        assertEquals(historical.getString("command.debug-tick"),yaml.getString("command.debug-tick"));
    }

}
