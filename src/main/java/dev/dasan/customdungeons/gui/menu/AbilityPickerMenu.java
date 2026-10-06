package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class AbilityPickerMenu extends PagedMenu<Ability> {
    private final Menu previous;
    private final Consumer<Ability> selected;
    public AbilityPickerMenu(Player p, Menu parent, Consumer<Ability> selected) {
        super(p, MobMenuBase.message("abilities"), 6); previous = parent; this.selected = selected;
    }
    @Override protected List<Ability> items() { return MobMenuBase.registry().all().stream().sorted(Comparator.comparing(Ability::id)).toList(); }
    @Override protected Button button(Ability a) {
        return Button.of(a.icon(), MobMenuBase.label("entry", a.id()), List.of(MobMenuBase.message("ability-pick-lore")), (p,c) ->
            MenuListener.instance().later(() -> { selected.accept(a); previous.open(); }));
    }
    @Override protected Menu parent() { return previous; }
}
