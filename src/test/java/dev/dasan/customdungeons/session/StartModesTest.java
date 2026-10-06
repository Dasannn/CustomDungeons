package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartModesTest {
    DungeonDef definition(StartMode mode,boolean startTp,boolean finishTp) {
        var base=new DungeonSessionFlowTest().definition(3);
        return base.withStart(mode,List.of(base.lobby()),1,null,startTp,finishTp,false,10);
    }
    Player player() {
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        var world=mock(World.class);when(world.getName()).thenReturn("world");
        when(p.getLocation()).thenReturn(new Location(world,20,64,20));return p;
    }
    @Test void firstRoomWaitsUntilParticipantEntersAndPollsEveryTenTicks() {
        var p=player();var s=new DungeonSession(definition(StartMode.AUTO,false,true),false,new SessionServices() {});
        s.join(p);s.forceStart();assertFalse(s.roomStarted());
        for(int i=0;i<10;i++) s.tick();assertFalse(s.roomStarted());
        p.getLocation().setX(1);p.getLocation().setZ(1);
        for(int i=0;i<9;i++) s.tick();assertFalse(s.roomStarted());
        s.tick();assertEquals(1,s.roomIndex());
    }
    @Test void completesAndFailsWithoutExitTpButStillCleansSession() {
        for(boolean completed:List.of(false,true)) {
            var p=player();var effects=mock(SessionServices.class,CALLS_REAL_METHODS);
            var s=new DungeonSession(definition(StartMode.AUTO,false,false),false,effects);
            s.join(p);clearInvocations(effects);s.forceStart();s.finish(completed);
            verify(effects,never()).teleport(p,s.def().exit());
            verify(effects,never()).teleport(p,s.checkpoint());
            verify(effects).finish(s); assertEquals(SessionState.FREE,s.state().state());assertTrue(s.players().isEmpty());
        }
    }
    @Test void oldStartTeleportDoesNotActivateWavesImmediately() {
        var p=player();var effects=mock(SessionServices.class,CALLS_REAL_METHODS);
        var s=new DungeonSession(definition(StartMode.AUTO,true,true),false,effects);
        s.join(p);clearInvocations(effects);s.forceStart();verify(effects).teleport(p,s.checkpoint());assertFalse(s.roomStarted());
    }
    @Test void plateCountdownCancelsEvenWhileChunksArePreparing() {
        var p=player();var effects=mock(SessionServices.class,CALLS_REAL_METHODS);
        when(effects.platesReady(any())).thenReturn(false);
        var s=new DungeonSession(definition(StartMode.PLATES,false,true),false,effects);s.join(p);
        for(int i=0;i<30;i++) s.tick();assertEquals(SessionState.LOBBY,s.state().state());
        when(effects.platesReady(s)).thenReturn(true); when(effects.prepareStart(s)).thenReturn(false);
        for(int i=0;i<21;i++) s.tick();assertEquals(SessionState.LOBBY,s.state().state());
        when(effects.platesReady(s)).thenReturn(false);when(effects.prepareStart(s)).thenReturn(true);
        s.tick();assertEquals(SessionState.LOBBY,s.state().state());
        when(effects.platesReady(s)).thenReturn(true);
        for(int i=0;i<20;i++) s.tick();assertEquals(SessionState.LOBBY,s.state().state());
        s.tick();assertEquals(SessionState.RUNNING,s.state().state());
    }
    @Test void timeLimitRunsWhileWaitingForEntry() {
        var d=definition(StartMode.AUTO,false,false);
        d=new DungeonDef(d.id(),d.displayName(),true,d.lobby(),d.exit(),1,0,0,3,false,1,0,false,d.scaling(),d.hooks(),d.reward(),d.rooms())
                .withStart(StartMode.AUTO,List.of(),3,null,false,false,false,10);
        var s=new DungeonSession(d,false,new SessionServices() {});s.join(player());s.forceStart();
        for(int i=0;i<20;i++) s.tick();assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void onlyGroundedSessionPlayersOnRealPlatesCount() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var f=new SessionRuntimeRegressionTest();f.configure();
        var d=definition(StartMode.PLATES,false,true);var s=mock(DungeonSession.class);when(s.def()).thenReturn(d);
        var member=player();when(member.isOnline()).thenReturn(true);when(member.isOnGround()).thenReturn(true);
        when(member.getGameMode()).thenReturn(GameMode.SURVIVAL);when(s.players()).thenReturn(List.of(member));
        var outsider=player();outsider.getLocation().setX(0);outsider.getLocation().setZ(0);
        var block=mock(org.bukkit.block.Block.class);when(block.getType()).thenReturn(Material.STONE_PRESSURE_PLATE);
        when(f.world.getBlockAt(0,64,0)).thenReturn(block);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            var runtime=new DungeonSessionRuntime(f.plugin,mock(SessionManager.class),f.definitions,f.config,f.storage);
            assertFalse(runtime.platesReady(s));
            member.getLocation().setX(.5);member.getLocation().setZ(.5);assertTrue(runtime.platesReady(s));
            when(member.getGameMode()).thenReturn(GameMode.SPECTATOR);assertFalse(runtime.platesReady(s));
            when(member.getGameMode()).thenReturn(GameMode.SURVIVAL);when(member.isOnGround()).thenReturn(false);assertFalse(runtime.platesReady(s));
            when(member.isOnGround()).thenReturn(true);when(block.getType()).thenReturn(Material.AIR);assertFalse(runtime.platesReady(s));
        }
    }

    @Test void failedEntranceAttemptRetriesOnSessionTickerAfterRunningTransition() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var f=new SessionRuntimeRegressionTest();f.configure();
        var runtime=new DungeonSessionRuntime(f.plugin,mock(SessionManager.class),f.definitions,f.config,f.storage);
        var effects=new SessionServices(){public void start(DungeonSession s){runtime.start(s);}};
        var s=new DungeonSession(definition(StartMode.AUTO,false,true),false,effects);runtime.attach(s);
        runtime.doors=mock(DoorService.class);
        when(runtime.doors.openEntrance()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false),
                java.util.concurrent.CompletableFuture.completedFuture(true));
        s.join(player());s.forceStart();assertEquals(SessionState.RUNNING,s.state().state());
        verify(runtime.doors).openEntrance();
        for(int i=0;i<20;i++)s.tick();verify(runtime.doors,times(2)).openEntrance();
    }

}
