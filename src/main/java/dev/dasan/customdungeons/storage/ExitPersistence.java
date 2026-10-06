package dev.dasan.customdungeons.storage;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Additive T38 persistence; the T01 Storage contract remains unchanged. */
public interface ExitPersistence {
    CompletableFuture<Void> saveReturnTarget(UUID player,ReturnTarget target);
    CompletableFuture<Optional<ReturnTarget>> returnTarget(UUID player);
    CompletableFuture<Void> clearReturnTarget(UUID player,UUID session);
}
