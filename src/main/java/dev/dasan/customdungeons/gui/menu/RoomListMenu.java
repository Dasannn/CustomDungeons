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
    @Override protected List<RoomDef> entries() {return root.draft.get().rooms();}
    @Override protected Button entry(RoomDef room,int index) {
        return action("room",Material.BRICKS,room.id(),(p,c)->{
            if(c.isShiftClick()) {
                if(c.isRightClick()) root.change(v->v.rooms=DungeonMenu.remove(v.rooms,index));
                else if(index>0) root.change(v->{var rooms=new ArrayList<>(v.rooms);Collections.swap(rooms,index,index-1);v.rooms=rooms;});
                refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new RoomMenu(root,index,this).open();});
        });
    }
    @Override protected void create() {
        String id=DungeonMenu.nextId("room_",entries().stream().map(RoomDef::id).toList());
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,new RoomDef(id,null,null,null,UnlockMode.AUTOMATIC,null,List.of())));refresh();
    }
}
