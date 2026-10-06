package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.Fireball;
public final class GhastFireballAbility implements Ability {
    public String id() { return "ghast_fireball"; }
    public Material icon() { return Material.FIRE_CHARGE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("yield", ParamType.DOUBLE, 2.0, 0.5, 16),
            new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            var aim = BorrowedAbilitiesB.aim(ctx, target);
            if (aim.lengthSquared() == 0) continue;
            var ball = BorrowedAbilitiesB.launch(ctx, Fireball.class, aim.multiply(0.8), (at, hit) -> {
                Effects.particles(ctx.session(), at, Particle.EXPLOSION_EMITTER, 1, 0);
                Effects.sound(ctx.session(), at, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
                BorrowedAbilitiesB.radial(ctx, at, ctx.params().getDouble("yield") * 2,
                        ctx.params().getDouble("damage"), 0);
            });
            ball.setIsIncendiary(false);
            // Yield is used by participant-only impact damage; never a native terrain explosion.
            ball.setYield(0);
        }
    }
}
