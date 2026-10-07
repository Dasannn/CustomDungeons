package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisconnectTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(org.bukkit.event.player.PlayerQuitEvent.QuitReason.class)
    void quitCauseSelectsPenaltyOrSafeExit(org.bukkit.event.player.PlayerQuitEvent.QuitReason reason) throws Exception {
        for(var configured:DisconnectMode.values())try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var s=new DungeonSession(t.f.definition().withDisconnectMode(configured),false,new SessionServices() {});
            s.join(t.player);
            SessionRuntimeRegressionTest.field(t.manager,"players",new HashMap<>(Map.of(t.player.getUniqueId(),s)));
            var quit=mock(org.bukkit.event.player.PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(t.player);when(quit.getReason()).thenReturn(reason);
            new SessionListener(t.manager).quit(quit);
            var captured=org.mockito.ArgumentCaptor.forClass(dev.dasan.customdungeons.storage.DisconnectRecord.class);
            verify(t.storage).saveDisconnect(captured.capture());
            var expected=switch(reason) {
                case DISCONNECTED,TIMED_OUT -> configured;
                case KICKED,ERRONEOUS_STATE -> DisconnectMode.RETURN_TO_EXIT;
            };
            assertEquals(expected,captured.getValue().mode());assertTrue(s.survivors().isEmpty());
            verify(t.player,never()).setHealth(anyDouble());
        }
    }
    @Test void oldYamlAndNewDungeonsDefaultToDeath() {
        var codec=new DefinitionCodec();
        var old=new org.bukkit.configuration.file.YamlConfiguration();
        assertEquals("DIE_AND_DROP",codec.encode(codec.decodeDungeon("old",old)).get("disconnect-mode"));
        assertEquals("DIE_AND_DROP",codec.encode(new DungeonSessionFlowTest().definition(3)).get("disconnect-mode"));
    }
    @Test void quitImmediatelyRemovesParticipantAndOccupantWithoutTeleporting() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var second=mock(Player.class);when(second.getUniqueId()).thenReturn(UUID.randomUUID());
            var teleports=new ArrayList<UUID>();
            var s=new DungeonSession(t.f.definition(),false,new SessionServices() {
                public void teleport(Player p,Point at){teleports.add(p.getUniqueId());}
                public boolean inside(DungeonSession s,Player p){return true;}
            });
            s.join(t.player);s.join(second);teleports.clear();
            SessionRuntimeRegressionTest.field(t.manager,"players",new HashMap<>(Map.of(t.player.getUniqueId(),s,second.getUniqueId(),s)));
            t.manager.disconnected(t.player);
            assertEquals(Set.of(second.getUniqueId()),s.survivors());
            assertEquals(Set.of(second.getUniqueId()),s.recoveryPlayers());
            assertEquals(SessionState.LOBBY,s.state().state());
            assertTrue(teleports.isEmpty(),"Quit must preserve the captured position and never teleport");
            assertEquals(JoinResult.OK,s.join(t.player),"A lobby slot must be available immediately");
        }
    }
    @Test void explicitExitModeSurvivesYamlAndModelRebuilds() throws Exception {
        var codec=new DefinitionCodec();var def=new DungeonSessionFlowTest().definition(3).withDisconnectMode(DisconnectMode.RETURN_TO_EXIT);
        var y=new org.bukkit.configuration.file.YamlConfiguration();codec.encode(def).forEach(y::set);
        var roundTrip=new org.bukkit.configuration.file.YamlConfiguration();roundTrip.loadFromString(y.saveToString());
        assertEquals(def,codec.decodeDungeon(def.id(),roundTrip));
        assertEquals(def,dev.dasan.customdungeons.config.SpawnerPresets.resolve(def,Map.of()));
        assertEquals(DisconnectMode.RETURN_TO_EXIT,def.withFinish(FinishMode.NONE,60,FinishDestination.PREVIOUS,List.of()).disconnectMode());
        assertEquals(DisconnectMode.RETURN_TO_EXIT,def.withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10).disconnectMode());
    }
    @Test void stoppingServerDoesNotWriteAPenaltyOrRemoveCrashRecoveryParticipants() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var s=new DungeonSession(t.f.definition(),false,new SessionServices() {});s.join(t.player);
            SessionRuntimeRegressionTest.field(t.manager,"players",new HashMap<>(Map.of(t.player.getUniqueId(),s)));
            t.bukkit.when(Bukkit::isStopping).thenReturn(true);t.manager.disconnected(t.player);
            verify(t.storage,never()).saveDisconnect(any());assertTrue(s.survivors().contains(t.player.getUniqueId()));
        }
    }
    @Test void runningGroupContinuesAndQuitRecordCapturesRulesAndPosition() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var second=mock(Player.class);when(second.getUniqueId()).thenReturn(UUID.randomUUID());
            var def=t.f.definition().withDisconnectMode(DisconnectMode.RETURN_TO_EXIT);
            var s=new DungeonSession(def,false,new SessionServices() {});s.join(t.player);s.join(second);s.forceStart();
            SessionRuntimeRegressionTest.field(t.manager,"players",new HashMap<>(Map.of(t.player.getUniqueId(),s)));
            t.manager.disconnected(t.player);
            assertEquals(SessionState.RUNNING,s.state().state());assertEquals(List.of(second),List.copyOf(s.players()));
            var captured=org.mockito.ArgumentCaptor.forClass(dev.dasan.customdungeons.storage.DisconnectRecord.class);
            verify(t.storage).saveDisconnect(captured.capture());
            var record=captured.getValue();assertEquals(t.player.getUniqueId(),record.player());assertEquals(s.id(),record.sessionId());
            assertEquals(def.id(),record.dungeonId());assertEquals(DisconnectMode.RETURN_TO_EXIT,record.mode());
            assertEquals(new Point("world",20,64,20,0,0),record.position());assertFalse(record.keepInventory());
        }
    }
    @Test void lastQuitImmediatelyReleasesFinalEvacuationAndFailsAnEmptyGame() {
        var p=new StartModesTest().player();var effects=mock(SessionServices.class,CALLS_REAL_METHODS);
        var def=new DungeonSessionFlowTest().definition(3).withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of());
        doReturn(true).when(effects).inside(any(),eq(p));
        var s=new DungeonSession(def,false,effects);s.join(p);s.forceStart();s.finish(true);assertTrue(s.evacuating());
        s.disconnect(p.getUniqueId());assertEquals(SessionState.FREE,s.state().state());
        var active=new DungeonSession(def,false,effects);active.join(p);active.disconnect(p.getUniqueId());
        assertEquals(SessionState.FREE,active.state().state());assertTrue(active.survivors().isEmpty());
        assertTrue(active.recoveryPlayers().isEmpty());
    }
    @Test void synchronousDatabaseFailureStillAbandonsImmediately() throws Exception {
        try(var t=new ReturnRecoveryRegressionTest.Fixture()) {
            var s=new DungeonSession(t.f.definition(),false,new SessionServices() {});s.join(t.player);
            SessionRuntimeRegressionTest.field(t.manager,"players",new HashMap<>(Map.of(t.player.getUniqueId(),s)));
            when(t.storage.saveDisconnect(any())).thenThrow(new IllegalStateException("closed"));
            assertDoesNotThrow(()->t.manager.disconnected(t.player));assertTrue(s.survivors().isEmpty());
            assertTrue(t.manager.sessionOf(t.player.getUniqueId()).isEmpty());
        }
    }
}
