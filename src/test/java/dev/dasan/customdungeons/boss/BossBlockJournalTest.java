package dev.dasan.customdungeons.boss;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossBlockJournalTest {
    @TempDir Path temp;
    MockedStatic<Bukkit> api;
    Plugin plugin;World world;Block block;BlockData original,expected;
    @BeforeEach void setup() {
        api=mockStatic(Bukkit.class);plugin=mock(Plugin.class);when(plugin.getDataFolder()).thenReturn(temp.toFile());when(plugin.isEnabled()).thenReturn(true);
        world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        block=mock(Block.class);when(block.getWorld()).thenReturn(world);when(block.getX()).thenReturn(32);when(block.getY()).thenReturn(64);when(block.getZ()).thenReturn(-16);
        when(world.getBlockAt(32,64,-16)).thenReturn(block);
        original=mock(BlockData.class);expected=mock(BlockData.class);when(original.getAsString()).thenReturn("minecraft:air");when(expected.getAsString()).thenReturn("minecraft:stone");when(block.getBlockData()).thenReturn(expected);
        api.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(original);
        var scheduler=mock(BukkitScheduler.class);api.when(Bukkit::getScheduler).thenReturn(scheduler);
        doAnswer(call->{call.getArgument(1,Runnable.class).run();return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
    }
    @AfterEach void teardown(){api.close();}
    @Test void durableRecordsRestoreMatchingBlocksAndReleaseTheirReservation() throws Exception {
        try(var journal=new BossBlockJournal(plugin)) {
            journal.add(block,original,expected).join();assertTrue(journal.reserved(block));
            assertTrue(Files.readString(temp.resolve("world-boss-blocks.journal")).contains("minecraft:air\tminecraft:stone"));
            journal.recoverLoadedChunk(world,2,-1);verify(block).setBlockData(original,false);assertFalse(journal.reserved(block));
        }
        assertEquals("",Files.readString(temp.resolve("world-boss-blocks.journal")));
    }
    @Test void restartRecoveryRequestsChunksAsynchronouslyAndPreservesForeignChanges() {
        try(var journal=new BossBlockJournal(plugin)){journal.add(block,original,expected).join();}
        var chunk=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(2,-1,true)).thenReturn(chunk);
        var changed=mock(BlockData.class);when(changed.getAsString()).thenReturn("minecraft:diamond_block");when(block.getBlockData()).thenReturn(changed);
        try(var recovered=new BossBlockJournal(plugin)) {
            recovered.recover(world);assertTrue(recovered.reserved(block));verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
            chunk.complete(mock(Chunk.class));assertFalse(recovered.reserved(block));verify(block,never()).setBlockData(any(),anyBoolean());
            verify(world,never()).getChunkAt(anyInt(),anyInt());
        }
    }
}
