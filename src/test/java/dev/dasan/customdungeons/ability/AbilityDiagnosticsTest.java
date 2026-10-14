package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlAbility;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import static dev.dasan.customdungeons.ability.AbilityEngine.DiscardReason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbilityDiagnosticsTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @BeforeEach void enable() {
        var admin=mock(org.bukkit.entity.Player.class);when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.hasPermission("customdungeons.admin.test")).thenReturn(true);dev.dasan.customdungeons.mob.DebugView.toggle(admin);
    }
    @AfterEach void reset() {dev.dasan.customdungeons.mob.DebugView.clear();}
    @Test void inactiveDebugDoesNotCollectDiagnosticsOrReselectUnavailableControlTargets() {
        dev.dasan.customdungeons.mob.DebugView.clear();
        var a=new AbilityInstance("roots",Trigger.ON_HIT,0,TargetMode.NEAREST,16,100,1,20,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());var ability=mock(ControlAbility.class);
        when(ability.id()).thenReturn("roots");when(ability.params()).thenReturn(List.of());f.registry.register(ability);
        try(var control=mockStatic(ControlService.class)) {
            control.when(()->ControlService.targetAvailable("roots",f.player)).thenReturn(false);
            var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);assertNull(engine.lastDiscarded(f.mob));
            verify(f.session).players();
        }
    }
    @Test void cooldownReceiptIsBoundedRetainedOnSuccessAndRemovedWithMob() {
        var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.ON_HIT,0,20,1,0)),List.of());
        var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);assertNull(engine.lastDiscarded(f.mob));
        f.fire(engine,Trigger.ON_HIT,1);assertEquals(new AbilityEngine.Discarded("test",COOLDOWN),engine.lastDiscarded(f.mob));
        var receipt=engine.lastDiscarded(f.mob);f.fire(engine,Trigger.ON_HIT,2);assertSame(receipt,engine.lastDiscarded(f.mob));
        f.fire(engine,Trigger.ON_HIT,20);assertSame(receipt,engine.lastDiscarded(f.mob));assertEquals(2,f.calls.size());
        when(f.entity.isDead()).thenReturn(true);engine.tick(List.of(f.mob),21);assertNull(engine.lastDiscarded(f.mob));
    }
    @Test void reachUsesAbilityParameterAndDoesNotSpendCooldown() {
        var a=new AbilityInstance("reach",Trigger.ON_HIT,0,TargetMode.NEAREST,24,40,1,0,Map.of("reach",3d));
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());
        var ability=mock(Ability.class);when(ability.id()).thenReturn("reach");
        when(ability.params()).thenReturn(List.of(new ParamSpec("reach",ParamType.DOUBLE,3d,0,24)));
        f.registry.register(ability);when(f.player.getLocation()).thenReturn(new Location(f.world,4,64,0));
        var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
        assertEquals(OUT_OF_RANGE,engine.lastDiscarded(f.mob).reason());assertTrue(f.mob.ready("reach#0",0));verify(ability,never()).execute(any());
    }
    @Test void targetEscapingDuringWarningRecordsTheCancellation() {
        var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.ON_HIT,0,40,1,10)),List.of());
        var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);assertEquals("test",engine.castingAbility(f.mob));
        when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.clock.advance(10);
        assertEquals(OUT_OF_RANGE,engine.lastDiscarded(f.mob).reason());assertTrue(f.mob.ready("test#0",10));assertTrue(f.calls.isEmpty());
    }
    @Test void pauseReportsPendingSkillWithoutExecutingIt() {
        var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.ON_HIT,0,40,1,0)),List.of());
        try(var combat=mockStatic(CombatService.class)) {
            combat.when(()->CombatService.paused(f.mob)).thenReturn(true);
            combat.when(()->CombatService.pauseInfo(f.mob)).thenReturn(new CombatService.PauseInfo("interruptible_ultimate",false));
            var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
            assertEquals(PAUSED,engine.lastDiscarded(f.mob).reason());assertTrue(f.mob.ready("test#0",0));assertTrue(f.calls.isEmpty());
        }
    }
    @Test void occupiedControlIsNotMisreportedAsOutOfRange() {
        var a=new AbilityInstance("roots",Trigger.ON_HIT,0,TargetMode.NEAREST,16,100,1,20,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());var ability=mock(ControlAbility.class);
        when(ability.id()).thenReturn("roots");when(ability.params()).thenReturn(List.of());f.registry.register(ability);
        try(var control=mockStatic(ControlService.class)) {
            control.when(()->ControlService.targetAvailable("roots",f.player)).thenReturn(false);
            var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
            assertEquals(CONTROL_OCCUPIED,engine.lastDiscarded(f.mob).reason());assertTrue(f.mob.ready("roots#0",0));
            verify(ability,never()).execute(any(),anyInt());
        }
    }
    @Test void aWaitingSkillReportsTheCurrentWarningCause() {
        var first=AbilityEngineTest.instance(Trigger.ON_HIT,0,40,1,10);
        var next=new AbilityInstance("second",Trigger.ON_SPAWN,0,TargetMode.NEAREST,16,40,1,0,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(first,next),List.of());var engine=f.engine();
        f.fire(engine,Trigger.ON_HIT,0);f.fire(engine,Trigger.ON_SPAWN,1);
        assertEquals(new AbilityEngine.Discarded("second",WARNING),engine.lastDiscarded(f.mob));
    }
    @Test void aComboStepThatLosesItsTargetReportsTheActualDiscardedAbility() {
        var combo=new ComboDef("pair",Trigger.ON_HIT,0,TargetMode.NEAREST,10,40,
                List.of(new ComboStep("test",Map.of(),0),new ComboStep("second",Map.of(),5)));
        var f=new AbilityEngineTest.Fixture(List.of(),List.of(combo));var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
        when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.clock.advance(5);
        assertEquals(new AbilityEngine.Discarded("second",OUT_OF_RANGE),engine.lastDiscarded(f.mob));
    }
    @Test void tacticalPeriodicSelectionReportsOutOfRangeWithoutSpendingCooldown() {
        var a=new AbilityInstance("test",Trigger.EVERY_X_SECONDS,.05,TargetMode.NEAREST,3,100,1,0,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());
        var active=new dev.dasan.customdungeons.runtime.ActiveMob(f.entity,f.mob.template().withIntelligence(dev.dasan.customdungeons.intelligence.IntelligenceDef.level(5)),f.session);
        when(f.player.getLocation()).thenReturn(new Location(f.world,4,64,0));
        var engine=f.engine();engine.tick(List.of(active),0);
        assertNotNull(engine.lastDiscarded(active));
        assertEquals(OUT_OF_RANGE,engine.lastDiscarded(active).reason());
        assertTrue(active.ready("test#0",0));assertTrue(f.calls.isEmpty());
    }
    @Test void tacticalPeriodicSelectionReportsOccupiedControlInsteadOfStaleDiagnostics() {
        var a=new AbilityInstance("roots",Trigger.EVERY_X_SECONDS,.05,TargetMode.NEAREST,16,100,1,20,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());
        var active=new dev.dasan.customdungeons.runtime.ActiveMob(f.entity,f.mob.template().withIntelligence(dev.dasan.customdungeons.intelligence.IntelligenceDef.level(5)),f.session);
        var ability=mock(ControlAbility.class);
        when(ability.id()).thenReturn("roots");when(ability.params()).thenReturn(List.of());f.registry.register(ability);
        try(var control=mockStatic(ControlService.class)) {
            control.when(()->ControlService.targetAvailable("roots",f.player)).thenReturn(false);
            var engine=f.engine();engine.tick(List.of(active),0);
            assertNotNull(engine.lastDiscarded(active));
            assertEquals(CONTROL_OCCUPIED,engine.lastDiscarded(active).reason());
            assertTrue(active.ready("roots#0",0));verify(ability,never()).execute(any(),anyInt());
        }
    }
    @Test void inactiveTacticalDebugDoesNotReselectBlockedControlTargets() {
        dev.dasan.customdungeons.mob.DebugView.clear();
        var a=new AbilityInstance("roots",Trigger.EVERY_X_SECONDS,.05,TargetMode.NEAREST,16,100,1,20,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a),List.of());
        var active=new dev.dasan.customdungeons.runtime.ActiveMob(f.entity,f.mob.template().withIntelligence(dev.dasan.customdungeons.intelligence.IntelligenceDef.level(5)),f.session);
        var ability=mock(ControlAbility.class);when(ability.id()).thenReturn("roots");when(ability.params()).thenReturn(List.of());f.registry.register(ability);
        try(var control=mockStatic(ControlService.class)) {
            control.when(()->ControlService.targetAvailable("roots",f.player)).thenReturn(false);
            TargetSelector.selectAbility(active,"roots",TargetMode.NEAREST,16);
            int baseline=(int)mockingDetails(f.session).getInvocations().stream().filter(i->i.getMethod().getName().equals("players")).count();
            clearInvocations(f.session);
            var engine=f.engine();engine.tick(List.of(active),0);
            assertNull(engine.lastDiscarded(active));verify(f.session,times(baseline)).players();
        }
    }
}
