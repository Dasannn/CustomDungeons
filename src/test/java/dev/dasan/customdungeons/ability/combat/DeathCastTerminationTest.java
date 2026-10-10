package dev.dasan.customdungeons.ability.combat;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeathCastTerminationTest {
 static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
 @Test void deathCombatAbilityMustReturnWithoutSynchronousRetryLoop() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.caster.abilities().add(new AbilityInstance("charge",Trigger.ON_DEATH,0,TargetMode.NEAREST,16,100,1,0,Map.of()));
   var reads=new AtomicInteger();
   when(f.entity.isDead()).thenAnswer(i->{if(reads.incrementAndGet()>30)throw new AssertionError("Bounded reproduction: synchronous ON_DEATH retry loop exceeded 30 attempts");return true;});
   var engine=new AbilityEngine(f.registry,f.platform,new Random(1));
   assertDoesNotThrow(()->engine.fire(Trigger.ON_DEATH,f.caster,null,0));
   int afterDeath=reads.get();
   engine.tick(List.of(f.caster),1);
   assertTrue(reads.get()<=afterDeath+1,"A rejected death cast must not remain pending");
  }
 }
 @Test void rejectedDeathComboStepMustNotScheduleAnotherAttempt() {
  try(var f=new CombatServiceTest.Fixture()) {
   when(f.entity.isDead()).thenReturn(true);
   var combo=new ComboDef("final",Trigger.ON_DEATH,0,TargetMode.NEAREST,16,100,
       List.of(new ComboStep("charge",Map.of(),0),new ComboStep("sonic_boom",Map.of(),0)));
   var runner=new ComboRunner(f.registry);
   assertDoesNotThrow(()->runner.start(combo,f.caster,List.of(f.player),null,"combo:final#0"));
   assertTrue(f.tasks.isEmpty(),"Rejected death steps get one attempt, without a scheduled retry");
   assertFalse(runner.running(f.caster,"combo:final#0"));
   verify(f.entity,atMost(30)).isDead();
  }
 }
}
