package dev.dasan.customdungeons.ability;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ParamValuesTest {
    private final List<ParamSpec> specs = List.of(new ParamSpec("radius", ParamType.DOUBLE, 3.0, 1,10),
        new ParamSpec("count", ParamType.INT, 2,1,5), new ParamSpec("enabled", ParamType.BOOLEAN,true,0,0),
        new ParamSpec("name", ParamType.STRING,"default",0,0));
    @Test void paramValuesClampAndDefault() {
        assertEquals(3.0, new ParamValues(Map.of(), specs).getDouble("radius"));
        assertEquals(10.0, new ParamValues(Map.of("radius",99), specs).getDouble("radius"));
        assertEquals(1.0, new ParamValues(Map.of("radius",-99), specs).getDouble("radius"));
    }
    @Test void invalidTypesAndNonFiniteNumbersUseDefaults() {
        ParamValues p = new ParamValues(Map.of("radius", Double.NaN, "count","bad", "enabled",42,"name",false),specs);
        assertEquals(3.0,p.getDouble("radius")); assertEquals(2,p.getInt("count"));
        assertTrue(p.getBoolean("enabled")); assertEquals("default",p.getString("name"));
    }
    @Test void activeMobKeepsMutableStateIndependentOfTemplate() {
        var template = new dev.dasan.customdungeons.model.MobTemplate("mob", "minecraft:zombie", "mob",
            0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var mob = new dev.dasan.customdungeons.runtime.ActiveMob(null, template, null);
        assertEquals(-1, mob.phaseIndex());
        assertTrue(mob.ready("attack", 0));
        mob.cooldown("attack", 10);
        assertFalse(mob.ready("attack", 9)); assertTrue(mob.ready("attack", 10));
        mob.phaseIndex(0); mob.invulnerableUntil(20);
        assertEquals(0,mob.phaseIndex()); assertEquals(20,mob.invulnerableUntil());
        mob.abilities().add(new dev.dasan.customdungeons.model.AbilityInstance("attack",
            dev.dasan.customdungeons.model.Trigger.ON_HIT,0,dev.dasan.customdungeons.model.TargetMode.NEAREST,3,0,1,0,Map.of()));
        assertTrue(template.abilities().isEmpty()); assertEquals(1, mob.abilities().size());
    }
    @Test void registryRejectsDuplicateAndInvalidIds() {
        AbilityRegistry registry = new AbilityRegistry();
        Ability a = ability("test_ability");
        registry.register(a);
        assertSame(a, registry.get("test_ability").orElseThrow());
        assertTrue(registry.get("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.register(a));
        assertThrows(IllegalArgumentException.class, () -> registry.register(ability("bad-id")));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }
    private Ability ability(String id) {
        return new Ability() {
            public String id() { return id; }
            public org.bukkit.Material icon() { return org.bukkit.Material.STONE; }
            public List<ParamSpec> params() { return List.of(); }
            public void execute(AbilityContext ctx) {}
        };
    }
    @Test void allTypedAccessorsAndSnapshot() {
        Map<String,Object> raw = new HashMap<>(Map.of("count",99,"enabled",false,"name","custom"));
        ParamValues p = new ParamValues(raw,specs); raw.put("count",1);
        assertEquals(5,p.getInt("count")); assertFalse(p.getBoolean("enabled")); assertEquals("custom",p.getString("name"));
    }
}
