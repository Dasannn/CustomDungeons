package dev.dasan.customdungeons.ability.impl.custom;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.potion.*;
public final class EnrageAbility implements Ability {
    public String id() { return "enrage"; }
    public Material icon() { return Material.BLAZE_POWDER; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("strengthAmplifier", ParamType.INT, 1, 0, 10), new ParamSpec("speedAmplifier", ParamType.INT, 1, 0, 10)); }
    public void execute(AbilityContext ctx) {
        for (var entry : List.of(PotionEffectType.STRENGTH, PotionEffectType.SPEED))
            ctx.caster().entity().addPotionEffect(new PotionEffect(entry, PotionEffect.INFINITE_DURATION,
                ctx.params().getInt(entry.equals(PotionEffectType.STRENGTH) ? "strengthAmplifier" : "speedAmplifier")));
        if (!ctx.caster().ready("t13:enrage-aura", ctx.session().scheduler().currentTick())) return;
        ctx.caster().cooldown("t13:enrage-aura", Long.MAX_VALUE);
        aura(ctx);
    }
    private void aura(AbilityContext ctx) {
        if (!CustomAbilitiesB.alive(ctx.caster())) return;
        Effects.particles(ctx.session(), ctx.caster().entity().getLocation().add(0, 1, 0), Particle.ANGRY_VILLAGER, 6, 0.5);
        ctx.session().scheduler().runLater(10, () -> aura(ctx));
    }
}
