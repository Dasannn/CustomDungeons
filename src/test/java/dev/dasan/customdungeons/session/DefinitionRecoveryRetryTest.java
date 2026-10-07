package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefinitionRecoveryRetryTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void successfulReloadAfterFailedInitialLoadMustResumeRecovery(boolean penalty) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();
            if(!penalty) {
                when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
                when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
                when(t.storage.takePendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            }
            var publication=ArgumentCaptor.forClass(Runnable.class);
            verify(t.f.definitions).onReload(publication.capture());
            var initial=new CompletableFuture<Void>();
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(initial);
            t.manager.joined(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);
            initial.completeExceptionally(new IllegalStateException("disk temporarily unavailable"));
            verify(t.storage,never()).disconnect(any());
            verify(t.storage,never()).clearDisconnect(any(),any());
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            // Simulate a later successful reload with this same connection alive.
            when(t.f.definitions.reloadCompletion()).thenReturn(CompletableFuture.completedFuture(null));
            publication.getValue().run();
            if(penalty) {
                verify(t.player).setHealth(0);
                verify(t.storage,never()).clearDisconnect(any(),any());
            } else {
                assertFalse(t.manager.recoveryPending(t.player.getUniqueId()),
                        "A player without a journal must not stay blocked after successful reload");
            }
        }
    }
    private static Runnable publication(DisconnectRecoveryTest.Fixture t) {
        var listener=ArgumentCaptor.forClass(Runnable.class);
        verify(t.f.definitions).onReload(listener.capture());return listener.getValue();
    }
    private static CompletableFuture<Void> waitingJoin(DisconnectRecoveryTest.Fixture t) {
        var load=new CompletableFuture<Void>();
        when(t.f.definitions.isReloading()).thenReturn(true);
        when(t.f.definitions.reloadCompletion()).thenReturn(load);
        t.manager.joined(t.player);return load;
    }
    private static void failLoad(DisconnectRecoveryTest.Fixture t,CompletableFuture<Void> load) {
        when(t.f.definitions.isReloading()).thenReturn(false);
        load.completeExceptionally(new IllegalStateException("disk temporarily unavailable"));
    }
    @Test void publicationAndCompletionCannotDuplicateAnInFlightJournalReadOrPenalty() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);var load=waitingJoin(t);
            var read=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.DisconnectRecord>>();
            when(t.storage.disconnect(any())).thenReturn(read);
            // DefinitionStore invokes its listener before setting isReloading=false/completing the future.
            published.run();verify(t.storage).disconnect(t.player.getUniqueId());
            when(t.f.definitions.isReloading()).thenReturn(false);load.complete(null);published.run();
            verify(t.storage,times(1)).disconnect(any());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            read.complete(Optional.of(t.record));published.run();
            verify(t.player,times(1)).setHealth(0);verify(t.storage,never()).clearDisconnect(any(),any());
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            t.manager.joined(t.player);
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            verify(t.player,times(1)).setHealth(0);assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"disconnect","shutdown","stopping","offline"})
    void publicationAfterFailedLoadCannotRecoverAnInactiveConnection(String end) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);failLoad(t,waitingJoin(t));
            switch(end) {
                case "disconnect" -> t.manager.disconnected(t.player);
                case "shutdown" -> t.manager.shutdown();
                case "stopping" -> t.bukkit.when(Bukkit::isStopping).thenReturn(true);
                case "offline" -> when(t.player.isOnline()).thenReturn(false);
            }
            published.run();verify(t.storage,never()).disconnect(any());
            verify(t.player,never()).setHealth(anyDouble());verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @Test void retryKeepsTheConfirmationCapturedAtTheRealJoin() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);
            var key=new org.bukkit.NamespacedKey("customdungeons","disconnect_applied");
            t.data.put(key,t.record.id().toString());failLoad(t,waitingJoin(t));
            t.data.remove(key); // A later in-memory state must not replace the real login's acknowledgement.
            published.run();verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            verify(t.player,never()).setHealth(anyDouble());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void aNewConnectionSupersedesTheOldDefinitionCompletion() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);var old=waitingJoin(t);
            t.manager.disconnected(t.player);var latest=waitingJoin(t);
            old.complete(null);verify(t.storage,never()).disconnect(any());
            published.run();when(t.f.definitions.isReloading()).thenReturn(false);latest.complete(null);
            verify(t.storage,times(1)).disconnect(any());verify(t.player,times(1)).setHealth(0);
            verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void lateFailedLoadFallbackCannotOverrideResumedPenalty(boolean chunk) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);
            var query=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.ReturnTarget>>();
            var loaded=new CompletableFuture<org.bukkit.Chunk>();
            if(chunk) {
                when(t.f.world.isChunkLoaded(1,1)).thenReturn(false);
                when(t.f.world.getChunkAtAsync(1,1)).thenReturn(loaded);
            } else when(t.storage.returnTarget(any())).thenReturn(query);
            failLoad(t,waitingJoin(t));published.run();verify(t.player).setHealth(0);
            if(chunk)loaded.complete(mock(org.bukkit.Chunk.class));else query.complete(Optional.of(t.target));
            verify(t.player,times(1)).teleport(any(Location.class));
            verify(t.player).teleport(argThat((Location at)->at.getX()==2));
            verify(t.storage,never()).clearReturnTarget(any(),any());
            verify(t.storage,never()).clearDisconnect(any(),any());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void retryWithoutPenaltyKeepsTheEntryGuardUntilTheFreshReturnReadCompletes() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.takePendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            var published=publication(t);failLoad(t,waitingJoin(t));
            var query=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.ReturnTarget>>();
            when(t.storage.returnTarget(any())).thenReturn(query);published.run();
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
            query.complete(Optional.empty());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void cooldownQueryFailureDoesNotPreventResumingRecoveryOnPublication() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var published=publication(t);failLoad(t,waitingJoin(t));
            t.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(t.player));
            when(t.storage.cooldownUntil(any(),any())).thenThrow(new IllegalStateException("cooldown unavailable"));
            assertDoesNotThrow(published::run);verify(t.player).setHealth(0);
        }
    }
}
