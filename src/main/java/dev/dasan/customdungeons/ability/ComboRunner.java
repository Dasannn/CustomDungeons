package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.model.ComboDef;
import dev.dasan.customdungeons.model.ComboStep;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

/** Sequential steps use only the session scheduler. Cooldown begins at completion. */
public final class ComboRunner {
    private final AbilityRegistry registry;
    private final Map<ActiveMob, Set<String>> running = new WeakHashMap<>();
    public ComboRunner(AbilityRegistry registry) { this.registry = Objects.requireNonNull(registry); }
    public boolean running(ActiveMob mob, String key) {
        return running.getOrDefault(mob, Set.of()).contains(key);
    }
    public void start(ComboDef combo, ActiveMob mob, List<LivingEntity> targets, @Nullable Event cause, String key) {
        if (running(mob, key)) return;
        if (combo.steps().size() < 2 || combo.steps().size() > 5) throw new IllegalArgumentException("Combo needs 2-5 steps");
        running.computeIfAbsent(mob, m -> new HashSet<>()).add(key);
        step(combo, mob, List.copyOf(targets), cause, key, 0);
    }
    private void finish(ActiveMob mob, String key) {
        var keys = running.get(mob);
        if (keys != null) { keys.remove(key); if (keys.isEmpty()) running.remove(mob); }
    }
    private void step(ComboDef combo, ActiveMob mob, List<LivingEntity> targets, Event cause, String key, int index) {
        ComboStep step = combo.steps().get(index);
        Runnable action = () -> {
            if (!AbilityEngine.alive(mob)) { finish(mob, key); return; }
            try {
                registry.get(step.abilityId()).ifPresent(a -> a.execute(new AbilityContext(mob,
                        targets.stream().filter(t -> TargetSelector.eligible(mob, t, combo.range())).toList(),
                        new ParamValues(step.params(), a.params()), mob.session(), cause)));
                if (index + 1 < combo.steps().size()) step(combo, mob, targets, cause, key, index + 1);
                else {
                    mob.cooldown(key, mob.session().scheduler().currentTick() + Math.max(0, combo.cooldownTicks()));
                    finish(mob, key);
                }
            } catch (RuntimeException ex) { finish(mob, key); throw ex; }
        };
        if (step.delayTicks() > 0) mob.session().scheduler().runLater(step.delayTicks(), action);
        else action.run();
    }
}
