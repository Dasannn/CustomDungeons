package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolListenerTest {
    final Player player = mock(Player.class);
    final PlayerInventory inventory = mock(PlayerInventory.class);
    final ToolService tools = mock(ToolService.class);
    final ToolListener listener;

    ToolListenerTest() {
        var plugin = mock(CustomDungeonsPlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        when(player.getInventory()).thenReturn(inventory);
        listener = new ToolListener(plugin, tools, mock(PreviewRenderer.class), mock(SpawnerMarkers.class));
    }

    @Test void qDropDeletesEntityWithoutReturningToolToInventoryEvenIfCancelled() {
        var item = mock(Item.class);
        doReturn(ToolServiceTest.tool(ToolType.REGION)).when(item).getItemStack();
        var event = new PlayerDropItemEvent(player, item);
        event.setCancelled(true);
        listener.drop(event);
        assertFalse(event.isCancelled());
        verify(item).remove();
        verify(tools).stored(player);
    }

    @Test void ordinaryDropIsUntouched() {
        var item = mock(Item.class);
        when(item.getItemStack()).thenReturn(mock(ItemStack.class));
        var event = new PlayerDropItemEvent(player, item);
        listener.drop(event);
        verify(item, never()).remove();
        verifyNoInteractions(tools);
    }

    @Test void inventoryQAndControlQDeleteToolWithoutDropping() {
        for (ClickType click : new ClickType[]{ClickType.DROP, ClickType.CONTROL_DROP}) {
            var event = mock(InventoryClickEvent.class);
            when(event.getWhoClicked()).thenReturn(player);
            doReturn(ToolServiceTest.tool(ToolType.DOOR)).when(event).getCurrentItem();
            when(event.getClick()).thenReturn(click);
            when(event.getClickedInventory()).thenReturn(inventory);
            when(event.getSlot()).thenReturn(3);
            listener.click(event);
            verify(event).setCancelled(true);
            verify(event).setCurrentItem(null);
        }
    }

    @Test void outsideCursorDropDeletesWholeTool() {
        var event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        doReturn(ToolServiceTest.tool(ToolType.POINT)).when(event).getCursor();
        when(event.getAction()).thenReturn(InventoryAction.DROP_ALL_CURSOR);
        listener.click(event);
        verify(event).setCancelled(true);
        verify(player).setItemOnCursor(null);
    }

    @Test void creativeDeletionAndReplacementAreAllowedButCreativeCopiesAreBlocked() {
        var event = mock(InventoryCreativeEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(3);
        doReturn(ToolServiceTest.tool(ToolType.REGION)).when(event).getCurrentItem();
        when(event.getCursor()).thenReturn(mock(ItemStack.class));
        listener.click(event);
        listener.creative(event);
        verify(event, never()).setCancelled(true);
        verify(tools).stored(player);
        doReturn(ToolServiceTest.tool(ToolType.REGION)).when(event).getCursor();
        listener.creative(event);
        verify(event).setCancelled(true);
    }

    @Test void creativeTrashDoesNotSpawnToolAndClearsServerCursor() {
        var event = mock(InventoryCreativeEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getSlotType()).thenReturn(InventoryType.SlotType.OUTSIDE);
        doReturn(ToolServiceTest.tool(ToolType.POINT)).when(event).getCursor();
        doReturn(ToolServiceTest.tool(ToolType.POINT)).when(player).getItemOnCursor();
        listener.creative(event);
        verify(event).setCancelled(true);
        verify(player).setItemOnCursor(null);
        verify(tools).stored(player);
    }

    @Test void creativeAirDeletesOwnedTool() {
        var event = mock(InventoryCreativeEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getSlot()).thenReturn(40);
        doReturn(ToolServiceTest.tool(ToolType.DOOR)).when(event).getCurrentItem();
        listener.creative(event);
        verify(event, never()).setCancelled(true);
        verify(tools).stored(player);
    }

    @Test void dragCannotInsertToolIntoContainerOrEquipment() {
        for (int slot : new int[]{3, 36}) {
            var event = mock(InventoryDragEvent.class);
            var view = mock(InventoryView.class);
            when(event.getWhoClicked()).thenReturn(player);
            when(event.getView()).thenReturn(view);
            when(event.getRawSlots()).thenReturn(Set.of(slot));
            when(view.getInventory(slot)).thenReturn(slot == 3 ? mock(Inventory.class) : inventory);
            when(view.convertSlot(slot)).thenReturn(slot);
            doReturn(ToolServiceTest.tool(ToolType.REGION)).when(event).getOldCursor();
            listener.drag(event);
            verify(event).setCancelled(true);
        }
    }

    @Test void containerShiftAndCloneStillCannotMoveTools() {
        for (ClickType click : new ClickType[]{ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.MIDDLE}) {
            var event = mock(InventoryClickEvent.class);
            when(event.getWhoClicked()).thenReturn(player);
            doReturn(ToolServiceTest.tool(ToolType.SPAWNER)).when(event).getCurrentItem();
            when(event.getClick()).thenReturn(click);
            when(event.getClickedInventory()).thenReturn(mock(Inventory.class));
            listener.click(event);
            verify(event).setCancelled(true);
        }
    }
    @Test void registeredPlatesAndTheirSupportsCannotBeBroken() {
        var plate=mock(org.bukkit.block.Block.class);var support=mock(org.bukkit.block.Block.class);
        when(support.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(plate);
        when(tools.protectedPlate(plate)).thenReturn(true);
        var direct=mock(org.bukkit.event.block.BlockBreakEvent.class);when(direct.getBlock()).thenReturn(plate);
        listener.breakPlate(direct);verify(direct).setCancelled(true);
        var below=mock(org.bukkit.event.block.BlockBreakEvent.class);when(below.getBlock()).thenReturn(support);
        listener.breakPlate(below);verify(below).setCancelled(true);
        var unrelated=mock(org.bukkit.block.Block.class);var normal=mock(org.bukkit.event.block.BlockBreakEvent.class);
        when(normal.getBlock()).thenReturn(unrelated);listener.breakPlate(normal);verify(normal,never()).setCancelled(true);
    }

}
