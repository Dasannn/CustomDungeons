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

/** Owns the single immutable-definition draft shared by all dungeon submenus. */
public final class DungeonMenu extends DungeonEditor {
    final Draft<DungeonDef> draft;
    final DungeonListMenu services;
    final DungeonListMenu list;
    private boolean saving;
    private boolean confirmingDiscard;
    private DungeonDef persisted;
    private List<ValidationError> errors = List.of();

    public DungeonMenu(Player player, DungeonDef definition, DungeonListMenu list) {
        super(player, "title", null, list);
        this.root = this;
        this.draft = new Draft<>(definition);
        this.services = list;
        this.list = list;
        this.persisted = list.store.dungeons().get(definition.id());
    }
    public Draft<DungeonDef> draft() { return draft; }
    boolean dirty() { return !Objects.equals(persisted, draft.get()); }
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
        if (confirmingDiscard || services.store.isReloading() || !services.plugin.isEnabled() || !viewer.isOnline()) return;
        MenuListener.instance().later(() -> {
            if (services.store.isReloading() || !list.current(this) || saving || !dirty() || !viewer.isOnline()) return;
            var holder = viewer.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof DungeonEditor editor && editor.root == this) return;
            boolean returningToList = holder == list;
            confirmDiscard(() -> {
                list.open();
                if (!returningToList) MenuListener.instance().later(viewer::closeInventory);
            });
        });
    }
    boolean outdated() { return !Objects.equals(persisted, services.store.dungeons().get(draft.get().id())); }
    boolean writable() { return canEdit(true); }
    boolean canEdit(boolean notify) {
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
    }
    void room(int index, UnaryOperator<RoomDef> action) {
        change(v -> v.rooms = replace(v.rooms, index, action.apply(v.rooms.get(index))));
    }
    void spawner(int room, int index, UnaryOperator<SpawnerDef> action) {
        room(room, r -> new RoomDef(r.id(), r.region(), r.checkpoint(), r.door(), r.unlock(),
                r.keyCarrierTemplateId(), replace(r.spawners(), index, action.apply(r.spawners().get(index)))));
    }
    void wave(int room, int spawner, int index, UnaryOperator<WaveDef> action) {
        spawner(room, spawner, s -> new SpawnerDef(s.id(), s.location(), s.radius(),
                replace(s.waves(), index, action.apply(s.waves().get(index)))));
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
        add(10, "settings", Material.COMPARATOR, () -> new DungeonSettingsMenu(this).open());
        add(12, "scaling", Material.ANVIL, () -> new ScalingMenu(this).open());
        add(14, "hooks", Material.COMMAND_BLOCK, () -> new HooksMenu(this).open());
        add(16, "rooms", Material.BRICKS, () -> new RoomListMenu(this).open());
        add(20, "reward", Material.CHEST, () -> new RewardMenu(this).open());
        toggle(22, "enabled", draft.get().enabled(), () -> change(v -> v.enabled = !v.enabled));
        if (!errors.isEmpty()) {
            var lore = new ArrayList<Component>();
            for (var error : errors) {
                var args = error.args().entrySet().stream().map(e -> Placeholder.unparsed(e.getKey(), e.getValue()))
                        .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
                lore.add(msg("error-line", Placeholder.unparsed("path", error.path()),
                        Placeholder.component("error", MenuListener.instance().messages().get(error.messageKey(), args))));
            }
            set(31, Button.of(Material.RED_DYE, msg("errors"), lore, (p,c) -> {}));
        }
    }
    void saveDraft() {
        if (!writable()) return;
        errors = new Validator().validate(draft.get(), services.store.mobs());
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
                    if (failure == null) persisted = snapshot;
                    saving = false;
                    locks.unlock(snapshot.id(), saveOwner);
                    if (!viewer.isOnline()) return;
                    tell(failure == null ? "saved" : "save-failed");
                    if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof DungeonEditor editor
                            && editor.root == this) editor.refresh();
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
        ScalingDef scaling; Map<HookEvent,List<String>> hooks; RewardDef reward; List<RoomDef> rooms;
        Values(DungeonDef d) {
            id=d.id(); name=d.displayName(); enabled=d.enabled(); lobby=d.lobby(); exit=d.exit();
            min=d.minPlayers(); max=d.maxPlayers(); countdown=d.lobbyCountdownSeconds(); lives=d.lives();
            keep=d.keepInventory(); time=d.timeLimitSeconds(); cooldown=d.cooldownSeconds(); permission=d.requirePermission();
            scaling=d.scaling(); hooks=d.hooks(); reward=d.reward(); rooms=d.rooms();
        }
        DungeonDef build() { return new DungeonDef(id,name,enabled,lobby,exit,min,max,countdown,lives,keep,time,
                cooldown,permission,scaling,hooks,reward,rooms); }
    }
}

