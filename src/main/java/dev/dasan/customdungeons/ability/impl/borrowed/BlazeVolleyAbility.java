package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

public final class BlazeVolleyAbility implements Ability {
    public String id() { return "blaze_volley"; }
    public Material icon() { return Material.BLAZE_ROD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("count", ParamType.INT, 3, 1, 16),
            new ParamSpec("spreadDeg", ParamType.DOUBLE, 20.0, 0, 180)); }
    /** Even yaw fan, with the declared spread spanning the two outer shots. */
    public static List<Vector> velocities(Vector aim, int count, double spreadDeg) {
        if (count <= 0 || aim.lengthSquared() == 0) return List.of();
        List<Vector> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = count == 1 ? 0 : Math.toRadians(spreadDeg * ((double) i / (count - 1) - 0.5));
            result.add(aim.clone().normalize().rotateAroundY(angle));
        }
        return List.copyOf(result);
    }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx))
            for (var velocity : velocities(BorrowedAbilitiesB.aim(ctx, target), ctx.params().getInt("count"), ctx.params().getDouble("spreadDeg"))) {
                var ball = BorrowedAbilitiesB.launch(ctx, SmallFireball.class, velocity, (context, at, hit) -> {
                    if (hit instanceof org.bukkit.entity.LivingEntity living) Effects.damage(living, 5, context.caster());
                });
                ball.setIsIncendiary(false);
            }
    }
}
