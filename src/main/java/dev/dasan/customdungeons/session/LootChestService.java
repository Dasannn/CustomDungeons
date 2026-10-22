package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Main-thread coordinator. Futures are polled by existing tickers; workers never access Bukkit. */
public final class LootChestService {
    private static final class AmbiguousBlock extends IllegalStateException {
        AmbiguousBlock(String reason) {super(reason);}
    }
    interface Blocks {
        boolean ready(Point point);
        String snapshot(Point point);
        String expectedChest();
        void chest(Point point,String expected);
        default void chest(Point point) {chest(point,expectedChest());}
        boolean matches(Point point,String snapshot,boolean allowContents);
        Inventory inventory(Point point);
        void restore(Point point,String data);
        /** Intent alone never authorizes replacement: require the exact original or this mutation's witness. */
        default void recover(LootBlockRecord record) {
            if(record.preparationCancelled())return;
            Point point=record.position();
            String original=record.sessionData()!=null?record.sessionData():record.placementData();
            String expected=record.expectedData();
            if(original==null)throw new AmbiguousBlock("Loot original snapshot unavailable");
            boolean mutated=expected!=null && matches(point,expected,true);
            boolean unchanged=matches(point,original,false);
            if(!mutated && !unchanged) {
                if(record.sessionActive() || record.placed())throw new AmbiguousBlock("Current block matches neither original nor expected loot mutation");
                return; // An already restored receipt must not overwrite subsequent foreign changes.
            }
            String target=record.placed() && record.sessionData()==null?expected:original;
            if(target==null)throw new AmbiguousBlock("Loot mutation snapshot unavailable");
            if(mutated || !target.equals(original))restore(point,target);
        }
        default void release(Point point) {}
        default void close() {}
    }
    private static final class Entry {
        final LootChestDef definition;
        final DungeonSession session;
        LootBlockRecord record;
        CompletableFuture<Void> write;
        boolean prepared,generated,restoring,applied,mutationAttempted;
        Inventory inventory;
        Entry(LootChestDef definition,DungeonSession session,LootBlockRecord record,CompletableFuture<Void> write) {
            this.definition=definition;this.session=session;this.record=record;this.write=write;
        }
    }
    private final LootPersistence persistence;
    private final Blocks blocks;
    private final Consumer<Throwable> failure;
    private final DoubleSupplier random;
    private final Map<Point,LootBlockRecord> records=new LinkedHashMap<>();
    private final Map<Point,Entry> entries=new LinkedHashMap<>();
    private final Map<Point,CompletableFuture<Void>> recoveryWrites=new HashMap<>();
    private final Set<Point> recovery=new LinkedHashSet<>();
    private final Set<Point> warned=new HashSet<>();
    private final Map<Point,String> ambiguous=new LinkedHashMap<>();
    private record Resolution(CompletableFuture<Void> write,CompletableFuture<Void> result,Runnable report) {}
    private final Map<Point,Resolution> resolutions=new LinkedHashMap<>();
    private final List<CompletableFuture<Void>> editWrites=new ArrayList<>();
    private final Map<Point,Runnable> edits=new LinkedHashMap<>();
    private record Evacuation(DungeonSession session,Map<UUID,Player> players) {}
    private final Map<UUID,Evacuation> evacuation=new HashMap<>();
    private record Placement(Point point,String room,String chest) {}
    private static final class Reconciliation {
        Set<Placement> retained;
        final CompletableFuture<Void> result=new CompletableFuture<>();
        final Map<Point,CompletableFuture<Void>> operations=new LinkedHashMap<>();
        Reconciliation(Set<Placement> retained) {this.retained=retained;}
    }
    private final Map<String,Reconciliation> reconciliations=new LinkedHashMap<>();
    private final Set<Point> paused=new HashSet<>(),waitingChunks=new HashSet<>();
    /** The only lifecycle reconciliation. Published definitions and confirmed durable drafts are its inputs. */
    public CompletableFuture<Void> reconcile(String dungeon,Collection<DungeonDef> definitions) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Loot service closed"));
        var retained=new HashSet<Placement>();
        for(var d:definitions)if(d.id().equals(dungeon))for(var room:d.rooms())for(var chest:room.lootChests())
            if(chest.location()!=null)retained.add(new Placement(chest.location(),room.id(),chest.id()));
        var edit=reconciliations.computeIfAbsent(dungeon,id->new Reconciliation(retained));edit.retained=retained;
        paused.removeIf(p->records.containsKey(p)&&records.get(p).dungeon().equals(dungeon));
        waitingChunks.removeIf(p->records.containsKey(p)&&records.get(p).dungeon().equals(dungeon));
        reconcileTick();changed.run();return edit.result;
    }
    public void reconcileAll(Collection<DungeonDef> definitions) {
        var ids=new LinkedHashSet<String>();records.values().forEach(r->ids.add(r.dungeon()));definitions.forEach(d->ids.add(d.id()));
        for(var id:ids)reconcile(id,definitions);
    }
    private boolean orphan(LootBlockRecord r,Reconciliation edit) {
        return r.placed()&&!edit.retained.contains(new Placement(r.position(),r.room(),r.chest()));
    }
    private void reconcileTick() {
        for(var item:List.copyOf(reconciliations.entrySet())) {
            String dungeon=item.getKey();var edit=item.getValue();
            for(var op:List.copyOf(edit.operations.entrySet()))if(op.getValue().isDone()) {
                edit.operations.remove(op.getKey());
                if(op.getValue().isCompletedExceptionally()&&records.containsKey(op.getKey())&&!ambiguous.containsKey(op.getKey()))paused.add(op.getKey());
            }
            boolean pending=!edit.operations.isEmpty();
            for(var r:List.copyOf(records.values()))if(r.dungeon().equals(dungeon)) {
                var p=r.position();
                boolean retained=edit.retained.contains(new Placement(p,r.room(),r.chest()));
                if(retained&&r.placed()&&!recovery.contains(p)&&!edits.containsKey(p))continue;
                if(recovery.contains(p)||edits.containsKey(p)||entries.containsKey(p)) {pending=true;continue;}
                if(!retained&&!orphan(r,edit))continue;
                pending=true;
                if(paused.contains(p)||waitingChunks.contains(p)||ambiguous.containsKey(p))continue;
                try {
                    if(!blocks.ready(p)) {waitingChunks.add(p);continue;}
                    if(retained) {
                        var chest=new LootChestDef(r.chest(),p,LootChestDef.Trigger.ROOM_CLEAR,List.of());
                        edit.operations.put(p,place(dungeon,r.room(),chest,
                                ()->edit.retained.contains(new Placement(p,r.room(),r.chest())),true));
                    } else edit.operations.put(p,remove(p,()->orphan(r,edit)));
                } catch(RuntimeException error) {paused.add(p);failed(p,error);}
            }
            if(!pending) {reconciliations.remove(dungeon);edit.result.complete(null);}
        }
    }
    /** Called by the chunk completion/listener, rather than retrying unavailable chunks on every tick. */
    public void chunksAvailable(String world) {
        waitingChunks.removeIf(p->p.world().equals(world));reconcileTick();changed.run();
    }
    private boolean closed;
    private Runnable changed=()->{};
    void onPendingChanged(Runnable changed) {this.changed=changed;}
    LootChestService(LootPersistence persistence,Blocks blocks,List<LootBlockRecord> saved,DoubleSupplier random,Consumer<Throwable> failure) {
        this.persistence=persistence;this.blocks=blocks;this.random=random;this.failure=failure;
        for(var r:saved){records.put(r.position(),r);recovery.add(r.position());}
    }
    static Point point(org.bukkit.Location at) {
        return new Point(at.getWorld().getName(),at.getBlockX(),at.getBlockY(),at.getBlockZ(),0,0);
    }
    boolean blocked(String dungeon) {
        return reconciliations.containsKey(dungeon) || recovery.stream().anyMatch(p->records.get(p).dungeon().equals(dungeon))
                || edits.keySet().stream().anyMatch(p->records.get(p).dungeon().equals(dungeon));
    }
    Point inventoryPoint(Inventory inventory) {
        for(var e:entries.values())if(e.inventory!=null && e.inventory.equals(inventory))return e.record.position();
        return null;
    }
    boolean contains(Point p) {var r=records.get(p);return entries.containsKey(p) || recovery.contains(p) || edits.containsKey(p) || r!=null && (r.placed() || r.sessionActive());}
    boolean pending() {return !closed && (!resolutions.isEmpty() || !edits.isEmpty() || !recoveryWrites.isEmpty()
            || recovery.stream().anyMatch(p->!ambiguous.containsKey(p)&&!paused.contains(p)&&!waitingChunks.contains(p))
            || reconciliations.values().stream().anyMatch(e->!e.operations.isEmpty()));}
    /** Explicit administrative discard. Never reads or mutates the current block or its inventory. */
    CompletableFuture<Integer> resolveAmbiguous(String dungeon,BiConsumer<Point,String> report) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Loot service closed"));
        var results=new ArrayList<CompletableFuture<Void>>();
        for(var p:List.copyOf(ambiguous.keySet())) {
            var r=records.get(p);
            if(r==null || !r.dungeon().equals(dungeon) || !recovery.contains(p) || entries.containsKey(p) || edits.containsKey(p))continue;
            var resolution=resolutions.get(p);
            if(resolution==null) {
                String reason=ambiguous.get(p);CompletableFuture<Void> write;
                try {write=persistence.deleteLootBlock(p);}catch(RuntimeException error){write=CompletableFuture.failedFuture(error);}
                resolution=new Resolution(write,new CompletableFuture<>(),()->report.accept(p,reason));resolutions.put(p,resolution);
            }
            results.add(resolution.result());
        }
        changed.run();
        return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenApply(v->results.size());
    }
    private void finishResolutions() {
        for(var p:List.copyOf(resolutions.keySet())) {
            var resolution=resolutions.get(p);if(!resolution.write().isDone())continue;
            resolutions.remove(p);
            try {
                resolution.write().join();records.remove(p);recovery.remove(p);recoveryWrites.remove(p);ambiguous.remove(p);warned.remove(p);
                paused.remove(p);waitingChunks.remove(p);blocks.release(p);resolution.report().run();resolution.result().complete(null);
            } catch(RuntimeException error) {failed(p,error);resolution.result().completeExceptionally(error);}
        }
    }
    private void failed(Point p,Throwable error) {if(warned.add(p))failure.accept(error);}
    private CompletableFuture<Void> save(LootBlockRecord r) {
        if(persistence==null)return CompletableFuture.failedFuture(new IllegalStateException("Loot persistence unavailable"));
        try{return persistence.saveLootBlock(r);}catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
    }
    /** T65 calls this after its own permission/draft checks, with all neighbouring chunks prepared. */
    public CompletableFuture<Void> place(String dungeon,String room,LootChestDef chest) {
        return place(dungeon,room,chest,()->true);
    }
    public CompletableFuture<Void> place(String dungeon,String room,LootChestDef chest,BooleanSupplier current) {
        return place(dungeon,room,chest,current,false);
    }
    private CompletableFuture<Void> place(String dungeon,String room,LootChestDef chest,BooleanSupplier current,boolean reinstating) {
        Point p=chest.location();
        if(!current.getAsBoolean())return CompletableFuture.failedFuture(new IllegalStateException("Stale loot edit"));
        if(closed || !reinstating&&reconciliations.containsKey(dungeon) || entries.containsKey(p) || recovery.contains(p) || edits.containsKey(p)
                || records.containsKey(p) && records.get(p).placed())return CompletableFuture.failedFuture(new IllegalStateException("Loot position busy"));
        if(reinstating) {
            var previous=records.get(p);
            String witness=previous.placementData()!=null?previous.placementData():previous.preparationCancelled()?null:previous.sessionData();
            if(witness==null||!blocks.matches(p,witness,false))return conflict(p,"Loot original changed before reinstatement");
        }
        String original=blocks.snapshot(p); // Inspector rejects occupied containers and unrestorable state.
        var r=new LootBlockRecord(p,dungeon,room,chest.id(),original,null,true,true,blocks.expectedChest());
        return edit(r,()->{
            if(!current.getAsBoolean())throw new IllegalStateException("Stale loot edit");
            if(reinstating) {
                if(!blocks.matches(p,original,false))throw new AmbiguousBlock("Loot original changed during reinstatement backup");
            } else if(!Objects.equals(original,blocks.snapshot(p)))throw new IllegalStateException("Loot block changed during backup");
        },()->blocks.chest(p,r.expectedData()));
    }
    /** Restores the placement original, including undo; never drops inventory contents. */
    public CompletableFuture<Void> remove(Point p) {
        return remove(p,()->true);
    }
    public CompletableFuture<Void> remove(Point p,BooleanSupplier current) {
        var r=records.get(p);
        if(!current.getAsBoolean())return CompletableFuture.failedFuture(new IllegalStateException("Stale loot edit"));
        if(closed || r==null || r.placementData()==null || entries.containsKey(p) || recovery.contains(p) || edits.containsKey(p))
            return CompletableFuture.failedFuture(new IllegalStateException("Loot position busy"));
        if(!r.placed())return CompletableFuture.completedFuture(null);
        String witness=r.sessionData()!=null&&!r.preparationCancelled()?r.sessionData():r.expectedData();
        if(witness==null||!blocks.matches(p,witness,true))return conflict(p,"Loot block changed before removal");
        return edit(r.removed(witness),()->{
            if(!current.getAsBoolean())throw new IllegalStateException("Stale loot edit");
            if(!blocks.matches(p,witness,true))throw new AmbiguousBlock("Loot block changed before removal");
        },()->blocks.restore(p,r.placementData()));
    }
    private CompletableFuture<Void> conflict(Point p,String reason) {
        var error=new AmbiguousBlock(reason);
        recovery.add(p);ambiguous.putIfAbsent(p,reason);blocks.release(p);failed(p,error);
        return CompletableFuture.failedFuture(error);
    }
    private CompletableFuture<Void> edit(LootBlockRecord r,Runnable preflight,Runnable mutate) {
        var result=new CompletableFuture<Void>();var saved=save(r);editWrites.add(saved);Point p=r.position();
        var previous=records.put(p,r);
        var receipt=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
        edits.put(p,()->{
            if(!saved.isDone())return;
            try {
                if(receipt.get()!=null) {
                    if(!receipt.get().isDone())return;
                    receipt.get().join();records.put(p,r.restored());blocks.release(p);edits.remove(p);result.complete(null);return;
                }
                saved.join();if(!blocks.ready(p))return;
                try {preflight.run();} catch(RuntimeException error) {
                    if(error instanceof AmbiguousBlock)throw error;
                    // No mutation occurred. Retire the intent without overwriting the changed block.
                    var cancelled=(previous!=null?previous:new LootBlockRecord(p,r.dungeon(),r.room(),r.chest(),null,null,false,false)).cancelPreparation();
                    records.put(p,cancelled);recovery.add(p);recoveryWrites.put(p,save(cancelled));throw error;
                }
                mutate.run();receipt.set(save(r.restored()));editWrites.add(receipt.get());
                if(receipt.get().isDone()) {
                    receipt.get().join();records.put(p,r.restored());blocks.release(p);edits.remove(p);result.complete(null);
                }
            } catch(RuntimeException e) {
                edits.remove(p);
                if(saved.isCompletedExceptionally()){if(previous==null)records.remove(p);else records.put(p,previous);}
                else recovery.add(p);
                if(e instanceof AmbiguousBlock)ambiguous.putIfAbsent(p,e.getMessage());
                else paused.add(p);
                failed(p,e);result.completeExceptionally(e);
            }
        });
        changed.run();return result;
    }
    boolean prepare(DungeonSession session) {
        if(closed || blocked(session.def().id()))return false;
        try {
            for(var room:session.def().rooms())for(var chest:room.lootChests()) {
                Point p=chest.location();
                if(recovery.contains(p) || edits.containsKey(p))return false;
                var e=entries.get(p);
                if(e==null) {
                    if(!blocks.ready(p))return false;
                    String original=blocks.snapshot(p);var old=records.get(p);
                    if(old!=null && (old.placed() || old.sessionActive()) && !old.dungeon().equals(session.def().id()))throw new IllegalStateException("Loot position owned by another dungeon");
                    var r=new LootBlockRecord(p,session.def().id(),room.id(),chest.id(),old!=null && old.placed()?old.placementData():null,original,old!=null && old.placed(),true,blocks.expectedChest());
                    e=new Entry(chest,session,r,save(r));entries.put(p,e);
                }
                if(e.session!=session)throw new IllegalStateException("Loot position busy");
                if(!e.prepared) {
                    if(!e.write.isDone())return false;
                    e.write.join();records.put(p,e.record);
                    try {
                        if(!Objects.equals(e.record.sessionData(),blocks.snapshot(p)))throw new IllegalStateException("Loot block changed during backup");
                    } catch(RuntimeException error) {cancelPreparation(e);throw error;}
                    // Set before calling Bukkit: a throwing placement may already have changed the block.
                    e.mutationAttempted=true;blocks.chest(p,e.record.expectedData());e.inventory=blocks.inventory(p);e.prepared=true;
                }
            }
            return true;
        } catch(RuntimeException error) {failure.accept(error);session.finish(false,true);return false;}
    }
    void trigger(DungeonSession session,String room,LootChestDef.Trigger trigger) {
        if(session.state().state()!=SessionState.RUNNING || session.introActive()
                || !session.def().rooms().get(session.roomIndex()).id().equals(room))return;
        for(var e:entries.values())if(e.session==session && e.record.room().equals(room) && e.definition.trigger()==trigger && e.prepared && !e.generated) {
            // Mark first: an exception cannot cause a second draw.
            e.generated=true;e.inventory.setContents(LootRoll.generate(e.definition.entries(),random));
        }
    }
    void finish(DungeonSession session) {
        if(session.state().state()==SessionState.COMPLETED) {
            var players=new LinkedHashMap<UUID,Player>();for(var player:session.players())players.put(player.getUniqueId(),player);
            evacuation.put(session.id(),new Evacuation(session,players));
        }
        else closeViews(session);
    }
    void departing(Player player,org.bukkit.Location to) {
        for(var evacuation:evacuation.values())if(evacuation.players().containsKey(player.getUniqueId())
                && !DungeonSessionRuntime.containsDungeon(evacuation.session().def(),to)) {
            revoke(player.getUniqueId());return;
        }
    }
    void revoke(UUID player) {
        for(var allowed:evacuation.values())allowed.players().remove(player);
        for(var e:entries.values()) {
            if(!e.prepared)continue;
            var inventory=e.inventory;
            for(var viewer:List.copyOf(inventory.getViewers()))if(viewer.getUniqueId().equals(player))viewer.closeInventory();
        }
    }
    boolean allowed(Point p,Player player,boolean extract) {
        var e=entries.get(p);
        if(e==null || !e.prepared || !e.generated || e.restoring || !player.isOnline() || player.isDead()
                || player.getGameMode()==org.bukkit.GameMode.SPECTATOR || extract && e.session.testMode())return false;
        var s=e.session;
        if(s.state().state()==SessionState.RUNNING)return !s.introActive() && s.survivors().contains(player.getUniqueId()) && s.livesLeft(player.getUniqueId())>0;
        var allowed=evacuation.get(s.id());
        if(s.state().state()!=SessionState.COMPLETED || allowed==null || !allowed.players().containsKey(player.getUniqueId()))return false;
        if(!DungeonSessionRuntime.containsDungeon(s.def(),player.getLocation())) {allowed.players().remove(player.getUniqueId());return false;}
        return true;
    }
    private void closeViews(DungeonSession session) {
        for(var e:entries.values())if(e.session==session && e.inventory!=null)
            for(var viewer:List.copyOf(e.inventory.getViewers()))viewer.closeInventory();
    }
    boolean reset(DungeonSession session) {
        evacuation.remove(session.id());closeViews(session);
        boolean done=true;
        for(var e:List.copyOf(entries.values()))if(e.session==session) {
            e.restoring=true;
            if(!restore(e))done=false;
        }
        reconcileTick();changed.run();return done;
    }
    private void cancelPreparation(Entry e) {
        e.record=e.record.cancelPreparation();records.put(e.record.position(),e.record);e.write=save(e.record);
    }
    private boolean restore(Entry e) {
        Point p=e.record.position();
        try {
            if(!e.write.isDone())return false;
            if(e.record.preparationCancelled()) {
                e.write.join();blocks.release(p);records.put(p,e.record);warned.remove(p);entries.remove(p);return true;
            }
            if(!e.applied) {
                if(e.write.isCompletedExceptionally()) {blocks.release(p);entries.remove(p);return true;} // No mutation was allowed.
                if(!e.mutationAttempted) {cancelPreparation(e);return false;}
                if(!blocks.ready(p))return false;
                blocks.recover(e.record);e.applied=true;
                e.write=save(e.record.restored());return false;
            }
            e.write.join();blocks.release(p);records.put(p,e.record.restored());warned.remove(p);entries.remove(p);return true;
        } catch(RuntimeException error) {
            failed(p,error);
            if(e.record.preparationCancelled())e.write=save(e.record);
            else if(e.applied)e.write=save(e.record.restored());
            return false;
        }
    }
    void tick() {
        if(closed)return;
        finishResolutions();
        reconcileTick();
        editWrites.removeIf(CompletableFuture::isDone);
        for(var action:List.copyOf(edits.values()))action.run();
        for(var p:List.copyOf(recovery))try {
            if(resolutions.containsKey(p)||ambiguous.containsKey(p)||waitingChunks.contains(p))continue;
            var write=recoveryWrites.get(p);
            if(write!=null) {
                if(!write.isDone())continue;
                write.join();records.put(p,records.get(p).restored());blocks.release(p);recoveryWrites.remove(p);recovery.remove(p);ambiguous.remove(p);paused.remove(p);warned.remove(p);continue;
            }
            if(paused.contains(p))continue;
            var r=records.get(p);
            if(!r.preparationCancelled()) {
                if(!blocks.ready(p)) {waitingChunks.add(p);continue;}
                blocks.recover(r);
                ambiguous.remove(p);
            }
            recoveryWrites.put(p,save(r.restored()));
        } catch(RuntimeException error) {if(error instanceof AmbiguousBlock)ambiguous.putIfAbsent(p,error.getMessage());else paused.add(p);failed(p,error);recoveryWrites.remove(p);}
        reconcileTick();
        for(var evacuation:List.copyOf(evacuation.values()))for(var player:List.copyOf(evacuation.players().values()))
            if(!player.isOnline() || player.isDead() || player.getGameMode()==org.bukkit.GameMode.SPECTATOR
                    || !DungeonSessionRuntime.containsDungeon(evacuation.session().def(),player.getLocation()))revoke(player.getUniqueId());
    }

    /** Shutdown may wait for accepted persistence. Unavailable worlds retain their journal. */
    void shutdown() {
        for(var resolution:resolutions.values())resolution.write().handle((v,t)->null).join();
        finishResolutions();
        for(var write:editWrites)write.handle((v,t)->null).join();
        for(var action:List.copyOf(edits.values()))action.run();
        for(var e:List.copyOf(entries.values())) {
            try{e.write.handle((v,t)->null).join();e.session.beginShutdownReset();reset(e.session);if(e.applied || e.record.preparationCancelled())e.write.join();}catch(RuntimeException error){failure.accept(error);}
        }
        for(var write:editWrites)write.handle((v,t)->null).join();
        for(var write:recoveryWrites.values())write.handle((v,t)->null).join();
        closed=true;blocks.close();
    }
}
