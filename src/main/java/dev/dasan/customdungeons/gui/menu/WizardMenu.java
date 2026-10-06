package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.gui.wizard.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** The shared dungeon editor root doubles as the assistant's seven-step shell. */
public final class WizardMenu extends DungeonMenu {
    private static final Map<UUID,WizardMenu> active=new HashMap<>();
    private static final Material[] icons={Material.MAP,Material.RED_BED,Material.SPAWNER,Material.OAK_DOOR,Material.COMPARATOR,Material.CHEST,Material.LIME_CONCRETE};
    private final WizardState state;
    private final UUID lockOwner=UUID.randomUUID();
    private final EditLocks locks=MenuListener.instance().editLocks();
    private WizardProgress progress;
    private boolean stopped;
    private boolean finishing;
    private int roomsPage;
    private int currentRoom;
    private int announced=-1;
    public WizardMenu(Player player,DungeonDef definition,DungeonListMenu list,WizardState state) {
        super(player,definition,list,false,"wizard-title-"+(state.step()+1));this.state=state;
    }
    public static WizardMenu active(UUID player) {return active.get(player);}
    public static void register(CustomDungeonsPlugin plugin) {
        var drafts=new WizardDraftStore(plugin.getDataFolder().toPath(),ForkJoinPool.commonPool(),
                file->plugin.getLogger().warning(plain(plugin,"wizard.draft-load-failed",Placeholder.unparsed("file",file))));
        plugin.getServer().getServicesManager().register(WizardDraftStore.class,drafts,plugin,org.bukkit.plugin.ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
                var menu=active(event.getPlayer().getUniqueId());if(menu!=null) menu.pause();
            }
            @org.bukkit.event.EventHandler public void disable(org.bukkit.event.server.PluginDisableEvent event) {
                if(event.getPlugin()!=plugin) return;
                for(var menu:List.copyOf(active.values())) menu.pause();
                try {drafts.close();} catch(RuntimeException failure) {plugin.getLogger().warning(plain(plugin,"wizard.draft-save-failed"));}
                active.clear();
            }
        },plugin);
    }
    private static String plain(CustomDungeonsPlugin plugin,String key,TagResolver... args) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(key,args));
    }
    static void launch(DungeonListMenu services,String id) {
        if(!validId(id)) {services.tell("invalid-id");return;}
        if(!services.viewerPlayer().hasPermission("customdungeons.admin.edit") || MenuListener.instance().rejectReload(services.viewerPlayer())) return;
        var drafts=services.plugin.getServer().getServicesManager().load(WizardDraftStore.class);
        if(drafts==null) return;
        var current=active(services.viewerPlayer().getUniqueId());
        if(current!=null && current.draft.get().id().equals(id)) {current.open();return;}
        var saved=drafts.get(id);
        if(saved.isEmpty() && services.store.dungeons().containsKey(id)) {MenuListener.instance().messages().send(services.viewerPlayer(),"gui.dungeon.duplicate-id",arg("id",id));return;}
        if(services.busy(id)) {services.tell("busy");return;}
        var menu=new WizardMenu(services.viewerPlayer(),saved.map(WizardDraftStore.Saved::definition).orElseGet(()->services.newDefinition(id)),services,
                saved.map(s->new WizardState(s.step(),s.completed())).orElseGet(()->new WizardState(0,0)));
        if(services.remember(menu)==null) return;
        menu.open();
    }
    void started() {active.put(viewer.getUniqueId(),this);persist();}
    public static Component w(String key,TagResolver... args) {return MenuListener.instance().messages().get("wizard."+key,args);}
    private static TagResolver arg(String key,Object value) {return Placeholder.unparsed(key,String.valueOf(value));}
    @Override protected Component title() {return w("title",arg("step",state.step()+1));}
    @Override protected int preferredRows() {return 6;}
    @Override protected Material borderMaterial() {return Material.LIME_STAINED_GLASS_PANE;}
    @Override boolean canEdit(boolean notify) {
        if(stopped || finishing || !services.plugin.isEnabled() || !viewer.isOnline() || !viewer.hasPermission("customdungeons.admin.edit") || services.store.isReloading()) return false;
        if(services.busy(draft.get().id())) {if(notify) tell("busy");return false;}
        if(outdated()) {if(notify) tell("conflict");return false;}
        boolean locked=locks.tryLock(draft.get().id(),lockOwner);
        if(!locked && notify) tell("locked");return locked;
    }
    @Override boolean saving() {return finishing;}
    @Override void closed() {} // Escape intentionally leaves tools, progress and particles active.
    @Override void confirmDiscard(Runnable next) {if(!finishing){pause();next.run();}}
    @Override void change(Consumer<Values> action) {
        if(!writable()) return;
        super.change(action);reconcile();persist();updateProgress();
    }
    private List<ValidationError> stepErrors(int step) {
        return WizardRules.errors(step,draft.get(),services.store.mobs(),services.store.spawnerPresets());
    }
    private void reconcile() {state.reconcile(i->stepErrors(i).isEmpty());}
    private void persist() {
        var store=services.plugin.getServer().getServicesManager().load(WizardDraftStore.class);
        if(store==null) return;
        store.save(new WizardDraftStore.Saved(draft.get(),state.step(),state.completed())).whenComplete((value,error)->{
            if(error!=null) services.plugin.getLogger().warning(plain(services.plugin,"wizard.draft-save-failed"));
        });
        var previews=services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(previews!=null && !stopped && services.plugin.isEnabled()) previews.wizard(viewer,draft.get());
    }
    private void updateProgress() {
        if(progress==null && active(viewer.getUniqueId())==this && Bukkit.getScoreboardManager()!=null)
            progress=new WizardProgress(viewer,MenuListener.instance().messages(),Bukkit.getScoreboardManager());
        if(progress!=null) progress.update(state);
        if(active(viewer.getUniqueId())==this && announced!=state.step()) {
            announced=state.step();MenuListener.instance().messages().send(viewer,"wizard.instruction-"+state.step());
        }
    }
    private void navigate(Runnable transition) {
        if(!writable()) return;reconcile();transition.run();persist();open();
    }
    private void button(int slot,Material material,String key,Runnable action,Component... extra) {
        var lore=new ArrayList<>(List.of(extra));lore.add(Component.empty());lore.add(w(key+"-lore"));
        set(slot,Button.of(material,w(key),lore,(p,c)->MenuListener.instance().later(()->{if(writable()) action.run();})));
    }
    private void info(int slot,Material material,String key,Component... lore) {set(slot,GuiTheme.information(material,w(key),List.of(lore)));}
    @Override protected void render() {
        reconcile();updateProgress();
        set(4,GuiTheme.information(Material.NETHER_STAR,w("summary",arg("id",draft.get().id())),
                List.of(w("summary-step",arg("step",state.step()+1),Placeholder.component("name",w("step-"+state.step()))),
                        state.step()==6?w("review-counts",arg("errors",stepErrors(6).size()),arg("warnings",reviewWarnings().size())):
                        state.step()==3&&!draft.get().rooms().isEmpty()?w("summary-room",arg("room",Math.min(currentRoom+1,draft.get().rooms().size())),arg("total",draft.get().rooms().size())):w("summary-particles"))));
        for(int i=0;i<7;i++) {
            int target=i;boolean current=i==state.step(),done=i<state.completed();
            Component name=w(current?"current-step":done?"done-step":"locked-step",Placeholder.component("name",w("label-"+i)));
            List<Component> lore=current?List.of(w("current")):done?List.of(w("completed"),Component.empty(),w("return-lore")):List.of(w("locked"));
            set(10+i,done&&!current?Button.of(icons[i],name,lore,(p,c)->MenuListener.instance().later(()->navigate(()->state.visit(target)))):
                    GuiTheme.information(current?icons[i]:Material.GRAY_DYE,name,lore));
        }
        switch(state.step()) {
            case 0 -> area();case 1 -> points();case 2 -> spawners();case 3 -> rooms();case 4 -> rules();case 5 -> reward();case 6 -> review();
            default -> throw new IllegalStateException();
        }
    }
    @Override protected void renderHeader() {
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->w("help-"+state.step()+"-"+i)).toList());
    }
    @Override protected Runnable onSave() {return null;}
    @Override void saveDraft() {if(writable()){persist();MenuListener.instance().messages().send(viewer,"wizard.draft-saved");}}
    @Override protected void renderFooter() {

        if(state.step()==0) set(45,GuiTheme.information(Material.GRAY_DYE,w("back"),List.of(w("first-step"))));
        else button(45,Material.ARROW,"back",()->navigate(state::back));
        set(49,Button.of(Material.BOOK,w("exit"),List.of(w("exit-saved"),w("exit-resume"),Component.empty(),w("exit-lore")),
                (p,c)->MenuListener.instance().later(()->{if(!finishing){pause();viewer.closeInventory();}})));
        String key=state.step()==6?"finish":"next";var errors=stepErrors(state.step());
        if(errors.isEmpty()) button(53,Material.LIME_CONCRETE,key,()->{
            if(state.step()==6) finish(false,false);else navigate(()->state.next(stepErrors(state.step()).isEmpty()));
        });
        else set(53,GuiTheme.information(Material.GRAY_DYE,w(key),List.of(w("unavailable",Placeholder.component("reason",reason(errors))))));
    }
    private Component reason(List<ValidationError> errors) {
        if(state.step()==0) return w("need-area");
        if(state.step()==3) {
            var first=errors.getFirst();int end=first.path().indexOf(']');
            if(first.path().startsWith("rooms[") && end>0) {
                int room=Integer.parseInt(first.path().substring(6,end));
                var fields=errors.stream().filter(e->e.path().startsWith("rooms["+room+"]")).map(e->{
                    String path=e.path().substring(e.path().indexOf(']')+2);
                    return w(path.startsWith("spawners")?"field-spawners":path.equals("region")?"field-region":path.equals("checkpoint")?"field-checkpoint":path.equals("door")?"field-door":"field-rules");
                }).distinct().toList();
                return w("room-incomplete",arg("room",room+1),Placeholder.component("fields",Component.join(net.kyori.adventure.text.JoinConfiguration.separator(w("separator")),fields)));
            }
        }
        return Validator.describe(errors.getFirst(),MenuListener.instance().messages());
    }
    private void area() {
        info(28,Material.WHITE_STAINED_GLASS_PANE,"area",w("area-lore"));
        button(29,Material.BLAZE_ROD,"area-wand",()->{services.tools.give(viewer,ToolType.REGION,draft.get().id());viewer.closeInventory();});
        var selection=services.tools.selection(viewer.getUniqueId()).filter(Selection::complete).orElse(null);
        button(31,Material.LIME_DYE,"area-selection",()->{
            var latest=services.tools.selection(viewer.getUniqueId()).filter(Selection::complete).orElse(null);
            if(latest==null){tell("no-selection");return;}
            change(v->v.area=latest.toRegion());refresh();
        },areaPosition(selection==null?draft.get().area():selection.toRegion()),regionSizeLore(selection==null?draft.get().area():selection.toRegion()));
        button(33,Material.SPYGLASS,"area-view",()->preview());
    }
    private Component areaPosition(Region region) {
        if(region==null) return regionLore(null);
        return w("area-position",arg("pos1",region.min().x()+","+region.min().y()+","+region.min().z()),
                arg("pos2",region.max().x()+","+region.max().y()+","+region.max().z()));
    }
    private void points() {
        pointHere(29,"lobby",draft.get().lobby(),p->change(v->v.lobby=p));
        pointHere(33,"exit",draft.get().exit(),p->change(v->v.exit=p));
        button(31,Material.COMPASS,"point-tool",()->{services.tools.give(viewer,ToolType.POINT,draft.get().id());viewer.closeInventory();});
    }
    private void spawners() {
        button(29,Material.SPAWNER,"spawners",()->new DungeonSpawnerMenu(this).open(),w("presets-count",arg("value",draft.get().spawnerPresets().size())));
        info(33,Material.BOOK,"spawners-info",w("spawners-info-lore"));
    }
    private void rooms() {
        var rooms=draft.get().rooms();int pages=Math.max(1,(rooms.size()+1)/2);roomsPage=Math.clamp(roomsPage,0,pages-1);
        for(int offset=0;offset<2;offset++) {
            int index=roomsPage*2+offset;
            if(index<rooms.size()) {
                var room=rooms.get(index);boolean complete=stepErrors(3).stream().noneMatch(e->e.path().startsWith("rooms["+index+"]"));
                set(28+offset,Button.of(complete?Material.OAK_DOOR:Material.IRON_DOOR,
                        w(complete?"room-complete":"room-error",arg("room",index+1)),
                        List.of(w("room-parts",Placeholder.component("region",w(room.region()!=null?"done-mark":"error-mark")),
                                Placeholder.component("checkpoint",w(room.checkpoint()!=null?"done-mark":"error-mark")),
                                Placeholder.component("door",w(room.door()!=null || index==rooms.size()-1?"done-mark":"error-mark"))),
                                w("spawners-count",arg("value",room.spawners().size())),Component.empty(),w("room-edit-lore")),
                        (p,c)->MenuListener.instance().later(()->{if(writable()){currentRoom=index;new RoomMenu(this,index,this).open();}})));
            }
        }
        button(30,Material.LIME_DYE,"room-add",()->{
            new RoomListMenu(this).create();currentRoom=draft.get().rooms().size()-1;new RoomMenu(this,currentRoom,this).open();
        });
        set(33,Button.of(Material.BREEZE_ROD,w("spawner-place"),List.of(w("spawner-place-detail"),w("spawner-place-detail-2"),
                Component.empty(),w("spawner-place-lore"),w("spawner-place-tool-lore")),(p,c)->MenuListener.instance().later(()->{
            if(!writable()) return;
            if(c.isRightClick()) {services.tools.give(viewer,ToolType.SPAWNER,draft.get().id());viewer.closeInventory();return;}
            var target=viewer.getTargetBlockExact(6);
            var location=target==null?viewer.getLocation():target.getLocation().add(.5,1,.5);
            Point point=new Point(location.getWorld().getName(),location.getX(),location.getY(),location.getZ(),location.getYaw(),location.getPitch());
            for(int i=0;i<draft.get().rooms().size();i++) {
                var region=draft.get().rooms().get(i).region();
                if(region!=null && region.contains(point.world(),location.getBlockX(),location.getBlockY(),location.getBlockZ())) {
                    new SpawnerPickerMenu(this,i,this,point).open();return;
                }
            }
            tell("spawner-outside-room");
        })));
        button(34,Material.SPYGLASS,"rooms-view",this::preview);
        if(pages>1) {
            button(19,Material.SPECTRAL_ARROW,"rooms-previous",()->{roomsPage=Math.max(0,roomsPage-1);refresh();});
            button(25,Material.SPECTRAL_ARROW,"rooms-next",()->{roomsPage=Math.min(pages-1,roomsPage+1);refresh();});
        }
        if(rooms.size()>3) button(22,Material.BOOKSHELF,"rooms-all",()->new RoomListMenu(this).open());
    }
    private void rules() {
        button(29,Material.COMPARATOR,"rules",()->new DungeonSettingsMenu(this).open());
        button(33,Material.ANVIL,"scaling",()->new ScalingMenu(this).open());
        info(31,Material.BOOK,"defaults",w("defaults-lore"));
    }
    private void reward() {
        button(31,Material.CHEST,"reward",()->new RewardMenu(this).open());
        info(29,Material.WHITE_STAINED_GLASS_PANE,"reward-info",w("reward-info-lore"));
    }
    private List<Validator.Warning> reviewWarnings() {
        return stepErrors(6).isEmpty()?new Validator().warnings(SpawnerPresets.resolve(draft.get(),services.store.spawnerPresets()),services.store.mobs()):List.of();
    }
    private void review() {
        var errors=stepErrors(6);var warnings=reviewWarnings();
        set(28,GuiTheme.information(errors.isEmpty()?Material.LIME_STAINED_GLASS_PANE:Material.RED_STAINED_GLASS_PANE,
                w(errors.isEmpty()?"no-errors":errors.size()==1?"error":"errors",arg("value",errors.size())),errors.isEmpty()?List.of(w("validator-ok")):
                        errors.stream().map(e->Validator.describe(e,MenuListener.instance().messages())).toList()));
        set(29,GuiTheme.information(Material.YELLOW_STAINED_GLASS_PANE,w("warnings",arg("value",warnings.size())),warnings.stream().map(e->w("warning-path",arg("path",e.path()),
                Placeholder.component("warning",MenuListener.instance().messages().get(e.messageKey(),e.args().entrySet().stream().map(a->arg(a.getKey(),a.getValue())).toArray(TagResolver[]::new))))).toList()));
        Component unavailable=errors.isEmpty()?null:w("unavailable",Placeholder.component("reason",reason(errors)));
        if(unavailable!=null || !viewer.hasPermission("customdungeons.admin.test"))
            info(31,Material.GRAY_DYE,"test",unavailable!=null?unavailable:w("unavailable",Placeholder.component("reason",msg("control-no-permission"))),Component.empty(),w("test-lore"));
        else button(31,Material.TARGET,"test",()->finish(false,true));
        if(unavailable!=null) info(33,Material.GRAY_DYE,"activate",unavailable,w("activate-detail"),Component.empty(),w("activate-lore"));
        else button(33,Material.LIME_DYE,"activate",()->finish(true,false),w("activate-detail"));
        button(34,Material.COMPARATOR,"full-editor",this::fullEditor);
    }
    private void preview() {
        var previews=services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(previews!=null) previews.wizard(viewer,draft.get());
    }
    void pause() {
        if(stopped) return;
        captureReward();persist();cleanup();services.discard(this);
    }
    private void captureReward() {
        if(viewer.getOpenInventory().getTopInventory().getHolder() instanceof RewardMenu reward && reward.root==this) reward.capture();
    }
    private void cleanup() {
        stopped=true;active.remove(viewer.getUniqueId(),this);
        if(progress!=null){progress.close();progress=null;}
        var previews=services.plugin.getServer().getServicesManager().load(PreviewRenderer.class);
        if(previews!=null) previews.stopWizard(viewer.getUniqueId());
        if(!finishing || !services.plugin.isEnabled()) locks.unlock(draft.get().id(),lockOwner);
    }
    void fullEditor() {
        if(!writable()) return;captureReward();persist();var definition=draft.get();cleanup();services.discard(this);
        var editor=services.remember(new DungeonMenu(viewer,definition,services));if(editor!=null) editor.open();
    }
    private void finish(boolean enable,boolean test) {
        if(!writable() || !stepErrors(6).isEmpty()) {refresh();return;}
        if(test && (!viewer.hasPermission("customdungeons.admin.test") || services.plugin.sessionManager()==null
                || services.plugin.sessionManager().sessionOf(viewer.getUniqueId()).isPresent())) {tell("control-busy");return;}
        var values=new Values(draft.get());values.enabled=enable;DungeonDef saved=values.build();finishing=true;
        // The independent lock owner survives closing the inventory or a quit while the save is pending.
        try {services.store.save(saved).whenComplete((unused,failure)->{
            if(!services.plugin.isEnabled()) return;
            MenuListener.instance().later(()->{
                finishing=false;
                if(stopped) locks.unlock(saved.id(),lockOwner);
                if(failure!=null) {tell("save-failed");if(viewer.isOnline()&&!stopped) open();return;}
                var drafts=services.plugin.getServer().getServicesManager().load(WizardDraftStore.class);
                if(test) {
                    // Keep a resumable draft; testing uses the validated persisted snapshot without prizes.
                    pause();if(viewer.isOnline()) {viewer.closeInventory();services.plugin.sessionManager().startTest(viewer,saved.id());}
                    return;
                }
                drafts.delete(saved.id()).whenComplete((value,error)->{if(error!=null)services.plugin.getLogger().warning(plain(services.plugin,"wizard.draft-save-failed"));});
                cleanup();services.discard(this);if(viewer.isOnline()){tell("saved");viewer.closeInventory();}
            });
        });} catch(RuntimeException failure) {
            finishing=false;tell("save-failed");refresh();
        }
    }
}
