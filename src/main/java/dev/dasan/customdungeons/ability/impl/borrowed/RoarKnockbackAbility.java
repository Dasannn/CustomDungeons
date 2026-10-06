package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;

public final class RoarKnockbackAbility implements Ability {
    public String id() { return "roar_knockback"; }
    public Material icon() { return Material.GOAT_HORN; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("knockback", ParamType.DOUBLE, 1.5, 0, 5),
            new ParamSpec("up", ParamType.DOUBLE, 0.4, 0, 3)); }
    public void execute(AbilityContext ctx) {
        var at = ctx.caster().entity().getLocation();
        Effects.sound(ctx.session(), at, Sound.ENTITY_RAVAGER_ROAR, 1, 1);
        for (var target : BorrowedAbilitiesC.targets(ctx))
            Effects.knockback(target, at, ctx.params().getDouble("knockback"), ctx.params().getDouble("up"));
    }
}
