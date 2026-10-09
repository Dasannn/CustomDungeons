package dev.dasan.customdungeons.ability.control;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlCatalogTest {
    @Test void eightAbilitiesDeclareExactDefaultsAndRangesAndBilingualTexts() {
        var r=new AbilityRegistry();ControlAbilities.register(r);assertEquals(8,r.all().size());
        check(r,"grab_throw","reach",4,1,8);check(r,"grab_throw","ticks",30,10,100);check(r,"grab_throw","force",1.5,.5,3);check(r,"grab_throw","damage",6,0,20);
        for(String id:List.of("grab_throw","levitation_cage","drain_grab"))check(r,id,"release-damage",20,1,200);
        check(r,"levitation_cage","level",1,1,5);check(r,"levitation_cage","damage",1,0,10);check(r,"levitation_cage","ticks",80,20,300);
        check(r,"drain_grab","ticks",120,20,300);check(r,"drain_grab","damage",2,0,10);check(r,"drain_grab","healing",100,0,200);check(r,"drain_grab","jumps",8,3,20);
        check(r,"bomb_mark","ticks",100,60,200);check(r,"bomb_mark","damage",12,0,40);check(r,"bomb_mark","radius",3,1,10);
        check(r,"tactical_summon","count",2,1,10);check(r,"tactical_summon","max-alive",4,1,20);
        check(r,"roots","ticks",80,20,200);check(r,"roots","health",10,1,100);
        check(r,"anchor_spear","damage",4,0,20);check(r,"anchor_spear","ticks",160,40,400);check(r,"anchor_spear","leash",4,2,10);check(r,"anchor_spear","hits",5,1,30);
        check(r,"soul_chain","ticks",160,40,400);check(r,"soul_chain","distance",6,2,16);check(r,"soul_chain","damage",2,0,10);
        for(String lang:List.of("messages.yml","messages_en.yml")) {
            var yaml=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/"+lang));assertEquals(24,yaml.getInt("version"));
            for(var a:r.all()){assertTrue(yaml.isString("ability."+a.id()+".name"));assertTrue(yaml.isString("ability."+a.id()+".lore"));assertTrue(yaml.isString("control.notice."+a.id()));}
            assertTrue(yaml.isString("control.escape"));assertTrue(yaml.isString("control.bomb-countdown"));assertTrue(yaml.isString("control.released"));
            assertTrue(new java.io.File("src/main/resources/defaults-history/"+lang.replace(".yml","-v23.yml")).isFile());
        }
    }
    private void check(AbilityRegistry r,String id,String key,double value,double min,double max) {
        var ability=r.get(id).orElseThrow();var spec=ability.params().stream().filter(p->p.key().equals(key)).findFirst().orElseThrow();
        assertEquals(value,((Number)spec.defaultValue()).doubleValue(),id+"/"+key);assertEquals(min,spec.min());assertEquals(max,spec.max());
        var low=new ParamValues(Map.of(key,-1000),ability.params());var high=new ParamValues(Map.of(key,1000),ability.params());assertEquals(min,low.getDouble(key));assertEquals(max,high.getDouble(key));
    }
}
