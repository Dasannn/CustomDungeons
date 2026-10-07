package dev.dasan.customdungeons.model;

import java.util.List;
import java.util.Map;

/** Optional overrides: absence inherits vanilla (or the current value in a phase); zero is explicit. */
public record MobAttributes(Map<String, Double> values) {
    public static final List<String> KEYS = List.of("max-health", "damage", "speed", "knockback-resistance", "scale",
            "armor", "armor-toughness", "follow-range", "attack-knockback", "jump-strength", "gravity",
            "step-height", "explosion-knockback-resistance");
    public static final MobAttributes EMPTY = new MobAttributes(Map.of());
    public MobAttributes {
        values = Map.copyOf(values);
        if (!KEYS.containsAll(values.keySet())) throw new IllegalArgumentException("Unknown mob attribute");
    }
}
