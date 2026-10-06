package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;

public final class EndermanBlinkAbility implements Ability {
    public String id() { return "ender_blink"; }
    public Material icon() { return Material.ENDER_PEARL; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("distance", ParamType.DOUBLE, 2.0, 1, 4)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var direction = target.getLocation().getDirection().setY(0);
            if (direction.lengthSquared() < 1e-8) direction = new org.bukkit.util.Vector(0, 0, 1);
            var behind = target.getLocation().clone().subtract(direction.normalize().multiply(ctx.params().getDouble("distance")));
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                var at = behind.clone().add(0, dy, 0);
                at.setX(at.getBlockX() + 0.5);
                at.setY(at.getBlockY());
                at.setZ(at.getBlockZ() + 0.5);
                if (!BorrowedAbilitiesC.safe(ctx, at)) continue;
                at.setDirection(target.getLocation().toVector().subtract(at.toVector()));
                var from = ctx.caster().entity().getLocation();
                if (ctx.caster().entity().teleport(at)) {
                    Effects.particles(ctx.session(), from, Particle.PORTAL, 24, 0.5);
                    Effects.particles(ctx.session(), at, Particle.PORTAL, 24, 0.5);
                }
                return;
            }
        }
    }
}
