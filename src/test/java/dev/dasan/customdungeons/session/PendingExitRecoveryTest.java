package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Location;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingExitRecoveryTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void aSupersededFallbackMustNotLoseTheConsumedLegacyExit(boolean waitingForChunk) {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            // Model the real SELECT+DELETE contract: the first caller takes the exit;
            // the next read cannot retrieve it, even if the first response arrives late.
            var consumed=new CompletableFuture<Optional<Point>>();
            var chunk=new CompletableFuture<org.bukkit.Chunk>();
            if(waitingForChunk) {
                consumed.complete(Optional.of(t.target.exit()));
                when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
                when(t.f.world.getChunkAtAsync(6,0)).thenReturn(chunk);
            }
            when(t.storage.takePendingExit(any())).thenReturn(consumed,
                    CompletableFuture.completedFuture(Optional.empty()));
            var published=ArgumentCaptor.forClass(Runnable.class);
            verify(t.f.definitions).onReload(published.capture());
            var definitions=new CompletableFuture<Void>();
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(definitions);
            t.manager.joined(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);
            definitions.completeExceptionally(new IllegalStateException("temporary load failure"));
            verify(t.storage,times(1)).takePendingExit(any());
            published.getValue().run();
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()),"Wait for the transferred exit/chunk before releasing the entry guard");
            if(waitingForChunk) {
                var ready=mock(org.bukkit.Chunk.class);when(ready.getWorld()).thenReturn(t.f.world);
                when(ready.getX()).thenReturn(6);chunk.complete(ready);
            }
            else consumed.complete(Optional.of(t.target.exit()));
            verify(t.player,times(1)).teleport(argThat((Location at)->at.getX()==99));
            verify(t.player,times(1)).teleport(any(Location.class));
            verify(t.storage,times(1)).takePendingExit(any());
            verify(t.storage,never()).addPendingExit(any(),any());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
            published.getValue().run();
            verify(t.player,times(1)).teleport(any(Location.class));
        }
    }
    @Test void aFallbackThatAlreadyReturnedThePlayerIsNotRepeatedOnPublication() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.takePendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(t.target.exit())),
                    CompletableFuture.completedFuture(Optional.empty()));
            var published=ArgumentCaptor.forClass(Runnable.class);verify(t.f.definitions).onReload(published.capture());
            var definitions=new CompletableFuture<Void>();when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(definitions);t.manager.joined(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);
            definitions.completeExceptionally(new IllegalStateException("temporary load failure"));
            verify(t.player).teleport(argThat((Location at)->at.getX()==99));
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId())); // still awaiting the disconnect query
            published.getValue().run();published.getValue().run();
            verify(t.player,times(1)).teleport(any(Location.class));verify(t.storage,times(1)).takePendingExit(any());
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
    @Test void anInheritedExitStillWaitsForTheDisconnectQueryBeforeTeleporting() {
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            var consumed=new CompletableFuture<Optional<Point>>();
            when(t.storage.takePendingExit(any())).thenReturn(consumed,CompletableFuture.completedFuture(Optional.empty()));
            var read=new CompletableFuture<Optional<dev.dasan.customdungeons.storage.DisconnectRecord>>();
            when(t.storage.disconnect(any())).thenReturn(read);
            var published=ArgumentCaptor.forClass(Runnable.class);verify(t.f.definitions).onReload(published.capture());
            var definitions=new CompletableFuture<Void>();when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(definitions);t.manager.joined(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);
            definitions.completeExceptionally(new IllegalStateException("temporary load failure"));
            published.getValue().run();consumed.complete(Optional.of(t.target.exit()));
            verify(t.player,never()).teleport(any(Location.class));assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            read.complete(Optional.empty());
            verify(t.player,times(1)).teleport(argThat((Location at)->at.getX()==99));
            verify(t.storage,times(1)).takePendingExit(any());assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
        }
    }
}
