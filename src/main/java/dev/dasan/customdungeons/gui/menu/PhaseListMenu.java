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

public final class PhaseListMenu extends MobMenuBase {
    public PhaseListMenu(Player p,MobMenu.MobDraft d,Menu parent) { super(p,"phases",d,parent); }
    @Override protected void render() {
        action(4,"add","",() -> {
            double threshold=data.phases.isEmpty() ? .66 : data.phases.getLast().threshold / 2;
            data.phases.add(new MobMenu.PhaseDraft(new PhaseDef(threshold,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,20)));
        });
        var buttons=new ArrayList<Button>();
        for(int i=0;i<data.phases.size();i++) {
            final int n=i; var phase=data.phases.get(i);
            buttons.add(entry(Material.NETHER_STAR,(i+1)+" / "+phase.threshold*100+"%",() -> new PhaseMenu(viewer,data,phase,this).open(),() -> data.phases.remove(n)));
        }
        entries(buttons);
    }
}
