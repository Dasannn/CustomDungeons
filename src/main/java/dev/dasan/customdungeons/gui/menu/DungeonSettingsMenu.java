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
        summary(Material.COMPARATOR,msg("settings"),status("section-players",d.minPlayers()>0),
                status("section-lives",d.lives()>0),msg("settings-name",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(d.displayName()))));
        section(10,"section-players",Material.ORANGE_STAINED_GLASS_PANE);
        section(12,"section-lives",Material.ORANGE_STAINED_GLASS_PANE);
        section(14,"section-times",Material.ORANGE_STAINED_GLASS_PANE);
        section(16,"section-access",Material.ORANGE_STAINED_GLASS_PANE);
        integer(19,"min",d.minPlayers(),1,100,n->root.change(v->v.min=n));
        integer(28,"max",d.maxPlayers(),0,300,n->root.change(v->v.max=n));
        text(37,"name",d.displayName(),128,n->root.change(v->v.name=n));
        integer(21,"lives",d.lives(),1,100,n->root.change(v->v.lives=n));
        toggle(30,"keep",d.keepInventory(),()->root.change(v->v.keep=!v.keep));
        integer(23,"countdown",d.lobbyCountdownSeconds(),5,600,n->root.change(v->v.countdown=n));
        integer(32,"time",d.timeLimitSeconds(),0,7200,n->root.change(v->v.time=n));
        integer(41,"cooldown",d.cooldownSeconds(),0,604800,n->root.change(v->v.cooldown=n));
        toggle(25,"permission",d.requirePermission(),()->root.change(v->v.permission=!v.permission));
        toggle(34,"enabled",d.enabled(),()->root.change(v->v.enabled=!v.enabled));
    }
}
