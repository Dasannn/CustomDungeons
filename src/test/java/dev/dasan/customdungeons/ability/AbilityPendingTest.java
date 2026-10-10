package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilityPendingTest {
    @Test void unavailablePendingTargetIsExaminedOncePerTickAndFreshOnTheNextTick() {
        var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.ON_HIT,0,100,1,0)),List.of());
        var engine=f.engine();org.mockito.Mockito.when(f.player.getLocation()).thenReturn(new org.bukkit.Location(f.world,30,64,0));
        f.fire(engine,Trigger.ON_HIT,0);org.mockito.Mockito.clearInvocations(f.session);
        f.clock.advance(1);engine.tick(List.of(f.mob),1);
        org.mockito.Mockito.verify(f.session).players();assertTrue(f.calls.isEmpty());assertTrue(f.mob.ready("test#0",1));
        org.mockito.Mockito.when(f.player.getLocation()).thenReturn(new org.bukkit.Location(f.world,2,64,0));
        f.clock.advance(2);engine.tick(List.of(f.mob),2);assertEquals(List.of("test@2"),f.calls);
    }
    @Test void oneShotReceivedDuringWarningRemainsReadyAndRunsOnceAfterward() {
        var a=AbilityEngineTest.instance(Trigger.ON_HIT,0,100,1,10);
        var b=new AbilityInstance("second",Trigger.ON_SPAWN,0,TargetMode.NEAREST,10,100,1,0,Map.of());
        var f=new AbilityEngineTest.Fixture(List.of(a,b),List.of());var engine=f.engine();
        f.fire(engine,Trigger.ON_HIT,0);
        for(int t=1;t<10;t++)f.fire(engine,Trigger.ON_SPAWN,t);
        assertTrue(f.mob.ready("second#1",9));
        f.clock.advance(10);engine.tick(List.of(f.mob),10);
        assertEquals(List.of("test@10","second@10"),f.calls);
        f.fire(engine,Trigger.ON_SPAWN,200);
        assertEquals(2,f.calls.size());
    }
    @Test void simultaneousEventsDrainAtMostOnePerEvaluationWithoutReplayingTheEvents() {
        var f=new AbilityEngineTest.Fixture(List.of(
                AbilityEngineTest.instance(Trigger.ON_HIT,0,100,1,0),
                new AbilityInstance("second",Trigger.ON_HIT,0,TargetMode.NEAREST,10,100,1,0,Map.of())),List.of());
        var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
        assertEquals(List.of("test@0"),f.calls);
        assertFalse(f.mob.ready("test#0",0));
        assertTrue(f.mob.ready("second#1",0));
        engine.tick(List.of(f.mob),0);assertEquals(1,f.calls.size());
        f.clock.advance(1);engine.tick(List.of(f.mob),1);assertEquals(List.of("test@0","second@1"),f.calls);
    }
    @Test void equalCadencesHaveDistinctInitialOffsets() {
        var f=new AbilityEngineTest.Fixture(List.of(
                AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,1,0),
                new AbilityInstance("second",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,1,0,Map.of())),List.of());
        var engine=f.engine();for(int t=0;t<100;t++){f.clock.advance(t);engine.tick(List.of(f.mob),t);}
        assertEquals(2,f.calls.size());
        assertNotEquals(f.calls.get(0).split("@")[1],f.calls.get(1).split("@")[1]);
    }
    @Test void deathDuringAnUnfinishedWarningStillRunsItsUniqueDeathAbility() {
        var f=new AbilityEngineTest.Fixture(List.of(
                AbilityEngineTest.instance(Trigger.ON_HIT,0,100,1,20),
                new AbilityInstance("second",Trigger.ON_DEATH,0,TargetMode.NEAREST,10,100,1,0,Map.of())),List.of());
        var engine=f.engine();f.fire(engine,Trigger.ON_HIT,0);
        org.mockito.Mockito.when(f.entity.isDead()).thenReturn(true);
        f.fire(engine,Trigger.ON_DEATH,1);
        assertEquals(List.of("second@1"),f.calls);
        assertFalse(AbilityCasts.warning(f.mob));
        f.fire(engine,Trigger.ON_DEATH,2);assertEquals(1,f.calls.size());
    }

    @Test void nativeRavagerStunAndRoarBlockMovementUntilTheyEnd() {
        var f=new AbilityEngineTest.Fixture(List.of(),List.of());
        var ravager=org.mockito.Mockito.mock(org.bukkit.entity.Ravager.class);
        var active=new dev.dasan.customdungeons.runtime.ActiveMob(ravager,f.mob.template(),f.mob.session());
        org.mockito.Mockito.when(ravager.getStunnedTicks()).thenReturn(40);
        assertTrue(MobMovement.paused(active));
        org.mockito.Mockito.when(ravager.getStunnedTicks()).thenReturn(0);
        org.mockito.Mockito.when(ravager.getRoarTicks()).thenReturn(20);
        assertTrue(MobMovement.paused(active));
        org.mockito.Mockito.when(ravager.getRoarTicks()).thenReturn(0);
        assertFalse(MobMovement.paused(active));
    }
}
