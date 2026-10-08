package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class VirtualHealthTest {
    static { PaperApiTestBootstrap.initialize(); }
    @org.junit.jupiter.api.AfterEach void clearCallbacks() { callbacks.clear(); }
    static final Map<MobCombatListener,List<Runnable>> callbacks=new IdentityHashMap<>();
    static MobCombatListener listener() {
        var queue=new ArrayList<Runnable>();
        var listener=new MobCombatListener(queue::add);callbacks.put(listener,queue);return listener;
    }
    static void flush(MobCombatListener listener) {
        var queue=callbacks.get(listener);var ready=List.copyOf(queue);queue.clear();ready.forEach(Runnable::run);
    }
    static EntityDamageEvent event(LivingEntity entity,EntityDamageEvent.DamageCause cause,
            Map<EntityDamageEvent.DamageModifier,Double> modifiers) {
        var functions=new EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>>(EntityDamageEvent.DamageModifier.class);
        modifiers.keySet().forEach(m->functions.put(m,x->0d));
        return new EntityDamageEvent(entity,cause,mock(DamageSource.class),new EnumMap<>(modifiers),functions);
    }
    static class Body {
        final Mob mob=mock(Mob.class);
        final Map<NamespacedKey,Object> data=new HashMap<>();
        final double[] physical={20},maximum={20};
        float absorption,lastHurt;
        boolean dead;
        int preDeathEquipmentDamage;
        Body(double health) {
            PaperApiTestBootstrap.initialize();
            var pdc=mock(PersistentDataContainer.class);when(mob.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(any(),any())).thenAnswer(c->data.get(c.getArgument(0)));
            when(pdc.has(any(),any())).thenAnswer(c->data.containsKey(c.getArgument(0)));
            doAnswer(c->{data.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(),any(),any());
            doAnswer(c->{data.remove(c.getArgument(0));return null;}).when(pdc).remove(any());
            var max=mock(AttributeInstance.class);when(mob.getAttribute(Attribute.MAX_HEALTH)).thenReturn(max);
            when(max.getValue()).thenAnswer(c->maximum[0]);
            doAnswer(c->{maximum[0]=c.getArgument(0);return null;}).when(max).setBaseValue(anyDouble());
            when(mob.getHealth()).thenAnswer(c->physical[0]);
            doAnswer(c->{physical[0]=(double)(float)(double)c.getArgument(0);return null;}).when(mob).setHealth(anyDouble());
            when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
            when(mob.isValid()).thenReturn(true);
            when(mob.isDead()).thenAnswer(c->dead);
            data.put(MobKeys.SESSION,"test");data.put(MobKeys.TEMPLATE,"test");
            MobHealth.configure(mob,health,false);
        }
        EntityDamageEvent damage(double amount,MobCombatListener listener) {
            return damage(amount,listener,EntityDamageEvent.DamageCause.CUSTOM);
        }
        EntityDamageEvent damage(double amount,MobCombatListener listener,EntityDamageEvent.DamageCause cause) {
            var event=new EntityDamageEvent(mob,cause,mock(DamageSource.class),amount);
            apply(event,listener);return event;
        }
        EntityDamageEvent hit(double amount,boolean withinCooldown,MobCombatListener listener) {
            // hurtServer rejects <= lastHurt before dispatching any damage event.
            if(withinCooldown && (float)amount<=lastHurt)return null;
            var modifiers=new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
            modifiers.put(EntityDamageEvent.DamageModifier.BASE,amount);
            if(withinCooldown)modifiers.put(EntityDamageEvent.DamageModifier.INVULNERABILITY_REDUCTION,-(double)lastHurt);
            double afterCooldown=amount-(withinCooldown?lastHurt:0);
            modifiers.put(EntityDamageEvent.DamageModifier.ABSORPTION,-Math.min(absorption,afterCooldown));
            var event=event(mob,EntityDamageEvent.DamageCause.CUSTOM,modifiers);
            apply(event,listener);return event;
        }
        void apply(EntityDamageEvent event,MobCombatListener listener) {
            listener.damaged(event);
            if(!event.isCancelled()) {
                // actuallyHurt consumes the event absorption modifier before writing HP.
                if(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
                    absorption=Math.max(0,absorption+(float)event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
                // computeAmountFromEntityDamageEvent excludes armor/absorption/i-frame reduction.
                lastHurt=0;
                for(var modifier:List.of(EntityDamageEvent.DamageModifier.BASE,EntityDamageEvent.DamageModifier.BLOCKING,
                        EntityDamageEvent.DamageModifier.FREEZING,EntityDamageEvent.DamageModifier.HARD_HAT))
                    if(event.isApplicable(modifier))lastHurt+=(float)event.getDamage(modifier);
                // LivingEntity.actuallyHurt uses float health - (float) final event damage.
                physical[0]=Math.clamp((float)physical[0]-(float)event.getFinalDamage(),0,(float)maximum[0]);
                // Non-player actuallyHurt also subtracts final damage from absorption.
                absorption=Math.max(0,absorption-(float)event.getFinalDamage());
                if(physical[0]==0) {
                    // Mob.dropCustomDeathLoot mutates equipment before callEntityDeathEvent.
                    preDeathEquipmentDamage++;
                    dead=true;
                }
            }
            flush(listener);
        }
    }
    // Paper 26.3 build 157: LivingEntity.hurtServer stores BASE (+ blocking,
    // freezing and hard-hat), not final health damage, in lastHurt.
    @Test void equalHitsWithinPaperImmunityWindowKeepLastHurtInOriginalUnits() {
        var f=new Body(5000);var listener=listener();
        f.hit(1000,false,listener);
        assertEquals(1000,f.lastHurt);
        assertNull(f.hit(1000,true,listener));
        assertEquals(4000,MobHealth.current(f.mob),1e-8);
        var larger=f.hit(1400,true,listener);
        assertEquals(1400,larger.getDamage());assertEquals(400,larger.getFinalDamage());
        assertEquals(1400,f.lastHurt);assertEquals(3600,MobHealth.current(f.mob));
    }
    @Test void paperFloatCannotKillANonlethalDoubleRemainder() {
        var f=new Body(5000.0001);f.damage(5000,listener());
        assertEquals(5000.0001-5000,MobHealth.current(f.mob));
        assertTrue(f.physical[0]>0,"Positive virtual HP must survive the native float subtraction");
        assertFalse(f.dead);
        f.damage(MobHealth.current(f.mob),listener());
        assertTrue(f.dead);assertEquals(0,MobHealth.current(f.mob));
    }
    // actuallyHurt subtracts -ABSORPTION from the native pool after event dispatch.
    @Test void vanillaAbsorptionConsumesTwoPointsFromFourWithoutScaling() {
        var f=new Body(5000);var listener=listener();f.absorption=4;
        var event=f.hit(2,false,listener);
        assertEquals(2,event.getDamage());assertEquals(0,event.getFinalDamage());
        assertEquals(-2,event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        assertEquals(2,f.absorption);assertEquals(5000,MobHealth.current(f.mob));
        f.hit(5,false,listener);
        assertEquals(0,f.absorption);assertEquals(4997,MobHealth.current(f.mob));
    }
    // PoisonMobEffect.applyEffectTick only calls hurtServer when physical HP > 1.
    @Test void nativePoisonRemainsEligibleAtFourVirtualHp() {
        var f=new Body(5000);var listener=listener();MobHealth.setCurrent(f.mob,4);
        for(int expected=3;expected>=1;expected--) {
            assertTrue(f.physical[0]>1,"Paper must keep issuing poison ticks above one virtual HP");
            f.damage(1,listener,EntityDamageEvent.DamageCause.POISON);
            assertEquals(expected,MobHealth.current(f.mob));assertFalse(f.dead);
        }
        assertTrue(f.physical[0]<=1,"The native poison gate stops at one virtual HP");
        f.damage(100,listener,EntityDamageEvent.DamageCause.POISON);
        assertEquals(1,MobHealth.current(f.mob));assertFalse(f.dead);
    }
    @Test void ordinaryDamageAndHealingRemainUnchangedAndRepeatedHitsKillExactlyAtConfiguredLife() {
        var f=new Body(5000);var listener=listener();
        assertEquals(1024,f.physical[0]);assertEquals(5000,MobHealth.current(f.mob));
        var event=f.damage(1000,listener);assertEquals(1000,event.getFinalDamage(),1e-9);
        assertEquals(4000,MobHealth.current(f.mob),1e-9);assertEquals(.8,MobHealth.fraction(f.mob),1e-9);
        var heal=new EntityRegainHealthEvent(f.mob,500,EntityRegainHealthEvent.RegainReason.CUSTOM);
        assertEquals(500,heal.getAmount(),1e-9);listener.healed(heal);
        f.physical[0]=(double)(float)Math.min(f.maximum[0],f.physical[0]+heal.getAmount());flush(listener);
        assertEquals(4500,MobHealth.current(f.mob),1e-9);
        f.damage(4499,listener);assertTrue(f.physical[0]>0);assertEquals(1,MobHealth.current(f.mob),1e-8);
        f.damage(1,listener);assertEquals(0,f.physical[0]);assertEquals(0,MobHealth.current(f.mob));
        var repeated=new Body(5000);
        for(int i=0;i<4;i++) {repeated.damage(1000,listener);assertTrue(repeated.physical[0]>0);}
        repeated.damage(1000,listener);assertEquals(0,repeated.physical[0]);
    }
    @Test void healingByAbilitiesUsesVirtualAmountsAndClampsAtConfiguredMaximum() {
        var f=new Body(5000);f.damage(3000,listener());
        MobHealth.heal(f.mob,500);assertEquals(2500,MobHealth.current(f.mob),1e-8);
        MobHealth.heal(f.mob,10000);assertEquals(5000,MobHealth.current(f.mob));
    }
    @Test void killAndVoidBypassVirtualScaling() {
        for(var cause:List.of(EntityDamageEvent.DamageCause.KILL,EntityDamageEvent.DamageCause.VOID)) {
            var f=new Body(5000);f.damage(4,listener(),cause);
            assertEquals(0,f.physical[0],cause.toString());assertEquals(0,MobHealth.current(f.mob));
        }
    }
    @Test void phaseThresholdsAndPredictionUseTheSameVirtualFraction() {
        var f=new Body(5000);var listener=listener();
        var pending=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),2000);
        assertEquals(.6,MobHealth.fractionAfterDamage(f.mob,pending),1e-9);
        f.damage(2000,listener);
        var phase=new PhaseDef(.66,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,0);
        assertEquals(0,BossController.nextPhase(List.of(phase),-1,MobHealth.fraction(f.mob)));
    }
    @Test void phaseOverridesPreserveFractionScaleByPlayersAndRemoveVirtualMarkersBelowLimit() {
        var f=new Body(5000);f.damage(2500,listener());
        f.data.put(MobKeys.HEALTH_MULTIPLIER,5d);
        var factory=new MobFactory(mock(dev.dasan.customdungeons.mob.MobsPlatform.class));
        factory.applyAttributes(f.mob,new MobAttributes(Map.of("max-health",2000d)));
        assertEquals(10000,MobHealth.maximum(f.mob));assertEquals(5000,MobHealth.current(f.mob),1e-8);
        factory.applyAttributes(f.mob,new MobAttributes(Map.of("max-health",100d,"damage",0d,"gravity",-.5)));
        assertEquals(500,MobHealth.maximum(f.mob));assertEquals(250,MobHealth.current(f.mob),1e-8);
        assertFalse(f.data.containsKey(MobKeys.VIRTUAL_MAX_HEALTH));
    }
    @Test void bossBarUsesVirtualLifeAndPhaseOverridesAndHealingApplyOnce() {
        var f=new Body(5000);when(f.mob.isValid()).thenReturn(true);
        var session=mock(dev.dasan.customdungeons.mob.MobHost.class);
        when(session.players()).thenReturn(List.of());
        var phase=new PhaseDef(.5,false,List.of(),List.of(),Map.of(),List.of(),10,List.of(),null,null,null,null,0,
                new MobAttributes(Map.of("max-health",10000d,"damage",4000d)));
        var template=new MobTemplate("boss","ZOMBIE","Boss",5000,3000,0,0,0,Map.of(),List.of(),List.of(),List.of(),
                true,"RED",null,List.of(phase),false);
        var boss=new dev.dasan.customdungeons.runtime.ActiveMob(f.mob,template,session);
        var controller=new BossController(new MobFactory(mock(dev.dasan.customdungeons.mob.MobsPlatform.class)),Map.of());
        assertEquals(1,controller.barFor(boss).progress());
        f.damage(2500,listener());controller.onDamaged(boss,1);
        assertEquals(0,boss.phaseIndex());assertEquals(10000,MobHealth.maximum(f.mob));
        assertEquals(6000,MobHealth.current(f.mob),1e-8);assertEquals(.6,controller.barFor(boss).progress(),1e-6);
        controller.onDamaged(boss,2);assertEquals(6000,MobHealth.current(f.mob),1e-8);
        assertEquals(4000d,f.data.get(MobKeys.VIRTUAL_ATTACK_DAMAGE));
    }
    @Test void fractionalHealthBelowMinecraftAttributeMinimumStillDiesExactly() {
        var f=new Body(.5);assertEquals(1,f.maximum[0]);assertEquals(.5,MobHealth.current(f.mob));
        f.damage(.25,listener());assertEquals(.25,MobHealth.current(f.mob));
        f.damage(.25,listener());assertEquals(0,f.physical[0]);
    }
    @Test void scalingThatCrosses1024AndExtremeFiniteValuesRemainFinite() {
        assertEquals(1500,MobHealth.scaledMaximum(1000,Scaling.healthMultiplier(3,1,.25)));
        assertEquals(Double.MAX_VALUE,MobHealth.scaledMaximum(Double.MAX_VALUE,2));
        var f=new Body(Double.MAX_VALUE);assertTrue(Double.isFinite(MobHealth.current(f.mob)));
        assertEquals(1,MobHealth.fraction(f.mob));f.damage(Double.MAX_VALUE,listener());assertEquals(0,f.physical[0]);
    }
    @Test void physicalMutationCannotOverwriteVirtualHpAndVanillaStillUsesPhysicalHp() {
        var vanilla=new Body(100);assertFalse(MobHealth.virtual(vanilla.mob));
        vanilla.damage(25,listener());assertEquals(75,MobHealth.current(vanilla.mob));
        var f=new Body(5000);f.mob.setHealth(512);assertEquals(5000,MobHealth.current(f.mob));
        MobHealth.mirror(f.mob);assertEquals(1024,f.physical[0]);
    }
    @Test void finalArmorAndResistanceDamageIsSubtractedWithoutChangingAnyModifier() {
        var f=new Body(5000);var modifiers=new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE,1000d);modifiers.put(EntityDamageEvent.DamageModifier.ARMOR,-200d);
        modifiers.put(EntityDamageEvent.DamageModifier.RESISTANCE,-100d);
        var functions=new EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>>(EntityDamageEvent.DamageModifier.class);
        modifiers.keySet().forEach(m->functions.put(m,x->0d));
        var event=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),new EnumMap<>(modifiers),functions);
        f.apply(event,listener());assertEquals(700,event.getFinalDamage());
        assertEquals(1000,event.getDamage());assertEquals(-200,event.getDamage(EntityDamageEvent.DamageModifier.ARMOR));
        assertEquals(-100,event.getDamage(EntityDamageEvent.DamageModifier.RESISTANCE));
        assertEquals(4300,MobHealth.current(f.mob));
    }
    @Test void attackAbove2048IsAppliedButAbilityProjectileAndCancelledDamageAreUntouched() {
        var source=new Body(5000);var target=new Body(5000);source.data.put(MobKeys.VIRTUAL_ATTACK_DAMAGE,3000d);
        var listener=listener();
        var melee=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,mock(DamageSource.class),2048);
        listener.melee(melee);assertEquals(3000,melee.getDamage());target.apply(melee,listener);
        assertEquals(2000,MobHealth.current(target.mob),1e-8);assertFalse(target.dead);
        doAnswer(c->{var ability=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,mock(DamageSource.class),(double)c.getArgument(0));
            listener.melee(ability);assertEquals(7,ability.getDamage());return null;}).when(target.mob).damage(anyDouble(),eq(source.mob));
        MobCombatListener.abilityDamage(target.mob,7,source.mob);
        var projectile=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.PROJECTILE,mock(DamageSource.class),4);
        listener.melee(projectile);assertEquals(4,projectile.getDamage());
        var cancelled=new EntityDamageEvent(target.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),1000);
        cancelled.setCancelled(true);listener.damaged(cancelled);assertEquals(2000,MobHealth.current(target.mob),1e-8);
    }
    @Test void poisonDoesNotRaiseLifeAlreadyBelowOneAndPredictionUsesItsFloor() {
        var f=new Body(5000);MobHealth.setCurrent(f.mob,1.25);
        var event=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.POISON,mock(DamageSource.class),10);
        assertEquals(1d/5000,MobHealth.fractionAfterDamage(f.mob,event));
        f.apply(event,listener());assertEquals(1,MobHealth.current(f.mob));
        MobHealth.setCurrent(f.mob,.5);f.damage(1,listener(),EntityDamageEvent.DamageCause.POISON);
        assertEquals(.5,MobHealth.current(f.mob));assertFalse(f.dead);assertTrue(f.physical[0]>0);
    }
    @Test void cancelledDamageAndHealingDoNotChangePdcOrQueueMirrors() {
        var f=new Body(5000);var listener=listener();
        var damage=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),1000);
        damage.setCancelled(true);f.apply(damage,listener);
        MobHealth.setCurrent(f.mob,4000);
        var heal=new EntityRegainHealthEvent(f.mob,1000,EntityRegainHealthEvent.RegainReason.CUSTOM);
        heal.setCancelled(true);listener.healed(heal);
        assertEquals(4000,MobHealth.current(f.mob));assertTrue(callbacks.get(listener).isEmpty());
    }
    @Test void pendingDamageAndHealingShareOneMirrorAndUseTheLatestDoubleValue() {
        var f=new Body(5000);var second=new Body(5000);var listener=listener();
        var hit=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),1000);
        listener.damaged(hit);f.physical[0]-=1000;
        // Reading between event dispatch and Paper's physical write cannot derive HP from float.
        assertEquals(4000,MobHealth.current(f.mob));
        var heal=new EntityRegainHealthEvent(f.mob,500,EntityRegainHealthEvent.RegainReason.CUSTOM);
        listener.healed(heal);f.physical[0]=(float)Math.min(f.maximum[0],f.physical[0]+heal.getAmount());
        listener.damaged(new EntityDamageEvent(second.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),1000));
        second.physical[0]-=1000;
        assertEquals(1,callbacks.get(listener).size());assertEquals(4500,MobHealth.current(f.mob));
        flush(listener);
        assertEquals((double)(float)(.9*1024),f.physical[0]);
        assertEquals((double)(float)(.8*1024),second.physical[0]);
    }
    @Test void terminalVirtualDeathCannotConsumeATotemButVanillaCanResurrect() {
        var f=new Body(5000);var listener=listener();MobHealth.remember(f.mob,0);
        var resurrection=new EntityResurrectEvent(f.mob,org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        listener.resurrect(resurrection);assertTrue(resurrection.isCancelled());
        var vanilla=new Body(100);var nativeResurrection=new EntityResurrectEvent(vanilla.mob,null);
        listener.resurrect(nativeResurrection);assertFalse(nativeResurrection.isCancelled());
    }
    @Test void physicallyLethalButVirtuallyNonlethalHitNeverRunsPaperDeathLoot() {
        var f=new Body(5000);var listener=listener();f.absorption=4;
        var hit=f.hit(3004,false,listener);
        assertEquals(2000,MobHealth.current(f.mob));
        assertEquals(0,f.absorption);
        assertEquals(-4,hit.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        assertFalse(f.dead);
        assertEquals(0,f.preDeathEquipmentDamage,"Survival must prevent pre-event loot side effects");
        assertTrue((float)1024-(float)hit.getFinalDamage()>0);
        f.damage(2000,listener);
        assertTrue(f.dead);assertEquals(1,f.preDeathEquipmentDamage);
    }
    @Test void physicalDeathGuardAlsoSurvivesDoubleModifierCancellationWithoutCreatingAbsorption() {
        var f=new Body(1e20);f.absorption=4;
        var hit=event(f.mob,EntityDamageEvent.DamageCause.CUSTOM,Map.of(
                EntityDamageEvent.DamageModifier.BASE,1e20,
                EntityDamageEvent.DamageModifier.ARMOR,-7.848484008596978e19,
                EntityDamageEvent.DamageModifier.RESISTANCE,-1.4907038950941166e18,
                EntityDamageEvent.DamageModifier.MAGIC,-7.530955490568924e18,
                EntityDamageEvent.DamageModifier.ABSORPTION,-4d));
        double remaining=1e20-hit.getFinalDamage();
        f.apply(hit,listener());
        assertEquals(remaining,MobHealth.current(f.mob));assertFalse(f.dead);
        assertEquals(0,f.preDeathEquipmentDamage);
        assertTrue(hit.getFinalDamage()>=0,"Negative final damage would create native absorption");
        assertEquals(-4,hit.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));assertEquals(0,f.absorption);
    }
    @Test void silentRemovalZerosAuthoritativeHpBeforeRemovalWithoutNativeDeath() {
        var f=new Body(5000);
        doAnswer(c->{assertEquals(0,MobHealth.current(f.mob));return null;}).when(f.mob).remove();
        MobHealth.terminate(f.mob,false);
        verify(f.mob).remove();assertEquals(1024,f.physical[0]);assertEquals(0,f.preDeathEquipmentDamage);
        var item=mock(org.bukkit.entity.Item.class);MobHealth.terminate(item,false);verify(item).remove();
    }
    @Test void nativeRegenerationRemainsEligibleWheneverVirtualHealthIsMissing() {
        var f=new Body(100_000_000);var listener=listener();f.damage(1,listener);
        assertEquals(99_999_999,MobHealth.current(f.mob));
        // RegenerationMobEffect compares native getHealth() < getMaxHealth() as floats.
        assertTrue((float)f.physical[0]<(float)f.maximum[0]);
        var heal=new EntityRegainHealthEvent(f.mob,1,EntityRegainHealthEvent.RegainReason.MAGIC_REGEN);
        listener.healed(heal);f.physical[0]=(float)Math.min(f.maximum[0],f.physical[0]+heal.getAmount());flush(listener);
        assertEquals(100_000_000,MobHealth.current(f.mob));assertEquals(1024,f.physical[0]);
    }
    @Test void healingEventClampsAtVirtualMaximum() {
        var f=new Body(5000);var listener=listener();f.damage(1000,listener);
        listener.healed(new EntityRegainHealthEvent(f.mob,10000,EntityRegainHealthEvent.RegainReason.MAGIC_REGEN));
        flush(listener);assertEquals(5000,MobHealth.current(f.mob));assertEquals(1024,f.physical[0]);
    }
    @Test void positiveDoubleUnderflowHasAPositiveFloatMirror() {
        var f=new Body(Double.MAX_VALUE);MobHealth.setCurrent(f.mob,Double.MIN_VALUE);
        assertEquals(Double.MIN_VALUE,MobHealth.current(f.mob));assertTrue(f.physical[0]>0);
        assertFalse(f.dead);
    }

}
