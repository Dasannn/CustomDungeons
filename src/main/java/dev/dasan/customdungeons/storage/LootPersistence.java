package dev.dasan.customdungeons.storage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.dasan.customdungeons.model.Point;

public interface LootPersistence {
    CompletableFuture<List<LootBlockRecord>> loadLootBlocks();
    CompletableFuture<Void> saveLootBlock(LootBlockRecord record);
    CompletableFuture<Void> deleteLootBlock(Point position);
}
