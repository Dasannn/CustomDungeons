package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;

public final class LaunchUpAbility implements Ability {
    public String id() { return "launch_up"; }
    public Material icon() { return Material.FEATHER; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("power", ParamType.DOUBLE, 1.2, 0, 3)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx))
            target.setVelocity(target.getVelocity().clone().setY(ctx.params().getDouble("power")));
    }
}
