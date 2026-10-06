package dev.dasan.customdungeons.runtime;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
public interface TempBlocks {
    /** Places only in air. Restores to air after ttlTicks or in restoreAll(). */
    boolean place(Block block, BlockData data, int ttlTicks);
    void restoreAll();
}
