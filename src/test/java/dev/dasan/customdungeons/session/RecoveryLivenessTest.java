package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.DisconnectMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryLivenessTest {
    @Test void successfulFallbackWithNoPenaltyMustReleaseTheGuardWithoutAnotherPublication() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            failedReloadJoin(t);
            verify(t.player,times(1)).teleport(any(Location.class));
            verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage,never()).clearPendingExit(any(),any());
            verify(t.storage,never()).clearReturnTarget(any(),any());
            for(int tick:new int[]{20,40,100,1000}) {
                t.bukkit.when(Bukkit::getCurrentTick).thenReturn(tick);t.manager.tickCinematicRecovery();
            }
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()),
                    "A completed fallback without a penalty must not wait for an unrelated publication");
            publication(t).run();verify(t.player,times(1)).teleport(any(Location.class));
        }
    }
    private static void failedReloadJoin(DisconnectRecoveryTest.Fixture t) {
        var reload=new CompletableFuture<Void>();
        when(t.f.definitions.isReloading()).thenReturn(true);when(t.f.definitions.reloadCompletion()).thenReturn(reload);
        t.manager.joined(t.player);when(t.f.definitions.isReloading()).thenReturn(false);
        reload.completeExceptionally(new IllegalStateException("reload failed"));
    }
    private static Map<?,?> recoveryState(SessionManager manager,String name) throws Exception {
        var field=SessionManager.class.getDeclaredField(name);field.setAccessible(true);return (Map<?,?>)field.get(manager);
    }
    @ParameterizedTest @ValueSource(strings={"success","fallback","empty_fallback","query_fallback",
            "acknowledgement","acknowledgement_failure","teleport_failure","fallback_failure",
            "penalty_failure","penalty_fallback","penalty_applied","penalty_acknowledged",
            "penalty_ack_failure","penalty_read_failure","penalty_fallback_read_failure",
            "penalty_death_failure","disconnect","shutdown"})
    void everyTerminalPathEitherReleasesRecoveryOrRetainsADurableContinuation(String path) throws Exception {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var target=new AtomicReference<>(t.target);var exit=new AtomicReference<>(t.pending);
            var penalty=new AtomicReference<>(path.startsWith("penalty_")?t.record:null);
            boolean acknowledgement=path.startsWith("acknowledgement") || path.startsWith("penalty_ack");
            when(t.storage.returnTarget(any())).thenAnswer(c->CompletableFuture.completedFuture(Optional.ofNullable(target.get())));
            when(t.storage.pendingExit(any())).thenAnswer(c->CompletableFuture.completedFuture(Optional.ofNullable(exit.get())));
            when(t.storage.disconnect(any())).thenAnswer(c->CompletableFuture.completedFuture(Optional.ofNullable(penalty.get())));
            when(t.storage.clearReturnTarget(any(),eq(t.target.sessionId()))).thenAnswer(c->{target.set(null);return CompletableFuture.completedFuture(null);});
            when(t.storage.clearPendingExit(any(),eq(t.pending.id()))).thenAnswer(c->{exit.set(null);return CompletableFuture.completedFuture(null);});
            when(t.storage.clearDisconnect(any(),eq(t.record.id()))).thenAnswer(c->{penalty.set(null);return CompletableFuture.completedFuture(null);});
            if(path.equals("empty_fallback")){target.set(null);exit.set(null);}
            if(path.startsWith("acknowledgement")) {
                t.data.put(new NamespacedKey("customdungeons","return_applied"),t.target.sessionId().toString());
                t.data.put(new NamespacedKey("customdungeons","exit_applied"),t.pending.id().toString());
            }
            if(path.startsWith("penalty_ack"))
                t.data.put(new NamespacedKey("customdungeons","disconnect_applied"),t.record.id().toString());
            if(path.equals("acknowledgement_failure"))
                when(t.storage.clearPendingExit(any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("ack failed")));
            if(path.equals("penalty_ack_failure"))
                when(t.storage.clearDisconnect(any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("ack failed")));
            if(path.equals("query_fallback"))
                when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("read failed")));
            if(path.endsWith("read_failure"))
                when(t.storage.disconnect(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("read failed")));
            if(path.endsWith("failure") && !acknowledgement && !path.endsWith("read_failure") && !path.endsWith("death_failure"))
                when(t.player.teleport(any(Location.class))).thenReturn(false);
            t.killEvents();
            if(path.endsWith("death_failure"))doNothing().when(t.player).setHealth(0);
            var chunk=new CompletableFuture<org.bukkit.Chunk>();
            boolean detached=path.equals("disconnect") || path.equals("shutdown");
            if(detached) {
                when(t.f.world.isChunkLoaded(1,1)).thenReturn(false);when(t.f.world.getChunkAtAsync(1,1)).thenReturn(chunk);
            }
            if(path.contains("fallback"))failedReloadJoin(t);else t.manager.joined(t.player);
            if(path.equals("disconnect"))t.manager.disconnected(t.player);
            if(path.equals("shutdown"))t.manager.shutdown();
            if(detached)chunk.complete(mock(org.bukkit.Chunk.class)); // obsolete callbacks cannot restore the guard
            boolean blocked=path.endsWith("failure") || path.equals("penalty_fallback") || path.equals("penalty_applied");
            assertEquals(blocked,t.manager.recoveryPending(t.player.getUniqueId()),path);
            var uuid=t.player.getUniqueId();
            if(blocked) {
                assertTrue(target.get()!=null || exit.get()!=null || penalty.get()!=null,"Every guard owns a durable record");
                assertTrue(recoveryState(t.manager,"pendingDefinitions").containsKey(uuid)
                        || recoveryState(t.manager,"recoveryRetries").containsKey(uuid),"Every guard has a registered continuation");
                var retry=recoveryState(t.manager,"recoveryRetries").get(uuid);
                if(retry!=null) {
                    var current=retry.getClass().getDeclaredMethod("current");current.setAccessible(true);
                    assertTrue(((java.util.function.BooleanSupplier)current.invoke(retry)).getAsBoolean(),"The continuation owns the current attempt");
                }
            } else {
                for(String state:new String[]{"pendingDefinitions","activeReturns","recoveryRetries"})
                    assertFalse(recoveryState(t.manager,state).containsKey(uuid),"Completed recovery must clear "+state);
            }
            verify(t.storage,never()).takePendingExit(any());
            if(path.equals("acknowledgement")) {
                assertNull(target.get());assertNull(exit.get());assertNull(penalty.get());
            } else if(path.equals("penalty_acknowledged")) {
                // A disconnect receipt cannot acknowledge another session's return generation.
                assertSame(t.target,target.get());assertNull(exit.get());assertNull(penalty.get());
            } else if(!acknowledgement && !path.equals("empty_fallback")) {
                assertSame(t.target,target.get());assertSame(t.pending,exit.get());
                verify(t.storage,never()).clearReturnTarget(any(),any());verify(t.storage,never()).clearPendingExit(any(),any());
            }
            if(detached)verify(t.player,never()).teleport(any(Location.class));
        }
    }
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
