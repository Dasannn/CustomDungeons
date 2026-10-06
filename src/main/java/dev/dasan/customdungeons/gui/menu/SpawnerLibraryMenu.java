package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

/** Shared library, optionally acting as a picker for a dungeon's preferred presets. */
public final class SpawnerLibraryMenu extends DungeonPage<SpawnerPreset> {
    final DungeonListMenu services;
    private final Consumer<String> choose;
    private boolean deleting;
    public SpawnerLibraryMenu(DungeonListMenu services,Menu parent,Consumer<String> choose) {
        super(services.viewerPlayer(),"preset-library",parent); this.services=services; this.choose=choose;
    }
    static Component m(String key,TagResolver... args) {return MenuListener.instance().messages().get("gui.spawner."+key,args);}
    static TagResolver arg(String key,Object value) {return Placeholder.unparsed(key,String.valueOf(value));}
    static Component label(SpawnerPreset p) {return dev.dasan.customdungeons.text.Text.parse(p.name());}
    static List<Component> waves(List<WaveDef> waves,DungeonListMenu services) {
        var lines=new ArrayList<Component>();
        for(int i=0;i<waves.size();i++) {
            var w=waves.get(i);
            var mobs=w.entries().stream().map(e -> {
                var mob=services.store.mobs().get(e.templateId());
                return DungeonEditor.msg("mob-count",arg("count",e.count()),Placeholder.component("name",mob==null?Component.text(e.templateId()):dev.dasan.customdungeons.text.Text.parse(mob.displayName())));
            }).toList();
            lines.add(DungeonEditor.msg("wave-mobs",arg("number",i+1),Placeholder.component("mode",DungeonEditor.msg("mode-"+w.mode().name().toLowerCase(Locale.ROOT))),
                    Placeholder.component("mobs",mobs.isEmpty()?DungeonEditor.msg("empty-mobs"):Component.join(net.kyori.adventure.text.JoinConfiguration.separator(DungeonEditor.msg("list-separator")),mobs))));
        }
        return lines;
    }
    static String coordinate(double value) {return Double.isFinite(value)?java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString():Double.toString(value);}
    static Component position(Point point,boolean colon) {
        if(point==null) return DungeonEditor.msg("point-unset");
        return m(colon?"point-colon":"point",arg("world",point.world()),arg("x",coordinate(point.x())),arg("y",coordinate(point.y())),arg("z",coordinate(point.z())));
    }
    static Component mobs(List<WaveDef> waves,DungeonListMenu services) {
        var lines=waves.stream().flatMap(w->w.entries().stream()).map(e -> {
            var mob=services.store.mobs().get(e.templateId());
            return DungeonEditor.msg("mob-count",arg("count",e.count()),Placeholder.component("name",mob==null?Component.text(e.templateId()):dev.dasan.customdungeons.text.Text.parse(mob.displayName())));
        }).toList();
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(m("separator")),lines);
    }
    static Button back(Runnable go,boolean shortLore) {
        return Button.of(Material.ARROW,MenuListener.instance().messages().get("gui.common.back"),List.of(Component.empty(),m(shortLore?"back-short-lore":"back-lore")),(p,c)->MenuListener.instance().later(go));
    }
    static Button save(Runnable save,boolean dirty) {
        var lore=new ArrayList<Component>();if(dirty) lore.add(MenuListener.instance().messages().get("gui.common.unsaved"));
        lore.add(Component.empty());lore.add(m("save-lore"));
        var button=Button.of(Material.LIME_CONCRETE,MenuListener.instance().messages().get("gui.common.save"),lore,(p,c)->{save.run();MenuListener.instance().play(p,MenuListener.instance().sounds().save());});
        if(dirty) button.icon().editMeta(meta->meta.setEnchantmentGlintOverride(true));return button;
    }
    static Component usage(String id,DungeonListMenu services) {
        var uses=SpawnerPresets.usage(id,services.store.dungeons());
        long dungeons=uses.stream().map(SpawnerPresets.Usage::dungeonId).distinct().count();
        return m(uses.isEmpty()?"unused":uses.size()==1?"usage-one":dungeons>1?"usage-many":"usage",arg("rooms",uses.size()),arg("dungeons",dungeons));
    }
    static List<Component> details(SpawnerPreset preset,DungeonListMenu services) {
        var lore=new ArrayList<Component>();
        lore.add(m("summary",arg("radius",Inputs.formatNumber(preset.radius(),1)),arg("waves",preset.waves().size())));
        lore.addAll(waves(preset.waves(),services));lore.add(usage(preset.id(),services));return lore;
    }
    @Override protected int preferredRows() {return Math.max(4,super.preferredRows());}
    @Override protected List<SpawnerPreset> entries() {return services.store.spawnerPresets().values().stream().sorted(Comparator.comparing(SpawnerPreset::id)).toList();}
    @Override protected void render() {
        super.render();summary(Material.SPAWNER,m("library-summary",arg("value",entries().size())),m("library-summary-lore"));
        if(entries().isEmpty()) set(13,GuiTheme.information(Material.GRAY_DYE,m("empty"),List.of(m("empty-lore"))));
    }
    @Override protected void renderHeader() {GuiTheme.help(this,List.of(m("library-help-1"),m("library-help-2"),m("library-help-3")));}
    @Override protected String createKey() {return "preset-create";}
    @Override protected Button entry(SpawnerPreset value,int index) {
        var lore=details(value,services);lore.add(Component.empty());lore.add(m(choose==null?"entry-lore":"choose-lore"));
        return Button.of(Material.SPAWNER,label(value),lore,(p,c)->MenuListener.instance().later(() -> {
            if(choose!=null) {choose.accept(value.id()); if(parent()!=null) parent().open();return;}
            if(c.isShiftClick()&&c.isRightClick()) {
                Inputs.confirm(p,m("delete-confirm",arg("rooms",SpawnerPresets.usage(value.id(),services.store.dungeons()).size())),() -> delete(value.id()));
            } else new SpawnerPresetMenu(services,value,this,null).open();
        }));
    }
    private void delete(String id) {
        if(deleting || !viewer.isOnline() || !viewer.hasPermission("customdungeons.admin.edit") || services.store.isReloading()) return;
        if(services.presetBusy(id) || services.presetDirty(id)) {tell("preset-in-use-busy");return;}
        var locks=MenuListener.instance().editLocks();UUID owner=UUID.randomUUID();
        if(!locks.tryLock("spawner:"+id,owner)) {tell("locked");return;}
        deleting=true;
        services.store.deleteSpawnerPreset(id).whenComplete((v,e) -> {
            if(!services.plugin.isEnabled()) return;
            MenuListener.instance().later(() -> {locks.unlock("spawner:"+id,owner);deleting=false;if(viewer.isOnline()){tell(e==null?"preset-deleted":"save-failed");refresh();}});
        });
    }
    @Override protected void create() {create(services,this,choose);}
    static void create(DungeonListMenu services,Menu parent,Consumer<String> saved) {
        Inputs.text(services.viewerPlayer(),m("create-name"),"",64,name -> {
            if(name.isBlank() || services.store.isReloading() || !services.viewerPlayer().isOnline() || !services.viewerPlayer().hasPermission("customdungeons.admin.edit")) return;
            String id=DungeonMenu.nextId("spawner_",services.store.spawnerPresets().keySet());
            new SpawnerPresetMenu(services,new SpawnerPreset(id,name,3,List.of()),parent,saved).open();
        });
    }
}
