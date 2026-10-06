package dev.dasan.customdungeons.gui.menu;
import dev.dasan.customdungeons.model.ComboStep;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ComboEditorTest {
    ComboStep step(String id) { return new ComboStep(id,Map.of("damage",2),0); }
    @Test void independentStepsCanBeAddedEditedMovedAndRemovedWithinLimits() {
        var original=List.of(step("hook"),step("anchor"));
        var editor=new ComboMenu.Editor(original);
        editor.add(step("meteors")); editor.add(step("freeze")); editor.add(step("swap"));
        assertFalse(editor.add(step("lightning")));
        editor.replace(2,new ComboStep("meteors",Map.of("damage",9),ComboMenu.delayTicks(1.25)));
        editor.move(2,0);
        assertEquals("meteors",editor.steps().getFirst().abilityId());
        assertEquals(26,editor.steps().getFirst().delayTicks());
        assertEquals(9,editor.steps().getFirst().params().get("damage"));
        while(editor.steps().size()>2) assertTrue(editor.remove(1));
        assertFalse(editor.remove(0)); assertEquals(original,List.of(step("hook"),step("anchor")));
        assertThrows(UnsupportedOperationException.class,()->editor.steps().clear());
    }
    @Test void delayHasTenthsOfSecondsAndRejectsInvalidInput() {
        assertEquals(24,ComboMenu.parseDelay("1,2").orElseThrow());
        assertEquals(24,ComboMenu.parseDelay("1.2").orElseThrow());
        assertTrue(ComboMenu.parseDelay("NaN").isEmpty());
        assertTrue(ComboMenu.parseDelay("bad").isEmpty());
        assertTrue(ComboMenu.parseDelay("3601").isEmpty());
        assertEquals(2,ComboMenu.delayTicks(.1)); assertEquals(24,ComboMenu.delayTicks(1.2));
        assertEquals("1.2",ComboMenu.delaySeconds(24));
        assertThrows(IllegalArgumentException.class,()->ComboMenu.delayTicks(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->ComboMenu.delayTicks(-1));
    }
}
