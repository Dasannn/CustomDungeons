package dev.dasan.customdungeons.ability.impl.generic;
import dev.dasan.customdungeons.ability.AbilityRegistry;
public final class GenericAbilities {
    private GenericAbilities() {}
    public static void register(AbilityRegistry registry) {
        // on_hit_effect belongs to CoreAbilities (T05); do not register it twice.
        registry.register(new ArrowEffectAbility());
    }
}
