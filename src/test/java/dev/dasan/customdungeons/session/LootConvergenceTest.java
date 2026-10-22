package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.construction.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LootConvergenceTest {
    @TempDir Path directory;
    final LootChestServiceTest fixture=new LootChestServiceTest();
    final DungeonDef definition=new LootDurableSourcesTest().definition();
    final RoomDef room=definition.rooms().getFirst();
    final LootChestDef chest=room.lootChests().getFirst();
    void ticks() {for(int i=0;i<12;i++)fixture.loot.tick();}
    void placeThenRemove() {
        var placed=fixture.loot.place(definition.id(),room.id(),chest);ticks();placed.join();
        var removed=fixture.loot.remove(chest.location());ticks();removed.join();
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
        assertFalse(fixture.store.records.get(chest.location()).placed());
    }
    void complete(CompletableFuture<Void> result) {ticks();assertTrue(result.isDone());result.join();}
    @Test void discardedRemovalReinstatesPublishedPlacementOnlyAfterItsBackupIsConfirmed() {
        placeThenRemove();var write=new CompletableFuture<Void>();fixture.store.next=write;
        var result=fixture.loot.reconcile(definition.id(),List.of(definition));
        assertFalse(result.isDone());assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
        write.complete(null);complete(result);
        assertEquals("minecraft:chest[type=single]",fixture.blocks.data);
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.store.records.get(chest.location()).placementData());
        int writes=fixture.store.writes;complete(fixture.loot.reconcile(definition.id(),List.of(definition)));
        assertEquals(writes,fixture.store.writes);
        complete(fixture.loot.reconcile(definition.id(),List.of()));assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
    }
    @Test void confirmedBuildWriteReconcilesAgainAfterDisconnectCleanupUsingReloadableDurableState() {
        var placed=fixture.loot.place(definition.id(),room.id(),chest);complete(placed);
        var worker=new ArrayDeque<Runnable>();
        try(var journal=new BuildJournal(directory,worker::add)) {
            journal.onDurableChange(id->fixture.loot.reconcile(id,journal.durableDefinitions()));
            var write=journal.save(UUID.randomUUID(),new BuildState(definition).snapshot());
            assertFalse(write.isDone());assertTrue(journal.durableDefinitions().isEmpty());
            complete(fixture.loot.reconcile(definition.id(),journal.durableDefinitions()));
            assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
            worker.remove().run();write.join();ticks();
            assertEquals("minecraft:chest[type=single]",fixture.blocks.data);
            assertTrue(fixture.store.records.get(chest.location()).placed());
            try(var reloaded=new BuildJournal(directory,Runnable::run)) {
                var restart=new LootChestService(fixture.store,fixture.blocks,List.copyOf(fixture.store.records.values()),()->0,fixture.failures::add);
                restart.reconcileAll(reloaded.durableDefinitions());for(int i=0;i<12;i++)restart.tick();
                assertEquals("minecraft:chest[type=single]",fixture.blocks.data);assertFalse(restart.blocked(definition.id()));
                assertEquals(LootChestServiceTest.ORIGINAL,fixture.store.records.get(chest.location()).placementData());
            }
        }
    }
    @Test void reinstatementRejectsForeignBlockWithoutRepeatedWritesAndResetPreservesIt() {
        placeThenRemove();fixture.blocks.data="minecraft:diamond_block";fixture.blocks.ownedExpectation=null;
        int writes=fixture.store.writes;var result=fixture.loot.reconcile(definition.id(),List.of(definition));
        ticks();ticks();assertEquals(writes,fixture.store.writes);assertFalse(result.isDone());
        assertFalse(fixture.loot.pending());
        var reset=fixture.loot.resolveAmbiguous(definition.id(),(p,r)->{});ticks();
        assertEquals(1,reset.join());assertTrue(result.isDone());assertFalse(fixture.loot.blocked(definition.id()));
        assertEquals("minecraft:diamond_block",fixture.blocks.data);
    }
    @Test void retiredAuthorityDuringReinstatementBackupDoesNotPlaceAnOrphan() {
        placeThenRemove();var write=new CompletableFuture<Void>();fixture.store.next=write;
        var result=fixture.loot.reconcile(definition.id(),List.of(definition));assertFalse(result.isDone());
        assertSame(result,fixture.loot.reconcile(definition.id(),List.of()));
        write.complete(null);complete(result);
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);assertFalse(fixture.loot.blocked(definition.id()));
    }
    @Test void foreignReplacementDuringReinstatementBackupRemainsAmbiguousUntilReset() {
        placeThenRemove();var write=new CompletableFuture<Void>();fixture.store.next=write;
        var result=fixture.loot.reconcile(definition.id(),List.of(definition));assertFalse(result.isDone());
        fixture.blocks.data="minecraft:diamond_block";fixture.blocks.ownedExpectation=null;write.complete(null);
        ticks();int writes=fixture.store.writes;ticks();assertEquals(writes,fixture.store.writes);
        assertFalse(result.isDone());assertFalse(fixture.loot.pending());
        var reset=fixture.loot.resolveAmbiguous(definition.id(),(p,r)->{});ticks();assertEquals(1,reset.join());
        assertEquals("minecraft:diamond_block",fixture.blocks.data);assertTrue(result.isDone());
    }
}
