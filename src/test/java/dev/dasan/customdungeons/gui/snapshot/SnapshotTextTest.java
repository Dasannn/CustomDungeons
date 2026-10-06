package dev.dasan.customdungeons.gui.snapshot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotTextTest {
    @Test void nestedTextKeepsPlainTextAndFirstVisibleInheritedColor() {
        var text = Component.empty().append(Component.text("Título", NamedTextColor.GOLD))
                .append(Component.text(" y detalle", NamedTextColor.GRAY));
        assertEquals("Título y detalle", SnapshotText.plain(text));
        assertEquals("#ffaa00", SnapshotText.color(text));
        assertEquals("#ff5555", SnapshotText.color(Component.empty().color(NamedTextColor.RED)
                .append(Component.text("Heredado"))));
        assertEquals("#ffffff", SnapshotText.color(Component.empty()));
    }
}
