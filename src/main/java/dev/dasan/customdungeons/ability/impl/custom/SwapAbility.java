package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
public final class SwapAbility implements Ability {
    public String id() { return "swap"; }
    public Material icon() { return Material.CHORUS_FRUIT; }
    public List<ParamSpec> params() { return List.of(); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var from = ctx.caster().entity().getLocation().clone();
            var to = target.getLocation().clone();
            if (!BorrowedAbilitiesC.safe(ctx, from) || !BorrowedAbilitiesC.safe(ctx, to)) continue;
            if (!ctx.caster().entity().teleport(to)) return;
            if (!target.teleport(from)) ctx.caster().entity().teleport(from);
            return; // An exchange has exactly one counterpart, even with ALL_IN_RADIUS.
        }
    }
}
