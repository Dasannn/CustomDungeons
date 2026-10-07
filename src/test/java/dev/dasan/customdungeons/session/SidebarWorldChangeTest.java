package dev.dasan.customdungeons.session;

import java.util.*;
import dev.dasan.customdungeons.model.*;
import org.bukkit.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SidebarWorldChangeTest {
    @Test void worldChangeKeepsLobbyAndRunningParticipantsAndRestoresOnlyAfterDetachment() throws Exception {
        for(boolean running:List.of(false,true)) {
            var boards=new SessionSidebarTest();var f=new SessionRuntimeRegressionTest();f.configure();
            when(f.plugin.getConfig()).thenReturn(ScoreboardTemplatesTest.bundled());when(f.plugin.messages()).thenReturn(SidebarDataTest.messages());
            var p=boards.player;when(p.isOnline()).thenReturn(true);when(p.getLocation()).thenReturn(new Location(f.world,1,64,1));
            try(var bukkit=mockStatic(Bukkit.class)) {
                bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
                var api=mock(org.bukkit.scoreboard.ScoreboardManager.class);when(api.getNewScoreboard()).thenReturn(boards.own);
                bukkit.when(Bukkit::getScoreboardManager).thenReturn(api);
                var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);
                var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
                var s=new DungeonSession(new DungeonSessionFlowTest().definition(3),false,runtime);runtime.attach(s);
                var players=map(manager,"players");players.put(p.getUniqueId(),s);map(manager,"runtimes").put(s.id(),runtime);
                s.join(p);if(running)s.state().start();
                var other=mock(World.class);when(other.getName()).thenReturn("other");when(p.getLocation()).thenReturn(new Location(other,0,64,0));
                var event=mock(PlayerChangedWorldEvent.class);when(event.getPlayer()).thenReturn(p);
                new SessionListener(manager).changedWorld(event);
                assertSame(boards.own,p.getScoreboard());assertTrue(s.survivors().contains(p.getUniqueId()));
                players.remove(p.getUniqueId());new SessionListener(manager).changedWorld(event);
                assertSame(boards.old,p.getScoreboard());assertEquals(0,runtime.sidebar.size());
            }
        }
    }
    @Test void completedDepartureToAnotherWorldRestoresEvenWhileExitTickerStillRuns() throws Exception {
        var boards=new SessionSidebarTest();var f=new SessionRuntimeRegressionTest();f.configure();
        when(f.plugin.getConfig()).thenReturn(ScoreboardTemplatesTest.bundled());when(f.plugin.messages()).thenReturn(SidebarDataTest.messages());
        var p=boards.player;when(p.isOnline()).thenReturn(true);when(p.getLocation()).thenReturn(new Location(f.world,1,64,1));
        when(p.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        when(p.getInventory().getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var api=mock(org.bukkit.scoreboard.ScoreboardManager.class);when(api.getNewScoreboard()).thenReturn(boards.own);bukkit.when(Bukkit::getScoreboardManager).thenReturn(api);
            var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);
            var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
            var def=new DungeonSessionFlowTest().definition(3).withFinish(FinishMode.DELAYED,10,FinishDestination.EXIT,List.of());
            var s=new DungeonSession(def,false,runtime);runtime.attach(s);
            map(manager,"players").put(p.getUniqueId(),s);map(manager,"runtimes").put(s.id(),runtime);
            s.join(p);s.state().start();s.finish(true);manager.worldChanged(p);assertSame(boards.own,p.getScoreboard());
            var other=mock(World.class);when(other.getName()).thenReturn("other");when(p.getLocation()).thenReturn(new Location(other,0,64,0));
            manager.worldChanged(p);assertSame(boards.old,p.getScoreboard());assertTrue(s.evacuating());
        }
    }
    @Test void scoreboardCreationFailureCannotAbortJoinOrItsReadyCallback() throws Exception {
        var f=new SessionRuntimeRegressionTest();f.configure();
        when(f.plugin.getConfig()).thenReturn(ScoreboardTemplatesTest.bundled());when(f.plugin.messages()).thenReturn(SidebarDataTest.messages());
        var p=mock(org.bukkit.entity.Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOnline()).thenReturn(true);when(p.getLocation()).thenReturn(new Location(f.world,1,64,1));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var api=mock(org.bukkit.scoreboard.ScoreboardManager.class);when(api.getNewScoreboard()).thenThrow(new IllegalStateException("scoreboard API unavailable"));
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(api);
            var manager=mock(SessionManager.class);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());
            var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
            var s=new DungeonSession(new DungeonSessionFlowTest().definition(3),false,runtime);runtime.attach(s);
            assertEquals(JoinResult.OK,assertDoesNotThrow(()->s.join(p)));assertTrue(s.survivors().contains(p.getUniqueId()));
            verify(manager).teleportPrepared(eq(p),any(),eq(false));assertEquals(0,runtime.sidebar.size());
            verify(f.plugin.getLogger()).log(eq(java.util.logging.Level.WARNING),anyString(),any(RuntimeException.class));
        }
    }
    @SuppressWarnings("unchecked") private static Map<UUID,Object> map(Object target,String name) throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return (Map<UUID,Object>)field.get(target);
    }
}
