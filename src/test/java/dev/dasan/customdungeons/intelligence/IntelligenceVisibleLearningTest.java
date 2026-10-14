package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.text.Messages;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntelligenceVisibleLearningTest {
    static {PaperApiTestBootstrap.initialize();}
    static class Fixture implements AutoCloseable {
        final World world=mock(World.class);
        final Mob entity=mock(Mob.class);
        final Player player=mock(Player.class), far=mock(Player.class);
        final MobHost host=mock(MobHost.class);
        final MobsPlatform platform=mock(MobsPlatform.class);
        final Component original=Component.text("Custodio",NamedTextColor.GOLD);
        final AtomicReference<Component> name=new AtomicReference<>(original);
        final IntelligenceService service;
        final ActiveMob mob;
        Fixture() {
            when(entity.getUniqueId()).thenReturn(new UUID(0,0));when(entity.isValid()).thenReturn(true);
            when(entity.getWorld()).thenReturn(world);when(entity.getLocation()).thenReturn(new Location(world,0,64,0));when(entity.getHeight()).thenReturn(2d);
            when(entity.customName()).thenAnswer(i->name.get());when(entity.isCustomNameVisible()).thenReturn(false);
            doAnswer(i->{name.set(i.getArgument(0));return null;}).when(entity).customName(any());
            when(player.getUniqueId()).thenReturn(new UUID(0,1));when(player.isValid()).thenReturn(true);when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(new Location(world,2,64,0));when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(far.isOnline()).thenReturn(true);when(far.getLocation()).thenReturn(new Location(world,100,64,0));
            when(host.players()).thenReturn(List.of(player));when(host.audience(any())).thenReturn(List.of(player,far));when(host.scheduler()).thenReturn(mock(TickScheduler.class));
            when(platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(50,1,24));
            var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/messages.yml"),StandardCharsets.UTF_8));
            var messages=new Messages();messages.load(yaml,"");when(platform.messages()).thenReturn(messages);
            var template=new MobTemplate("visible","HUSK","&6Custodio",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false)
                    .withIntelligence(IntelligenceDef.level(5));
            mob=new ActiveMob(entity,template,host);service=new IntelligenceService(platform,IntelligenceRules.defaults());IntelligenceService.track(mob);
        }
        IntelligenceBrain.Adaptation prepare() {
            var brain=IntelligenceService.brain(mob);brain.memory().record(player.getUniqueId(),"critical","ENTITY_ATTACK",1,0);
            brain.memory().record(player.getUniqueId(),"critical","ENTITY_ATTACK",1,1);return brain.evaluate(1).getFirst();
        }
        void tick(long tick) {IntelligenceService.tick(mob,mock(AbilityEngine.class),tick);}
        @Override public void close() {service.close();}
    }
    @Test void warningDoesNotColorNameAndActivationHasBoundedEyeAndLowSoundOnce() {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var adaptation=f.prepare();f.tick(adaptation.begins()-1);assertEquals(f.original,f.name.get());
            f.tick(adaptation.begins());assertNotEquals(f.original,f.name.get());
            verify(f.entity).setCustomNameVisible(true);
            verify(f.player).playSound(any(Location.class),anyString(),eq(SoundCategory.HOSTILE),anyFloat(),floatThat(p->p<1));
            verify(f.player,atLeast(5)).spawnParticle(eq(Particle.END_ROD),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
            verify(f.far,never()).spawnParticle(any(Particle.class),any(Location.class),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
            verify(f.far,never()).playSound(any(Location.class),anyString(),any(SoundCategory.class),anyFloat(),anyFloat());
            clearInvocations(f.player);f.tick(adaptation.begins()+1);verifyNoInteractions(f.player);
        }
    }
    @ParameterizedTest @ValueSource(strings={"expiry","death","removed","cleanup","reload","disable","phase"})
    void everyWithdrawalRestoresExactNameAndVisibility(String ending) {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var adaptation=f.prepare();f.tick(adaptation.begins());assertNotEquals(f.original,f.name.get());
            switch(ending) {
                case "expiry"->f.tick(adaptation.expires());
                case "death"->{var event=mock(org.bukkit.event.entity.EntityDeathEvent.class);when(event.getEntity()).thenReturn(f.entity);f.service.death(event);}
                case "removed"->f.service.removed(new com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent(f.entity,f.world));
                case "cleanup"->IntelligenceService.cleanup(f.mob);
                case "reload"->IntelligenceService.reset();
                case "disable"->f.service.close();
                case "phase"->IntelligenceService.phase(f.mob,IntelligenceDef.level(0),adaptation.begins()+1);
            }
            assertEquals(f.original,f.name.get());verify(f.entity).setCustomNameVisible(false);
        }
    }
    @Test void overlappingAdaptationsKeepTheFirstNameLeaseUntilTheLastExpires() {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var first=f.prepare();f.tick(first.begins());var colored=f.name.get();
            var brain=IntelligenceService.brain(f.mob);
            brain.memory().record(f.player.getUniqueId(),"shield","",0,202);
            brain.memory().record(f.player.getUniqueId(),"shield","",0,203);
            var second=brain.evaluate(205).getFirst();f.tick(second.begins());
            assertEquals(colored,f.name.get());f.tick(first.expires());assertEquals(colored,f.name.get());
            f.tick(second.expires());assertEquals(f.original,f.name.get());
            verify(f.entity).customName(f.original);verify(f.entity).setCustomNameVisible(true);
        }
    }
    @Test void anUnnamedMobRestoresNullAndItsOriginalVisibility() {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            f.name.set(null);when(f.entity.name()).thenReturn(Component.translatable("entity.minecraft.husk"));
            when(f.entity.isCustomNameVisible()).thenReturn(true);
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var adaptation=f.prepare();f.tick(adaptation.begins());assertNotNull(f.name.get());
            f.tick(adaptation.expires());assertNull(f.name.get());verify(f.entity).customName(isNull());
            verify(f.entity,times(2)).setCustomNameVisible(true);
        }
    }
    @Test void zeroDensityDisablesParticlesAndLevelZeroHasNoVisualWork() {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            when(f.platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(50,0,24));
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var adaptation=f.prepare();f.tick(adaptation.begins());assertTrue(IntelligenceService.learningVisible(f.mob));
            verify(f.player,never()).spawnParticle(any(Particle.class),any(Location.class),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
            IntelligenceService.phase(f.mob,IntelligenceDef.level(0),3);assertFalse(IntelligenceService.learningVisible(f.mob));
            clearInvocations(f.entity,f.host,f.platform,f.player);f.tick(10_000);verifyNoInteractions(f.host,f.platform,f.player);
            verify(f.entity,never()).customName(any());verify(f.entity,never()).setCustomNameVisible(anyBoolean());
        }
    }
    @Test void eyeGeometryHasAPupilAndNeverExceedsThirteenPointsAboveTheHead() {
        var center=new Location(null,10,70,20,90,0);
        var points=AdaptationVisuals.eyePoints(center,999);
        assertEquals(13,points.size());assertEquals(center,points.getLast());
        for(var point:points) {assertTrue(point.toVector().distanceSquared(center.toVector())<=.46*.46);assertEquals(10,point.getX(),1e-8);assertTrue(point.getY()>=69.8);}
    }
    @Test void debugShowsTheAdaptationWarningAndListsOnlyActiveAdaptations() {
        try(var f=new Fixture();var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(f.player.getUniqueId())).thenReturn(f.player);
            var adaptation=f.prepare();var engine=mock(AbilityEngine.class);
            var warning=DebugView.snapshot(f.mob,engine,f.platform.messages(),adaptation.begins()-1);
            assertEquals("warning",warning.state());assertTrue(warning.adaptations().isEmpty());
            assertEquals(f.platform.messages().get("intelligence.adaptation.critical"),warning.causeAbility());
            f.tick(adaptation.begins());
            assertEquals(1,DebugView.snapshot(f.mob,engine,f.platform.messages(),adaptation.begins()).adaptations().size());
            f.tick(adaptation.expires());
            assertTrue(DebugView.snapshot(f.mob,engine,f.platform.messages(),adaptation.expires()).adaptations().isEmpty());
        }
    }
}
