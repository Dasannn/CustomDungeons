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

    /** T09/T16 must supply a predicate covering lobby, running and reset sessions. */
    public static void dungeonBusy(Predicate<String> predicate) { dungeonBusy=Objects.requireNonNull(predicate); }
    boolean busy(String id) {return dungeonBusy.test(id);}
    public DungeonListMenu(Player player) {
        super(player,"list",null);
        plugin=org.bukkit.plugin.java.JavaPlugin.getPlugin(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var services=plugin.getServer().getServicesManager();
        store=Objects.requireNonNull(services.load(DefinitionStore.class));
        tools=Objects.requireNonNull(services.load(ToolService.class));
        markers=Objects.requireNonNull(services.load(SpawnerMarkers.class));
    }
    public static void register(dev.dasan.customdungeons.CustomDungeonsPlugin plugin) {
        var markers=Objects.requireNonNull(plugin.getServer().getServicesManager().load(SpawnerMarkers.class));
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
            @org.bukkit.event.EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
                DungeonMenu menu=editors.remove(event.getPlayer().getUniqueId());
                if(menu!=null) markers.hide(menu.draft.get().id());
            }
            @org.bukkit.event.EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent event) {
                if(event.getInventory().getHolder() instanceof RewardMenu reward) reward.capture();
                if(event.getInventory().getHolder() instanceof DungeonMenu menu) menu.closed();
            }
            @org.bukkit.event.EventHandler public void disable(org.bukkit.event.server.PluginDisableEvent event) {
                if(event.getPlugin()==plugin) {editors.clear();dungeonBusy=id->false;}
            }
        },plugin);
    }
    private DungeonMenu editor(String id) {
        DungeonMenu old=editors.get(viewer.getUniqueId());
        if(old!=null&&old.saving()) {tell("busy");return null;}
        if(old!=null&&old.draft.get().id().equals(id)) {
            if(!old.outdated()) return old;
            old.confirmDiscard(()->{
                DungeonDef latest=store.dungeons().get(id);
                if(latest==null) {open();return;}
                DungeonMenu replacement=remember(new DungeonMenu(viewer,latest,this));
                if(replacement!=null) replacement.open(); else open();
            });
            return null;
        }
        DungeonDef definition=store.dungeons().get(id);
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
    void discard(DungeonMenu menu) {
        if(menu.saving()||!editors.remove(viewer.getUniqueId(),menu)) return;
        MenuListener.instance().editLocks().unlock(menu.draft.get().id(),viewer.getUniqueId());
        markers.hide(menu.draft.get().id());
    }
    private DungeonMenu remember(DungeonMenu menu) {
        DungeonMenu old=editors.get(viewer.getUniqueId());
        if(old!=null&&old.saving()) {tell("busy");return null;}
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
        return menu;
    }
    @Override protected int firstContentRow() {return 2;}
    @Override protected void render() {
        super.render();
        var messages = MenuListener.instance().messages();
        set(4,Button.of(Material.MAP,msg("list-heading"),List.of(msg("list-heading-lore")),(p,c)->{}));
        set(11,action("new-dungeon",Material.EMERALD,"",(p,c)->MenuListener.instance().later(this::create)));
        set(15,Button.of(Material.GRAY_DYE,msg("wizard-soon"),List.of(msg("wizard-soon-lore")),(p,c)->{}));
        set(13, Button.of(Material.BOOK, messages.get("gui.common.mob-library"),
                List.of(messages.get("gui.common.mob-library-lore"), messages.get("gui.common.click-lore")),
                (p,c) -> MenuListener.instance().later(() -> new MobLibraryMenu(p, this).open())));
    }
    @Override protected List<DungeonDef> entries() {
        var definitions=new HashMap<>(store.dungeons());
        var current=editors.get(viewer.getUniqueId());
        if(current!=null) definitions.put(current.draft.get().id(),current.draft.get());
        return definitions.values().stream().sorted(Comparator.comparing(DungeonDef::id)).toList();
    }
    @Override protected Button entry(DungeonDef value,int index) {
        boolean occupied=busy(value.id());
        String state=occupied?"state-busy":value.enabled()?"state-enabled":"state-disabled";
        var lore=List.of(msg("dungeon-lore"),msg("dungeon-summary",Placeholder.unparsed("rooms",Integer.toString(value.rooms().size())),
                Placeholder.unparsed("minimum",Integer.toString(value.minPlayers())),
                Placeholder.component("maximum",value.maxPlayers()==0?msg("unlimited"):Component.text(value.maxPlayers())),
                Placeholder.component("state",msg(state))));
        return Button.of(occupied?Material.CLOCK:value.enabled()?Material.LIME_CONCRETE:Material.GRAY_CONCRETE,
                msg("dungeon-label",Placeholder.unparsed("id",value.id()),Placeholder.unparsed("name",value.displayName())),lore,
                (p,c)->MenuListener.instance().later(()->{
            if(busy(value.id())) {
                DungeonDef latest=store.dungeons().get(value.id());
                if(latest==null) {tell("control-invalid");return;}
                // Controls never enter the editor registry or acquire an edit lock.
                MenuListener.instance().editLocks().unlock(value.id(),viewer.getUniqueId());
                new DungeonMenu(viewer,latest,this,true).open();
            } else {
                DungeonMenu menu=editor(value.id());if(menu!=null&&menu.writable()) menu.open();
            }
        }));
    }
    @Override protected void create() {
        Inputs.text(viewer,msg("new-id"),"",32,id->{
            if(!DungeonMenu.validId(id)) {tell("invalid-id");return;}
            if(store.dungeons().containsKey(id)) {MenuListener.instance().messages().send(viewer,"gui.dungeon.duplicate-id",Placeholder.unparsed("id",id));return;}
            var current=editors.get(viewer.getUniqueId());
            if(current!=null&&current.draft.get().id().equals(id)) {if(current.writable()) current.open();return;}
            var defaults=plugin.getServer().getServicesManager().load(PluginConfig.class).defaults();
            var definition=new DungeonDef(id,id,false,null,null,defaults.minPlayers(),defaults.maxPlayers(),
                    defaults.lobbyCountdownSeconds(),defaults.lives(),defaults.keepInventory(),0,defaults.cooldownSeconds(),
                    false,defaults.scaling(),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
            var menu=remember(new DungeonMenu(viewer,definition,this));if(menu!=null) menu.open();
        });
    }
}
