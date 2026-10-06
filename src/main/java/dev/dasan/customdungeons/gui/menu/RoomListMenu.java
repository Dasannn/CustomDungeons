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

public final class RoomListMenu extends DungeonPage<RoomDef> {
    public RoomListMenu(DungeonMenu root) {super("rooms",root,root);}
    @Override protected String createKey() {return "add-room";}
    @Override protected void render() {
        super.render();
        summary(Material.OAK_DOOR,msg("section-rooms"),msg("room-count",Placeholder.unparsed("value",Integer.toString(entries().size()))));
    }
    @Override protected List<RoomDef> entries() {return root.draft.get().rooms();}
    @Override protected Button entry(RoomDef room,int index) {
        return action("room-label",complete(room,index)?Material.OAK_DOOR:Material.IRON_DOOR,index+1,(p,c)->{
            if(c.isShiftClick()) {
                if(c.isRightClick()) {
                    MenuListener.instance().later(()->Inputs.confirm(viewer,msg("remove-room"),()->{
                        if(root.writable()) {root.change(v->v.rooms=DungeonMenu.remove(v.rooms,index));refresh();}
                    })); return;
                }
                else if(index>0) root.change(v->{var rooms=new ArrayList<>(v.rooms);Collections.swap(rooms,index,index-1);v.rooms=rooms;});
                refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new RoomMenu(root,index,this).open();});
        },status("region",room.region()!=null),status("checkpoint",room.checkpoint()!=null),
                status("door",room.door()!=null||index==entries().size()-1),status("section-unlock",room.unlock()==UnlockMode.AUTOMATIC||room.keyCarrierTemplateId()!=null),
                msg("total-mobs",Placeholder.unparsed("value",Integer.toString(totalMobs(room)))),regionLore(room.region()),regionSizeLore(room.region()),msg("checkpoint-current",Placeholder.component("point",pointLore(room.checkpoint()))),RoomMenu.unlockLore(room),
                msg("room-summary",Placeholder.unparsed("id",room.id()),Placeholder.unparsed("position",Integer.toString(index+1)),
                        Placeholder.unparsed("spawners",Integer.toString(room.spawners().size()))),
                msg("door-current",Placeholder.component("region",regionLore(room.door()))));
    }
    private boolean complete(RoomDef room,int index) {
        return new Validator().validate(root.draft.get(),root.services.store.mobs()).stream()
                .noneMatch(error->error.path().startsWith("rooms["+index+"]"));
    }
    @Override protected void create() {
        String id=DungeonMenu.nextId("room_",entries().stream().map(RoomDef::id).toList());
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,new RoomDef(id,null,null,null,UnlockMode.AUTOMATIC,"*",List.of())));refresh();
    }
}
