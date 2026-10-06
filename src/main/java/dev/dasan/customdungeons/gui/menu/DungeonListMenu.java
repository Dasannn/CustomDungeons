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

/** Public entry point for T16: new DungeonListMenu(player).open(). */
public final class DungeonListMenu extends DungeonPage<DungeonDef> {
    private static final Map<UUID,DungeonMenu> editors = new HashMap<>();
    private static Predicate<String> dungeonBusy = id -> false;
    final dev.dasan.customdungeons.CustomDungeonsPlugin plugin;
    final DefinitionStore store;
    final ToolService tools;
    final SpawnerMarkers markers;
    private final boolean listView;
    private final Menu previous;

    /** T09/T16 must supply a predicate covering lobby, running and reset sessions. */
    public static void dungeonBusy(Predicate<String> predicate) { dungeonBusy=Objects.requireNonNull(predicate); }
    boolean presetBusy(String id) {
        return SpawnerPresets.usage(id,store.dungeons()).stream().anyMatch(u -> busy(u.dungeonId()));
    }
    boolean presetDirty(String id) {
        return editors.values().stream().anyMatch(e -> e.dirty() && (e.draft.get().spawnerPresets().contains(id)
                || !SpawnerPresets.usage(id,Map.of(e.draft.get().id(),e.draft.get())).isEmpty()));
    }
    boolean busy(String id) {return dungeonBusy.test(id);}
    public DungeonListMenu(Player player) {
        this(player,false,null);
    }
    DungeonListMenu(Player player,boolean listView,Menu previous) {
        super(player,listView?"list":"main",previous);
        this.listView=listView; this.previous=previous;
        plugin=org.bukkit.plugin.java.JavaPlugin.getPlugin(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var services=plugin.getServer().getServicesManager();
        store=Objects.requireNonNull(services.load(DefinitionStore.class));
        tools=Objects.requireNonNull(services.load(ToolService.class));
        markers=Objects.requireNonNull(services.load(SpawnerMarkers.class));
    }
    public static void register(dev.dasan.customdungeons.CustomDungeonsPlugin plugin) {
        var markers=Objects.requireNonNull(plugin.getServer().getServicesManager().load(SpawnerMarkers.class));
        var tools=Objects.requireNonNull(plugin.getServer().getServicesManager().load(ToolService.class));
        tools.onPlateEdit((player,id)->{
            var menu=editors.get(player.getUniqueId());
            if(menu==null || !id.isEmpty() && !menu.draft.get().id().equals(id) || !menu.writable())return null;
            return new ToolService.PlateEditor() {
                public DungeonDef definition(){return menu.draft.get();}
                public boolean update(List<Point> points){
                    if(editors.get(player.getUniqueId())!=menu || !menu.writable())return false;
                    menu.change(v->v.plates=points);return menu.draft.get().plates().equals(points);
                }
                public boolean updateExit(List<Point> points){
                    if(editors.get(player.getUniqueId())!=menu || !menu.writable())return false;
                    menu.change(v->v.exitPlates=points);return menu.draft.get().exitPlates().equals(points);
                }
            };
        },()->{
            var definitions=new ArrayList<>(Objects.requireNonNull(plugin.getServer().getServicesManager().load(DefinitionStore.class)).dungeons().values());
            editors.values().forEach(e->definitions.add(e.draft.get()));return definitions;
        });
        markers.onEdit((player,dungeonId,spawnerId)->{
            var list=new DungeonListMenu(player);
            var menu=list.editor(dungeonId);
            if(menu==null||!menu.writable()) return;
            for(int r=0;r<menu.draft.get().rooms().size();r++) {
                var room=menu.draft.get().rooms().get(r);
                for(int s=0;s<room.spawners().size();s++) if(room.spawners().get(s).id().equals(spawnerId)) {
                    new SpawnerMenu(menu,r,s,new RoomMenu(menu,r,new RoomListMenu(menu))).open();return;
                }
            }
        });
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
            public void place(org.bukkit.event.player.PlayerInteractEvent event) {
                if(event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND || event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null
                        || !event.getPlayer().hasPermission("customdungeons.admin.tools")) return;
                var tools=plugin.getServer().getServicesManager().load(ToolService.class);
                var item=event.getItem();
                if(tools==null || item==null || !item.hasItemMeta()) return;
                var tag=item.getItemMeta().getPersistentDataContainer().get(dev.dasan.customdungeons.mob.MobKeys.TOOL,org.bukkit.persistence.PersistentDataType.STRING);
                if(tag==null || !tag.startsWith("SPAWNER:")) return;
                String bound=tag.substring("SPAWNER:".length());
                var player=event.getPlayer();
                MenuListener.instance().later(() -> {
                    var point=tools.lastPoint(player.getUniqueId()).orElse(null);
                    var menu=editors.get(player.getUniqueId());
                    if(point==null || menu==null || !menu.writable()) return;
                    if(!bound.isEmpty() && !bound.equals(menu.draft.get().id())) return;
                    for(int r=0;r<menu.draft.get().rooms().size();r++) {
                        var room=menu.draft.get().rooms().get(r);
                        if(room.region()!=null && room.region().contains(point.getWorld().getName(),point.getBlockX(),point.getBlockY(),point.getBlockZ())) {
                            var p=new Point(point.getWorld().getName(),point.getX(),point.getY(),point.getZ(),point.getYaw(),point.getPitch());
                            new SpawnerPickerMenu(menu,r,menu instanceof WizardMenu?menu:new RoomMenu(menu,r,new RoomListMenu(menu)),p).open(); return;
                        }
                    }
                    menu.tell("spawner-outside-room");
                });
            }
            @org.bukkit.event.EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
                DungeonMenu menu=editors.remove(event.getPlayer().getUniqueId());
                if(menu!=null) markers.hide(menu.draft.get().id());
            }
            @org.bukkit.event.EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent event) {
                if(event.getInventory().getHolder() instanceof Menu owner && owner.getInventory()!=event.getInventory()) return;
                if(event.getInventory().getHolder() instanceof RewardMenu reward)
                    reward.capture(event.getReason()==org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH);
                // Preset contexts bind their own close handler to each root/child inventory.
                if(event.getInventory().getHolder() instanceof DungeonMenu menu && !(menu instanceof SpawnerPresetMenu)) menu.closed();
            }
            @org.bukkit.event.EventHandler public void disable(org.bukkit.event.server.PluginDisableEvent event) {
                if(event.getPlugin()==plugin) {editors.clear();dungeonBusy=id->false;}
            }
        },plugin);
    }
    DungeonMenu editor(String id) {
        DungeonMenu old=editors.get(viewer.getUniqueId());
        if(old!=null&&old.saving()) {tell("busy");return null;}
        if(old!=null&&old.draft.get().id().equals(id)) {
            if(!old.outdated()) return old;
            old.confirmDiscard(()->{
                DungeonDef latest=DungeonMenu.latestDefinition(this,id);
                if(latest==null) {open();return;}
                DungeonMenu replacement=remember(new DungeonMenu(viewer,latest,this));
                if(replacement!=null) replacement.open(); else open();
            });
            return null;
        }
        DungeonDef definition=DungeonMenu.latestDefinition(this,id);
        if(definition==null) return null;
        if(old!=null&&old.dirty()) {
            old.confirmDiscard(()->{
                DungeonMenu menu=editor(id);
                if(menu!=null) menu.open(); else open();
            });
            return null;
        }
        return remember(new DungeonMenu(viewer,definition,this));
    }
    boolean current(DungeonMenu menu) {return editors.get(viewer.getUniqueId())==menu;}
    boolean enterBuild(BuildMenu menu,DungeonMenu source) {
        if(!current(source)||source.saving()||!source.writable()) return false;
        var locks=MenuListener.instance().editLocks();
        locks.unlock(source.draft.get().id(),viewer.getUniqueId());
        if(!locks.tryLock(menu.definition().id(),menu.lockOwner())) {
            locks.tryLock(source.draft.get().id(),viewer.getUniqueId());return false;
        }
        editors.put(viewer.getUniqueId(),menu);return true;
    }
    void discard(DungeonMenu menu) {
        // All assistant exits, including replacement, pass through this session boundary.
        if(menu.saving() && !(menu instanceof WizardMenu) && !(menu instanceof BuildMenu)) return;
        if(menu instanceof WizardMenu wizard) wizard.sessionClosed();
        if(!editors.remove(viewer.getUniqueId(),menu)) return;
        MenuListener.instance().editLocks().unlock(menu.draft.get().id(),viewer.getUniqueId());
        markers.hide(menu.draft.get().id());
    }
    DungeonMenu remember(DungeonMenu menu) {
        DungeonMenu old=editors.get(viewer.getUniqueId());
        if(old==menu) return menu.writable()?menu:null;
        if(old!=null&&old.saving()) {tell("busy");return null;}
        // A wizard owns a separate lock and HUD even when its definition is clean.
        // Release it before checking the new editor's lock, including the same dungeon.
        if(old instanceof WizardMenu wizard) {wizard.pause();old=null;}
        if(old!=null&&old.dirty()) {
            old.confirmDiscard(()->{
                DungeonMenu replacement=remember(menu);
                if(replacement!=null) replacement.open(); else open();
            });
            return null;
        }
        if(!menu.writable()) return null;
        if(old!=null) {
            if(!old.draft.get().id().equals(menu.draft.get().id()))
                MenuListener.instance().editLocks().unlock(old.draft.get().id(),viewer.getUniqueId());
            markers.hide(old.draft.get().id());
        }
        editors.put(viewer.getUniqueId(),menu);
        if(menu instanceof WizardMenu wizard) wizard.sessionStarted();
        return menu;
    }
    @Override protected int preferredRows() {return listView?6:3;}
    @Override protected Menu parent() {return previous;}
    @Override protected boolean canCreate() {return listView;}
    @Override protected boolean hasPreviousPage() {return listView&&super.hasPreviousPage();}
    @Override protected boolean hasNextPage() {return listView&&super.hasNextPage();}
    @Override protected String createKey() {return "new-dungeon";}
    @Override protected void render() {
        if(listView) super.render();
        summary(listView?Material.BOOKSHELF:Material.NETHER_STAR,msg("main-summary",Placeholder.unparsed("dungeons",Integer.toString(entries().size())),
                Placeholder.unparsed("mobs",Integer.toString(store.mobs().size())),Placeholder.unparsed("spawners",Integer.toString(store.spawnerPresets().size()))),msg(listView?"list-heading-lore":"main-summary-lore"));
        if(!listView) {
            add(10,"created-dungeons",Material.BOOKSHELF,()->new DungeonListMenu(viewer,true,this).open());
            add(12,"new-wizard",Material.LIME_DYE,()->Inputs.text(viewer,msg("new-id"),"",32,this::openWizard));
            var messages=MenuListener.instance().messages();
            set(14,Button.of(Material.BOOK,messages.get("gui.common.mob-library"),
                    List.of(SpawnerLibraryMenu.m("count",Placeholder.unparsed("value",Integer.toString(store.mobs().size()))),Component.empty(),SpawnerLibraryMenu.m("main-mobs-lore")),
                    (p,c)->MenuListener.instance().later(()->new MobLibraryMenu(p,this).open())));
            set(16,Button.of(Material.SPAWNER,SpawnerLibraryMenu.m("library"),
                    List.of(SpawnerLibraryMenu.m("count",Placeholder.unparsed("value",Integer.toString(store.spawnerPresets().size()))),Component.empty(),SpawnerLibraryMenu.m("library-lore")),
                    (p,c)->MenuListener.instance().later(()->new SpawnerLibraryMenu(this,this,null).open())));
        }
    }
    @Override protected void renderHeader() {
        if(listView) {super.renderHeader();return;}
        GuiTheme.help(this,List.of(msg("help-main-1"),msg("help-main-2")));
    }
    @Override protected List<DungeonDef> entries() {
        var definitions=new HashMap<>(store.dungeons());
        var drafts=plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
        if(drafts!=null) drafts.all().forEach((id,saved)->definitions.putIfAbsent(id,saved.definition()));
        var current=editors.get(viewer.getUniqueId());
        if(current!=null) definitions.put(current.draft.get().id(),current.draft.get());
        return definitions.values().stream().sorted(Comparator.comparing(DungeonDef::id)).toList();
    }
    @Override protected Button entry(DungeonDef value,int index) {
        boolean occupied=busy(value.id());
        String state=occupied?"state-busy":value.enabled()?"state-enabled":"state-disabled";
        var errors=new Validator().validate(value,store.mobs(),store.spawnerPresets());
        var lore=List.of(msg("dungeon-summary",Placeholder.unparsed("rooms",Integer.toString(value.rooms().size())),
                Placeholder.unparsed("minimum",Integer.toString(value.minPlayers())),
                Placeholder.component("maximum",value.maxPlayers()==0?msg("unlimited"):Component.text(value.maxPlayers())),
                Placeholder.component("state",msg(state))),
                msg("error-count",Placeholder.unparsed("value",Integer.toString(errors.size()))),Component.empty(),msg("dungeon-lore"),
                wizardDraft(value.id())?WizardMenu.w("continue-lore"):Component.empty());
        return Button.of(occupied?Material.ORANGE_CONCRETE:!errors.isEmpty()?Material.RED_CONCRETE:value.enabled()?Material.LIME_CONCRETE:Material.GRAY_CONCRETE,
                wizardDraft(value.id())?WizardMenu.w("continue-label",Placeholder.unparsed("id",value.id())):
                msg("dungeon-label",Placeholder.unparsed("id",value.id()),Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(value.displayName()))),lore,
                (p,c)->MenuListener.instance().later(()->{
            if(busy(value.id())) {
                DungeonDef latest=store.dungeons().get(value.id());
                if(latest==null) {tell("control-invalid");return;}
                // Controls never enter the editor registry or acquire an edit lock.
                MenuListener.instance().editLocks().unlock(value.id(),viewer.getUniqueId());
                new DungeonMenu(viewer,latest,this,true).open();
            } else {
                if(wizardDraft(value.id()) && !c.isRightClick()) {openWizard(value.id());return;}
                if(wizardDraft(value.id()) && c.isRightClick()) {openWizard(value.id());var active=WizardMenu.active(viewer.getUniqueId());if(active!=null&&active.draft.get().id().equals(value.id())) active.fullEditor();return;}
                DungeonMenu menu=editor(value.id());if(menu!=null&&menu.writable()) menu.open();
            }
        }));
    }
    private boolean wizardDraft(String id) {
        var drafts=plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
        return drafts!=null && drafts.get(id).isPresent();
    }
    public void openWizard(String id) {WizardMenu.launch(this,id);}
    DungeonDef newDefinition(String id) {
        var defaults=plugin.getServer().getServicesManager().load(PluginConfig.class).defaults();
        return new DungeonDef(id,id,false,null,null,defaults.minPlayers(),defaults.maxPlayers(),
                defaults.lobbyCountdownSeconds(),defaults.lives(),defaults.keepInventory(),0,defaults.cooldownSeconds(),
                false,defaults.scaling(),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of())
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10);
    }
    @Override protected void create() {
        Inputs.text(viewer,msg("new-id"),"",32,id->{
            if(!DungeonMenu.validId(id)) {tell("invalid-id");return;}
            if(store.dungeons().containsKey(id)) {MenuListener.instance().messages().send(viewer,"gui.dungeon.duplicate-id",Placeholder.unparsed("id",id));return;}
            if(wizardDraft(id)) {openWizard(id);var wizard=WizardMenu.active(viewer.getUniqueId());if(wizard!=null&&wizard.draft.get().id().equals(id))wizard.fullEditor();return;}
            var current=editors.get(viewer.getUniqueId());
            if(current!=null&&current.draft.get().id().equals(id)) {if(current.writable()) current.open();return;}
            var defaults=plugin.getServer().getServicesManager().load(PluginConfig.class).defaults();
            var definition=new DungeonDef(id,id,false,null,null,defaults.minPlayers(),defaults.maxPlayers(),
                    defaults.lobbyCountdownSeconds(),defaults.lives(),defaults.keepInventory(),0,defaults.cooldownSeconds(),
                    false,defaults.scaling(),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of())
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10);
            var menu=remember(new DungeonMenu(viewer,definition,this));if(menu!=null) menu.open();
        });
    }
}
