package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.model.Point;
import java.util.UUID;

/** A durable exit generation; replacing even the same destination creates a new generation. */
public record PendingExitRecord(UUID id,Point exit) {}
