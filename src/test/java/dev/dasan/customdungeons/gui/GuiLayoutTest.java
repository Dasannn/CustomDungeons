package dev.dasan.customdungeons.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutTest {
    @Test void groupsAreCenteredAndNeverTouchTheSideFrame() {
        assertEquals(List.of(13),GuiLayout.centeredRow(1,1));
        assertEquals(List.of(12,14),GuiLayout.centeredRow(1,2));
        assertEquals(List.of(11,13,15),GuiLayout.centeredRow(1,3));
        assertEquals(List.of(10,12,14,16),GuiLayout.centeredRow(1,4));
        for(int count=0;count<=7;count++) {
            var slots=GuiLayout.centeredRow(2,count);
            assertEquals(count,slots.size());
            assertEquals(count,slots.stream().distinct().count());
            for(int i=0;i<count;i++) {
                assertTrue(slots.get(i)>=19 && slots.get(i)<=25);
                assertEquals(44,slots.get(i)+slots.get(count-1-i));
            }
        }
    }
    @Test void partialPageRowsAreCenteredWithoutChangingEntryOrder() {
        assertEquals(10,GuiLayout.pageSlot(0,10,1));
        assertEquals(16,GuiLayout.pageSlot(6,10,1));
        assertEquals(20,GuiLayout.pageSlot(7,10,1));
        assertEquals(22,GuiLayout.pageSlot(8,10,1));
        assertEquals(24,GuiLayout.pageSlot(9,10,1));
        assertEquals(22,GuiLayout.pageSlot(0,1,2));
    }
    @Test void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->GuiLayout.centeredRow(-1,1));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.centeredRow(6,1));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.centeredRow(1,8));
        assertThrows(IndexOutOfBoundsException.class,()->GuiLayout.pageSlot(3,3,1));
    }
    @Test void adaptableRowsReserveSummaryAndFooterAndCapLongLists() {
        assertEquals(3,GuiLayout.rowsFor(0,7,0));
        assertEquals(3,GuiLayout.rowsFor(7,7,0));
        assertEquals(4,GuiLayout.rowsFor(8,7,0));
        assertEquals(6,GuiLayout.rowsFor(Integer.MAX_VALUE,7,0));
        assertEquals(5,GuiLayout.rowsFor(8,7,1));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.rowsFor(-1,7,0));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.rowsFor(8,0,0));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.rowsFor(8,8,0));
        assertThrows(IllegalArgumentException.class,()->GuiLayout.rowsFor(8,7,4));
    }
    @Test void footerIncludesOnlyContextualControls() {
        assertEquals(List.of(8),GuiLayout.footerSlots(false,false,false));
        assertEquals(List.of(0,4,8),GuiLayout.footerSlots(true,false,true));
        assertEquals(List.of(0,3,5,8),GuiLayout.footerSlots(true,true,false));
        assertEquals(List.of(0,3,4,5,8),GuiLayout.footerSlots(true,true,true));
    }
    @Test void exampleCountsOnlyPlayersAboveTheConfiguredMinimum() {
        assertEquals(75,GuiLayout.scalingExample(.25,1));
        assertEquals(30,GuiLayout.scalingExample(.15,2),1e-9);
        assertEquals(0,GuiLayout.scalingExample(.25,4));
        assertEquals(0,GuiLayout.scalingExample(.25,6));
    }
}
