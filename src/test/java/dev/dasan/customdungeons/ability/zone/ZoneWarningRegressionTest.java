package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.control.*;
import dev.dasan.customdungeons.config.PluginConfig;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneWarningRegressionTest {
    static List<String> ids(){return ZoneServiceTest.IDS;}
    @ParameterizedTest @MethodSource("ids")
    void noZoneMayActWhenItsCompleteWarningDoesNotFitTheSharedBudget(String id) {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(50,10,48));
            f.positions.put(f.player,new Location(f.world,30,64,30));f.positions.put(f.ally,new Location(f.world,30,64,30));
            for(int i=0;i<15;i++)f.cast("inverted_gravity");
            f.positions.put(f.player,new Location(f.world,0,64,2));f.positions.put(f.ally,new Location(f.world,0,64,2));
            clearInvocations(f.player,f.ally);
            f.cast(id,id.equals("inverted_gravity")?Map.of("damage",2):Map.of("count",1));
            f.step(100);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
            verify(f.player,never()).addPotionEffect(any());verify(f.player,never()).teleport(any(Location.class));verify(f.player,never()).setVelocity(any());
            assertTrue(f.created.isEmpty());verify(f.blocks,never()).place(any(),any(),anyInt());
        }
    }
    @Test void unmarkedCrackedTilesStaySafeAtMaximumConcurrentZones() throws Exception {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.positions.put(f.player,new Location(f.world,30,64,30));f.positions.put(f.ally,new Location(f.world,30,64,30));
            for(int i=0;i<15;i++)f.cast("inverted_gravity");
            f.positions.put(f.player,new Location(f.world,0,64,2));f.positions.put(f.ally,new Location(f.world,0,64,2));
            clearInvocations(f.player);f.cast("cracked_floor",Map.of("radius",16,"warning",20));f.step(19);
            var owners=ZoneService.class.getDeclaredField("owners");owners.setAccessible(true);
            Object cracked=((Map<?,Set<?>>)owners.get(f.service)).values().stream().flatMap(Set::stream).filter(z->{
                try {var id=z.getClass().getDeclaredField("id");id.setAccessible(true);return id.get(z).equals("cracked_floor");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }).findFirst().orElseThrow();
            var pattern=cracked.getClass().getDeclaredField("pattern");pattern.setAccessible(true);
            Set<ZoneRules.Tile> marked=new HashSet<>();
            for(var call:mockingDetails(f.player).getInvocations())if(call.getMethod().getName().equals("spawnParticle")&&call.getArgument(1) instanceof Location at&&Math.abs(at.getX())<=18&&Math.abs(at.getZ())<=18)
                marked.add(new ZoneRules.Tile((int)Math.floor(at.getX()/2)*2,(int)Math.floor(at.getZ()/2)*2));
            var tile=ZoneRules.tiles(16,pattern.getInt(cracked),0).stream().filter(t->Math.hypot(t.x()+1,t.z()+1)<16&&!marked.contains(t)).findFirst().orElseThrow();
            f.positions.put(f.player,new Location(f.world,tile.x()+1,64,tile.z()+1));f.step(1);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.player,never()).setVelocity(any());
        }
    }
    @Test void activeSoulChainExcludesGravityWithoutAcquiringAnotherControl() {
        try(var f=new ZoneServiceTest.Fixture();var control=new ControlService(f.platform)) {
            var chain=new SoulChainAbility();chain.execute(new AbilityContext(f.caster,List.of(f.player),new ParamValues(Map.of(),chain.params()),f.host,null));
            f.step(20);assertEquals(1,control.activeCount());f.cast("inverted_gravity",Map.of("damage",2));f.step(30);
            assertEquals(1,control.activeCount());assertFalse(f.player.hasPotionEffect(PotionEffectType.LEVITATION));verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void arrowCannotDamageAValidParticipantOutsideTheWarnedCircle() {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.cast("arrow_rain");f.step(35);var arrow=f.created.getFirst();
            f.positions.put(f.player,new Location(f.world,20,64,20));
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(arrow);when(hit.getEntity()).thenReturn(f.player);
            f.service.arrowDamage(hit);verify(hit).setCancelled(true);
        }
    }
    @Test void immunityAfterControlReleaseDoesNotExcludeGravity() {
        try(var f=new ZoneServiceTest.Fixture();var control=new ControlService(f.platform)) {
            var chain=new SoulChainAbility();chain.execute(new AbilityContext(f.caster,List.of(f.player),new ParamValues(Map.of(),chain.params()),f.host,null));
            f.step(20);ControlService.cleanup(f.caster);assertEquals(0,control.activeCount());
            f.cast("inverted_gravity");f.step(30);assertTrue(f.player.hasPotionEffect(PotionEffectType.LEVITATION));
        }
    }
    @Test void newActiveControlWithdrawsExistingGravity() {
        try(var f=new ZoneServiceTest.Fixture();var control=new ControlService(f.platform)) {
            f.cast("inverted_gravity");f.step(30);assertTrue(f.player.hasPotionEffect(PotionEffectType.LEVITATION));
            var chain=new SoulChainAbility();chain.execute(new AbilityContext(f.caster,List.of(f.player),new ParamValues(Map.of(),chain.params()),f.host,null));
            f.step(21);assertEquals(1,control.activeCount());assertFalse(f.player.hasPotionEffect(PotionEffectType.LEVITATION));
        }
    }
}
