package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import java.util.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class BuildModeListenerTest {
    final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class);
    final BuildModeService mode=mock(BuildModeService.class);
    final Player player=mock(Player.class);
    final BuildModeListener listener=new BuildModeListener(plugin,mode);
    BuildModeListenerTest() {
        UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(mode.protects(id)).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
    }
    @Test void everyExtractionPathIsCancelledIncludingCreativeReplacement() {
        for(var click:List.of(ClickType.LEFT,ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.SWAP_OFFHAND,
                ClickType.DROP,ClickType.CONTROL_DROP,ClickType.MIDDLE,ClickType.DOUBLE_CLICK)) {
            var event=mock(InventoryClickEvent.class);when(event.getWhoClicked()).thenReturn(player);when(event.getClick()).thenReturn(click);
            listener.click(event);verify(event).setCancelled(true);
        }
        var creative=mock(InventoryCreativeEvent.class);when(creative.getWhoClicked()).thenReturn(player);
        listener.click(creative);verify(creative).setCancelled(true);
    }
    @Test void dragDropAndOffhandCannotRemoveTools() {
        var drag=mock(InventoryDragEvent.class);when(drag.getWhoClicked()).thenReturn(player);listener.drag(drag);verify(drag).setCancelled(true);
        var drop=new PlayerDropItemEvent(player,mock(Item.class));listener.drop(drop);org.junit.jupiter.api.Assertions.assertTrue(drop.isCancelled());
        var swap=mock(PlayerSwapHandItemsEvent.class);when(swap.getPlayer()).thenReturn(player);listener.swap(swap);verify(swap).setCancelled(true);
    }
    @Test void disconnectRoutesThroughTheSameRestorationBoundary() {
        var event=mock(PlayerQuitEvent.class);when(event.getPlayer()).thenReturn(player);listener.quit(event);verify(mode).disconnected(player);
    }
    @Test void ordinaryInventoryRemainsUsableOutsideBuildMode() {
        when(mode.protects(player.getUniqueId())).thenReturn(false);
        var event=mock(InventoryClickEvent.class);when(event.getWhoClicked()).thenReturn(player);listener.click(event);
        verify(event,never()).setCancelled(true);
    }
}
