package dev.dasan.customdungeons.mob;

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
    static class Body {
        final Mob mob=mock(Mob.class);
        final Map<NamespacedKey,Object> data=new HashMap<>();
        final double[] physical={20},maximum={20};
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
            data.put(MobKeys.SESSION,"test");data.put(MobKeys.TEMPLATE,"test");
            MobHealth.configure(mob,health,false);
        }
        EntityDamageEvent damage(double amount,MobCombatListener listener) {
            return damage(amount,listener,EntityDamageEvent.DamageCause.CUSTOM);
        }
        EntityDamageEvent damage(double amount,MobCombatListener listener,EntityDamageEvent.DamageCause cause) {
            var event=new EntityDamageEvent(mob,cause,mock(DamageSource.class),amount);
            listener.damage(event);listener.damaged(event);
            physical[0]=(double)(float)Math.max(0,physical[0]-(float)event.getFinalDamage());return event;
        }
    }
    @Test void incomingDamageAndHealingAreScaledAndRepeatedHitsKillExactlyAtConfiguredLife() {
        var f=new Body(5000);var listener=new MobCombatListener();
        assertEquals(1024,f.physical[0]);assertEquals(5000,MobHealth.current(f.mob));
        var event=f.damage(1000,listener);assertEquals(204.8,event.getFinalDamage(),1e-9);
        assertEquals(4000,MobHealth.current(f.mob),1e-9);assertEquals(.8,MobHealth.fraction(f.mob),1e-9);
        var heal=new EntityRegainHealthEvent(f.mob,500,EntityRegainHealthEvent.RegainReason.CUSTOM);
        listener.heal(heal);assertEquals(102.4,heal.getAmount(),1e-9);listener.healed(heal);
        f.physical[0]=(double)(float)(f.physical[0]+heal.getAmount());
        assertEquals(4500,MobHealth.current(f.mob),1e-9);
        f.damage(4499,listener);assertTrue(f.physical[0]>0);assertEquals(1,MobHealth.current(f.mob),1e-8);
        f.damage(1,listener);assertEquals(0,f.physical[0]);assertEquals(0,MobHealth.current(f.mob));
        var repeated=new Body(5000);
        for(int i=0;i<4;i++) {repeated.damage(1000,listener);assertTrue(repeated.physical[0]>0);}
        repeated.damage(1000,listener);assertEquals(0,repeated.physical[0]);
    }
    @Test void healingByAbilitiesUsesVirtualAmountsAndClampsAtConfiguredMaximum() {
        var f=new Body(5000);f.damage(3000,new MobCombatListener());
        MobHealth.heal(f.mob,500);assertEquals(2500,MobHealth.current(f.mob),1e-8);
        MobHealth.heal(f.mob,10000);assertEquals(5000,MobHealth.current(f.mob));
    }
    @Test void killAndVoidBypassVirtualScaling() {
        for(var cause:List.of(EntityDamageEvent.DamageCause.KILL,EntityDamageEvent.DamageCause.VOID)) {
            var f=new Body(5000);f.damage(4,new MobCombatListener(),cause);
            assertEquals(0,f.physical[0],cause.toString());assertEquals(0,MobHealth.current(f.mob));
        }
    }
    @Test void phaseThresholdsAndPredictionUseTheSameVirtualFraction() {
        var f=new Body(5000);var listener=new MobCombatListener();
        var pending=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),2000);
        assertEquals(.6,MobHealth.fractionAfterDamage(f.mob,MobCombatListener.projectedPhysicalDamage(pending)),1e-9);
        listener.damage(pending);
        assertEquals(.6,MobHealth.fractionAfterDamage(f.mob,MobCombatListener.projectedPhysicalDamage(pending)),1e-9);
        f.damage(2000,listener);
        var phase=new PhaseDef(.66,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,0);
        assertEquals(0,BossController.nextPhase(List.of(phase),-1,MobHealth.fraction(f.mob)));
    }
    @Test void phaseOverridesPreserveFractionScaleByPlayersAndRemoveVirtualMarkersBelowLimit() {
        var f=new Body(5000);f.damage(2500,new MobCombatListener());
        f.data.put(MobKeys.HEALTH_MULTIPLIER,5d);
        var factory=new MobFactory(mock(dev.dasan.customdungeons.config.PluginConfig.class));
        factory.applyAttributes(f.mob,new MobAttributes(Map.of("max-health",2000d)));
        assertEquals(10000,MobHealth.maximum(f.mob));assertEquals(5000,MobHealth.current(f.mob),1e-8);
        factory.applyAttributes(f.mob,new MobAttributes(Map.of("max-health",100d,"damage",0d,"gravity",-.5)));
        assertEquals(500,MobHealth.maximum(f.mob));assertEquals(250,MobHealth.current(f.mob),1e-8);
        assertFalse(f.data.containsKey(MobKeys.VIRTUAL_MAX_HEALTH));
    }
    @Test void bossBarUsesVirtualLifeAndPhaseOverridesAndHealingApplyOnce() {
        var f=new Body(5000);when(f.mob.isValid()).thenReturn(true);
        var session=mock(dev.dasan.customdungeons.runtime.SessionContext.class);
        when(session.players()).thenReturn(List.of());
        var phase=new PhaseDef(.5,false,List.of(),List.of(),Map.of(),List.of(),10,List.of(),null,null,null,null,0,
                new MobAttributes(Map.of("max-health",10000d,"damage",4000d)));
        var template=new MobTemplate("boss","ZOMBIE","Boss",5000,3000,0,0,0,Map.of(),List.of(),List.of(),List.of(),
                true,"RED",null,List.of(phase),false);
        var boss=new dev.dasan.customdungeons.runtime.ActiveMob(f.mob,template,session);
        var controller=new BossController(new MobFactory(mock(dev.dasan.customdungeons.config.PluginConfig.class)),Map.of());
        assertEquals(1,controller.barFor(boss).progress());
        f.damage(2500,new MobCombatListener());controller.onDamaged(boss,1);
        assertEquals(0,boss.phaseIndex());assertEquals(10000,MobHealth.maximum(f.mob));
        assertEquals(6000,MobHealth.current(f.mob),1e-8);assertEquals(.6,controller.barFor(boss).progress(),1e-6);
        controller.onDamaged(boss,2);assertEquals(6000,MobHealth.current(f.mob),1e-8);
        assertEquals(4000d,f.data.get(MobKeys.VIRTUAL_ATTACK_DAMAGE));
    }
    @Test void fractionalHealthBelowMinecraftAttributeMinimumStillDiesExactly() {
        var f=new Body(.5);assertEquals(1,f.maximum[0]);assertEquals(.5,MobHealth.current(f.mob));
        f.damage(.25,new MobCombatListener());assertEquals(.25,MobHealth.current(f.mob));
        f.damage(.25,new MobCombatListener());assertEquals(0,f.physical[0]);
    }
    @Test void scalingThatCrosses1024AndExtremeFiniteValuesRemainFinite() {
        assertEquals(1500,MobHealth.scaledMaximum(1000,Scaling.healthMultiplier(3,1,.25)));
        assertEquals(Double.MAX_VALUE,MobHealth.scaledMaximum(Double.MAX_VALUE,2));
        var f=new Body(Double.MAX_VALUE);assertTrue(Double.isFinite(MobHealth.current(f.mob)));
        assertEquals(1,MobHealth.fraction(f.mob));f.damage(Double.MAX_VALUE,new MobCombatListener());assertEquals(0,f.physical[0]);
    }
    @Test void vanillaHealthAndDirectExternalSetHealthRemainConsistent() {
        var vanilla=new Body(100);assertFalse(MobHealth.virtual(vanilla.mob));
        vanilla.damage(25,new MobCombatListener());assertEquals(75,MobHealth.current(vanilla.mob));
        var f=new Body(5000);f.mob.setHealth(512);assertEquals(2500,MobHealth.current(f.mob));
    }
    @Test void armorAndResistanceAreComputedBeforeTheIncomingDamageIsScaled() {
        var f=new Body(5000);var modifiers=new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE,1000d);modifiers.put(EntityDamageEvent.DamageModifier.ARMOR,-200d);
        modifiers.put(EntityDamageEvent.DamageModifier.RESISTANCE,-100d);
        var functions=new EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>>(EntityDamageEvent.DamageModifier.class);
        modifiers.keySet().forEach(m->functions.put(m,x->0d));
        var event=new EntityDamageEvent(f.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),modifiers,functions);
        new MobCombatListener().damage(event);assertEquals(700/ (5000d/1024),event.getFinalDamage(),1e-9);
    }
    @Test void attackAbove2048IsAppliedButAbilityProjectileAndCancelledDamageAreUntouched() {
        var source=new Body(5000);var target=new Body(5000);source.data.put(MobKeys.VIRTUAL_ATTACK_DAMAGE,3000d);
        var listener=new MobCombatListener();
        var melee=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,mock(DamageSource.class),2048);
        listener.melee(melee);assertEquals(3000,melee.getDamage());listener.damage(melee);listener.damaged(melee);
        target.physical[0]=(double)(float)(target.physical[0]-(float)melee.getFinalDamage());assertEquals(2000,MobHealth.current(target.mob),1e-8);
        doAnswer(c->{var ability=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.ENTITY_ATTACK,mock(DamageSource.class),(double)c.getArgument(0));
            listener.melee(ability);assertEquals(7,ability.getDamage());return null;}).when(target.mob).damage(anyDouble(),eq(source.mob));
        MobCombatListener.abilityDamage(target.mob,7,source.mob);
        var projectile=new EntityDamageByEntityEvent(source.mob,target.mob,EntityDamageEvent.DamageCause.PROJECTILE,mock(DamageSource.class),4);
        listener.melee(projectile);assertEquals(4,projectile.getDamage());
        var cancelled=new EntityDamageEvent(target.mob,EntityDamageEvent.DamageCause.CUSTOM,mock(DamageSource.class),1000);
        listener.damage(cancelled);cancelled.setCancelled(true);listener.damaged(cancelled);assertEquals(2000,MobHealth.current(target.mob),1e-8);
    }
}
