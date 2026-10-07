package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.PendingExitRecord;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingExitRecoveryTest {
    private static final NamespacedKey APPLIED=new NamespacedKey("customdungeons","exit_applied");
    /** Model both APIs faithfully: the old method consumes; reads and conditional acknowledgements do not. */
    private AtomicReference<PendingExitRecord> durableExit(DisconnectRecoveryTest.Fixture t) {
        var saved=new AtomicReference<>(new PendingExitRecord(UUID.randomUUID(),t.target.exit()));
        when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(t.storage.pendingExit(any())).thenAnswer(c->CompletableFuture.completedFuture(Optional.ofNullable(saved.get())));
        when(t.storage.takePendingExit(any())).thenAnswer(c->{
            var taken=saved.getAndSet(null);return CompletableFuture.completedFuture(Optional.ofNullable(taken).map(PendingExitRecord::exit));
        });
        when(t.storage.clearPendingExit(any(),any())).thenAnswer(c->{
            var record=saved.get();if(record!=null && record.id().equals(c.getArgument(1)))saved.compareAndSet(record,null);
            return CompletableFuture.completedFuture(null);
        });
        when(t.storage.addPendingExit(any(),any())).thenAnswer(c->{
            saved.set(new PendingExitRecord(UUID.randomUUID(),c.getArgument(1)));return CompletableFuture.completedFuture(null);
        });
        return saved;
    }
    private Runnable publication(DisconnectRecoveryTest.Fixture t) {
        var published=ArgumentCaptor.forClass(Runnable.class);verify(t.f.definitions).onReload(published.capture());return published.getValue();
    }
    private void failedLoadJoin(DisconnectRecoveryTest.Fixture t) {
        var definitions=new CompletableFuture<Void>();when(t.f.definitions.isReloading()).thenReturn(true);
        when(t.f.definitions.reloadCompletion()).thenReturn(definitions);t.manager.joined(t.player);
        when(t.f.definitions.isReloading()).thenReturn(false);
        definitions.completeExceptionally(new IllegalStateException("temporary load failure"));
    }
    private Chunk exitChunk(DisconnectRecoveryTest.Fixture t) {
        var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);when(chunk.getX()).thenReturn(6);return chunk;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void publicationSupersedesALateQueryOrChunkWithoutLosingTheDurableExit(boolean waitingForChunk) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();var published=publication(t);
            var oldQuery=new CompletableFuture<Optional<PendingExitRecord>>();
            var currentQuery=new CompletableFuture<Optional<PendingExitRecord>>();
            var oldChunk=new CompletableFuture<Chunk>();var currentChunk=new CompletableFuture<Chunk>();
            if(waitingForChunk) {
                when(t.f.world.isChunkLoaded(6,0)).thenReturn(false);
                when(t.f.world.getChunkAtAsync(6,0)).thenReturn(oldChunk,currentChunk);
            } else when(t.storage.pendingExit(any())).thenReturn(oldQuery,currentQuery);
            failedLoadJoin(t);published.run();assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            if(waitingForChunk)oldChunk.complete(exitChunk(t));else oldQuery.complete(Optional.of(record));
            verify(t.player,never()).teleport(any(Location.class));
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()),"The obsolete callback must not release the latest guard");
            assertEquals(record,saved.get());
            if(waitingForChunk)currentChunk.complete(exitChunk(t));else currentQuery.complete(Optional.of(record));
            verify(t.player,times(1)).teleport(argThat((Location at)->at.getX()==99));
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));assertEquals(record,saved.get());
            verify(t.storage,times(2)).pendingExit(any());verify(t.storage,never()).takePendingExit(any());
            verify(t.storage,never()).clearPendingExit(any(),any());verify(t.storage,never()).addPendingExit(any(),any());
            published.run();verify(t.player,times(1)).teleport(any(Location.class));
        }
    }
    @Test void aFallbackThatAlreadyReturnedThePlayerIsNotRepeatedOnPublication() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();var published=publication(t);
            failedLoadJoin(t);verify(t.player).teleport(argThat((Location at)->at.getX()==99));
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId())); // no penalty remains after this completed fallback
            published.run();published.run();
            verify(t.player,times(1)).teleport(any(Location.class));verify(t.storage,never()).takePendingExit(any());
            verify(t.storage,never()).clearPendingExit(any(),any());assertEquals(record,saved.get());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void aLateExitReadStillWaitsForTheDisconnectQueryBeforeTeleporting() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();
            var old=new CompletableFuture<Optional<PendingExitRecord>>();
            when(t.storage.pendingExit(any())).thenReturn(old,CompletableFuture.completedFuture(Optional.of(record)));
            var read=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.DisconnectRecord>>();
            when(t.storage.disconnect(any())).thenReturn(read);
            var published=publication(t);failedLoadJoin(t);published.run();old.complete(Optional.of(record));
            verify(t.player,never()).teleport(any(Location.class));assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            read.complete(Optional.empty());
            verify(t.player,times(1)).teleport(argThat((Location at)->at.getX()==99));
            verify(t.storage,never()).takePendingExit(any());assertEquals(record,saved.get());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void reconnectDuringChunkLoadingRetainsTheExitAndOnlyTheLatestConnectionReturns() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();
            var oldChunk=new CompletableFuture<Chunk>();var currentChunk=new CompletableFuture<Chunk>();
            when(t.f.world.isChunkLoaded(6,0)).thenReturn(false);
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(oldChunk,currentChunk);
            t.manager.joined(t.player);t.manager.disconnected(t.player);t.manager.joined(t.player);
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()),"The latest connection must still await its exit chunk");
            oldChunk.complete(exitChunk(t));verify(t.player,never()).teleport(any(Location.class));
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));assertEquals(record,saved.get());
            verify(t.storage,never()).addPendingExit(any(),any());verify(t.storage,never()).clearPendingExit(any(),any());
            currentChunk.complete(exitChunk(t));
            verify(t.player,times(1)).teleport(argThat((Location at)->at.getX()==99));
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));assertEquals(record,saved.get());
            verify(t.storage,never()).takePendingExit(any());
        }
    }
    @Test void shutdownBeforeChunkCompletionLeavesTheExitPersisted() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();
            var chunk=new CompletableFuture<Chunk>();when(t.f.world.isChunkLoaded(6,0)).thenReturn(false);
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(chunk);
            t.manager.joined(t.player);t.manager.shutdown();
            assertEquals(record,saved.get(),"An unapplied return must remain in storage across shutdown");
            chunk.complete(exitChunk(t));verify(t.player,never()).teleport(any(Location.class));assertEquals(record,saved.get());
            verify(t.storage,never()).takePendingExit(any());verify(t.storage,never()).clearPendingExit(any(),any());
            verify(t.storage,never()).addPendingExit(any(),any());assertFalse(t.data.containsKey(APPLIED));
        }
    }
    @Test void aCompletedReturnRemainsDurableUntilARealLoginConfirmsTheVanillaReceipt() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();t.manager.joined(t.player);
            assertEquals(record.id().toString(),t.data.get(APPLIED));assertEquals(record,saved.get());
            t.manager.connected(t.player); // re-enable sees an in-memory marker, not a vanilla disk confirmation
            verify(t.player,times(1)).teleport(any(Location.class));verify(t.storage,never()).clearPendingExit(any(),any());
            assertEquals(record,saved.get());t.manager.disconnected(t.player);t.manager.joined(t.player);
            verify(t.storage).clearPendingExit(t.player.getUniqueId(),record.id());assertNull(saved.get());
            verify(t.player,times(1)).teleport(any(Location.class));assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void aNewExitAtTheSamePointIsNotAcknowledgedByThePreviousVanillaReceipt() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);t.manager.joined(t.player);
            var latest=new PendingExitRecord(UUID.randomUUID(),t.target.exit());saved.set(latest);
            t.manager.disconnected(t.player);t.manager.joined(t.player);
            verify(t.player,times(2)).teleport(argThat((Location at)->at.getX()==99));assertEquals(latest,saved.get());
            verify(t.storage,never()).clearPendingExit(any(),eq(latest.id()));
        }
    }
    @Test void cancellationOfEveryReturnDestinationRetainsTheRecordAndEntryGuard() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();when(t.player.teleport(any(Location.class))).thenReturn(false);
            t.manager.joined(t.player);assertEquals(record,saved.get());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            assertFalse(t.data.containsKey(APPLIED));verify(t.storage,never()).clearPendingExit(any(),any());
            verify(t.storage,never()).takePendingExit(any());
        }
    }
    @Test void theLoginReceiptIsCapturedBeforeALateQueryEvenIfTheInMemoryMarkerChanges() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();t.data.put(APPLIED,record.id().toString());
            var query=new CompletableFuture<Optional<PendingExitRecord>>();when(t.storage.pendingExit(any())).thenReturn(query);
            t.manager.joined(t.player);t.data.remove(APPLIED);query.complete(Optional.of(record));
            verify(t.player,never()).teleport(any(Location.class));verify(t.storage).clearPendingExit(t.player.getUniqueId(),record.id());
            assertNull(saved.get());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void aLateAcknowledgementCannotClearANewExitOrReleaseANewerConnection() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var old=saved.get();t.data.put(APPLIED,old.id().toString());
            var acknowledgement=new CompletableFuture<Void>();
            when(t.storage.clearPendingExit(any(),eq(old.id()))).thenAnswer(c->acknowledgement.thenRun(()->saved.compareAndSet(old,null)));
            t.manager.joined(t.player);assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            var latest=new PendingExitRecord(UUID.randomUUID(),t.target.exit());saved.set(latest);
            var chunk=new CompletableFuture<Chunk>();when(t.f.world.isChunkLoaded(6,0)).thenReturn(false);
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(chunk);t.manager.disconnected(t.player);t.manager.joined(t.player);
            acknowledgement.complete(null);assertEquals(latest,saved.get());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            verify(t.player,never()).teleport(any(Location.class));chunk.complete(exitChunk(t));
            verify(t.player,times(1)).teleport(any(Location.class));assertEquals(latest,saved.get());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void aFailedAcknowledgementRetainsTheRecordAndGuardAndCanBeRetriedOnLogin() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var saved=durableExit(t);var record=saved.get();t.data.put(APPLIED,record.id().toString());
            when(t.storage.clearPendingExit(any(),eq(record.id()))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("storage unavailable")));
            t.manager.joined(t.player);assertEquals(record,saved.get());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            verify(t.player,never()).teleport(any(Location.class));
            when(t.storage.clearPendingExit(any(),eq(record.id()))).thenAnswer(c->{saved.compareAndSet(record,null);return CompletableFuture.completedFuture(null);});
            t.manager.disconnected(t.player);t.manager.joined(t.player);
            assertNull(saved.get());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
            verify(t.player,never()).teleport(any(Location.class));
        }
    }
    @Test void thePreviousPositionJournalUsesTheSameVanillaConfirmationAsPendingExits() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            durableExit(t);var target=new AtomicReference<>(t.target);
            when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.returnTarget(any())).thenAnswer(c->CompletableFuture.completedFuture(Optional.ofNullable(target.get())));
            when(t.storage.clearReturnTarget(any(),eq(t.target.sessionId()))).thenAnswer(c->{target.compareAndSet(t.target,null);return CompletableFuture.completedFuture(null);});
            t.manager.joined(t.player);verify(t.player).teleport(argThat((Location at)->at.getX()==99));
            assertEquals(t.target,target.get());verify(t.storage,never()).clearReturnTarget(any(),any());
            t.manager.connected(t.player);verify(t.player,times(1)).teleport(any(Location.class));assertEquals(t.target,target.get());
            t.manager.disconnected(t.player);t.manager.joined(t.player);
            verify(t.storage).clearReturnTarget(t.player.getUniqueId(),t.target.sessionId());assertNull(target.get());
            verify(t.player,times(1)).teleport(any(Location.class));assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }

    @Test void anOldDisconnectAcknowledgementCannotStartWritesAfterReconnection() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.data.put(new NamespacedKey("customdungeons","disconnect_applied"),t.record.id().toString());
            var oldAcknowledgement=new CompletableFuture<Void>();
            when(t.storage.clearReturnTarget(any(),any())).thenReturn(oldAcknowledgement);
            t.manager.joined(t.player);
            var latestQuery=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.DisconnectRecord>>();
            when(t.storage.disconnect(any())).thenReturn(latestQuery);
            t.manager.disconnected(t.player);t.manager.joined(t.player);
            oldAcknowledgement.complete(null);
            verify(t.storage,never()).pendingExit(any());verify(t.storage,never()).takePendingExit(any());
            verify(t.storage,never()).clearPendingExit(any(),any());verify(t.storage,never()).clearDisconnect(any(),any());
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));verify(t.player,never()).teleport(any(Location.class));
            latestQuery.complete(Optional.empty());
            verify(t.player,times(1)).teleport(any(Location.class));assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }

}
