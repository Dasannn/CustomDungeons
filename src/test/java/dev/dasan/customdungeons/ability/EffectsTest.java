package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.config.PluginConfig;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class EffectsTest {
    @AfterEach void resetEffects() {
        Effects.configure(new PluginConfig.PerformanceLimits(50, 1, 48));
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
