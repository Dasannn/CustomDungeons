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

public final class DungeonSettingsMenu extends DungeonEditor {
    public DungeonSettingsMenu(DungeonMenu root) {super("settings",root,root);}
    @Override protected void render() {
        var d=root.draft.get();
        text(10,"name",d.displayName(),128,s->root.change(v->v.name=s));
        number(11,"min",d.minPlayers(),1,1000,n->root.change(v->v.min=(int)n));
        number(12,"max",d.maxPlayers(),0,1000,n->root.change(v->v.max=(int)n));
        number(13,"countdown",d.lobbyCountdownSeconds(),0,3600,n->root.change(v->v.countdown=(int)n));
        number(14,"lives",d.lives(),1,1000,n->root.change(v->v.lives=(int)n));
        number(15,"time",d.timeLimitSeconds(),0,86400,n->root.change(v->v.time=(int)n));
        number(16,"cooldown",d.cooldownSeconds(),0,604800,n->root.change(v->v.cooldown=(int)n));
        toggle(20,"keep",d.keepInventory(),()->root.change(v->v.keep=!v.keep));
        toggle(22,"permission",d.requirePermission(),()->root.change(v->v.permission=!v.permission));
        point(29,"lobby",d.lobby(),p->root.change(v->v.lobby=p),ToolType.POINT);
        giveTool(30,ToolType.POINT);
        point(31,"exit",d.exit(),p->root.change(v->v.exit=p),ToolType.POINT);
        giveTool(32,ToolType.POINT);
    }
}
