package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Particle;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jspecify.annotations.Nullable;

/** Main-thread engine driven exclusively by its owning session ticker. */
public final class AbilityEngine {
    private final AbilityRegistry registry;
    private final Random random;
    private final ComboRunner combos;
    // Weak keys allow ended sessions to be collected without a global cleanup task.
    private final Map<ActiveMob, State> states = new WeakHashMap<>();
    private static final class State {
        final Set<String> once = new HashSet<>(), pending = new HashSet<>();
        final Map<String, Long> cadence = new HashMap<>();
    }
    public AbilityEngine(AbilityRegistry registry, PluginConfig config) {
        this(registry, config, new Random());
    }
    public AbilityEngine(AbilityRegistry registry, PluginConfig config, Random random) {
        this.registry = Objects.requireNonNull(registry);
        this.random = Objects.requireNonNull(random);
        combos = new ComboRunner(registry);
        if (config != null) Effects.configure(config.limits());
    }
    public void tick(Collection<ActiveMob> mobs, long tick) {
        for (ActiveMob mob : List.copyOf(mobs)) {
            if (!alive(mob)) { states.remove(mob); continue; }
            fire(Trigger.EVERY_X_SECONDS, mob, null, tick);
            fire(Trigger.PLAYER_IN_RANGE, mob, null, tick);
            fire(Trigger.HEALTH_BELOW, mob, null, tick);
        }
    }
    public void fire(Trigger trigger, ActiveMob mob, @Nullable Event cause, long tick) {
        if (trigger != Trigger.ON_DEATH && !alive(mob)) return;
        State state = states.computeIfAbsent(mob, k -> new State());
        var abilities = List.copyOf(mob.abilities());
        for (int i = 0; i < abilities.size(); i++) {
            AbilityInstance instance = abilities.get(i);
            String key = instance.abilityId() + "#" + i;
            if (instance.trigger() != trigger || state.pending.contains(key) || !mob.ready(key, tick)) continue;
            Ability ability = registry.get(instance.abilityId()).orElse(null);
            if (ability == null || !matches(trigger, instance.triggerValue(), mob, cause, tick, key, state)) continue;
            if (!(random.nextDouble() < Math.clamp(instance.chance(), 0, 1))) continue;
            var targets = TargetSelector.select(mob, instance.target(), instance.range(), random);
            if (targets.isEmpty() && trigger != Trigger.ON_SPAWN && trigger != Trigger.ON_DEATH) continue;
            if (oneShot(trigger)) state.once.add(key);
            state.pending.add(key);
            mob.cooldown(key, tick + Math.max(0, instance.cooldownTicks()));
            Runnable execute = () -> {
                try {
                    if (trigger != Trigger.ON_DEATH && !alive(mob)) return;
                    var valid = targets.stream().filter(t -> TargetSelector.eligible(mob, t, instance.range())).toList();
                    if (valid.isEmpty() && trigger != Trigger.ON_SPAWN && trigger != Trigger.ON_DEATH) return;
                    ability.execute(new AbilityContext(mob, valid,
                            new ParamValues(instance.params(), ability.params()), mob.session(), cause));
                } finally { state.pending.remove(key); }
            };
            // Death abilities must run synchronously: a dead caster cannot complete a warning.
            if (instance.telegraphTicks() > 0 && trigger != Trigger.ON_DEATH) {
                Telegraph.show(mob.session(), mob.entity().getLocation(), instance.range(),
                        instance.telegraphTicks(), Particle.CRIT);
                mob.session().scheduler().runLater(instance.telegraphTicks(), execute);
            } else execute.run();
        }
        var definitions = List.copyOf(mob.combos());
        for (int i = 0; i < definitions.size(); i++) {
            ComboDef combo = definitions.get(i);
            String key = "combo:" + combo.id() + "#" + i;
            if (combo.trigger() != trigger || !mob.ready(key, tick) || combos.running(mob, key)) continue;
            if (!matches(trigger, combo.triggerValue(), mob, cause, tick, key, state)) continue;
            var targets = TargetSelector.select(mob, combo.target(), combo.range(), random);
            if (targets.isEmpty() && trigger != Trigger.ON_SPAWN && trigger != Trigger.ON_DEATH) continue;
            if (oneShot(trigger)) state.once.add(key);
            combos.start(combo, mob, targets, cause, key);
        }
    }
    static boolean alive(ActiveMob mob) { return mob.entity().isValid() && !mob.entity().isDead(); }
    private static boolean oneShot(Trigger t) {
        return t == Trigger.HEALTH_BELOW || t == Trigger.ON_SPAWN || t == Trigger.ON_DEATH;
    }
    @SuppressWarnings("deprecation")
    private boolean matches(Trigger trigger, double value, ActiveMob mob, Event cause,
                            long tick, String key, State state) {
        if (oneShot(trigger) && state.once.contains(key)) return false;
        if (trigger == Trigger.HEALTH_BELOW) {
            double health = mob.entity().getHealth();
            if (cause instanceof EntityDamageEvent damage && !damage.isCancelled()
                    && damage.getEntity().equals(mob.entity())) health -= damage.getFinalDamage();
            return mob.entity().getMaxHealth() > 0 && health / mob.entity().getMaxHealth() * 100 <= value;
        }
        if (trigger == Trigger.EVERY_X_SECONDS || trigger == Trigger.PLAYER_IN_RANGE) {
            long period = trigger == Trigger.PLAYER_IN_RANGE ? 10 : Math.max(1, Math.round(value * 20));
            long offset = Math.floorMod(mob.entity().getUniqueId().hashCode(), period);
            if (Math.floorMod(tick, period) != offset || Objects.equals(state.cadence.get(key), tick)) return false;
            state.cadence.put(key, tick);
        }
        return true;
    }
}
