package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;

public final class CobwebAbility implements Ability {
    public String id() { return "cobweb"; }
    public Material icon() { return Material.COBWEB; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("ttl", ParamType.TICKS, 100, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var at = target.getLocation();
            if (!BorrowedAbilitiesC.inRoom(ctx, at)) continue;
            var block = at.getBlock();
            if (block.getType().isAir())
                ctx.session().tempBlocks().place(block, Material.COBWEB.createBlockData(), ctx.params().getInt("ttl"));
        }
    }
}
