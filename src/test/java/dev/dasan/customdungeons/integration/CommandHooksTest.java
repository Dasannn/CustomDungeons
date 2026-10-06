package dev.dasan.customdungeons.integration;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.session.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandHooksTest {
    @Test void hookRenderPlaceholders() {
        assertEquals("portal catacumba 3 6",CommandHooks.render("portal {dungeon} {players} {max}","catacumba",3,6));
    }
    @Test void unlimitedRendersInfinity() {
        assertEquals("∞",CommandHooks.render("{max}","id",1,0));
    }
    @Test void transitionsDispatchCorrespondingHooks() {
        var commands = new ArrayList<String>(); var hooks = new CommandHooks(commands::add);
        var s = mock(DungeonSession.class); var def = mock(DungeonDef.class);
        when(s.def()).thenReturn(def); when(def.id()).thenReturn("id");
        when(s.survivors()).thenReturn(Set.of(UUID.randomUUID()));
        var map = new EnumMap<HookEvent,List<String>>(HookEvent.class);
        for (var e : HookEvent.values()) map.put(e,List.of(e.name()+" {players}"));
        when(def.hooks()).thenReturn(map);
        hooks.onStateChange(s,SessionState.FREE,SessionState.LOBBY); hooks.onLobbyFull(s);
        hooks.onStateChange(s,SessionState.LOBBY,SessionState.RUNNING);
        hooks.onStateChange(s,SessionState.RUNNING,SessionState.COMPLETED);
        hooks.onStateChange(s,SessionState.COMPLETED,SessionState.RESETTING);
        when(s.survivors()).thenReturn(Set.of());
        hooks.onStateChange(s,SessionState.RESETTING,SessionState.FREE);
        hooks.onStateChange(s,SessionState.RUNNING,SessionState.FAILED);
        assertEquals(List.of("LOBBY_OPEN 1","FULL 1","START 1","COMPLETE 1","FREE 0","FAIL 0"),commands);
    }
    @Test void recoveryRestoresJournalBeforeRemovingItsRecord() {
        var plugin=mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var server=mock(org.bukkit.Server.class); var plugins=mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var storage=mock(dev.dasan.customdungeons.storage.Storage.class);
        var manager=mock(SessionManager.class); var world=mock(org.bukkit.World.class);
        var block=mock(org.bukkit.block.Block.class); var data=mock(org.bukkit.block.data.BlockData.class);
        var record=new dev.dasan.customdungeons.storage.TempBlockRecord("world",1,64,2,"minecraft:stone");
        var missing=new dev.dasan.customdungeons.storage.TempBlockRecord("missing",3,64,4,"minecraft:air");
        when(storage.abortUnfinishedRuns(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(2));
        when(storage.loadTempBlocks()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(record,missing)));
        var player=UUID.randomUUID(); var session=UUID.randomUUID();
        var exit=new Point("world",0,64,0,0,0);
        when(storage.loadActive()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(
                new dev.dasan.customdungeons.storage.ActiveSessionRecord(session,"dungeon",Set.of(player),exit))));
        when(storage.addPendingExit(any(),any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(storage.clearActive(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(block.getType()).thenReturn(org.bukkit.Material.AIR);
        when(world.getBlockAt(1,64,2)).thenReturn(block); when(world.getEntities()).thenReturn(List.of());
        try (var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> org.bukkit.Bukkit.getWorlds()).thenReturn(List.of(world));
            bukkit.when(() -> org.bukkit.Bukkit.getOnlinePlayers()).thenReturn(List.of());
            bukkit.when(() -> org.bukkit.Bukkit.createBlockData("minecraft:stone")).thenReturn(data);
            var recovery=new RecoveryService(plugin,storage,manager); recovery.recoverOnEnable();
            var ordered=inOrder(block,storage);
            ordered.verify(storage).abortUnfinishedRuns(any(java.time.Instant.class));
            ordered.verify(storage).loadTempBlocks();
            ordered.verify(block).setBlockData(data,false);
            ordered.verify(storage).removeTempBlock("world",1,64,2);
            ordered.verify(storage).loadActive();
            ordered.verify(storage).addPendingExit(player,exit);
            ordered.verify(storage).clearActive(session);
            verify(storage,never()).removeTempBlock(eq("missing"),anyInt(),anyInt(),anyInt());
            var loaded=mock(org.bukkit.World.class); var missingBlock=mock(org.bukkit.block.Block.class);
            when(missingBlock.getType()).thenReturn(org.bukkit.Material.AIR);
            when(loaded.getName()).thenReturn("missing"); when(loaded.getBlockAt(3,64,4)).thenReturn(missingBlock);
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("missing")).thenReturn(loaded);
            bukkit.when(() -> org.bukkit.Bukkit.createBlockData("minecraft:air")).thenReturn(data);
            recovery.worldLoaded(new org.bukkit.event.world.WorldLoadEvent(loaded));
            verify(missingBlock).setBlockData(data,false); verify(storage).removeTempBlock("missing",3,64,4);
        }
    }
    @Test void failedAbortPreventsRecoveryFromClearingActiveSessions() {
        var plugin=mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var server=mock(org.bukkit.Server.class); var plugins=mock(org.bukkit.plugin.PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        var storage=mock(dev.dasan.customdungeons.storage.Storage.class);
        when(storage.abortUnfinishedRuns(any())).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        var recovery=new RecoveryService(plugin,storage,mock(SessionManager.class));
        assertThrows(java.util.concurrent.CompletionException.class,recovery::recoverOnEnable);
        verify(storage,never()).loadActive(); verify(storage,never()).clearActive(any());
    }
}
