package dev.dasan.customdungeons.gui.snapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SnapshotButtonsTest {
    @Test void identifiesOnlyAnExplicitEmptyHandler() {
        assertTrue(SnapshotButtons.noopCreation("set(4, Button.of(icon, msg(\"title\"), List.of(msg(\"lore\")), (p, c) -> { }));"));
        assertFalse(SnapshotButtons.noopCreation("Button.of(icon, msg(\"(title)\"), List.of(), (p,c)->later(() -> {}))"));
        assertFalse(SnapshotButtons.noopCreation("Button.of(icon, name, List.of(), handler)"));
        assertFalse(SnapshotButtons.noopCreation("Button.of(icon, name, List.of(), (p,c)->{ tell(\"info\"); })"));
    }
}
