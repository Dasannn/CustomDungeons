package dev.dasan.customdungeons.tool;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolInventoryTest {
    @Test void findsEveryCopyWithoutTouchingOtherTypesOrEmptySlots() {
        ToolType[] contents = new ToolType[41];
        contents[0] = ToolType.POINT;
        contents[4] = ToolType.REGION;
        contents[12] = ToolType.POINT;
        contents[40] = ToolType.POINT;
        assertEquals(List.of(0, 12, 40), ToolInventory.matchingSlots(contents, ToolType.POINT));
        assertEquals(List.of(4), ToolInventory.matchingSlots(contents, ToolType.REGION));
        assertEquals(List.of(), ToolInventory.matchingSlots(contents, ToolType.DOOR));
    }

    @Test void reissuingEveryToolTypeKeepsItsFirstSlotAndFindsOnlyItsCopies() {
        for (ToolType type : ToolType.values()) {
            assertEquals(List.of(1, 3), ToolInventory.matchingSlots(new ToolType[] {null, type, null, type}, type));
        }
    }
}
