package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
public final class HookAbility implements Ability {
    public String id() { return "hook"; }
    public Material icon() { return Material.FISHING_ROD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("power", ParamType.DOUBLE, 1.2, 0, 3),
            new ParamSpec("up", ParamType.DOUBLE, 0.2, 0, 2)); }
    public void execute(AbilityContext ctx) {
        var at = ctx.caster().entity().getLocation();
        for (var target : BorrowedAbilitiesC.targets(ctx))
            Effects.knockback(target, at, -ctx.params().getDouble("power"), ctx.params().getDouble("up"));
    }
}
