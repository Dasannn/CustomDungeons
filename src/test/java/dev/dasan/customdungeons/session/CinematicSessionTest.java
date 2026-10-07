package dev.dasan.customdungeons.session;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinematicSessionTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    DungeonDef def(boolean tp,boolean intro) {
        var b=new DungeonSessionFlowTest().definition(3);
        return new DungeonDef(b.id(),b.displayName(),true,b.lobby(),b.exit(),1,0,0,3,false,1,0,false,b.scaling(),b.hooks(),b.reward(),b.rooms())
            .withStart(StartMode.AUTO,List.of(),3,null,tp,true,intro,5);
    }
    @Test void noWavesOrLimitDuringIntroAndStartTeleportIsDeferred() {
        var effects=mock(SessionServices.class,CALLS_REAL_METHODS);when(effects.introTick(any())).thenReturn(true);
        var s=new DungeonSession(def(true,true),false,effects);var p=new StartModesTest().player();s.join(p);clearInvocations(effects);s.forceStart();
        assertTrue(s.introActive());verify(effects,never()).teleport(p,s.checkpoint());
        s.enterRoom(0);for(int i=0;i<150;i++)s.tick();
        assertFalse(s.roomStarted());assertEquals(SessionState.RUNNING,s.state().state());assertEquals(0,s.elapsedTicks());
        when(effects.introTick(s)).thenReturn(false);s.tick();assertFalse(s.introActive());
        // Per-player restoration/TP belongs to the cinematic boundary.
        for(int i=0;i<19;i++)s.tick();assertEquals(SessionState.RUNNING,s.state().state());
        s.tick();assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void ordinaryTicksNeverConsultCinematicBoundary() {
        var effects=mock(SessionServices.class,CALLS_REAL_METHODS);var s=new DungeonSession(def(false,false),false,effects);
        s.join(new StartModesTest().player());s.forceStart();for(int i=0;i<10;i++)s.tick();
        verify(effects,never()).introTick(any());assertFalse(s.introActive());
    }
    @Test void allTerminationPathsCancelBeforeOtherCleanup() {
        for(String end:List.of("leave","disconnect","stop")) {
            var effects=mock(SessionServices.class,CALLS_REAL_METHODS);var s=new DungeonSession(def(false,true),false,effects);
            var p=new StartModesTest().player();s.join(p);s.forceStart();
            switch(end){case "leave"->s.leave(p.getUniqueId());case "disconnect"->s.disconnect(p.getUniqueId());default->s.finish(false,true);}
            verify(effects).introRestore(s,p);assertFalse(s.introActive());
        }
    }

    @Test void sidebarSelectsIntroAndDoesNotExposeATimeLimitCountdown() {
        var effects=mock(SessionServices.class,CALLS_REAL_METHODS);when(effects.introTick(any())).thenReturn(true);
        var s=new DungeonSession(def(false,true),false,effects);var p=new StartModesTest().player();s.join(p);s.forceStart();
        var data=SidebarData.capture(s,p,SidebarDataTest.messages(),0,false);
        assertEquals("intro",data.state());assertNotNull(data.values().get("objective"));assertFalse(data.values().containsKey("time_left"));
    }
    @Test void disconnectDuringGroupIntroNeverRecordsT44Penalty() throws Exception {
        try(var f=new ReturnRecoveryRegressionTest.Fixture()) {
            var s=mock(DungeonSession.class);var state=new SessionStateMachine();state.openLobby();state.start();
            when(s.state()).thenReturn(state);when(s.introActive()).thenReturn(true);when(s.def()).thenReturn(def(false,true));
            var field=SessionManager.class.getDeclaredField("players");field.setAccessible(true);
            @SuppressWarnings("unchecked") var owners=(Map<UUID,DungeonSession>)field.get(f.manager);owners.put(f.player.getUniqueId(),s);
            f.manager.disconnected(f.player,org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED);
            verify(f.storage,never()).saveDisconnect(any());verify(f.storage).addPendingExit(f.player.getUniqueId(),s.def().exit());
            verify(s).disconnect(f.player.getUniqueId());assertTrue(f.manager.sessionOf(f.player.getUniqueId()).isEmpty());
        }
    }
    @Test void cameraPreventsSpectatorEntityBindingAndRoutesSneakToOneViewer() {
        var s=mock(DungeonSession.class);when(s.introActive()).thenReturn(true);
        var p=new StartModesTest().player();var f=new SessionRuntimeRegressionTest();f.configure();
        var manager=mock(SessionManager.class);var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
        when(manager.sessionOf(p.getUniqueId())).thenReturn(Optional.of(s));when(manager.runtime(s)).thenReturn(runtime);
        // Empty viewer set must not take control of an unrelated spectator.
        var event=new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(p,null,mock(org.bukkit.entity.Entity.class));
        new SessionListener(manager).spectate(event);assertFalse(event.isCancelled());
    }
}
