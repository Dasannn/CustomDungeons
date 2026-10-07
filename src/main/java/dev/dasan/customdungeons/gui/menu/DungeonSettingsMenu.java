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
        if(d.startMode()==StartMode.PLATES) set(19,GuiTheme.information(Material.GRAY_DYE,
                msg("min",Placeholder.unparsed("value",Integer.toString(d.minPlayers()))),
                List.of(msg("min-plates",Placeholder.unparsed("value",Integer.toString(d.plates().size()))))));
        else integer(19,"min",d.minPlayers(),n->root.change(v->v.min=n));
        integer(28,"max",d.maxPlayers(),n->root.change(v->v.max=n));
        text(37,"name",d.displayName(),128,n->root.change(v->v.name=n));
        integer(21,"lives",d.lives(),n->root.change(v->v.lives=n));
        toggle(30,"keep",d.keepInventory(),()->root.change(v->v.keep=!v.keep));
        set(39,Button.of(d.disconnectMode()==DisconnectMode.DIE_AND_DROP?Material.SKELETON_SKULL:Material.OAK_DOOR,
                msg("disconnect",Placeholder.component("value",msg("disconnect-"+d.disconnectMode().name().toLowerCase(Locale.ROOT)))),
                List.of(msg("disconnect-lore"),msg("disconnect-death-lore"),msg("disconnect-respawn-lore"),msg("disconnect-exit-lore"),msg("disconnect-shutdown-lore")),
                (p,c)->{if(root.writable()){root.change(v->v.disconnectMode=v.disconnectMode==DisconnectMode.DIE_AND_DROP
                        ?DisconnectMode.RETURN_TO_EXIT:DisconnectMode.DIE_AND_DROP);refresh();}}));
        integer(23,"countdown",d.lobbyCountdownSeconds(),n->root.change(v->v.countdown=n));
        integer(32,"time",d.timeLimitSeconds(),n->root.change(v->v.time=n));
        integer(41,"cooldown",d.cooldownSeconds(),n->root.change(v->v.cooldown=n));
        toggle(25,"permission",d.requirePermission(),()->root.change(v->v.permission=!v.permission));
        toggle(34,"enabled",d.enabled(),()->root.change(v->v.enabled=!v.enabled));
    }
}
