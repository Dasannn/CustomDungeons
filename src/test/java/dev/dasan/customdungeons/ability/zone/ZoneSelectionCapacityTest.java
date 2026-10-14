package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.config.PluginConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneSelectionCapacityTest {
    @Test void poolCapacityIsSharedWithActualCreationAndReleasedOnCleanup() {
        try (var f = new ZoneServiceTest.Fixture()) {
            assertTrue(ZoneService.canStart("poison_pools", f.caster));
            for (int i = 0; i < 3; i++) f.cast("poison_pools", Map.of("count", 2));
            assertEquals(6, f.service.activeCount());
            assertFalse(ZoneService.canStart("poison_pools", f.caster));
            assertTrue(ZoneService.canStart("sweep", f.caster));
            ZoneService.cleanup(f.caster);
            assertTrue(ZoneService.canStart("poison_pools", f.caster));
        }
    }
    @Test void zeroCapacityExcludesNewZonesAndLiveChangesTakeEffect() {
        try (var f = new ZoneServiceTest.Fixture()) {
            when(f.platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(0, 1, 48));
            assertFalse(ZoneService.canStart("sweep", f.caster));
            when(f.platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(1, 1, 48));
            assertTrue(ZoneService.canStart("sweep", f.caster));
            f.cast("vortex");
            assertFalse(ZoneService.canStart("sweep", f.caster));
        }
    }
}
