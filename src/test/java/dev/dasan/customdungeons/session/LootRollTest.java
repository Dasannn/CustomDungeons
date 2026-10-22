package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.LootEntryDef;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootRollTest {
    ItemStack stack() { var item=mock(ItemStack.class);when(item.clone()).thenReturn(item);return item; }
    @Test void zeroNeverOneAlwaysAndEntriesKeepTheirSlots() {
        var item=stack();int[] draws={0};
        var contents=LootRoll.generate(List.of(new LootEntryDef(item,0),new LootEntryDef(item,1)),()->{draws[0]++;return .5;});
        assertNull(contents[0]);assertSame(item,contents[1]);assertEquals(27,contents.length);assertEquals(2,draws[0]);
    }
    @Test void independentDrawsDoNotNormalizeOrSplitTheStack() {
        var item=stack();when(item.getAmount()).thenReturn(64);var values=new ArrayDeque<>(List.of(.2,.8,.3));
        var contents=LootRoll.generate(Collections.nCopies(3,new LootEntryDef(item,.5)),values::removeFirst);
        assertEquals(64,contents[0].getAmount());assertNull(contents[1]);assertEquals(64,contents[2].getAmount());assertTrue(values.isEmpty());
    }
    private static final class Stack extends ItemStack {
        int amount;
        final Map<String,Object> nbt;
        Stack(int amount,Map<String,Object> nbt){super();this.amount=amount;this.nbt=Map.copyOf(nbt);}
        @Override public int getAmount(){return amount;}
        @Override public void setAmount(int amount){this.amount=amount;}
        @Override public ItemStack clone(){return new Stack(amount,nbt);}
    }
    @Test void generatedStacksKeepAllMetadataAndCannotMutateTemplatesOrOtherRuns() {
        var original=new Stack(64,Map.of("name","Treasure","custom-data",Map.of("token",42),"enchantments",List.of("unbreaking")));
        var entry=new LootEntryDef(original,1);original.setAmount(1);
        var first=(Stack)LootRoll.generate(List.of(entry),()->.5)[0];var second=(Stack)LootRoll.generate(List.of(entry),()->.5)[0];
        assertEquals(64,first.getAmount());assertEquals(original.nbt,first.nbt);assertNotSame(first,second);first.setAmount(0);
        assertEquals(64,second.getAmount());assertEquals(64,entry.item().getAmount());
    }
    @Test void emptyAndAllFailedAreValid() {
        assertTrue(Arrays.stream(LootRoll.generate(List.of(),()->{fail();return 0;})).allMatch(Objects::isNull));
        assertTrue(Arrays.stream(LootRoll.generate(List.of(new LootEntryDef(stack(),.1)),()->.9)).allMatch(Objects::isNull));
    }
}
