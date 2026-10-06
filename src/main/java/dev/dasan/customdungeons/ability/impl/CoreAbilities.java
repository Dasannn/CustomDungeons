package dev.dasan.customdungeons.ability.impl;
import dev.dasan.customdungeons.ability.AbilityRegistry;
public final class CoreAbilities {
    private CoreAbilities() {}
    public static void register(AbilityRegistry registry) {
        registry.register(new LightningAbility());
        registry.register(new OnHitEffectAbility());
        registry.register(new SummonMinionsAbility());
    }
}
