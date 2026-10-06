package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.model.BlockPos;
import dev.dasan.customdungeons.model.Region;
import org.junit.jupiter.api.Test;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.text.Messages;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectionTest {
    @Test void selectionCompleteAndToRegionNormalizes() {
        Selection selection = new Selection("dungeons", new BlockPos(7, -2, 9), new BlockPos(-3, 6, 1));
        assertTrue(selection.complete());
        assertEquals(new Region("dungeons", new BlockPos(-3, -2, 1), new BlockPos(7, 6, 9)), selection.toRegion());
    }
    @Test void incompleteSelectionCannotBecomeRegion() {
        for (Selection selection : new Selection[] {
                new Selection("world", null, null),
                new Selection("world", new BlockPos(1, 2, 3), null),
                new Selection("world", null, new BlockPos(1, 2, 3))}) {
            assertFalse(selection.complete());
            assertThrows(IllegalStateException.class, selection::toRegion);
        }
    }
    @Test void singleBlockSelectionHasUnitVolume() {
        BlockPos point = new BlockPos(-1, -64, 2);
        assertEquals(1, new Selection("world", point, point).toRegion().volume());
    }

    private static ItemStack tool(ToolType type) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ToolService.TOOL_KEY)).thenReturn(true);
        when(pdc.get(ToolService.TOOL_KEY, PersistentDataType.STRING)).thenReturn(type.name() + ":example");
        return item;
    }
    private static class Fixture {
        final CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class);
        final Server server = mock(Server.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ToolService tools = mock(ToolService.class);
        final PreviewRenderer previews = mock(PreviewRenderer.class);
        final SpawnerMarkers markers = mock(SpawnerMarkers.class);
        final ToolListener listener = new ToolListener(plugin, tools, previews, markers);
        Fixture() {
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        }
    }
    @Test void toolDropIsCancelled() {
        Fixture f = new Fixture();
        Item dropped = mock(Item.class);
        ItemStack regionTool = tool(ToolType.REGION);
        when(dropped.getItemStack()).thenReturn(regionTool);
        PlayerDropItemEvent event = new PlayerDropItemEvent(f.player, dropped);
        f.listener.drop(event);
        assertTrue(event.isCancelled());
        ItemStack normalItem = mock(ItemStack.class);
        when(dropped.getItemStack()).thenReturn(normalItem);
        event = new PlayerDropItemEvent(f.player, dropped);
        f.listener.drop(event);
        assertFalse(event.isCancelled());
    }
    @Test void containerHotbarSwapIsCancelled() {
        Fixture f = new Fixture();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(f.player);
        when(event.getClickedInventory()).thenReturn(mock(Inventory.class));
        when(event.getHotbarButton()).thenReturn(2);
        ItemStack doorTool = tool(ToolType.DOOR);
        when(f.inventory.getItem(2)).thenReturn(doorTool);
        f.listener.click(event);
        verify(event).setCancelled(true);
    }
    @Test void shiftTransferAndCloneAreCancelledButOwnStorageMoveIsAllowed() {
        Fixture f = new Fixture();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(f.player);
        ItemStack pointTool = tool(ToolType.POINT);
        when(event.getCurrentItem()).thenReturn(pointTool);
        when(event.getClickedInventory()).thenReturn(f.inventory);
        when(event.getSlot()).thenReturn(12);
        when(event.getHotbarButton()).thenReturn(-1);
        f.listener.click(event);
        verify(event, never()).setCancelled(true);
        when(event.isShiftClick()).thenReturn(true);
        f.listener.click(event);
        verify(event).setCancelled(true);
        clearInvocations(event);
        when(event.isShiftClick()).thenReturn(false);
        when(event.getAction()).thenReturn(InventoryAction.CLONE_STACK);
        f.listener.click(event);
        verify(event).setCancelled(true);
    }
    @Test void dragIntoContainerIsCancelled() {
        Fixture f = new Fixture();
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory top = mock(Inventory.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getSize()).thenReturn(27);
        ItemStack spawnerTool = tool(ToolType.SPAWNER);
        when(event.getOldCursor()).thenReturn(spawnerTool);
        when(event.getRawSlots()).thenReturn(Set.of(1, 30));
        f.listener.drag(event);
        verify(event).setCancelled(true);
    }
    @Test void deathRemovesToolsEvenWithKeepInventory() {
        Fixture f = new Fixture();
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        ItemStack tool = tool(ToolType.REGION);
        ItemStack normal = mock(ItemStack.class);
        List<ItemStack> drops = new ArrayList<>(List.of(tool, normal));
        List<ItemStack> keep = new ArrayList<>(List.of(tool, normal));
        when(event.getEntity()).thenReturn(f.player);
        when(event.getDrops()).thenReturn(drops);
        when(event.getItemsToKeep()).thenReturn(keep);
        when(f.inventory.getSize()).thenReturn(41);
        when(f.inventory.getItem(0)).thenReturn(tool);
        f.listener.death(event);
        assertEquals(List.of(normal), drops);
        assertEquals(List.of(normal), keep);
        verify(f.inventory).setItem(0, null);
        verify(f.tools).clear(f.player.getUniqueId());
    }
    @Test void changingWorldResetsOppositeCornerAndPointIsDefensivelyCopied() {
        Messages messages = mock(Messages.class);
        PreviewRenderer previews = mock(PreviewRenderer.class);
        ToolService tools = new ToolService(messages, previews);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        World first = mock(World.class), second = mock(World.class);
        when(first.getName()).thenReturn("first");
        when(second.getName()).thenReturn("second");
        tools.select(player, new Location(first, 1, 2, 3), true);
        tools.select(player, new Location(first, 4, 5, 6), false);
        assertTrue(tools.selection(uuid).orElseThrow().complete());
        tools.select(player, new Location(second, 4, 5, 6), false);
        assertNull(tools.selection(uuid).orElseThrow().a());
        assertEquals("second", tools.selection(uuid).orElseThrow().world());
        Location location = new Location(second, 1.25, 2, 3, 45, -20);
        tools.point(player, location);
        location.setYaw(0);
        Location stored = tools.lastPoint(uuid).orElseThrow();
        assertEquals(45, stored.getYaw());
        assertEquals(-20, stored.getPitch());
        stored.setX(100);
        assertEquals(1.25, tools.lastPoint(uuid).orElseThrow().getX());
        tools.clear(uuid);
        assertTrue(tools.lastPoint(uuid).isEmpty());
        assertTrue(tools.selection(uuid).isEmpty());
    }
    @Test void oneTickerStopsWhenLastToolIsStored() {
        Fixture f = new Fixture();
        when(f.plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(f.plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doReturn(List.of(f.player)).when(f.server).getOnlinePlayers();
        when(f.player.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        ItemStack regionTool = tool(ToolType.REGION);
        when(f.inventory.getItemInMainHand()).thenReturn(regionTool);
        BukkitTask task = mock(BukkitTask.class);
        when(f.scheduler.runTaskTimer(eq(f.plugin), any(Runnable.class), eq(0L), eq(10L))).thenReturn(task);
        PreviewRenderer previews = new PreviewRenderer(f.plugin, f.markers);
        previews.refresh();
        previews.refresh();
        assertTrue(previews.running());
        verify(f.scheduler, times(1)).runTaskTimer(eq(f.plugin), any(Runnable.class), eq(0L), eq(10L));
        when(f.inventory.getItemInMainHand()).thenReturn(null);
        previews.refresh();
        assertFalse(previews.running());
        verify(task).cancel();
    }

    @Test void toolCannotBeInsertedIntoBundleInOwnInventory() {
        Fixture f = new Fixture();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        ItemStack bundle = mock(ItemStack.class);
        org.bukkit.inventory.meta.BundleMeta meta = mock(org.bukkit.inventory.meta.BundleMeta.class);
        when(bundle.getItemMeta()).thenReturn(meta);
        ItemStack item = tool(ToolType.POINT);
        when(event.getWhoClicked()).thenReturn(f.player);
        when(event.getClickedInventory()).thenReturn(f.inventory);
        when(event.getSlot()).thenReturn(12);
        when(event.getHotbarButton()).thenReturn(-1);
        when(event.getCurrentItem()).thenReturn(item);
        when(event.getCursor()).thenReturn(bundle);
        f.listener.click(event);
        verify(event).setCancelled(true);
    }
}
