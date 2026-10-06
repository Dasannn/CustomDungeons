package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.model.*;
import java.util.UUID;
import java.util.function.Predicate;

/** Durable per-player return position and fallback, independent of the active-session journal. */
public record ReturnTarget(UUID sessionId, Point previous, Point exit, FinishDestination destination) {
    public Point resolve(Predicate<Point> safe) {
        return destination==FinishDestination.PREVIOUS && safe.test(previous)?previous:exit;
    }
}
