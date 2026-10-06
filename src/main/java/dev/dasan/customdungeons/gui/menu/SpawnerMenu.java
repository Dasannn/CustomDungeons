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
        summary(Material.SPAWNER,msg("spawner-label",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value",Integer.toString(spawner+1))),
                pointLore(s.location()),msg("spawner-summary",Placeholder.unparsed("radius",Inputs.formatNumber(s.radius(),1)),Placeholder.unparsed("waves",Integer.toString(s.waves().size()))));
        section(11,"section-location",Material.CYAN_STAINED_GLASS_PANE,pointLore(s.location()));
        section(13,"section-radius",Material.CYAN_STAINED_GLASS_PANE);
        section(15,"section-waves",Material.CYAN_STAINED_GLASS_PANE);
        pointHere(20,"location",s.location(),p->root.spawner(room,spawner,v->new SpawnerDef(v.id(),p,v.radius(),v.waves())));
        giveTool(29,ToolType.SPAWNER);
        decimal(22,"radius",s.radius(),1,64,1,n->root.spawner(room,spawner,v->new SpawnerDef(v.id(),v.location(),n,v.waves())));
        set(31,action("markers",Material.SPYGLASS,"",(p,c)->{
            if(c.isRightClick()) root.services.markers.hide(root.draft.get().id());
            else {
                var rooms=root.draft.get().rooms().stream().map(r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners().stream().filter(v->v.location()!=null).toList())).toList();
                var v=new DungeonMenu.Values(root.draft.get());v.rooms=rooms;root.services.markers.show(v.build());
            }
        }));
        var list=new WaveListMenu(root,room,spawner,this);
        for(int w=0;w<Math.min(3,s.waves().size());w++) {
            if(w==2&&s.waves().size()>3) set(42,action("all-waves",Material.ZOMBIE_HEAD,s.waves().size(),
                    (p,c)->MenuListener.instance().later(list::open)));
            else set(24+w*9,list.waveButton(s.waves().get(w),w,this,this::refresh));
        }
    }
    @Override protected void renderFooter() {
        var list=new WaveListMenu(root,room,spawner,this);
        add(getInventory().getSize()-9+2,"add-wave",Material.LIME_DYE,()->{list.create();refresh();});
    }
}
