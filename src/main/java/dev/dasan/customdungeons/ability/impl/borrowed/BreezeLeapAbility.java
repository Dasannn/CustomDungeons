package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
public final class BreezeLeapAbility implements Ability {
    public String id() { return "breeze_leap"; }
    public Material icon() { return Material.BREEZE_ROD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("height", ParamType.DOUBLE, 0.8, 0, 3)); }
    public void execute(AbilityContext ctx) {
        var targets = BorrowedAbilitiesB.targets(ctx);
        if (targets.isEmpty()) return;
        var direction = targets.getFirst().getLocation().toVector().subtract(ctx.caster().entity().getLocation().toVector()).setY(0);
        if (direction.lengthSquared() > 0) direction.normalize().multiply(0.8);
        ctx.caster().entity().setVelocity(direction.setY(ctx.params().getDouble("height")));
        Effects.particles(ctx.session(), ctx.caster().entity().getLocation(), Particle.GUST, 12, 0.5);
    }
}
