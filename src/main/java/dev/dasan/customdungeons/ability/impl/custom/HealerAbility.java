package dev.dasan.customdungeons.ability.impl.custom;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
public final class HealerAbility implements Ability {
    public String id() { return "healer"; }
    public Material icon() { return Material.GLISTERING_MELON_SLICE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 8.0, 0, 32), new ParamSpec("heal", ParamType.DOUBLE, 4.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        var origin = ctx.caster().entity().getLocation();
        double radius = ctx.params().getDouble("radius");
        for (var mob : List.copyOf(ctx.session().mobs())) {
            var at = mob.entity().getLocation();
            if (CustomAbilitiesB.alive(mob) && at.getWorld().equals(origin.getWorld()) && at.distanceSquared(origin) <= radius * radius) {
                CustomAbilitiesB.heal(mob.entity(), ctx.params().getDouble("heal"));
                CustomAbilitiesB.line(ctx.session(), origin.clone().add(0, 1, 0), at.clone().add(0, 1, 0), Particle.HAPPY_VILLAGER);
            }
        }
    }
}
