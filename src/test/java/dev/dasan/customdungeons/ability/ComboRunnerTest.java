package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComboRunnerTest {
    @Test void retriesReuseTheSameDeclaredParameterSnapshotAndStillCompleteTheCombo() {
        var f=new AbilityEngineTest.Fixture(List.of(),List.of());
        var ability=mock(dev.dasan.customdungeons.ability.combat.CombatAbility.class);
        when(ability.id()).thenReturn("counted");when(ability.icon()).thenReturn(org.bukkit.Material.STONE);
        when(ability.params()).thenReturn(List.of(new ParamSpec("reach",ParamType.DOUBLE,8d,1,20)));
        f.registry.register(ability);clearInvocations(ability);
        var parameters=new ArrayList<ParamValues>();
        doAnswer(i->{var ctx=(AbilityContext)i.getArgument(0);parameters.add(ctx.params());if(parameters.size()<3)AbilityCasts.cancel(ctx,true);else AbilityCasts.started(ctx);return null;}).when(ability).execute(any(AbilityContext.class));
        var definition=new ComboDef("retry",Trigger.ON_HIT,0,TargetMode.NEAREST,10,100,List.of(new ComboStep("counted",Map.of(),0),new ComboStep("second",Map.of(),0)));
        var runner=new ComboRunner(f.registry);runner.start(definition,f.mob,List.of(f.player),null,"retry");
        f.clock.advance(1);assertTrue(f.mob.ready("retry",1));f.clock.advance(2);
        assertEquals(List.of("second@2"),f.calls);assertFalse(runner.running(f.mob,"retry"));
        assertEquals(3,parameters.size());assertSame(parameters.getFirst(),parameters.getLast());verify(ability).params();
    }
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
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
        assertEquals(List.of(List.of(f.player)), targets);
    }
    @Test void discardedStepDoesNotReportACompletedComboOrConsumeItsCooldown() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of(combo()));
        var runner = new ComboRunner(f.registry);
        runner.start(combo(), f.mob, List.of(f.player), null, "combo");
        f.clock.advance(2); when(f.session.players()).thenReturn(List.of()); f.clock.advance(5);
        assertEquals(List.of("test@2"), f.calls);
        assertTrue(f.mob.ready("combo", 5), "A skipped step cannot complete the combo");
        assertFalse(runner.running(f.mob, "combo"));
    }
    @Test void comboCancelledWhenCasterDies() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of(combo())); var e = f.engine();
        f.fire(e, Trigger.ON_HIT, 0); f.clock.advance(2); when(f.entity.isDead()).thenReturn(true);
        f.clock.advance(5); assertEquals(List.of("test@2"), f.calls);
    }
}
