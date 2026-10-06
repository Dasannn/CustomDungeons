package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.nio.file.Path;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NumericRangesTest {
    @Test void everyStatSharesBoundsWithValidatorAndLocalizedLore() {
        PaperApiTestBootstrap.initialize();
        var keys=Map.of("health","max-health","damage","damage","speed","speed","resistance","knockback-resistance","scale","scale");
        for(String catalog:List.of("messages.yml","messages_en.yml")) {
            var messages=new Messages();messages.load(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/"+catalog).toFile()),"");
            var services=mock(MenuListener.class);when(services.messages()).thenReturn(messages);
            try(var framework=mockStatic(MenuListener.class)) {
                framework.when(MenuListener::instance).thenReturn(services);
                for(var entry:keys.entrySet()) {
                    var range=NumericRanges.stat(entry.getKey());
                    var stats=new HashMap<String,Double>();stats.put(entry.getKey(),range.max()+1);
                    var mob=mob(stats,List.of(),List.of(),List.of());
                    var error=new Validator().validate(mob,config(),Set.of()).stream().filter(e->e.path().equals(entry.getValue())).findFirst().orElseThrow();
                    assertEquals(range.max(),Double.parseDouble(error.args().get("max")));
                    assertEquals(range.min(),Double.parseDouble(error.args().get("min")));
                    String lore=PlainTextComponentSerializer.plainText().serialize(NumericInputs.description(range));
                    assertTrue(lore.contains(range.format(range.min())+"–"+range.format(range.max())),lore);
                    assertTrue(new Validator().validate(mob(Map.of(entry.getKey(),range.max()),List.of(),List.of(),List.of()),config(),Set.of()).isEmpty());
                }
            }
        }
    }
    @Test void everyRegisteredNumericParamUsesExactParamSpecBoundsAndRejectsInvalidValues() {
        PaperApiTestBootstrap.initialize();
        var registry=new AbilityRegistry();Abilities.registerDefaults(registry);
        var validator=new Validator(registry);int checked=0;
        for(var ability:registry.all()) for(var spec:ability.params()) if(NumericRanges.numeric(spec)) {
            var range=NumericRanges.parameter(spec);checked++;
            assertEquals(spec.min(),range.min());assertEquals(spec.max(),range.max());
            assertTrue(range.contains(((Number)spec.defaultValue()).doubleValue()),ability.id()+":"+spec.key());
            for(double invalid:new double[]{range.min()-1,range.max()+1,Double.NaN,Double.POSITIVE_INFINITY}) {
                var a=new AbilityInstance(ability.id(),Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of(spec.key(),invalid));
                var errors=validator.validate(mob(Map.of(),List.of(a),List.of(),List.of()),config(),Set.of(ability.id()));
                assertTrue(errors.stream().anyMatch(e->e.path().equals("abilities[0].params."+spec.key())),ability.id()+":"+spec.key()+"="+invalid);
            }
        }
        assertTrue(checked>0,"All registered ability specifications must be covered");
        System.out.println("Validated numeric ParamSpecs: "+checked);
    }
    @Test void nestedComboAndPhaseParamsUseTheRegisteredRange() {
        PaperApiTestBootstrap.initialize();
        var ability=new AbilityInstance("lightning",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of("damage",1001));
        var combo=new ComboDef("combo",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,List.of(new ComboStep("lightning",Map.of("damage",1001),0),new ComboStep("lightning",Map.of(),0)));
        var phase=new PhaseDef(.5,false,List.of(ability),List.of(combo),Map.of(),List.of(),0,List.of(),null,null,null,null,0);
        var errors=new Validator().validate(mob(Map.of(),List.of(),List.of(combo),List.of(phase)),config(),Set.of("lightning"));
        for(String path:List.of("combos[0].steps[0].params.damage","phases[0].abilities[0].params.damage","phases[0].combos[0].steps[0].params.damage"))
            assertTrue(errors.stream().anyMatch(e->e.path().equals(path)),path);
    }
    @Test void scaleWarningsAndHeightUseTheFullRangeAndEffectiveMinecraftMinimum() {
        var heights=new EntityHeights(Map.of(EntityType.WARDEN,2.9));
        assertEquals(46.4,heights.scaledHeight(EntityType.WARDEN,16),1e-9);
        assertEquals(2.9,heights.scaledHeight(EntityType.WARDEN,0),1e-9);
        assertEquals(2.9*.0625,heights.scaledHeight(EntityType.WARDEN,.01),1e-9);
        for(double scale:new double[]{0,10,16.01,Double.NaN}) assertTrue(new Validator().warnings(mob(Map.of("scale",scale),List.of(),List.of(),List.of())).isEmpty());
        for(double scale:new double[]{10.01,16}) assertEquals("validation.scale-high",new Validator().warnings(mob(Map.of("scale",scale),List.of(),List.of(),List.of())).getFirst().messageKey());
    }
    @Test void wholeScalingPercentagesSurviveDecimalCoefficientConversion() {
        var codec=new DefinitionCodec();var yaml=new YamlConfiguration();
        codec.encode(DefinitionCodecTest.dungeon()).forEach(yaml::set);
        for(double fraction:new double[]{.07,.14,.29,.57,2.49}) {
            yaml.set("scaling.extra-mobs-per-player",fraction);yaml.set("scaling.extra-health-per-player",fraction);
            var errors=new Validator().validate(codec.decodeDungeon("test",yaml),Map.of("zombie",DefinitionCodecTest.mob()));
            assertTrue(errors.stream().noneMatch(e->e.path().startsWith("scaling.")),"GUI integer percentage: "+fraction+" "+errors);
        }
    }
    @Test void precisionSentinelsAndOriginAreExplicit() {
        assertTrue(NumericRanges.HEALTH.contains(0));assertFalse(NumericRanges.HEALTH.contains(.5));
        assertTrue(NumericRanges.SCALE.containsPrecise(.0625));
        assertEquals(.0625,Inputs.parseDecimal("0.0625",NumericRanges.SCALE.inputMin(),NumericRanges.SCALE.max(),NumericRanges.SCALE.decimals()));
        assertFalse(NumericRanges.SPAWNER_RADIUS.containsPrecise(1.25));
        assertEquals(NumericRange.Origin.PLUGIN,NumericRanges.stat("speed").origin());
        assertEquals(NumericRange.Origin.PLUGIN,NumericRanges.stat("resistance").origin());
        assertEquals(NumericRange.Origin.MINECRAFT,NumericRanges.POTION_LEVEL.origin());
        assertEquals(NumericRange.Origin.MINECRAFT,NumericRanges.parameter(new ParamSpec("amplifier",ParamType.INT,0,0,255)).origin());
        assertEquals(NumericRange.Origin.PLUGIN,NumericRanges.parameter(new ParamSpec("speedAmplifier",ParamType.INT,1,0,10)).origin());
    }
    private PluginConfig config() {var config=mock(PluginConfig.class);when(config.armorCapable()).thenReturn(Set.of(EntityType.ZOMBIE));return config;}
    private MobTemplate mob(Map<String,Double> stats,List<AbilityInstance> abilities,List<ComboDef> combos,List<PhaseDef> phases) {
        return new MobTemplate("zombie","ZOMBIE","",stats.getOrDefault("health",0d),stats.getOrDefault("damage",0d),stats.getOrDefault("speed",0d),
                stats.getOrDefault("resistance",0d),stats.getOrDefault("scale",0d),Map.of(),List.of(),abilities,combos,false,"RED",null,phases,false);
    }
}
