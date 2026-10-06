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

public final class AbilityListMenu extends MobMenuBase {
    private final MobMenu.Loadout loadout;
    public AbilityListMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, Menu parent) { super(p, "abilities", d, parent); loadout = l; }
    @Override protected void render() {
        action(4, Material.BLAZE_POWDER, "add", "", () -> new AbilityPickerMenu(viewer, this, a -> loadout.abilities.add(defaults(a))).open());
        var buttons = new ArrayList<Button>();
        for (int i=0; i<loadout.abilities.size(); i++) {
            final int index = i; AbilityInstance a = loadout.abilities.get(i);
            Material icon = registry().get(a.abilityId()).map(MobMenuBase::abilityIcon).orElse(Material.BARRIER);
            buttons.add(entry(icon, MenuListener.instance().messages().get("gui.mob.ability-entry",
                Placeholder.component("ability",abilityName(a.abilityId())),Placeholder.component("trigger",displayValue("trigger",a.trigger()))),
                () -> new ParamEditorMenu(viewer, data, a, this, v -> loadout.abilities.set(index, v)).open(),
                () -> loadout.abilities.remove(index)));
        }
        entries(buttons);
    }
}
