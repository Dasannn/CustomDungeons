package dev.dasan.customdungeons.ability.impl;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.potion.*;
public final class OnHitEffectAbility implements Ability {
    public String id() { return "on_hit_effect"; }
    public Material icon() { return Material.POTION; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("effect", ParamType.POTION_EFFECT, "minecraft:poison", 0, 0),
        new ParamSpec("amplifier", ParamType.INT, 0, 0, 255),
        new ParamSpec("seconds", ParamType.DOUBLE, 5.0, 0.05, 3600)); }
    public void execute(AbilityContext ctx) {
        NamespacedKey key = NamespacedKey.fromString(ctx.params().getString("effect"));
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        if (type == null) return;
        PotionEffect effect = new PotionEffect(type, (int) Math.round(ctx.params().getDouble("seconds") * 20),
                ctx.params().getInt("amplifier"));
        for (var target : ctx.targets()) target.addPotionEffect(effect);
    }
}
