package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.MobTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WardenMobFactoryTest {
    @Test void wardenInitializesVanillaDigCooldownAndKeepsPersistenceWithoutForcingCombat() {
        PaperApiTestBootstrap.initialize();
        var world = mock(World.class);
        var warden = mock(Warden.class);
        when(warden.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var host = mock(MobHost.class);
        when(host.id()).thenReturn(UUID.randomUUID());
        doAnswer(call -> {
            Consumer<Warden> configure = call.getArgument(4);
            configure.accept(warden);
            return warden;
        }).when(world).spawn(any(Location.class), eq(Warden.class), eq(SpawnReason.CUSTOM), anyBoolean(), any());
        var at = new Location(world, 0, 64, 0);
        var factory = new MobFactory(mock(MobsPlatform.class));
        for (String type : List.of("WARDEN", "minecraft:warden")) {
            var template = new MobTemplate("warden", type, "", 0, 0, 0, 0, 0,
                    Map.of(), List.of(), List.of(), List.of(), false, "PURPLE", null, List.of(), false);
            assertSame(warden, factory.spawn(template, at, host, 1).entity());
        }
        verify(world, times(2)).spawn(eq(at), eq(Warden.class), eq(SpawnReason.CUSTOM), eq(true), any());
        // Vanilla renews an existing DIG_COOLDOWN every tick when persistence is required.
        verify(warden, times(2)).setRemoveWhenFarAway(false);
        verify(warden, times(2)).setPersistent(false);
        verify(warden, never()).setAI(anyBoolean());
        verify(warden, never()).setAware(anyBoolean());
        verify(warden, never()).setAnger(any(), anyInt());
        verify(warden, never()).setTarget(any());
        verify(warden, never()).setPose(any());
    }
}
