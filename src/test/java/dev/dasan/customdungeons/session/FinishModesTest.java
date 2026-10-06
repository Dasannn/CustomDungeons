package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinishModesTest {
    final StartModesTest fixture=new StartModesTest();
    final List<UUID> teleported=new ArrayList<>();
    final Map<UUID,Point> targets=new HashMap<>();
    final Set<UUID> inside=new HashSet<>(), onPlate=new HashSet<>();
    final SessionServices effects=new SessionServices() {
        public void teleport(Player p,Point target) {teleported.add(p.getUniqueId());targets.put(p.getUniqueId(),target);inside.remove(p.getUniqueId());}
        public boolean inside(DungeonSession s,Player p){return inside.contains(p.getUniqueId());}
        public boolean onExitPlate(DungeonSession s,Player p){return onPlate.contains(p.getUniqueId());}
    };
    DungeonSession session(FinishMode mode,boolean completed) {
        var d=fixture.definition(StartMode.AUTO,false,true).withFinish(mode,10,FinishDestination.EXIT,List.of());
        var s=new DungeonSession(d,false,effects);var p=fixture.player();s.join(p);s.forceStart();
        inside.add(p.getUniqueId());teleported.clear();s.finish(completed);return s;
    }
    void ticks(DungeonSession s,int n){for(int i=0;i<n;i++)s.tick();}
    @Test void immediateTeleportsAndReleases() {
        var s=session(FinishMode.IMMEDIATE,true);assertEquals(1,teleported.size());assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void delayedKeepsDungeonBusyUntilDeadline() {
        var s=session(FinishMode.DELAYED,true);assertEquals(SessionState.COMPLETED,s.state().state());
        ticks(s,199);assertTrue(teleported.isEmpty());s.tick();assertEquals(1,teleported.size());assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void delayedReleasesEarlyWhenEveryonePhysicallyLeavesWithoutAnotherTeleport() {
        var s=session(FinishMode.DELAYED,true);inside.clear();ticks(s,10);
        assertEquals(SessionState.FREE,s.state().state());assertTrue(s.players().isEmpty());assertTrue(teleported.isEmpty());
    }
    @Test void delayedStillWaitsForTheLastOccupantIncludingFormerParticipants() {
        var d=fixture.definition(StartMode.AUTO,false,true).withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of());
        var s=new DungeonSession(d,false,effects);var a=fixture.player();var b=fixture.player();s.join(a);s.join(b);s.forceStart();inside.addAll(s.survivors());teleported.clear();s.finish(true);
        inside.remove(a.getUniqueId());ticks(s,10);assertEquals(SessionState.COMPLETED,s.state().state());assertEquals(Set.of(b.getUniqueId()),s.survivors());
        inside.remove(b.getUniqueId());ticks(s,10);assertEquals(SessionState.FREE,s.state().state());assertTrue(teleported.isEmpty());
    }
    @Test void allDelayedPlayersUsingExitPlatesReleaseBeforeDeadline() {
        var d=fixture.definition(StartMode.AUTO,false,true).withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of());
        var s=new DungeonSession(d,false,effects);var a=fixture.player();var b=fixture.player();s.join(a);s.join(b);s.forceStart();inside.addAll(s.survivors());teleported.clear();s.finish(true);
        s.tick();onPlate.addAll(s.survivors());s.exitPlate(a.getUniqueId());s.exitPlate(b.getUniqueId());ticks(s,10);
        assertEquals(SessionState.FREE,s.state().state());assertEquals(2,teleported.size());ticks(s,1200);assertEquals(2,teleported.size());
    }
    @Test void noneReleasesOnlyWhenAllHaveLeftOrSafetyDeadline() {
        var s=session(FinishMode.NONE,true);ticks(s,5999);assertTrue(teleported.isEmpty());s.tick();assertEquals(1,teleported.size());assertEquals(SessionState.FREE,s.state().state());
        s=session(FinishMode.NONE,true);inside.clear();ticks(s,10);assertEquals(SessionState.FREE,s.state().state());assertEquals(0,s.players().size());
    }
    @Test void exitPlateIsIndividualAndOnlyAfterCompletionForDelayedAndNone() {
        for(var mode:List.of(FinishMode.DELAYED,FinishMode.NONE)) {
            inside.clear();onPlate.clear();teleported.clear();
            var d=fixture.definition(StartMode.AUTO,false,true).withFinish(mode,10,FinishDestination.PREVIOUS,List.of());
            var s=new DungeonSession(d,false,effects);var a=fixture.player();var b=fixture.player();s.join(a);s.join(b);s.forceStart();
            inside.addAll(s.survivors());onPlate.add(a.getUniqueId());teleported.clear();ticks(s,10);assertTrue(teleported.isEmpty());
            s.finish(true);ticks(s,10);assertEquals(List.of(a.getUniqueId()),teleported);assertEquals(1,s.players().size());assertEquals(new Point("world",20,64,20,0,0),targets.get(a.getUniqueId()));
            ticks(s,mode==FinishMode.DELAYED?200:6000);assertEquals(2,teleported.size());
        }
    }
    @Test void playerReenteringWhileAnotherWaitsKeepsDungeonReserved() {
        var d=fixture.definition(StartMode.AUTO,false,true).withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of());
        var s=new DungeonSession(d,false,effects);var a=fixture.player();var b=fixture.player();s.join(a);s.join(b);s.forceStart();inside.addAll(s.survivors());s.finish(true);
        inside.remove(a.getUniqueId());ticks(s,10);inside.add(a.getUniqueId());inside.remove(b.getUniqueId());ticks(s,10);
        assertEquals(SessionState.COMPLETED,s.state().state());inside.clear();ticks(s,10);assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void failedRunDoesNotActivateExitPlate() {
        var s=session(FinishMode.NONE,false);onPlate.addAll(s.survivors());ticks(s,10);assertTrue(teleported.isEmpty());
    }
    @Test void physicalExitPlateActsImmediatelyOnlyAfterCompletion() {
        var d=fixture.definition(StartMode.AUTO,false,true).withFinish(FinishMode.DELAYED,60,FinishDestination.PREVIOUS,List.of());
        var s=new DungeonSession(d,false,effects);var a=fixture.player();var b=fixture.player();s.join(a);s.join(b);s.forceStart();
        inside.addAll(s.survivors());teleported.clear();
        assertFalse(s.exitPlate(a.getUniqueId()));
        s.finish(true);s.tick();onPlate.add(a.getUniqueId());
        assertTrue(s.exitPlate(a.getUniqueId()));
        assertEquals(List.of(a.getUniqueId()),teleported);
        assertEquals(new Point("world",20,64,20,0,0),targets.get(a.getUniqueId()));
        assertEquals(1,s.players().size());assertEquals(SessionState.COMPLETED,s.state().state());
    }
    @Test void timeoutForcesEveryModeToSelectedPreviousDestination() {
        for(var mode:FinishMode.values()) {
            var d=fixture.definition(StartMode.AUTO,false,true);
            d=new DungeonDef(d.id(),d.displayName(),true,d.lobby(),d.exit(),1,0,0,3,false,1,0,false,d.scaling(),d.hooks(),d.reward(),d.rooms())
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10).withFinish(mode,10,FinishDestination.PREVIOUS,List.of());
            var p=fixture.player();var fx=mock(SessionServices.class,CALLS_REAL_METHODS);var s=new DungeonSession(d,false,fx);
            s.join(p);s.forceStart();clearInvocations(fx);ticks(s,20);
            verify(fx).teleport(p,new Point("world",20,64,20,0,0));assertEquals(SessionState.FREE,s.state().state());
        }
    }
    @Test void terminalStatesRejectNewRunAsVacating() {
        for(var state:List.of(SessionState.COMPLETED,SessionState.FAILED,SessionState.RESETTING))
            assertEquals(JoinResult.RESETTING,JoinRules.check(state,true,false,0,0,false,null,java.time.Instant.now(),false,true));
    }
}
