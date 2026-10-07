package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises scoreboard hooks through the real session/runtime lifecycle, without Paper. */
class SidebarLifecycleTest {
    @Test void priorBoardIsRestoredForLeaveQuitLastLifeForcedFinishAndRelease() throws Exception {
        for(String action:List.of("leave","quit","death","finish","release")) {
            var boards=new SessionSidebarTest();var f=new SessionRuntimeRegressionTest();f.configure();
            var yaml=ScoreboardTemplatesTest.bundled();when(f.plugin.getConfig()).thenReturn(yaml);when(f.plugin.messages()).thenReturn(SidebarDataTest.messages());
            var p=boards.player;when(p.isOnline()).thenReturn(true);when(p.getLocation()).thenReturn(new Location(f.world,1,64,1));
            when(p.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
            when(p.getInventory().getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
            when(f.storage.addPendingExit(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            try(var bukkit=mockStatic(Bukkit.class)) {
                bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
                var scoreboardManager=mock(org.bukkit.scoreboard.ScoreboardManager.class);
                when(scoreboardManager.getNewScoreboard()).thenReturn(boards.own);bukkit.when(Bukkit::getScoreboardManager).thenReturn(scoreboardManager);
                var manager=mock(SessionManager.class);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
                var s=new DungeonSession(new DungeonSessionFlowTest().definition(1),false,runtime);runtime.attach(s);s.join(p);
                assertSame(boards.own,p.getScoreboard(),action);
                switch(action) {
                    case "leave"->s.leave(p.getUniqueId());
                    case "quit"->{when(p.isOnline()).thenReturn(false);s.leave(p.getUniqueId());}
                    case "death"->s.playerDied(p.getUniqueId());
                    case "finish"->s.finish(false,true);
                    case "release"->runtime.released(s);
                }
                assertSame(boards.old,p.getScoreboard(),action);assertEquals(0,runtime.sidebar.size(),action);
            }
        }
    }
    @Test void delayedResultKeepsBoardUntilActualExitAndDisconnectRemovesItImmediately() throws Exception {
        var boards=new SessionSidebarTest();var f=new SessionRuntimeRegressionTest();f.configure();
        when(f.plugin.getConfig()).thenReturn(ScoreboardTemplatesTest.bundled());when(f.plugin.messages()).thenReturn(SidebarDataTest.messages());
        var p=boards.player;when(p.isOnline()).thenReturn(true);when(p.getLocation()).thenReturn(new Location(f.world,1,64,1));
        when(p.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        when(p.getInventory().getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var scoreboardManager=mock(org.bukkit.scoreboard.ScoreboardManager.class);
            when(scoreboardManager.getNewScoreboard()).thenReturn(boards.own);bukkit.when(Bukkit::getScoreboardManager).thenReturn(scoreboardManager);
            var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);
            var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
            var def=new DungeonSessionFlowTest().definition(3).withFinish(FinishMode.DELAYED,10,FinishDestination.EXIT,List.of());
            var s=new DungeonSession(def,false,runtime);runtime.attach(s);
            // Register this real runtime just as SessionManager.create does; no world-loading test fixture needed.
            @SuppressWarnings("unchecked") var runtimes=(Map<UUID,DungeonSessionRuntime>)field(manager,"runtimes");runtimes.put(s.id(),runtime);
            s.join(p);s.state().start();s.finish(true);assertSame(boards.own,p.getScoreboard());
            for(int i=0;i<20;i++)s.tick();
            assertTrue(boards.scores.values().stream().anyMatch(score->{
                return mockingDetails(score).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("customName")
                        && Arrays.stream(call.getArguments()).anyMatch(v->v.toString().contains("COMPLETADA")));
            }));
            manager.disconnected(p);assertSame(boards.old,p.getScoreboard());assertEquals(0,runtime.sidebar.size());
        }
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
}
