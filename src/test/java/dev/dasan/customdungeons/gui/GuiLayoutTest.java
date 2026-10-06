package dev.dasan.customdungeons.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutTest {
    @Test void groupsAreCenteredAndNeverTouchTheSideFrame() {
        assertEquals(List.of(13),GuiLayout.centeredRow(1,1));
        assertEquals(List.of(12,14),GuiLayout.centeredRow(1,2));
        assertEquals(List.of(11,13,15),GuiLayout.centeredRow(1,3));
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
    @Test void exampleCountsOnlyPlayersAboveTheConfiguredMinimum() {
        assertEquals(75,GuiLayout.scalingExample(.25,1));
        assertEquals(30,GuiLayout.scalingExample(.15,2),1e-9);
        assertEquals(0,GuiLayout.scalingExample(.25,4));
        assertEquals(0,GuiLayout.scalingExample(.25,6));
    }
}
