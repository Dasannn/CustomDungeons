package dev.dasan.customdungeons.storage;

import java.util.UUID;
import java.util.Set;
import dev.dasan.customdungeons.model.Point;

public record ActiveSessionRecord(UUID sessionId, String dungeonId, Set<UUID> players, Point exit) {
    public ActiveSessionRecord { players = Set.copyOf(players); }
}
