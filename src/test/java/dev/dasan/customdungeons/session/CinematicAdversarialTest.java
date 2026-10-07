package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicAdversarialTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap() { PaperApiTestBootstrap.initialize(); }
    CinematicRestorationTest harness() { var t=new CinematicRestorationTest();t.root=root;return t; }

    @Test void persistentHighestVetoRetainsBackupAndRetriesEveryTwentyTicksWithOneWarning() {
        var t=harness();
        try(var f=t.new Fixture(false,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.a.world);
            f.session.tick();var veto=new AtomicBoolean(true);
            var manager=mock(SessionManager.class);when(manager.cinematics()).thenReturn(f.recovery);
            var listener=new SessionListener(manager);
            doAnswer(c->{
                var event=new org.bukkit.event.player.PlayerGameModeChangeEvent(f.a.p,c.getArgument(0),
                    org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.PLUGIN,null);
                event.setCancelled(true);listener.restoreGameMode(event);
                // A later HIGHEST listener can still cancel the scoped last resort.
                if(veto.get())event.setCancelled(true);
                if(!event.isCancelled())f.a.mode.set(c.getArgument(0));return null;
            }).when(f.a.p).setGameMode(any());
            f.intro.skip(f.a.p.getUniqueId());for(int i=0;i<60;i++)f.session.tick();
            assertEquals(GameMode.SPECTATOR,f.a.mode.get());assertTrue(CinematicRecovery.pending(f.a.p));
            UUID token=UUID.fromString(f.a.markers.get(CinematicRecovery.MARKER).substring(7));
            assertFalse(f.journal.get(f.a.p.getUniqueId(),token).orElseThrow().restored());assertEquals(1,f.errors.size());
            clearInvocations(f.a.p);for(int i=0;i<20;i++)f.session.tick();
            verify(f.a.p,times(1)).setGameMode(GameMode.ADVENTURE);assertEquals(1,f.errors.size());
            veto.set(false);for(int i=0;i<20;i++)f.session.tick();
            assertEquals(GameMode.ADVENTURE,f.a.mode.get());assertFalse(CinematicRecovery.pending(f.a.p));
            assertTrue(f.journal.get(f.a.p.getUniqueId(),token).orElseThrow().restored());
        }
    }

    @Test void adminStopMustContinueTrackingSavedModeAfterTemporarySurvivalFallback() {
        var t=harness();
        try(var f=t.new Fixture(false,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.a.world);
            f.session.tick();var veto=new AtomicBoolean(true);
            doAnswer(c->{if(!veto.get())f.a.mode.set(GameMode.ADVENTURE);return null;}).when(f.a.p).setGameMode(GameMode.ADVENTURE);
            f.session.finish(false,true);
            assertEquals(GameMode.SURVIVAL,f.a.mode.get());
            assertTrue(f.recovery.hasPending());assertEquals(SessionState.FREE,f.session.state().state());
            UUID token=UUID.fromString(f.a.markers.get(CinematicRecovery.MARKER).substring(7));
            for(int i=1;i<=60;i++)f.recovery.tick(i);
            assertTrue(CinematicRecovery.pending(f.a.p));assertFalse(f.journal.get(f.a.p.getUniqueId(),token).orElseThrow().restored());
            veto.set(false);
            for(int i=61;i<=100;i++)f.recovery.tick(i);
            assertEquals(GameMode.ADVENTURE,f.a.mode.get(),"session is FREE; intro tracking is no longer ticked");
            assertFalse(CinematicRecovery.pending(f.a.p));
        }
    }

    @Test void successfulRedirectedTeleportMustNotConfirmLostExactPosition() {
        var t=harness();var a=t.new Actor();var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,(p,point)->{
                a.teleport(p,point);a.at.get().add(10,0,0);return true;
            },Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            try { recovery.restore(a.p,saved); } catch(RuntimeException acceptable) {}
            assertTrue(CinematicRecovery.pending(a.p),"redirect accepted as exact restoration; next login will delete backup");
        }
    }

    @Test void delayedCinematicReturnMustNotUndoAdminStopExitTeleport() {
        var t=harness();var a=t.new Actor();var ready=new AtomicBoolean(false);
        var load=new java.util.concurrent.CompletableFuture<org.bukkit.Chunk>();
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);
            when(a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(load);
            var recovery=new CinematicRecovery(journal,(p,point)->ready.get() && a.teleport(p,point),Runnable::run,error->{});
            var intro=new SessionCinematic(recovery,a::teleport,p->{},error->{});
            var def=new CinematicSessionTest().def(false,true);
            var frames=CinematicRoute.calculate(def,new CinematicRoute.Limits(-64,320,-1000,1000,-1000,1000));
            var session=new DungeonSession(def,false,new SessionServices() {
                public void start(DungeonSession s) {intro.start(s,frames);}
                public void recoveryTick(long tick) {recovery.tick(tick);}
                public boolean introTick(DungeonSession s) {return intro.tick(s);}
                public void introRestore(DungeonSession s,org.bukkit.entity.Player p) {intro.restore(p);}
                public void finish(DungeonSession s) {intro.clear();}
                public void teleport(org.bukkit.entity.Player p,Point point) {a.teleport(p,point);}
                public boolean inside(DungeonSession s,org.bukkit.entity.Player p) {return false;}
            });
            session.join(a.p);session.forceStart();session.tick();
            intro.skip(a.p.getUniqueId());for(int i=0;i<20;i++)session.tick();
            assertEquals(GameMode.ADVENTURE,a.mode.get(),"a pending position must preserve the already restored game mode");
            for(int i=0;i<30;i++)session.tick();verify(a.world,atLeastOnce()).getChunkAtAsync(anyInt(),anyInt());
            session.finish(false,true);assertEquals(SessionState.FREE,session.state().state());
            Location expectedExit=a.at.get().clone();assertEquals(def.exit().x(),expectedExit.getX());
            ready.set(true);load.complete(mock(org.bukkit.Chunk.class));
            assertEquals(expectedExit,a.at.get(),"late recovery moved player from final EXIT back to pre-camera lobby");
            assertTrue(CinematicRecovery.pending(a.p));
            recovery.tick(100);assertEquals(expectedExit,a.at.get());assertFalse(CinematicRecovery.pending(a.p));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"world,0.49,true","world,0.5,false","other,0,false"})
    void confirmationRequiresTheSameWorldAndDistanceStrictlyBelowHalfABlock(String world,double displacement,boolean accepted) {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        var destination=mock(World.class);when(destination.getName()).thenReturn(world);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,(p,point)->{
                a.at.set(new Location(destination,point.x()+displacement,point.y(),point.z(),point.yaw(),point.pitch()));return true;
            },Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            if(accepted)assertDoesNotThrow(()->recovery.restore(a.p,saved));
            else assertThrows(IllegalStateException.class,()->recovery.restore(a.p,saved));
            assertEquals(accepted,journal.get(saved.player(),saved.token()).orElseThrow().restored());
            assertEquals(!accepted,CinematicRecovery.pending(a.p));
        }
    }

    @Test void pluginRegistryRetainsUnverifiedBackupAfterDisconnectAndRetriesOnNextLogin() {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);var veto=new AtomicBoolean(true);
        doAnswer(c->{if(!veto.get())a.mode.set(GameMode.ADVENTURE);return null;}).when(a.p).setGameMode(GameMode.ADVENTURE);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var warnings=new ArrayList<Throwable>();var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,warnings::add);
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            var done=new AtomicInteger();recovery.recover(a.p,true,a.p::isOnline,done::incrementAndGet,()->{});
            assertTrue(recovery.hasPending());recovery.tick(0);recovery.tick(20);assertEquals(1,warnings.size());
            when(a.p.isOnline()).thenReturn(false);clearInvocations(a.p);recovery.tick(40);
            verify(a.p,never()).setGameMode(any());assertFalse(recovery.hasPending());
            assertTrue(CinematicRecovery.pending(a.p));assertFalse(journal.get(saved.player(),saved.token()).orElseThrow().restored());
            when(a.p.isOnline()).thenReturn(true);veto.set(false);
            try(var bukkit=mockStatic(Bukkit.class)) {
                bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);when(a.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
                recovery.recover(a.p,true,a.p::isOnline,done::incrementAndGet,()->fail("login recovery"));
                assertEquals(1,done.get());assertTrue(journal.get(saved.player(),saved.token()).isPresent());
                recovery.recover(a.p,true,a.p::isOnline,done::incrementAndGet,()->fail("login acknowledgement"));
                assertTrue(journal.get(saved.player(),saved.token()).isEmpty());
            }
        }
    }

    @Test void activeVanillaGenerationReopensPreviouslyConfirmedBackupWithoutDeletingIt() {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);recovery.restore(a.p,saved);
            // Disk journal won the crash race, but vanilla still persisted the active state.
            a.markers.put(CinematicRecovery.MARKER,"active:"+saved.token());a.mode.set(GameMode.SPECTATOR);
            doNothing().when(a.p).setGameMode(any());
            recovery.recover(a.p,true,()->true,()->fail("veto is unresolved"),()->{});
            assertTrue(recovery.hasPending());assertFalse(journal.get(saved.player(),saved.token()).orElseThrow().restored());
            assertThrows(java.util.concurrent.CompletionException.class,()->journal.acknowledge(saved.player(),saved.token()).join());
            assertTrue(journal.get(saved.player(),saved.token()).isPresent());journal.pending(saved).join();
        }
    }

    @Test void laterVanillaConfirmationPreservesLegitimateChangesAfterTheVerifiedRestoration() {
        var a=harness().new Actor();var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run)) {
            var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->{});
            recovery.backup(saved).join();recovery.activate(a.p,saved);recovery.restore(a.p,saved);
            // Ordinary gameplay/admin changes after restoration are not cinematic residue.
            a.mode.set(GameMode.CREATIVE);a.invulnerable.set(false);a.at.get().add(50,0,0);
            var done=new AtomicInteger();recovery.recover(a.p,true,()->true,done::incrementAndGet,()->fail("verified vanilla confirmation"));
            assertEquals(1,done.get());assertEquals(GameMode.CREATIVE,a.mode.get());assertFalse(a.invulnerable.get());
            assertEquals(saved.position().x()+50,a.at.get().getX());assertTrue(journal.get(saved.player(),saved.token()).isEmpty());
        }
    }

    @Test void departingPlayersPendingModeDoesNotKeepTheRemainingGroupInIntro() {
        try(var f=harness().new Fixture(false,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.a.world);
            f.session.tick();var veto=new AtomicBoolean(true);
            doAnswer(c->{if(!veto.get())f.a.mode.set(GameMode.ADVENTURE);return null;}).when(f.a.p).setGameMode(GameMode.ADVENTURE);
            f.session.leave(f.a.p.getUniqueId());assertTrue(f.recovery.hasPending());
            f.intro.skip(f.b.p.getUniqueId());f.session.tick();assertFalse(f.session.introActive());
            assertTrue(CinematicRecovery.pending(f.a.p));veto.set(false);
            f.recovery.tick(40);assertEquals(GameMode.ADVENTURE,f.a.mode.get());assertFalse(CinematicRecovery.pending(f.a.p));
            assertEquals(f.session.def().exit().x(),f.a.at.get().getX());
        }
    }

    @Test void replacementGenerationInvalidatesAnOutstandingChunkCallback() {
        var a=harness().new Actor();var first=CinematicRecovery.capture(a.p);
        var load=new java.util.concurrent.CompletableFuture<Chunk>();
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);when(a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(load);
            var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->{});
            recovery.backup(first).join();recovery.activate(a.p,first);
            recovery.recover(a.p,true,()->true,()->fail("stale generation"),()->{});
            var second=CinematicRecovery.capture(a.p);recovery.backup(second).join();recovery.activate(a.p,second);
            var currentCamera=new Location(a.world,80,90,100);a.at.set(currentCamera.clone());load.complete(mock(Chunk.class));
            assertEquals(currentCamera,a.at.get());assertEquals(GameMode.SPECTATOR,a.mode.get());
            assertEquals("active:"+second.token(),a.markers.get(CinematicRecovery.MARKER));
            assertFalse(journal.get(first.player(),first.token()).orElseThrow().restored());
        }
    }

    @Test void crashAtEveryTransitionKeepsExactBackupUntilVanillaConfirmation() {
        for(String phase:List.of("before-activation","before-spectator-after-marker","during","restoring",
                "after-restore-before-confirm","after-restore-vanilla-still-active")) {
            var t=harness();var a=t.new Actor();var original=a.at.get().clone();var saved=CinematicRecovery.capture(a.p);
            try(var journal=new CinematicJournal(root,Runnable::run)) {
                var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->fail(error));
                recovery.backup(saved).join();
                if(phase.equals("before-spectator-after-marker"))a.markers.put(CinematicRecovery.MARKER,"active:"+saved.token());
                else if(!phase.equals("before-activation"))recovery.activate(a.p,saved);
                if(phase.equals("restoring")) { recovery.restoreAttributes(a.p,saved,false);a.at.get().add(12,0,0); }
                if(phase.equals("after-restore-before-confirm"))recovery.restore(a.p,saved);
                if(phase.equals("after-restore-vanilla-still-active")) {
                    recovery.restore(a.p,saved);
                    a.markers.put(CinematicRecovery.MARKER,"active:"+saved.token());
                    a.mode.set(GameMode.SPECTATOR);a.at.get().add(100,0,0);
                }
            }
            try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
                bukkit.when(()->Bukkit.getWorld("world")).thenReturn(a.world);
                when(a.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
                var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->fail(error));
                recovery.recover(a.p,!phase.equals("after-restore-before-confirm"),()->true,()->{},()->fail(phase));
                a.original(original);
                assertTrue(journal.get(saved.player(),saved.token()).isPresent(),phase);
            }
        }
    }

    @Test void journalDoesNotWriteOnCameraFrames() {
        var t=harness();var writes=new AtomicInteger();
        try(var f=t.new Fixture(false,action->{writes.incrementAndGet();action.run();})) {
            assertEquals(2,writes.get());f.session.tick();
            for(int i=0;i<99;i++)f.session.tick();
            assertEquals(2,writes.get());f.session.tick();assertEquals(4,writes.get());
        }
    }

    @Test void originalWorldUnloadedDuringChunkLoadUsesExitAndKeepsSavedFlags() {
        var t=harness();var a=t.new Actor();var saved=CinematicRecovery.capture(a.p);
        var load=new java.util.concurrent.CompletableFuture<org.bukkit.Chunk>();
        var originalWorld=new AtomicReference<World>(a.world);
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            var exitWorld=mock(World.class);when(exitWorld.getName()).thenReturn("exit");
            when(exitWorld.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            when(exitWorld.getChunkAtAsync(anyInt(),anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mock(Chunk.class)));
            bukkit.when(()->Bukkit.getWorld("world")).thenAnswer(c->originalWorld.get());
            bukkit.when(()->Bukkit.getWorld("exit")).thenReturn(exitWorld);
            when(a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(load);
            var recovery=new CinematicRecovery(journal,(p,point)->{
                a.at.set(DungeonSessionRuntime.location(point));return true;
            },Runnable::run,error->{},c->{},c->{},p->new Point("exit",99,64,0,0,0));
            recovery.backup(saved).join();recovery.activate(a.p,saved);
            var done=new AtomicInteger();recovery.recover(a.p,true,()->true,done::incrementAndGet,()->fail("fallback"));
            assertEquals(GameMode.ADVENTURE,a.mode.get());assertTrue(a.flight.get());assertFalse(a.flying.get());
            originalWorld.set(null);load.complete(mock(org.bukkit.Chunk.class));
            assertEquals(1,done.get());assertEquals("exit",a.at.get().getWorld().getName());
            assertTrue(a.markers.get(CinematicRecovery.MARKER).startsWith("restored:"));
        }
    }

    @Test void skipThenDisconnectAndKickNeverRecordAPenalty() throws Exception {
        for(var reason:List.of(org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED,
                org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED)) {
            var t=harness();
            try(var f=t.new Fixture(false,Runnable::run);var managerFixture=new ReturnRecoveryRegressionTest.Fixture()) {
                f.session.tick();f.intro.skip(f.a.p.getUniqueId());f.session.tick();
                assertFalse(f.intro.contains(f.a.p.getUniqueId()));assertTrue(f.session.introActive());
                var field=SessionManager.class.getDeclaredField("players");field.setAccessible(true);
                @SuppressWarnings("unchecked") var players=(Map<UUID,DungeonSession>)field.get(managerFixture.manager);
                players.put(f.a.p.getUniqueId(),f.session);
                managerFixture.manager.disconnected(f.a.p,reason);
                verify(managerFixture.storage,never()).saveDisconnect(any());
                verify(managerFixture.storage).addPendingExit(f.a.p.getUniqueId(),f.session.def().exit());
                f.a.original(f.original);
            }
        }
    }

    @Test void additionalItemInteractionsAreBlockedByTheActiveMarker() {
        var t=harness();
        try(var f=t.new Fixture(false,Runnable::run)) {
            f.session.tick();var listener=new SessionListener(mock(SessionManager.class));
            var pickup=mock(org.bukkit.event.entity.EntityPickupItemEvent.class);when(pickup.getEntity()).thenReturn(f.a.p);
            listener.pickup(pickup);verify(pickup).setCancelled(true);
            var empty=mock(org.bukkit.event.player.PlayerBucketEmptyEvent.class);when(empty.getPlayer()).thenReturn(f.a.p);
            listener.bucketEmpty(empty);verify(empty).setCancelled(true);
            var fill=mock(org.bukkit.event.player.PlayerBucketFillEvent.class);when(fill.getPlayer()).thenReturn(f.a.p);
            listener.bucketFill(fill);verify(fill).setCancelled(true);
            var consume=mock(org.bukkit.event.player.PlayerItemConsumeEvent.class);when(consume.getPlayer()).thenReturn(f.a.p);
            listener.consume(consume);verify(consume).setCancelled(true);
            var shoot=mock(org.bukkit.event.entity.EntityShootBowEvent.class);when(shoot.getEntity()).thenReturn(f.a.p);
            listener.shoot(shoot);verify(shoot).setCancelled(true);
            var armor=mock(org.bukkit.event.player.PlayerArmorStandManipulateEvent.class);when(armor.getPlayer()).thenReturn(f.a.p);
            listener.manipulate(armor);verify(armor).setCancelled(true);
        }
    }

    @Test void routeMatrixOneFiveAndSixtyOneRoomsFiveAndTwentySeconds() {
        var helper=new CinematicRouteTest();
        for(int seconds:List.of(5,20))for(int count:List.of(1,5,61)) {
            var rooms=new ArrayList<Region>();for(int i=0;i<count;i++)rooms.add(helper.box(i*20,60,(i%2)*100));
            var base=helper.dungeon(null,rooms,helper.box(-30,60,0));
            var def=base.withStart(StartMode.AUTO,List.of(),3,base.entranceDoor(),false,true,true,seconds);
            var frames=CinematicRoute.calculate(def,new CinematicRoute.Limits(-64,320,-10000,10000,-10000,10000));
            int duration=seconds*20,orbit=duration*2/5,visits=Math.min(count,(duration-orbit-10)/10);
            assertEquals(duration+1,frames.size());
            var found=new ArrayList<Integer>();int previous=orbit;
            for(int tick=orbit+1;tick<duration;tick++) {
                Point p=frames.get(tick);
                for(int room=0;room<count;room++) {
                    Point center=CinematicRoute.center(rooms.get(room));
                    if(p.x()==center.x() && p.y()==center.y() && p.z()==center.z()) {
                        assertTrue(tick-previous>=10);previous=tick;found.add(room);
                    }
                }
            }
            var expected=new ArrayList<Integer>();
            for(int i=0;i<visits;i++)expected.add(visits==1?0:(int)Math.round((double)i*(count-1)/(visits-1)));
            assertEquals(expected,found,seconds+"s/"+count+" rooms");assertTrue(duration-previous>=10);
        }
    }
}
