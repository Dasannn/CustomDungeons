package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatCatalogTest {
    @Test void completeBilingualCatalogMatchesSpecDefaultsAndRanges() {
        var r=new AbilityRegistry();CombatAbilities.register(r);assertEquals(9,r.all().size());
        check(r,"charge","length",12,4,24);check(r,"charge","damage",10,0,30);check(r,"charge","stun-ticks",40,0,100);
        check(r,"boss_totem","health",30,5,500);check(r,"boss_totem","heal-percent",2,.5,10);check(r,"boss_totem","reduction",30,10,60);check(r,"boss_totem","ticks",400,100,1200);
        check(r,"decoys","count",2,1,6);check(r,"decoys","health",10,1,100);check(r,"decoys","damage-percent",25,0,100);check(r,"decoys","ticks",300,100,1200);
        check(r,"interruptible_ultimate","charge-ticks",80,40,200);check(r,"interruptible_ultimate","interrupt-percent",10,1,50);check(r,"interruptible_ultimate","radius",10,3,24);check(r,"interruptible_ultimate","damage",30,0,80);
        assertEquals(false,r.get("interruptible_ultimate").orElseThrow().params().stream().filter(p->p.key().equals("lethal")).findFirst().orElseThrow().defaultValue());
        check(r,"purge","radius",8,3,16);check(r,"buff_steal","count",1,1,3);check(r,"buff_steal","max-ticks",600,100,1200);
        check(r,"silence","radius",6,3,12);check(r,"silence","ticks",80,20,200);
        check(r,"pain_link","ticks",160,60,300);check(r,"pain_link","percent",30,10,100);check(r,"pain_link","cap",8,2,40);check(r,"pain_link","distance",12,6,24);
        check(r,"blink_behind","distance",2,1,4);check(r,"blink_behind","bonus",50,0,200);
        for(String lang:List.of("messages","messages_en")) {
            var yaml=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/"+lang+".yml"));assertEquals(27,yaml.getInt("version"));
            var old=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/defaults-history/"+lang+"-v25.yml"));assertEquals(25,old.getInt("version"));
            for(var a:r.all()) {
                assertTrue(yaml.isString("ability."+a.id()+".name"));assertTrue(yaml.isString("ability."+a.id()+".lore"));assertTrue(yaml.isString("combat.notice."+a.id()));
                for(var p:a.params())assertTrue(yaml.isString("combat.parameters."+p.key()));
            }
        }
    }
    private void check(AbilityRegistry r,String id,String key,double value,double min,double max) {
        var p=r.get(id).orElseThrow().params().stream().filter(s->s.key().equals(key)).findFirst().orElseThrow();
        assertEquals(value,((Number)p.defaultValue()).doubleValue());assertEquals(min,p.min());assertEquals(max,p.max());
    }
}
