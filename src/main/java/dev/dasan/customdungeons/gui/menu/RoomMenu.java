package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.tool.ToolType;
import java.util.*;
import org.bukkit.Material;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Four aligned editing panels and a separate spawner section. */
public final class RoomMenu extends DungeonEditor {
    private final int room;
    public RoomMenu(DungeonMenu root,int room,Menu parent) {super("room",root,parent);this.room=room;}
    private RoomDef value() {return root.draft.get().rooms().get(room);}
    static String unlockName(RoomDef.OpeningMode mode) {
        return switch (mode) {
            case AUTOMATIC -> "unlock-automatic";
            case KEY -> "unlock-key";
            case EXTERNAL_KEY -> "unlock-external";
        };
    }
    static Component unlockLore(RoomDef room) {
        if (room.openingMode()==RoomDef.OpeningMode.EXTERNAL_KEY) return msg("unlock-external-current");
        return msg("unlock-current",Placeholder.component("mode",msg(unlockName(room.openingMode()))),
                Placeholder.component("carrier",carrierLore(room.keyCarrierTemplateId())));
    }
    private static Component carrierLore(String carrier) {
        if(carrier==null || carrier.isBlank()) return msg("carrier-unset");
        if(carrier.equals("*")) return msg("carrier-last-name");
        return msg("carrier-current",Placeholder.unparsed("value",carrier));
    }
    @Override protected void render() {
        var r=value();
        boolean finalRoom=room==root.draft.get().rooms().size()-1;
        if(finalRoom && r.unlock()==UnlockMode.KEY) r=new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),
                UnlockMode.AUTOMATIC,r.keyCarrierTemplateId(),r.spawners(),RoomDef.OpeningMode.AUTOMATIC,r.ambience());
        summary(Material.OAK_DOOR,msg("room-label",Placeholder.unparsed("value",Integer.toString(room+1))),
                AmbienceMenu.m("room-overview",Placeholder.component("region",AmbienceMenu.m(r.region()!=null?"marker-ready":"marker-missing")),
                        Placeholder.component("checkpoint",AmbienceMenu.m(r.checkpoint()!=null?"marker-ready":"marker-missing")),
                        Placeholder.component("door",AmbienceMenu.m(r.door()!=null || finalRoom?"marker-ready":"marker-missing"))),
                AmbienceMenu.m("room-opening",Placeholder.component("mode",msg(unlockName(r.openingMode()))),
                        Placeholder.component("ambience",AmbienceMenu.m(r.ambience()==null?"default":"custom"))));
        sectionState(10,"section-region",r.region()!=null,regionLore(r.region()));
        sectionState(12,"section-checkpoint",r.checkpoint()!=null,pointLore(r.checkpoint()));
        sectionState(14,"section-door",r.door()!=null||(r.unlock()==UnlockMode.AUTOMATIC&&room==root.draft.get().rooms().size()-1),regionLore(r.door()));
        sectionState(16,"section-unlock",r.openingMode()!=RoomDef.OpeningMode.KEY||r.keyCarrierTemplateId()!=null,unlockLore(r));
        giveTool(19,ToolType.REGION);
        region(28,"region",r.region(),v->root.room(room,old->new RoomDef(old.id(),v,old.checkpoint(),old.door(),old.unlock(),old.keyCarrierTemplateId(),old.spawners(),old.openingMode(),old.ambience())),ToolType.REGION);
        pointHere(21,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience())));
        point(30,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience())),ToolType.POINT);
        giveTool(23,ToolType.DOOR);
        var selection=root.services.tools.selection(viewer.getUniqueId()).orElse(null);
        set(32,action("door",Material.LIME_DYE,"",(p,c)->{
            if(c.isRightClick()&&value().door()!=null) {
                root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),null,v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience()));
            } else {
                var latest=root.services.tools.selection(p.getUniqueId()).orElse(null);
                if(latest==null||!latest.complete()) {tell("no-selection");return;}
                root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),latest.toRegion(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience()));
            }
            refresh();
        },regionLore(r.door()),selectionLore(selection),msg("door-remove-selection-lore")));
        var openingIcon=switch(r.openingMode()) {
            case AUTOMATIC -> Material.LIME_DYE;
            case KEY -> Material.TRIPWIRE_HOOK;
            case EXTERNAL_KEY -> Material.COMMAND_BLOCK;
        };
        var openingLabel=msg(switch(r.openingMode()) {
            case AUTOMATIC -> "unlock-automatic";
            case KEY -> "unlock-key-editor";
            case EXTERNAL_KEY -> "unlock-external-editor";
        });
        set(25,finalRoom?GuiTheme.unavailable(msg("key",Placeholder.component("value",msg("unlock-automatic"))),msg("final-room-unlock")):
                action("key",openingIcon,net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(openingLabel),(p,c)->{
            if(room==root.draft.get().rooms().size()-1) return;
            root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode().next(),v.ambience()));refresh();
        },unlockLore(r),r.openingMode()==RoomDef.OpeningMode.EXTERNAL_KEY
                ? msg("unlock-external-command",Placeholder.unparsed("dungeon",root.draft.get().id())) : msg("unlock-cycle")));
        set(34,r.openingMode()!=RoomDef.OpeningMode.KEY?GuiTheme.unavailable(msg("carrier"),msg("only-key")):
                action("carrier",Material.TRIPWIRE_HOOK,"",(p,c)->MenuListener.instance().later(()->{if(root.writable()) new CarrierPicker().open();}),
                        msg("carrier-selected",Placeholder.component("carrier",carrierLore(r.keyCarrierTemplateId())))));
        set(37,action("open-spawners",Material.SPAWNER,r.spawners().size(),(p,c)->MenuListener.instance().later(()->{
            if(root.writable())new RoomSpawnerList(root,room,this).open();
        })));
        add(39,"add-spawner",Material.LIME_DYE,()->createSpawner());
        set(43,Button.of(Material.NOTE_BLOCK,AmbienceMenu.m("link"),List.of(AmbienceMenu.m("link-lore")),(p,c)->
                MenuListener.instance().later(()->{if(root.writable())new AmbienceMenu(root,room,this).open();})));
        add(41,"view-room",Material.SPYGLASS,()->{
            var previews=root.services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.tool.PreviewRenderer.class);
            if(previews!=null) {
                var v=new DungeonMenu.Values(root.draft.get()); v.rooms=List.of(value());
                previews.showDungeon(viewer,v.build(),15);
            }
        });
    }
    @Override protected void renderFooter() {
        for(int slot:new int[]{48,50})set(slot,GuiTheme.information(borderMaterial(),Component.empty(),List.of()));
    }
    static List<String> carrierTemplates(RoomDef room,Map<String,MobTemplate> library) {
        return room.spawners().stream().flatMap(s->s.waves().stream()).flatMap(w->w.entries().stream())
                .map(WaveEntry::templateId).filter(library::containsKey).distinct().sorted().toList();
    }
    private RoomDef resolvedRoom() {
        var r=value();
        var spawners=r.spawners().stream().map(s -> s.presetId()!=null && !root.services.store.spawnerPresets().containsKey(s.presetId())
                ? s : SpawnerPresets.makeLocal(s,root.services.store.spawnerPresets())).toList();
        return new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),spawners,r.openingMode(),r.ambience());
    }
    private final class CarrierPicker extends DungeonPage<String> {
        CarrierPicker() { super("carrier",RoomMenu.this.root,RoomMenu.this); }
        @Override protected List<String> entries() {
            var templates=carrierTemplates(resolvedRoom(),root.services.store.mobs());
            if(templates.isEmpty()) return List.of("*", "");
            var ids=new ArrayList<String>();ids.add("*");ids.addAll(templates);return ids;
        }
        @Override protected Button entry(String id,int index) {
            if(id.isEmpty()) return GuiTheme.unavailable(msg("carrier-template"),msg("carrier-no-mobs"));
            var mob = root.services.store.mobs().get(id);
            return Button.of("*".equals(id) ? Material.TRIPWIRE_HOOK : TemplatePickerMenu.egg(mob.entityType()),
                    "*".equals(id) ? msg("carrier-last") : msg("template-name",Placeholder.unparsed("id",id),Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(mob.displayName()))),
                    List.of(msg("carrier-option-lore")),(p,c)->{
                        if (!root.writable() || (!id.equals("*")&&!carrierTemplates(resolvedRoom(),root.services.store.mobs()).contains(id))) return;
                        root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),id,v.spawners(),v.openingMode(),v.ambience()));
                        MenuListener.instance().later(RoomMenu.this::open);
                    });
        }
        @Override protected void render() {
            super.render();
            summary(Material.TRIPWIRE_HOOK,msg("carrier"),msg("carrier-choice-lore"));
        }
        @Override protected void create() { }
        @Override protected boolean canCreate() {return false;}
        @Override protected Runnable onSave() {return null;}
    }
    Button spawnerButton(SpawnerDef spawner,int index,RoomSpawnerList previous) {
        var lore=new ArrayList<Component>();lore.add(pointLore(spawner.location()));
        var preset=spawner.presetId()==null?null:root.services.store.spawnerPresets().get(spawner.presetId());
        var waves=preset==null?spawner.waves():preset.waves();
        if(spawner.presetId()!=null) lore.add(SpawnerLibraryMenu.m("origin",Placeholder.component("name",preset==null?Component.text(spawner.presetId()):SpawnerLibraryMenu.label(preset))));
        lore.add(msg("spawner-summary",Placeholder.unparsed("radius",Inputs.formatNumber(spawner.radius(),1)),
                Placeholder.unparsed("waves",Integer.toString(waves.size()))));
        for(int w=0;w<waves.size();w++) lore.add(waveLine(waves.get(w),w));
        return action("spawner-label",Material.SPAWNER,index+1,(p,c)->{
            if(c.isShiftClick()&&c.isRightClick()) {
                root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.remove(r.spawners(),index),r.openingMode(),r.ambience()));
                previous.refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new SpawnerMenu(root,room,index,previous).open();});
        },lore.toArray(Component[]::new));
    }
    void createSpawner() {
        new SpawnerPickerMenu(root,room,this,position(viewer)).open();
    }

}

/** Keeps all spawners accessible without crowding the room's editing panels. */
final class RoomSpawnerList extends DungeonPage<SpawnerDef> {
    private final int room;
    private final RoomMenu owner;
    RoomSpawnerList(DungeonMenu root,int room,RoomMenu owner) {super("room-spawners",root,owner);this.room=room;this.owner=owner;}
    @Override protected String createKey() {return "add-spawner";}
    @Override protected List<SpawnerDef> entries() {return root.draft.get().rooms().get(room).spawners();}
    @Override protected Button entry(SpawnerDef value,int index) {return owner.spawnerButton(value,index,this);}
    @Override protected void create() {owner.createSpawner();}
}
