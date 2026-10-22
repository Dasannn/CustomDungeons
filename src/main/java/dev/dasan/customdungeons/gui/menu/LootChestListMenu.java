package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import static dev.dasan.customdungeons.gui.menu.LootEditor.*;

public final class LootChestListMenu extends DungeonPage<LootChestDef> {
    private final String roomId;
    public LootChestListMenu(DungeonMenu root,String room,Menu parent) {super("loot",m("list-title",arg("room",roomNumber(root,room))),root,parent);roomId=room;}
    @Override protected Component title() {return m("list-title",arg("room",roomNumber(root,roomId)));}
    @Override protected Material borderMaterial() {return Material.YELLOW_STAINED_GLASS_PANE;}
    @Override protected Runnable onSave() {return null;}
    @Override protected List<LootChestDef> entries() {return root.draft.get().rooms().get(roomNumber(root,roomId)-1).lootChests();}
    @Override protected void render() {super.render();summary(Material.CHEST,m("list-summary",arg("room",roomNumber(root,roomId)),arg("id",roomId),arg("count",entries().size())),m("shared"));}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->m("help-list-"+i)).toList());tintName(8,NamedTextColor.YELLOW);}
    @Override protected void renderFooter() {
        set(getInventory().getSize()-5,Button.of(Material.LIME_DYE,m("add"),List.of(m("associated",arg("id",roomId)),Component.empty(),m("add-lore")),(p,c)->MenuListener.instance().later(()->{if(root.writable())create();})));
        tintNavigation(NamedTextColor.YELLOW);
    }
    @Override protected Button entry(LootChestDef chest,int index) {
        return Button.of(Material.CHEST,m("edit",arg("id",chest.id())),List.of(chest.location()==null?m("missing"):m("list-position",Placeholder.component("position",LootEditor.position(chest.location()))),m(chest.entries().size()==1?"list-trigger-one":"list-trigger",Placeholder.component("trigger",trigger(chest.trigger())),arg("count",chest.entries().size())),Component.empty(),m("edit-lore"),m("preview-lore"),m("delete-lore")),(p,c)->MenuListener.instance().later(()->{
            if(!root.writable())return;
            if(c.isShiftClick()&&c.isRightClick()) Inputs.confirm(p,m("delete-confirm"),()->{
                if(root.writable())root.editLootPlacement(roomId,chest.id(),null,true,this::refresh);
            });
            else if(c.isRightClick()) LootChestMenu.preview(root,chest);
            else new LootChestMenu(root,roomId,chest.id(),this).open();
        }));
    }
    @Override protected void create() {
        if(!root.writable())return;
        Inputs.text(viewer,m("id"),"",32,id->{
            if(!root.writable())return;
            if(!DungeonMenu.validId(id)) {root.tell("invalid-id");return;}
            if(root.draft.get().rooms().stream().flatMap(r->r.lootChests().stream()).anyMatch(c->c.id().equals(id))) {root.tell("loot-duplicate");return;}
            root.room(roomNumber(root,roomId)-1,r->DungeonMenu.withLoot(r,DungeonMenu.append(r.lootChests(),new LootChestDef(id,null,LootChestDef.Trigger.ROOM_CLEAR,List.of()))));
            new LootChestMenu(root,roomId,id,this).open();
        });
    }
}
