package dev.dasan.customdungeons.mob;

import java.nio.file.*;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveTestJournalOwnershipTest {
    @TempDir Path directory;
    @ParameterizedTest @CsvSource({"true,true","true,false","false,true","false,false"})
    void recoveryUsesExactPlacedDataAndDiscardsBothKindsOfRecord(boolean matching,boolean loaded) throws Exception {
        var world=mock(World.class);var uid=UUID.randomUUID();when(world.getUID()).thenReturn(uid);
        var block=mock(Block.class);when(world.getBlockAt(1,64,0)).thenReturn(block);
        var current=mock(BlockData.class);when(current.getAsString()).thenReturn(matching?"minecraft:stone_bricks":"minecraft:diamond_block");when(block.getBlockData()).thenReturn(current);
        var original=mock(BlockData.class);var file=directory.resolve("live.journal");
        Files.writeString(file,uid+";1;64;0\tminecraft:air\tminecraft:stone_bricks\n");
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.getWorld(uid)).thenReturn(loaded?world:null);
            api.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(original);
            var journal=new LiveTestService.Journal(file);
            try {
                if(!loaded)journal.worldLoaded(world);
                verify(block,matching?times(1):never()).setBlockData(original,false);
                if(!matching)verify(block,never()).setBlockData(any(),anyBoolean());
            } finally {journal.close();}
        }
        assertEquals("",Files.readString(file));
    }
    @ParameterizedTest @CsvSource({"true,minecraft:cobweb","false,minecraft:cobweb","true,minecraft:diamond_block","false,minecraft:diamond_block"})
    void legacyRecordsKeepTheOriginalMainRecoveryRule(boolean loaded,String present) throws Exception {
        var world=mock(World.class);var uid=UUID.randomUUID();when(world.getUID()).thenReturn(uid);
        var block=mock(Block.class);when(world.getBlockAt(1,64,0)).thenReturn(block);
        var current=mock(BlockData.class);when(current.getAsString()).thenReturn(present);when(block.getBlockData()).thenReturn(current);
        var original=mock(BlockData.class);var file=directory.resolve("legacy.journal");
        Files.writeString(file,uid+";1;64;0\tminecraft:air\n");
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.getWorld(uid)).thenReturn(loaded?world:null);
            api.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(original);
            var journal=new LiveTestService.Journal(file);
            try {
                if(!loaded){verify(block,never()).setBlockData(any(),anyBoolean());journal.worldLoaded(world);}
                verify(block).setBlockData(original,false);
            } finally {journal.close();}
        }
        assertEquals("",Files.readString(file));
    }

}
