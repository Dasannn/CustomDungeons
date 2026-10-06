package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
public final class FreezeAbility implements Ability {
    public String id() { return "freeze"; }
    public Material icon() { return Material.ICE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("ticks", ParamType.TICKS, 200, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx))
            target.setFreezeTicks(ctx.params().getInt("ticks"));
    }
}
