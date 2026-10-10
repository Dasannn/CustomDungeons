package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PeriodicProbabilityTest {
 static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
 @Test void rejectedPeriodicChanceMustWaitForTheNextPeriod() {
  var instance=AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,.5,0);
  var f=new AbilityEngineTest.Fixture(List.of(instance),List.of());
  var random=mock(Random.class);when(random.nextDouble()).thenReturn(.9,.1);
  var engine=new AbilityEngine(f.registry,null,random);
  long first=Math.floorMod(AbilityEngine.initialOffset(instance,0,100)+f.entity.getUniqueId().hashCode(),100);
  engine.fire(Trigger.ON_SPAWN,f.mob,null,0);
  f.fire(engine,Trigger.EVERY_X_SECONDS,first);assertTrue(f.calls.isEmpty());
  f.fire(engine,Trigger.EVERY_X_SECONDS,first+1);
  assertTrue(f.calls.isEmpty(),"A rejected periodic probability was rerolled on the very next tick: "+f.calls);
  f.fire(engine,Trigger.EVERY_X_SECONDS,first+99);assertTrue(f.calls.isEmpty());
  f.fire(engine,Trigger.EVERY_X_SECONDS,first+100);assertEquals(List.of("test@"+(first+100)),f.calls);
  verify(random,times(2)).nextDouble();
 }
}
