package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.potion.*;
public final class ElderCurseAbility implements Ability {
    public String id() { return "elder_curse"; }
    public Material icon() { return Material.ELDER_GUARDIAN_SPAWN_EGG; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("seconds", ParamType.DOUBLE, 10.0, 0.05, 3600),
            new ParamSpec("amplifier", ParamType.INT, 2, 0, 255)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    (int) Math.round(ctx.params().getDouble("seconds") * 20), ctx.params().getInt("amplifier")));
            Effects.particles(ctx.session(), target.getEyeLocation(), Particle.ELDER_GUARDIAN, 1, 0);
        }
    }
}
