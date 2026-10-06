package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;
public final class LightningAbility implements Ability {
    public String id() { return "lightning"; }
    public Material icon() { return Material.LIGHTNING_ROD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        for (var target : ctx.targets()) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            Effects.damage(target, ctx.params().getDouble("damage"), ctx.caster());
        }
    }
}
