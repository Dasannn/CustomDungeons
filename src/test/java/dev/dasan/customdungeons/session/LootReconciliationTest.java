package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LootReconciliationTest {
    LootChestServiceTest fixture=new LootChestServiceTest();
    void place() {
        var result=fixture.loot.place("dungeon","first",fixture.chest(LootChestDef.Trigger.ROOM_ENTER));
        fixture.loot.tick();result.join();
    }
    void ticks(LootChestService service,int count) {for(int i=0;i<count;i++)service.tick();}
    @Test void restorationCompletesBeforeReceiptAndKeepsExactPlacementOriginal() {
        place();var pending=new CompletableFuture<Void>();fixture.store.next=pending;
        var result=fixture.loot.reconcile("dungeon",List.of());assertFalse(result.isDone());
        assertEquals("minecraft:chest[type=single]",fixture.blocks.data);
        pending.complete(null);ticks(fixture.loot,5);result.join();
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);assertFalse(fixture.loot.blocked("dungeon"));
    }
    @Test void crashBeforePublicationWithoutQuitRestoresOrphanFromT63RecordsAlone() {
        place();var restart=new LootChestService(fixture.store,fixture.blocks,List.copyOf(fixture.store.records.values()),()->0,fixture.failures::add);
        restart.reconcileAll(List.of());ticks(restart,20);
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);assertFalse(restart.blocked("dungeon"));
    }
    @Test void permanentForeignBlockDoesNotWriteRepeatedlyAndResetKeepsIt() {
        place();fixture.blocks.data="minecraft:diamond_block";fixture.blocks.ownedExpectation=null;int before=fixture.store.writes;
        var result=fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,40);
        assertEquals(before,fixture.store.writes);assertEquals(1,fixture.failures.size());assertFalse(fixture.loot.pending());
        fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,40);assertEquals(before,fixture.store.writes);
        var reset=fixture.loot.resolveAmbiguous("dungeon",(p,r)->{});ticks(fixture.loot,3);
        assertEquals(1,reset.join());assertTrue(result.isDone());assertFalse(fixture.loot.blocked("dungeon"));
        assertEquals("minecraft:diamond_block",fixture.blocks.data);
    }
    @Test void transientRestorationFailureWaitsForNextReconciliationInsteadOfTickRetries() {
        place();fixture.blocks.failRestore=true;var result=fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,3);
        int writes=fixture.store.writes;ticks(fixture.loot,40);assertEquals(writes,fixture.store.writes);assertFalse(fixture.loot.pending());
        fixture.blocks.failRestore=false;assertSame(result,fixture.loot.reconcile("dungeon",List.of()));ticks(fixture.loot,10);
        result.join();assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
    }
    @Test void unloadedChunkWaitsForAvailabilityEvent() {
        place();fixture.blocks.ready=false;int writes=fixture.store.writes;
        var result=fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,40);assertEquals(writes,fixture.store.writes);assertFalse(fixture.loot.pending());
        fixture.blocks.ready=true;ticks(fixture.loot,20);assertFalse(result.isDone());
        fixture.loot.chunksAvailable("world");ticks(fixture.loot,4);result.join();assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
    }
    @Test void repeatedReconciliationSharesOneOperationAndHasNoPlayerAuthorization() {
        place();var pending=new CompletableFuture<Void>();fixture.store.next=pending;int before=fixture.store.writes;
        var result=fixture.loot.reconcile("dungeon",List.of());
        for(int i=0;i<20;i++)assertSame(result,fixture.loot.reconcile("dungeon",List.of()));
        assertEquals(before+1,fixture.store.writes);pending.complete(null);ticks(fixture.loot,6);result.join();
        assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
    }
    @Test void failedDatabaseWriteWaitsForNewReconciliation() {
        place();fixture.store.next=CompletableFuture.failedFuture(new IllegalStateException("offline"));
        var result=fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,3);int writes=fixture.store.writes;
        ticks(fixture.loot,40);assertEquals(writes,fixture.store.writes);assertFalse(result.isDone());
        fixture.loot.reconcile("dungeon",List.of());ticks(fixture.loot,8);result.join();assertEquals(LootChestServiceTest.ORIGINAL,fixture.blocks.data);
    }
}
