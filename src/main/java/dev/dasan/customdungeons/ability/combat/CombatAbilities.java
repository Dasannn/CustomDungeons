package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.AbilityRegistry;

public final class CombatAbilities {
    private CombatAbilities() {}
    public static void register(AbilityRegistry registry) {
        registry.register(new ChargeAbility());
        registry.register(new BossTotemAbility());
        registry.register(new DecoysAbility());
        registry.register(new InterruptibleUltimateAbility());
        registry.register(new PurgeAbility());
        registry.register(new BuffStealAbility());
        registry.register(new SilenceAbility());
        registry.register(new PainLinkAbility());
        registry.register(new BlinkBehindAbility());
    }
}
