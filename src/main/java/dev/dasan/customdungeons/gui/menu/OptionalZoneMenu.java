package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class OptionalZoneMenu extends DungeonEditor {
    private final EditOrigin origin;
    public OptionalZoneMenu(DungeonMenu root,String id,Menu parent) {super("zone",msg("zone-title",Placeholder.unparsed("value",id)),root,parent);origin=EditOrigin.zone(id);}
    @Override protected int preferredRows() {return 5;}
    private OptionalZoneDef value() {return root.draft.get().optionalZones().get(origin.index(root.draft.get()));}
    static Component error(ValidationError error) {
        return Validator.describe(error,MenuListener.instance().messages());
    }
    @Override protected void render() {
        var z=value();
        summary(Material.MAP,msg("zone-title",Placeholder.unparsed("value",z.id())),msg("zone-overview",Placeholder.component("region",msg(z.region()!=null?"zone-region-ready":"zone-region-missing")),Placeholder.unparsed("spawners",Integer.toString(z.spawners().size())),Placeholder.unparsed("mobs",Integer.toString(mobs(root,z)))),context());
        set(12,GuiTheme.section(z.region()!=null,status("zone-section-region",z.region()!=null),List.of(regionPosition(z.region()))));
        set(14,GuiTheme.section(!z.spawners().isEmpty(),status("zone-section-spawners",!z.spawners().isEmpty()),List.of(totals(root,z))));
        add(21,"zone-give-region",toolIcon(ToolType.REGION),()->root.services.tools.give(viewer,ToolType.REGION,root.draft.get().id(),origin),context());
        set(30,action("zone-use-region",Material.LIME_DYE,"",(p,c)->{
            var selected=root.services.tools.origin(p.getUniqueId());
            if(selected.isPresent()&&!selected.get().equals(origin)) {tell("zone-selection-context");return;}
            var selection=root.services.tools.selection(p.getUniqueId()).filter(Selection::complete).orElse(null);
            if(selection==null) {tell("no-selection");return;}
            root.zone(z.id(),old->new OptionalZoneDef(old.id(),selection.toRegion(),old.spawners()));refresh();
        },root.services.tools.selection(viewer.getUniqueId()).filter(Selection::complete).map(selection->msg("zone-selected-region",Placeholder.component("region",regionPosition(selection.toRegion())))).orElseGet(()->selectionLore(root.services.tools.selection(viewer.getUniqueId()).orElse(null)))));
        set(23,action("zone-open-spawners",Material.SPAWNER,z.spawners().size(),(p,c)->MenuListener.instance().later(()->{if(root.writable())new ZoneSpawnerList(root,origin,this).open();}),Component.text(String.join(" · ",z.spawners().stream().map(SpawnerDef::id).toList()))));
        add(32,"zone-add-spawner",Material.LIME_DYE,()->new SpawnerPickerMenu(root,origin,this,position(viewer)).open(),msg("zone-simple-context",Placeholder.unparsed("id",origin.id())));
    }
    static List<WaveDef> waves(DungeonMenu root,SpawnerDef s) {
        var preset=s.presetId()==null?null:root.services.store.spawnerPresets().get(s.presetId());return s.presetId()==null?s.waves():preset==null?List.of():preset.waves();
    }
    static int mobs(DungeonMenu root,OptionalZoneDef zone) {return zone.spawners().stream().flatMap(s->waves(root,s).stream()).flatMap(w->w.entries().stream()).mapToInt(WaveEntry::count).sum();}
    static int waves(DungeonMenu root,OptionalZoneDef zone) {return zone.spawners().stream().mapToInt(s->waves(root,s).size()).sum();}
    static Component totals(DungeonMenu root,OptionalZoneDef zone) {return msg("zone-totals",Placeholder.unparsed("spawners",Integer.toString(zone.spawners().size())),Placeholder.unparsed("waves",Integer.toString(waves(root,zone))),Placeholder.unparsed("mobs",Integer.toString(mobs(root,zone))));}
    static Component regionPosition(Region r) {return r==null?msg("zone-region-missing"):msg("zone-region-position",Placeholder.unparsed("world",r.world()),Placeholder.unparsed("min",r.min().x()+", "+r.min().y()+", "+r.min().z()),Placeholder.unparsed("max",r.max().x()+", "+r.max().y()+", "+r.max().z()));}
    private Component context() {return msg("zone-context",Placeholder.unparsed("value",origin.id()));}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->msg("help-zone-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.GREEN);}
    @Override protected void renderFooter() {add(38,"view-zone",Material.SPYGLASS,()->preview(root,value()),msg("zone-preview-context",Placeholder.unparsed("id",origin.id())));tintNavigation(net.kyori.adventure.text.format.NamedTextColor.GREEN);}
    static void preview(DungeonMenu root,OptionalZoneDef zone) {
        if(!root.writable())return;
        var renderer=root.services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(renderer!=null) {var v=new DungeonMenu.Values(root.draft.get());v.rooms=List.of();v.optionalZones=List.of(zone);v.area=null;renderer.showDungeon(root.viewerPlayer(),v.build(),15);}
    }
}

final class ZoneSpawnerList extends DungeonPage<SpawnerDef> {
    private final EditOrigin origin;
    @Override protected Component title() {return msg("zone-spawners",Placeholder.unparsed("value",origin.id()));}
    ZoneSpawnerList(DungeonMenu root,EditOrigin origin,Menu parent) {super("room-spawners",root,parent);this.origin=origin;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->msg("help-zone-spawners-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.AQUA);}
    @Override protected void renderFooter() {
        set(getInventory().getSize()-5,action("zone-add-spawner",Material.LIME_DYE,"",(p,c)->MenuListener.instance().later(()->{if(root.writable())create();}),msg("zone-context",Placeholder.unparsed("value",origin.id()))));
        tintName(getInventory().getSize()-5,net.kyori.adventure.text.format.NamedTextColor.AQUA);tintNavigation(net.kyori.adventure.text.format.NamedTextColor.AQUA);
    }
    @Override protected Runnable onSave() {return null;}
    @Override protected String createKey() {return "add-spawner";}
    @Override protected List<SpawnerDef> entries() {return origin.spawners(root.draft.get());}
    @Override protected void render() {
        super.render();var zone=root.draft.get().optionalZones().get(origin.index(root.draft.get()));
        summary(Material.SPAWNER,msg("zone-spawners-summary",Placeholder.unparsed("id",origin.id()),Placeholder.unparsed("count",Integer.toString(entries().size()))),msg("zone-spawners-overview",Placeholder.unparsed("waves",Integer.toString(OptionalZoneMenu.waves(root,zone))),Placeholder.unparsed("mobs",Integer.toString(OptionalZoneMenu.mobs(root,zone)))));
    }
    @Override protected Button entry(SpawnerDef s,int index) {
        var lore=new ArrayList<Component>();lore.add(msg("zone-spawner-position",Placeholder.component("point",compactPoint(s.location())),Placeholder.unparsed("radius",SpawnerLibraryMenu.coordinate(s.radius()))));
        if(s.presetId()!=null) lore.add(SpawnerLibraryMenu.m("origin",Placeholder.component("name",Component.text(s.presetId()))));
        else lore.add(msg("zone-local-waves"));
        var waves=OptionalZoneMenu.waves(root,s);for(int i=0;i<waves.size();i++)lore.add(msg("zone-wave-line",Placeholder.unparsed("number",Integer.toString(i+1)),Placeholder.component("mode",msg("zone-mode-"+waves.get(i).mode().name().toLowerCase(Locale.ROOT))),Placeholder.component("mobs",Component.join(net.kyori.adventure.text.JoinConfiguration.separator(msg("list-separator")),waveMobs(waves.get(i))))));
        lore.add(Component.empty());lore.add(msg("zone-spawner-open"));
        return Button.of(Material.SPAWNER,msg("zone-spawner",Placeholder.unparsed("value",s.id())),lore,(p,c)->{
            if(!root.writable())return;
            if(c.isShiftClick()&&c.isRightClick()) {root.spawners(origin,values->DungeonMenu.remove(values,index));refresh();}
            else MenuListener.instance().later(()->{if(root.writable())new SpawnerMenu(root,origin,index,this).open();});
        });
    }
    @Override protected void create() {new SpawnerPickerMenu(root,origin,this,position(viewer)).open();}
}
