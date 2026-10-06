package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.Validator;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.*;
import dev.dasan.customdungeons.tool.construction.BuildState;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/** Persistent build context shares the existing editor, validation, selectors and edit locks. */
public final class BuildMenu extends DungeonMenu {
    private final BuildModeService mode;
    private final BuildState state;
    private final UUID lockOwner=UUID.randomUUID();
    private boolean released,saving;
    private Selection selection;
    private int selectionTool=-1,selectionRoom=-1;
    private List<dev.dasan.customdungeons.config.ValidationError> validation=List.of();

    public BuildMenu(Player player,DungeonListMenu list,BuildModeService mode,BuildState state) {
        super(player,state.definition(),list);this.mode=mode;this.state=state;
    }
    public static BuildMenu prepare(Player player,String id,BuildModeService mode) {
        if(!validId(id)) {MenuListener.instance().messages().send(player,"gui.dungeon.invalid-id");return null;}
        var wizard=WizardMenu.active(player.getUniqueId());if(wizard!=null) wizard.pause();
        // Return native reward deposits while their existing editor still owns its lock.
        // They must be in the player's snapshot before this context replaces that editor.
        if(player.getOpenInventory().getTopInventory().getHolder() instanceof RewardMenu reward) reward.capture();
        var list=new DungeonListMenu(player);
        if(list.busy(id)) {list.tell("busy");return null;}
        var source=list.editor(id);
        if(source==null) {MenuListener.instance().messages().send(player,"build.missing");return null;}
        if(!source.writable()||source.saving()) return null;
        var saved=mode.journal().draft(player.getUniqueId(),id);
        var state=saved.map(BuildState::new).orElseGet(()->new BuildState(new BuildState.Saved(source.draft.get(),
                latestDefinition(list,id),0,0,List.of())));
        if(state.conflicts(latestDefinition(list,id))) {list.tell("conflict");return null;}
        // Explicit unsaved editor changes become a build action; the initial entry keeps them too.
        if(saved.isPresent()&&source.dirty()) state.change(source.draft.get());
        var menu=new BuildMenu(player,list,mode,state);
        if(!list.enterBuild(menu,source)) return null;
        return menu;
    }
    public BuildState state() {return state;}
    public DungeonDef definition() {return draft.get();}
    public boolean ready() {
        return !released&&viewer.isOnline()&&viewer.hasPermission("customdungeons.admin.edit")
                &&!services.store.isReloading()&&!services.busy(definition().id())&&!outdated()
                &&MenuListener.instance().editLocks().tryLock(definition().id(),lockOwner);
    }
    UUID lockOwner() {return lockOwner;}
    @Override boolean canEdit(boolean notify) {
        boolean ready=ready()&&!saving&&mode.active(viewer.getUniqueId(),this);
        if(!ready&&notify) tell(outdated()?"conflict":"busy");return ready;
    }
    @Override boolean outdated() {return state!=null&&state.conflicts(latestDefinition(services,state.definition().id()));}
    @Override boolean saving() {return saving;}
    @Override boolean dirty() {return !Objects.equals(services.store.dungeons().get(definition().id()),definition());}
    @Override void closed() {} // Escape keeps the build session and its bounded particles active.
    @Override void confirmDiscard(Runnable next) {mode.exit(viewer);next.run();}
    @Override void change(Consumer<Values> action) {
        if(!writable()) return;
        var values=new Values(draft.get());action.accept(values);state.change(values.build());
        draft.set(state.definition());validation=List.of();selection=null;persist();refreshTools();preview();
    }
    private void persist() {
        mode.journal().save(viewer.getUniqueId(),state.snapshot()).whenComplete((v,failure)->{
            if(failure==null||!services.plugin.isEnabled()) return;
            MenuListener.instance().later(()->{if(viewer.isOnline()) bsend("draft-save-failed");});
        });
    }
    public void refreshTools() {
        if(!mode.active(viewer.getUniqueId(),this)) return;
        for(int i=0;i<9;i++) viewer.getInventory().setItem(i,BuildTools.item(MenuListener.instance().messages(),i,state.room()));
        actionbar();
    }
    public void preview() {
        var renderer=services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(renderer!=null&&mode.active(viewer.getUniqueId(),this)) renderer.wizard(viewer,draft.get());
    }
    public void actionbar() {
        if(!mode.active(viewer.getUniqueId(),this)) return;
        int slot=viewer.getInventory().getHeldItemSlot();
        var messages=MenuListener.instance().messages();
        viewer.sendActionBar(messages.get("build.actionbar",
                Placeholder.component("tool",BuildTools.name(messages,slot,state.room())),
                Placeholder.component("context",messages.get(draft.get().rooms().isEmpty()?"build.no-room":"build.context",
                        Placeholder.unparsed("room",Integer.toString(state.room()+1)),Placeholder.component("point",messages.get("build.point-"+state.snapshot().point()))))));
    }
    public void release() {
        if(released)return;released=true;
        services.discard(this);
        if(!saving) MenuListener.instance().editLocks().unlock(definition().id(),lockOwner);
        var renderer=services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(renderer!=null) renderer.stopWizard(viewer.getUniqueId());
        services.tools.clear(viewer.getUniqueId());viewer.sendActionBar(Component.empty());
    }
    public void interact(PlayerInteractEvent event) {
        if(!writable()) return;
        int slot=BuildTools.slot(viewer.getInventory().getItemInMainHand());if(slot<0) return;
        boolean left=event.getAction()==Action.LEFT_CLICK_BLOCK||event.getAction()==Action.LEFT_CLICK_AIR;
        if(slot<=2) {
            if(slot==2&&viewer.isSneaking()) {
                // TODO T38: switch between the room door and the entrance door when that model exists.
                bsend("entry-door-unavailable");return;
            }
            if(slot>0&&!hasRoom()) return;
            var block=event.getClickedBlock();if(block==null)return;
            if(selectionTool!=slot||selectionRoom!=state.room()||selection==null||!selection.world().equals(block.getWorld().getName())) {
                selection=new Selection(block.getWorld().getName(),null,null);
                services.tools.clear(viewer.getUniqueId());
            }
            selectionTool=slot;selectionRoom=state.room();
            var pos=new BlockPos(block.getX(),block.getY(),block.getZ());
            selection=new Selection(selection.world(),left?pos:selection.a(),left?selection.b():pos);
            services.tools.selectBuild(viewer,block.getLocation(),left);
            if(selection.complete()) {
                var region=selection.toRegion();
                if(slot==0) change(v->v.area=region);
                else room(state.room(),r->new RoomDef(r.id(),slot==1?region:r.region(),r.checkpoint(),slot==2?region:r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners(),r.openingMode()));
                bsend("changed");
            }
        } else if(slot==3) {
            if(!hasRoom())return;
            var target=viewer.getTargetBlockExact(8);if(target==null){bsend("no-target");return;}
            var l=target.getLocation().add(.5,1,.5);
            var point=new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),viewer.getLocation().getYaw(),0);
            new SpawnerPickerMenu(this,state.room(),this,point).open();
        } else if(slot==4) bsend("plates-unavailable");
        else if(slot==5) {
            if(viewer.isSneaking()) {state.cyclePoint();persist();actionbar();return;}
            var point=position(viewer);
            if(state.snapshot().point()==0) {
                if(!hasRoom())return;
                room(state.room(),r->new RoomDef(r.id(),r.region(),point,r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners(),r.openingMode()));
            } else change(v->{if(state.snapshot().point()==1)v.lobby=point;else v.exit=point;});
            bsend("changed");
        } else if(slot==6) {
            if(!left&&!viewer.isSneaking()) {new BuildRoomsMenu(this).open();return;}
            if(!hasRoom()) {new BuildRoomsMenu(this).open();return;}
            selectRoom(Math.floorMod(state.room()+(viewer.isSneaking()?-1:1),draft.get().rooms().size()));
        } else if(slot==7) undo();
        else open();
        actionbar();
    }
    private boolean hasRoom() {if(!draft.get().rooms().isEmpty()) return true;bsend("no-room");return false;}
    void selectRoom(int room) {if(!writable())return;state.room(room);selection=null;services.tools.clear(viewer.getUniqueId());persist();refreshTools();}
    void createRoom() {
        if(!writable())return;
        String id=nextId("room_",draft.get().rooms().stream().map(RoomDef::id).toList());
        change(v->v.rooms=append(v.rooms,new RoomDef(id,null,null,null,UnlockMode.AUTOMATIC,"*",List.of())));
        selectRoom(draft.get().rooms().size()-1);
    }
    void undo() {
        if(!writable()) return;
        if(!state.undo()) {bsend("undo-empty");return;}
        draft.set(state.definition());selection=null;validation=List.of();services.tools.clear(viewer.getUniqueId());
        persist();refreshTools();preview();bsend("undone");
    }
    @Override protected void render() {
        super.render();
        if(!validation.isEmpty()) set(41,Button.of(Material.RED_DYE,msg("errors"),validation.stream().map(error-> {
            var args=error.args().entrySet().stream().map(e->Placeholder.unparsed(e.getKey(),e.getValue()))
                    .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
            return msg("error-line",Placeholder.unparsed("path",error.path()),Placeholder.component("error",MenuListener.instance().messages().get(error.messageKey(),args)));
        }).toList(),(p,c)->{}));
    }
    @Override protected void renderFooter() {
        set(45,Button.of(Material.RECOVERY_COMPASS,b("undo"),List.of(b("tool-8.lore")),(p,c)->MenuListener.instance().later(()->{undo();refresh();})));
        set(47,Button.of(Material.OAK_DOOR,b("rooms"),List.of(b("room-previous-create")),(p,c)->MenuListener.instance().later(()->new BuildRoomsMenu(this).open())));
        set(48,Button.of(Material.ENDER_EYE,b("validate"),List.of(b("validate-lore")),(p,c)->MenuListener.instance().later(()->{if(writable()){validateDraft();refresh();}})));
        set(49,SpawnerLibraryMenu.save(this::saveDraft,dirty()));
        set(51,Button.of(Material.BARRIER,b("exit"),List.of(b("exit-lore")),(p,c)->MenuListener.instance().later(()->mode.exit(viewer))));
    }
    private boolean validateDraft() {
        validation=new Validator().validate(draft.get(),services.store.mobs(),services.store.spawnerPresets());
        bsend(validation.isEmpty()?"valid":"invalid");return validation.isEmpty();
    }
    @Override void saveDraft() {
        if(!writable()||!validateDraft()) {refresh();return;}
        DungeonDef snapshot=draft.get();saving=true;
        var listener=MenuListener.instance();
        // Keep the lease's independent edit lock while both persistence operations run.
        mode.journal().save(viewer.getUniqueId(),state.snapshot()).thenCompose(v->{
            var publication=new java.util.concurrent.CompletableFuture<Void>();
            listener.later(()->{
                try {services.store.save(snapshot).whenComplete((ignored,error)->{
                    if(error==null) publication.complete(null);else publication.completeExceptionally(error);
                });} catch(RuntimeException error) {publication.completeExceptionally(error);}
            });
            return publication;
        }).whenComplete((v,failure)->{
            if(!services.plugin.isEnabled())return;
            listener.later(()->{
                saving=false;
                if(failure==null) {
                    state.published(services.store.dungeons().getOrDefault(snapshot.id(),snapshot));draft.set(state.definition());
                    var wizard=services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
                    if(wizard!=null) wizard.get(snapshot.id()).ifPresent(saved->wizard.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(state.definition(),saved.step(),saved.completed())));
                    persist();acceptWorkingVersion();preview();
                }
                // An exit during save is permitted; restore never waits on publication.
                if(released) listener.editLocks().unlock(snapshot.id(),lockOwner);
                if(viewer.isOnline()) {tell(failure==null?"saved":"save-failed");if(!released)refresh();}
            });
        });
    }
    public static Component b(String key,net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {return MenuListener.instance().messages().get("build."+key,args);}
    private void bsend(String key) {MenuListener.instance().messages().send(viewer,"build."+key);}
}

final class BuildRoomsMenu extends DungeonPage<RoomDef> {
    private final BuildMenu build;
    BuildRoomsMenu(BuildMenu build) {super("rooms",build,build);this.build=build;}
    @Override protected List<RoomDef> entries() {return build.definition().rooms();}
    @Override protected Button entry(RoomDef room,int index) {
        return Button.of(Material.OAK_DOOR,BuildMenu.b("room-name",Placeholder.unparsed("room",Integer.toString(index+1))),
                List.of(BuildMenu.b("select-room-lore")),(p,c)->MenuListener.instance().later(()->{build.selectRoom(index);viewer.closeInventory();}));
    }
    @Override protected String createKey() {return "add-room";}
    @Override protected void create() {build.createRoom();viewer.closeInventory();}
}
