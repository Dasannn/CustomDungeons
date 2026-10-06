package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.*;

public final class DarknessPulseAbility implements Ability {
    public String id() { return "darkness_pulse"; }
    public Material icon() { return Material.SCULK_SHRIEKER; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("radius", ParamType.DOUBLE, 8.0, 0.5, 32),
        new ParamSpec("seconds", ParamType.DOUBLE, 5.0, 0.05, 3600)); }
    public void execute(AbilityContext ctx) {
        Effects.sound(ctx.session(), ctx.caster().entity().getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1, 1);
        for (var target : BorrowedAbilitiesA.targets(ctx, ctx.params().getDouble("radius")))
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS,
                    (int) Math.round(ctx.params().getDouble("seconds") * 20), 0));
    }
}
