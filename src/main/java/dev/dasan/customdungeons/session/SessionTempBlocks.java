package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.runtime.TempBlocks;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Journal before mutation; completions are applied by the existing session ticker. */
public final class SessionTempBlocks implements TempBlocks {
    private record Position(String world,int x,int y,int z) {
        static Position of(Block block) { return new Position(block.getWorld().getName(),block.getX(),block.getY(),block.getZ()); }
    }
    /** Shared by the manager: coordinates stay ordered across consecutive sessions. */
    static final class Journal {
        private record Done(Position position,CompletableFuture<Void> operation) {}
        private final Map<Position,CompletableFuture<Void>> tails=new HashMap<>();
        private final Set<Position> reserved=new HashSet<>();
        private final Queue<Done> completed=new ConcurrentLinkedQueue<>();
        boolean reserve(Position position) { drain(); return reserved.add(position); }
        void release(Position position) { reserved.remove(position); }
        CompletableFuture<Void> enqueue(Position position,java.util.function.Supplier<CompletableFuture<Void>> operation) {
            drain();
            var previous=tails.getOrDefault(position,CompletableFuture.completedFuture(null));
            var next=previous.handle((unused,error) -> null).thenCompose(unused -> operation.get());
            tails.put(position,next);
            next.whenComplete((unused,error) -> completed.add(new Done(position,next)));
            return next;
        }
        void drain() {
            Done done;
            while ((done=completed.poll())!=null) tails.remove(done.position(),done.operation());
        }
    }
    private static final class Entry {
        final Block block;
        final Position position;
        final BlockData original;
        BlockData replacement;
        boolean door;
        final int ttl;
        final CompletableFuture<Void> saved;
        long expires = Long.MAX_VALUE;
        boolean placed;
        Entry(Block block,Position position,BlockData original,BlockData replacement,int ttl,CompletableFuture<Void> saved) {
            this.block=block; this.position=position; this.original=original; this.replacement=replacement; this.ttl=ttl; this.saved=saved;
        }
    }
    private final Map<Block,Entry> entries = new LinkedHashMap<>();
    private final Queue<Entry> ready = new ConcurrentLinkedQueue<>();
    private final List<CompletableFuture<Void>> removals = new ArrayList<>();
    private final Storage storage;
    private final Journal journal;
    private final Consumer<Throwable> failure;
    public SessionTempBlocks(Storage storage, Consumer<Throwable> failure) { this(storage,failure,new Journal()); }
    SessionTempBlocks(Storage storage, Consumer<Throwable> failure, Journal journal) { this.storage = storage; this.failure = failure; this.journal=journal; }
    public boolean place(Block block, BlockData data, int ttlTicks) {
        if (!block.isEmpty() || entries.containsKey(block)) return false;
        return register(block,data,ttlTicks,false);
    }
    /** Door-only replacement: unlike ability placement, this may remove an existing solid block. */
    void openDoor(Block block, BlockData air) {
        Entry entry=entries.get(block);
        if (entry != null) {
            entry.replacement=air.clone(); entry.door=true;
            if (entry.placed) block.setBlockData(entry.replacement,false);
        } else if (!block.isEmpty()) register(block,air,Integer.MAX_VALUE,true);
    }
    private boolean register(Block block,BlockData data,int ttlTicks,boolean door) {
        Position position=Position.of(block);
        BlockData original=block.getBlockData().clone(), replacement=data.clone();
        var record=new TempBlockRecord(position.world(),position.x(),position.y(),position.z(),original.getAsString());
        if (!journal.reserve(position)) return false;
        var saved=journal.enqueue(position,() -> storage.addTempBlock(record));
        Entry entry=new Entry(block,position,original,replacement,ttlTicks,saved);
        entry.door=door;
        entries.put(block,entry);
        saved.whenComplete((unused,error) -> { if (error != null) failure.accept(error); ready.add(entry); });
        return true;
    }
    public void tick(long now) {
        journal.drain();
        Entry entry;
        while ((entry=ready.poll()) != null) {
            if (entries.get(entry.block)!=entry) continue;
            if (entry.saved.isCompletedExceptionally()) { restore(entry.block); continue; }
            // Another plugin/player may have filled this block while the journal was written.
            if (entry.door ? !entry.block.getBlockData().getAsString().equals(entry.original.getAsString()) : !entry.block.isEmpty()) {
                restore(entry.block); continue;
            }
            entry.block.setBlockData(entry.replacement,false); entry.placed=true;
            entry.expires=entry.ttl==Integer.MAX_VALUE ? Long.MAX_VALUE : now+Math.max(1,entry.ttl);
        }
        for (Entry value : List.copyOf(entries.values())) if (now>=value.expires) restore(value.block);
        removals.removeIf(CompletableFuture::isDone);
    }
    void restore(Block block) {
        Entry entry=entries.remove(block); if (entry==null) return;
        if (entry.placed) block.setBlockData(entry.original,false);
        // Release the reservation now, while retaining the database ordering for its successor.
        journal.release(entry.position);
        Position position=entry.position;
        var removal=journal.enqueue(position,() -> storage.removeTempBlock(position.world(),position.x(),position.y(),position.z()));
        removal.exceptionally(error -> { failure.accept(error); return null; }); removals.add(removal);
    }
    public void restoreAll() { for (Block block : List.copyOf(entries.keySet())) restore(block); }
    /** Shutdown only; ensures chained deletions are accepted before Storage.close(). */
    boolean drained() {
        if (!removals.stream().allMatch(CompletableFuture::isDone)) return false;
        ready.clear(); return true;
    }
    void flushOnDisable() { CompletableFuture.allOf(removals.toArray(CompletableFuture[]::new)).exceptionally(error -> null).join(); ready.clear(); }
}
