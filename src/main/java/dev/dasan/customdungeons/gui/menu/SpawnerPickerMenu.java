package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import static dev.dasan.customdungeons.gui.menu.SpawnerLibraryMenu.*;

/** The two paginated groups preserve dungeon priority and never hide the empty placement action. */
public final class SpawnerPickerMenu extends DungeonEditor {
    private final int room;
    private final Point point;
    private int page;
    public SpawnerPickerMenu(DungeonMenu root,int room,Menu parent,Point point) {
        super("preset-picker",root,parent);this.room=room;this.point=point;
    }
    @Override protected int preferredRows() {return 4;}
    @Override protected Runnable onSave() {return null;}
    private List<String> preferred() {return root.draft.get().spawnerPresets();}
    private List<String> library() {return root.services.store.spawnerPresets().keySet().stream().filter(id->!preferred().contains(id)).sorted().toList();}
    private int pages() {return Math.max(PagedMenu.pageCount(preferred().size(),6),PagedMenu.pageCount(library().size(),5));}
    @Override protected boolean hasPreviousPage() {return page>0;}
    @Override protected boolean hasNextPage() {return page+1<pages();}
    @Override protected void previousPage() {if(hasPreviousPage()){page--;refresh();}}
    @Override protected void nextPage() {if(hasNextPage()){page++;refresh();}}
    @Override protected void renderHeader() {GuiTheme.help(this,List.of(m("picker-help-1"),m("picker-help-2")));}
    @Override protected void render() {
        page=Math.clamp(page,0,pages()-1);
        summary(Material.BREEZE_ROD,m("picker-summary",arg("room",room+1),arg("world",point.world()),arg("x",coordinate(point.x())),arg("y",coordinate(point.y())),arg("z",coordinate(point.z()))),m("picker-summary-lore"));
        set(10,GuiTheme.section(m("preferred"),List.of()));set(19,GuiTheme.section(m("library-group"),List.of()));
        for(int i=page*6;i<Math.min(page*6+6,preferred().size());i++) set(11+i%6,entry(preferred().get(i),true));
        for(int i=page*5;i<Math.min(page*5+5,library().size());i++) set(20+i%5,entry(library().get(i),false));
        set(25,Button.of(Material.LIME_DYE,m("empty-spawner"),List.of(Component.empty(),m("empty-spawner-lore")),(p,c)->MenuListener.instance().later(()->place(null))));
    }
    @Override protected void renderFooter() {if(parent()!=null) set(27,back(parent()::open,true));}
    private Button entry(String id,boolean preferred) {
        var preset=root.services.store.spawnerPresets().get(id);
        if(preset==null) return GuiTheme.information(Material.RED_DYE,m("missing",arg("id",id)),List.of(m("missing-lore")));
        var lore=new ArrayList<Component>();lore.add(mobs(preset.waves(),root.services));lore.add(Component.empty());lore.add(m(preferred?"place-lore":"place-library-lore"));
        return Button.of(Material.SPAWNER,preferred?m("preferred-name",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("name",label(preset))):label(preset),lore,
                (p,c)->MenuListener.instance().later(()->place(id)));
    }
    private void place(String presetId) {
        if(!root.writable()) return;
        var preset=presetId==null?null:root.services.store.spawnerPresets().get(presetId);
        if(presetId!=null&&preset==null) {tell("conflict");refresh();return;}
        String id=DungeonMenu.nextId("spawner_",root.draft.get().rooms().stream().flatMap(r->r.spawners().stream()).map(SpawnerDef::id).toList());
        int index=root.draft.get().rooms().get(room).spawners().size();
        root.change(v->{
            var r=v.rooms.get(room);
            var s=new SpawnerDef(id,point,preset==null?3:preset.radius(),List.of(),presetId);
            v.rooms=DungeonMenu.replace(v.rooms,room,new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.append(r.spawners(),s),r.openingMode()));
            if(presetId!=null&&!v.spawnerPresets.contains(presetId)) v.spawnerPresets=DungeonMenu.append(v.spawnerPresets,presetId);
        });
        new SpawnerMenu(root,room,index,parent()).open();
    }
}
