package dev.dasan.customdungeons.ability.impl.custom;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
public final class VampirismAbility implements Ability {
    public String id() { return "vampirism"; }
    public Material icon() { return Material.REDSTONE; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("percent", ParamType.DOUBLE, 25.0, 0, 100)); }
    public void execute(AbilityContext ctx) {
        if (CustomAbilitiesB.hitPlayer(ctx) != null) {
            var event = (EntityDamageByEntityEvent) ctx.cause();
            CustomAbilitiesB.heal(ctx.caster().entity(), event.getFinalDamage() * ctx.params().getDouble("percent") / 100);
        }
    }
}
