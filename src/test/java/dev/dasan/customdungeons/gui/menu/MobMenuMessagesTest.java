package dev.dasan.customdungeons.gui.menu;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MobMenuMessagesTest {
    YamlConfiguration catalog(String file) throws Exception {
        var yaml=new YamlConfiguration();
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/"+file),StandardCharsets.UTF_8)) { yaml.load(reader); }
        return yaml;
    }
    @Test void validationDetailsHaveLocalizedReadablePathsAndRanges() throws Exception {
        for (String file : java.util.List.of("messages.yml","messages_en.yml")) {
            var yaml=catalog(file);
            var messages=new dev.dasan.customdungeons.text.Messages(); messages.load(yaml,"");
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            String scale=plain.serialize(dev.dasan.customdungeons.config.Validator.describe(
                    new dev.dasan.customdungeons.config.ValidationError("scale","validation.stat-range",java.util.Map.of("value","10.01","min","0.00","max","10.00")),messages));
            assertTrue(scale.contains(file.equals("messages.yml") ? "Escala" : "Scale"));
            assertTrue(scale.contains("10.01")); assertTrue(scale.contains("0.00–10.00"));
            String hand=plain.serialize(dev.dasan.customdungeons.config.Validator.describe(
                    new dev.dasan.customdungeons.config.ValidationError("phases[0].equipment.HAND","validation.equipment-tool",java.util.Map.of()),messages));
            assertTrue(hand.contains(file.equals("messages.yml") ? "mano principal" : "main hand"));
            assertFalse(hand.contains("equipment.HAND"));
            assertFalse(hand.contains("<validation."));
            for (String key : java.util.List.of("gui.mob.duplicate-id","gui.dungeon.duplicate-id")) {
                String duplicate=plain.serialize(messages.get(key,net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("id","warden")));
                assertTrue(duplicate.contains("warden"));
            }
        }
    }
    @Test void scaleLoreAndWarningsExistInBothLanguages() throws Exception {
        for(String file:java.util.List.of("messages.yml","messages_en.yml")) {
            var yaml=catalog(file);
            assertFalse(yaml.getString("gui.mob.scale-lore").contains("0–10"));
            assertTrue(yaml.isString("gui.common.numeric-range"));
            assertTrue(yaml.isString("gui.common.range-origin-minecraft"));
            assertTrue(yaml.isString("gui.common.range-origin-plugin"));
            assertTrue(yaml.isString("validation.scale-high"));
            for(String key:java.util.List.of("validation.mob-height","gui.dungeon.warnings","gui.dungeon.warning-line","livetest.space-warning"))
                assertTrue(yaml.getString(key,"").startsWith("&e"),key);
            assertTrue(yaml.getString("livetest.space-warning").contains(file.equals("messages.yml")
                    ? "puede quedarse atascado o atravesar el suelo" : "may get stuck or fall through the floor"));
        }
    }
    @Test void everyRegisteredAbilityAndMobEditorMessageExistsInBothLanguages() throws Exception {
        PaperApiTestBootstrap.initialize();
        var registry=new AbilityRegistry(); Abilities.registerDefaults(registry);
        var es=catalog("messages.yml"); var en=catalog("messages_en.yml");
        for(var ability:registry.all()) for(var suffix:java.util.List.of("name","lore")) {
            String key="ability."+ability.id()+"."+suffix;
            assertTrue(es.isString(key),key); assertTrue(en.isString(key),key);
        }
        for(String key:es.getKeys(true)) if(es.isString(key)&&(key.startsWith("gui.mob.")||key.startsWith("livetest.")))
            assertTrue(en.isString(key),key);
    }
}
