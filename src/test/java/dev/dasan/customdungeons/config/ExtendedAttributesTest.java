package dev.dasan.customdungeons.config;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedAttributesTest {
    @Test void healthDamageAndSpeedUseExpandedBounds() {
        assertTrue(NumericRanges.HEALTH.contains(5000));
        assertTrue(NumericRanges.HEALTH.contains(.5));
        assertTrue(NumericRanges.stat("damage").contains(3000));
        assertTrue(NumericRanges.stat("speed").contains(1024));
        assertFalse(NumericRanges.HEALTH.contains(Double.POSITIVE_INFINITY));
    }
    @Test void everyAttributeRoundTripsInMobAndPhaseIncludingExplicitZero() throws Exception {
        var attributes = new LinkedHashMap<String,Object>();
        String[] keys={"max-health","damage","speed","knockback-resistance","scale","armor","armor-toughness",
                "follow-range","attack-knockback","jump-strength","gravity","step-height","explosion-knockback-resistance"};
        double[] values={5000,3000,1024,0,16,30,20,2048,5,32,-1,10,1};
        for(int i=0;i<keys.length;i++) attributes.put(keys[i],values[i]);
        var yaml=new YamlConfiguration();yaml.set("entity-type","ZOMBIE");yaml.set("attributes",attributes);
        yaml.set("phases",List.of(Map.of("health-threshold",.5,"attributes",attributes)));
        var codec=new DefinitionCodec();var encoded=codec.encode(codec.decodeMob("extended",yaml));
        assertEquals(attributes,encoded.get("attributes"));
        assertEquals(attributes,((Map<?,?>)((List<?>)encoded.get("phases")).getFirst()).get("attributes"));
    }
    @Test void everyBoundedAttributeClampsOnLoadInMobAndPhaseWithoutChangingTheYaml() {
        for(String key:dev.dasan.customdungeons.model.MobAttributes.KEYS) {
            var range=NumericRanges.attribute(key);
            for(double invalid:range.unbounded() ? new double[]{range.min()-1} : new double[]{range.min()-1,range.max()+1}) {
                var yaml=new YamlConfiguration();yaml.set("entity-type","ZOMBIE");yaml.set("attributes",Map.of(key,invalid));
                yaml.set("phases",List.of(Map.of("health-threshold",.5,"attributes",Map.of(key,invalid))));
                String original=yaml.saveToString();
                var normalized=new NumericLoadNormalizer(null,null).normalize("mobs",yaml);
                var loaded=new DefinitionCodec().decodeMob("extended",normalized.yaml());
                assertEquals(range.clamp(invalid),loaded.attributes().values().get(key));
                assertEquals(range.clamp(invalid),loaded.phases().getFirst().attributes().values().get(key));
                assertEquals(2,normalized.warnings().size(),key);
                assertEquals(original,yaml.saveToString());
            }
        }
    }
    @Test void validatorRejectsOutOfRangeAndNonFiniteOverridesInBothContexts() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var config=org.mockito.Mockito.mock(PluginConfig.class);
        org.mockito.Mockito.when(config.armorCapable()).thenReturn(Set.of(org.bukkit.entity.EntityType.ZOMBIE));
        var validator=new Validator();var codec=new DefinitionCodec();
        for(String key:dev.dasan.customdungeons.model.MobAttributes.KEYS) {
            var range=NumericRanges.attribute(key);
            for(double invalid:new double[]{range.min()-1,range.unbounded()?Double.POSITIVE_INFINITY:range.max()+1,Double.NaN}) {
                var yaml=new YamlConfiguration();yaml.set("entity-type","ZOMBIE");yaml.set("attributes",Map.of(key,invalid));
                yaml.set("phases",List.of(Map.of("health-threshold",.5,"attributes",Map.of(key,invalid))));
                var overrides=new dev.dasan.customdungeons.model.MobAttributes(Map.of(key,invalid));
                var phase=new dev.dasan.customdungeons.model.PhaseDef(.5,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,0,overrides);
                var mob=new dev.dasan.customdungeons.model.MobTemplate("extended","ZOMBIE","",0,0,0,0,0,
                        Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(phase),false,overrides);
                var errors=validator.validate(mob,config,Set.of());
                assertTrue(errors.stream().anyMatch(e->e.path().equals("attributes."+key)),key);
                assertTrue(errors.stream().anyMatch(e->e.path().equals("phases[0].attributes."+key)),key);
                if(!Double.isFinite(invalid)) assertThrows(NumericLoadNormalizer.InvalidNumber.class,
                        ()->new NumericLoadNormalizer(null,null).normalize("mobs",yaml));
            }
        }
    }
    @Test void oldYamlAndConstructorsKeepIdenticalEncodedFields() throws Exception {
        var codec=new DefinitionCodec();var old=DefinitionCodecTest.mob();var original=codec.encode(old);
        var loaded=codec.decodeMob(old.id(),DefinitionCodecTest.yaml(original));
        assertEquals(old,loaded);assertEquals(original,codec.encode(loaded));
        assertFalse(original.containsKey("attributes"));
    }
}
