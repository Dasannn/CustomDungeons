package dev.dasan.customdungeons.model;

import java.util.Map;

public record ComboStep(String abilityId, Map<String, Object> params, int delayTicks) {
    public ComboStep {
        params = Map.copyOf(params);
    }
}
