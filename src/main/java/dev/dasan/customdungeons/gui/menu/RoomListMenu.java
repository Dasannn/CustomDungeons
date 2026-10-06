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
    private Set<String> incompleteRoomPaths=Set.of();
    public RoomListMenu(DungeonMenu root) {super("rooms",root,root);}
    @Override protected String createKey() {return "add-room";}
    @Override protected void render() {
        incompleteRoomPaths=new Validator().validate(root.draft.get(),root.services.store.mobs(),root.services.store.spawnerPresets()).stream()
                .map(ValidationError::path).filter(path->path.startsWith("rooms["))
                .map(path->path.substring(0,path.indexOf(']')+1)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        super.render();
        summary(Material.OAK_DOOR,msg("section-rooms"),msg("room-count",Placeholder.unparsed("value",Integer.toString(entries().size()))));
    }
    @Override protected List<RoomDef> entries() {return root.draft.get().rooms();}
    @Override protected Button entry(RoomDef room,int index) {
        boolean finalRoom=index==entries().size()-1;
        var unlock=finalRoom?RoomDef.OpeningMode.AUTOMATIC:room.openingMode();
        return action("room-label",complete(index)?Material.OAK_DOOR:Material.IRON_DOOR,index+1,(p,c)->{
            if(c.isShiftClick()) {
                if(c.isRightClick()) {
                    MenuListener.instance().later(()->Inputs.confirm(viewer,msg("remove-room"),()->{
                        if(root.writable()) {root.change(v->v.rooms=DungeonMenu.remove(v.rooms,index));refresh();}
                    })); return;
                }
                else if(index>0) root.change(v->{var rooms=new ArrayList<>(v.rooms);Collections.swap(rooms,index,index-1);v.rooms=rooms;});
                refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new RoomMenu(root,index,this).open();});
        },status("room-part-region",room.region()!=null),status("section-checkpoint",room.checkpoint()!=null),
                status("section-door",room.door()!=null||finalRoom),
                MenuListener.instance().messages().get(unlock!=RoomDef.OpeningMode.KEY||room.keyCarrierTemplateId()!=null?"gui.common.ready":"gui.common.missing",
                        Placeholder.component("part",msg("room-unlock-name",Placeholder.component("mode",msg(RoomMenu.unlockName(unlock)))))),
                msg("room-totals",Placeholder.unparsed("spawners",Integer.toString(room.spawners().size())),Placeholder.unparsed("mobs",Integer.toString(resolvedMobCount(room)))));
    }
    private int resolvedMobCount(RoomDef room) {
        var presets=root.services.store.spawnerPresets();
        return room.spawners().stream().flatMap(s -> (s.presetId()!=null&&!presets.containsKey(s.presetId())
                ? List.<WaveDef>of() : SpawnerPresets.waves(s,presets)).stream()).flatMap(w->w.entries().stream()).mapToInt(WaveEntry::count).sum();
    }
    private boolean complete(int index) { return !incompleteRoomPaths.contains("rooms["+index+"]"); }
    @Override protected void create() {
        String id=DungeonMenu.nextId("room_",entries().stream().map(RoomDef::id).toList());
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,new RoomDef(id,null,null,null,UnlockMode.AUTOMATIC,"*",List.of())));refresh();
    }
}
