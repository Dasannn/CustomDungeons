package dev.dasan.customdungeons.ability.control;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComboHealthTriggerTest {
    @Test void firstStartedStepSpendsTheHealthTriggerEvenIfTheSecondStepIsCancelled() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.caster.combos().add(new ComboDef("started",Trigger.HEALTH_BELOW,100,TargetMode.NEAREST,4,100,
                List.of(new ComboStep("grab_throw",Map.of(),0),new ComboStep("roots",Map.of(),30))));
            when(f.platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48));
            var engine=new AbilityEngine(f.registry,f.platform,new Random(1));f.players.remove(f.ally);
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,0);f.step(20);
            assertEquals(1,f.service.activeCount());
            when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.step(80);
            when(f.player.getLocation()).thenReturn(new Location(f.world,1,64,0));
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,f.tick.get());f.step(20);
            verify(f.entity,times(1)).addPassenger(any());
        }
    }
    @Test void escapedFirstWarningDoesNotSpendTheOneShotComboBeforeAnyStepStarts() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.caster.combos().add(new ComboDef("escape",Trigger.HEALTH_BELOW,100,TargetMode.NEAREST,4,100,
                List.of(new ComboStep("grab_throw",Map.of(),0),new ComboStep("roots",Map.of(),0))));
            when(f.platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48));
            var engine=new AbilityEngine(f.registry,f.platform,new Random(1));
            f.players.remove(f.ally);
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,0);
            when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.step(20);
            assertEquals(0,f.service.activeCount());assertTrue(f.caster.ready("combo:escape#0",20));
            verify(f.entity,never()).addPassenger(any());
            when(f.player.getLocation()).thenReturn(new Location(f.world,1,64,0));f.step(1);
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,f.tick.get());f.step(20);
            assertEquals(1,f.service.activeCount(),"No step started, but the health-below combo's unique trigger was already consumed");
        }
    }
    @Test void equivalentOrdinaryAbilityCanRetryTheUnspentHealthTrigger() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.caster.abilities().add(new AbilityInstance("grab_throw",Trigger.HEALTH_BELOW,100,TargetMode.NEAREST,4,100,1,0,Map.of()));
            when(f.platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48));
            var engine=new AbilityEngine(f.registry,f.platform,new Random(1));f.players.remove(f.ally);
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,0);
            when(f.player.getLocation()).thenReturn(new Location(f.world,30,64,0));f.step(20);assertEquals(0,f.service.activeCount());
            when(f.player.getLocation()).thenReturn(new Location(f.world,1,64,0));f.step(1);
            engine.fire(Trigger.HEALTH_BELOW,f.caster,null,f.tick.get());f.step(20);assertEquals(1,f.service.activeCount());
        }
    }
}
