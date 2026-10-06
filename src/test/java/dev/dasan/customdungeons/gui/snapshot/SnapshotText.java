package dev.dasan.customdungeons.gui.snapshot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Text projection only; no changes to the player-facing components. */
public final class SnapshotText {
    private SnapshotText() {}
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
    public static String color(Component component) {
        var color = firstColor(component, null);
        return color == null ? "#ffffff" : color.asHexString().toLowerCase(java.util.Locale.ROOT);
    }
    private static TextColor firstColor(Component component, TextColor inherited) {
        var color = component.color() == null ? inherited : component.color();
        if (component instanceof TextComponent text && !text.content().isBlank()) {
            return color == null ? TextColor.color(0xffffff) : color;
        }
        for (var child : component.children()) {
            var found = firstColor(child, color);
            if (found != null) return found;
        }
        return null;
    }
}
