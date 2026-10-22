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

/** Dungeon editor, or a control-only view without a draft or edit lock for active runs. */
public class DungeonMenu extends DungeonEditor {
    @org.jetbrains.annotations.Nullable final Draft<DungeonDef> draft;
    final DungeonListMenu services;
    final DungeonListMenu list;
    private final boolean controlOnly;
    private final String dungeonId;
    private boolean saving;
    boolean lootPending;
    java.util.concurrent.CompletableFuture<Void> lootDraftWrite=java.util.concurrent.CompletableFuture.completedFuture(null);
    private UUID lootOwner;
    UUID lockOwner() {return viewer.getUniqueId();}
    final UUID currentLockOwner() {return lootPending?lootOwner:lockOwner();}
    final void beginLoot() {
        var locks=MenuListener.instance().editLocks();String id=draft.get().id();
        locks.unlock(id,lockOwner());lootOwner=UUID.randomUUID();
        if(!locks.tryLock(id,lootOwner))throw new IllegalStateException("Loot edit lock unavailable");
        lootPending=true;
    }
    final void endLoot() {
        if(!lootPending)return;
        MenuListener.instance().editLocks().unlock(draft.get().id(),lootOwner);lootPending=false;lootOwner=null;
        if(services.plugin.isEnabled()&&viewer.isOnline()&&list.current(this))canEdit(false);
    }
    void reconcileLoot() {
        var manager=services.plugin.sessionManager();
        if(manager!=null&&manager.loot()!=null&&!services.store.deleting(dungeonId)&&services.plugin.isEnabled())manager.loot().reconcile(dungeonId,
                dev.dasan.customdungeons.session.SessionManager.lootDefinitions(services.plugin,services.store));
    }
    void disconnected() {
        if(!list.current(this))return;
        list.discard(this);
    }
    private void abandon(Runnable accept) {
        boolean placed=draft.get().rooms().stream().flatMap(r->r.lootChests().stream()).anyMatch(c->c.location()!=null);
        if(!placed) {accept.run();return;}
        LootPlacement.reconcile(this,accept);
    }
    void removeRoom(String id,Runnable done) {
        if(!writable()||lootPending)return;
        var v=new Values(draft.get());v.rooms=v.rooms.stream().filter(r->!r.id().equals(id)).toList();var target=v.build();
        if(!LootPlacement.changed(draft.get(),target)) {applyChange(target);done.run();return;}
        if(!viewer.hasPermission("customdungeons.admin.tools"))return;
        var wizard=services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
        boolean durable=this instanceof BuildMenu||this instanceof WizardMenu||wizard!=null&&wizard.get(dungeonId).isPresent();
        if(durable)applyChange(target);
        LootPlacement.reconcile(this,()->{if(!writable())return;if(!durable)applyChange(target);done.run();});
    }
    private boolean confirmingDiscard;
    private DungeonDef persisted;
    private WorkingVersion expected;
    private record WorkingVersion(DungeonDef definition, boolean wizardDraft) {}
    private List<ValidationError> errors = List.of();
    private List<Validator.Warning> warnings = List.of();

