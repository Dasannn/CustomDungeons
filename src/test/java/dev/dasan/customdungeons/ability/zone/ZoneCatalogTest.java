package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneCatalogTest {
    @Test void nineAbilitiesHaveExactSpecDefaultsRangesAndBilingualVersionedTexts() {
        var registry=new AbilityRegistry();ZoneAbilities.register(registry);assertEquals(9,registry.all().size());
        check(registry,"vortex","radius",8,3,16);check(registry,"vortex","force",.3,.1,1);check(registry,"vortex","ticks",40,20,100);check(registry,"vortex","damage",6,0,20);check(registry,"vortex","burst-radius",3,1,8);
        check(registry,"inverted_gravity","radius",5,2,12);check(registry,"inverted_gravity","ticks",60,20,160);check(registry,"inverted_gravity","damage",0,0,10);
        check(registry,"cracked_floor","radius",8,3,16);check(registry,"cracked_floor","warning",40,20,100);check(registry,"cracked_floor","damage",8,0,30);check(registry,"cracked_floor","waves",1,1,3);
        check(registry,"sweep","angle",120,60,240);check(registry,"sweep","reach",5,2,10);check(registry,"sweep","damage",10,0,30);
        check(registry,"falling_pillars","count",3,1,8);check(registry,"falling_pillars","warning",30,20,80);check(registry,"falling_pillars","damage",10,0,30);check(registry,"falling_pillars","obstacle-ticks",120,0,400);
        check(registry,"poison_pools","count",2,1,6);check(registry,"poison_pools","radius",2,1,5);check(registry,"poison_pools","ticks",200,60,600);check(registry,"poison_pools","damage",1,0,10);
        check(registry,"charged_beam","warning",30,10,80);check(registry,"charged_beam","length",16,4,32);check(registry,"charged_beam","width",1,.5,3);check(registry,"charged_beam","damage",14,0,40);
        check(registry,"arrow_rain","radius",4,2,10);check(registry,"arrow_rain","ticks",60,20,160);check(registry,"arrow_rain","rate",6,2,20);check(registry,"arrow_rain","damage",3,0,10);
        check(registry,"rift","min-distance",8,4,32);check(registry,"rift","max-distance",16,4,32);
        for(String lang:List.of("messages","messages_en")) {
            var yaml=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/"+lang+".yml"));assertTrue(yaml.getInt("version")>=25);
            var old=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/defaults-history/"+lang+"-v24.yml"));assertEquals(24,old.getInt("version"));
            for(var ability:registry.all()) {
                assertTrue(yaml.isString("ability."+ability.id()+".name"));assertTrue(yaml.isString("ability."+ability.id()+".lore"));assertTrue(yaml.isString("zone.notice."+ability.id()));
                for(var param:ability.params())assertTrue(yaml.isString("zone.parameters."+param.key()));
            }
        }
    }
    private void check(AbilityRegistry registry,String id,String key,double value,double min,double max) {
        var spec=registry.get(id).orElseThrow().params().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();
        assertEquals(value,((Number)spec.defaultValue()).doubleValue());assertEquals(min,spec.min());assertEquals(max,spec.max());
    }
}
