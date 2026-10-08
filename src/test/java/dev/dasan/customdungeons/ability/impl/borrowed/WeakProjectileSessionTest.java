package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.mob.MobHost;
import java.lang.ref.Reference;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeakProjectileSessionTest {
    @Test void membershipIsLiveAndExpiredSessionFailsClosed() throws Exception {
        var session = mock(MobHost.class);
        var id = UUID.randomUUID();
        var player = mock(Player.class);
        when(session.id()).thenReturn(id);
        when(session.players()).thenReturn(List.of(player));
        var view = new WeakProjectileSession(session);
        assertEquals(id, view.id());
        assertEquals(List.of(player), view.players());
        when(session.players()).thenReturn(List.of());
        assertTrue(view.players().isEmpty());
        when(session.players()).thenReturn(List.of(player));
        // Clear the non-owning reference deterministically, without relying on GC timing.
        var field = WeakProjectileSession.class.getDeclaredField("session");
        field.setAccessible(true);
        ((Reference<?>) field.get(view)).clear();
        assertEquals(id, view.id());
        assertTrue(view.players().isEmpty());
    }
}
