package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
public final class WindChargeAbility implements Ability {
    public String id() { return "wind_charge"; }
    public Material icon() { return Material.WIND_CHARGE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("power", ParamType.DOUBLE, 1.5, 0, 5)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            var aim = BorrowedAbilitiesB.aim(ctx, target);
            if (aim.lengthSquared() == 0) continue;
            BorrowedAbilitiesB.launch(ctx, org.bukkit.entity.WindCharge.class, aim, (at, hit) -> {
                Effects.particles(ctx.session(), at, Particle.GUST, 12, 0.5);
                BorrowedAbilitiesB.radial(ctx, at, 4, 1, ctx.params().getDouble("power"));
            });
        }
    }
}
