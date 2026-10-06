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
        add(16, "rooms", Material.OAK_DOOR, () -> new RoomListMenu(this).open());
        add(20, "reward", Material.CHEST, () -> new RewardMenu(this).open());
        toggle(22, "enabled", draft.get().enabled(), () -> change(v -> v.enabled = !v.enabled));
        var d=draft.get();
        pointHere(28,"lobby",d.lobby(),p->change(v->v.lobby=p));
        point(29,"lobby",d.lobby(),p->change(v->v.lobby=p),ToolType.POINT);
        pointHere(33,"exit",d.exit(),p->change(v->v.exit=p));
        point(34,"exit",d.exit(),p->change(v->v.exit=p),ToolType.POINT);
        control(37,"start",Material.LIME_CONCRETE,"customdungeons.admin.control");
        control(39,"test",Material.BLAZE_POWDER,"customdungeons.admin.test");
        control(41,"stop",Material.RED_CONCRETE,"customdungeons.admin.control");
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
        if(key.equals("stop") && state==dev.dasan.customdungeons.session.SessionState.FREE) return "control-no-session";
        return null;
    }
    private String controlReason(String key,String permission) {
        var manager=sessions();
        if(services.store.isReloading() || saving || manager==null) return "control-busy";
        var d=draft.get();
        var state=manager.session(d.id()).map(s->s.state().state()).orElse(dev.dasan.customdungeons.session.SessionState.FREE);
        return controlReason(key,viewer.hasPermission(permission),d.enabled(),new Validator().validate(d,services.store.mobs()).isEmpty(),
                dirty()||outdated(),state,manager.sessionOf(viewer.getUniqueId()).isPresent());
    }
    private void control(int slot,String key,Material icon,String permission) {
        String reason=controlReason(key,permission);
        var lore=new ArrayList<Component>();lore.add(msg("control-"+key+"-lore"));
        if(reason!=null) lore.add(msg(reason));
        set(slot,Button.of(reason==null?icon:Material.GRAY_CONCRETE,msg("control-"+key),lore,(p,c)->
                MenuListener.instance().later(()->{
                    if(!p.isOnline() || !p.hasPermission("customdungeons.admin.edit")) return;
                    String latest=controlReason(key,permission);
                    if(latest!=null) {tell(latest);refresh();return;}
                    String id=draft.get().id();
                    if(key.equals("start")) sessions().forceStart(id);
                    else if(key.equals("test")) sessions().startTest(p,id);
                    else sessions().reset(id);
                    MenuListener.instance().messages().send(p,"command."+(key.equals("test")?"test-started":key.equals("stop")?"reset":"start"));
                    refresh();
                })));
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
        return Button.of(icon,msg(key,Placeholder.unparsed("value",String.valueOf(value))),List.of(msg(key+"-lore"),msg("value",Placeholder.unparsed("value",String.valueOf(value)))),
                (p,c) -> { if (root == null || root.writable()) handler.handle(p,c); });
    }
    void add(int slot,String key,Material icon,Runnable run) {
        set(slot,action(key,icon,"",(p,c)->MenuListener.instance().later(() -> { if(root==null||root.writable()) run.run(); })));
    }
    static double inputValue(double value, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : min;
    }
    static Material icon(String key) {
        return switch (key) {
            case "min" -> Material.PLAYER_HEAD; case "max" -> Material.ARMOR_STAND;
            case "lives" -> Material.TOTEM_OF_UNDYING; case "countdown", "interval" -> Material.CLOCK;
            case "time", "pause" -> Material.REPEATER; case "cooldown", "delay" -> Material.HONEY_BOTTLE;
            case "radius" -> Material.ENDER_PEARL; case "count" -> Material.ZOMBIE_HEAD;
            case "extra-mobs" -> Material.ANVIL; case "extra-health" -> Material.ENCHANTED_GOLDEN_APPLE;
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
            if(c.isRightClick()) Inputs.numberWithClicks(p,msg(key,Placeholder.unparsed("value",Integer.toString(value))),min,max,inputValue(value,min,max),accept,0);
            else Inputs.integer(p,msg(key,Placeholder.unparsed("value",Integer.toString(value))),min,max,(int)inputValue(value,min,max),n->accept.accept(n));
        })));
    }
    void decimal(int slot,String key,double value,double min,double max,int decimals,DoubleConsumer submit) {
        set(slot,action(key,icon(key),Inputs.formatNumber(value,decimals),(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.decimal(p,msg(key,Placeholder.unparsed("value",Inputs.formatNumber(value,decimals))),min,max,inputValue(value,min,max),decimals,n->{if(root.writable()){submit.accept(n);refresh();}});
        })));
    }
    void text(int slot,String key,String value,int length,Consumer<String> submit) {
        set(slot,action(key,Material.NAME_TAG,value,(p,c)->MenuListener.instance().later(() -> {
            if(root.writable()) Inputs.text(p,msg(key,Placeholder.unparsed("value",value)),value,length,s->{if(root.writable()) {submit.accept(s);refresh();}});
        })));
    }
    void toggle(int slot,String key,boolean value,Runnable run) {
        // Translate booleans instead of displaying Java true/false.
        set(slot,Button.of(icon(key),msg(key,Placeholder.component("value",msg(value?"yes":"no"))),
                List.of(msg(key+"-lore"),msg(value?"yes":"no")),(p,c)->{if(root.writable()){run.run();refresh();}}));
    }
    void giveTool(int slot, ToolType type) {
        add(slot,"give-"+type.name().toLowerCase(Locale.ROOT),switch(type) {case REGION -> Material.WOODEN_AXE; case DOOR -> Material.IRON_AXE; case POINT -> Material.BLAZE_ROD; case SPAWNER -> Material.STICK;},()->root.services.tools.give(viewer,type,root.draft.get().id()));
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
    void pointHere(int slot,String key,Point current,Consumer<Point> submit) {
        set(slot,Button.of(icon(key),msg(key+"-here"),List.of(msg("point-here-lore"),pointLore(current)),(p,c)->{
            if(root.writable()) {submit.accept(position(p));refresh();}
        }));
    }
    void point(int slot,String key,Point current,Consumer<Point> submit,ToolType type) {
        set(slot,Button.of(key.equals("exit")?Material.RECOVERY_COMPASS:Material.COMPASS,msg(key),List.of(msg(key+"-lore"),pointLore(current)),(p,c)->{
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
        set(slot,Button.of(type==ToolType.DOOR?Material.IRON_DOOR:Material.GRASS_BLOCK,msg(key),
                List.of(msg(key+"-lore"),selectionLore(selection)),(p,c)->{
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
