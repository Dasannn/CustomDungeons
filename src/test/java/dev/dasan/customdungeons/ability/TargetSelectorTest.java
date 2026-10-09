package dev.dasan.customdungeons.ability;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetSelectorTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @Test void effectsDamageIsAttributedAndRejectsOutsiders() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        Effects.damage(f.player, 6, f.mob);
        verify(f.player).damage(6, f.entity);
        var outsider = mock(Player.class); Effects.damage(outsider, 6, f.mob);
        verify(outsider, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
        when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        Effects.damage(f.player, 6, f.mob); verify(f.player, times(1)).damage(6, f.entity);
    }
    @Test void markedProjectilesProtectTerrainAndOnlyDamageParticipants() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        var projectile = mock(org.bukkit.entity.Fireball.class);
        var data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(projectile.getPersistentDataContainer()).thenReturn(data);
        when(data.has(Effects.PROJECTILE_KEY, org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
        var velocity = new org.bukkit.util.Vector(1, 0, 0);
        doAnswer(c->{((java.util.function.Consumer<org.bukkit.entity.Projectile>)c.getArgument(2)).accept(projectile);return projectile;}).when(f.entity).launchProjectile(eq(org.bukkit.entity.Fireball.class), eq(velocity), any());
        assertSame(projectile, Effects.launch(f.mob, org.bukkit.entity.Fireball.class, velocity));
        verify(projectile).setIsIncendiary(false); verify(projectile).setYield(0);
        verify(data).set(Effects.PROJECTILE_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        var listener = new dev.dasan.customdungeons.listener.AbilityProtectionListener();
        var explosion = mock(org.bukkit.event.entity.EntityExplodeEvent.class);
        var blocks = new ArrayList<org.bukkit.block.Block>(); blocks.add(mock(org.bukkit.block.Block.class));
        when(explosion.getEntity()).thenReturn(projectile); when(explosion.blockList()).thenReturn(blocks);
        listener.explode(explosion); assertTrue(blocks.isEmpty());
        var prime = mock(org.bukkit.event.entity.ExplosionPrimeEvent.class);
        when(prime.getEntity()).thenReturn(projectile); listener.prime(prime); verify(prime).setFire(false);
        var hit = mock(org.bukkit.event.entity.ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(projectile); when(hit.getHitBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        listener.hit(hit); verify(hit).setCancelled(true); verify(projectile).remove();
        var damage = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(projectile); when(damage.getEntity()).thenReturn(f.player);
        listener.damage(damage); verify(damage, never()).setCancelled(true);
        when(damage.getEntity()).thenReturn(mock(Player.class)); listener.damage(damage);
        verify(damage).setCancelled(true);
        var ignite = mock(org.bukkit.event.block.BlockIgniteEvent.class);
        when(ignite.getIgnitingEntity()).thenReturn(projectile); listener.ignite(ignite); verify(ignite).setCancelled(true);
    }
    @Test void visualsUseConfiguredDensityAndViewRadius() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        try {
            Effects.configure(() -> new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50, 0.5, 3));
            var at = new Location(f.world, 0, 64, 0);
            Effects.particles(f.session, at, Particle.CRIT, 4, 1);
            verify(f.player).spawnParticle(Particle.CRIT, at, 2, 1, 1, 1, 0);
            Effects.configure(() -> new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50, 1, 1));
            Effects.particles(f.session, at, Particle.CRIT, 4, 1);
            verify(f.player, times(1)).spawnParticle(eq(Particle.CRIT), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
        } finally { Effects.configure(() -> new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50, 1, 48)); }
    }
    @Test void selectorExcludesNonSessionAndSpectators() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        var outsider = mock(Player.class); when(f.entity.getTarget()).thenReturn(outsider);
        assertTrue(TargetSelector.select(f.mob, TargetMode.CURRENT_TARGET, 10).isEmpty());
        assertEquals(List.of(f.player), TargetSelector.select(f.mob, TargetMode.NEAREST, 10));
        when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertTrue(TargetSelector.select(f.mob, TargetMode.ALL_IN_RADIUS, 10).isEmpty());
    }
    @Test void excludesCreativeDeadDifferentWorldAndOutOfRange() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        assertTrue(TargetSelector.select(f.mob, TargetMode.NEAREST, 1).isEmpty());
        when(f.player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(TargetSelector.select(f.mob, TargetMode.NEAREST, 10).isEmpty());
        when(f.player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(f.player.isDead()).thenReturn(true);
        assertTrue(TargetSelector.select(f.mob, TargetMode.NEAREST, 10).isEmpty());
        when(f.player.isDead()).thenReturn(false); when(f.player.getWorld()).thenReturn(mock(World.class));
        assertTrue(TargetSelector.select(f.mob, TargetMode.NEAREST, 10).isEmpty());
    }
    @Test void liveCurrentTargetMatchesDungeonWithoutFallbackOrChangingMobTarget() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        when(f.session.audience(any(org.bukkit.Location.class))).thenAnswer(call ->
                dev.dasan.customdungeons.ability.EffectAudience.nearby(f.world.getPlayers(), call.getArgument(0), 48));
        assertTrue(TargetSelector.select(f.mob, TargetMode.CURRENT_TARGET, 10).isEmpty());
        when(f.entity.getTarget()).thenReturn(mock(Player.class));
        assertTrue(TargetSelector.select(f.mob, TargetMode.CURRENT_TARGET, 10).isEmpty());
        when(f.entity.getTarget()).thenReturn(f.player);
        assertEquals(List.of(f.player), TargetSelector.select(f.mob, TargetMode.CURRENT_TARGET, 10));
        verify(f.entity, never()).setTarget(any());
    }
    @Test void boundaryAndCurrentTargetAndRandomStayWithinSession() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        when(f.entity.getTarget()).thenReturn(f.player);
        for(var mode: List.of(TargetMode.CURRENT_TARGET,TargetMode.NEAREST,TargetMode.RANDOM,TargetMode.ALL_IN_RADIUS)) assertEquals(List.of(f.player), TargetSelector.select(f.mob, mode, 2));
        for(var mode: TargetMode.values())if(mode.ordinal()>TargetMode.ALL_IN_RADIUS.ordinal())assertTrue(TargetSelector.select(f.mob,mode,2).isEmpty());
    }
    @Test void intelligentTargetsUseOnlyEligibleParticipantsAndMemory() {
        var f=new AbilityEngineTest.Fixture(List.of(),List.of());
        when(f.player.getUniqueId()).thenReturn(new UUID(0,2));when(f.player.getHealth()).thenReturn(10d);
        var mob=new dev.dasan.customdungeons.runtime.ActiveMob(f.entity,f.mob.template().withIntelligence(dev.dasan.customdungeons.intelligence.IntelligenceDef.level(5)),f.session);
        try(var service=new dev.dasan.customdungeons.intelligence.IntelligenceService(mock(dev.dasan.customdungeons.mob.MobsPlatform.class),dev.dasan.customdungeons.intelligence.IntelligenceRules.defaults())) {
            dev.dasan.customdungeons.intelligence.IntelligenceService.track(mob);
            var memory=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(mob).memory();
            memory.record(f.player.getUniqueId(),"heal","",0,0);memory.record(f.player.getUniqueId(),"damage","PROJECTILE",5,1,false,true,false);
            for(var mode:List.of(TargetMode.MOST_THREAT,TargetMode.WEAKEST,TargetMode.TANKIEST,TargetMode.LEAST_ARMOR,TargetMode.LAST_HEALED,TargetMode.ARCHER,TargetMode.FARTHEST))assertEquals(List.of(f.player),TargetSelector.select(mob,mode,2));
            when(f.player.getLocation()).thenReturn(new Location(f.world,0,64,-2));assertEquals(List.of(f.player),TargetSelector.select(mob,TargetMode.BEHIND,2));
            when(f.player.getGameMode()).thenReturn(GameMode.CREATIVE);for(var mode:TargetMode.values())assertTrue(TargetSelector.select(mob,mode,2).isEmpty());
        }
    }
    @Test void blinkAtStrategicLevelPrefersRememberedRangedAttackerOverNearestPlayer() {
        var f=new AbilityEngineTest.Fixture(List.of(),List.of());when(f.player.getUniqueId()).thenReturn(new UUID(0,2));
        var nearest=mock(Player.class);when(nearest.getUniqueId()).thenReturn(new UUID(0,3));when(nearest.isOnline()).thenReturn(true);when(nearest.isValid()).thenReturn(true);when(nearest.getGameMode()).thenReturn(GameMode.SURVIVAL);when(nearest.getWorld()).thenReturn(f.world);when(nearest.getLocation()).thenReturn(new Location(f.world,0,64,1));
        when(f.session.players()).thenReturn(List.of(f.player,nearest));
        var caster=new dev.dasan.customdungeons.runtime.ActiveMob(f.entity,f.mob.template().withIntelligence(dev.dasan.customdungeons.intelligence.IntelligenceDef.level(3)),f.session);
        try(var service=new dev.dasan.customdungeons.intelligence.IntelligenceService(mock(dev.dasan.customdungeons.mob.MobsPlatform.class),dev.dasan.customdungeons.intelligence.IntelligenceRules.defaults())) {
            dev.dasan.customdungeons.intelligence.IntelligenceService.track(caster);
            dev.dasan.customdungeons.intelligence.IntelligenceService.brain(caster).memory().record(f.player.getUniqueId(),"damage","PROJECTILE",5,0,false,true,false);
            assertEquals(List.of(f.player),TargetSelector.selectAbility(caster,"blink_behind",TargetMode.NEAREST,16));
        }
        assertEquals(List.of(nearest),TargetSelector.selectAbility(f.mob,"blink_behind",TargetMode.NEAREST,16));
    }

}
