package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import java.util.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyInteractionTest {
    final SessionManager manager = mock(SessionManager.class);
    final SessionListener listener = new SessionListener(manager);
    ItemStack key(boolean marked) {
        var item = mock(ItemStack.class);
        var pdc = mock(PersistentDataContainer.class);
        when(item.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(MobKeys.KEY_ITEM,PersistentDataType.STRING)).thenReturn(marked);
        return item;
    }
    @Test void foreignOrStaleKeysNeverPlaceOrUseVanillaBlocks() {
        for (Action action : List.of(Action.RIGHT_CLICK_AIR,Action.RIGHT_CLICK_BLOCK,Action.LEFT_CLICK_BLOCK)) {
            var event = mock(PlayerInteractEvent.class);
            var marked = key(true); when(event.getItem()).thenReturn(marked); when(event.getAction()).thenReturn(action);
            var player = mock(Player.class); when(event.getPlayer()).thenReturn(player);
            listener.interact(event);
            verify(event).setUseInteractedBlock(Event.Result.DENY);
            verify(event).setUseItemInHand(Event.Result.DENY);
        }
        var place = mock(BlockPlaceEvent.class); var markedPlace = key(true); when(place.getItemInHand()).thenReturn(markedPlace);
        listener.place(place); verify(place).setCancelled(true);
    }
    @Test void ordinaryHooksAreUnaffected() {
        var ordinary = key(false); var event = mock(PlayerInteractEvent.class); when(event.getItem()).thenReturn(ordinary);
        listener.interact(event); verify(event,never()).setUseItemInHand(any());
        var place = mock(BlockPlaceEvent.class); when(place.getItemInHand()).thenReturn(ordinary);
        listener.place(place); verify(place,never()).setCancelled(anyBoolean());
        verifyNoInteractions(manager);
    }
    @Test void cancelledAirClickRoutesToOwningKeyService() {
        var fixture = new SessionRuntimeRegressionTest(); fixture.configure();
        var session = mock(DungeonSession.class); var runtime = fixture.runtime(manager,session);
        runtime.keys = mock(KeyService.class);
        var player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session)); when(manager.runtime(session)).thenReturn(runtime);
        var item = key(true);
        var event = mock(PlayerInteractEvent.class);
        when(event.getItem()).thenReturn(item); when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR); when(event.isCancelled()).thenReturn(true);
        listener.interact(event);
        verify(runtime.keys).use(player,null,item); verify(event).setUseItemInHand(Event.Result.DENY);
    }
}
