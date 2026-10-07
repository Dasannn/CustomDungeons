package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicRestorationTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap(){PaperApiTestBootstrap.initialize();}
    class Actor {
        final Player p=mock(Player.class);
        final World world=mock(World.class);
        final AtomicReference<Location> at=new AtomicReference<>();
        final AtomicReference<GameMode> mode=new AtomicReference<>(GameMode.ADVENTURE);
        final AtomicBoolean invulnerable=new AtomicBoolean(true),flight=new AtomicBoolean(true),flying=new AtomicBoolean(false);
        final Map<NamespacedKey,String> markers=new HashMap<>();
        Actor() {
            when(world.getName()).thenReturn("world");when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);
            at.set(new Location(world,21.25,74.125,-1.75,177,-30));
            when(p.getLocation()).thenAnswer(c->at.get().clone());when(p.getGameMode()).thenAnswer(c->mode.get());
            when(p.isInvulnerable()).thenAnswer(c->invulnerable.get());when(p.getAllowFlight()).thenAnswer(c->flight.get());when(p.isFlying()).thenAnswer(c->flying.get());
            doAnswer(c->{mode.set(c.getArgument(0));return null;}).when(p).setGameMode(any());
            doAnswer(c->{flight.set(c.getArgument(0));return null;}).when(p).setAllowFlight(anyBoolean());
            doAnswer(c->{flying.set(c.getArgument(0));return null;}).when(p).setFlying(anyBoolean());
            doAnswer(c->{invulnerable.set(c.getArgument(0));return null;}).when(p).setInvulnerable(anyBoolean());
            var pdc=mock(PersistentDataContainer.class);when(p.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenAnswer(c->markers.get(c.getArgument(0)));
            doAnswer(c->{markers.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(NamespacedKey.class),eq(PersistentDataType.STRING),anyString());
            doAnswer(c->{markers.remove(c.getArgument(0));return null;}).when(pdc).remove(any());
        }
        boolean teleport(Player ignored,Point point) {if(!p.isOnline())return false;at.set(new Location(world,point.x(),point.y(),point.z(),point.yaw(),point.pitch()));return true;}
        void original(Location original) {
            assertEquals(original.getX(),at.get().getX());assertEquals(original.getY(),at.get().getY());assertEquals(original.getZ(),at.get().getZ());
            assertEquals(original.getYaw(),at.get().getYaw());assertEquals(original.getPitch(),at.get().getPitch());assertEquals(original.getWorld().getName(),at.get().getWorld().getName());assertEquals(GameMode.ADVENTURE,mode.get());assertTrue(invulnerable.get());assertTrue(flight.get());assertFalse(flying.get());
        }
    }
    class Fixture implements AutoCloseable {
        final Actor a=new Actor(),b=new Actor();
        final List<Throwable> errors=new ArrayList<>();
        final CinematicJournal journal;
        final CinematicRecovery recovery;
        final SessionCinematic intro;
        final DungeonSession session;
        final Location original;
        Fixture(boolean tp,Executor executor) {
            journal=new CinematicJournal(root,executor);
            recovery=new CinematicRecovery(journal,(p,point)->actor(p).teleport(p,point),Runnable::run,errors::add);
            intro=new SessionCinematic(recovery,(p,point)->actor(p).teleport(p,point),p->{},errors::add);
            var d=new CinematicSessionTest().def(tp,true);
            var frames=CinematicRoute.calculate(d,new CinematicRoute.Limits(-64,320,-1000,1000,-1000,1000));
            session=new DungeonSession(d,false,new SessionServices(){
                public void start(DungeonSession s){intro.start(s,frames);}
                public boolean introTick(DungeonSession s){return intro.tick(s);}
                public void introRestore(DungeonSession s,Player p){intro.restore(p);}
                public void finish(DungeonSession s){intro.clear();}
                public boolean inside(DungeonSession s,Player p){return false;}
            });
            session.join(a.p);session.join(b.p);original=a.at.get().clone();session.forceStart();
        }
        Actor actor(Player p){return p==a.p?a:b;}
        public void close(){journal.close();}
    }
    @Test void naturalCompletionRestoresEveryFlagAndExactCoordinatesForBothPlayers() {
        try(var f=new Fixture(false,Runnable::run)) {
            f.session.tick();assertEquals(GameMode.SPECTATOR,f.a.mode.get());assertTrue(f.a.flying.get());
            for(int i=0;i<100;i++)f.session.tick();
            assertFalse(f.session.introActive());f.a.original(f.original);f.b.original(f.original);
            assertTrue(f.a.markers.get(CinematicRecovery.MARKER).startsWith("restored:"));assertTrue(f.errors.isEmpty());
        }
    }
    @Test void sneakSkipsOnlyOnePlayerAndStartTeleportFollowsRestoration() {
        try(var f=new Fixture(true,Runnable::run)) {
            f.session.tick();f.intro.skip(f.a.p.getUniqueId());f.session.tick();
            assertEquals(GameMode.ADVENTURE,f.a.mode.get());assertEquals(GameMode.SPECTATOR,f.b.mode.get());assertTrue(f.session.introActive());
            var cp=f.session.checkpoint();assertEquals(cp.x(),f.a.at.get().getX());assertEquals(cp.y(),f.a.at.get().getY());
            var order=inOrder(f.a.p);order.verify(f.a.p).setGameMode(GameMode.SPECTATOR);order.verify(f.a.p).setGameMode(GameMode.ADVENTURE);
            assertFalse(f.intro.contains(f.a.p.getUniqueId()));assertTrue(f.intro.contains(f.b.p.getUniqueId()));
        }
    }
    @Test void quitLeaveStopAndShutdownCleanupRestoreWithoutWaitingForEnd() {
        for(String reason:List.of("disconnect","leave","stop","shutdown")) {
            try(var f=new Fixture(false,Runnable::run)) {
                f.session.tick();
                switch(reason) {case "disconnect"->f.session.disconnect(f.a.p.getUniqueId());case "leave"->f.session.leave(f.a.p.getUniqueId());
                    case "stop"->f.session.finish(false,true);default->f.intro.clear();}
                f.a.original(f.original);
                if(reason.equals("stop") || reason.equals("shutdown"))f.b.original(f.original);
            }
        }
    }
    @Test void spectatorCannotStartUntilAllBackupsAreDurableAndCancelledPreparationNeverMutatesState() {
        var queue=new ArrayDeque<Runnable>();
        try(var f=new Fixture(false,queue::add)) {
            f.session.tick();assertEquals(GameMode.ADVENTURE,f.a.mode.get());assertEquals(GameMode.ADVENTURE,f.b.mode.get());
            f.session.finish(false,true);while(!queue.isEmpty())queue.removeFirst().run();
            f.a.original(f.original);verify(f.a.p,never()).setGameMode(any());
        }
    }
    @Test void crashRecoveryKeepsRecordThroughReenableUntilRealLoginAcknowledgesRestoration() {
        var f=new Fixture(false,Runnable::run);f.session.tick();
        String active=f.a.markers.get(CinematicRecovery.MARKER);UUID token=UUID.fromString(active.substring(7));f.close();
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.a.world);
            when(f.a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
            var done=new AtomicInteger();var failed=new AtomicInteger();
            var recovery=new CinematicRecovery(journal,f.a::teleport,Runnable::run,f.errors::add);
            recovery.recover(f.a.p,true,()->true,done::incrementAndGet,failed::incrementAndGet);
            f.a.original(f.original);assertEquals(1,done.get());assertEquals(0,failed.get());assertTrue(journal.get(f.a.p.getUniqueId(),token).isPresent());
            recovery.recover(f.a.p,false,()->true,done::incrementAndGet,failed::incrementAndGet);
            assertTrue(journal.get(f.a.p.getUniqueId(),token).isPresent());
            recovery.recover(f.a.p,true,()->true,done::incrementAndGet,failed::incrementAndGet);
            assertTrue(journal.get(f.a.p.getUniqueId(),token).isEmpty());assertFalse(f.a.markers.containsKey(CinematicRecovery.MARKER));
        }
    }
    @Test void failedTeleportRetainsActiveGenerationForRetryAndDoesNotSkipOtherPlayersCleanup() {
        var f=new Fixture(false,Runnable::run);f.session.tick();
        UUID token=UUID.fromString(f.a.markers.get(CinematicRecovery.MARKER).substring(7));var saved=f.journal.get(f.a.p.getUniqueId(),token).orElseThrow();
        var recovery=new CinematicRecovery(f.journal,(p,point)->false,Runnable::run,f.errors::add);
        var original=saved;assertThrows(IllegalStateException.class,()->recovery.restore(f.a.p,original));
        assertTrue(f.a.markers.get(CinematicRecovery.MARKER).startsWith("active:"));assertFalse(f.journal.get(f.a.p.getUniqueId(),token).orElseThrow().restored());
        f.intro.clear();f.a.original(f.original);f.b.original(f.original);f.close();
    }
    @Test void staleAsyncRecoveryCannotMutateAReconnectedPlayer() {
        var f=new Fixture(false,Runnable::run);f.session.tick();f.close();
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            var chunk=new CompletableFuture<Chunk>();var current=new AtomicBoolean(true);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.a.world);when(f.a.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(chunk);
            var recovery=new CinematicRecovery(journal,f.a::teleport,Runnable::run,f.errors::add);
            recovery.recover(f.a.p,true,current::get,()->fail("stale callback"),()->fail("stale callback"));
            current.set(false);chunk.complete(mock(Chunk.class));assertEquals(GameMode.SPECTATOR,f.a.mode.get());
        }
    }

    @Test void allGameModesAndFlightFlagsRestoreIncludingOriginallyVulnerablePlayers() {
        for(GameMode mode:GameMode.values()) {
            var a=new Actor();a.mode.set(mode);a.invulnerable.set(false);a.flight.set(mode==GameMode.CREATIVE || mode==GameMode.SPECTATOR);a.flying.set(a.flight.get());
            Location original=a.at.get().clone();var saved=CinematicRecovery.capture(a.p);
            try(var journal=new CinematicJournal(root,Runnable::run)) {
                var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,error->fail(error));
                recovery.backup(saved).join();recovery.activate(a.p,saved);assertEquals(GameMode.SPECTATOR,a.mode.get());
                recovery.restore(a.p,saved);assertEquals(mode,a.mode.get());assertEquals(original,a.at.get());assertFalse(a.invulnerable.get());
                assertEquals(saved.allowFlight(),a.flight.get());assertEquals(saved.flying(),a.flying.get());
            }
        }
    }
    @Test void missingWorldFailsClosedAndKeepsTheBackup() {
        var a=new Actor();var saved=CinematicRecovery.capture(a.p);
        try(var journal=new CinematicJournal(root,Runnable::run);var bukkit=mockStatic(Bukkit.class)) {
            var errors=new ArrayList<Throwable>();var recovery=new CinematicRecovery(journal,a::teleport,Runnable::run,errors::add);
            recovery.backup(saved).join();recovery.activate(a.p,saved);var failed=new AtomicBoolean();
            recovery.recover(a.p,true,()->true,()->fail("missing world"),()->failed.set(true));
            assertTrue(failed.get());assertEquals(1,errors.size());assertTrue(journal.get(a.p.getUniqueId(),saved.token()).isPresent());
        }
    }

    @Test void offlineQuitCannotAbortTheRemainingGroupWhenItsTeleportFails() {
        try(var f=new Fixture(false,Runnable::run)) {
            f.session.tick();when(f.a.p.isOnline()).thenReturn(false);f.session.disconnect(f.a.p.getUniqueId());
            assertFalse(f.intro.contains(f.a.p.getUniqueId()));assertEquals(GameMode.ADVENTURE,f.a.mode.get());
            f.session.tick();assertEquals(SessionState.RUNNING,f.session.state().state());assertEquals(GameMode.SPECTATOR,f.b.mode.get());
            assertTrue(f.a.markers.get(CinematicRecovery.MARKER).startsWith("active:"));
        }
    }

    @Test void managerShutdownRestoresActiveCamerasBeforeClosingItsJournal() throws Exception {
        try(var f=new Fixture(false,Runnable::run);var managerFixture=new ReturnRecoveryRegressionTest.Fixture()) {
            f.session.tick();var field=SessionManager.class.getDeclaredField("sessions");field.setAccessible(true);
            @SuppressWarnings("unchecked") var sessions=(Map<String,DungeonSession>)field.get(managerFixture.manager);
            sessions.put(f.session.def().id(),f.session);managerFixture.manager.shutdown();
            f.a.original(f.original);f.b.original(f.original);assertFalse(f.session.introActive());
            assertTrue(f.a.markers.get(CinematicRecovery.MARKER).startsWith("restored:"));
        }
    }

    @Test void failedDefinitionReloadStillRestoresTemporaryStateBeforeReturnRecovery() {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var a=new Actor();var saved=CinematicRecovery.capture(a.p);var cinematic=t.manager.cinematics();
            when(a.p.teleport(any(Location.class))).thenAnswer(call->{a.at.set(call.getArgument(0));return true;});
            when(t.storage.returnTarget(a.p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.storage.takePendingExit(a.p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("definitions")));
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);
            when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(chunk));
            cinematic.backup(saved).join();cinematic.activate(a.p,saved);t.manager.connected(a.p);
            assertEquals(GameMode.ADVENTURE,a.mode.get());assertTrue(a.invulnerable.get());assertTrue(a.flight.get());assertFalse(a.flying.get());
            assertEquals(saved.position().x(),a.at.get().getX());assertTrue(a.markers.get(CinematicRecovery.MARKER).startsWith("restored:"));
            verify(a.p,never()).kick(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test void unresolvedActiveGenerationBlocksNewSessionsUntilRestorationIsConfirmedInMemory() {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var a=new Actor();t.bukkit.when(()->Bukkit.getPlayer(a.p.getUniqueId())).thenReturn(a.p);
            assertFalse(t.manager.recoveryPending(a.p.getUniqueId()));
            UUID token=UUID.randomUUID();a.markers.put(CinematicRecovery.MARKER,"active:"+token);
            assertTrue(t.manager.recoveryPending(a.p.getUniqueId()));assertEquals(JoinResult.RESETTING,t.manager.join(a.p,"missing"));
            a.markers.put(CinematicRecovery.MARKER,"restored:"+token);assertFalse(t.manager.recoveryPending(a.p.getUniqueId()));
        }
    }

    @Test void offlineTickBeforeQuitKeepsTheRemainingCameraRunningAndRetainsRecovery() {
        try(var f=new Fixture(false,Runnable::run)) {
            f.session.tick();when(f.a.p.isOnline()).thenReturn(false);
            assertDoesNotThrow(f.session::tick);assertTrue(f.session.introActive());
            assertFalse(f.intro.contains(f.a.p.getUniqueId()));assertEquals(GameMode.SPECTATOR,f.b.mode.get());
            assertEquals(GameMode.ADVENTURE,f.a.mode.get());assertTrue(f.a.markers.get(CinematicRecovery.MARKER).startsWith("active:"));
            assertEquals(1,f.errors.size());f.session.disconnect(f.a.p.getUniqueId());
            f.session.tick();assertEquals(SessionState.RUNNING,f.session.state().state());
        }
    }
}
