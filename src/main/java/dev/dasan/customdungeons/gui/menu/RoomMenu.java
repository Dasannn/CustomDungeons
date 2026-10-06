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

public final class RoomMenu extends DungeonPage<SpawnerDef> {
    private final int room;
    public RoomMenu(DungeonMenu root,int room,Menu parent) {super("room",root,parent);this.room=room;}
    private RoomDef value() {return root.draft.get().rooms().get(room);}
    @Override protected List<SpawnerDef> entries() {return value().spawners();}
    @Override protected void render() {
        super.render();
        // Room controls occupy the header; spawners keep all 28 paginated content slots.
        region(0,"region",value().region(),r->root.room(room,v->new RoomDef(v.id(),r,v.checkpoint(),v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners())),ToolType.REGION);
        giveTool(1,ToolType.REGION);
        region(2,"door",value().door(),r->root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),r,v.unlock(),v.keyCarrierTemplateId(),v.spawners())),ToolType.DOOR);
        giveTool(3,ToolType.DOOR);
        point(5,"checkpoint",value().checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners())),ToolType.POINT);
        giveTool(6,ToolType.POINT);
        toggle(7,"key",value().unlock()==UnlockMode.KEY,()->root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock()==UnlockMode.KEY?UnlockMode.AUTOMATIC:UnlockMode.KEY,v.keyCarrierTemplateId(),v.spawners())));
        add(8,"carrier",Material.TRIPWIRE_HOOK,()->new TemplatePickerMenu(root,this,id->root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),id,v.spawners()))).open());
        add(44,"clear-door",Material.BARRIER,()->{root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),null,v.unlock(),v.keyCarrierTemplateId(),v.spawners()));refresh();});
    }
    @Override protected Button entry(SpawnerDef spawner,int index) {
        return action("spawner",Material.SPAWNER,spawner.id(),(p,c)->{
            if(c.isShiftClick()&&c.isRightClick()) {
                root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.remove(r.spawners(),index)));refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new SpawnerMenu(root,room,index,this).open();});
        });
    }
    @Override protected void create() {
        String id=DungeonMenu.nextId("spawner_",root.draft.get().rooms().stream().flatMap(r->r.spawners().stream()).map(SpawnerDef::id).toList());
        root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.append(r.spawners(),new SpawnerDef(id,null,3,List.of()))));refresh();
    }
}
