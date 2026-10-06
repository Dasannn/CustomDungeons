package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.model.Point;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.ItemStack;

public interface Storage extends AutoCloseable {
    CompletableFuture<Long> startRun(String dungeonId, Instant start, Collection<UUID> players);
    CompletableFuture<Void> finishRun(long runId, RunResult result, Instant end, List<RunPlayerRecord> players);
    CompletableFuture<Optional<Instant>> cooldownUntil(UUID player, String dungeonId);
    CompletableFuture<Void> setCooldown(UUID player, String dungeonId, Instant until);
    CompletableFuture<Void> addClaims(UUID player, List<ItemStack> items);
    CompletableFuture<List<ItemStack>> takeClaims(UUID player);
    CompletableFuture<PlayerStats> stats(UUID player);
    CompletableFuture<Void> markActive(ActiveSessionRecord r);
    CompletableFuture<Void> clearActive(UUID sessionId);
    CompletableFuture<List<ActiveSessionRecord>> loadActive();
    CompletableFuture<Void> addTempBlock(TempBlockRecord r);
    CompletableFuture<Void> removeTempBlock(String world, int x, int y, int z);
    CompletableFuture<List<TempBlockRecord>> loadTempBlocks();
    CompletableFuture<Void> addPendingExit(UUID player, Point exit);
    CompletableFuture<Optional<Point>> takePendingExit(UUID player);

    /** Drains accepted operations before closing the pool; call during shutdown, not gameplay. */
    @Override void close();
}
