package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;

public final class DoubleAbility implements Ability {
    // Weak entity-state keys avoid retaining finished sessions. Mark the owner before spawn:
    // spawnMinion may synchronously fire ON_SPAWN on the child before returning it.
    private final Set<ActiveMob> copies = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ActiveMob> spawning = Collections.newSetFromMap(new WeakHashMap<>());
    public String id() { return "double"; }
    public Material icon() { return Material.ZOMBIE_SPAWN_EGG; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("factor", ParamType.DOUBLE, 0.5, 0.05, 0.99)); }
    public void execute(AbilityContext ctx) {
        if (copies.contains(ctx.caster()) || !spawning.isEmpty()) return;
        spawning.add(ctx.caster());
        try {
            var child = ctx.session().spawnMinion(ctx.caster().template().id(), ctx.caster().entity().getLocation(), ctx.caster());
            if (child == null) return;
            copies.add(child);
            strip(child);
            double factor = ctx.params().getDouble("factor");
            var max = child.entity().getAttribute(Attribute.MAX_HEALTH);
            var ownerMax = ctx.caster().entity().getAttribute(Attribute.MAX_HEALTH);
            if (max != null && ownerMax != null) {
                double health = Math.max(0.01, dev.dasan.customdungeons.mob.MobHealth.maximum(ctx.caster().entity()) * factor);
                dev.dasan.customdungeons.mob.MobHealth.configure(child.entity(),health,false);
            }
            var scale = child.entity().getAttribute(Attribute.SCALE);
            var ownerScale = ctx.caster().entity().getAttribute(Attribute.SCALE);
            if (scale != null && ownerScale != null) scale.setBaseValue(ownerScale.getValue() * factor);
            guard(child);
        } finally { spawning.remove(ctx.caster()); }
    }
    private void strip(ActiveMob mob) {
        mob.abilities().removeIf(a -> a.abilityId().equals(id()));
        mob.combos().removeIf(c -> c.steps().stream().anyMatch(s -> s.abilityId().equals(id())));
    }
    private void guard(ActiveMob child) {
        if (!CustomAbilitiesB.alive(child)) return;
        strip(child); // Boss phases can restore abilities from the immutable source template.
        child.session().scheduler().runLater(1, () -> guard(child));
    }
}
