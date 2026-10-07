package dev.dasan.customdungeons.gui;

import dev.dasan.customdungeons.config.NumericRange;
import dev.dasan.customdungeons.config.NumericRanges;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** Shared presentation and entry adapter; bounds never come from translated text. */
public final class NumericInputs {
    private NumericInputs() {}
    public static Component description(NumericRange range) {
        var messages=MenuListener.instance().messages();
        return messages.get("gui.common.numeric-range",Placeholder.unparsed("min",range.format(range.min())),
                Placeholder.unparsed("max",range.format(range.max())),Placeholder.component("origin",
                        messages.get("gui.common.range-origin-"+range.origin().name().toLowerCase(java.util.Locale.ROOT))));
    }
    public static List<Component> lore(NumericRange range) {
        var lines=new ArrayList<Component>();lines.add(description(range));
        lines.add(MenuListener.instance().messages().get("gui.common.numeric-precision",Placeholder.unparsed("decimals",Integer.toString(range.decimals()))));
        if(range.vanillaZero()) lines.add(MenuListener.instance().messages().get("gui.common.vanilla-zero"));
        if(range.equals(NumericRanges.SCALE)) lines.add(MenuListener.instance().messages().get("gui.common.scale-minimum",
                Placeholder.unparsed("min",range.format(NumericRanges.SCALE_ATTRIBUTE_MIN))));
        return List.copyOf(lines);
    }
    public static Button decorate(Button button,NumericRange range) {
        button.icon().editMeta(meta->{var lines=new ArrayList<Component>(lore(range));
            if(meta.lore()!=null) lines.addAll(meta.lore());meta.lore(lines);});
        return button;
    }
    public static void edit(Player player,Component title,NumericRange range,double current,DoubleConsumer submit) {
        Inputs.ranged(player,title,range,range.clamp(current),submit);
    }
    public static void clicks(Player player,Component title,NumericRange range,double current,DoubleConsumer submit) {
        Inputs.rangedClicks(player,title,range,range.clamp(current),submit);
    }
}
