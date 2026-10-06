package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import static dev.dasan.customdungeons.gui.menu.SpawnerLibraryMenu.*;

/** A wave-editing context backed by a preset draft, sharing the existing wave/entry menus. */
public final class SpawnerPresetMenu extends DungeonMenu {
    private final Menu previous;
    private final Consumer<String> afterSave;
    private SpawnerPreset persisted;
    private boolean writing;
    private boolean discarding;
    private boolean abandoned;
    private List<ValidationError> errors=List.of();
    public SpawnerPresetMenu(DungeonListMenu services,SpawnerPreset preset,Menu parent,Consumer<String> afterSave) {
        super(services.viewerPlayer(),context(preset),services,false,"preset-editor");
        this.previous=parent; this.afterSave=afterSave;persisted=services.store.spawnerPresets().get(preset.id());
    }
    /** Only this adapter sees a room-shaped container; it is never saved as a dungeon. */
    private static DungeonDef context(SpawnerPreset preset) {
        var room=new RoomDef("preset",null,null,null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("preset",null,preset.radius(),preset.waves())));
        return new DungeonDef(preset.id(),preset.name(),false,null,null,1,0,30,3,false,0,0,false,new ScalingDef(0,0),
                Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
    }
    SpawnerPreset value() {
        var d=draft.get();var s=d.rooms().getFirst().spawners().getFirst();
        return new SpawnerPreset(d.id(),d.displayName(),s.radius(),s.waves());
    }
    @Override boolean dirty() {return !Objects.equals(persisted,value());}
    @Override boolean saving() {return writing;}
    @Override boolean outdated() {return !Objects.equals(persisted,services.store.spawnerPresets().get(value().id()));}
    @Override boolean canEdit(boolean notify) {
        if(abandoned || !viewer.isOnline() || !viewer.hasPermission("customdungeons.admin.edit") || services.store.isReloading()) return false;
        if(writing || services.presetBusy(value().id())) {if(notify) tell("busy");return false;}
        if(outdated()) {if(notify) tell("conflict");return false;}
        if(!MenuListener.instance().editLocks().tryLock("spawner:"+value().id(),viewer.getUniqueId())) {if(notify) tell("locked");return false;}
        return true;
    }
    @Override boolean writable() {return canEdit(true);}
    @Override void change(Consumer<Values> action) {
        if(!writable()) return;
        var values=new Values(draft.get());action.accept(values);draft.set(values.build());errors=List.of();
    }
    @Override protected int preferredRows() {return 6;}
    @Override protected Menu parent() {return previous;}
    @Override protected void renderHeader() {GuiTheme.help(this,List.of(m("editor-help-1"),m("editor-help-2"),m("editor-help-3")));}
    @Override protected void render() {
        var preset=value();
        summary(Material.SPAWNER,label(preset),m("radius-ready",arg("radius",Inputs.formatNumber(preset.radius(),1))),
                m("waves-ready",arg("value",preset.waves().size())),usage(preset.id(),services));
        set(11,GuiTheme.section(m("identity"),List.of(m("identity-lore"))));
        set(13,GuiTheme.section(m("use"),List.of(m("use-lore"))));
        set(15,GuiTheme.section(msg("section-waves"),List.of(m("waves-order"))));
        set(20,Button.of(Material.NAME_TAG,m("name",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("value",label(preset))),List.of(msg("value",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("value",label(preset))),Component.empty(),m("name-lore")),(p,c)->MenuListener.instance().later(()->{
            if(writable()) Inputs.text(p,m("name",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("value",label(value()))),value().name(),64,name->{if(writable()){change(v->v.name=name);refresh();}});
        })));
        set(29,Button.of(Material.TARGET,msg("radius",arg("value",Inputs.formatNumber(preset.radius(),1))),List.of(msg("value",arg("value",Inputs.formatNumber(preset.radius(),1))),Component.empty(),m("radius-lore")),(p,c)->MenuListener.instance().later(()->{
            if(writable()) Inputs.decimal(p,msg("radius",arg("value",Inputs.formatNumber(value().radius(),1))),1,64,inputValue(value().radius(),1,64),1,radius->{if(writable()){spawner(0,0,v->new SpawnerDef(v.id(),v.location(),radius,v.waves()));refresh();}});
        })));
        var uses=SpawnerPresets.usage(preset.id(),services.store.dungeons());
        set(22,GuiTheme.information(Material.BOOKSHELF,m("used-rooms",arg("rooms",uses.size())),uses.stream()
                .map(u -> m("used-room",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("dungeon",dev.dasan.customdungeons.text.Text.parse(u.dungeonName())),arg("room",u.roomId()))).toList()));
        var waves=new WaveListMenu(this,0,0,this);
        for(int w=0;w<Math.min(3,preset.waves().size());w++) {
            if(w==2&&preset.waves().size()>3) add(42,"all-waves",Material.ZOMBIE_HEAD,waves::open);
            else {
                var wave=preset.waves().get(w);var lore=new ArrayList<Component>();
                lore.add(m(wave.mode()==SpawnMode.STAGGERED?"wave-staggered":"wave-timing",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mode",msg("mode-"+wave.mode().name().toLowerCase(Locale.ROOT))),arg("pause",Inputs.formatNumber(wave.pauseAfterTicks()/20.0,1)),arg("interval",Inputs.formatNumber(wave.staggerIntervalTicks()/20.0,1))));
                lore.addAll(waveMobs(wave));lore.add(Component.empty());lore.add(m("wave-lore"));
                set(24+w*9,Button.of(Material.ZOMBIE_HEAD,msg("wave-label",arg("value",w+1)),lore,waves.waveButton(wave,w,this,this::refresh).onClick()));
            }
        }
        if(!errors.isEmpty()) set(40,GuiTheme.information(Material.RED_DYE,msg("errors"),errors.stream().map(e->Validator.describe(e,MenuListener.instance().messages())).toList()));
    }
    @Override protected void renderFooter() {
        set(45,back(parent()::open,false));set(49,save(this::saveDraft,dirty()));
        set(48,Button.of(Material.LIME_DYE,msg("add-wave"),List.of(Component.empty(),m("add-wave-lore")),(p,c)->MenuListener.instance().later(()->{if(writable()){new WaveListMenu(this,0,0,this).create();refresh();}})));
    }
    @Override void saveDraft() {
        if(!writable()) return;
        var preset=value();errors=new Validator().validate(preset,services.store.mobs());
        if(!errors.isEmpty()) {open();return;}
        writing=true;
        var locks=MenuListener.instance().editLocks();var owner=UUID.randomUUID();String key="spawner:"+preset.id();
        synchronized(locks) {locks.unlock(key,viewer.getUniqueId());locks.tryLock(key,owner);}
        services.store.save(preset,persisted).whenComplete((v,e)->{
            if(!services.plugin.isEnabled()) return;
            MenuListener.instance().later(()->{
                writing=false;locks.unlock(key,owner);if(e==null) persisted=preset;
                if(!viewer.isOnline()) return;
                tell(e==null?"saved":"save-failed");
                if(e==null&&afterSave!=null) afterSave.accept(preset.id());
                var holder=viewer.getOpenInventory().getTopInventory().getHolder();
                if(holder instanceof DungeonEditor editor && editor.root==this) editor.refresh();
            });
        });
    }
    @Override void closed() {
        if(abandoned || discarding || writing || services.store.isReloading() || !viewer.isOnline() || !services.plugin.isEnabled()) return;
        MenuListener.instance().later(() -> {
            var holder=viewer.getOpenInventory().getTopInventory().getHolder();
            if(holder instanceof DungeonEditor editor && editor.root==this) return;
            if(writing) return;
            MenuListener.instance().editLocks().unlock("spawner:"+value().id(),viewer.getUniqueId());
            if(!dirty()) return;
            discarding=true;
            try {open();Inputs.confirm(viewer,msg("discard-conflict"),()->{
                draft.set(context(persisted==null?new SpawnerPreset(value().id(),value().name(),3,List.of()):persisted));
                abandoned=true;MenuListener.instance().editLocks().unlock("spawner:"+value().id(),viewer.getUniqueId());
                if(previous!=null) previous.open();else viewer.closeInventory();
            });} finally {discarding=false;}
        });
    }
}
