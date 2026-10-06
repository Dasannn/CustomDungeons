package dev.dasan.customdungeons.tool.construction;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildStateTest {
    static DungeonDef definition(String name) {
        return new DungeonDef("draft",name,false,null,null,1,0,30,3,false,0,0,false,
                new ScalingDef(.25,.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
    }
    @Test void publishedDraftAdoptsBaselineAfterCrashBeforeBaselineWrite() {
        var state=new BuildState(definition("old"));
        state.change(definition("published"));
        assertFalse(state.conflicts(definition("published")));
        assertEquals(definition("published"),state.snapshot().baseline());
        assertTrue(state.conflicts(definition("another editor")));
    }
    @Test void undoRetainsOnlyTheLastTwentyChangesAndIgnoresNoops() {
        var state=new BuildState(definition("0"));
        for(int i=1;i<=25;i++) state.change(definition(""+i));
        state.change(definition("25"));
        assertEquals(20,state.snapshot().undo().size());
        for(int i=24;i>=5;i--) {assertTrue(state.undo());assertEquals(""+i,state.definition().displayName());}
        assertFalse(state.undo());
    }
    @Test void resumedHistoryAndContextRemainIndependentOfThePublishedBaseline() {
        var state=new BuildState(definition("base"));state.cyclePoint();state.change(definition("draft"));
        var resumed=new BuildState(state.snapshot());
        assertEquals(1,resumed.snapshot().point());
        assertFalse(resumed.conflicts(definition("base")));
        assertTrue(resumed.conflicts(definition("other editor")));
        assertTrue(resumed.undo());assertEquals(definition("base"),resumed.definition());
    }
    @Test void publishAcceptsTheNewBaselineWithoutCreatingAnUndoAction() {
        var state=new BuildState(definition("base"));state.change(definition("draft"));
        state.published(definition("normalized"));
        assertFalse(state.conflicts(definition("normalized")));
        assertEquals(1,state.snapshot().undo().size());
        assertEquals(definition("normalized"),state.definition());
    }
}
