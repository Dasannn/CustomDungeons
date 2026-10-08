package dev.dasan.customdungeons;

import dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformReloadTest {
    @Test void limitsReadTheCurrentYamlAfterReloadReplacesIt() {
        var plugin = mock(CustomDungeonsPlugin.class, CALLS_REAL_METHODS);
        var before = new YamlConfiguration();
        before.set("performance.max-alive-mobs-per-session", 50);
        before.set("performance.particle-density", 1);
        before.set("performance.effect-view-radius", 48);
        doReturn(before).when(plugin).getConfig();
        assertEquals(new PerformanceLimits(50, 1, 48), plugin.limits());
        var after = new YamlConfiguration();
        after.set("performance.max-alive-mobs-per-session", 5);
        after.set("performance.particle-density", .5);
        after.set("performance.effect-view-radius", 16);
        doReturn(after).when(plugin).getConfig();
        assertEquals(new PerformanceLimits(5, .5, 16), plugin.limits());
    }
}
