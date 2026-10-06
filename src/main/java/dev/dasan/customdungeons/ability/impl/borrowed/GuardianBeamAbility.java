package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
public final class GuardianBeamAbility implements Ability {
    public String id() { return "guardian_beam"; }
    public Material icon() { return Material.PRISMARINE_CRYSTALS; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("chargeTicks", ParamType.TICKS, 40, 1, 1200),
            new ParamSpec("damage", ParamType.DOUBLE, 6.0, 0, 1000)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx)) charge(ctx, target, ctx.params().getInt("chargeTicks"));
    }
    private void charge(AbilityContext ctx, LivingEntity target, int remaining) {
        if (!BorrowedAbilitiesB.alive(ctx) || !BorrowedAbilitiesB.allowed(ctx, target)) return;
        if (remaining == 0) {
            if (ctx.caster().entity().hasLineOfSight(target)) Effects.damage(target, ctx.params().getDouble("damage"), ctx.caster());
            return;
        }
        var from = ctx.caster().entity().getEyeLocation();
        var delta = target.getEyeLocation().toVector().subtract(from.toVector());
        int points = Math.min(64, Math.max(1, (int) Math.ceil(delta.length() * 2)));
        for (int i = 0; i <= points; i++) Effects.particles(ctx.session(), from.clone().add(delta.clone().multiply((double) i / points)), Particle.BUBBLE, 1, 0);
        ctx.session().scheduler().runLater(1, () -> charge(ctx, target, remaining - 1));
    }
}
