package dev.dasan.customdungeons.runtime;

import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockRestorationTest {
    static Stream<Arguments> records() {
        return Stream.of(false,true).flatMap(disk->Stream.of(
                Arguments.of(disk,"minecraft:stone_bricks","minecraft:air","minecraft:stone_bricks",true),
                Arguments.of(disk,"minecraft:air","minecraft:air","minecraft:stone_bricks",false),
                Arguments.of(disk,"minecraft:diamond_block","minecraft:air","minecraft:stone_bricks",false),
                Arguments.of(disk,"minecraft:air","minecraft:air","minecraft:air",false)));
    }
    @ParameterizedTest @MethodSource("records")
    void onlyAPlacedBlockThatDiffersFromItsOriginalNeedsRestoration(boolean disk,String present,String before,String placed,boolean changed) {
        var block=mock(Block.class);var current=mock(BlockData.class);var original=mock(BlockData.class);
        when(current.getAsString()).thenReturn(present);when(block.getBlockData()).thenReturn(current);when(original.getAsString()).thenReturn(before);
        try(var api=mockStatic(Bukkit.class)) {
            api.when(()->Bukkit.createBlockData(before)).thenReturn(original);
            assertEquals(changed,disk?BlockRestoration.restore(block,before,placed):BlockRestoration.restore(block,original,placed));
            verify(block,changed?times(1):never()).setBlockData(original,false);
            if(!changed)verify(block,never()).setBlockData(any(),anyBoolean());
        }
    }
}
