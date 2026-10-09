package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobsPlatform;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.model.ComboDef;
import dev.dasan.customdungeons.model.Trigger;
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
    public AbilityEngine(AbilityRegistry registry, MobsPlatform platform) {
        this(registry, platform, new Random());
    }
    public AbilityEngine(AbilityRegistry registry, MobsPlatform platform, Random random) {
        this.registry = Objects.requireNonNull(registry);
        this.random = Objects.requireNonNull(random);
        combos = new ComboRunner(registry);
        if (platform != null) Effects.configure(platform::limits);
    }
    public void tick(Collection<ActiveMob> mobs, long tick) {
        for (ActiveMob mob : List.copyOf(mobs)) {
            if (!alive(mob)) { states.remove(mob); continue; }
            dev.dasan.customdungeons.intelligence.IntelligenceService.tick(mob,this,tick);
            fire(Trigger.EVERY_X_SECONDS, mob, null, tick);
            fire(Trigger.PLAYER_IN_RANGE, mob, null, tick);
            fire(Trigger.HEALTH_BELOW, mob, null, tick);
        }
    }
    public void fire(Trigger trigger, ActiveMob mob, @Nullable Event cause, long tick) {
        if (trigger != Trigger.ON_DEATH && !alive(mob)) return;
        if (trigger.ordinal() > Trigger.PLAYER_IN_RANGE.ordinal()) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(mob);
            if(brain==null||brain.definition().level()<(trigger==Trigger.SURROUNDED?1:2))return;
        }
        State state = states.computeIfAbsent(mob, k -> new State());
        var abilities = List.copyOf(mob.abilities());
        for (int i = 0; i < abilities.size(); i++) {
            AbilityInstance instance = abilities.get(i);
            String key = instance.abilityId() + "#" + i;
            if (instance.trigger() != trigger || state.pending.contains(key) || !mob.ready(key, tick)) continue;
            Ability ability = registry.get(instance.abilityId()).orElse(null);
            if (ability == null || !matches(trigger, instance.triggerValue(), mob, cause, tick, key, state, instance.range())) continue;
            if (!(random.nextDouble() < Math.clamp(instance.chance(), 0, 1))) continue;
            var targets = TargetSelector.selectAbility(mob, instance.abilityId(), instance.target(), instance.range(), random);
            if (targets.isEmpty() && trigger != Trigger.ON_SPAWN && trigger != Trigger.ON_DEATH) continue;
            if (oneShot(trigger)) state.once.add(key);
            state.pending.add(key);
            mob.cooldown(key, tick + Math.max(0, instance.cooldownTicks()));
            Runnable execute = () -> {
                try {
                    if (trigger != Trigger.ON_DEATH && !alive(mob)) return;
                    var valid = targets.stream().filter(t -> TargetSelector.eligible(mob, t, instance.range())).toList();
                    if (valid.isEmpty() && trigger != Trigger.ON_SPAWN && trigger != Trigger.ON_DEATH) return;
                    var context=new AbilityContext(mob, valid,new ParamValues(instance.params(), ability.params()),mob.session(),cause);
                    if(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility control)control.execute(context,instance.telegraphTicks());
                    else ability.execute(context);
                } finally { state.pending.remove(key); }
            };
            // Death abilities must run synchronously: a dead caster cannot complete a warning.
            if (instance.telegraphTicks() > 0 && trigger != Trigger.ON_DEATH
                    && !(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility)) {
                Telegraph.show(mob, mob.entity().getLocation(), instance.range(),
                        instance.telegraphTicks(), Particle.CRIT, execute, () -> state.pending.remove(key));
            } else execute.run();
        }
        var definitions = List.copyOf(mob.combos());
        for (int i = 0; i < definitions.size(); i++) {
            ComboDef combo = definitions.get(i);
            String key = "combo:" + combo.id() + "#" + i;
            if (combo.trigger() != trigger || !mob.ready(key, tick) || combos.running(mob, key)) continue;
            if (!matches(trigger, combo.triggerValue(), mob, cause, tick, key, state, combo.range())) continue;
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
                            long tick, String key, State state,double range) {
        if (oneShot(trigger) && state.once.contains(key)) return false;
        if(trigger==Trigger.SURROUNDED)return TargetSelector.select(mob,dev.dasan.customdungeons.model.TargetMode.ALL_IN_RADIUS,range).size()>=Math.max(2,value);
        if(trigger==Trigger.PLAYER_NEAR_DEATH)return mob.session().players().stream().anyMatch(p->TargetSelector.eligible(mob,p,range)&&p.getHealth()<=Math.max(1,value==0?4:value));
        if(trigger==Trigger.DAMAGE_BURST) {
            var brain=dev.dasan.customdungeons.intelligence.IntelligenceService.brain(mob);if(brain==null)return false;
            int window=brain.definition().value("window")*20;
            double damage=brain.memory().players().stream().flatMap(p->brain.memory().observations(p,tick).stream()).filter(o->tick-o.tick()<=window).mapToDouble(dev.dasan.customdungeons.intelligence.EncounterMemory.Observation::damage).sum();
            return damage>=Math.max(1,value==0?dev.dasan.customdungeons.mob.MobHealth.maximum(mob.entity())*.1:value);
        }
        if (trigger == Trigger.HEALTH_BELOW) {
            double fraction=dev.dasan.customdungeons.mob.MobHealth.fraction(mob.entity());
            if (cause instanceof EntityDamageEvent damage && !damage.isCancelled()
                    && damage.getEntity().equals(mob.entity()))
                fraction=dev.dasan.customdungeons.mob.MobHealth.fractionAfterDamage(mob.entity(),
                        damage);
            return fraction*100<=value;
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
