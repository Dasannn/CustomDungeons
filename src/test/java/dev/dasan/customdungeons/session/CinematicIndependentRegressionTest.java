package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.Point;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicIndependentRegressionTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap() {PaperApiTestBootstrap.initialize();}
    CinematicRestorationTest harness() {var t=new CinematicRestorationTest();t.root=root;return t;}

    @Test void redirectedOrientationMustNotConfirmExactRestoration() {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,(p,point)->{
                a.teleport(p,point);a.at.get().setYaw(0);a.at.get().setPitch(90);return true;
            },Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            assertThrows(IllegalStateException.class,()->recovery.restore(a.p,saved),
                    "wrong yaw/pitch must not publish restored or permit backup deletion");
            assertTrue(CinematicRecovery.pending(a.p));assertFalse(journal.get(saved.player(),saved.token()).orElseThrow().restored());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missing-world","chunk-error","teleport-error"})
    void retryFailedOriginalDestinationMustAdvanceToNewlyAvailableExit(String originalFailure) {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        var exitAvailable=new AtomicBoolean(false);var exit=mock(World.class);
        when(exit.getName()).thenReturn("exit");when(exit.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(exit.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        var primary=mock(World.class);when(primary.getName()).thenReturn("primary");
        when(primary.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(primary.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        when(primary.getSpawnLocation()).thenReturn(new Location(primary,500,70,500));
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            if(!originalFailure.equals("missing-world"))bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);
            if(originalFailure.equals("chunk-error"))when(a.world.getChunkAtAsync(anyInt(),anyInt()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("original chunk failed")));
            bukkit.when(()->Bukkit.getWorld("exit")).thenAnswer(c->exitAvailable.get()?exit:null);
            bukkit.when(()->Bukkit.getWorld("primary")).thenReturn(primary);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(primary));
            var recovery=new CinematicRecovery(journal,(p,point)->{
                if(point.world().equals("world"))throw new IllegalStateException("original teleport failed");
                if(point.world().equals("primary"))return false; // Another plugin vetoes the spawn TP.
                a.at.set(DungeonSessionRuntime.location(point));return true;
            },Runnable::run,error->{},c->{},c->{},p->new Point("exit",99,64,0,0,0));
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            a.at.set(new Location(primary,-50,70,0)); // Vanilla moved the player out of the absent world.
            var done=new AtomicInteger();recovery.recover(a.p,true,()->true,done::incrementAndGet,()->{});
            assertTrue(recovery.hasPending());exitAvailable.set(true);
            for(int tick=20;tick<=100;tick+=20)recovery.tick(tick);
            assertEquals(1,done.get(),"missing original world must not bypass the fallback chain on every retry");
            assertEquals("exit",a.at.get().getWorld().getName());assertFalse(recovery.hasPending());
            assertFalse(CinematicRecovery.pending(a.p));assertTrue(journal.get(saved.player(),saved.token()).orElseThrow().restored());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void deferredReturnMustWaitForAsyncChunkBeforeTeleport(boolean alreadyLoaded) {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        var load=new CompletableFuture<Chunk>();var teleports=new AtomicInteger();
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);
            when(a.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(alreadyLoaded);
            when(a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(load);
            var recovery=new CinematicRecovery(journal,(p,point)->{
                teleports.incrementAndGet();return a.teleport(p,point);
            },Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            recovery.defer(a.p,saved,saved.position(),0,()->true,()->{});
            recovery.tick(20);
            assertEquals(0,teleports.get(),"retry must preload, not teleport directly into an unloaded chunk");
            verify(a.world).getChunkAtAsync(anyInt(),anyInt());
            load.complete(mock(Chunk.class));assertEquals(1,teleports.get());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "177,-30,178,-30,true", "177,-30,178.01,-30,false",
        "177,-30,177,-29,true", "177,-30,177,-28.99,false",
        "179.5,-30,-179.5,-30,true", "179.5,-30,-179.4,-30,false",
        "177,-30,537,-30,true", "177,-30,NaN,-30,false"
    })
    void orientationUsesOneDegreeToleranceAndWrappedYaw(float savedYaw,float savedPitch,float actualYaw,float actualPitch,boolean accepted) {
        var a=harness().new Actor();a.at.get().setYaw(savedYaw);a.at.get().setPitch(savedPitch);
        var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,(p,point)->{
                a.teleport(p,point);a.at.get().setYaw(actualYaw);a.at.get().setPitch(actualPitch);return true;
            },Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            if(accepted)assertDoesNotThrow(()->recovery.restore(a.p,saved));
            else assertThrows(IllegalStateException.class,()->recovery.restore(a.p,saved));
            assertEquals(accepted,journal.get(saved.player(),saved.token()).orElseThrow().restored());
            assertEquals(!accepted,CinematicRecovery.pending(a.p));
        }
    }

    @Test void acceptedTotalVetoRetainsUnconfirmedBackupAndRetriesAfterAdminStop() {
        try(var f=harness().new Fixture(false,Runnable::run)) {
            f.session.tick();var manager=mock(SessionManager.class);when(manager.cinematics()).thenReturn(f.recovery);
            var listener=new SessionListener(manager);
            doAnswer(c->{
                var event=new org.bukkit.event.player.PlayerGameModeChangeEvent(f.a.p,c.getArgument(0),
                        org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.PLUGIN,null);
                event.setCancelled(true);listener.restoreGameMode(event);event.setCancelled(true);
                if(!event.isCancelled())f.a.mode.set(c.getArgument(0));return null;
            }).when(f.a.p).setGameMode(any());
            f.session.finish(false,true);assertEquals(SessionState.FREE,f.session.state().state());
            verify(f.a.p,atLeastOnce()).setGameMode(GameMode.SURVIVAL);
            UUID token=UUID.fromString(f.a.markers.get(CinematicRecovery.MARKER).substring(7));
            clearInvocations(f.a.p);
            for(int tick=20;tick<=200;tick+=20)f.recovery.tick(tick);
            verify(f.a.p,atLeast(8)).setGameMode(GameMode.ADVENTURE);
            assertEquals(GameMode.SPECTATOR,f.a.mode.get());assertTrue(f.recovery.hasPending());
            assertTrue(CinematicRecovery.pending(f.a.p));assertFalse(f.journal.get(f.a.p.getUniqueId(),token).orElseThrow().restored());
            assertEquals(1,f.errors.size());
            when(f.a.p.isOnline()).thenReturn(false);clearInvocations(f.a.p);f.recovery.tick(220);
            verify(f.a.p,never()).setGameMode(any());assertFalse(f.recovery.hasPending());
            assertTrue(CinematicRecovery.pending(f.a.p));
            try(var reloaded=new CinematicJournal(root,Runnable::run)) {
                assertFalse(reloaded.get(f.a.p.getUniqueId(),token).orElseThrow().restored());
            }
        }
    }
}
