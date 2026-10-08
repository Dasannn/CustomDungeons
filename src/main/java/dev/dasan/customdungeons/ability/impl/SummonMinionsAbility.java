package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
public final class SummonMinionsAbility implements Ability {
    public String id() { return "summon_minions"; }
    public Material icon() { return Material.ZOMBIE_SPAWN_EGG; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("template", ParamType.MOB_TEMPLATE, "", 0, 0),
        new ParamSpec("count", ParamType.INT, 2, 1, 50),
        new ParamSpec("radius", ParamType.DOUBLE, 3.0, 0, 32)); }
    public void execute(AbilityContext ctx) {
        String template = ctx.params().getString("template");
        if (template.isBlank()) return;
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < ctx.params().getInt("count"); i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = Math.sqrt(random.nextDouble()) * ctx.params().getDouble("radius");
            Location at = ctx.caster().entity().getLocation().clone()
                    .add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            var region = ctx.session().area();
            if (region != null && !region.contains(at)) continue;
            ctx.session().spawnMinion(template, at, ctx.caster());
        }
    }
}
