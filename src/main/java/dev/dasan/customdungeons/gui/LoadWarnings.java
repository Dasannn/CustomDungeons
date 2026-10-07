package dev.dasan.customdungeons.gui;

import dev.dasan.customdungeons.config.Validator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Localized retained load adjustments, with the same field paths used in console warnings. */
public final class LoadWarnings {
    private LoadWarnings() {}
    public static List<Component> lore(List<Validator.Warning> warnings) {
        var messages=MenuListener.instance().messages();
        return warnings.stream().map(w->messages.get("gui.common.load-adjustment-line",
                Placeholder.unparsed("path",w.path()),Placeholder.component("warning",messages.get(w.messageKey(),
                        w.args().entrySet().stream().map(e->Placeholder.unparsed(e.getKey(),e.getValue())).toArray(TagResolver[]::new)))))
                .toList();
    }
}
