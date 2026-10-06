package dev.dasan.customdungeons.model;

import java.util.List;

public record ComboDef(String id, Trigger trigger, double triggerValue, TargetMode target, double range,
                       int cooldownTicks, List<ComboStep> steps) {
    public ComboDef {
        steps = List.copyOf(steps);
    }
}
