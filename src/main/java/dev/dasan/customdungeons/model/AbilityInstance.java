package dev.dasan.customdungeons.model;

import java.util.Map;

public record AbilityInstance(String abilityId, Trigger trigger, double triggerValue, TargetMode target,
                              double range, int cooldownTicks, double chance, int telegraphTicks,
                              Map<String, Object> params) {
    public AbilityInstance {
        params = Map.copyOf(params);
    }
}
