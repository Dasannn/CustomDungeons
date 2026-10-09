package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TempBlockOwnershipTest {
    static BlockData data(String value) {
        var data=mock(BlockData.class);when(data.getAsString()).thenReturn(value);when(data.clone()).thenReturn(data);return data;
    }
    static final class Fixture {
        final Storage storage=mock(Storage.class);
        final World world=mock(World.class);final Block block=mock(Block.class);
        final BlockData air=data("minecraft:air"),pillar=data("minecraft:stone_bricks"),foreign=data("minecraft:diamond_block");
        final AtomicReference<BlockData> state=new AtomicReference<>(air);
        final List<TempBlockRecord> records=new ArrayList<>();
        final SessionTempBlocks blocks=new SessionTempBlocks(storage,error->{throw new AssertionError(error);});
        Fixture() {
            when(world.getName()).thenReturn("world");when(world.getBlockAt(1,64,0)).thenReturn(block);
            when(block.getWorld()).thenReturn(world);when(block.getX()).thenReturn(1);when(block.getY()).thenReturn(64);
            when(block.isEmpty()).thenAnswer(i->state.get()==air);when(block.getBlockData()).thenAnswer(i->state.get());
            when(block.getType()).thenAnswer(i->state.get()==air?Material.AIR:state.get()==pillar?Material.STONE_BRICKS:Material.DIAMOND_BLOCK);
            doAnswer(i->{state.set(i.getArgument(0));return null;}).when(block).setBlockData(any(),eq(false));
            when(storage.addTempBlock(any())).thenAnswer(i->{records.add(i.getArgument(0));return CompletableFuture.completedFuture(null);});
            when(storage.markTempBlockRestored(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.abortUnfinishedRuns(any())).thenReturn(CompletableFuture.completedFuture(0));
            when(storage.loadTempBlocks()).thenAnswer(i->CompletableFuture.completedFuture(List.copyOf(records)));
            when(storage.loadActive()).thenReturn(CompletableFuture.completedFuture(List.of()));
            when(storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        }
        void place() {assertTrue(blocks.place(block,pillar,20));blocks.tick(1);assertSame(pillar,state.get());}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void crashRestoresTheRecordedPillarAndNeverAnUnrelatedReplacement(boolean matching) {
        var f=new Fixture();f.place();if(!matching)f.state.set(f.foreign);
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            api.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(f.air);
            new RecoveryService(mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS),f.storage,mock(SessionManager.class)).recoverOnEnable();
            assertSame(matching?f.air:f.foreign,f.state.get());
            verify(f.storage).removeTempBlock("world",1,64,0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"expire","individual","all"})
    void foreignBlockSurvivesEveryNormalRestorationPath(String reason) {
        var f=new Fixture();f.place();f.state.set(f.foreign);
        switch(reason){case "expire"->f.blocks.tick(21);case "individual"->f.blocks.restore(f.block);default->f.blocks.restoreAll();}
        assertSame(f.foreign,f.state.get());verify(f.storage).markTempBlockRestored("world",1,64,0);
    }
}
