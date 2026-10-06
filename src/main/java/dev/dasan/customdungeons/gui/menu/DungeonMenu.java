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
    private boolean confirmingDiscard;
    private DungeonDef persisted;
    private List<ValidationError> errors = List.of();
    private List<Validator.Warning> warnings = List.of();

    public DungeonMenu(Player player, DungeonDef definition, DungeonListMenu list) {
        this(player,definition,list,false);
    }
    DungeonMenu(Player player, DungeonDef definition, DungeonListMenu list, boolean controlOnly) {
        this(player,definition,list,controlOnly,"title");
    }
    DungeonMenu(Player player,DungeonDef definition,DungeonListMenu list,boolean controlOnly,String title) {
        super(player, title, null, list);
        this.controlOnly=controlOnly;
        this.dungeonId=definition.id();
        this.root = this;
        this.draft = controlOnly ? null : new Draft<>(definition);
        this.services = list;
        this.list = list;
        this.persisted = list.store.dungeons().get(definition.id());
    }
    public @org.jetbrains.annotations.Nullable Draft<DungeonDef> draft() { return draft; }
    boolean dirty() { return !controlOnly && !Objects.equals(persisted, draft.get()); }
    boolean saving() { return saving; }
    void confirmDiscard(Runnable next) {
        if (services.store.isReloading()) return;
        if (saving) { tell("busy"); return; }
        confirmingDiscard = true;
        try {
            // Inputs restores its origin on Cancel, including after Escape or Back.
            open();
            Inputs.confirm(viewer, msg("discard-conflict"), () -> {
                if (saving || !list.current(this)) return;
                list.discard(this);
                next.run();
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
    boolean outdated() { return !controlOnly && !Objects.equals(persisted, services.store.dungeons().get(draft.get().id())); }
    boolean writable() { return canEdit(true); }
    boolean canEdit(boolean notify) {
        if (controlOnly) {if(notify) tell("control-edit-blocked"); return false;}
        if (!viewer.isOnline() || !viewer.hasPermission("customdungeons.admin.edit")) return false;
        if (saving || services.busy(draft.get().id())) { if (notify) tell("busy"); return false; }
        if (!MenuListener.instance().editLocks().tryLock(draft.get().id(), viewer.getUniqueId())) {
            if (notify) tell("locked"); return false;
        }
        if (outdated()) {
            MenuListener.instance().editLocks().unlock(draft.get().id(), viewer.getUniqueId());
            if (notify) tell("conflict"); return false;
        }
        return true;
    }
    void change(Consumer<Values> action) {
        if (!writable()) return;
        Values values = new Values(draft.get());
        action.accept(values);
        draft.set(values.build());
        errors = List.of();
        warnings = List.of();
    }
    void room(int index, UnaryOperator<RoomDef> action) {
        change(v -> v.rooms = replace(v.rooms, index, action.apply(v.rooms.get(index))));
    }
    void spawner(int room, int index, UnaryOperator<SpawnerDef> action) {
        room(room, r -> new RoomDef(r.id(), r.region(), r.checkpoint(), r.door(), r.unlock(),
                r.keyCarrierTemplateId(), replace(r.spawners(), index, action.apply(r.spawners().get(index))),r.openingMode()));
    }
    void wave(int room, int spawner, int index, UnaryOperator<WaveDef> action) {
        if(draft.get().rooms().get(room).spawners().get(spawner).presetId()!=null) return;
        spawner(room, spawner, s -> new SpawnerDef(s.id(), s.location(), s.radius(),
                replace(s.waves(), index, action.apply(s.waves().get(index))),s.presetId()));
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
                msg("error-count",Placeholder.unparsed("value",Integer.toString(new Validator().validate(d,services.store.mobs(),services.store.spawnerPresets()).size()))));
        section(10,"section-structure",Material.ORANGE_STAINED_GLASS_PANE);
        section(12,"section-rules",Material.ORANGE_STAINED_GLASS_PANE);
        section(14,"section-points",Material.ORANGE_STAINED_GLASS_PANE);
        section(16,"section-session",Material.ORANGE_STAINED_GLASS_PANE);
        if (controlOnly) {
            blocked(19,"dungeon-presets"); blocked(28,"rooms"); blocked(37,"reward");
            blocked(21,"settings"); blocked(30,"scaling"); blocked(39,"hooks");
            blocked(23,"lobby-here"); blocked(32,"exit-here");
        } else {
            set(19,action("dungeon-presets",Material.SPAWNER,d.spawnerPresets().size(),(p,c)->MenuListener.instance().later(()->new DungeonSpawnerMenu(this).open()),
                    Component.join(net.kyori.adventure.text.JoinConfiguration.separator(SpawnerLibraryMenu.m("separator")),d.spawnerPresets().stream()
                            .map(id -> {var preset=services.store.spawnerPresets().get(id);return preset==null?SpawnerLibraryMenu.m("missing",SpawnerLibraryMenu.arg("id",id)):SpawnerLibraryMenu.label(preset);}).toList())));
            set(28,action("rooms-link",Material.OAK_DOOR,d.rooms().size(),(p,c)->MenuListener.instance().later(()->new RoomListMenu(this).open())));
            add(37,"reward",Material.CHEST,()->new RewardMenu(this).open());
            add(21,"settings",Material.COMPARATOR,()->new DungeonSettingsMenu(this).open(),
                    SpawnerLibraryMenu.m("settings-summary",Placeholder.component("state",msg(d.enabled()?"enabled-active":"enabled-inactive")),SpawnerLibraryMenu.arg("min",d.minPlayers()),
                            Placeholder.component("max",d.maxPlayers()==0?msg("unlimited"):Component.text(d.maxPlayers()))));
            add(30,"scaling",Material.ANVIL,()->new ScalingMenu(this).open());
            add(39,"hooks",Material.COMMAND_BLOCK,()->new HooksMenu(this).open());
            pointHere(23,"lobby",d.lobby(),p->change(v->v.lobby=p));
            pointHere(32,"exit",d.exit(),p->change(v->v.exit=p));
        }
        control(25,"start",Material.LIME_CONCRETE,"customdungeons.admin.control");
        control(34,"test",Material.TARGET,"customdungeons.admin.test");
        control(43,"stop",Material.RED_CONCRETE,"customdungeons.admin.control");
        if (!errors.isEmpty() || !warnings.isEmpty()) {
            var lore = new ArrayList<Component>();
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
            set(41, Button.of(errors.isEmpty() ? Material.YELLOW_DYE : Material.RED_DYE,
                    msg(errors.isEmpty() ? "warnings" : "errors"), lore, (p,c) -> {}));
        }
    }
    private void blocked(int slot,String key) {
        var d=services.store.dungeons().get(dungeonId);
        Component name=key.equals("rooms")?msg("rooms-link",Placeholder.unparsed("value",Integer.toString(d==null?0:d.rooms().size()))):
                key.equals("dungeon-presets")?msg(key,Placeholder.unparsed("value",Integer.toString(d==null?0:d.spawnerPresets().size()))):msg(key,Placeholder.component("value",msg(d!=null&&d.enabled()?"enabled-active":"enabled-inactive")));
        set(slot,GuiTheme.unavailable(name,msg("control-edit-blocked")));
    }
    @Override protected void renderFooter() {
        if(parent()!=null) set(45,SpawnerLibraryMenu.back(parent()::open,true));
        if(!controlOnly) set(49,SpawnerLibraryMenu.save(this::saveDraft,dirty()));
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
        if(key.equals("start") && state!=dev.dasan.customdungeons.session.SessionState.LOBBY) return "control-no-lobby";
        if(key.equals("test") && (state!=dev.dasan.customdungeons.session.SessionState.FREE || playerBusy)) return "control-busy";
        if((key.equals("stop")||key.equals("reset")) && state==dev.dasan.customdungeons.session.SessionState.FREE) return "control-no-session";
        return null;
    }
    private String controlReason(String key,String permission) {
        var manager=sessions();
        if(services.store.isReloading() || saving || manager==null) return "control-busy";
        var d=controlOnly?services.store.dungeons().get(dungeonId):draft.get();
        if(d==null) return "control-invalid";
        var state=manager.session(d.id()).map(s->s.state().state()).orElse(dev.dasan.customdungeons.session.SessionState.FREE);
        return controlReason(key,viewer.hasPermission(permission),d.enabled(),new Validator().validate(d,services.store.mobs(),services.store.spawnerPresets()).isEmpty(),
                dirty()||outdated(),state,manager.sessionOf(viewer.getUniqueId()).isPresent());
    }
    private void control(int slot,String key,Material icon,String permission) {
        String reason=controlReason(key,permission);
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
                    }
                    saving = false;
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

    /** Local copy builder; T01 records remain untouched. */
    static final class Values {
        String id, name; boolean enabled, keep, permission;
        Point lobby, exit; int min, max, countdown, lives, time, cooldown;
        ScalingDef scaling; Map<HookEvent,List<String>> hooks; RewardDef reward; List<RoomDef> rooms; List<String> spawnerPresets;
        Values(DungeonDef d) {
            id=d.id(); name=d.displayName(); enabled=d.enabled(); lobby=d.lobby(); exit=d.exit();
            min=d.minPlayers(); max=d.maxPlayers(); countdown=d.lobbyCountdownSeconds(); lives=d.lives();
            keep=d.keepInventory(); time=d.timeLimitSeconds(); cooldown=d.cooldownSeconds(); permission=d.requirePermission();
            scaling=d.scaling(); hooks=d.hooks(); reward=d.reward(); rooms=d.rooms(); spawnerPresets=d.spawnerPresets();
        }
        DungeonDef build() { return new DungeonDef(id,name,enabled,lobby,exit,min,max,countdown,lives,keep,time,
                cooldown,permission,scaling,hooks,reward,rooms,spawnerPresets); }
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
    DungeonEditor(String title, DungeonMenu root, Menu previous) { this(root.viewerPlayer(), title, root, previous); }
    @Override protected Material borderMaterial() {
        return switch(category) {
            case "room", "rooms" -> Material.LIME_STAINED_GLASS_PANE;
            case "preset-editor", "preset-library", "dungeon-presets", "preset-picker", "room-spawners", "spawner", "waves", "wave", "entry" -> Material.CYAN_STAINED_GLASS_PANE;
            case "reward" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "template", "carrier" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
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
            case "time", "pause" -> Material.CLOCK; case "cooldown", "delay" -> Material.CLOCK;
            case "radius" -> Material.TARGET; case "count" -> Material.ZOMBIE_HEAD;
            case "extra-mobs" -> Material.ANVIL; case "extra-health" -> Material.APPLE;
            case "lobby" -> Material.RED_BED; case "exit" -> Material.DARK_OAK_DOOR;
            case "checkpoint" -> Material.RESPAWN_ANCHOR; case "location" -> Material.LODESTONE;
            case "keep" -> Material.CHEST; case "permission" -> Material.IRON_BARS;
            case "enabled" -> Material.REDSTONE_TORCH; case "key" -> Material.TRIPWIRE_HOOK;
            default -> Material.COMPARATOR;
        };
    }
    void integer(int slot,String key,int value,int min,int max,IntConsumer submit) {
        set(slot,action(key,icon(key),value,(p,c)->MenuListener.instance().later(() -> {
            if (!root.writable()) return;
            DoubleConsumer accept = n -> {if(root.writable()) {submit.accept((int)n);refresh();}};
            if(c.isRightClick()) Inputs.numberWithClicks(p,valueName(key,Integer.toString(value)),min,max,inputValue(value,min,max),accept,0);
            else Inputs.integer(p,valueName(key,Integer.toString(value)),min,max,(int)inputValue(value,min,max),n->accept.accept(n));
        }),msg("value",Placeholder.unparsed("value",Integer.toString(value)))));
    }
    void decimal(int slot,String key,double value,double min,double max,int decimals,DoubleConsumer submit) {
        set(slot,action(key,icon(key),Inputs.formatNumber(value,decimals),(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.decimal(p,valueName(key,Inputs.formatNumber(value,decimals)),min,max,inputValue(value,min,max),decimals,n->{if(root.writable()){submit.accept(n);refresh();}});
        }),msg("value",Placeholder.unparsed("value",Inputs.formatNumber(value,decimals)))));
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
    private Material toolIcon(ToolType type) {
        // ToolMaterials is package-private: use its public service configuration and safe defaults.
        Material fallback=switch(type) {case REGION -> Material.BLAZE_ROD; case DOOR -> Material.AMETHYST_SHARD; case POINT -> Material.ECHO_SHARD; case SPAWNER -> Material.BREEZE_ROD;};
        var allowed=Set.of(Material.BLAZE_ROD,Material.AMETHYST_SHARD,Material.BREEZE_ROD,Material.ECHO_SHARD,
                Material.STICK,Material.PAPER,Material.FEATHER,Material.FLINT,Material.QUARTZ,
                Material.PRISMARINE_SHARD,Material.PRISMARINE_CRYSTALS,Material.BONE);
        var configured=Material.matchMaterial(root.services.plugin.getConfig().getString("tools.items."+type.name().toLowerCase(Locale.ROOT),fallback.name()));
        return configured!=null&&allowed.contains(configured)?configured:fallback;
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
    void region(int slot,String key,Region current,Consumer<Region> submit,ToolType type) {
        var selection=root.services.tools.selection(viewer.getUniqueId()).orElse(null);
        set(slot,Button.of(Material.LIME_DYE,msg(key),
                List.of(regionLore(current),regionSizeLore(current),selectionLore(selection),Component.empty(),msg(key+"-lore")),(p,c)->{
            if(!root.writable()) return;
            if(c.isRightClick()) {root.services.tools.give(p,type,root.draft.get().id());return;}
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
