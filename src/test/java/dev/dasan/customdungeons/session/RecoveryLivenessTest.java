package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.DisconnectMode;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryLivenessTest {
    @Test void publicationShouldRetryAfterATemporaryTeleportVetoEnds() {
        try (var t = new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP, false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);
            var callback = ArgumentCaptor.forClass(Runnable.class);
            verify(t.f.definitions).onReload(callback.capture());
            t.manager.joined(t.player);
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            when(t.player.teleport(any(Location.class))).thenReturn(true);
            callback.getValue().run();
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()),
                    "A successful publication with valid destinations must resume the stranded return");
        }
    }
    @Test void failedReloadWithNoJournalShouldNotStrandTheConnection() {
        try (var t = new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP, false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            // The fixture already exposes valid published definitions from the previous load.
            var reload = new CompletableFuture<Void>();
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(reload);
            t.manager.joined(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);
            reload.completeExceptionally(new IllegalStateException("transient reload failure"));
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()),
                    "An empty recovery with still-valid definitions must not await another login or reload");
        }
    }
    private Runnable publication(DisconnectRecoveryTest.Fixture t) {
        var callback=ArgumentCaptor.forClass(Runnable.class);verify(t.f.definitions).onReload(callback.capture());return callback.getValue();
    }
    @Test void existingSharedTickerRetriesWithBackoffAndStopsAfterACompletedReturn() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);
            var renderer=mock(dev.dasan.customdungeons.tool.PreviewRenderer.class);t.manager.recoveryTicker(renderer);
            var pending=ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);var tick=ArgumentCaptor.forClass(Runnable.class);
            verify(renderer).recoveryWork(pending.capture(),tick.capture());
            t.manager.joined(t.player);assertTrue(pending.getValue().getAsBoolean());verify(t.storage,times(1)).returnTarget(any());
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(10);tick.getValue().run();verify(t.storage,times(1)).returnTarget(any());
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(20);tick.getValue().run();verify(t.storage,times(2)).returnTarget(any());
            tick.getValue().run();verify(t.storage,times(2)).returnTarget(any()); // no tight loop at the same tick
            clearInvocations(t.player);when(t.player.teleport(any(Location.class))).thenReturn(true);
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(40);tick.getValue().run();
            verify(t.player,times(1)).teleport(any(Location.class));verify(t.storage,times(3)).returnTarget(any());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));assertFalse(pending.getValue().getAsBoolean());
            verify(t.storage,never()).takePendingExit(any());verify(t.storage,never()).clearPendingExit(any(),any());
            verify(renderer,atLeastOnce()).refreshRecoveries();
        }
    }
    @Test void aPublicationAndTicksCannotDuplicateAnInFlightRetry() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);var published=publication(t);t.manager.joined(t.player);
            var read=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.ReturnTarget>>();when(t.storage.returnTarget(any())).thenReturn(read);
            clearInvocations(t.player);when(t.player.teleport(any(Location.class))).thenReturn(true);published.run();
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(100);t.manager.tickCinematicRecovery();published.run();
            verify(t.storage,times(2)).returnTarget(any());assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            verify(t.player,never()).teleport(any(Location.class));read.complete(Optional.of(t.target));
            verify(t.player,times(1)).teleport(any(Location.class));assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"disconnect","shutdown","stopping","offline"})
    void queuedRetriesCannotActOnAnInactiveConnection(String end) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);var published=publication(t);t.manager.joined(t.player);
            switch(end) {
                case "disconnect" -> t.manager.disconnected(t.player);
                case "shutdown" -> t.manager.shutdown();
                case "stopping" -> t.bukkit.when(Bukkit::isStopping).thenReturn(true);
                case "offline" -> when(t.player.isOnline()).thenReturn(false);
            }
            clearInvocations(t.storage,t.player);when(t.player.teleport(any(Location.class))).thenReturn(true);
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(100);t.manager.tickCinematicRecovery();published.run();
            verify(t.player,never()).teleport(any(Location.class));verify(t.storage,never()).returnTarget(any());
            verify(t.storage,never()).pendingExit(any());verify(t.storage,never()).clearPendingExit(any(),any());
        }
    }
    @Test void aNewConnectionSupersedesItsPredecessorsQueuedRetry() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);var published=publication(t);t.manager.joined(t.player);
            var read=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.ReturnTarget>>();when(t.storage.returnTarget(any())).thenReturn(read);
            t.manager.disconnected(t.player);clearInvocations(t.storage,t.player);when(t.player.teleport(any(Location.class))).thenReturn(true);
            t.manager.joined(t.player);t.bukkit.when(Bukkit::getCurrentTick).thenReturn(100);t.manager.tickCinematicRecovery();published.run();
            verify(t.storage,times(1)).returnTarget(any());verify(t.player,never()).teleport(any(Location.class));
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));read.complete(Optional.of(t.target));
            verify(t.player,times(1)).teleport(any(Location.class));assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void anEmptyJournalOnRetryClosesTheRecoveryInsteadOfKeepingItsGuard() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.player.teleport(any(Location.class))).thenReturn(false);t.manager.joined(t.player);
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            clearInvocations(t.player);t.bukkit.when(Bukkit::getCurrentTick).thenReturn(20);t.manager.tickCinematicRecovery();
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));verify(t.player,never()).teleport(any(Location.class));
        }
    }
    @Test void aDisconnectWithFailedDestinationsHasAContinuationWithoutDuplicatingItsPenalty() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();when(t.player.teleport(any(Location.class))).thenReturn(false);var published=publication(t);t.manager.joined(t.player);
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));verify(t.player,never()).setHealth(0);
            clearInvocations(t.player);when(t.player.teleport(any(Location.class))).thenReturn(true);
            t.bukkit.when(Bukkit::getCurrentTick).thenReturn(20);t.manager.tickCinematicRecovery();
            verify(t.player,times(1)).setHealth(0);verify(t.player,times(1)).teleport(any(Location.class));
            published.run();t.bukkit.when(Bukkit::getCurrentTick).thenReturn(100);t.manager.tickCinematicRecovery();
            verify(t.player,times(1)).setHealth(0);verify(t.storage,never()).clearDisconnect(any(),any());
            t.manager.disconnected(t.player);t.manager.joined(t.player);
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }

}
