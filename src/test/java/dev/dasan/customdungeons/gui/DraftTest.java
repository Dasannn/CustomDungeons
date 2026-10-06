package dev.dasan.customdungeons.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DraftTest {
    @Test void draftTracksDirty() {
        Draft<String> draft = new Draft<>("original");
        assertEquals("original", draft.original());
        assertEquals("original", draft.get());
        assertFalse(draft.dirty());
        draft.set("edited");
        assertTrue(draft.dirty());
        assertEquals("original", draft.original());
        assertEquals("edited", draft.get());
        draft.set(new String("original"));
        assertFalse(draft.dirty());
    }
    @Test void nullValuesAreComparedSafely() {
        Draft<String> draft = new Draft<>(null);
        assertFalse(draft.dirty());
        draft.set("value");
        assertTrue(draft.dirty());
        draft.set(null);
        assertFalse(draft.dirty());
    }
}