    public DungeonMenu(Player player, DungeonDef definition, DungeonListMenu list) {
        this(player,definition,list,false);
    }
    DungeonMenu(Player player, DungeonDef definition, DungeonListMenu list, boolean controlOnly) {
        this(player,definition,list,controlOnly,"title");
    }
    DungeonMenu(Player player,DungeonDef definition,DungeonListMenu list,boolean controlOnly,String title) {
        this(player,definition,list,controlOnly,title,title.equals("title")?msg("dungeon-title",Placeholder.component("name",dev.dasan.customdungeons.text.Text.parse(definition.displayName()))):msg(title));
    }
    DungeonMenu(Player player,DungeonDef definition,DungeonListMenu list,boolean controlOnly,String category,Component title) {
        super(player,category,title,null,list);
        this.controlOnly=controlOnly;
        this.dungeonId=definition.id();
        this.root = this;
        this.draft = controlOnly ? null : new Draft<>(definition);
        this.services = list;
        this.list = list;
        this.persisted = list.store.dungeons().get(definition.id());
        this.expected = workingVersion(list,definition.id());
    }
    public @org.jetbrains.annotations.Nullable Draft<DungeonDef> draft() { return draft; }
    boolean dirty() { return !controlOnly && !Objects.equals(persisted, draft.get()); }
    boolean saving() { return saving||lootPending; }
    void confirmDiscard(Runnable next) {
        if (services.store.isReloading()) return;
        if (saving||lootPending) { tell("busy"); return; }
        confirmingDiscard = true;
        try {
            // Inputs restores its origin on Cancel, including after Escape or Back.
            open();
            Inputs.confirm(viewer, msg("discard-conflict"), () -> {
                if (saving || !list.current(this)) return;
                abandon(()->{list.discard(this);next.run();});
            });
        } finally { confirmingDiscard = false; }
    }
    void closed() {
        if (controlOnly || confirmingDiscard || services.store.isReloading() || !services.plugin.isEnabled() || !viewer.isOnline()) return;
        MenuListener.instance().later(() -> {
            if (services.store.isReloading() || !list.current(this) || saving || !dirty() || !viewer.isOnline()) return;
            var holder = viewer.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof DungeonEditor editor && editor.root == this) return;
            if (holder instanceof DungeonMenu menu && menu.controlOnly) return;
            boolean returningToList = holder == list;
            confirmDiscard(() -> {
                list.open();
                if (!returningToList) MenuListener.instance().later(viewer::closeInventory);
            });
        });
    }
    private static WorkingVersion workingVersion(DungeonListMenu services,String id) {
        var drafts=services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
        var saved=drafts==null?Optional.<dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved>empty():drafts.get(id);
        return saved.map(s->new WorkingVersion(s.definition(),true))
                .orElseGet(()->new WorkingVersion(services.store.dungeons().get(id),false));
    }
    static DungeonDef latestDefinition(DungeonListMenu services,String id) {return workingVersion(services,id).definition();}
    final void acceptWorkingVersion() {expected=workingVersion(services,draft.get().id());}
    boolean outdated() { return !controlOnly && !Objects.equals(expected,workingVersion(services,draft.get().id())); }
    boolean writable() { return canEdit(true); }
    boolean canEdit(boolean notify) {
        if (controlOnly) {if(notify) tell("control-edit-blocked"); return false;}
        if (!viewer.isOnline() || !viewer.hasPermission("customdungeons.admin.edit")) return false;
        if (services.store.isReloading() || saving || services.busy(draft.get().id())) { if (notify) tell("busy"); return false; }
        if (!MenuListener.instance().editLocks().tryLock(draft.get().id(), currentLockOwner())) {
            if (notify) tell("locked"); return false;
        }
        if (outdated()) {
            MenuListener.instance().editLocks().unlock(draft.get().id(), currentLockOwner());
            if (notify) tell("conflict"); return false;
        }
        return true;
    }
    void change(Consumer<Values> action) {
        if (!writable() || lootPending) return;
        Values values = new Values(draft.get());
        action.accept(values);
        applyChange(values.build());
    }
    /** Completes an already authorized mutation after its durable receipt; no world access here. */
    void applyChange(DungeonDef definition) {
        draft.set(definition);
        // Advanced editing of a resumable assistant draft must preserve those edits too.
        if(!(this instanceof WizardMenu) && !(this instanceof SpawnerPresetMenu)) {
            var wizardDrafts=services.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
            if(wizardDrafts!=null) wizardDrafts.get(draft.get().id()).ifPresent(saved->{
                lootDraftWrite=wizardDrafts.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(draft.get(),saved.step(),saved.completed()));
                acceptWorkingVersion();
            });
        }
        errors = List.of();
        warnings = List.of();
    }
    void room(int index, UnaryOperator<RoomDef> action) {
        change(v -> v.rooms = replace(v.rooms, index, action.apply(v.rooms.get(index))));
    }
    static RoomDef withLoot(RoomDef r,List<LootChestDef> chests) {
        return new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners(),r.openingMode(),r.ambience(),chests);
    }
    void loot(String room,String chest,UnaryOperator<LootChestDef> action) {
        int index=LootEditor.roomNumber(this,room)-1;
        room(index,r->withLoot(r,r.lootChests().stream().map(c->c.id().equals(chest)?action.apply(c):c).toList()));
    }
    void editLootPlacement(String room,String chest,Point point,boolean delete,Runnable done) {
        if(!writable()||lootPending||!viewer.hasPermission("customdungeons.admin.tools"))return;
        var values=new Values(draft.get());int index=LootEditor.roomNumber(this,room)-1;
        var r=values.rooms.get(index);
        var items=r.lootChests().stream().filter(c->!delete||!c.id().equals(chest)).map(c->c.id().equals(chest)?new LootChestDef(c.id(),point,c.trigger(),c.entries()):c).toList();
        values.rooms=replace(values.rooms,index,withLoot(r,items));var target=values.build();
        if(!LootPlacement.changed(draft.get(),target)) {change(v->v.rooms=target.rooms());done.run();return;}
        LootPlacement.apply(this,target,()->{applyChange(target);if(viewer.isOnline())done.run();});
    }
    void zone(String id,UnaryOperator<OptionalZoneDef> action) {
        change(v->{int index=EditOrigin.zone(id).index(v.build());v.optionalZones=replace(v.optionalZones,index,action.apply(v.optionalZones.get(index)));});
    }
    void spawners(EditOrigin origin,UnaryOperator<List<SpawnerDef>> action) {change(v->spawners(v,origin,action));}
    static void spawners(Values values,EditOrigin origin,UnaryOperator<List<SpawnerDef>> action) {
        int index=origin.index(values.build());
        if(origin.kind()==EditOrigin.Kind.ZONE) {
            var zone=values.optionalZones.get(index);
            values.optionalZones=replace(values.optionalZones,index,new OptionalZoneDef(zone.id(),zone.region(),action.apply(zone.spawners())));
        } else {
            var room=values.rooms.get(index);
            values.rooms=replace(values.rooms,index,new RoomDef(room.id(),room.region(),room.checkpoint(),room.door(),room.unlock(),room.keyCarrierTemplateId(),action.apply(room.spawners()),room.openingMode(),room.ambience(),room.lootChests()));
        }
    }
    void spawner(int room,int index,UnaryOperator<SpawnerDef> action) {spawner(EditOrigin.room(draft.get(),room),index,action);}
    void spawner(EditOrigin origin,int index,UnaryOperator<SpawnerDef> action) {
        spawners(origin,values->replace(values,index,action.apply(values.get(index))));
    }
    void wave(int room,int spawner,int index,UnaryOperator<WaveDef> action) {wave(EditOrigin.room(draft.get(),room),spawner,index,action);}
    void wave(EditOrigin origin,int spawner,int index,UnaryOperator<WaveDef> action) {
        if(origin.spawners(draft.get()).get(spawner).presetId()!=null) return;
        spawner(origin,spawner,s->new SpawnerDef(s.id(),s.location(),s.radius(),replace(s.waves(),index,action.apply(s.waves().get(index))),s.presetId()));
    }
    static <T> List<T> replace(List<T> source, int index, T value) {
        var copy = new ArrayList<>(source); copy.set(index, value); return List.copyOf(copy);
    }
    static <T> List<T> remove(List<T> source, int index) {
        var copy = new ArrayList<>(source); copy.remove(index); return List.copyOf(copy);
    }
    static <T> List<T> append(List<T> source, T value) {
        var copy = new ArrayList<>(source); copy.add(value); return List.copyOf(copy);
    }
    static boolean validId(String id) { return id != null && id.matches("[a-z0-9_-]{1,32}"); }
    static String nextId(String prefix, Collection<String> used) {
        int i = 1; while (used.contains(prefix + i)) i++; return prefix + i;
    }
    @Override protected void render() {
        var d = controlOnly ? services.store.dungeons().get(dungeonId) : draft.get();
        if (d == null) return;
        summary(Material.MAP, dev.dasan.customdungeons.text.Text.parse(d.displayName()),
                SpawnerLibraryMenu.m("dungeon-ready",SpawnerLibraryMenu.arg("value",d.spawnerPresets().size())),
                SpawnerLibraryMenu.m("rooms-ready",SpawnerLibraryMenu.arg("value",d.rooms().size())),
                status("lobby-exit",d.lobby()!=null && d.exit()!=null),status("reward",d.reward()!=null),
                status("start-status",d.startMode()==StartMode.AUTO || !d.plates().isEmpty() && d.entranceDoor()!=null),
                msg("error-count",Placeholder.unparsed("value",Integer.toString(new Validator().validate(d,services.store.mobs(),services.store.spawnerPresets()).size()))));
        section(10,"section-structure",Material.ORANGE_STAINED_GLASS_PANE);
        section(12,"section-rules",Material.ORANGE_STAINED_GLASS_PANE);
        section(14,"section-points",Material.ORANGE_STAINED_GLASS_PANE);
        section(16,"section-session",Material.ORANGE_STAINED_GLASS_PANE);
        if (controlOnly) {
            blocked(51,"dungeon-presets"); blocked(19,"rooms"); blocked(28,"zones"); blocked(37,"reward");
            blocked(21,"settings"); blocked(30,"scaling"); blocked(39,"hooks");
            blocked(23,"lobby-here"); blocked(32,"exit-here"); blocked(41,"start-link");
        } else {
            set(51,action("dungeon-presets",Material.SPAWNER,d.spawnerPresets().size(),(p,c)->MenuListener.instance().later(()->new DungeonSpawnerMenu(this).open()),
                    Component.join(net.kyori.adventure.text.JoinConfiguration.separator(SpawnerLibraryMenu.m("separator")),d.spawnerPresets().stream()
                            .map(id -> {var preset=services.store.spawnerPresets().get(id);return preset==null?SpawnerLibraryMenu.m("missing",SpawnerLibraryMenu.arg("id",id)):SpawnerLibraryMenu.label(preset);}).toList())));
            set(19,action("rooms-link",Material.OAK_DOOR,d.rooms().size(),(p,c)->MenuListener.instance().later(()->new RoomListMenu(this).open())));
            set(28,action("zones",Material.MAP,d.optionalZones().size(),(p,c)->MenuListener.instance().later(()->{if(writable())new OptionalZoneListMenu(this,this).open();})));
            add(37,"reward",Material.CHEST,()->new RewardMenu(this).open());
            add(21,"settings",Material.COMPARATOR,()->new DungeonSettingsMenu(this).open(),
                    SpawnerLibraryMenu.m("settings-summary",Placeholder.component("state",msg(d.enabled()?"enabled-active":"enabled-inactive")),SpawnerLibraryMenu.arg("min",d.minPlayers()),
                            Placeholder.component("max",d.maxPlayers()==0?msg("unlimited"):Component.text(d.maxPlayers()))));
            add(30,"scaling",Material.ANVIL,()->new ScalingMenu(this).open());
            add(39,"hooks",Material.COMMAND_BLOCK,()->new HooksMenu(this).open());
            pointHere(23,"lobby",d.lobby(),p->change(v->v.lobby=p));
            pointHere(32,"exit",d.exit(),p->change(v->v.exit=p));
            add(41,"start-link",Material.LEVER,()->new StartSettingsMenu(this).open(),StartSettingsMenu.overview(d));
        }
        control(25,"start",Material.LIME_CONCRETE,"customdungeons.admin.control");
        control(34,"test",Material.TARGET,"customdungeons.admin.test");
        if(services.plugin.sessionManager()!=null&&services.plugin.sessionManager().session(d.id()).isPresent()) control(43,"stop",Material.RED_CONCRETE,"customdungeons.admin.control");
        var loadWarnings=services.store.loadWarnings("dungeons",dungeonId);
        if (!errors.isEmpty() || !warnings.isEmpty() || !loadWarnings.isEmpty()) {
            var lore = new ArrayList<Component>(LoadWarnings.lore(loadWarnings));
            for (var error : errors) {
                var args = error.args().entrySet().stream().map(e -> Placeholder.unparsed(e.getKey(), e.getValue()))
                        .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
                lore.add(msg("error-line", Placeholder.unparsed("path", error.path()),
                        Placeholder.component("error", MenuListener.instance().messages().get(error.messageKey(), args))));
            }
            for (var warning : warnings) {
                var args = warning.args().entrySet().stream().map(e -> Placeholder.unparsed(e.getKey(),
                        e.getKey().equals("mob") ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(dev.dasan.customdungeons.text.Text.parse(e.getValue())) : e.getValue()))
                        .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
                lore.add(msg("warning-line", Placeholder.unparsed("path", warning.path()),
                        Placeholder.component("warning", MenuListener.instance().messages().get(warning.messageKey(), args))));
            }
            set(40, Button.of(errors.isEmpty() ? Material.YELLOW_DYE : Material.RED_DYE,
                    msg(errors.isEmpty() ? "warnings" : "errors"), lore, (p,c) -> {}));
        }
    }
    @Override protected void renderHeader() {
        GuiTheme.help(this,java.util.stream.IntStream.rangeClosed(1,4).mapToObj(i->msg("help-zoned-dungeon-"+i)).toList());
        tintName(8,net.kyori.adventure.text.format.NamedTextColor.GOLD);
        for(int slot:new int[]{4,19,28,37,39}) tintName(slot,net.kyori.adventure.text.format.NamedTextColor.GOLD);
        var d=controlOnly?services.store.dungeons().get(dungeonId):draft.get();if(d==null)return;
        var issues=new Validator().validate(d,services.store.mobs(),services.store.spawnerPresets());
        long missing=java.util.stream.IntStream.range(0,d.optionalZones().size()).filter(i->issues.stream().anyMatch(e->e.path().startsWith("optional-zones["+i+"]"))).count();
        lore(4,msg("root-room-total",Placeholder.unparsed("count",Integer.toString(d.rooms().size()))),
                msg("root-zone-total",Placeholder.component("mark",msg(missing==0?"zone-mark-ready":"zone-mark-missing")),Placeholder.unparsed("complete",Long.toString(d.optionalZones().size()-missing)),Placeholder.unparsed("missing",Long.toString(missing))),
                msg("root-points-reward",Placeholder.component("points",status("lobby-exit",d.lobby()!=null&&d.exit()!=null)),Placeholder.component("reward",status("reward",d.reward()!=null))),
                msg(dirty()?"root-draft-state":"root-saved-state",Placeholder.component("state",msg(d.enabled()?"enabled-active":"enabled-inactive"))));
        for(int slot:new int[]{10,12,14,16})lore(slot);
        if(controlOnly)return;
        lore(19,msg("root-rooms",Placeholder.unparsed("count",Integer.toString(d.rooms().size())),Placeholder.unparsed("spawners",Integer.toString(d.rooms().stream().mapToInt(r->r.spawners().size()).sum()))),Component.empty(),msg("root-open-rooms"));
        lore(21,msg("root-settings",Placeholder.component("state",msg(d.enabled()?"enabled-active":"enabled-inactive")),Placeholder.unparsed("min",Integer.toString(d.minPlayers())),Placeholder.component("max",d.maxPlayers()==0?msg("unlimited"):Component.text(d.maxPlayers()))),Component.empty(),msg("root-open-settings"));
        for(int slot:new int[]{23,32})lore(slot,compactPoint(slot==23?d.lobby():d.exit()),Component.empty(),msg("root-edit"));
        var names=new ArrayList<Component>();for(int i=0;i<d.optionalZones().size();i++) {
            int index=i;var zone=d.optionalZones().get(i);names.add(msg(issues.stream().noneMatch(e->e.path().startsWith("optional-zones["+index+"]"))?"zone-ready":"zone-missing",Placeholder.unparsed("value",zone.id())));
        }
        var zones=Component.join(net.kyori.adventure.text.JoinConfiguration.separator(msg("root-separator")),names);
        lore(28,zones,Component.empty(),msg("zones-lore"));
        lore(30,msg("root-scaling"),Component.empty(),msg("root-open-scaling"));
        lore(37,msg("root-reward"),Component.empty(),msg("root-open-reward"));
        lore(39,msg("root-hooks"),Component.empty(),msg("root-edit"));
        lore(41,msg("root-start-finish",Placeholder.component("start",StartSettingsMenu.mode(d)),Placeholder.component("finish",msg("finish-mode-"+d.finishMode().name().toLowerCase(Locale.ROOT))),Placeholder.unparsed("mode",d.finishMode().name())),Component.empty(),msg("root-edit"));
        lore(51,Component.join(net.kyori.adventure.text.JoinConfiguration.separator(msg("root-separator")),d.spawnerPresets().stream().map(id->{var preset=services.store.spawnerPresets().get(id);return preset==null?Component.text(id):SpawnerLibraryMenu.label(preset);}).toList()),Component.empty(),msg("root-open-presets"));
    }
    private void blocked(int slot,String key) {
        var d=services.store.dungeons().get(dungeonId);
        Component name=key.equals("rooms")?msg("rooms-link",Placeholder.unparsed("value",Integer.toString(d==null?0:d.rooms().size()))):
                key.equals("zones")?msg(key,Placeholder.unparsed("value",Integer.toString(d==null?0:d.optionalZones().size()))):
                key.equals("dungeon-presets")?msg(key,Placeholder.unparsed("value",Integer.toString(d==null?0:d.spawnerPresets().size()))):msg(key,Placeholder.component("value",msg(d!=null&&d.enabled()?"enabled-active":"enabled-inactive")));
        set(slot,GuiTheme.unavailable(name,msg("control-edit-blocked")));
    }
    @Override protected void renderFooter() {
        if(parent()!=null) set(45,SpawnerLibraryMenu.back(parent()::open,true));
        tintNavigation(net.kyori.adventure.text.format.NamedTextColor.GOLD);
        if(!controlOnly) set(49,SpawnerLibraryMenu.save(this::saveDraft,dirty()));
        if(!controlOnly) set(47,Button.of(Material.BRICKS,BuildMenu.b("enter"),List.of(BuildMenu.b("enter-lore")),(p,c)->
                MenuListener.instance().later(()->{
                    if(!writable())return;
                    var build=services.plugin.getServer().getServicesManager().load(BuildModeService.class);
                    if(build!=null) build.enter(p,draft.get().id());
                })));
        if(!controlOnly)lore(47,msg("root-build"),Component.empty(),msg("root-open-build"));
        tintNavigation(net.kyori.adventure.text.format.NamedTextColor.GOLD);
    }
    @Override protected Runnable onSave() {return controlOnly?null:super.onSave();}
    private dev.dasan.customdungeons.session.SessionManager sessions() {
        return services.plugin.sessionManager();
    }
    static String controlReason(String key,boolean permitted,boolean enabled,boolean valid,boolean unsaved,
                                dev.dasan.customdungeons.session.SessionState state,boolean playerBusy) {
        if(!permitted) return "control-no-permission";
        if(!enabled) return "control-disabled";
        if(!valid) return "control-invalid";
        if(unsaved) return "control-save-first";
        if((key.equals("start") || key.equals("test")) && (state==dev.dasan.customdungeons.session.SessionState.COMPLETED
                || state==dev.dasan.customdungeons.session.SessionState.FAILED || state==dev.dasan.customdungeons.session.SessionState.RESETTING)) return "control-vacating";
        if(key.equals("start") && state!=dev.dasan.customdungeons.session.SessionState.LOBBY) return "control-no-lobby";
        if(key.equals("test") && (state!=dev.dasan.customdungeons.session.SessionState.FREE || playerBusy)) return "control-busy";
        if((key.equals("stop")||key.equals("reset")) && state==dev.dasan.customdungeons.session.SessionState.FREE) return "control-no-session";
        return null;
    }
    private String controlReason(String key,String permission) {
        if(this instanceof BuildMenu) return "control-busy";
        var manager=sessions();
        if(services.store.isReloading() || saving || manager==null) return "control-busy";
        var d=controlOnly?services.store.dungeons().get(dungeonId):draft.get();
        if(d==null) return "control-invalid";
        if((key.equals("start") || key.equals("test")) && manager.vacating(d.id()))return "control-vacating";
        var state=manager.session(d.id()).map(s->s.state().state()).orElse(dev.dasan.customdungeons.session.SessionState.FREE);
        return controlReason(key,viewer.hasPermission(permission),d.enabled(),new Validator().validate(d,services.store.mobs(),services.store.spawnerPresets()).isEmpty(),
                dirty()||outdated(),state,manager.sessionOf(viewer.getUniqueId()).isPresent());
    }
    private void control(int slot,String key,Material icon,String permission) {
        String reason=controlReason(key,permission);
        if(!controlOnly&&(key.equals("start")||key.equals("test"))) {
            var invalid=new Validator().validate(draft.get(),services.store.mobs(),services.store.spawnerPresets()).stream().filter(e->e.path().startsWith("optional-zones[")).toList();
            if(!invalid.isEmpty()) {
                var paths=Component.join(net.kyori.adventure.text.JoinConfiguration.separator(msg("zone-path-and")),invalid.stream().map(e->Component.text(e.path())).toList());
                set(slot,GuiTheme.information(Material.GRAY_DYE,GuiTheme.grayName(msg("control-"+key)),List.of(msg("zone-fix-paths",Placeholder.component("paths",paths)))));return;
            }
        }
        var lore=new ArrayList<Component>();lore.add(msg("control-"+key+"-lore"));
        if(key.equals("stop")) lore.add(msg("control-reset-click"));
        if(reason!=null) lore.add(MenuListener.instance().messages().get("gui.common.unavailable",Placeholder.component("reason",msg(reason))));
        lore.add(0,Component.empty());
        set(slot,Button.of(reason==null?icon:Material.GRAY_DYE,(reason==null?msg("control-"+key):GuiTheme.grayName(msg("control-"+key))),lore,(p,c)->
                MenuListener.instance().later(()->{
                    if(!p.isOnline() || !p.hasPermission("customdungeons.admin.edit")) return;
                    String actionKey=key.equals("stop")&&c.isRightClick()?"reset":key;
                    String latest=controlReason(actionKey,permission);
                    if(latest!=null) {tell(latest);refresh();return;}
                    try {
                        var manager=sessions();
                        boolean succeeded;
                        boolean preparing=false;
                        if(actionKey.equals("start")) {
                            manager.forceStart(dungeonId);
                            var session=manager.session(dungeonId);
                            succeeded=session.filter(s->s.state().state()==dev.dasan.customdungeons.session.SessionState.RUNNING).isPresent();
                            preparing=session.filter(s->s.state().state()==dev.dasan.customdungeons.session.SessionState.LOBBY&&!s.players().isEmpty()).isPresent()&&worldsLoaded();
                        } else if(actionKey.equals("test")) {
                            manager.startTest(p,dungeonId);
                            var session=manager.sessionOf(p.getUniqueId()).filter(s->s.def().id().equals(dungeonId)&&s.testMode());
                            succeeded=session.filter(s->s.state().state()==dev.dasan.customdungeons.session.SessionState.RUNNING).isPresent();
                            preparing=session.filter(s->s.state().state()==dev.dasan.customdungeons.session.SessionState.LOBBY).isPresent()&&worldsLoaded();
                        } else {
                            if(actionKey.equals("stop")) manager.stop(dungeonId); else manager.reset(dungeonId);
                            succeeded=manager.session(dungeonId).map(s->s.state().state()==dev.dasan.customdungeons.session.SessionState.FREE).orElse(true);
                        }
                        if(succeeded) MenuListener.instance().messages().send(p,"command."+(actionKey.equals("test")?"test-started":actionKey));
                        else if(preparing) tell("control-preparing");
                        else tell((actionKey.equals("test")||actionKey.equals("start"))&&!worldsLoaded()?"control-world-unavailable":"control-"+actionKey+"-rejected");
                    } catch(RuntimeException failure) {tell("control-failed");}
                    refresh();
                })));
    }
    private boolean worldsLoaded() {
        var d=services.store.dungeons().get(dungeonId);
        if(d==null) return false;
        var worlds=new HashSet<String>();
        if(d.lobby()!=null) worlds.add(d.lobby().world());
        if(d.exit()!=null) worlds.add(d.exit().world());
        for(var room:d.rooms()) {
            if(room.region()!=null) worlds.add(room.region().world());
            if(room.checkpoint()!=null) worlds.add(room.checkpoint().world());
            if(room.door()!=null) worlds.add(room.door().world());
            for(var spawner:room.spawners()) if(spawner.location()!=null) worlds.add(spawner.location().world());
        }
        return worlds.stream().allMatch(w->w!=null&&org.bukkit.Bukkit.getWorld(w)!=null);
    }
    void saveDraft() {
        if(lootPending)return;
        if (!writable()) return;
        var validator = new Validator();
        errors = validator.validate(draft.get(), services.store.mobs(),services.store.spawnerPresets());
        warnings = errors.isEmpty() ? validator.warnings(SpawnerPresets.resolve(draft.get(),services.store.spawnerPresets()), services.store.mobs(), Objects.requireNonNull(
                services.plugin.getServer().getServicesManager().load(EntityHeights.class),"Entity heights service")) : List.of();
        if (!errors.isEmpty()) { refresh(); MenuListener.instance().later(this::open); MenuListener.instance().play(viewer, MenuListener.instance().sounds().error()); return; }
        DungeonDef snapshot = draft.get();
        saving = true;
        var locks = MenuListener.instance().editLocks();
        UUID saveOwner = UUID.randomUUID();
        // Framework close/quit handlers release the player's locks. The write owns
        // this lock independently until its completion runs on the main thread.
        synchronized (locks) {
            locks.unlock(snapshot.id(), viewer.getUniqueId());
            locks.tryLock(snapshot.id(), saveOwner);
        }
        try {
            services.store.save(snapshot).whenComplete((unused, failure) -> {
                if (!services.plugin.isEnabled()) return;
                MenuListener.instance().later(() -> {
                    if (failure == null) {
                        // Saving can normalize a legacy/reordered final KEY room to AUTOMATIC.
                        persisted = services.store.dungeons().getOrDefault(snapshot.id(),snapshot);
                        draft.set(persisted);
                        acceptWorkingVersion();
                    }
                    saving = false;
                    reconcileLoot();
                    locks.unlock(snapshot.id(), saveOwner);
                    if (!viewer.isOnline()) return;
                    tell(failure == null ? "saved" : "save-failed");
                    if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof DungeonEditor editor
                            && editor.root == this) {
                        if (failure == null && !warnings.isEmpty()) open();
                        else editor.refresh();
                    }
                });
            });
        } catch (RuntimeException failure) {
            saving = false;
            locks.unlock(snapshot.id(), saveOwner);
            tell("save-failed");
        }
    }
    @Override protected Menu parent() { return list; }

    /** Local immutable copy builder, including optional authoring fields. */
    static final class Values {
        String id, name; boolean enabled, keep, permission;
        StartMode startMode; List<Point> plates; int plateCountdown, introSeconds; Region entranceDoor;
        boolean startTp, cinematic; DisconnectMode disconnectMode; FinishMode finishMode; FinishDestination finishDestination; int exitGrace; List<Point> exitPlates;
        Region area; Point lobby, exit; int min, max, countdown, lives, time, cooldown;
        ScalingDef scaling; Map<HookEvent,List<String>> hooks; RewardDef reward; List<RoomDef> rooms; List<String> spawnerPresets; List<OptionalZoneDef> optionalZones;
        Values(DungeonDef d) {
            id=d.id(); name=d.displayName(); enabled=d.enabled(); lobby=d.lobby(); exit=d.exit();
            min=d.minPlayers(); max=d.maxPlayers(); countdown=d.lobbyCountdownSeconds(); lives=d.lives();
            keep=d.keepInventory(); time=d.timeLimitSeconds(); cooldown=d.cooldownSeconds(); permission=d.requirePermission();
            scaling=d.scaling(); hooks=d.hooks(); reward=d.reward(); rooms=d.rooms(); optionalZones=d.optionalZones(); spawnerPresets=d.spawnerPresets(); area=d.area();
            startMode=d.startMode(); plates=d.plates(); plateCountdown=d.plateCountdownSeconds(); entranceDoor=d.entranceDoor();
            disconnectMode=d.disconnectMode(); startTp=d.teleportOnStart(); finishMode=d.finishMode(); finishDestination=d.finishDestination(); exitGrace=d.exitGraceSeconds(); exitPlates=d.exitPlates(); cinematic=d.introCinematic(); introSeconds=d.introSeconds();
        }
        DungeonDef build() { return new DungeonDef(id,name,enabled,lobby,exit,min,max,countdown,lives,keep,time,
                cooldown,permission,scaling,hooks,reward,rooms,spawnerPresets,area,startMode,plates,plateCountdown,entranceDoor,startTp,cinematic,introSeconds,finishMode,exitGrace,finishDestination,exitPlates,disconnectMode,optionalZones); }
    }
}

