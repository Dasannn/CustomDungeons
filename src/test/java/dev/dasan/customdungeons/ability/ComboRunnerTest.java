package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComboRunnerTest {
    static ComboDef combo() { return new ComboDef("combo", Trigger.ON_HIT, 0, TargetMode.NEAREST, 10, 20,
        List.of(new ComboStep("test", Map.of(), 2), new ComboStep("second", Map.of(), 3))); }
    @Test void comboRunsStepsInOrderWithDelays() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of(combo())); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); f.clock.advance(1); assertTrue(f.calls.isEmpty());
        f.clock.advance(2); assertEquals(List.of("test@2"), f.calls);
        f.fire(e, Trigger.ON_HIT, 3); f.clock.advance(5);
        assertEquals(List.of("test@2", "second@5"), f.calls);
        f.fire(e, Trigger.ON_HIT, 24); f.clock.advance(24); assertEquals(2, f.calls.size());
        f.fire(e, Trigger.ON_HIT, 25); f.clock.advance(30); assertEquals(4, f.calls.size());
    }
    @Test void comboKeepsTargetsAndRevalidatesThemAtEachStep() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of(combo()));
        var targets = new ArrayList<List<org.bukkit.entity.LivingEntity>>();
        var registry = new AbilityRegistry();
        for (String id : List.of("test", "second")) registry.register(new Ability() {
            public String id() { return id; }
            public org.bukkit.Material icon() { return org.bukkit.Material.STONE; }
            public List<ParamSpec> params() { return List.of(); }
            public void execute(AbilityContext c) { targets.add(c.targets()); }
        });
        var runner = new ComboRunner(registry);
        runner.start(combo(), f.mob, List.of(f.player), null, "combo");
        f.clock.advance(2); when(f.session.players()).thenReturn(List.of()); f.clock.advance(5);
        assertEquals(List.of(List.of(f.player), List.of()), targets);
    }
    @Test void comboCancelledWhenCasterDies() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of(combo())); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); f.clock.advance(2); when(f.entity.isDead()).thenReturn(true);
        f.clock.advance(5); assertEquals(List.of("test@2"), f.calls);
    }
}
