package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.*;

public final class WitherShockwaveAbility implements Ability {
    public String id() { return "wither_shockwave"; }
    public Material icon() { return Material.WITHER_SKELETON_SKULL; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 5.0, 0.5, 32),
        new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000),
        new ParamSpec("knockback", ParamType.DOUBLE, 1.2, 0, 5)); }
    public void execute(AbilityContext ctx) {
        var at = ctx.caster().entity().getLocation();
        Effects.particles(ctx.session(), at, Particle.EXPLOSION_EMITTER, 1, 0);
        for (var target : BorrowedAbilitiesA.targets(ctx, ctx.params().getDouble("radius"))) {
            Effects.damage(target, ctx.params().getDouble("damage"), ctx.caster());
            Effects.knockback(target, at, ctx.params().getDouble("knockback"), 0.3);
        }
    }
}
