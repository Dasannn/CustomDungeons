package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.intelligence.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntelligenceCodecTest {
    @Test void legacyDefinitionRoundTripsWithoutAddingAnySection() {
        var yaml=new YamlConfiguration();yaml.set("entity-type","HUSK");
        var codec=new DefinitionCodec();var mob=codec.decodeMob("legacy",yaml);var before=codec.encode(mob);
        assertFalse(before.containsKey("intelligence"));assertEquals(0,mob.intelligence().level());
        var copy=new YamlConfiguration();before.forEach(copy::set);assertEquals(before,codec.encode(codec.decodeMob("legacy",copy)));
        var draft=new dev.dasan.customdungeons.gui.menu.MobMenu.MobDraft(mob);assertEquals(before,codec.encode(draft.snapshot()));
    }
    @Test void explicitValuesAndIndependentPhaseOverridesRoundTrip() {
        var yaml=new YamlConfiguration();yaml.set("entity-type","HUSK");yaml.set("intelligence",Map.of("level",5,"weak-point","back","weak-point-bonus",30,"advanced",Map.of("duration",14,"maximum",2),"disabled-adaptations",List.of("shield")));
        yaml.set("phases",List.of(Map.of("health-threshold",.6,"intelligence",Map.of("level",3)),Map.of("health-threshold",.3,"intelligence",Map.of("weak-point","head","weak-point-bonus",40))));
        var codec=new DefinitionCodec();var mob=codec.decodeMob("boss",yaml);var saved=new YamlConfiguration();codec.encode(mob).forEach(saved::set);
        assertEquals(mob,codec.decodeMob("boss",saved));var def=mob.intelligence().phase(mob.phases().getFirst().intelligence()).phase(mob.phases().getLast().intelligence());
        assertEquals(3,def.level());assertEquals(IntelligenceDef.WeakPoint.HEAD,def.weakPoint());assertEquals(40,def.bonus());assertEquals(14,def.value("duration"));assertTrue(def.disabled().contains("shield"));
        assertEquals(mob.intelligence(),mob.withWorldBoss(null).intelligence());
    }
    @Test void catalogsHaveParityAndPreviousVersionHistory() throws Exception {
        var es=new YamlConfiguration();var en=new YamlConfiguration();
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/messages.yml"))){es.load(reader);}
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/messages_en.yml"))){en.load(reader);}
        var a=new TreeSet<String>();var b=new TreeSet<String>();
        for(String key:es.getKeys(true))if((key.startsWith("gui.intelligence.")||key.startsWith("intelligence."))&&es.isString(key))a.add(key);
        for(String key:en.getKeys(true))if((key.startsWith("gui.intelligence.")||key.startsWith("intelligence."))&&en.isString(key))b.add(key);
        assertEquals(a,b);assertTrue(a.size()>100);assertEquals(24,es.getInt("version"));
        for(String file:List.of("messages","messages_en")){var history=new YamlConfiguration();try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/defaults-history/"+file+"-v22.yml"))){history.load(reader);}assertEquals(22,history.getInt("version"));assertFalse(history.contains("gui.intelligence"));}
    }
    @Test void numericLoadClampsWithWarningsAndNeverRewritesSource() {
        var yaml=new YamlConfiguration();yaml.set("intelligence",Map.of("level",3,"weak-point-bonus",200,"advanced",Map.of("maximum",3,"duration",99,"repetitions",1)));
        var before=yaml.saveToString();var result=new NumericLoadNormalizer(null,null).normalize("mobs",yaml);
        assertEquals(before,yaml.saveToString());var mob=new DefinitionCodec().decodeMob("a",result.yaml());
        assertEquals(1,mob.intelligence().maximum());assertEquals(100,mob.intelligence().bonus());assertEquals(30,mob.intelligence().value("duration"));assertEquals(2,mob.intelligence().value("repetitions"));assertEquals(4,result.warnings().size());
    }
}
