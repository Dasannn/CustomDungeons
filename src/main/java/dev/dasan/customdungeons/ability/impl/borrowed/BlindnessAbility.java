package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.potion.*;
public final class BlindnessAbility implements Ability {
    public String id() { return "blindness"; }
    public Material icon() { return Material.INK_SAC; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("seconds", ParamType.DOUBLE, 5.0, 0.05, 60)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx))
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    (int) Math.round(ctx.params().getDouble("seconds") * 20), 0));
    }
}