/** Shared main-thread UI helpers, kept in the authorized T15 file. */
abstract class DungeonEditor extends Menu {
    DungeonMenu root;
    private final Menu previous;
    protected final String category;
    private final org.bukkit.event.Listener presetCloseListener=new org.bukkit.event.Listener() {
        @org.bukkit.event.EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent event) {
            if(event.getInventory()!=getInventory()) return;
            releaseInventoryListener(event.getInventory());
            if(root instanceof SpawnerPresetMenu preset) preset.closed();
        }
    };
    DungeonEditor(Player player, String title, DungeonMenu root, Menu previous) {
        super(player, msg(title), switch(title) {
            case "main", "entry" -> 3;
            case "scaling" -> 4;
            case "wave" -> 5;
            default -> 6;
        }); this.root=root; this.previous=previous; this.category=title;
    }
    DungeonEditor(Player player,String category,Component title,DungeonMenu root,Menu previous) {
        super(player,title,6);this.root=root;this.previous=previous;this.category=category;
    }
    DungeonEditor(String category,Component title,DungeonMenu root,Menu previous) {
        super(root.viewerPlayer(),title,6);this.root=root;this.previous=previous;this.category=category;
    }
    DungeonEditor(String category,Component title,int rows,DungeonMenu root,Menu previous) {
        super(root.viewerPlayer(),title,rows);this.root=root;this.previous=previous;this.category=category;
    }
    static Component originTitle(String category,EditOrigin origin) {return origin.kind()==EditOrigin.Kind.ZONE?msg("zone-editor-title",Placeholder.component("editor",msg(category)),Placeholder.unparsed("id",origin.id())):msg(category);}

    DungeonEditor(String title, DungeonMenu root, Menu previous) { this(root.viewerPlayer(), title, root, previous); }
    @Override protected Material borderMaterial() {
        return switch(category) {
            case "room" -> Material.GREEN_STAINED_GLASS_PANE;
            case "rooms", "zones", "zone" -> Material.LIME_STAINED_GLASS_PANE;
            case "ambience", "ambience-effects" -> Material.GREEN_STAINED_GLASS_PANE;
            case "preset-editor", "preset-library", "dungeon-presets", "preset-picker", "room-spawners", "spawner", "waves", "wave", "entry" -> Material.CYAN_STAINED_GLASS_PANE;
            case "reward", "loot" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "context", "template", "carrier" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            default -> Material.ORANGE_STAINED_GLASS_PANE;
        };
    }
    Player viewerPlayer() { return viewer; }
    static Component msg(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        return MenuListener.instance().messages().get("gui.dungeon."+key,args);
    }
    void tell(String key) { MenuListener.instance().messages().send(viewer,"gui.dungeon."+key); }
    Button action(String key, Material icon, Object value, Button.ClickHandler handler, Component... details) {
        var lore=new ArrayList<Component>();
        lore.addAll(List.of(details));
        lore.add(Component.empty()); lore.add(msg(key+"-lore"));
        return Button.of(icon,valueName(key,String.valueOf(value)),lore,
                (p,c) -> { if (root == null || root.writable()) handler.handle(p,c); });
    }
    private static Component valueName(String key,String value) {
        return msg(key,key.equals("name")?Placeholder.component("value",dev.dasan.customdungeons.text.Text.parse(value)):Placeholder.unparsed("value",value),
                Placeholder.component("unit",msg(value.equals("1")?"unit-mob":"unit-mobs")));
    }
    void add(int slot,String key,Material icon,Runnable run,Component... details) {
        set(slot,action(key,icon,"",(p,c)->MenuListener.instance().later(() -> { if(root==null||root.writable()) run.run(); }),details));
    }
    void section(int slot,String key,Material icon,Component... details) {
        var lore=new ArrayList<Component>();
        lore.add(msg(key+"-lore"));
        lore.addAll(List.of(details));
        set(slot,GuiTheme.section(msg(key),lore));
    }
    void sectionState(int slot,String key,boolean ready,Component... details) {
        var lore=new ArrayList<Component>();lore.add(msg(key+"-lore"));lore.addAll(List.of(details));
        set(slot,GuiTheme.section(ready,status(key,ready),lore));
    }
    @Override protected boolean hasUnsavedChanges() { return root != null && root.dirty(); }
    /** Wave and entry editors share the preset draft but own separate inventory lifetimes. */
    protected final void bindPresetInventoryListener() {
        if(root instanceof SpawnerPresetMenu) bindInventoryListener(presetCloseListener,root.services.plugin);
    }
    @Override protected void renderHeader() {
        bindPresetInventoryListener();
        GuiTheme.help(this, java.util.stream.IntStream.rangeClosed(1,3)
                .mapToObj(i -> msg("help-"+category+"-"+i)).toList());
    }
    void summary(Material icon,Component name,Component... lore) {
        set(4,GuiTheme.information(icon,name,List.of(lore)));
    }
    void tintName(int slot,net.kyori.adventure.text.format.TextColor color) {
        var item=getInventory().getItem(slot);if(item==null)return;
        item.editMeta(meta->{if(meta.displayName()!=null)meta.displayName(Component.text(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName()),color));});
    }
    void lore(int slot,Component... lines) {var item=getInventory().getItem(slot);if(item!=null)item.editMeta(meta->meta.lore(List.of(lines)));}
    void tintNavigation(net.kyori.adventure.text.format.TextColor color) {
        int base=getInventory().getSize()-9;
        tintName(8,color);tintName(base,color);
        if(parent()!=null)lore(base,Component.empty(),msg("zone-nav-back"));
        lore(base+8,Component.empty(),msg("zone-nav-close"));
        if(onSave()!=null) {
            var lines=new ArrayList<Component>();if(hasUnsavedChanges())lines.add(MenuListener.instance().messages().get("gui.common.unsaved"));
            lines.add(Component.empty());lines.add(msg("zone-save-action"));lore(base+4,lines.toArray(Component[]::new));
        }
    }
    static Component status(String key, boolean ready) {
        return MenuListener.instance().messages().get(ready?"gui.common.ready":"gui.common.missing",
                Placeholder.component("part",msg(key)));
    }
    static int totalMobs(RoomDef room) {
        return room.spawners().stream().flatMap(s -> s.waves().stream()).flatMap(w -> w.entries().stream())
                .mapToInt(WaveEntry::count).sum();
    }
    List<Component> waveMobs(WaveDef wave) {
        return wave.entries().stream().map(e -> {
            var mob=root.services.store.mobs().get(e.templateId());
            return msg("mob-count",Placeholder.unparsed("count",Integer.toString(e.count())),
                    Placeholder.component("name",mob==null?Component.text(e.templateId()):dev.dasan.customdungeons.text.Text.parse(mob.displayName())));
        }).toList();
    }
    Component waveLine(WaveDef wave,int index) {
        var names=waveMobs(wave);
        Component mobs=names.isEmpty()?msg("empty-mobs"):Component.join(net.kyori.adventure.text.JoinConfiguration.separator(msg("list-separator")),names);
        return msg("wave-mobs",Placeholder.unparsed("number",Integer.toString(index+1)),
                Placeholder.component("mode",msg("mode-"+wave.mode().name().toLowerCase(Locale.ROOT))),Placeholder.component("mobs",mobs));
    }
    static Component compactPoint(Point p) {
        return p==null?pointLore(null):msg("root-point",Placeholder.unparsed("world",p.world()),Placeholder.unparsed("x",SpawnerLibraryMenu.coordinate(p.x())),Placeholder.unparsed("y",SpawnerLibraryMenu.coordinate(p.y())),Placeholder.unparsed("z",SpawnerLibraryMenu.coordinate(p.z())));
    }
    static Component regionLore(Region region) {
        if(region==null) return msg("region-unset");
        return msg("region-current",Placeholder.unparsed("world",region.world()),
                Placeholder.unparsed("pos1",region.min().x()+", "+region.min().y()+", "+region.min().z()),
                Placeholder.unparsed("pos2",region.max().x()+", "+region.max().y()+", "+region.max().z()),
                Placeholder.unparsed("size",((long)region.max().x()-region.min().x()+1)+" × "+((long)region.max().y()-region.min().y()+1)+" × "+((long)region.max().z()-region.min().z()+1)));
    }
    static Component regionSizeLore(Region region) {
        if(region==null) return Component.empty();
        return msg("region-size",Placeholder.unparsed("size",((long)region.max().x()-region.min().x()+1)+" × "+((long)region.max().y()-region.min().y()+1)+" × "+((long)region.max().z()-region.min().z()+1)));
    }
    static double inputValue(double value, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
    static Material icon(String key) {
        return switch (key) {
            case "min" -> Material.PLAYER_HEAD; case "max" -> Material.PLAYER_HEAD;
            case "lives" -> Material.TOTEM_OF_UNDYING; case "countdown", "interval" -> Material.CLOCK;
            case "time", "pause", "plate-countdown", "intro-seconds", "exit-grace" -> Material.CLOCK; case "cooldown", "delay" -> Material.CLOCK;
            case "radius" -> Material.TARGET; case "count" -> Material.ZOMBIE_HEAD;
            case "extra-mobs" -> Material.ANVIL; case "extra-health" -> Material.APPLE;
            case "lobby" -> Material.RED_BED; case "exit" -> Material.DARK_OAK_DOOR;
            case "checkpoint" -> Material.RESPAWN_ANCHOR; case "location" -> Material.LODESTONE;
            case "keep" -> Material.CHEST; case "permission" -> Material.IRON_BARS;
            case "enabled" -> Material.REDSTONE_TORCH; case "key" -> Material.TRIPWIRE_HOOK;
            default -> Material.COMPARATOR;
        };
    }
    void integer(int slot,String key,int value,IntConsumer submit,Component... details) {
        var range=NumericRanges.dungeon(key);
        set(slot,NumericInputs.decorate(action(key,icon(key),value,(p,c)->MenuListener.instance().later(() -> {
            if (!root.writable()) return;
            DoubleConsumer accept = n -> {if(root.writable()) {submit.accept((int)n);refresh();}};
            if(c.isRightClick()) NumericInputs.clicks(p,valueName(key,Integer.toString(value)),range,value,accept);
            else NumericInputs.edit(p,valueName(key,Integer.toString(value)),range,value,accept);
        }),details.length==0?new Component[]{msg("value",Placeholder.unparsed("value",Integer.toString(value)))}:details),range));
    }
    void decimal(int slot,String key,double value,DoubleConsumer submit) {
        var range=NumericRanges.dungeon(key);
        set(slot,NumericInputs.decorate(action(key,icon(key),Inputs.formatNumber(value,range.decimals()),(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) NumericInputs.edit(p,valueName(key,Inputs.formatNumber(value,range.decimals())),range,value,n->{if(root.writable()){submit.accept(n);refresh();}});
        }),msg("value",Placeholder.unparsed("value",Inputs.formatNumber(value,range.decimals())))),range));
    }
    void text(int slot,String key,String value,int length,Consumer<String> submit) {
        set(slot,action(key,Material.NAME_TAG,value,(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.text(p,valueName(key,value),value,length,s->{if(root.writable()) {submit.accept(s);refresh();}});
        }),msg("value",Placeholder.component("value",dev.dasan.customdungeons.text.Text.parse(value)))));
    }
    void toggle(int slot,String key,boolean value,Runnable run) {
        // Translate booleans instead of displaying Java true/false.
        set(slot,Button.of(GuiTheme.toggleIcon(value),msg(key,Placeholder.component("value",msg(key.equals("enabled")?(value?"enabled-active":"enabled-inactive"):(value?"yes":"no")))),
                List.of(msg(value?"yes":"no"),Component.empty(),msg(key+"-lore")),(p,c)->{if(root.writable()){run.run();refresh();}}));
    }
    void giveTool(int slot, ToolType type) {
        add(slot,"give-"+type.name().toLowerCase(Locale.ROOT),toolIcon(type),()->root.services.tools.give(viewer,type,root.draft.get().id()));
    }
    static Point position(Player player) {
        var l=player.getLocation();
        return new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch());
    }
    static Component pointLore(Point point) {
        if(point==null) return msg("point-unset");
        return msg("point-coordinates", Placeholder.unparsed("world",point.world()),
                Placeholder.unparsed("x",Inputs.formatNumber(point.x(),1)),Placeholder.unparsed("y",Inputs.formatNumber(point.y(),1)),
                Placeholder.unparsed("z",Inputs.formatNumber(point.z(),1)),Placeholder.unparsed("yaw",Inputs.formatNumber(point.yaw(),1)),
                Placeholder.unparsed("pitch",Inputs.formatNumber(point.pitch(),1)));
    }
    final Material toolIcon(ToolType type) {
        return ToolMaterials.resolve(type,root.services.plugin.getConfig().getString("tools.items."+type.name().toLowerCase(Locale.ROOT)));
    }
    void pointHere(int slot,String key,Point current,Consumer<Point> submit) {
        set(slot,Button.of(Material.LIME_DYE,msg(key+"-here"),List.of(category.equals("title")?SpawnerLibraryMenu.position(current,true):pointLore(current),Component.empty(),category.equals("title")?SpawnerLibraryMenu.m("point-here-lore"):msg("point-here-lore")),(p,c)->{
            if(!root.writable()) return;
            if(c.isShiftClick()) {root.services.tools.give(p,ToolType.POINT,root.draft.get().id());return;}
            if(c.isRightClick()) {
                var point=root.services.tools.lastPoint(p.getUniqueId());
                if(point.isEmpty()) {tell("no-point");return;}
                var l=point.get();submit.accept(new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()));
            } else submit.accept(position(p));
            refresh();
        }));
    }
    void point(int slot,String key,Point current,Consumer<Point> submit,ToolType type) {
        set(slot,Button.of(Material.LIME_DYE,msg(key),List.of(pointLore(current),Component.empty(),msg(key+"-lore")),(p,c)->{
            if(!root.writable()) return;
            if(c.isRightClick()) {root.services.tools.give(p,type,root.draft.get().id());return;}
            var location=root.services.tools.lastPoint(p.getUniqueId());
            if(location.isEmpty()) {tell("no-point");return;}
            var l=location.get(); submit.accept(new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()));refresh();
        }));
    }
    Component selectionLore(dev.dasan.customdungeons.tool.Selection selection) {
        if(selection==null || !selection.complete()) return msg(selection==null || selection.a()==null
                ? (selection==null || selection.b()==null ? "selection-missing-both" : "selection-missing-pos1") : "selection-missing-pos2");
        var r=selection.toRegion();
        return msg("selection-complete",Placeholder.unparsed("world",r.world()),
                Placeholder.unparsed("pos1",r.min().x()+", "+r.min().y()+", "+r.min().z()),
                Placeholder.unparsed("pos2",r.max().x()+", "+r.max().y()+", "+r.max().z()),
                Placeholder.unparsed("size",((long)r.max().x()-r.min().x()+1)+" × "+((long)r.max().y()-r.min().y()+1)+" × "+((long)r.max().z()-r.min().z()+1)));
    }
    void region(int slot,String key,Region current,Consumer<Region> submit,ToolType type) {region(slot,key,current,submit,type,null);}
    void region(int slot,String key,Region current,Consumer<Region> submit,ToolType type,EditOrigin target) {
        var selection=root.services.tools.selection(viewer.getUniqueId()).orElse(null);
        set(slot,Button.of(Material.LIME_DYE,msg(key),
                List.of(regionLore(current),regionSizeLore(current),selectionLore(selection),Component.empty(),msg(key+"-lore")),(p,c)->{
            if(!root.writable()) return;
            if(c.isRightClick()) {
                if(target==null)root.services.tools.give(p,type,root.draft.get().id());
                else root.services.tools.give(p,type,root.draft.get().id(),target);
                return;
            }
            var origin=root.services.tools.origin(p.getUniqueId());
            if(target!=null&&origin.isPresent()&&!target.equals(origin.get())) {tell("zone-selection-context");return;}
            var latest=root.services.tools.selection(p.getUniqueId()).orElse(null);
            if(latest==null||!latest.complete()) {MenuListener.instance().messages().send(p,"gui.dungeon.no-selection");return;}
            submit.accept(latest.toRegion());refresh();
        }));
    }
    @Override protected Menu parent() { return previous; }
    @Override protected Runnable onSave() { return root==null?null:root::saveDraft; }
}

