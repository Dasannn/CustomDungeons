package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExitRuntimeTest {
    @org.junit.jupiter.api.BeforeAll static void api(){dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @Test void onlyOnlineGroundedPlayerOnRealBlackstonePlateCanExit() {
        var f=new SessionRuntimeRegressionTest();f.configure();var p=new StartModesTest().player();
        var point=new Point("world",1.5,64,1.5,0,0);var d=f.definition().withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of(point));
        var s=mock(DungeonSession.class);when(s.def()).thenReturn(d);
        when(p.isOnline()).thenReturn(true);when(p.isOnGround()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var block=mock(Block.class);when(block.getType()).thenReturn(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);when(f.world.getBlockAt(1,64,1)).thenReturn(block);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            var runtime=new DungeonSessionRuntime(f.plugin,mock(SessionManager.class),f.definitions,f.config,f.storage);
            assertFalse(runtime.onExitPlate(s,p));p.getLocation().setX(1.5);p.getLocation().setZ(1.5);assertTrue(runtime.onExitPlate(s,p));
            when(block.getType()).thenReturn(Material.STONE_PRESSURE_PLATE);assertFalse(runtime.onExitPlate(s,p));
            when(block.getType()).thenReturn(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);when(p.isOnGround()).thenReturn(false);assertFalse(runtime.onExitPlate(s,p));
        }
    }
    @Test void lobbyTeleportWaitsForDurablePreviousAndActiveJournals() {
        var f=new SessionRuntimeRegressionTest();f.configure();var manager=mock(SessionManager.class);
        var persisted=new CompletableFuture<Void>();when(manager.persistJoin(any(),any(),any())).thenReturn(persisted);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(manager).main(any());
        var storage=mock(SqlStorage.class);var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,storage);
        var s=new DungeonSession(f.definition().withFinish(FinishMode.DELAYED,60,FinishDestination.PREVIOUS,List.of()),false,runtime);runtime.attach(s);
        var p=new StartModesTest().player();when(p.isOnline()).thenReturn(true);s.join(p);
        var playerId=p.getUniqueId();var capture=org.mockito.ArgumentCaptor.forClass(ReturnTarget.class);verify(manager).persistJoin(eq(s),eq(playerId),capture.capture());
        assertEquals(new Point("world",20,64,20,0,0),capture.getValue().previous());assertEquals(FinishDestination.PREVIOUS,capture.getValue().destination());
        verify(manager,never()).teleport(any(),any());s.forceStart();assertEquals(SessionState.LOBBY,s.state().state());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));persisted.complete(null);
            verify(manager).teleport(eq(p),any());
        }
    }
    @Test void recoveryGuardRejectsJoinUntilPreviousOccupantsLeaveRegions() {
        var f=new SessionRuntimeRegressionTest();f.configure();when(f.definitions.dungeons()).thenReturn(Map.of("test",f.definition()));
        var old=new StartModesTest().player();when(old.isOnline()).thenReturn(true);old.getLocation().setX(1);old.getLocation().setZ(1);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            bukkit.when(()->Bukkit.getPlayer(old.getUniqueId())).thenReturn(old);
            var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);manager.recoverOccupants("test",Set.of(old.getUniqueId()));
            assertTrue(manager.vacating("test"));assertEquals(JoinResult.RESETTING,manager.join(new StartModesTest().player(),"test"));
            old.getLocation().setX(100);assertFalse(manager.vacating("test"));
        }
    }
    @Test void reconnectPreloadsPreviousPositionBeforeCheckingSafetyAndTeleporting() {
        var f=new SessionRuntimeRegressionTest();f.configure();when(f.definitions.dungeons()).thenReturn(Map.of());when(f.plugin.isEnabled()).thenReturn(true);
        var p=new StartModesTest().player();when(p.isOnline()).thenReturn(true);when(p.teleport(any(Location.class))).thenReturn(true);var uuid=p.getUniqueId();
        var target=new ReturnTarget(UUID.randomUUID(),new Point("world",20,64,20,0,0),new Point("world",99,64,0,0,0),FinishDestination.PREVIOUS);
        var storage=mock(SqlStorage.class);when(storage.returnTarget(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(target)));
        when(storage.takePendingExit(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(target.exit())));
        when(storage.clearReturnTarget(uuid,target.sessionId())).thenReturn(CompletableFuture.completedFuture(null));
        var loaded=new java.util.concurrent.atomic.AtomicBoolean();when(f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(call->loaded.get());
        var chunkReady=new CompletableFuture<Chunk>();when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(chunkReady);
        when(f.world.getMinHeight()).thenReturn(-64);when(f.world.getMaxHeight()).thenReturn(320);
        var border=mock(WorldBorder.class);when(border.isInside(any())).thenReturn(true);when(f.world.getWorldBorder()).thenReturn(border);
        var feet=mock(Block.class);var head=mock(Block.class);var floor=mock(Block.class);when(f.world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(head);when(feet.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(floor);
        when(feet.isPassable()).thenReturn(true);when(head.isPassable()).thenReturn(true);when(feet.getType()).thenReturn(Material.AIR);when(head.getType()).thenReturn(Material.AIR);
        var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);when(floor.getType()).thenReturn(solid);
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
        doAnswer(call->{((Runnable)call.getArgument(1)).run();return mock(org.bukkit.scheduler.BukkitTask.class);}).when(scheduler).runTask(eq(f.plugin),any(Runnable.class));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            var manager=new SessionManager(f.plugin,f.definitions,f.config,storage);manager.connected(p);
            verify(p,never()).teleport(any(Location.class));verify(f.world,never()).getBlockAt(any(Location.class));
            assertEquals(JoinResult.RESETTING,manager.join(p,"test"));
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(f.world);loaded.set(true);chunkReady.complete(chunk);
            verify(p).teleport(argThat((Location at)->at.getX()==20 && at.getZ()==20));
            verify(storage).clearReturnTarget(uuid,target.sessionId());assertEquals(JoinResult.DISABLED,manager.join(p,"test"));
        }
    }
    @Test void unsafeOrMissingPreviousWorldFallsBackToExit() {
        var f=new SessionRuntimeRegressionTest();f.configure();var point=new Point("world",1,64,1,0,0);
        try(var bukkit=mockStatic(Bukkit.class)) {
            assertFalse(DungeonSessionRuntime.safePrevious(point));bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            when(f.world.getMinHeight()).thenReturn(-64);when(f.world.getMaxHeight()).thenReturn(320);
            var border=mock(WorldBorder.class);when(border.isInside(any())).thenReturn(true);when(f.world.getWorldBorder()).thenReturn(border);
            var feet=mock(Block.class);var head=mock(Block.class);var floor=mock(Block.class);when(f.world.getBlockAt(1,64,1)).thenReturn(feet);when(f.world.getBlockAt(any(Location.class))).thenReturn(feet);
            when(feet.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(head);when(feet.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(floor);
            when(feet.isPassable()).thenReturn(true);when(head.isPassable()).thenReturn(true);when(feet.getType()).thenReturn(Material.AIR);when(head.getType()).thenReturn(Material.AIR);
            var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);when(floor.getType()).thenReturn(solid);
            assertTrue(DungeonSessionRuntime.safePrevious(point));
            var runtime=new DungeonSessionRuntime(f.plugin,mock(SessionManager.class),f.definitions,f.config,f.storage);
            var session=mock(DungeonSession.class);var def=f.definition().withFinish(FinishMode.NONE,60,FinishDestination.PREVIOUS,List.of());
            when(session.def()).thenReturn(def);when(session.previous(any())).thenReturn(point);
            // Give EXIT a distinct, outside location while the saved position is safe but inside a room.
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();var codec=new dev.dasan.customdungeons.config.DefinitionCodec();codec.encode(def).forEach(yaml::set);yaml.set("exit",Map.of("world","world","x",99,"y",64,"z",0));
            when(session.def()).thenReturn(codec.decodeDungeon("test",yaml));
            assertEquals(99,runtime.destination(session,new StartModesTest().player()).x());
            when(feet.isPassable()).thenReturn(false);assertFalse(DungeonSessionRuntime.safePrevious(point));
            clearInvocations(f.world);when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            assertFalse(DungeonSessionRuntime.safePrevious(point));verify(f.world,never()).getBlockAt(any(Location.class));
        }
    }
}
