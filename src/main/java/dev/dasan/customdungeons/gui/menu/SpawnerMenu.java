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
        pointHere(20,"location",s.location(),p->root.spawner(room,spawner,v->new SpawnerDef(v.id(),p,v.radius(),v.waves(),v.presetId())));
        if(s.presetId()==null) giveTool(29,ToolType.SPAWNER);
        else add(29,"spawner-give",Material.BREEZE_ROD,()->root.services.tools.give(viewer,ToolType.SPAWNER,root.draft.get().id()));
        decimal(22,"radius",s.radius(),n->root.spawner(room,spawner,v->new SpawnerDef(v.id(),v.location(),n,v.waves(),v.presetId())));
        set(31,action(s.presetId()==null?"markers":"markers-linked",Material.SPYGLASS,"",(p,c)->{
            if(c.isRightClick()) root.services.markers.hide(root.draft.get().id());
            else {
                var rooms=root.draft.get().rooms().stream().map(r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners().stream().filter(v->v.location()!=null).toList(),r.openingMode(),r.ambience())).toList();
                var v=new DungeonMenu.Values(root.draft.get());v.rooms=rooms;root.services.markers.show(v.build());
            }
        }));
        if(s.presetId()!=null) {
            renderLinked(s); return;
        }
        var list=new WaveListMenu(root,room,spawner,this);
        for(int w=0;w<Math.min(3,s.waves().size());w++) {
            if(w==2&&s.waves().size()>3) set(42,action("all-waves",Material.ZOMBIE_HEAD,s.waves().size(),
                    (p,c)->MenuListener.instance().later(list::open)));
            else set(24+w*9,list.waveButton(s.waves().get(w),w,this,this::refresh));
        }
    }
    @Override protected void renderHeader() {
        if(root.draft.get().rooms().get(room).spawners().get(spawner).presetId()==null) {super.renderHeader();return;}
        GuiTheme.help(this,List.of(SpawnerLibraryMenu.m("linked-help-1"),SpawnerLibraryMenu.m("linked-help-2"),SpawnerLibraryMenu.m("linked-help-3")));
    }
    private void renderLinked(SpawnerDef placed) {
        var preset=root.services.store.spawnerPresets().get(placed.presetId());
        if(preset==null) {
            set(24,GuiTheme.information(Material.RED_DYE,SpawnerLibraryMenu.m("missing",SpawnerLibraryMenu.arg("id",placed.presetId())),List.of(SpawnerLibraryMenu.m("missing-lore"))));
            return;
        }
        int count=SpawnerPresets.usage(preset.id(),root.services.store.dungeons()).size();
        set(11,GuiTheme.section(SpawnerLibraryMenu.m("location-heading"),List.of()));
        set(13,GuiTheme.section(SpawnerLibraryMenu.m("radius-heading"),List.of()));
        set(20,Button.of(Material.LIME_DYE,msg("location-here"),List.of(SpawnerLibraryMenu.position(placed.location(),false),Component.empty(),SpawnerLibraryMenu.m("point-here-lore")),(p,c)->{
            if(root.writable()) {root.spawner(room,spawner,v->new SpawnerDef(v.id(),position(p),v.radius(),v.waves(),v.presetId()));refresh();}
        }));
        set(22,NumericInputs.decorate(Button.of(Material.TARGET,msg("radius",SpawnerLibraryMenu.arg("value",Inputs.formatNumber(placed.radius(),1))),
                List.of(placed.radius()==preset.radius()?SpawnerLibraryMenu.m("radius-from-preset",SpawnerLibraryMenu.arg("value",Inputs.formatNumber(placed.radius(),1))):msg("value",SpawnerLibraryMenu.arg("value",Inputs.formatNumber(placed.radius(),1))),Component.empty(),SpawnerLibraryMenu.m("radius-lore")),
                (p,c)->MenuListener.instance().later(()->{
                    if(root.writable()) NumericInputs.edit(p,msg("radius",SpawnerLibraryMenu.arg("value",Inputs.formatNumber(placed.radius(),1))),NumericRanges.SPAWNER_RADIUS,placed.radius(),n->{
                        if(root.writable()) {root.spawner(room,spawner,v->new SpawnerDef(v.id(),v.location(),n,v.waves(),v.presetId()));refresh();}
                    });
                })),NumericRanges.SPAWNER_RADIUS));
        var point=placed.location();
        Component position=point==null?pointLore(null):SpawnerLibraryMenu.m("point-radius",SpawnerLibraryMenu.arg("world",point.world()),SpawnerLibraryMenu.arg("x",SpawnerLibraryMenu.coordinate(point.x())),SpawnerLibraryMenu.arg("y",SpawnerLibraryMenu.coordinate(point.y())),SpawnerLibraryMenu.arg("z",SpawnerLibraryMenu.coordinate(point.z())),SpawnerLibraryMenu.arg("radius",Inputs.formatNumber(placed.radius(),1)));
        summary(Material.SPAWNER,SpawnerLibraryMenu.m("linked-summary",SpawnerLibraryMenu.arg("number",spawner+1),Placeholder.component("name",SpawnerLibraryMenu.label(preset))),
                SpawnerLibraryMenu.m("linked-origin",Placeholder.component("name",SpawnerLibraryMenu.label(preset)),SpawnerLibraryMenu.arg("rooms",count)),position);
        set(15,GuiTheme.section(SpawnerLibraryMenu.m("linked-waves"),List.of(SpawnerLibraryMenu.m("origin-heading",Placeholder.component("name",SpawnerLibraryMenu.label(preset))))));
        for(int i=0;i<Math.min(2,preset.waves().size());i++) set(24+i*9,GuiTheme.information(Material.ZOMBIE_HEAD,msg("wave-label",Placeholder.unparsed("value",Integer.toString(i+1))),
                List.of(SpawnerLibraryMenu.m("wave-readonly",Placeholder.component("mode",msg("mode-"+preset.waves().get(i).mode().name().toLowerCase(Locale.ROOT))),Placeholder.component("mobs",SpawnerLibraryMenu.mobs(List.of(preset.waves().get(i)),root.services))))));
        if(preset.waves().size()>2) set(41,Button.of(Material.ZOMBIE_HEAD,msg("all-waves",Placeholder.unparsed("value",Integer.toString(preset.waves().size()))),List.of(SpawnerLibraryMenu.m("readonly-lore")),
                (p,c)->MenuListener.instance().later(()->new ReadOnlyWaves().open())));
        set(42,Button.of(Material.WRITABLE_BOOK,SpawnerLibraryMenu.m("edit"),List.of(SpawnerLibraryMenu.m("edit-affects",SpawnerLibraryMenu.arg("rooms",count)),Component.empty(),SpawnerLibraryMenu.m("edit-lore")),
                (p,c)->MenuListener.instance().later(()->{if(root.writable()) new SpawnerPresetMenu(root.services,preset,this,null).open();})));
        set(43,Button.of(Material.SHEARS,SpawnerLibraryMenu.m("make-local"),List.of(SpawnerLibraryMenu.m("make-local-detail"),Component.empty(),SpawnerLibraryMenu.m("make-local-lore")),
                (p,c)->MenuListener.instance().later(()->{
                    if(!root.writable()) return;
                    Inputs.confirm(p,SpawnerLibraryMenu.m("make-local-confirm"),()->{
                        if(!root.writable()) return;
                        var current=root.draft.get().rooms().get(room).spawners().get(spawner);
                        if(current.presetId()==null) return;
                        if(!root.services.store.spawnerPresets().containsKey(current.presetId())) {tell("conflict");return;}
                        root.spawner(room,spawner,v->SpawnerPresets.makeLocal(v,root.services.store.spawnerPresets()));open();
                    });
                })));
    }
    private final class ReadOnlyWaves extends DungeonPage<WaveDef> {
        ReadOnlyWaves() {super("waves",SpawnerMenu.this.root,SpawnerMenu.this);}
        @Override protected List<WaveDef> entries() {
            var s=root.draft.get().rooms().get(room).spawners().get(spawner);
            var p=root.services.store.spawnerPresets().get(s.presetId());return p==null?List.of():p.waves();
        }
        @Override protected Button entry(WaveDef w,int i) {return GuiTheme.information(Material.ZOMBIE_HEAD,msg("wave-label",Placeholder.unparsed("value",Integer.toString(i+1))),List.of(waveLine(w,i)));}
        @Override protected boolean canCreate() {return false;}
        @Override protected void create() {}
        @Override protected Runnable onSave() {return null;}
    }
    @Override protected void renderFooter() {
        set(45,SpawnerLibraryMenu.back(parent()::open,true));set(49,SpawnerLibraryMenu.save(root::saveDraft,root.dirty()));
        if(root.draft.get().rooms().get(room).spawners().get(spawner).presetId()!=null) return;
        var list=new WaveListMenu(root,room,spawner,this);
        add(getInventory().getSize()-9+2,"add-wave",Material.LIME_DYE,()->{list.create();refresh();});
    }
}
