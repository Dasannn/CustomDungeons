package dev.dasan.customdungeons.runtime;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

/** All temporary-block owners and their journals use this exact ownership check. */
public final class BlockRestoration {
    private BlockRestoration() {}
    public static boolean restore(Block block,BlockData original,String placed) {
        return restore(block,original.getAsString(),placed,()->original);
    }
    public static boolean restore(Block block,String original,String placed) {
        return restore(block,original,placed,()->Bukkit.createBlockData(original));
    }
    private static boolean restore(Block block,String before,String placed,Supplier<BlockData> original) {
        String current=block.getBlockData().getAsString();
        // A write-ahead record may describe a mutation that never reached the world.
        if(current.equals(before)||placed==null||!placed.equals(current)||block.getState() instanceof TileState)return false;
        block.setBlockData(original.get(),false);
        return true;
    }
}
