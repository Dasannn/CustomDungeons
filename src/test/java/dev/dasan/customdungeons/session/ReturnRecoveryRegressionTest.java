package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnRecoveryRegressionTest {
    static class Fixture implements AutoCloseable {
        final SessionRuntimeRegressionTest f=new SessionRuntimeRegressionTest();
        final Player player=new StartModesTest().player();
        final SqlStorage storage=mock(SqlStorage.class);
        final Logger logger=mock(Logger.class);
        final PendingExitRecord pending=new PendingExitRecord(UUID.randomUUID(),new Point("world",99,64,0,0,0));
        final ReturnTarget target=new ReturnTarget(UUID.randomUUID(),new Point("world",20,64,20,0,0),new Point("world",99,64,0,0,0),FinishDestination.PREVIOUS);
        final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final SessionManager manager;
        Fixture() {this("");}
        Fixture(String respawnWorld) {
            f.configure();when(f.plugin.isEnabled()).thenReturn(true);when(f.plugin.getLogger()).thenReturn(logger);
            when(f.config.dungeonWorld()).thenReturn("dungeons");
            f.plugin.getConfig().set("respawn-world",respawnWorld);
            when(player.isOnline()).thenReturn(true);when(player.teleport(any(Location.class))).thenReturn(true);
            var codec=new DefinitionCodec();var yaml=new org.bukkit.configuration.file.YamlConfiguration();codec.encode(f.definition()).forEach(yaml::set);
            yaml.set("exit",Map.of("world","world","x",99,"y",64,"z",0));when(f.definitions.dungeons()).thenReturn(Map.of("test",codec.decodeDungeon("test",yaml)));
            when(storage.saveDisconnect(any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(storage.returnTarget(player.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.of(target)));
            when(storage.pendingExit(player.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.of(pending)));
            when(storage.cooldownUntil(any(),any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(storage.clearReturnTarget(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.addPendingExit(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.clearPendingExit(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(f.world.getSpawnLocation()).thenReturn(new Location(f.world,500,70,500));
            RespawnSafetyRegressionTest.terrain(f.world);
            when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(call->{
                var loaded=mock(Chunk.class);when(loaded.getWorld()).thenReturn(f.world);
                when(loaded.getX()).thenReturn(call.getArgument(0));when(loaded.getZ()).thenReturn(call.getArgument(1));
                return CompletableFuture.completedFuture(loaded);
            });
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(Bukkit::getWorlds).thenReturn(List.of(f.world));
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
            doAnswer(call->{((Runnable)call.getArgument(1)).run();return mock(org.bukkit.scheduler.BukkitTask.class);}).when(scheduler).runTask(eq(f.plugin),any(Runnable.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager=spy(new SessionManager(f.plugin,f.definitions,f.config,storage));
        }
        void assertReturned(double x) {
            verify(player).teleport(argThat((Location at)->at.getX()==x));
            assertEquals(JoinResult.DISABLED,manager.join(player,"missing"));
        }
        public void close(){bukkit.close();}
    }
    @Test void failedPreviousChunkFallsBackToLoadedExitAndClearsReturning() {
        try(var t=new Fixture()) {
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==6);
            when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("chunk failed")));
            t.manager.connected(t.player);t.assertReturned(99);verify(t.logger,atLeastOnce()).warning(anyString());
        }
    }
    @Test void failedReturnQueryStillUsesLegacyExitAndLogsRecovery() {
        try(var t=new Fixture()) {
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
            t.manager.connected(t.player);t.assertReturned(99);verify(t.logger,atLeastOnce()).warning(anyString());
        }
    }
    @Test void bothQueriesFailRecoverKnownDungeonExit() {
        try(var t=new Fixture()) {
            t.manager.recoverOccupants("test",Set.of(t.player.getUniqueId()));
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
            when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
            t.manager.connected(t.player);t.assertReturned(99);
        }
    }
    @Test void failedExitChunkUsesPrimaryWorldSpawnAsLastResort() {
        try(var t=new Fixture()) {
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==31);
            when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("chunk failed")));
            t.manager.connected(t.player);t.assertReturned(500);
        }
    }
    @Test void hungPreviousChunkTimesOutFallsBackAndIgnoresLateCompletion() throws Exception {
        try(var t=new Fixture()) {
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==6);
            var hung=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(hung);
            awaitTimeoutRecovery(t);t.assertReturned(99);
            hung.complete(mock(Chunk.class));t.assertReturned(99);
        }
    }
    @Test void hungDatabaseQueriesTimeOutToKnownExitAndNeverStrandReturning() throws Exception {
        try(var t=new Fixture()) {
            t.manager.recoverOccupants("test",Set.of(t.player.getUniqueId()));
            when(t.storage.returnTarget(any())).thenReturn(new CompletableFuture<>());
            when(t.storage.pendingExit(any())).thenReturn(new CompletableFuture<>());
            awaitTimeoutRecovery(t);t.assertReturned(99);verify(t.logger,atLeastOnce()).warning(contains("TimeoutException"));
        }
    }
    private void awaitTimeoutRecovery(Fixture t) throws Exception {
        doReturn(20L).when(t.manager).recoveryTimeoutMillis();
        var mainTasks=new ConcurrentLinkedQueue<Runnable>();
        doAnswer(call->{mainTasks.add(call.getArgument(0));return null;}).when(t.manager).main(any());
        t.manager.connected(t.player);assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(t.manager.join(t.player,"missing")==JoinResult.RESETTING && System.nanoTime()<deadline) {
            Runnable task;while((task=mainTasks.poll())!=null)task.run();
            Thread.sleep(2);
        }
        assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
    }
    @Test void emptyRecoveryDoesNotTeleportNormalConnections() {
        try(var t=new Fixture()) {
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            t.manager.connected(t.player);verify(t.player,never()).teleport(any(Location.class));
            assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
        }
    }
    @Test void cooldownQueryFailureCannotPreventExitRecovery() {
        try(var t=new Fixture()) {
            when(t.storage.cooldownUntil(any(),any())).thenThrow(new IllegalStateException("database closed"));
            assertDoesNotThrow(()->t.manager.connected(t.player));t.assertReturned(99);
        }
    }
    @Test void previousPositionInsideDungeonFallsBackBeforeLoadingItsChunk() {
        try(var t=new Fixture()) {
            var inside=new ReturnTarget(t.target.sessionId(),new Point("world",1.5,64,1.5,0,0),t.target.exit(),FinishDestination.PREVIOUS);
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(inside)));
            t.manager.connected(t.player);t.assertReturned(99);verify(t.f.world,never()).getBlockAt(any(Location.class));
        }
    }
    @Test void missingPreviousWorldFallsBackToExit() {
        try(var t=new Fixture()) {
            var missing=new ReturnTarget(t.target.sessionId(),new Point("deleted",20,64,20,0,0),t.target.exit(),FinishDestination.PREVIOUS);
            when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(missing)));
            t.manager.connected(t.player);t.assertReturned(99);
        }
    }
    @Test void synchronousQueryFailureDoesNotStrandConnection() {
        try(var t=new Fixture()) {
            when(t.storage.returnTarget(any())).thenThrow(new IllegalStateException("database closed"));
            assertDoesNotThrow(()->t.manager.connected(t.player));t.assertReturned(99);
        }
    }
}
