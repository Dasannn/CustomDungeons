package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ScalingMenu extends DungeonEditor {
    public ScalingMenu(DungeonMenu root) {super("scaling",root,root);}
    @Override protected void render() {
        var s=root.draft.get().scaling();
        number(11,"extra-mobs",s.extraMobsPerPlayer()*100,0,1000,n->root.change(v->v.scaling=new ScalingDef(n/100,v.scaling.extraHealthPerPlayer())));
        number(15,"extra-health",s.extraHealthPerPlayer()*100,0,1000,n->root.change(v->v.scaling=new ScalingDef(v.scaling.extraMobsPerPlayer(),n/100)));
    }
}
