package dev.dasan.customdungeons.ability.combat;
import dev.dasan.customdungeons.intelligence.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.session.SessionState;
import dev.dasan.customdungeons.ability.*;
import org.bukkit.*;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Permanent regressions for the six T57c review findings. */
class CombatReviewRegressionTest {
 static void dispatch(Object listener,org.bukkit.event.Event event) {
  for(var method:listener.getClass().getMethods()) {
   var handler=method.getAnnotation(org.bukkit.event.EventHandler.class);
   if(handler==null||method.getParameterCount()!=1||!method.getParameterTypes()[0].isInstance(event))continue;
   if(handler.ignoreCancelled()&&event instanceof org.bukkit.event.Cancellable c&&c.isCancelled())continue;
   try {method.invoke(listener,event);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
  }
 }

 static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
 @Test void blinkMustNotStrikeAcrossFloors() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("blink_behind");
   f.positions.put(f.player,new Location(f.world,0,90,2));f.step(10);
   verify(f.entity,never()).teleport(any(Location.class));
   verify(f.player,never()).damage(anyDouble(),any(org.bukkit.entity.Entity.class));
  }
 }
 @Test void blockedDecoyHitMustNotFeedBossMemory() {
  try(var f=new CombatServiceTest.Fixture();var intelligence=new IntelligenceService(f.platform,IntelligenceRules.defaults())) {
   var boss=new ActiveMob(f.entity,f.caster.template().withIntelligence(IntelligenceDef.level(5)),f.host);
   IntelligenceService.track(boss);
   for(int t=0;t<10;t++)IntelligenceService.tick(boss,mock(AbilityEngine.class),t);
   f.cast("decoys");f.step(20);
   var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(f.player);when(hit.getDamager()).thenReturn(f.created.getFirst());
   when(hit.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);when(hit.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-4d);
   intelligence.shield(hit);
   assertEquals(0,IntelligenceService.brain(boss).memory().repetitions(f.player.getUniqueId(),"shield",0,200));
  }
 }
 @Test void dungeonDecoyExplosionMustClearBlocks() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("decoys");f.step(20);
   var event=mock(EntityExplodeEvent.class);when(event.getEntity()).thenReturn(f.created.getFirst());
   var blocks=new java.util.ArrayList<org.bukkit.block.Block>();blocks.add(f.world.getBlockAt(0,63,0));when(event.blockList()).thenReturn(blocks);
   dispatch(f.service,event);
   verify(event).setCancelled(true);
   assertTrue(blocks.isEmpty());
  }
 }

 @Test void decoyPotionMustNotApplyEffectsToForeignPlayer() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("decoys");f.step(20);
   var potion=mock(org.bukkit.entity.ThrownPotion.class);when(potion.getShooter()).thenReturn((org.bukkit.entity.Mob)f.created.getFirst());
   when(potion.getUniqueId()).thenReturn(java.util.UUID.randomUUID());doReturn(CombatServiceTest.pdc()).when(potion).getPersistentDataContainer();
   var launch=mock(ProjectileLaunchEvent.class);when(launch.getEntity()).thenReturn(potion);f.service.launch(launch);
   verify(launch).setCancelled(true);
   var splash=mock(PotionSplashEvent.class);when(splash.getPotion()).thenReturn(potion);when(splash.getAffectedEntities()).thenReturn(java.util.List.of(f.player,f.foreign));
   dispatch(f.service,splash);
   verify(splash).setCancelled(true);
   verify(splash).setIntensity(f.foreign,0);
  }
 }
 @Test void normalStrengthMustStillBePurgedDuringRoots() {
  try(var f=new CombatServiceTest.Fixture();var controls=mockStatic(dev.dasan.customdungeons.ability.control.ControlService.class)) {
   controls.when(()->dev.dasan.customdungeons.ability.control.ControlService.hasActiveControl(f.player)).thenReturn(true);
   f.effects.put(f.player,new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH,200,1));
   f.cast("purge");f.step(20);
   verify(f.player).removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
  }
 }
 @Test void decoyDeathMustNotPersistARealKill() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("decoys");f.step(20);
   var session=mock(dev.dasan.customdungeons.session.DungeonSession.class);var manager=mock(dev.dasan.customdungeons.session.SessionManager.class);
   var storage=mock(dev.dasan.customdungeons.storage.Storage.class);var definition=mock(DungeonDef.class);
   when(definition.id()).thenReturn("review");when(definition.exit()).thenReturn(new Point("test",0,64,0,0,0));
   var encounterId=f.host.id();var playerId=f.player.getUniqueId();when(session.def()).thenReturn(definition);when(session.id()).thenReturn(encounterId);when(session.survivors()).thenReturn(java.util.Set.of(playerId));
   when(manager.sessionOf(f.player.getUniqueId())).thenReturn(java.util.Optional.of(session));
   when(storage.markActive(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
   when(storage.startRun(anyString(),any(),any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(1L));
   when(storage.finishRun(anyLong(),any(),any(),any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
   var recorder=new dev.dasan.customdungeons.session.RunRecorder(storage,manager,java.util.logging.Logger.getAnonymousLogger());
   recorder.onStateChange(session,SessionState.FREE,SessionState.LOBBY);
   var copy=(org.bukkit.entity.Mob)f.created.getFirst();when(copy.getKiller()).thenReturn(f.player);
   var death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn(copy);recorder.kill(death);
   recorder.onFinished(session,dev.dasan.customdungeons.storage.RunResult.FAILED,java.util.Set.of());
   verify(storage).finishRun(anyLong(),any(),any(),argThat(records->records.getFirst().kills()==0));
  }
 }

 @ParameterizedTest @ValueSource(strings={"dungeon","live_test","world_boss","untracked"})
 void commonListenerBlocksEveryNativeNonMeleeRoute(String host) {
  try(var f=new CombatServiceTest.Fixture()) {
   Mob copy=mock(Mob.class);doReturn(CombatServiceTest.pdc()).when(copy).getPersistentDataContainer();
   copy.getPersistentDataContainer().set(CombatService.DECOY,PersistentDataType.BYTE,(byte)1);
   if(!host.equals("untracked"))copy.getPersistentDataContainer().set(dev.dasan.customdungeons.mob.MobKeys.SESSION,PersistentDataType.STRING,"encounter");
   if(host.equals("live_test"))copy.getPersistentDataContainer().set(dev.dasan.customdungeons.mob.MobsPlatform.key(host),PersistentDataType.BYTE,(byte)1);
   if(host.equals("world_boss"))copy.getPersistentDataContainer().set(dev.dasan.customdungeons.mob.MobsPlatform.key(host),PersistentDataType.STRING,"encounter");
   var prime=mock(ExplosionPrimeEvent.class);when(prime.getEntity()).thenReturn(copy);dispatch(f.service,prime);verify(prime).setCancelled(true);
   var explode=mock(EntityExplodeEvent.class);when(explode.getEntity()).thenReturn(copy);
   var blocks=new ArrayList<org.bukkit.block.Block>();blocks.add(f.world.getBlockAt(0,63,0));when(explode.blockList()).thenReturn(blocks);
   dispatch(f.service,explode);verify(explode).setCancelled(true);assertTrue(blocks.isEmpty());
   var change=mock(EntityChangeBlockEvent.class);when(change.getEntity()).thenReturn(copy);dispatch(f.service,change);verify(change).setCancelled(true);
   var shot=mock(Arrow.class);when(shot.getShooter()).thenReturn(copy);
   var launch=mock(ProjectileLaunchEvent.class);when(launch.getEntity()).thenReturn(shot);dispatch(f.service,launch);verify(launch).setCancelled(true);
   var bow=mock(EntityShootBowEvent.class);when(bow.getEntity()).thenReturn(copy);dispatch(f.service,bow);verify(bow).setCancelled(true);
   var effect=mock(EntityPotionEffectEvent.class);when(effect.getEntity()).thenReturn(f.foreign);when(effect.getSource()).thenReturn(copy);
   dispatch(f.service,effect);verify(effect).setCancelled(true);
   var combust=mock(EntityCombustByEntityEvent.class);when(combust.getCombuster()).thenReturn(copy);dispatch(f.service,combust);verify(combust).setCancelled(true);
  }
 }
 @Test void decoySpellsAndCloudsAreCancelledInsteadOfBecomingOwnedAttacks() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("decoys");f.step(20);var copy=(Mob)f.created.getFirst();
   var fangs=mock(EvokerFangs.class);when(fangs.getOwner()).thenReturn(copy);
   var spawn=mock(EntitySpawnEvent.class);when(spawn.getEntity()).thenReturn(fangs);dispatch(f.service,spawn);verify(spawn).setCancelled(true);
   var cloud=mock(AreaEffectCloud.class);when(cloud.getSource()).thenReturn(copy);
   var apply=mock(AreaEffectCloudApplyEvent.class);when(apply.getEntity()).thenReturn(cloud);dispatch(f.service,apply);verify(apply).setCancelled(true);
   var potion=mock(ThrownPotion.class);when(potion.getShooter()).thenReturn(copy);
   var lingering=mock(LingeringPotionSplashEvent.class);when(lingering.getEntity()).thenReturn(potion);dispatch(f.service,lingering);verify(lingering).setCancelled(true);
   var effect=mock(EntityPotionEffectEvent.class);when(effect.getSource()).thenReturn(potion);when(effect.getEntity()).thenReturn(f.foreign);dispatch(f.service,effect);verify(effect).setCancelled(true);
  }
 }
 @ParameterizedTest @ValueSource(ints={0,25,100})
 void decoyMeleeScalesDamageAndZeroCancelsAllEffects(int percent) {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("decoys",Map.of("damage-percent",percent));f.step(20);var copy=f.created.getFirst();
   var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(f.player);when(hit.getDamager()).thenReturn(copy);when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
   f.service.auxiliaryDamage(hit);
   if(percent==0)verify(hit).setCancelled(true);else {verify(hit).setDamage(percent/10d);verify(hit,never()).setCancelled(true);}
   var effect=mock(EntityPotionEffectEvent.class);when(effect.getEntity()).thenReturn(f.player);when(effect.getSource()).thenReturn(copy);dispatch(f.service,effect);verify(effect).setCancelled(true);
   var ranged=mock(EntityDamageByEntityEvent.class);when(ranged.getEntity()).thenReturn(f.player);when(ranged.getDamager()).thenReturn(copy);when(ranged.getCause()).thenReturn(EntityDamageEvent.DamageCause.SONIC_BOOM);
   f.service.auxiliaryDamage(ranged);verify(ranged).setCancelled(true);verify(ranged,never()).setDamage(anyDouble());
  }
 }
 @Test void commonListenerLeavesRealMobsAndTheirEffectsAlone() {
  try(var f=new CombatServiceTest.Fixture()) {
   var prime=mock(ExplosionPrimeEvent.class);when(prime.getEntity()).thenReturn(f.entity);dispatch(f.service,prime);verify(prime,never()).setCancelled(true);
   var effect=mock(EntityPotionEffectEvent.class);when(effect.getEntity()).thenReturn(f.player);when(effect.getSource()).thenReturn(f.entity);dispatch(f.service,effect);verify(effect,never()).setCancelled(true);
  }
 }
 @Test void blinkAtFourBlocksTeleportsButDoesNotHitOutsideMeleeReach() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.cast("blink_behind",Map.of("distance",4));f.step(10);
   verify(f.entity).teleport(argThat((Location at)->Math.abs(at.distance(f.player.getLocation())-4)<1e-8));
   verify(f.player,never()).damage(anyDouble(),any(Entity.class));
  }
 }
 @Test void blinkUsesTargetFloorEvenWhenTheBossStartsFarBelowIt() {
  try(var f=new CombatServiceTest.Fixture()) {
   f.positions.put(f.player,new Location(f.world,0,90,2));
   for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
    var ground=f.world.getBlockAt(x,89,z);when(ground.isSolid()).thenReturn(true);when(ground.isPassable()).thenReturn(false);when(ground.getType()).thenReturn(Material.STONE);
   }
   f.cast("blink_behind");f.step(10);
   verify(f.entity).teleport(argThat((Location at)->at.getY()==90&&at.distance(f.player.getLocation())==2));
   verify(f.player).damage(15,f.entity);
  }
 }
 @ParameterizedTest @ValueSource(strings={"purge","buff_steal"})
 void ordinaryBuffIsRemovedDuringControlButNegativeEffectsArePreserved(String ability) {
  try(var f=new CombatServiceTest.Fixture();var controls=mockStatic(dev.dasan.customdungeons.ability.control.ControlService.class)) {
   controls.when(()->dev.dasan.customdungeons.ability.control.ControlService.hasActiveControl(f.player)).thenReturn(true);
   var strength=new PotionEffect(PotionEffectType.STRENGTH,800,1);var levitation=new PotionEffect(PotionEffectType.LEVITATION,80,0);var poison=new PotionEffect(PotionEffectType.POISON,80,0);
   when(f.player.getActivePotionEffects()).thenReturn(List.of(strength,levitation,poison));
   f.cast(ability);f.step(20);verify(f.player).removePotionEffect(PotionEffectType.STRENGTH);
   verify(f.player,never()).removePotionEffect(PotionEffectType.LEVITATION);verify(f.player,never()).removePotionEffect(PotionEffectType.POISON);
   if(ability.equals("buff_steal"))verify(f.entity).addPotionEffect(argThat(p->p.getType()==PotionEffectType.STRENGTH&&p.getDuration()==600));
  }
 }
 @Test void shieldFromADecoyProjectileDoesNotFeedMemoryButRealBossShieldDoes() {
  try(var f=new CombatServiceTest.Fixture();var intelligence=new IntelligenceService(f.platform,IntelligenceRules.defaults())) {
   var boss=new ActiveMob(f.entity,f.caster.template().withIntelligence(IntelligenceDef.level(5)),f.host);IntelligenceService.track(boss);
   for(int t=0;t<10;t++)IntelligenceService.tick(boss,mock(AbilityEngine.class),t);
   f.cast("decoys");f.step(20);var arrow=mock(Arrow.class);when(arrow.getShooter()).thenReturn((Mob)f.created.getFirst());
   var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(f.player);when(hit.getDamager()).thenReturn(arrow);
   when(hit.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);when(hit.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-4d);
   intelligence.shield(hit);assertEquals(0,IntelligenceService.brain(boss).memory().repetitions(f.player.getUniqueId(),"shield",20,200));
   when(hit.getDamager()).thenReturn(f.entity);intelligence.shield(hit);assertEquals(1,IntelligenceService.brain(boss).memory().repetitions(f.player.getUniqueId(),"shield",20,200));
  }
 }
 @Test void decoyCannotObserveConsumptionOrDamageEvenIfExplicitlyTracked() {
  try(var f=new CombatServiceTest.Fixture();var intelligence=new IntelligenceService(f.platform,IntelligenceRules.defaults())) {
   f.cast("decoys");f.step(20);var copy=(Mob)f.created.getFirst();when(copy.getWorld()).thenReturn(f.world);when(copy.getLocation()).thenAnswer(i->new Location(f.world,0,64,0));
   var decoy=new ActiveMob(copy,f.caster.template().withIntelligence(IntelligenceDef.level(5)),f.host);IntelligenceService.track(decoy);
   for(int t=0;t<10;t++)IntelligenceService.tick(decoy,mock(AbilityEngine.class),t);
   var consume=mock(org.bukkit.event.player.PlayerItemConsumeEvent.class);when(consume.getPlayer()).thenReturn(f.player);var apple=mock(org.bukkit.inventory.ItemStack.class);when(apple.getType()).thenReturn(Material.GOLDEN_APPLE);when(consume.getItem()).thenReturn(apple);intelligence.consume(consume);
   assertNull(IntelligenceService.brain(decoy),"decoys cannot own intelligence or observe player actions");
  }
 }

 @Test void aPhaseChangeCannotGiveIntelligenceToADecoy() {
  try(var f=new CombatServiceTest.Fixture();var intelligence=new IntelligenceService(f.platform,IntelligenceRules.defaults())) {
   f.cast("decoys");f.step(20);var copy=(Mob)f.created.getFirst();
   var decoy=new ActiveMob(copy,f.caster.template(),f.host);
   IntelligenceService.phase(decoy,IntelligenceDef.level(5),20);
   assertNull(IntelligenceService.brain(decoy));
  }
 }
 @Test void everyMemoryEntryRejectsAMarkedMobEvenAfterItWasIndexed() {
  try(var f=new CombatServiceTest.Fixture();var intelligence=new IntelligenceService(f.platform,IntelligenceRules.defaults())) {
   var boss=new ActiveMob(f.entity,f.caster.template().withIntelligence(IntelligenceDef.level(5)),f.host);IntelligenceService.track(boss);
   for(int t=0;t<10;t++)IntelligenceService.tick(boss,mock(AbilityEngine.class),t);
   f.entity.getPersistentDataContainer().set(CombatService.DECOY,PersistentDataType.BYTE,(byte)1);
   var consume=mock(org.bukkit.event.player.PlayerItemConsumeEvent.class);when(consume.getPlayer()).thenReturn(f.player);var apple=mock(org.bukkit.inventory.ItemStack.class);when(apple.getType()).thenReturn(Material.GOLDEN_APPLE);when(consume.getItem()).thenReturn(apple);intelligence.consume(consume);
   var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(f.entity);when(hit.getDamager()).thenReturn(f.player);
   intelligence.applied(new dev.dasan.customdungeons.mob.MobDamageAppliedEvent(f.entity,hit,5,95));
   when(hit.getEntity()).thenReturn(f.player);when(hit.getDamager()).thenReturn(f.entity);
   when(hit.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);when(hit.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-4d);intelligence.shield(hit);
   assertTrue(IntelligenceService.brain(boss).memory().observations(f.player.getUniqueId(),0).isEmpty());
  }
 }

 @Test void blinkChecksTheActualDestinationIfAnotherListenerRedirectsTeleport() {
  try(var f=new CombatServiceTest.Fixture()) {
   when(f.entity.getLocation()).thenAnswer(i->new Location(f.world,12,64,0));
   // Simulate a successful teleport redirected to somewhere outside melee reach.
   when(f.entity.teleport(any(Location.class))).thenReturn(true);
   f.cast("blink_behind");f.step(10);
   verify(f.entity).teleport(any(Location.class));verify(f.player,never()).damage(anyDouble(),any(Entity.class));
  }
 }
}
