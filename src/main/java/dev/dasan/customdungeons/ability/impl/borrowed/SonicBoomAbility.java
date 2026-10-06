package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.*;

public final class SonicBoomAbility implements Ability {
    public String id() { return "sonic_boom"; }
    public Material icon() { return Material.SCULK_CATALYST; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("damage", ParamType.DOUBLE, 10.0, 0, 1000),
        new ParamSpec("range", ParamType.DOUBLE, 16.0, 0.5, 64)); }
    public void execute(AbilityContext ctx) {
        var origin = ctx.caster().entity().getEyeLocation();
        Effects.sound(ctx.session(), origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        for (var target : BorrowedAbilitiesA.targets(ctx, ctx.params().getDouble("range"))) {
            var delta = target.getEyeLocation().toVector().subtract(origin.toVector());
            double length = delta.length();
            if (length > 0) {
                var step = delta.normalize();
                for (double d = 0; d <= length; d += 1)
                    Effects.particles(ctx.session(), origin.clone().add(step.clone().multiply(d)), Particle.SONIC_BOOM, 1, 0);
            }
            double damage = ctx.params().getDouble("damage");
            if (damage > 0) target.damage(damage, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.SONIC_BOOM)
                    .withCausingEntity(ctx.caster().entity()).withDirectEntity(ctx.caster().entity()).build());
        }
    }
}
