package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.model.*;
import java.util.UUID;

/** Snapshot of the rules at quit time, independent of later definition/session edits. */
public record DisconnectRecord(UUID id, UUID player, UUID sessionId, String dungeonId,
                               Point position, Point exit, DisconnectMode mode, boolean keepInventory) {}
