package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DungeonDraftTest {
    @Test void replacementPreservesOriginalAndOtherEntries() {
        var original=List.of(new WaveEntry("a",1,0),new WaveEntry("b",2,3));
        var edited=DungeonMenu.replace(original,1,new WaveEntry("b",5,8));
        assertEquals(2,original.get(1).count());assertEquals(original.get(0),edited.get(0));
        assertEquals(5,edited.get(1).count());assertThrows(UnsupportedOperationException.class,()->edited.clear());
    }
    @Test void builderPreservesEveryUneditedContractField() {
        var d=new DungeonDef("test","Nombre",true,new Point("w",1,2,3,4,5),new Point("w",6,7,8,9,10),
                2,7,12,5,true,90,120,true,new ScalingDef(.25,.15),Map.of(HookEvent.FULL,List.of("say full")),
                new RewardDef(List.of(),10,20,List.of("say reward")),List.of(new RoomDef("r",null,null,null,UnlockMode.KEY,"z",List.of())));
        var copy=new DungeonMenu.Values(d);assertEquals(d,copy.build());copy.name="Nuevo";
        var changed=copy.build();assertEquals("Nombre",d.displayName());assertEquals("Nuevo",changed.displayName());
        copy.name=d.displayName();assertEquals(d,copy.build());
    }
    @Test void disconnectSettingSurvivesEditsFromOtherMenus() {
        var codec=new dev.dasan.customdungeons.config.DefinitionCodec();
        var def=codec.decodeDungeon("test",new org.bukkit.configuration.file.YamlConfiguration()).withDisconnectMode(DisconnectMode.RETURN_TO_EXIT);
        var values=new DungeonMenu.Values(def);values.lives=7;
        assertEquals(DisconnectMode.RETURN_TO_EXIT,values.build().disconnectMode());
    }
    @Test void identifiersAreSafeAndUniqueAcrossDeletedGaps() {
        assertTrue(DungeonMenu.validId("dungeon_2-test"));
        for(String id:Arrays.asList(null,"","../escape","UPPER","a".repeat(33))) assertFalse(DungeonMenu.validId(id));
        assertEquals("spawner_2",DungeonMenu.nextId("spawner_",List.of("spawner_1","spawner_3")));
    }
    @Test void rewardHasExactly27CellsSeparateFromControls() {
        int count=0;
        for(int slot=0;slot<54;slot++) if(RewardMenu.itemSlot(slot)) {
            count++;assertTrue(slot>=18&&slot<45);
        }
        assertEquals(27,count);
    }
    @Test void invalidYamlNumbersCanBeCorrectedWithoutBreakingInputs() {
        assertEquals(0, DungeonEditor.inputValue(Double.NaN,0,100));
        assertEquals(0, DungeonEditor.inputValue(Double.POSITIVE_INFINITY,0,100));
        assertEquals(100, DungeonEditor.inputValue(1000,0,100));
        assertEquals(0, DungeonEditor.inputValue(-10,0,100));
        assertEquals(20, DungeonEditor.inputValue(20,0,100));
    }
    @Test void listEditsPreserveOrderAndRejectStaleIndexes() {
        var source=List.of("a","b","c");
        assertEquals(List.of("a","c"),DungeonMenu.remove(source,1));
        assertEquals(List.of("a","b","c","d"),DungeonMenu.append(source,"d"));
        assertEquals(List.of("a","b","c"),source);
        assertThrows(IndexOutOfBoundsException.class,()->DungeonMenu.replace(source,3,"d"));
    }
}
