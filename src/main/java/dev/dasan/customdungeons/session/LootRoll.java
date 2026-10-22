package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.LootEntryDef;
import java.util.*;
import java.util.function.DoubleSupplier;
import org.bukkit.inventory.ItemStack;

/** One independent draw per entry, preserving template slots and full stacks. */
final class LootRoll {
    private LootRoll() {}
    static ItemStack[] generate(List<LootEntryDef> entries,DoubleSupplier random) {
        var contents=new ItemStack[(int)dev.dasan.customdungeons.config.NumericRanges.LOOT_ENTRIES.max()];
        for(int slot=0;slot<entries.size();slot++) {
            var entry=entries.get(slot);
            if(random.getAsDouble()<entry.chance())contents[slot]=entry.item();
        }
        return contents;
    }
}
