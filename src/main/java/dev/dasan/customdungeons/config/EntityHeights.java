package dev.dasan.customdungeons.config;

import java.util.Map;
import org.bukkit.entity.EntityType;

/** Immutable configuration snapshot; absent types have no estimated height. */
public record EntityHeights(Map<EntityType,Double> values) {
    public EntityHeights { values = Map.copyOf(values); }
    public double scaledHeight(EntityType type,double scale) {return height(type)*NumericRanges.effectiveScale(scale);}
    public double height(EntityType type) { return values.getOrDefault(type,Double.NaN); }
}
