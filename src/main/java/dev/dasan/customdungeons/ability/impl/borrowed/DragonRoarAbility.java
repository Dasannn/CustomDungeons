package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.*;

public final class DragonRoarAbility implements Ability {
    public String id() { return "dragon_roar"; }
    public Material icon() { return Material.DRAGON_HEAD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 8.0, 0.5, 32),
        new ParamSpec("knockback", ParamType.DOUBLE, 1.5, 0, 5),
        new ParamSpec("up", ParamType.DOUBLE, 0.5, 0, 3)); }
    public void execute(AbilityContext ctx) {
        var at = ctx.caster().entity().getLocation();
        Effects.sound(ctx.session(), at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 1);
        for (var target : BorrowedAbilitiesA.targets(ctx, ctx.params().getDouble("radius")))
            Effects.knockback(target, at, ctx.params().getDouble("knockback"), ctx.params().getDouble("up"));
    }
}
