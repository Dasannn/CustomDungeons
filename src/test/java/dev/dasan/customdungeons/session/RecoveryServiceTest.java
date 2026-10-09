package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryServiceTest {
    @Test void recoveryRestoresOnlyAirAndPurgesBothCasesIdempotently() {
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        var storage=mock(Storage.class); var manager=mock(SessionManager.class);
        var world=mock(World.class); var empty=mock(Block.class); var changed=mock(Block.class);
        when(empty.getType()).thenReturn(Material.AIR); when(changed.getType()).thenReturn(Material.DIAMOND_BLOCK);
        var air=DoorServiceTest.data("minecraft:air");var diamond=DoorServiceTest.data("minecraft:diamond_block");
        when(empty.getBlockData()).thenReturn(air);when(changed.getBlockData()).thenReturn(diamond);
        when(world.getBlockAt(1,64,0)).thenReturn(empty); when(world.getBlockAt(2,64,0)).thenReturn(changed);
        var records=List.of(new TempBlockRecord("world",1,64,0,"minecraft:iron_bars"),new TempBlockRecord("world",2,64,0,"minecraft:iron_bars"));
        when(storage.abortUnfinishedRuns(any())).thenReturn(CompletableFuture.completedFuture(0));
        when(storage.loadTempBlocks()).thenReturn(CompletableFuture.completedFuture(records));
        when(storage.loadActive()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        var original=mock(org.bukkit.block.data.BlockData.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(()->Bukkit.createBlockData("minecraft:iron_bars")).thenReturn(original);
            var recovery=new RecoveryService(plugin,storage,manager);
            recovery.recoverOnEnable();
            verify(empty).setBlockData(original,false); verify(changed,never()).setBlockData(any(),anyBoolean());
            verify(storage).removeTempBlock("world",1,64,0); verify(storage).removeTempBlock("world",2,64,0);
            // A crash after restoring but before purging must not overwrite the already restored block.
            var restored=DoorServiceTest.data("minecraft:iron_bars");
            when(empty.getType()).thenReturn(Material.IRON_BARS);when(empty.getBlockData()).thenReturn(restored);
            recovery.recoverOnEnable(); verify(empty,times(1)).setBlockData(original,false);
        }
    }
    @Test void resetKeepsJournalUntilNextStartup() {
        var fixture=new DoorServiceTest();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(fixture.world);
            bukkit.when(()->Bukkit.createBlockData(Material.AIR)).thenReturn(fixture.air);
            fixture.doors.open(0); fixture.temp.tick(1); fixture.temp.restoreAll();
            verify(fixture.storage,never()).removeTempBlock(anyString(),anyInt(),anyInt(),anyInt());
        }
    }
}
