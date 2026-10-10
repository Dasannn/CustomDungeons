package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineSchedulingRegressionTest {
  static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
  @Test void unchangedPeriodicDefinitionResolvesItsWarningOnlyOnce() {
    var f=new AbilityEngineTest.Fixture(List.of(),List.of());
    var resolutions=new java.util.concurrent.atomic.AtomicInteger();
    f.registry.register(new Ability() {
      public String id(){return "budget";}
      public org.bukkit.Material icon(){return org.bukkit.Material.STONE;}
      public List<ParamSpec> params(){resolutions.incrementAndGet();return List.of();}
      public void execute(AbilityContext context){fail("Chance zero must not start");}
    });
    f.mob.abilities().add(new AbilityInstance("budget",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,0,20,Map.of()));
    var engine=f.engine();
    for(int t=0;t<100;t++)f.fire(engine,Trigger.EVERY_X_SECONDS,t);
    assertEquals(1,resolutions.get(),"An unchanged definition should not rebuild parameters every tick");
    f.mob.abilities().set(0,new AbilityInstance("budget",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,0,40,Map.of()));
    for(int t=100;t<200;t++)f.fire(engine,Trigger.EVERY_X_SECONDS,t);
    assertEquals(2,resolutions.get(),"A phase replacement must refresh its warning configuration");
  }

  @Test void allConfiguredDeathAbilitiesHaveAnOpportunityBeforeRemoval() {
    var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.ON_DEATH,0,100,1,0),
      new AbilityInstance("second",Trigger.ON_DEATH,0,TargetMode.NEAREST,10,100,1,0,Map.of())),List.of());
    var engine=f.engine();org.mockito.Mockito.when(f.entity.isDead()).thenReturn(true);
    engine.fire(Trigger.ON_DEATH,f.mob,null,0);engine.tick(List.of(f.mob),1);
    assertEquals(2,f.calls.size(),"The second death ability is lost when its caster is removed");
  }
  @Test void delayedComboStepDoesNotStarveInOtherwiseOpenTicks() {
    var f=new AbilityEngineTest.Fixture(List.of(),List.of());
    var entity=org.mockito.Mockito.mock(org.bukkit.entity.Ravager.class);
    org.mockito.Mockito.when(entity.isValid()).thenReturn(true);
    org.mockito.Mockito.when(entity.getLocation()).thenAnswer(i->f.entity.getLocation());
    org.mockito.Mockito.when(entity.getWorld()).thenReturn(f.world);
    var active=new dev.dasan.customdungeons.runtime.ActiveMob(entity,f.mob.template(),f.session);
    var combo=new ComboDef("test",Trigger.ON_HIT,0,TargetMode.NEAREST,10,100,List.of(new ComboStep("test",Map.of(),0),new ComboStep("second",Map.of(),30)));
    new ComboRunner(f.registry).start(combo,active,List.of(f.player),null,"combo:test#0");
    for(int t=1;t<=500;t++) {
      org.mockito.Mockito.when(entity.getStunnedTicks()).thenReturn(t%31==30?1:0);
      f.clock.advance(t);
    }
    assertTrue(f.calls.stream().anyMatch(c->c.startsWith("second@")),"One blocked tick per 31 permanently starved a delayed combo: "+f.calls);
  }
  @Test void ordinaryPeriodicAbilityDoesNotSkipAnEntirePeriodAfterWarning() {
    var f = new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,1,20)),List.of());
    var engine=f.engine();
    for(int t=0;t<=400;t++){f.clock.advance(t);engine.tick(List.of(f.mob),t);}
    assertTrue(f.calls.size()>=3);
    for(int i=1;i<f.calls.size();i++)assertEquals(100,Integer.parseInt(f.calls.get(i).split("@")[1])-Integer.parseInt(f.calls.get(i-1).split("@")[1]),"Warning must not add a period or extend the start cadence: "+f.calls);
  }
  @Test void phaseAdditionDoesNotMoveAnExistingInitialOffset() {
    var f=new AbilityEngineTest.Fixture(List.of(AbilityEngineTest.instance(Trigger.EVERY_X_SECONDS,5,100,1,0),
      new AbilityInstance("second",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,10,100,1,0,Map.of())),List.of());
    var reference=new AbilityEngineTest.Fixture(List.copyOf(f.mob.abilities()),List.of());
    var baseline=reference.engine();var engine=f.engine();
    for(int t=0;t<100;t++){
      reference.clock.advance(t);baseline.tick(List.of(reference.mob),t);
      f.clock.advance(t);
      if(t==10)f.mob.abilities().add(new AbilityInstance("test",Trigger.ON_HIT,0,TargetMode.NEAREST,10,100,1,0,Map.of()));
      engine.tick(List.of(f.mob),t);
    }
    assertEquals(reference.calls,f.calls,"Adding a phase ability changed an existing skill's offset");
  }
}
