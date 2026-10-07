package dev.dasan.customdungeons.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Non-consuming T50 recovery; the T01 Storage contract remains unchanged. */
public interface PendingExitPersistence {
    CompletableFuture<Optional<PendingExitRecord>> pendingExit(UUID player);
    CompletableFuture<Void> clearPendingExit(UUID player,UUID generation);
}
