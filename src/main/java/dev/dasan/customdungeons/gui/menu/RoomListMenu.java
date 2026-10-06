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
    @Override protected int firstContentRow() {return 2;}
    @Override protected void render() {
        super.render();
        section(4,"section-rooms",Material.OAK_DOOR,msg("room-count",Placeholder.unparsed("value",Integer.toString(entries().size()))));
        add(13,"add-room",Material.EMERALD,this::create);
    }
    @Override protected List<RoomDef> entries() {return root.draft.get().rooms();}
    @Override protected Button entry(RoomDef room,int index) {
        return action("room",Material.OAK_DOOR,room.id(),(p,c)->{
            if(c.isShiftClick()) {
                if(c.isRightClick()) root.change(v->v.rooms=DungeonMenu.remove(v.rooms,index));
                else if(index>0) root.change(v->{var rooms=new ArrayList<>(v.rooms);Collections.swap(rooms,index,index-1);v.rooms=rooms;});
                refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new RoomMenu(root,index,this).open();});
        },regionLore(room.region()),regionSizeLore(room.region()),msg("checkpoint-current",Placeholder.component("point",pointLore(room.checkpoint()))),RoomMenu.unlockLore(room),
                msg("room-summary",Placeholder.unparsed("id",room.id()),Placeholder.unparsed("position",Integer.toString(index+1)),
                        Placeholder.unparsed("spawners",Integer.toString(room.spawners().size()))),
                msg("door-current",Placeholder.component("region",regionLore(room.door()))));
    }
    @Override protected void create() {
        String id=DungeonMenu.nextId("room_",entries().stream().map(RoomDef::id).toList());
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,new RoomDef(id,null,null,null,UnlockMode.AUTOMATIC,"*",List.of())));refresh();
    }
}
