package dev.dasan.customdungeons.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Additive T44 journal; the shared Storage contract remains unchanged. */
public interface DisconnectPersistence {
    CompletableFuture<Void> saveDisconnect(DisconnectRecord record);
    CompletableFuture<Optional<DisconnectRecord>> disconnect(UUID player);
    /** Compare the generation so a late cleanup cannot erase a subsequent quit. */
    CompletableFuture<Void> clearDisconnect(UUID player, UUID id);
}
