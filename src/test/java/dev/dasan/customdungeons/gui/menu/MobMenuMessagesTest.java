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
