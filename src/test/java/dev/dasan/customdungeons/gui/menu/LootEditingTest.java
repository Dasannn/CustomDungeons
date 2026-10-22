package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.NumericRanges;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LootEditingTest {
    @Test void chanceClicksKeepValidatorPrecisionWithoutAccumulatedRounding() {
        double chance=.35;
        for(int i=0;i<40;i++) {chance=LootChanceInputMenu.adjust(chance,true,false);assertTrue(NumericRanges.LOOT_CHANCE.containsPrecise(chance));}
        for(int i=0;i<40;i++)chance=LootChanceInputMenu.adjust(chance,false,false);
        assertEquals(.35,chance);
    }
    @Test void shiftChanceClicksUseTenPercentagePointsAndClampToSharedBounds() {
        assertEquals(.45,LootChanceInputMenu.adjust(.35,true,true));
        assertEquals(NumericRanges.LOOT_CHANCE.max(),LootChanceInputMenu.adjust(.99,true,true));
        assertEquals(NumericRanges.LOOT_CHANCE.min(),LootChanceInputMenu.adjust(.01,false,true));
    }
    @Test void allTwentySevenInputSlotsIncludeEdgesAndExcludeChrome() {
        assertEquals(27,java.util.stream.IntStream.range(0,54).filter(LootContentMenu::itemSlot).count());
        assertTrue(LootContentMenu.itemSlot(18));assertTrue(LootContentMenu.itemSlot(44));assertFalse(LootContentMenu.itemSlot(45));
    }
    @Test void roomReorderingAndTriggerOrContentChangesNeedNoBlockMutation() {
        var d=DungeonMenuFlowTest.definition("loot");var first=d.rooms().getFirst();
        var a=DungeonMenu.withLoot(first,List.of(new LootChestDef("a",new Point("world",0,64,0,0,0),LootChestDef.Trigger.ROOM_CLEAR,List.of())));
        var b=new RoomDef("b",first.region(),first.checkpoint(),null,UnlockMode.AUTOMATIC,null,List.of());
        var v=new DungeonMenu.Values(d);v.rooms=List.of(a,b);var before=v.build();v.rooms=List.of(b,a);
        assertFalse(LootPlacement.changed(before,v.build()));
        v.rooms=List.of(b,DungeonMenu.withLoot(a,List.of(new LootChestDef("a",a.lootChests().getFirst().location(),LootChestDef.Trigger.ROOM_ENTER,List.of()))));
        assertFalse(LootPlacement.changed(before,v.build()));
        v.rooms=List.of(b,DungeonMenu.withLoot(a,List.of()));assertTrue(LootPlacement.changed(before,v.build()));
    }
}
