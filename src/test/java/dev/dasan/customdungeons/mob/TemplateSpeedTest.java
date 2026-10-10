package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.MobAttributes;
import java.util.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateSpeedTest {
    @Test void stableEffectiveSpeedDoesNotCloneModifierMetadataOnEveryEvaluation() {
        PaperApiTestBootstrap.initialize();var mob=mock(Mob.class);var speed=mock(AttributeInstance.class);
        when(mob.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);when(speed.getBaseValue()).thenReturn(.3);
        var modifiers=new HashMap<org.bukkit.NamespacedKey,AttributeModifier>();
        doAnswer(i->{var m=(AttributeModifier)i.getArgument(0);modifiers.put(m.getKey(),m);return null;}).when(speed).addTransientModifier(any());
        when(speed.getModifier(any(org.bukkit.NamespacedKey.class))).thenAnswer(i->modifiers.get(i.getArgument(0)));
        doAnswer(i->{modifiers.remove(i.getArgument(0));return null;}).when(speed).removeModifier(any(org.bukkit.NamespacedKey.class));
        when(speed.getValue()).thenAnswer(i->speed.getBaseValue()+modifiers.values().stream().mapToDouble(AttributeModifier::getAmount).sum());
        new MobFactory(mock(MobsPlatform.class)).applyAttributes(mob,new MobAttributes(Map.of("speed",.25)));
        var active=mock(dev.dasan.customdungeons.runtime.ActiveMob.class);when(active.entity()).thenReturn(mob);
        clearInvocations(speed);
        for(int i=0;i<100;i++)MobFactory.refreshSpeed(active);
        assertEquals(.25,speed.getValue(),1e-9);verify(speed,never()).getModifier(any(org.bukkit.NamespacedKey.class));
        when(speed.getBaseValue()).thenReturn(.35);MobFactory.refreshSpeed(active);
        assertEquals(.25,speed.getValue(),1e-9);verify(speed).getModifier(any(org.bukkit.NamespacedKey.class));
        MobFactory.clearSpeed(active);assertTrue(modifiers.isEmpty());
    }
    @Test void nearZeroNativeBaseCannotAmplifyRecoveryIntoTeleportSizedMovement() {
        PaperApiTestBootstrap.initialize();var mob=mock(Mob.class);var speed=mock(AttributeInstance.class);
        when(mob.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);when(speed.getBaseValue()).thenReturn(.001);
        new MobFactory(mock(MobsPlatform.class)).applyAttributes(mob,new MobAttributes(Map.of("speed",.25)));
        var capture=org.mockito.ArgumentCaptor.forClass(AttributeModifier.class);verify(speed).addTransientModifier(capture.capture());
        var modifier=capture.getValue();double recovered=modifier.getOperation()==AttributeModifier.Operation.ADD_NUMBER?.035+modifier.getAmount():.035*(1+modifier.getAmount());
        assertTrue(recovered<=.30,"A near-zero base must not multiply native recovery into excessive movement: "+recovered);
    }
    @Test void configuredSpeedSurvivesNativeBaseChangesAndPreservesNativeStops() {
        PaperApiTestBootstrap.initialize();
        var mob=mock(Mob.class);var speed=mock(AttributeInstance.class);
        when(mob.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
        when(speed.getDefaultValue()).thenReturn(.7);
        when(speed.getBaseValue()).thenReturn(.3);
        var modifiers=new HashMap<org.bukkit.NamespacedKey,AttributeModifier>();
        doAnswer(i->{var m=(AttributeModifier)i.getArgument(0);modifiers.put(m.getKey(),m);return null;}).when(speed).addTransientModifier(any());
        when(speed.getModifier(any(org.bukkit.NamespacedKey.class))).thenAnswer(i->modifiers.get(i.getArgument(0)));
        doAnswer(i->{modifiers.remove(i.getArgument(0));return null;}).when(speed).removeModifier(any(org.bukkit.NamespacedKey.class));
        var factory=new MobFactory(mock(MobsPlatform.class));
        factory.applyAttributes(mob,new MobAttributes(Map.of("speed",.6)));
        verify(speed,never()).setBaseValue(anyDouble());
        assertEquals(1,modifiers.size());
        var modifier=modifiers.values().iterator().next();
        assertEquals(AttributeModifier.Operation.ADD_NUMBER,modifier.getOperation());
        assertEquals(.6,.3+modifier.getAmount(),1e-9);
        var active=mock(dev.dasan.customdungeons.runtime.ActiveMob.class);when(active.entity()).thenReturn(mob);
        when(speed.getBaseValue()).thenReturn(.35);
        MobFactory.refreshSpeed(active);
        modifier=modifiers.values().iterator().next();
        assertEquals(.6,.35+modifier.getAmount(),1e-9);
        when(speed.getBaseValue()).thenReturn(.4);
        dev.dasan.customdungeons.ability.MobMovement.track(active);
        dev.dasan.customdungeons.ability.MobMovement.face(mob,new org.bukkit.Location(null,0,0,0),new org.bukkit.Location(null,0,0,0));
        assertEquals(.6,.4+modifiers.values().iterator().next().getAmount(),1e-9,"Post-AI movement evaluation must compensate the current base");
        factory.applyAttributes(mob,new MobAttributes(Map.of("speed",.15)));
        assertEquals(1,modifiers.size());
        assertEquals(.15,.4+modifiers.values().iterator().next().getAmount(),1e-9);
        MobFactory.clearSpeed(active);assertTrue(modifiers.isEmpty());
        MobFactory.refreshSpeed(active);assertTrue(modifiers.isEmpty());
    }
}
