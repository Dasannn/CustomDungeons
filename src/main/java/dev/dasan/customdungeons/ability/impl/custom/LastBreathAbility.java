package dev.dasan.customdungeons.ability.impl.custom;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDeathEvent;
public final class LastBreathAbility implements Ability {
    public String id() { return "last_breath"; }
    public Material icon() { return Material.TNT; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("mode", ParamType.STRING, "EXPLOSION", 0, 0),
        new ParamSpec("template", ParamType.MOB_TEMPLATE, "", 0, 0), new ParamSpec("radius", ParamType.DOUBLE, 5.0, 0, 16),
        new ParamSpec("damage", ParamType.DOUBLE, 10.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        if (!(ctx.cause() instanceof EntityDeathEvent event) || !event.getEntity().equals(ctx.caster().entity())) return;
        String mode = ctx.params().getString("mode");
        if (mode.equalsIgnoreCase("SUMMON")) {
            String template = ctx.params().getString("template");
            if (!template.isBlank()) ctx.session().spawnMinion(template, ctx.caster().entity().getLocation(), ctx.caster());
        } else if (mode.equalsIgnoreCase("EXPLOSION"))
            CustomAbilitiesB.blast(ctx, ctx.caster().entity().getLocation(), ctx.params().getDouble("radius"), ctx.params().getDouble("damage"), 0);
    }
}
