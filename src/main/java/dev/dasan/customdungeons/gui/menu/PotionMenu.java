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

public final class PotionMenu extends MobMenuBase {
    private final MobMenu.Loadout loadout;
    public PotionMenu(Player p, MobMenu.MobDraft d, MobMenu.Loadout l, Menu parent) { super(p, "potions", d, parent); loadout = l; }
    @Override protected void render() {
        action(4, Material.POTION, "add", "", () -> choose(viewer, "potions", potionKeys(), this,
            key -> loadout.potions.add(new PotionDef(key, 0, true))));
        var buttons = new ArrayList<Button>();
        for (int i=0; i<loadout.potions.size(); i++) {
            final int index = i; PotionDef potion = loadout.potions.get(i);
            buttons.add(entry(Material.POTION, potion.effectKey() + " / " + (potion.amplifier()+1),
                () -> new MobMenuBase(viewer, "potions", data, this) {
                    @Override protected void render() {
                        PotionDef current = loadout.potions.get(index);
                        select(10, "potion-type", current.effectKey(), potionKeys(), v -> loadout.potions.set(index, new PotionDef(v,current.amplifier(),current.particles())));
                        number(11, "potion-level", current.amplifier()+1, 1, 256, v -> loadout.potions.set(index, new PotionDef(current.effectKey(),(int)v-1,current.particles())));
                        bool(12, "particles-visible", current.particles(), v -> loadout.potions.set(index,new PotionDef(current.effectKey(),current.amplifier(),v)));
                    }
                }.open(), () -> loadout.potions.remove(index)));
        }
        entries(buttons);
    }
}
