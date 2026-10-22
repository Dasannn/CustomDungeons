package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;

/** Authoring adapter only: T63 owns snapshots, mutation, receipts and crash recovery. */
final class LootPlacement {
    private LootPlacement() {}
    record Chest(String room,LootChestDef definition) {}
    static Map<String,Chest> chests(DungeonDef d) {
        var result=new LinkedHashMap<String,Chest>();
        for(var room:d.rooms())for(var chest:room.lootChests())result.put(chest.id(),new Chest(room.id(),chest));
        return result;
    }
    static boolean changed(DungeonDef before,DungeonDef after) {
        var a=chests(before);var b=chests(after);
        return !positions(a).equals(positions(b));
    }
    private static Map<String,Point> positions(Map<String,Chest> values) {
        var result=new HashMap<String,Point>();values.forEach((id,c)->{if(c.definition().location()!=null)result.put(c.room()+":"+id,c.definition().location());});return result;
    }
    static void apply(DungeonMenu root,DungeonDef target,Runnable accept) {
        if(!root.writable()||root.lootPending||!root.viewerPlayer().hasPermission("customdungeons.admin.tools"))return;
        var before=root.draft.get();
        var manager=root.services.plugin.sessionManager();
        var service=manager==null?null:manager.loot();
        if(service==null) {root.tell("loot-placement-failed");return;}
        root.beginLoot();
        BooleanSupplier current=()->root.services.plugin.isEnabled()&&root.list.current(root)&&root.viewerPlayer().isOnline()
                &&root.viewerPlayer().hasPermission("customdungeons.admin.tools")&&root.writable()&&root.draft.get().equals(before);
        var a=chests(before);var b=chests(target);
        var actions=new ArrayList<java.util.function.Supplier<CompletableFuture<Void>>>();
        var points=new LinkedHashSet<Point>();
        for(var entry:a.entrySet()) {
            var old=entry.getValue().definition();var next=b.get(entry.getKey());
            if(old.location()!=null&&(next==null||!Objects.equals(old.location(),next.definition().location()))) {
                points.add(old.location());actions.add(()->service.remove(old.location(),current));
            }
        }
        for(var entry:b.entrySet()) {
            var next=entry.getValue();var old=a.get(entry.getKey());
            if(next.definition().location()!=null&&(old==null||!Objects.equals(old.definition().location(),next.definition().location()))) {
                points.add(next.definition().location());actions.add(()->{
                int room=new dev.dasan.customdungeons.tool.EditOrigin(dev.dasan.customdungeons.tool.EditOrigin.Kind.ROOM,next.room()).index(target);
                String path="rooms["+room+"].loot-chests["+target.rooms().get(room).lootChests().indexOf(next.definition())+"].location";
                var errors=new Validator().validate(target,root.services.store.mobs(),root.services.store.spawnerPresets()).stream()
                        .filter(e->e.path().equals(path)||e.path().endsWith(".location")&&(e.messageKey().equals("validation.loot-conflict")||e.messageKey().equals("validation.loot-double"))).toList();
                if(root.services.store.dungeons().values().stream().filter(d->!d.id().equals(target.id())).flatMap(d->d.rooms().stream()).flatMap(r->r.lootChests().stream()).anyMatch(c->Objects.equals(c.location(),next.definition().location())))
                    return CompletableFuture.failedFuture(new IllegalStateException("Loot position belongs to another dungeon"));
                if(!errors.isEmpty())return CompletableFuture.failedFuture(new IllegalStateException("Invalid loot location"));
                var inspected=new BukkitLootBlockInspector().inspect(next.definition().location());
                if(!inspected.prepared()||!inspected.restorable()||inspected.doubleChest())return CompletableFuture.failedFuture(new IllegalStateException("Loot block unavailable"));
                return service.place(target.id(),next.room(),next.definition(),current);
                });
            }
        }
        try {
            var preparations=new ArrayList<CompletableFuture<?>>();
            for(var p:points) {
                var world=Bukkit.getWorld(p.world());if(world==null) {root.endLoot();root.tell("loot-placement-failed");return;}
                for(int[] offset:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}})
                    preparations.add(world.getChunkAtAsync(((int)p.x()+offset[0])>>4,((int)p.z()+offset[1])>>4));
            }
            CompletableFuture.allOf(preparations.toArray(CompletableFuture[]::new)).whenComplete((v,error)->main(root,()->{
                if(error!=null||!current.getAsBoolean()) {finish(root,false,accept);return;}
                next(root,actions,0,current,accept);
            }));
        } catch(RuntimeException failure) {finish(root,false,accept);}
    }
    static void reconcile(DungeonMenu root,Runnable accept) {
        if(root.lootPending)return;
        if(!root.viewerPlayer().isOnline()||!root.viewerPlayer().hasPermission("customdungeons.admin.edit")
                ||!root.viewerPlayer().hasPermission("customdungeons.admin.tools")||root.services.store.isReloading()
                ||root.services.busy(root.draft.get().id()))return;
        var manager=root.services.plugin.sessionManager();var service=manager==null?null:manager.loot();
        if(service==null) {root.tell("loot-placement-failed");return;}
        root.beginLoot();
        root.lootDraftWrite.whenComplete((v,error)->main(root,()->{
            if(error!=null) {root.endLoot();root.tell("loot-placement-failed");return;}
            service.reconcile(root.draft.get().id(),dev.dasan.customdungeons.session.SessionManager.lootDefinitions(root.services.plugin,root.services.store))
                    .whenComplete((ignored,failure)->main(root,()->{
                        root.endLoot();
                        if(root.list.current(root)&&root.viewerPlayer().isOnline()) {
                            if(failure==null)accept.run();else root.tell("loot-placement-failed");
                        }
                    }));
        }));
    }
    private static void next(DungeonMenu root,List<java.util.function.Supplier<CompletableFuture<Void>>> actions,int index,BooleanSupplier current,Runnable accept) {
        if(index==actions.size()) {finish(root,true,accept);return;}
        if(!current.getAsBoolean()) {finish(root,false,accept);return;}
        try {actions.get(index).get().whenComplete((v,error)->main(root,()->{if(error!=null)finish(root,false,accept);else next(root,actions,index+1,current,accept);}));}
        catch(RuntimeException error) {finish(root,false,accept);}
    }
    private static void finish(DungeonMenu root,boolean success,Runnable accept) {
        try {
            if(success&&root.list.current(root)&&root.writable())accept.run();
            else if(root.viewerPlayer().isOnline())root.tell("loot-placement-failed");
        } finally {root.endLoot();if(!root.list.current(root))root.reconcileLoot();}
    }
    private static void main(DungeonMenu root,Runnable action) {
        if(!root.services.plugin.isEnabled())return;
        if(Bukkit.isPrimaryThread())action.run();
        else root.services.plugin.getServer().getScheduler().runTask(root.services.plugin,()->{if(root.services.plugin.isEnabled())action.run();});
    }
}
