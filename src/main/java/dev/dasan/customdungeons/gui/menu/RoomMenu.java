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
    public RoomMenu(DungeonMenu root,int room,Menu parent) {super("room",LootEditor.m("room-title",LootEditor.arg("room",room+1),LootEditor.arg("id",capitalized(root.draft.get().rooms().get(room).id()))),root,parent);this.room=room;}
    private static String capitalized(String id) {return id.substring(0,1).toUpperCase(Locale.ROOT)+id.substring(1);}
    @Override protected Material borderMaterial() {return Material.LIME_STAINED_GLASS_PANE;}
    @Override protected void renderHeader() {GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->LootEditor.m("help-room-"+i)).toList());tintName(8,net.kyori.adventure.text.format.NamedTextColor.GREEN);}
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
                UnlockMode.AUTOMATIC,r.keyCarrierTemplateId(),r.spawners(),RoomDef.OpeningMode.AUTOMATIC,r.ambience(),r.lootChests());
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
        add(19,"give-region",toolIcon(ToolType.REGION),()->root.services.tools.give(viewer,ToolType.REGION,root.draft.get().id(),dev.dasan.customdungeons.tool.EditOrigin.room(root.draft.get(),room)));
        region(28,"region",r.region(),v->root.room(room,old->new RoomDef(old.id(),v,old.checkpoint(),old.door(),old.unlock(),old.keyCarrierTemplateId(),old.spawners(),old.openingMode(),old.ambience(),old.lootChests())),ToolType.REGION,dev.dasan.customdungeons.tool.EditOrigin.room(root.draft.get(),room));
        pointHere(21,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience(),v.lootChests())));
        point(30,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience(),v.lootChests())),ToolType.POINT);
        giveTool(23,ToolType.DOOR);
        var selection=root.services.tools.selection(viewer.getUniqueId()).orElse(null);
        set(32,action("door",Material.LIME_DYE,"",(p,c)->{
            if(c.isRightClick()&&value().door()!=null) {
                root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),null,v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience(),v.lootChests()));
            } else {
                var latest=root.services.tools.selection(p.getUniqueId()).orElse(null);
                if(latest==null||!latest.complete()) {tell("no-selection");return;}
                root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),latest.toRegion(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode(),v.ambience(),v.lootChests()));
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
            root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners(),v.openingMode().next(),v.ambience(),v.lootChests()));refresh();
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
        presentation(r,finalRoom);
    }
    private void present(int slot,Component name,Component... lines) {
        var item=getInventory().getItem(slot);if(item!=null)item.editMeta(meta->{if(name!=null)meta.displayName(name);meta.lore(List.of(lines));});
    }
    private Component label(String key) {return LootEditor.m(key).color(net.kyori.adventure.text.format.NamedTextColor.GREEN);}
    private Component roomState(String key,boolean ready) {return MenuListener.instance().messages().get(ready?"gui.common.ready":"gui.common.missing",Placeholder.component("part",LootEditor.m("room-"+key)));}
    private Component coordinates(Region r) {
        return r==null?regionLore(null):LootEditor.m("room-region-value",LootEditor.arg("world",r.world()),LootEditor.arg("x1",r.min().x()),LootEditor.arg("y1",r.min().y()),LootEditor.arg("z1",r.min().z()),LootEditor.arg("x2",r.max().x()),LootEditor.arg("y2",r.max().y()),LootEditor.arg("z2",r.max().z()));
    }
    private void presentation(RoomDef r,boolean finalRoom) {
        var configured=LootEditor.m("room-configured");var context=LootEditor.m("room-context",LootEditor.arg("id",r.id()));
        summary(Material.OAK_DOOR,LootEditor.m("room-title",LootEditor.arg("room",room+1),LootEditor.arg("id",r.id())),
                LootEditor.m("room-overview",Placeholder.component("region",roomState("region",r.region()!=null)),Placeholder.component("checkpoint",roomState("checkpoint",r.checkpoint()!=null)),Placeholder.component("door",roomState("door",r.door()!=null||finalRoom)),Placeholder.component("unlock",roomState("unlock",r.openingMode()!=RoomDef.OpeningMode.KEY||r.keyCarrierTemplateId()!=null))),
                LootEditor.m("room-totals",LootEditor.arg("spawners",r.spawners().size()),LootEditor.arg("chests",r.lootChests().size()),Placeholder.component("ambience",AmbienceMenu.m(r.ambience()==null?"default":"custom"))));
        int[] slots={10,12,14,16};String[] keys={"region","checkpoint","door","unlock"};boolean[] ready={r.region()!=null,r.checkpoint()!=null,r.door()!=null||finalRoom,r.openingMode()!=RoomDef.OpeningMode.KEY||r.keyCarrierTemplateId()!=null};
        for(int i=0;i<slots.length;i++)present(slots[i],roomState(keys[i],ready[i]),ready[i]?configured:msg("point-unset"));
        present(19,null,context,Component.empty(),LootEditor.m("room-wand-lore"));
        present(21,null,LootEditor.position(r.checkpoint()),Component.empty(),LootEditor.m("room-edit"));
        present(23,null,Component.empty(),LootEditor.m("room-door-tool-lore"));
        if(!finalRoom)present(25,LootEditor.m("room-opening",Placeholder.component("mode",msg(unlockName(r.openingMode())))),"*".equals(r.keyCarrierTemplateId())?LootEditor.m("room-carrier-last"):unlockLore(r),Component.empty(),LootEditor.m("room-opening-lore"));
        present(28,null,coordinates(r.region()),Component.empty(),LootEditor.m("room-edit"));
        var marked=root.services.tools.lastPoint(viewer.getUniqueId()).filter(org.bukkit.Location::isWorldLoaded).map(l->new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch())).orElse(null);
        present(30,null,LootEditor.m("room-point-marked",Placeholder.component("point",LootEditor.position(marked))),Component.empty(),LootEditor.m("room-edit"));
        present(32,null,coordinates(r.door()),Component.empty(),LootEditor.m("room-use-selection"),LootEditor.m("room-remove-door"));
        if(r.openingMode()==RoomDef.OpeningMode.KEY)present(34,label("room-carrier"),"*".equals(r.keyCarrierTemplateId())?LootEditor.m("room-last-mob"):carrierLore(r.keyCarrierTemplateId()),Component.empty(),LootEditor.m("room-edit"));
        var resolved=resolvedRoom();present(37,null,LootEditor.m("room-spawners",LootEditor.arg("waves",resolved.spawners().stream().mapToInt(sp->sp.waves().size()).sum()),LootEditor.arg("mobs",totalMobs(resolved))),Component.empty(),LootEditor.m("room-edit"));
        present(39,null,context,Component.empty(),LootEditor.m("room-edit"));
        present(41,label("room-preview"),LootEditor.m("room-preview-detail"),Component.empty(),LootEditor.m("preview-time"));
        present(43,null,LootEditor.m(r.ambience()==null?"room-default":"room-custom"),Component.empty(),LootEditor.m("room-edit"));
    }
    @Override protected void renderFooter() {
        var r=value();
        set(47,Button.of(Material.CHEST,LootEditor.m("room-link",LootEditor.arg("count",r.lootChests().size())).color(net.kyori.adventure.text.format.NamedTextColor.GREEN),
                List.of(Component.text(String.join(" · ",r.lootChests().stream().map(LootChestDef::id).toList())),LootEditor.m("shared"),Component.empty(),LootEditor.m("room-link-lore")),
                (p,c)->MenuListener.instance().later(()->{if(root.writable())new LootChestListMenu(root,r.id(),this).open();})));
        for(int slot:new int[]{48,50})set(slot,GuiTheme.information(borderMaterial(),Component.empty(),List.of()));
        tintNavigation(net.kyori.adventure.text.format.NamedTextColor.GREEN);
    }
    static List<String> carrierTemplates(RoomDef room,Map<String,MobTemplate> library) {
        return room.spawners().stream().flatMap(s->s.waves().stream()).flatMap(w->w.entries().stream())
                .map(WaveEntry::templateId).filter(library::containsKey).distinct().sorted().toList();
    }
    private RoomDef resolvedRoom() {
        var r=value();
        var spawners=r.spawners().stream().map(s -> s.presetId()!=null && !root.services.store.spawnerPresets().containsKey(s.presetId())
                ? s : SpawnerPresets.makeLocal(s,root.services.store.spawnerPresets())).toList();
        return new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),spawners,r.openingMode(),r.ambience(),r.lootChests());
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
                        root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),id,v.spawners(),v.openingMode(),v.ambience(),v.lootChests()));
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
                root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.remove(r.spawners(),index),r.openingMode(),r.ambience(),r.lootChests()));
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
