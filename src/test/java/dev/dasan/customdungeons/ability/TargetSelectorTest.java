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
        for(var mode: TargetMode.values()) assertEquals(List.of(f.player), TargetSelector.select(f.mob, mode, 2));
    }
}