/** Centered adaptive lists with contextual footer actions. */
abstract class DungeonPage<T> extends DungeonEditor {
    private int page;
    DungeonPage(String category,Component title,DungeonMenu root,Menu previous) {super(category,title,root,previous);}
    DungeonPage(String title,DungeonMenu root,Menu previous) {super(title,root,previous);}
    DungeonPage(Player player,String title,Menu previous) {super(player,title,null,previous);}
    protected abstract List<T> entries();
    protected abstract Button entry(T value,int index);
    protected abstract void create();
    protected String createKey() {return "add";}
    protected int firstContentRow() {return 1;}
    protected boolean canCreate() {return true;}
    @Override protected int preferredRows() {return GuiLayout.rowsFor(entries().size(),7,firstContentRow()-1);}
    private int capacity() {return (getInventory().getSize()/9-1-firstContentRow())*7;}
    @Override protected void renderFooter() {
        if(canCreate()) set(getInventory().getSize()-9+(onSave()==null?4:2),action(createKey(),Material.LIME_DYE,"",(p,c)->MenuListener.instance().later(() -> {if(root==null||root.writable()) create();})));
    }
    @Override protected void render() {
        List<T> values=entries(); page=Math.clamp(page,0,PagedMenu.pageCount(values.size(),capacity())-1);
        summary(switch(category) {case "rooms" -> Material.OAK_DOOR; case "room-spawners" -> Material.SPAWNER;
            case "waves" -> Material.ZOMBIE_HEAD;case "commands" -> Material.COMMAND_BLOCK;default -> Material.BOOK;},
                msg(category),msg(switch(category) {case "room-spawners" -> "spawner-count";case "commands" -> "command-count";default -> "list-summary";},Placeholder.unparsed("value",Integer.toString(values.size()))));
        int start=page*capacity();
        for(int i=start;i<Math.min(start+capacity(),values.size());i++) {
            int offset=i-start;set(GuiLayout.pageSlot(offset,Math.min(capacity(),values.size()-start),firstContentRow()),entry(values.get(i),i));
        }
    }
    @Override protected boolean hasPreviousPage() {return page>0;}
    @Override protected boolean hasNextPage() {return (page+1)*capacity()<entries().size();}
    @Override protected void previousPage() {page--;refresh();}
    @Override protected void nextPage() {page++;refresh();}
}
