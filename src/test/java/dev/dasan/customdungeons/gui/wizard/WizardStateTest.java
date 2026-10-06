package dev.dasan.customdungeons.gui.wizard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WizardStateTest {
    @Test void onlyValidStepsAdvanceAndCompletedStepsCanBeRevisited() {
        var state = new WizardState(0,0);
        assertFalse(state.next(false)); assertEquals(0,state.step());
        assertFalse(state.back()); assertFalse(state.visit(1));
        assertTrue(state.next(true)); assertEquals(1,state.step());
        assertTrue(state.next(true)); assertEquals(2,state.completed());
        assertTrue(state.back()); assertEquals(1,state.step());
        assertTrue(state.visit(0)); assertFalse(state.visit(3));
        assertTrue(state.visit(2));
    }
    @Test void restoredProgressKeepsStepAndCompletionButCannotSkipLocks() {
        var state=new WizardState(4,5);
        assertEquals(4,state.step()); assertEquals(5,state.completed());
        assertTrue(state.visit(5)); assertFalse(state.visit(6));
        assertThrows(IllegalArgumentException.class,()->new WizardState(4,2));
    }
    @Test void reviewCannotAdvanceBeyondTheSevenSteps() {
        var state=new WizardState(0,0);
        for(int i=0;i<6;i++) assertTrue(state.next(true));
        assertEquals(6,state.step()); assertFalse(state.next(true));
    }
    @Test void invalidatingAnEarlierStepRelocksLaterSteps() {
        var state=new WizardState(6,6);
        state.reconcile(i->i!=1);
        assertEquals(1,state.completed()); assertEquals(1,state.step());
        assertFalse(state.visit(2));
    }
}
