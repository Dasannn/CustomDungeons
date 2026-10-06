package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import static dev.dasan.customdungeons.gui.menu.SpawnerLibraryMenu.*;

public final class DungeonSpawnerMenu extends DungeonPage<String> {
    public DungeonSpawnerMenu(DungeonMenu root) {super("dungeon-presets",root,root);}
    @Override protected int preferredRows() {return Math.max(4,super.preferredRows());}
    @Override protected List<String> entries() {return root.draft.get().spawnerPresets();}
    @Override protected void render() {
        super.render();
        if(entries().size()==2) {
            set(12,GuiTheme.information(Material.GRAY_STAINED_GLASS_PANE,Component.empty(),List.of()));
            set(14,GuiTheme.information(Material.GRAY_STAINED_GLASS_PANE,Component.empty(),List.of()));
            set(11,entry(entries().get(0),0));set(13,entry(entries().get(1),1));
        }
        summary(Material.SPAWNER,m("dungeon-summary",arg("value",entries().size())),m("dungeon-summary-lore"));
        if(entries().isEmpty()) set(13,GuiTheme.information(Material.GRAY_DYE,m("empty"),List.of(m("empty-lore"))));
    }
    @Override protected void renderHeader() {GuiTheme.help(this,List.of(m("dungeon-help-1"),m("dungeon-help-2"),m("dungeon-help-3")));}
    @Override protected Runnable onSave() {return null;}
    @Override protected Button entry(String id,int index) {
        var preset=root.services.store.spawnerPresets().get(id);
        if(preset==null) return Button.of(Material.RED_DYE,m("missing",arg("id",id)),List.of(m("missing-remove")),(p,c)->{
            if(root.writable()&&c.isRightClick()) {root.change(v->v.spawnerPresets=DungeonMenu.remove(v.spawnerPresets,index));refresh();}
        });
        var placed=root.draft.get().rooms().stream().flatMap(r -> r.spawners().stream()).filter(s -> id.equals(s.presetId())).count();
        var rooms=root.draft.get().rooms().stream().filter(r->r.spawners().stream().anyMatch(s->id.equals(s.presetId()))).map(RoomDef::id).toList();
        var lore=new ArrayList<Component>();lore.add(m("short-summary",arg("radius",Inputs.formatNumber(preset.radius(),1)),arg("waves",preset.waves().size())));
        lore.add(m(placed==0?"placed-empty":"placed",arg("value",placed),arg("rooms",String.join(", ",rooms))));lore.add(Component.empty());lore.add(m("dungeon-entry-lore"));
        return Button.of(Material.SPAWNER,label(preset),lore,(p,c)->MenuListener.instance().later(() -> {
            if(!root.writable()) return;
            if(c.isRightClick()) {root.change(v->v.spawnerPresets=DungeonMenu.remove(v.spawnerPresets,index));refresh();}
            else new SpawnerPresetMenu(root.services,preset,this,null).open();
        }));
    }
    private void select(String id) {if(root.writable()&&!entries().contains(id)) root.change(v -> v.spawnerPresets=DungeonMenu.append(v.spawnerPresets,id));}
    @Override protected void create() {new SpawnerLibraryMenu(root.services,this,this::select).open();}
    @Override protected void renderFooter() {
        int base=getInventory().getSize()-9;
        set(base,back(parent()::open,true));
        set(base+3,Button.of(Material.BOOKSHELF,m("choose-library"),List.of(Component.empty(),m("choose-library-lore")),(p,c)->MenuListener.instance().later(()->{if(root.writable()) create();})));
        set(base+5,Button.of(Material.LIME_DYE,m("create-new"),List.of(Component.empty(),m("create-new-lore")),(p,c)->MenuListener.instance().later(()->{
            if(root.writable()) SpawnerLibraryMenu.create(root.services,this,this::select);
        })));
        // Keep pagination separate from these two fixed actions on longer lists.
        if(hasPreviousPage()) set(18,Button.of(Material.SPECTRAL_ARROW,m("previous"),List.of(m("previous-lore")),(p,c)->previousPage()));
        if(hasNextPage()) set(26,Button.of(Material.SPECTRAL_ARROW,m("next"),List.of(m("next-lore")),(p,c)->nextPage()));
    }
}
