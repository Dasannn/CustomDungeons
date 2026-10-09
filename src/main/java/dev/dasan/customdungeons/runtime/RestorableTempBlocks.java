package dev.dasan.customdungeons.runtime;

import org.bukkit.block.Block;

/** Optional per-block cancellation, including a pending write-ahead placement. */
public interface RestorableTempBlocks extends TempBlocks {
    void restore(Block block);
}
