package dev.dasan.customdungeons.mob;
import java.util.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"deprecation","removal"})
class Round3ExtremeDamageTest {
 @org.junit.jupiter.api.AfterEach void clearCallbacks() { VirtualHealthTest.callbacks.clear(); }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(doubles={1e30,1e40,Double.MAX_VALUE,Double.POSITIVE_INFINITY})
 void legacyExtremeMeleeOverrideCannotProduceNanWithPaperFloatDefenseFunctions(double configuredDamage) {
  var source = new VirtualHealthTest.Body(5000);
  var target = new VirtualHealthTest.Body(5000);
  source.data.put(MobKeys.VIRTUAL_ATTACK_DAMAGE, configuredDamage);
  var modifiers = new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
  var functions = new EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>>(EntityDamageEvent.DamageModifier.class);
  for(var modifier: EntityDamageEvent.DamageModifier.values()) {
   modifiers.put(modifier, modifier==EntityDamageEvent.DamageModifier.BASE ? 2048d : 0d);
   functions.put(modifier, amount -> 0d);
  }
  // Exact Paper 26.3 build 157 lambda$handleEntityDamage$4 for armor=0:
  // -(amount - getDamageAfterArmorAbsorb(source, amount.floatValue())).
  // CombatRules returns amount.floatValue() * (1 - 0/25).
  functions.put(EntityDamageEvent.DamageModifier.ARMOR, amount -> -(amount-(double)(amount.floatValue()*1f)));
  // Exact lambda$handleEntityDamage$6 with no enchantment protection.
  functions.put(EntityDamageEvent.DamageModifier.MAGIC, amount -> -(amount-(double)amount.floatValue()));
  // Exact lambda$handleEntityDamage$7 with zero absorption.
  functions.put(EntityDamageEvent.DamageModifier.ABSORPTION, amount -> -Math.max(amount-Math.max(amount-0,0),0));
  var hit = new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,
      mock(DamageSource.class),modifiers,functions);
  hit=floatOnly(hit);
  var listener = VirtualHealthTest.listener();
  listener.melee(hit);
  assertEquals(1e30,hit.getDamage());
  assertTrue(Double.isFinite(hit.getFinalDamage()));
  target.apply(hit,listener);
  assertEquals(0,MobHealth.current(target.mob));
  assertEquals(0,MobHealth.fraction(target.mob));assertTrue(target.dead);
 }
 @Test void invalidFinalDamageUsesIncomingDamageToChooseDeathOrIgnoringTheHit() {
  for(double incoming:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,100,6000,1e40,Double.POSITIVE_INFINITY})
   for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
    var target=new VirtualHealthTest.Body(5000);var listener=VirtualHealthTest.listener();
    var hit=VirtualHealthTest.event(target.mob,EntityDamageEvent.DamageCause.CUSTOM,Map.of(
      EntityDamageEvent.DamageModifier.BASE,incoming,EntityDamageEvent.DamageModifier.ARMOR,invalid));
    hit=floatOnly(hit);
    boolean lethal=incoming>=5000;
    assertEquals(lethal ? 0 : 1,MobHealth.fractionAfterDamage(target.mob,hit));
    target.apply(hit,listener);
    assertEquals(lethal ? 0 : 5000,MobHealth.current(target.mob));assertEquals(lethal,target.dead);
    assertTrue(Double.isFinite(target.physical[0]));
    if(lethal) assertTrue(Float.isFinite((float)hit.getFinalDamage()));
    else assertTrue(hit.isCancelled());
   }
 }
 @Test void pendingHealingAndDirectWritesNeverPersistNonFiniteVirtualHealth() {
  var target=new VirtualHealthTest.Body(5000);MobHealth.setCurrent(target.mob,4000);
  MobHealth.remember(target.mob,Double.NaN);assertEquals(4000,MobHealth.current(target.mob));
  var listener=VirtualHealthTest.listener();
  listener.healed(new EntityRegainHealthEvent(target.mob,Double.NaN,EntityRegainHealthEvent.RegainReason.CUSTOM));
  VirtualHealthTest.flush(listener);assertEquals(4000,MobHealth.current(target.mob));
  listener.healed(new EntityRegainHealthEvent(target.mob,Double.MAX_VALUE,EntityRegainHealthEvent.RegainReason.CUSTOM));
  VirtualHealthTest.flush(listener);assertEquals(5000,MobHealth.current(target.mob));
 }
 @Test void protectedHitNeverWritesABaseOutsideTheFloatRange() {
  var target=new VirtualHealthTest.Body(Double.MAX_VALUE);
  var hit=floatOnly(VirtualHealthTest.event(target.mob,EntityDamageEvent.DamageCause.CUSTOM,Map.of(
    EntityDamageEvent.DamageModifier.BASE,-3e38,
    EntityDamageEvent.DamageModifier.FREEZING,3e38,
    EntityDamageEvent.DamageModifier.HARD_HAT,3e38)));
  target.apply(hit,VirtualHealthTest.listener());
  assertFalse(target.dead);assertEquals(Double.MAX_VALUE,MobHealth.current(target.mob));
  assertEquals(0,hit.getFinalDamage());
 }
 private static <T extends EntityDamageEvent> T floatOnly(T original) {
  T checked=spy(original);
  doAnswer(call->{assertFloat(call.getArgument(0));return call.callRealMethod();}).when(checked).setDamage(anyDouble());
  doAnswer(call->{assertFloat(call.getArgument(1));return call.callRealMethod();}).when(checked)
    .setDamage(any(EntityDamageEvent.DamageModifier.class),anyDouble());
  return checked;
 }
 private static void assertFloat(double value) {
  assertTrue(Double.isFinite(value) && Math.abs(value)<=Float.MAX_VALUE,"setDamage must receive a float-safe value: "+value);
 }
}