/** Shared main-thread UI helpers, kept in the authorized T15 file. */
abstract class DungeonEditor extends Menu {
    DungeonMenu root;
    private final Menu previous;
    DungeonEditor(Player player, String title, DungeonMenu root, Menu previous) {
        super(player, msg(title), 6); this.root=root; this.previous=previous;
    }
    DungeonEditor(String title, DungeonMenu root, Menu previous) { this(root.viewerPlayer(), title, root, previous); }
    Player viewerPlayer() { return viewer; }
    static Component msg(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        return MenuListener.instance().messages().get("gui.dungeon."+key,args);
    }
    void tell(String key) { MenuListener.instance().messages().send(viewer,"gui.dungeon."+key); }
    Button action(String key, Material icon, Object value, Button.ClickHandler handler) {
        return Button.of(icon,msg(key),List.of(msg(key+"-lore"),msg("value",Placeholder.unparsed("value",String.valueOf(value)))),
                (p,c) -> { if (root == null || root.writable()) handler.handle(p,c); });
    }
    void add(int slot,String key,Material icon,Runnable run) {
        set(slot,action(key,icon,"",(p,c)->MenuListener.instance().later(() -> { if(root==null||root.writable()) run.run(); })));
    }
    static double inputValue(double value, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
    void number(int slot,String key,double value,double min,double max,DoubleConsumer submit) {
        set(slot,action(key,Material.PAPER,value,(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.number(p,msg(key),min,max,inputValue(value,min,max),n->{ if(root.writable()) {submit.accept(n); refresh();} });
        })));
    }
    void text(int slot,String key,String value,int length,Consumer<String> submit) {
        set(slot,action(key,Material.NAME_TAG,value,(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.text(p,msg(key),value,length,s->{if(root.writable()) {submit.accept(s);refresh();}});
        })));
    }
    void toggle(int slot,String key,boolean value,Runnable run) {
        // Translate booleans instead of displaying Java true/false.
        set(slot,Button.of(value?Material.LIME_DYE:Material.GRAY_DYE,msg(key),
                List.of(msg(key+"-lore"),msg(value?"yes":"no")),(p,c)->{if(root.writable()){run.run();refresh();}}));
    }
    void giveTool(int slot, ToolType type) {
        add(slot,"give-"+type.name().toLowerCase(Locale.ROOT),Material.CHEST,()->root.services.tools.give(viewer,type,root.draft.get().id()));
    }
    void point(int slot,String key,Point current,Consumer<Point> submit,ToolType type) {
        set(slot,action(key,Material.COMPASS,current==null?"":current.world(),(p,c)->{
            if(c.isRightClick()) {root.services.tools.give(p,type,root.draft.get().id());return;}
            var location=root.services.tools.lastPoint(p.getUniqueId());
            if(location.isEmpty()) {tell("no-point");return;}
            var l=location.get(); submit.accept(new Point(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()));refresh();
        }));
    }
    void region(int slot,String key,Region current,Consumer<Region> submit,ToolType type) {
        set(slot,action(key,Material.BLAZE_ROD,current==null?"":current.world(),(p,c)->{
            if(c.isRightClick()) {root.services.tools.give(p,type,root.draft.get().id());return;}
            var selection=root.services.tools.selection(p.getUniqueId());
            if(selection.isEmpty()||!selection.get().complete()) {tell("no-selection");return;}
            submit.accept(selection.get().toRegion());refresh();
        }));
    }
    @Override protected Menu parent() { return previous; }
    @Override protected Runnable onSave() { return root==null?null:root::saveDraft; }
}

/** Pagination with an add button in the header and the framework's fixed navigation bar. */
abstract class DungeonPage<T> extends DungeonEditor {
    private int page;
    DungeonPage(String title,DungeonMenu root,Menu previous) {super(title,root,previous);}
    DungeonPage(Player player,String title,Menu previous) {super(player,title,null,previous);}
    protected abstract List<T> entries();
    protected abstract Button entry(T value,int index);
    protected abstract void create();
    @Override protected void render() {
        List<T> values=entries(); page=Math.clamp(page,0,PagedMenu.pageCount(values.size(),28)-1);
        set(4,action("add",Material.EMERALD,"",(p,c)->MenuListener.instance().later(() -> {if(root==null||root.writable()) create();})));
        int start=page*28;
        for(int i=start;i<Math.min(start+28,values.size());i++) {
            int offset=i-start;set((offset/7+1)*9+offset%7+1,entry(values.get(i),i));
        }
    }
    @Override protected boolean hasPreviousPage() {return page>0;}
    @Override protected boolean hasNextPage() {return (page+1)*28<entries().size();}
    @Override protected void previousPage() {page--;refresh();}
    @Override protected void nextPage() {page++;refresh();}
}
