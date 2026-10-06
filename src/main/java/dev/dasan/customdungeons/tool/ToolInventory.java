package dev.dasan.customdungeons.tool;

import java.util.ArrayList;
import java.util.List;

/** Pure inventory scan; null entries represent ordinary items or empty slots. */
final class ToolInventory {
    private ToolInventory() {}

    static List<Integer> matchingSlots(ToolType[] contents, ToolType requested) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == requested) {
                slots.add(i);
            }
        }
        return slots;
    }
}
