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

public final class SpawnerMenu extends DungeonEditor {
    private final int room,spawner;
    public SpawnerMenu(DungeonMenu root,int room,int spawner,Menu parent) {super("spawner",root,parent);this.room=room;this.spawner=spawner;}
    @Override protected void render() {
        var s=root.draft.get().rooms().get(room).spawners().get(spawner);
        section(4,"section-spawner",Material.SPAWNER,msg("spawner-summary",Placeholder.unparsed("radius",Inputs.formatNumber(s.radius(),1)),Placeholder.unparsed("waves",Integer.toString(s.waves().size()))));
        section(11,"section-location",Material.LODESTONE,pointLore(s.location()));
        section(13,"section-radius",Material.ENDER_PEARL,msg("value",Placeholder.unparsed("value",Inputs.formatNumber(s.radius(),1))));
        section(15,"section-waves",Material.ZOMBIE_HEAD,msg("wave-count",Placeholder.unparsed("value",Integer.toString(s.waves().size()))));
        section(31,"section-markers",Material.SPAWNER);
        point(20,"location",s.location(),p->root.spawner(room,spawner,v->new SpawnerDef(v.id(),p,v.radius(),v.waves())),ToolType.SPAWNER);
        giveTool(38,ToolType.SPAWNER);
        integer(22,"radius",(int)Math.round(s.radius()),1,64,n->root.spawner(room,spawner,v->new SpawnerDef(v.id(),v.location(),n,v.waves())));
        add(24,"waves",Material.ZOMBIE_HEAD,()->new WaveListMenu(root,room,spawner,this).open());
        add(40,"markers",Material.SPAWNER,()->{
            // T08 markers require locations; unfinished spawners remain solely in the draft.
            var rooms=root.draft.get().rooms().stream().map(r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners().stream().filter(v->v.location()!=null).toList())).toList();
            var v=new DungeonMenu.Values(root.draft.get());v.rooms=rooms;root.services.markers.show(v.build());
        });
        add(42,"hide-markers",Material.BARRIER,()->root.services.markers.hide(root.draft.get().id()));
    }
}
