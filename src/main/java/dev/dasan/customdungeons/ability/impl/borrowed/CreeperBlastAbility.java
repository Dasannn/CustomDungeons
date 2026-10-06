package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
public final class CreeperBlastAbility implements Ability {
    public String id() { return "creeper_blast"; }
    public Material icon() { return Material.TNT; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 4.0, 0.5, 16),
            new ParamSpec("damage", ParamType.DOUBLE, 10.0, 0, 1000),
            new ParamSpec("fuseTicks", ParamType.TICKS, 30, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        var at = ctx.caster().entity().getLocation().clone();
        double radius = ctx.params().getDouble("radius");
        Telegraph.show(ctx.caster(), at, radius, ctx.params().getInt("fuseTicks"), Particle.SMOKE, () -> {
            if (!BorrowedAbilitiesB.alive(ctx)) return;
            BorrowedAbilitiesB.explosion(ctx, at, (float) (radius / 2));
            BorrowedAbilitiesB.radial(ctx, at, radius, ctx.params().getDouble("damage"), 0);
        }, () -> {});
    }
}
