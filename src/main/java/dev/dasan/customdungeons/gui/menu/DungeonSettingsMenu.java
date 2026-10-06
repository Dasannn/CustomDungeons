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
        text(4,"name",d.displayName(),128,s->root.change(v->v.name=s));
        header(11,"section-players",Material.BELL);
        integer(19,"min",d.minPlayers(),1,100,n->root.change(v->v.min=n));
        integer(21,"max",d.maxPlayers(),0,300,n->root.change(v->v.max=n));
        header(15,"section-lives",Material.GOLDEN_APPLE);
        integer(23,"lives",d.lives(),1,100,n->root.change(v->v.lives=n));
        toggle(25,"keep",d.keepInventory(),()->root.change(v->v.keep=!v.keep));
        header(29,"section-times",Material.DAYLIGHT_DETECTOR);
        integer(37,"countdown",d.lobbyCountdownSeconds(),5,600,n->root.change(v->v.countdown=n));
        integer(38,"time",d.timeLimitSeconds(),0,7200,n->root.change(v->v.time=n));
        integer(39,"cooldown",d.cooldownSeconds(),0,604800,n->root.change(v->v.cooldown=n));
        header(33,"section-access",Material.TRIPWIRE_HOOK);
        toggle(41,"permission",d.requirePermission(),()->root.change(v->v.permission=!v.permission));
        toggle(43,"enabled",d.enabled(),()->root.change(v->v.enabled=!v.enabled));
    }
    private void header(int slot,String key,Material material) {
        set(slot,Button.of(material,msg(key),List.of(msg(key+"-lore")),(p,c)->{}));
    }
}
