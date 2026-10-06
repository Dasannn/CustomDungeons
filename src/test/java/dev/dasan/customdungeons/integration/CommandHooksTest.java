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
        when(storage.loadTempBlocks()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(record,missing)));
        when(storage.loadActive()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of()));
        when(storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(world.getBlockAt(1,64,2)).thenReturn(block); when(world.getEntities()).thenReturn(List.of());
        try (var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> org.bukkit.Bukkit.getWorlds()).thenReturn(List.of(world));
            bukkit.when(() -> org.bukkit.Bukkit.getOnlinePlayers()).thenReturn(List.of());
            bukkit.when(() -> org.bukkit.Bukkit.createBlockData("minecraft:stone")).thenReturn(data);
            var recovery=new RecoveryService(plugin,storage,manager); recovery.recoverOnEnable();
            var ordered=inOrder(block,storage);
            ordered.verify(block).setBlockData(data,false);
            ordered.verify(storage).removeTempBlock("world",1,64,2);
            verify(storage,never()).removeTempBlock(eq("missing"),anyInt(),anyInt(),anyInt());
            var loaded=mock(org.bukkit.World.class); var missingBlock=mock(org.bukkit.block.Block.class);
            when(loaded.getName()).thenReturn("missing"); when(loaded.getBlockAt(3,64,4)).thenReturn(missingBlock);
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("missing")).thenReturn(loaded);
            bukkit.when(() -> org.bukkit.Bukkit.createBlockData("minecraft:air")).thenReturn(data);
            recovery.worldLoaded(new org.bukkit.event.world.WorldLoadEvent(loaded));
            verify(missingBlock).setBlockData(data,false); verify(storage).removeTempBlock("missing",3,64,4);
        }
    }
}
