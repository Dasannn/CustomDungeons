package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.config.PluginConfig;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class EffectsTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @AfterEach void resetEffects() {
        Effects.configure(new PluginConfig.PerformanceLimits(50, 1, 48));
    }
    @Test void liveEffectsReachNearbyNonAdminButNotFarOrOtherWorldPlayers() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        var nearby = mock(org.bukkit.entity.Player.class);
        when(nearby.isOnline()).thenReturn(true);
        when(nearby.getWorld()).thenReturn(f.world);
        when(nearby.getLocation()).thenReturn(new Location(f.world, 48, 64, 0));
        var far = mock(org.bukkit.entity.Player.class);
        when(far.isOnline()).thenReturn(true);
        when(far.getWorld()).thenReturn(f.world);
        when(far.getLocation()).thenReturn(new Location(f.world, 48.01, 64, 0));
        var otherWorld = mock(org.bukkit.entity.Player.class);
        var secondWorld = mock(org.bukkit.World.class);
        when(otherWorld.isOnline()).thenReturn(true);
        when(otherWorld.getWorld()).thenReturn(secondWorld);
        when(otherWorld.getLocation()).thenReturn(new Location(secondWorld, 0, 64, 0));
        when(f.session.isLiveTest()).thenReturn(true);
        when(f.world.getPlayers()).thenReturn(List.of(f.player, nearby, far, otherWorld));
        var at = f.entity.getLocation();
        Effects.sound(f.session, at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        Effects.particles(f.session, at, Particle.CRIT, 4, 0);
        verify(nearby).playSound(at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        verify(nearby).spawnParticle(Particle.CRIT, at, 4, 0, 0, 0, 0);
        verify(f.player).playSound(at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        verify(far, never()).playSound(any(Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
        verify(otherWorld, never()).playSound(any(Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
        verify(far, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        // Receiving an effect must never grant permission to be hit by an ability.
        Effects.damage(nearby, 6, f.mob);
        verify(nearby, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
    }
    @Test void dungeonSoundReachesAllMembersWhileParticlesRemainBoundedAndOutsidersExcluded() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        var far = mock(org.bukkit.entity.Player.class);
        when(far.isOnline()).thenReturn(true);
        when(far.getWorld()).thenReturn(f.world);
        when(far.getLocation()).thenReturn(new Location(f.world, 100, 64, 0));
        var outsider = mock(org.bukkit.entity.Player.class);
        when(f.session.players()).thenReturn(List.of(f.player, far));
        when(f.world.getPlayers()).thenReturn(List.of(f.player, far, outsider));
        var at = f.entity.getLocation();
        Effects.sound(f.session, at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        Effects.particles(f.session, at, Particle.CRIT, 4, 0);
        verify(f.player).playSound(at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        verify(far).playSound(at, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        verify(far, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verifyNoInteractions(outsider);
    }
    @Test void densityRoundsAndRetainsMinimumAndMaximumParticleCounts() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        var at = f.entity.getLocation();
        Effects.configure(new PluginConfig.PerformanceLimits(50, 0.5, 48));
        Effects.particles(f.session, at, Particle.CRIT, 3, 0);
        verify(f.player).spawnParticle(Particle.CRIT, at, 2, 0, 0, 0, 0);
        clearInvocations(f.player);
        Effects.configure(new PluginConfig.PerformanceLimits(50, 0, 48));
        Effects.particles(f.session, at, Particle.CRIT, 1, 0);
        verify(f.player).spawnParticle(Particle.CRIT, at, 1, 0, 0, 0, 0);
        clearInvocations(f.player);
        Effects.configure(new PluginConfig.PerformanceLimits(50, 10, 48));
        Effects.particles(f.session, at, Particle.CRIT, Integer.MAX_VALUE, 0);
        verify(f.player).spawnParticle(Particle.CRIT, at, 256, 0, 0, 0, 0);
    }
    @Test void nonPositiveCountsDoNotEmitParticles() {
        var f = new AbilityEngineTest.Fixture(List.of(), List.of());
        Effects.particles(f.session, f.entity.getLocation(), Particle.CRIT, 0, 0);
        Effects.particles(f.session, f.entity.getLocation(), Particle.CRIT, -1, 0);
        verify(f.player, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
