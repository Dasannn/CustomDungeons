package dev.dasan.customdungeons.tool;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolReadOnlyDataTest {
    @Test void enchantedItemsAreInspectedWithoutCopyingTheirMetadata() {
        var item=mock(ItemStack.class);var data=mock(PersistentDataContainerView.class);
        when(item.getPersistentDataContainer()).thenReturn(data);when(item.hasItemMeta()).thenReturn(true);
        assertFalse(ToolService.isTool(item));assertNull(ToolService.type(item));
        when(data.has(ToolService.TOOL_KEY)).thenReturn(true);
        when(data.get(ToolService.TOOL_KEY,PersistentDataType.STRING)).thenReturn("PLATE:dungeon");
        assertTrue(ToolService.isTool(item));assertEquals(ToolType.PLATE,ToolService.type(item));
        verify(item,never()).getItemMeta();verify(item,never()).hasItemMeta();
        assertFalse(BuildTools.isTool(item));
        when(data.has(BuildTools.KEY)).thenReturn(true);when(data.get(BuildTools.KEY,PersistentDataType.INTEGER)).thenReturn(4);
        assertTrue(BuildTools.isTool(item));assertEquals(4,BuildTools.slot(item));
        verify(item,never()).getItemMeta();verify(item,never()).hasItemMeta();
    }
}
