package dev.dasan.customdungeons.intelligence;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class IntelligenceLifecycleTest {
 static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
 @Test void groundingFallGuardSurvivesCasterRemovalUntilLanding() {
  var world=mock(World.class);var entity=mock(Mob.class);var player=mock(Player.class);
  when(entity.getUniqueId()).thenReturn(new UUID(0,0));when(entity.isValid()).thenReturn(true);
  when(player.getUniqueId()).thenReturn(new UUID(0,1));when(player.isOnline()).thenReturn(true);when(player.isValid()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
  when(entity.getWorld()).thenReturn(world);when(player.getWorld()).thenReturn(world);
  when(entity.getLocation()).thenReturn(new Location(world,0,100,0));when(player.getLocation()).thenReturn(new Location(world,1,125,0));when(player.getVelocity()).thenReturn(new org.bukkit.util.Vector());
  var host=mock(MobHost.class);when(host.players()).thenReturn(List.of(player));var clock=mock(TickScheduler.class);when(host.scheduler()).thenReturn(clock);when(clock.currentTick()).thenReturn(15L);
  var template=new MobTemplate("review","HUSK","",100,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false).withIntelligence(IntelligenceDef.level(5));var mob=new ActiveMob(entity,template,host);
  var engine=mock(dev.dasan.customdungeons.ability.AbilityEngine.class);
  try(var bukkit=mockStatic(Bukkit.class);var service=new IntelligenceService(mock(MobsPlatform.class),IntelligenceRules.defaults())) {
   bukkit.when(()->Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);IntelligenceService.track(mob);var brain=IntelligenceService.brain(mob);
   brain.memory().record(player.getUniqueId(),"flight","FIREWORK_ROCKET",0,0);brain.memory().record(player.getUniqueId(),"flight","FIREWORK_ROCKET",0,0);assertEquals(1,brain.evaluate(0).size());
   // Index before the 15-tick warning finishes, then apply grounding.
   IntelligenceService.tick(mob,engine,10);IntelligenceService.tick(mob,engine,15);verify(player).setGliding(false);
   var fall=mock(EntityDamageEvent.class);when(fall.getEntity()).thenReturn(player);when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);service.fall(fall);verify(fall).setCancelled(true);
   IntelligenceService.cleanup(mob);var landing=mock(EntityDamageEvent.class);when(landing.getEntity()).thenReturn(player);when(landing.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);service.fall(landing);
   verify(landing).setCancelled(true);
  }
 }
 @Test void rememberedPatternStillRespectsConfiguredDetectionWindow() {
  var memory=new EncounterMemory();UUID player=UUID.randomUUID();var definition=IntelligenceDef.level(5).withAdvanced("window",1);var brain=new IntelligenceBrain(definition,memory,IntelligenceRules.defaults());
  memory.record(player,"critical","ENTITY_ATTACK",1,0);memory.remember(player,"critical");memory.record(player,"critical","ENTITY_ATTACK",1,60);
  assertTrue(brain.evaluate(60).isEmpty(),"two hits three seconds apart must not meet a one-second window");
 }
 @Test void recallOnlyLowersRepetitionsAndNeverBelowTwo() {
  UUID player=UUID.randomUUID();var memory=new EncounterMemory();var definition=IntelligenceDef.level(5).withAdvanced("repetitions",4).withAdvanced("window",1);
  var brain=new IntelligenceBrain(definition,memory,IntelligenceRules.defaults());
  for(int tick=0;tick<3;tick++)memory.record(player,"critical","ENTITY_ATTACK",1,tick);
  assertTrue(brain.evaluate(2).isEmpty());memory.remember(player,"critical");assertEquals(1,brain.evaluate(2).size());
  brain.clear();memory.record(player,"critical","ENTITY_ATTACK",1,100);memory.remember(player,"critical");
  var minimum=new IntelligenceBrain(definition.withAdvanced("repetitions",2),memory,IntelligenceRules.defaults());
  assertTrue(minimum.evaluate(100).isEmpty());memory.record(player,"critical","ENTITY_ATTACK",1,101);assertEquals(1,minimum.evaluate(101).size());
 }
}
