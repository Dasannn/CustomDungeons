package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
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
    static Component unlockLore(RoomDef room) {
        return msg("unlock-current",Placeholder.component("mode",msg(room.unlock()==UnlockMode.KEY?"unlock-key":"unlock-automatic")),
                Placeholder.component("carrier",carrierLore(room.keyCarrierTemplateId())));
    }
    private static Component carrierLore(String carrier) {
        if(carrier==null || carrier.isBlank()) return msg("carrier-unset");
        if(carrier.equals("*")) return msg("carrier-last-name");
        return msg("carrier-current",Placeholder.unparsed("value",carrier));
    }
    @Override protected void render() {
        var r=value();
        section(4,"section-spawners",Material.SPAWNER,msg("spawner-count",Placeholder.unparsed("value",Integer.toString(r.spawners().size()))),msg("room-summary",Placeholder.unparsed("id",r.id()),Placeholder.unparsed("position",Integer.toString(room+1)),Placeholder.unparsed("spawners",Integer.toString(r.spawners().size()))));
        add(2,"open-spawners",Material.SPAWNER,()->new RoomSpawnerList(root,room,this).open());
        add(6,"add-spawner",Material.EMERALD,()->{createSpawner();refresh();});

        section(11,"section-region",Material.GRASS_BLOCK,regionLore(r.region()),regionSizeLore(r.region()));
        region(19,"region",r.region(),v->root.room(room,old->new RoomDef(old.id(),v,old.checkpoint(),old.door(),old.unlock(),old.keyCarrierTemplateId(),old.spawners())),ToolType.REGION);
        giveTool(21,ToolType.REGION);
        section(15,"section-checkpoint",Material.RESPAWN_ANCHOR,pointLore(r.checkpoint()));
        point(23,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners())),ToolType.POINT);
        pointHere(25,"checkpoint",r.checkpoint(),p->root.room(room,v->new RoomDef(v.id(),v.region(),p,v.door(),v.unlock(),v.keyCarrierTemplateId(),v.spawners())));

        section(29,"section-door",Material.IRON_DOOR,regionLore(r.door()),regionSizeLore(r.door()));
        region(37,"door",r.door(),v->root.room(room,old->new RoomDef(old.id(),old.region(),old.checkpoint(),v,old.unlock(),old.keyCarrierTemplateId(),old.spawners())),ToolType.DOOR);
        section(38,"door-status",Material.PAPER,regionLore(r.door()),regionSizeLore(r.door()));
        add(39,"clear-door",Material.BARRIER,()->{root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),null,v.unlock(),v.keyCarrierTemplateId(),v.spawners()));refresh();});
        section(33,"section-unlock",Material.TRIPWIRE_HOOK,unlockLore(r));
        set(41,action("key",Material.TRIPWIRE_HOOK,"",(p,c)->{
            root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock()==UnlockMode.KEY?UnlockMode.AUTOMATIC:UnlockMode.KEY,v.keyCarrierTemplateId(),v.spawners()));refresh();
        },unlockLore(r)));
        set(42,Button.of(Material.TRIPWIRE_HOOK,msg("carrier"),
                List.of(msg("carrier-choice-lore"),msg("carrier-selected",Placeholder.component("carrier",carrierLore(r.keyCarrierTemplateId())))),
                (p,c)->MenuListener.instance().later(()->{if(root.writable()) new CarrierPicker().open();})));
        add(43,"carrier-template",Material.SKELETON_SKULL,()->new TemplatePickerMenu(root,this,id->root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),id,v.spawners()))).open(),carrierLore(r.keyCarrierTemplateId()));
    }
    private final class CarrierPicker extends DungeonPage<String> {
        CarrierPicker() { super("carrier",RoomMenu.this.root,RoomMenu.this); }
        @Override protected List<String> entries() {
            var ids = new ArrayList<String>(); ids.add("*");
            root.services.store.mobs().keySet().stream().sorted().forEach(ids::add);
            return ids;
        }
        @Override protected Button entry(String id,int index) {
            var mob = root.services.store.mobs().get(id);
            return Button.of("*".equals(id) ? Material.TRIPWIRE_HOOK : TemplatePickerMenu.egg(mob.entityType()),
                    "*".equals(id) ? msg("carrier-last") : msg("template-name",Placeholder.unparsed("id",id),Placeholder.unparsed("name",mob.displayName())),
                    List.of(msg("*".equals(id) ? "carrier-last-lore" : "template-lore")),(p,c)->{
                        if (!root.writable()) return;
                        root.room(room,v->new RoomDef(v.id(),v.region(),v.checkpoint(),v.door(),v.unlock(),id,v.spawners()));
                        MenuListener.instance().later(RoomMenu.this::open);
                    });
        }
        @Override protected void render() {
            super.render();
            set(4,Button.of(Material.TRIPWIRE_HOOK,msg("carrier"),List.of(msg("carrier-choice-lore")),(p,c)->{}));
        }
        @Override protected void create() { }
    }
    Button spawnerButton(SpawnerDef spawner,int index,RoomSpawnerList previous) {
        return action("spawner",Material.SPAWNER,spawner.id(),(p,c)->{
            if(c.isShiftClick()&&c.isRightClick()) {
                root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.remove(r.spawners(),index)));
                previous.refresh();
            } else MenuListener.instance().later(()->{if(root.writable()) new SpawnerMenu(root,room,index,previous).open();});
        },pointLore(spawner.location()),msg("spawner-summary",Placeholder.unparsed("radius",Inputs.formatNumber(spawner.radius(),1)),
                Placeholder.unparsed("waves",Integer.toString(spawner.waves().size()))));
    }
    void createSpawner() {
        String id=DungeonMenu.nextId("spawner_",root.draft.get().rooms().stream().flatMap(r->r.spawners().stream()).map(SpawnerDef::id).toList());
        root.room(room,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),DungeonMenu.append(r.spawners(),new SpawnerDef(id,null,3,List.of()))));
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
    @Override protected void create() {owner.createSpawner();refresh();}
}
