package dev.dasan.customdungeons.config;

import java.util.Map;
import org.bukkit.entity.EntityType;

/** Immutable configuration snapshot; absent types have no estimated height. */
public record EntityHeights(Map<EntityType,Double> values) {
    public EntityHeights { values = Map.copyOf(values); }
    public double height(EntityType type) { return values.getOrDefault(type,Double.NaN); }
}
